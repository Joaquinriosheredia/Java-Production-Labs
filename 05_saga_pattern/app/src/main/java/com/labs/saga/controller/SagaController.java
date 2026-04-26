package com.labs.saga.controller;

import com.labs.saga.model.PurchaseOrder;
import com.labs.saga.saga.OrderSagaOrchestrator;
import com.labs.saga.service.OrderRepository;
import com.labs.saga.service.SagaOrderService;
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
        PurchaseOrder order = orderService.startSaga(req.customerId(), req.amount());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(order);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<PurchaseOrder> getOrder(@PathVariable UUID id) {
        return orderRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
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
