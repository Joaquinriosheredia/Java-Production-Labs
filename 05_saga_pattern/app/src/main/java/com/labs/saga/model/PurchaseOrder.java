package com.labs.saga.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus sagaStatus;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
        if (this.sagaStatus == null) this.sagaStatus = SagaStatus.STARTED;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public enum SagaStatus {
        STARTED,
        PAYMENT_PENDING,
        PAYMENT_APPROVED,
        INVENTORY_RESERVED,
        COMPLETED,
        PAYMENT_FAILED,
        INVENTORY_FAILED,
        COMPENSATED
    }

    // Getters/setters
    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String c) { this.customerId = c; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal a) { this.amount = a; }
    public SagaStatus getSagaStatus() { return sagaStatus; }
    public void setSagaStatus(SagaStatus s) { this.sagaStatus = s; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
