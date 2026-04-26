package com.labs.outbox.service;

import com.labs.outbox.entity.OutboxEvent;
import com.labs.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls the outbox table and publishes PENDING events to Kafka.
 * Runs on a fixed interval (every 1 second by default).
 *
 * Guarantees: at-least-once delivery. Consumers must be idempotent.
 */
@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String TOPIC = "orders";

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public OutboxPublisher(OutboxEventRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
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

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload())
                    .get(); // block to confirm delivery

                event.setStatus(OutboxEvent.EventStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                publishedCounter.increment();
                log.info("Published event {} type={} aggregate={}", event.getId(), event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                event.setStatus(OutboxEvent.EventStatus.FAILED);
                failedCounter.increment();
                log.error("Failed to publish event {}: {}", event.getId(), e.getMessage());
            }
            outboxRepository.save(event);
        }
    }
}
