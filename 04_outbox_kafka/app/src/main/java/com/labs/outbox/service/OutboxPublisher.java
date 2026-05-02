package com.labs.outbox.service;

import com.labs.outbox.entity.OutboxEvent;
import com.labs.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polls the outbox table and publishes PENDING events to Kafka.
 * Runs on a fixed interval (every 1 second by default).
 *
 * Guarantees: at-least-once delivery. Consumers must be idempotent.
 *
 * Publishing strategy: all pending events are fired concurrently (non-blocking send),
 * then results are collected with a bounded timeout ({@code outbox.publish-timeout-ms}).
 * This prevents the scheduler thread from blocking indefinitely if Kafka is slow or unavailable.
 */
@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String TOPIC = "orders";

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long publishTimeoutMs;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public OutboxPublisher(OutboxEventRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           MeterRegistry meterRegistry,
                           @Value("${outbox.publish-timeout-ms:5000}") long publishTimeoutMs) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.publishTimeoutMs = publishTimeoutMs;
        this.publishedCounter = Counter.builder("lab.outbox.published")
            .tag("lab", "04_outbox_kafka")
            .register(meterRegistry);
        this.failedCounter = Counter.builder("lab.outbox.failed")
            .tag("lab", "04_outbox_kafka")
            .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:1000}")
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> pending = outboxRepository.findPendingEvents();
        if (pending.isEmpty()) return;

        log.debug("Publishing {} outbox events", pending.size());

        // Fire all sends concurrently — no per-event blocking
        record SendAttempt(OutboxEvent event, CompletableFuture<SendResult<String, String>> future) {}
        List<SendAttempt> attempts = pending.stream()
            .map(e -> new SendAttempt(e,
                kafkaTemplate.send(TOPIC, e.getAggregateId(), e.getPayload())))
            .toList();

        // Collect results with a bounded deadline — scheduler thread cannot block indefinitely
        for (SendAttempt attempt : attempts) {
            try {
                attempt.future().get(publishTimeoutMs, TimeUnit.MILLISECONDS);
                attempt.event().setStatus(OutboxEvent.EventStatus.PUBLISHED);
                attempt.event().setPublishedAt(Instant.now());
                publishedCounter.increment();
                log.info("Published event {} type={} aggregate={}",
                    attempt.event().getId(), attempt.event().getEventType(), attempt.event().getAggregateId());
            } catch (TimeoutException e) {
                attempt.event().setStatus(OutboxEvent.EventStatus.FAILED);
                failedCounter.increment();
                log.error("Timeout publishing event {} after {}ms — Kafka may be slow or unavailable",
                    attempt.event().getId(), publishTimeoutMs);
            } catch (Exception e) {
                attempt.event().setStatus(OutboxEvent.EventStatus.FAILED);
                failedCounter.increment();
                log.error("Failed to publish event {}: {}", attempt.event().getId(), e.getMessage());
            }
            outboxRepository.save(attempt.event());
        }
    }
}
