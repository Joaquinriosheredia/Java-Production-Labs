package com.labs.redisvskafka.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class MessagingBenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(MessagingBenchmarkService.class);
    static final String REDIS_CHANNEL = "lab06:benchmark";
    static final String KAFKA_TOPIC   = "lab06-benchmark";

    // 128-byte fixed payload → deterministic comparison across runs
    private static final String PAYLOAD_TEMPLATE =
        "{\"id\":%06d,\"ts\":%d,\"data\":\"%-80s\"}";

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Timer redisPublishTimer;
    private final Timer kafkaPublishTimer;

    private final AtomicLong redisReceived = new AtomicLong(0);
    private final AtomicLong kafkaReceived = new AtomicLong(0);
    private volatile CountDownLatch redisLatch;
    private volatile CountDownLatch kafkaLatch;

    // Per-message send-duration samples (nanos) for percentile computation
    private volatile ConcurrentLinkedQueue<Long> redisSendNanos;
    private volatile ConcurrentLinkedQueue<Long> kafkaSendNanos;

    public MessagingBenchmarkService(StringRedisTemplate redisTemplate,
                                      KafkaTemplate<String, String> kafkaTemplate,
                                      MeterRegistry meterRegistry) {
        this.redisTemplate     = redisTemplate;
        this.kafkaTemplate     = kafkaTemplate;
        this.redisPublishTimer = Timer.builder("lab.messaging.publish.duration")
            .tag("lab", "06").tag("backend", "redis").register(meterRegistry);
        this.kafkaPublishTimer = Timer.builder("lab.messaging.publish.duration")
            .tag("lab", "06").tag("backend", "kafka").register(meterRegistry);
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    public record LatencyStats(long p50us, long p95us, long p99us, long minUs, long maxUs, double meanUs) {}

    public record BenchmarkResult(
        String backend,
        int    messages,
        long   publishDurationMs,
        long   receivedCount,
        double publishThroughputMps,
        double deliveryRatePct,
        LatencyStats latency,
        String note
    ) {}

    public record FaultPhaseResult(
        String phase,
        long   sent,
        long   received,
        long   lost,
        double deliveryRatePct
    ) {}

    // -------------------------------------------------------------------------
    // Warmup — call before measuring to stabilise JIT + connections
    // -------------------------------------------------------------------------

    public void warmup(int warmupCount) throws InterruptedException {
        log.info("[warmup] Redis {} msgs", warmupCount);
        redisLatch = new CountDownLatch(warmupCount);
        redisSendNanos = new ConcurrentLinkedQueue<>();
        redisReceived.set(0);
        for (int i = 0; i < warmupCount; i++) {
            redisTemplate.convertAndSend(REDIS_CHANNEL, payload(i));
        }
        redisLatch.await(15, TimeUnit.SECONDS);

        log.info("[warmup] Kafka {} msgs", warmupCount);
        kafkaLatch = new CountDownLatch(warmupCount);
        kafkaSendNanos = new ConcurrentLinkedQueue<>();
        kafkaReceived.set(0);
        for (int i = 0; i < warmupCount; i++) {
            kafkaTemplate.send(KAFKA_TOPIC, "wk-" + i, payload(i));
        }
        kafkaTemplate.flush();
        kafkaLatch.await(20, TimeUnit.SECONDS);
        log.info("[warmup] complete");
    }

    // -------------------------------------------------------------------------
    // Baseline benchmarks
    // -------------------------------------------------------------------------

    public BenchmarkResult benchmarkRedis(int messages) throws InterruptedException {
        redisSendNanos = new ConcurrentLinkedQueue<>();
        redisLatch     = new CountDownLatch(messages);
        redisReceived.set(0);

        long wallStart = System.currentTimeMillis();
        for (int i = 0; i < messages; i++) {
            long t0 = System.nanoTime();
            redisPublishTimer.record(() -> redisTemplate.convertAndSend(REDIS_CHANNEL, payload(i)));
            redisSendNanos.add(System.nanoTime() - t0);
        }

        boolean completed = redisLatch.await(15, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - wallStart;
        long received = redisReceived.get();

        return buildResult("redis", messages, duration, received,
            computeLatency(redisSendNanos), completed ? null : "latch timed out");
    }

    public BenchmarkResult benchmarkKafka(int messages) throws InterruptedException {
        kafkaSendNanos = new ConcurrentLinkedQueue<>();
        kafkaLatch     = new CountDownLatch(messages);
        kafkaReceived.set(0);

        long wallStart = System.currentTimeMillis();
        for (int i = 0; i < messages; i++) {
            final int idx = i;
            long t0 = System.nanoTime();
            // async send — do NOT block per-message (.get()) to get realistic throughput
            kafkaPublishTimer.record(() ->
                kafkaTemplate.send(KAFKA_TOPIC, "key-" + idx, payload(idx)));
            kafkaSendNanos.add(System.nanoTime() - t0);
        }
        kafkaTemplate.flush(); // ensure all batches are sent before timing

        boolean completed = kafkaLatch.await(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - wallStart;
        long received = kafkaReceived.get();

        return buildResult("kafka", messages, duration, received,
            computeLatency(kafkaSendNanos), completed ? null : "latch timed out");
    }

    // -------------------------------------------------------------------------
    // Optimized variants (batching / pipeline tuning)
    // -------------------------------------------------------------------------

    public BenchmarkResult benchmarkRedisOptimized(int messages) {
        redisSendNanos = new ConcurrentLinkedQueue<>();
        redisLatch     = new CountDownLatch(messages);
        redisReceived.set(0);

        long start = System.currentTimeMillis();
        // Redis pipeline: batch publishes in a single round-trip
        redisTemplate.executePipelined(
            (org.springframework.data.redis.core.RedisCallback<Object>) conn -> {
                for (int i = 0; i < messages; i++) {
                    long t0 = System.nanoTime();
                    conn.publish(REDIS_CHANNEL.getBytes(), payload(i).getBytes());
                    redisSendNanos.add(System.nanoTime() - t0);
                }
                return null;
            });

        try {
            redisLatch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long duration = System.currentTimeMillis() - start;
        long received = redisReceived.get();
        return buildResult("redis-optimized", messages, duration, received,
            computeLatency(redisSendNanos), "pipeline batching");
    }

    public BenchmarkResult benchmarkKafkaOptimized(int messages) throws InterruptedException {
        kafkaSendNanos = new ConcurrentLinkedQueue<>();
        kafkaLatch     = new CountDownLatch(messages);
        kafkaReceived.set(0);

        long start = System.currentTimeMillis();
        // Kafka: async batch — linger.ms & batch.size tuned via optimized producer config
        for (int i = 0; i < messages; i++) {
            final int idx = i;
            long t0 = System.nanoTime();
            kafkaTemplate.send(KAFKA_TOPIC, "opt-" + idx, payload(idx));
            kafkaSendNanos.add(System.nanoTime() - t0);
        }
        kafkaTemplate.flush();

        boolean completed = kafkaLatch.await(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - start;
        long received = kafkaReceived.get();
        return buildResult("kafka-optimized", messages, duration, received,
            computeLatency(kafkaSendNanos), "async batch + linger tuning");
    }

    // -------------------------------------------------------------------------
    // Fault-injection phases — orchestrated by run-benchmark.sh
    // -------------------------------------------------------------------------

    /** Phase called while broker is UP (before simulated fault). */
    public FaultPhaseResult faultPhasePre(String backend, int messages) throws InterruptedException {
        return runFaultPhase(backend, "pre-fault", messages, "pre-");
    }

    /** Phase called while broker is DOWN — measures loss / producer errors. */
    public FaultPhaseResult faultPhaseDuring(String backend, int messages) throws InterruptedException {
        return runFaultPhase(backend, "during-fault", messages, "fault-");
    }

    /** Phase called after broker has restarted — verifies recovery. */
    public FaultPhaseResult faultPhaseAfter(String backend, int messages) throws InterruptedException {
        return runFaultPhase(backend, "post-restart", messages, "post-");
    }

    // -------------------------------------------------------------------------
    // Listener callbacks (called by Spring's MessageListenerAdapter / @KafkaListener)
    // -------------------------------------------------------------------------

    public void incrementRedisReceived() {
        redisReceived.incrementAndGet();
        if (redisLatch != null) redisLatch.countDown();
    }

    public void incrementKafkaReceived() {
        kafkaReceived.incrementAndGet();
        if (kafkaLatch != null) kafkaLatch.countDown();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private FaultPhaseResult runFaultPhase(String backend, String phase,
                                            int messages, String prefix)
            throws InterruptedException {
        AtomicLong errors = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(messages);
        long sent = 0;

        if ("redis".equals(backend)) {
            redisLatch = latch;
            redisReceived.set(0);
            for (int i = 0; i < messages; i++) {
                try {
                    redisTemplate.convertAndSend(REDIS_CHANNEL, prefix + i);
                    sent++;
                } catch (Exception e) {
                    errors.incrementAndGet();
                    latch.countDown();
                }
            }
        } else {
            kafkaLatch = latch;
            kafkaReceived.set(0);
            for (int i = 0; i < messages; i++) {
                final int idx = i;
                try {
                    kafkaTemplate.send(KAFKA_TOPIC, prefix + idx, prefix + idx)
                        .whenComplete((r, ex) -> {
                            if (ex != null) {
                                errors.incrementAndGet();
                            }
                            latch.countDown();
                        });
                    sent++;
                } catch (Exception e) {
                    errors.incrementAndGet();
                    latch.countDown();
                }
            }
            try { kafkaTemplate.flush(); } catch (Exception ignored) {}
        }

        latch.await(15, TimeUnit.SECONDS);
        long received = "redis".equals(backend) ? redisReceived.get() : kafkaReceived.get();
        long lost = messages - received;
        double rate = messages > 0 ? received * 100.0 / messages : 0;
        return new FaultPhaseResult(phase, sent, received, lost, rate);
    }

    private static BenchmarkResult buildResult(String backend, int messages,
                                                long durationMs, long received,
                                                LatencyStats latency, String note) {
        double tps  = durationMs > 0 ? messages * 1000.0 / durationMs : 0;
        double rate = messages > 0 ? received * 100.0 / messages : 0;
        return new BenchmarkResult(backend, messages, durationMs, received, tps, rate, latency, note);
    }

    private static String payload(int i) {
        return String.format(PAYLOAD_TEMPLATE, i, System.currentTimeMillis(), "benchmark-data");
    }

    private static LatencyStats computeLatency(ConcurrentLinkedQueue<Long> nanos) {
        if (nanos == null || nanos.isEmpty()) {
            return new LatencyStats(0, 0, 0, 0, 0, 0);
        }
        List<Long> us = nanos.stream()
            .map(n -> n / 1_000L)
            .sorted()
            .collect(Collectors.toList());
        int n = us.size();
        return new LatencyStats(
            us.get(Math.min((int)(n * 0.50), n - 1)),
            us.get(Math.min((int)(n * 0.95), n - 1)),
            us.get(Math.min((int)(n * 0.99), n - 1)),
            us.get(0),
            us.get(n - 1),
            us.stream().mapToLong(Long::longValue).average().orElse(0)
        );
    }
}
