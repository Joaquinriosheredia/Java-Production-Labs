package com.labs.saga;

import com.labs.saga.model.PurchaseOrder;
import com.labs.saga.service.OrderRepository;
import com.labs.saga.service.SagaOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    // 6 @KafkaListener x @RetryableTopic(attempts = 3) = main + 2 retry + DLT each.
    private static final int EXPECTED_LISTENER_CONTAINERS = 24;
    // Every topic in this lab has one partition (broker default; @RetryableTopic creates
    // its retry and DLT topics with one partition).
    private static final int PARTITIONS_PER_CONTAINER = 1;
    private static final Duration LISTENERS_READY_LIMIT = Duration.ofSeconds(90);

    /**
     * The saga only advances once every consumer group in the chain has partitions.
     * Starting it earlier made the 15 s saga timeout also cover the listeners' startup,
     * which is slow with few CPUs (CI), so the test failed intermittently.
     */
    private void awaitListenersAssigned() {
        Collection<MessageListenerContainer> containers = registry.getAllListenerContainers();
        assertThat(containers).as("listener containers").hasSize(EXPECTED_LISTENER_CONTAINERS);
        try {
            assertTimeoutPreemptively(LISTENERS_READY_LIMIT, () -> {
                for (MessageListenerContainer container : containers) {
                    ContainerTestUtils.waitForAssignment(container, PARTITIONS_PER_CONTAINER);
                }
            });
        } catch (AssertionError | IllegalStateException e) {
            throw new AssertionError(String.format(
                "Kafka listeners not ready: no partitions assigned within %ss to %s. "
                    + "The saga was not started; this is a startup failure, not a saga failure.",
                LISTENERS_READY_LIMIT.toSeconds(), unassigned(containers)), e);
        }
    }

    private static String unassigned(Collection<MessageListenerContainer> containers) {
        return containers.stream()
            .filter(c -> c.getAssignedPartitions() == null || c.getAssignedPartitions().isEmpty())
            .map(c -> c.getListenerId() + " " + Arrays.toString(c.getContainerProperties().getTopics()))
            .collect(Collectors.joining(", ", "[", "]"));
    }

    @Test
    void happyPath_sagaShouldComplete() {
        awaitListenersAssigned();

        PurchaseOrder order = orderService.startSaga("customer-happy", new BigDecimal("100.00"));
        assertThat(order.getId()).isNotNull();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            PurchaseOrder updated = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(updated.getSagaStatus()).isEqualTo(PurchaseOrder.SagaStatus.COMPLETED);
        });
    }
}
