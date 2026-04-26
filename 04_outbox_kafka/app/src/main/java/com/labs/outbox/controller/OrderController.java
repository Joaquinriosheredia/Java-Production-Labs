package com.labs.outbox.controller;

import com.labs.outbox.entity.Order;
import com.labs.outbox.entity.OutboxEvent;
import com.labs.outbox.repository.OutboxEventRepository;
import com.labs.outbox.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final OutboxEventRepository outboxRepository;

    public OrderController(OrderService orderService, OutboxEventRepository outboxRepository) {
        this.orderService = orderService;
        this.outboxRepository = outboxRepository;
    }

    record CreateOrderRequest(String customerId, BigDecimal amount) {}

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest req) {
        Order order = orderService.createOrder(req.customerId(), req.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
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
