package com.labs.saga.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labs.saga.model.PurchaseOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class SagaOrderService {

    private static final Logger log = LoggerFactory.getLogger(SagaOrderService.class);

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public SagaOrderService(OrderRepository orderRepository,
                             KafkaTemplate<String, String> kafka,
                             ObjectMapper mapper) {
        this.orderRepository = orderRepository;
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @Transactional
    public PurchaseOrder startSaga(String customerId, BigDecimal amount) {
        PurchaseOrder order = new PurchaseOrder();
        order.setCustomerId(customerId);
        order.setAmount(amount);
        PurchaseOrder saved = orderRepository.save(order);

        try {
            String payload = mapper.writeValueAsString(Map.of(
                "orderId", saved.getId(),
                "customerId", customerId,
                "amount", amount
            ));
            kafka.send("saga.order.created", saved.getId().toString(), payload).get();
            log.info("Saga started for order {}", saved.getId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to start saga", e);
        }

        return saved;
    }
}
