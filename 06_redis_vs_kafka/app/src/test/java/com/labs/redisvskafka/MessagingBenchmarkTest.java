package com.labs.redisvskafka;

import com.labs.redisvskafka.service.MessagingBenchmarkService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class MessagingBenchmarkTest {

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url", redis::getRedisURI);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private MessagingBenchmarkService service;

    @Test
    void redisBenchmark_shouldPublishAndReceiveMessages() throws Exception {
        var result = service.benchmarkRedis(100);
        assertThat(result.messages()).isEqualTo(100);
        assertThat(result.publishDurationMs()).isGreaterThan(0);
        assertThat(result.publishThroughputMps()).isGreaterThan(0);
    }

    @Test
    void kafkaBenchmark_shouldPublishAndReceiveMessages() throws Exception {
        var result = service.benchmarkKafka(50);
        assertThat(result.messages()).isEqualTo(50);
        assertThat(result.publishThroughputMps()).isGreaterThan(0);
    }
}
