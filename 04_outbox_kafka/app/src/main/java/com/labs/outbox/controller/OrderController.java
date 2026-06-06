package com.labs.outbox.controller;

import com.labs.outbox.entity.Order;
import com.labs.outbox.entity.OutboxEvent;
import com.labs.outbox.repository.OutboxEventRepository;
import com.labs.outbox.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final OutboxEventRepository outboxRepository;

    public OrderController(OrderService orderService, OutboxEventRepository outboxRepository) {
        this.orderService = orderService;
        this.outboxRepository = outboxRepository;
    }

    record CreateOrderRequest(String customerId, BigDecimal amount) {}

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest req) {
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        MDC.put("customerId", req.customerId());
        try {
            log.info("Creating order amount={}", req.amount());
            Order order = orderService.createOrder(req.customerId(), req.amount());
            log.info("Order created orderId={}", order.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/outbox/stats")
    public ResponseEntity<Map<String, Object>> outboxStats() {
        return ResponseEntity.ok(Map.of(
            "pending", outboxRepository.countByStatus(OutboxEvent.EventStatus.PENDING),
            "published", outboxRepository.countByStatus(OutboxEvent.EventStatus.PUBLISHED),
            "failed", outboxRepository.countByStatus(OutboxEvent.EventStatus.FAILED)
        ));
    }
}
