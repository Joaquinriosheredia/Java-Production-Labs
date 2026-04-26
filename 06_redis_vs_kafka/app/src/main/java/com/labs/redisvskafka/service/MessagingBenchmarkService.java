package com.labs.redisvskafka.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MessagingBenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(MessagingBenchmarkService.class);
    private static final String REDIS_CHANNEL = "lab06:benchmark";
    private static final String KAFKA_TOPIC = "lab06-benchmark";

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Timer redisPublishTimer;
    private final Timer kafkaPublishTimer;

    // Used by subscriber to measure end-to-end latency
    private final AtomicLong redisReceived = new AtomicLong(0);
    private final AtomicLong kafkaReceived = new AtomicLong(0);
    private volatile CountDownLatch redisLatch;
    private volatile CountDownLatch kafkaLatch;

    public MessagingBenchmarkService(StringRedisTemplate redisTemplate,
                                      KafkaTemplate<String, String> kafkaTemplate,
                                      MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.redisPublishTimer = Timer.builder("lab.messaging.publish.duration")
            .tag("lab", "06_redis_vs_kafka")
            .tag("backend", "redis")
            .register(meterRegistry);
        this.kafkaPublishTimer = Timer.builder("lab.messaging.publish.duration")
            .tag("lab", "06_redis_vs_kafka")
            .tag("backend", "kafka")
            .register(meterRegistry);
    }

    public record BenchmarkResult(
        String backend,
        int messages,
        long publishDurationMs,
        long receivedCount,
        double publishThroughputMps
    ) {}

    public BenchmarkResult benchmarkRedis(int messages) throws InterruptedException {
        redisLatch = new CountDownLatch(messages);
        redisReceived.set(0);

        long start = System.currentTimeMillis();
        for (int i = 0; i < messages; i++) {
            final int idx = i;
            redisPublishTimer.record(() ->
                redisTemplate.convertAndSend(REDIS_CHANNEL, "msg-" + idx));
        }
        long publishMs = System.currentTimeMillis() - start;

        redisLatch.await(10, TimeUnit.SECONDS);
        double tps = messages * 1000.0 / Math.max(publishMs, 1);
        return new BenchmarkResult("redis", messages, publishMs, redisReceived.get(), tps);
    }

    public BenchmarkResult benchmarkKafka(int messages) throws InterruptedException, Exception {
        kafkaLatch = new CountDownLatch(messages);
        kafkaReceived.set(0);

        long start = System.currentTimeMillis();
        for (int i = 0; i < messages; i++) {
            final int idx = i;
            kafkaPublishTimer.record(() -> {
                try {
                    kafkaTemplate.send(KAFKA_TOPIC, "key-" + idx, "msg-" + idx).get();
                } catch (Exception e) {
                    log.error("Kafka publish error", e);
                }
            });
        }
        long publishMs = System.currentTimeMillis() - start;

        kafkaLatch.await(30, TimeUnit.SECONDS);
        double tps = messages * 1000.0 / Math.max(publishMs, 1);
        return new BenchmarkResult("kafka", messages, publishMs, kafkaReceived.get(), tps);
    }

    public void incrementRedisReceived() {
        redisReceived.incrementAndGet();
        if (redisLatch != null) redisLatch.countDown();
    }

    public void incrementKafkaReceived() {
        kafkaReceived.incrementAndGet();
        if (kafkaLatch != null) kafkaLatch.countDown();
    }
}
