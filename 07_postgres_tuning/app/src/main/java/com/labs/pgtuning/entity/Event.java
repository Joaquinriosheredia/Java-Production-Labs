package com.labs.pgtuning.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(columnDefinition = "TEXT")
    private String payload;

    public enum EventStatus { PENDING, PROCESSED, FAILED }

    // Getters/setters
    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public void setUserId(String u) { this.userId = u; }
    public String getEventType() { return eventType; }
    public void setEventType(String t) { this.eventType = t; }
    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus s) { this.status = s; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant t) { this.occurredAt = t; }
    public String getPayload() { return payload; }
    public void setPayload(String p) { this.payload = p; }
}
