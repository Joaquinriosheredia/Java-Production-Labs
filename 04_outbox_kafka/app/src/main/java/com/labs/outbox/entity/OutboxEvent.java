package com.labs.outbox.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox table: persisted atomically with the business entity in the same DB transaction.
 * A background poller reads PENDING events and publishes them to Kafka.
 * This guarantees at-least-once delivery without dual-write problems.
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status_created", columnList = "status, created_at")
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
}
