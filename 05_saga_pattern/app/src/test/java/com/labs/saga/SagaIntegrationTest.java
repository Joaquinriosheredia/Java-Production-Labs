package com.labs.saga;

import com.labs.saga.model.PurchaseOrder;
import com.labs.saga.service.OrderRepository;
import com.labs.saga.service.SagaOrderService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SagaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("saga_lab")
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
    private SagaOrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void happyPath_sagaShouldComplete() {
        PurchaseOrder order = orderService.startSaga("customer-happy", new BigDecimal("100.00"));
        assertThat(order.getId()).isNotNull();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            PurchaseOrder updated = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(updated.getSagaStatus()).isEqualTo(PurchaseOrder.SagaStatus.COMPLETED);
        });
    }
}
