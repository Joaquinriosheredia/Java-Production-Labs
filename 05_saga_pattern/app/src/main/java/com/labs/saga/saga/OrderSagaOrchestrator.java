package com.labs.saga.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labs.saga.event.SagaEvents;
import com.labs.saga.model.PurchaseOrder;
import com.labs.saga.service.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Choreography-based Saga coordinator.
 *
 * Each service listens to its trigger event and emits a result event.
 * No central orchestrator: services react to domain events.
 *
 * This class simulates all participants (payment, inventory) in one app
 * to keep the lab self-contained. In production each would be a separate service.
 */
@Component
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final KafkaTemplate<String, String> kafka;
    private final OrderRepository orderRepository;
    private final ObjectMapper mapper;
    private final Counter completedCounter;
    private final Counter compensatedCounter;

    // Controls payment/inventory failure for chaos testing
    private volatile boolean simulatePaymentFailure = false;
    private volatile boolean simulateInventoryFailure = false;

    public OrderSagaOrchestrator(KafkaTemplate<String, String> kafka,
                                  OrderRepository orderRepository,
                                  ObjectMapper mapper,
                                  MeterRegistry meterRegistry) {
        this.kafka = kafka;
        this.orderRepository = orderRepository;
        this.mapper = mapper;
        this.completedCounter = Counter.builder("lab.saga.completed")
            .tag("lab", "05_saga_pattern").register(meterRegistry);
        this.compensatedCounter = Counter.builder("lab.saga.compensated")
            .tag("lab", "05_saga_pattern").register(meterRegistry);
    }

    // Step 1: Order created → initiate payment
    @KafkaListener(topics = "saga.order.created", groupId = "payment-service")
    @Transactional
    public void onOrderCreated(String message) throws Exception {
        var event = mapper.readValue(message, Map.class);
        UUID orderId = UUID.fromString(event.get("orderId").toString());
        log.info("Processing payment for order {}", orderId);

        updateStatus(orderId, PurchaseOrder.SagaStatus.PAYMENT_PENDING);

        if (simulatePaymentFailure) {
            publish("saga.payment.failed", Map.of("orderId", orderId, "reason", "Insufficient funds (simulated)"));
        } else {
            publish("saga.payment.approved", Map.of("orderId", orderId, "transactionId", UUID.randomUUID()));
        }
    }

    // Step 2: Payment approved → reserve inventory
    @KafkaListener(topics = "saga.payment.approved", groupId = "inventory-service")
    @Transactional
    public void onPaymentApproved(String message) throws Exception {
        var event = mapper.readValue(message, Map.class);
        UUID orderId = UUID.fromString(event.get("orderId").toString());
        log.info("Reserving inventory for order {}", orderId);

        updateStatus(orderId, PurchaseOrder.SagaStatus.PAYMENT_APPROVED);

        if (simulateInventoryFailure) {
            publish("saga.inventory.failed", Map.of("orderId", orderId, "reason", "Out of stock (simulated)"));
        } else {
            publish("saga.inventory.reserved", Map.of("orderId", orderId, "reservationId", UUID.randomUUID()));
        }
    }

    // Step 3 (happy path): Inventory reserved → complete order
    @KafkaListener(topics = "saga.inventory.reserved", groupId = "order-service-complete")
    @Transactional
    public void onInventoryReserved(String message) throws Exception {
        var event = mapper.readValue(message, Map.class);
        UUID orderId = UUID.fromString(event.get("orderId").toString());
        log.info("Order {} completed", orderId);

        updateStatus(orderId, PurchaseOrder.SagaStatus.COMPLETED);
        completedCounter.increment();
    }

    // Compensation Step A: Inventory failed → refund payment
    @KafkaListener(topics = "saga.inventory.failed", groupId = "payment-service-compensate")
    @Transactional
    public void onInventoryFailed(String message) throws Exception {
        var event = mapper.readValue(message, Map.class);
        UUID orderId = UUID.fromString(event.get("orderId").toString());
        log.warn("Compensating order {} — inventory failed, refunding payment", orderId);

        updateStatus(orderId, PurchaseOrder.SagaStatus.INVENTORY_FAILED);
        publish("saga.payment.refunded", Map.of("orderId", orderId, "reason", event.get("reason")));
    }

    // Compensation Step B: Payment refunded → cancel order
    @KafkaListener(topics = "saga.payment.refunded", groupId = "order-service-cancel")
    @Transactional
    public void onPaymentRefunded(String message) throws Exception {
        var event = mapper.readValue(message, Map.class);
        UUID orderId = UUID.fromString(event.get("orderId").toString());
        log.warn("Order {} cancelled after compensation", orderId);

        updateStatus(orderId, PurchaseOrder.SagaStatus.COMPENSATED);
        compensatedCounter.increment();
    }

    // Payment failure compensation
    @KafkaListener(topics = "saga.payment.failed", groupId = "order-service-payment-failed")
    @Transactional
    public void onPaymentFailed(String message) throws Exception {
        var event = mapper.readValue(message, Map.class);
        UUID orderId = UUID.fromString(event.get("orderId").toString());
        log.warn("Order {} payment failed — cancelling", orderId);
        updateStatus(orderId, PurchaseOrder.SagaStatus.PAYMENT_FAILED);
        compensatedCounter.increment();
    }

    private void updateStatus(UUID orderId, PurchaseOrder.SagaStatus status) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setSagaStatus(status);
            orderRepository.save(order);
        });
    }

    private void publish(String topic, Object payload) throws Exception {
        kafka.send(topic, mapper.writeValueAsString(payload)).get();
    }

    public void setSimulatePaymentFailure(boolean v) { this.simulatePaymentFailure = v; }
    public void setSimulateInventoryFailure(boolean v) { this.simulateInventoryFailure = v; }
}
