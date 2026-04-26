package com.labs.saga.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Saga event types for the choreography-based order saga.
 *
 * Flow (happy path):
 *   OrderCreated → PaymentApproved → InventoryReserved → OrderCompleted
 *
 * Flow (failure with compensation):
 *   OrderCreated → PaymentApproved → InventoryFailed → PaymentRefunded → OrderCancelled
 */
public sealed interface SagaEvents {

    record OrderCreated(UUID orderId, String customerId, BigDecimal amount) implements SagaEvents {}
    record PaymentApproved(UUID orderId, String transactionId) implements SagaEvents {}
    record PaymentFailed(UUID orderId, String reason) implements SagaEvents {}
    record PaymentRefunded(UUID orderId, String reason) implements SagaEvents {}
    record InventoryReserved(UUID orderId, String reservationId) implements SagaEvents {}
    record InventoryFailed(UUID orderId, String reason) implements SagaEvents {}
    record OrderCompleted(UUID orderId) implements SagaEvents {}
    record OrderCancelled(UUID orderId, String reason) implements SagaEvents {}
}
