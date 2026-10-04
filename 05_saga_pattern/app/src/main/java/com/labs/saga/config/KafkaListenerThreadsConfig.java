package com.labs.saga.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

/**
 * Kafka listener containers run their consumer loops on platform threads, on purpose.
 *
 * <p>With {@code spring.threads.virtual.enabled=true}, Spring Boot 3.2 also gives the
 * listener containers a virtual-thread executor. HTTP requests and {@code @Async} keep
 * using virtual threads; only the consumer loops are moved to platform threads. The
 * customizer also applies to the retry and DLT containers, created from the same factory.
 *
 * <p><b>Measured</b> (SagaIntegrationTest in CI, 2 of 4 CPUs, 30 runs per variant,
 * Java 21.0.12): consumer loops on virtual threads failed 5/30 with
 * "Consumer thread failed to start"; on platform threads 0/30.
 *
 * <p><b>Probable cause, not demonstrated:</b> thread dumps of stuck runs show 6-14
 * consumers blocked in a native {@code EPoll.wait} inside {@code KafkaConsumer.poll()},
 * which in Java 21 keeps the carrier thread, and 2 consumer threads never mounted on a
 * carrier, so they miss {@code consumerStartTimeout} (30 s). The dumps also showed 1-2
 * idle carriers in 4 of 5 cases, so this is slow carrier compensation while 24 poll loops
 * start, not proven starvation.
 */
@Configuration
public class KafkaListenerThreadsConfig {

    @Bean
    ContainerCustomizer<Object, Object, ConcurrentMessageListenerContainer<Object, Object>> platformThreadListeners() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("kafka-consumer-");
        executor.setVirtualThreads(false);
        return container -> container.getContainerProperties().setListenerTaskExecutor(executor);
    }
}
