package com.labs.saga.controller;

import com.labs.saga.model.PurchaseOrder;
import com.labs.saga.saga.OrderSagaOrchestrator;
import com.labs.saga.service.OrderRepository;
import com.labs.saga.service.SagaOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/saga")
public class SagaController {

    private static final Logger log = LoggerFactory.getLogger(SagaController.class);

    private final SagaOrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderSagaOrchestrator orchestrator;

    public SagaController(SagaOrderService orderService,
                           OrderRepository orderRepository,
                           OrderSagaOrchestrator orchestrator) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.orchestrator = orchestrator;
    }

    record CreateOrderRequest(String customerId, BigDecimal amount) {}

    @PostMapping("/orders")
    public ResponseEntity<PurchaseOrder> createOrder(@RequestBody CreateOrderRequest req) {
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        MDC.put("customerId", req.customerId());
        try {
            log.info("Starting saga amount={}", req.amount());
            PurchaseOrder order = orderService.startSaga(req.customerId(), req.amount());
            log.info("Saga initiated orderId={} status={}", order.getId(), order.getSagaStatus());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(order);
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<PurchaseOrder> getOrder(@PathVariable UUID id) {
        MDC.put("orderId", id.toString());
        try {
            return orderRepository.findById(id)
                .map(order -> {
                    log.info("Order lookup orderId={} status={}", id, order.getSagaStatus());
                    return ResponseEntity.ok(order);
                })
                .orElseGet(() -> {
                    log.warn("Order not found orderId={}", id);
                    return ResponseEntity.notFound().<PurchaseOrder>build();
                });
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Long> byStatus = Arrays.stream(PurchaseOrder.SagaStatus.values())
            .collect(Collectors.toMap(Enum::name, orderRepository::countBySagaStatus));
        return ResponseEntity.ok(Map.of("byStatus", byStatus));
    }

    @PostMapping("/chaos/payment-failure")
    public ResponseEntity<String> simulatePaymentFailure(@RequestParam boolean enabled) {
        orchestrator.setSimulatePaymentFailure(enabled);
        return ResponseEntity.ok("Payment failure simulation: " + enabled);
    }

    @PostMapping("/chaos/inventory-failure")
    public ResponseEntity<String> simulateInventoryFailure(@RequestParam boolean enabled) {
        orchestrator.setSimulateInventoryFailure(enabled);
        return ResponseEntity.ok("Inventory failure simulation: " + enabled);
    }
}
