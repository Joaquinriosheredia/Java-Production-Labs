package com.labs.outbox;

import com.labs.outbox.entity.Order;
import com.labs.outbox.entity.OutboxEvent;
import com.labs.outbox.repository.OutboxEventRepository;
import com.labs.outbox.service.OrderService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OutboxIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("outbox_lab")
        .withUsername("labs")
        .withPassword("labs");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Test
    void createOrder_shouldPersistOrderAndOutboxEventAtomically() {
        Order order = orderService.createOrder("customer-1", new BigDecimal("99.99"));

        assertThat(order.getId()).isNotNull();
        assertThat(order.getCustomerId()).isEqualTo("customer-1");

        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).anyMatch(e ->
            e.getAggregateId().equals(order.getId().toString()) &&
            e.getEventType().equals("OrderCreated")
        );
    }

    @Test
    void outboxPoller_shouldPublishEventToKafka() throws InterruptedException {
        orderService.createOrder("customer-kafka", new BigDecimal("50.00"));

        // Wait for the poller to publish
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            long published = outboxRepository.countByStatus(OutboxEvent.EventStatus.PUBLISHED);
            assertThat(published).isGreaterThanOrEqualTo(1);
        });

        // Verify event reached Kafka
        try (KafkaConsumer<String, String> consumer = createConsumer(kafka.getBootstrapServers())) {
            consumer.subscribe(List.of("orders"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);
        }
    }

    private KafkaConsumer<String, String> createConsumer(String bootstrapServers) {
        return new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()
        ));
    }
}
