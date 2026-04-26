package com.labs.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labs.outbox.entity.Order;
import com.labs.outbox.entity.OutboxEvent;
import com.labs.outbox.repository.OrderRepository;
import com.labs.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Creates an order AND its outbox event in a single DB transaction.
 * This is the core of the Transactional Outbox Pattern:
 * - If the DB commit succeeds: both order and event are persisted atomically
 * - If Kafka is down: no data loss — the poller will retry from the outbox table
 * - No dual-write: we never write to two systems without a transaction
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Counter ordersCreated;

    public OrderService(OrderRepository orderRepository,
                        OutboxEventRepository outboxRepository,
                        ObjectMapper objectMapper,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.ordersCreated = Counter.builder("lab.orders.created")
            .tag("lab", "04_outbox_kafka")
            .register(meterRegistry);
    }

    @Transactional
    public Order createOrder(String customerId, BigDecimal amount) {
        // Step 1: persist the business entity
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setAmount(amount);
        Order saved = orderRepository.save(order);

        // Step 2: persist the outbox event in the SAME transaction
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("Order");
        event.setAggregateId(saved.getId().toString());
        event.setEventType("OrderCreated");
        event.setPayload(toJson(saved));
        outboxRepository.save(event);

        ordersCreated.increment();
        log.info("Order {} created with outbox event (customerId={})", saved.getId(), customerId);
        return saved;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }
}
