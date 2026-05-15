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
 * Polls the outbox table and publishes PENDING (and retryable FAILED) events to Kafka.
 * Runs on a fixed interval (default 500ms).
 *
 * Delivery guarantee: at-least-once. Consumers must be idempotent.
 *
 * Retry strategy: exponential backoff on FAILED events.
 *   next_retry_at = now + min(2^(retryCount-1), 60) seconds
 *   Events are retried up to maxRetries times, then permanently FAILED.
 *
 * Publishing strategy: all eligible events are fired concurrently (non-blocking send),
 * then results are collected with a bounded timeout (outbox.publish-timeout-ms).
 */
@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String TOPIC = "orders";
    private static final long MAX_BACKOFF_SECONDS = 60L;

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long publishTimeoutMs;
    private final int maxRetries;
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Counter retriedCounter;

    public OutboxPublisher(OutboxEventRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           MeterRegistry meterRegistry,
                           @Value("${outbox.publish-timeout-ms:10000}") long publishTimeoutMs,
                           @Value("${outbox.max-retries:10}") int maxRetries) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.publishTimeoutMs = publishTimeoutMs;
        this.maxRetries = maxRetries;
        this.publishedCounter = Counter.builder("lab.outbox.published")
            .tag("lab", "04_outbox_kafka").register(meterRegistry);
        this.failedCounter = Counter.builder("lab.outbox.failed")
            .tag("lab", "04_outbox_kafka").register(meterRegistry);
        this.retriedCounter = Counter.builder("lab.outbox.retried")
            .tag("lab", "04_outbox_kafka").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> events = outboxRepository.findRetryableEvents(maxRetries);
        if (events.isEmpty()) return;

        log.debug("Publishing {} outbox events ({} max retries)", events.size(), maxRetries);

        record SendAttempt(OutboxEvent event, CompletableFuture<SendResult<String, String>> future) {}
        List<SendAttempt> attempts = events.stream()
            .map(e -> new SendAttempt(e,
                kafkaTemplate.send(TOPIC, e.getAggregateId(), e.getPayload())))
            .toList();

        for (SendAttempt attempt : attempts) {
            try {
                attempt.future().get(publishTimeoutMs, TimeUnit.MILLISECONDS);
                attempt.event().setStatus(OutboxEvent.EventStatus.PUBLISHED);
                attempt.event().setPublishedAt(Instant.now());
                publishedCounter.increment();
                log.info("Published event {} type={} aggregate={} retryCount={}",
                    attempt.event().getId(), attempt.event().getEventType(),
                    attempt.event().getAggregateId(), attempt.event().getRetryCount());
            } catch (TimeoutException e) {
                markFailed(attempt.event(), "Timeout after " + publishTimeoutMs + "ms");
            } catch (Exception e) {
                markFailed(attempt.event(), e.getMessage());
            }
            outboxRepository.save(attempt.event());
        }
    }

    private void markFailed(OutboxEvent event, String reason) {
        int newRetryCount = event.getRetryCount() + 1;
        event.setRetryCount(newRetryCount);
        event.setStatus(OutboxEvent.EventStatus.FAILED);

        // Exponential backoff: 2^(retryCount-1) seconds, capped at MAX_BACKOFF_SECONDS
        long backoffSecs = Math.min((long) Math.pow(2, newRetryCount - 1), MAX_BACKOFF_SECONDS);
        event.setNextRetryAt(Instant.now().plusSeconds(backoffSecs));

        if (newRetryCount >= maxRetries) {
            failedCounter.increment();
            log.error("Event {} permanently FAILED after {} retries — manual intervention required. reason={}",
                event.getId(), newRetryCount, reason);
        } else {
            retriedCounter.increment();
            log.warn("Event {} failed (attempt {}/{}), retry in {}s. reason={}",
                event.getId(), newRetryCount, maxRetries, backoffSecs, reason);
        }
    }
}
