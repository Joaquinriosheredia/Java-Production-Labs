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
 * listener containers a virtual-thread executor. A consumer loop is a long-lived thread
 * that blocks in {@code poll()} for its whole life, so virtual threads bring no benefit
 * there. On 2 CPUs some of this lab's 24 consumers (6 listeners x main + 2 retry + DLT)
 * did not start within {@code consumerStartTimeout} (see the PR that introduced this).
 *
 * <p>HTTP requests and {@code @Async} keep using virtual threads; only the consumer loops
 * are pinned to platform threads. The customizer also applies to the retry and DLT
 * containers, which are created from the same factory.
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
