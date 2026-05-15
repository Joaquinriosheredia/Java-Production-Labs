package com.labs.outbox.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox table: persisted atomically with the business entity in the same DB transaction.
 * A background poller reads PENDING events and publishes them to Kafka.
 * This guarantees at-least-once delivery without dual-write problems.
 *
 * FAILED events are retried with exponential backoff via retry_count + next_retry_at.
 * Max retries is configurable (default 10). After max retries, events remain FAILED
 * and require manual intervention or a dead-letter process.
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
    @Index(name = "idx_outbox_failed_retry",   columnList = "next_retry_at, created_at")
})
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = EventStatus.PENDING;
    }

    public enum EventStatus { PENDING, PUBLISHED, FAILED }

    // Getters/setters
    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String t) { this.aggregateType = t; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String id) { this.aggregateId = id; }
    public String getEventType() { return eventType; }
    public void setEventType(String t) { this.eventType = t; }
    public String getPayload() { return payload; }
    public void setPayload(String p) { this.payload = p; }
    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus s) { this.status = s; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant t) { this.publishedAt = t; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int n) { this.retryCount = n; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant t) { this.nextRetryAt = t; }
}
