package com.labs.redisvskafka.controller;

import com.labs.redisvskafka.service.MessagingBenchmarkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/benchmark")
public class BenchmarkController {

    private final MessagingBenchmarkService service;

    public BenchmarkController(MessagingBenchmarkService service) {
        this.service = service;
    }

    // --- Warmup ---

    @PostMapping("/warmup")
    public ResponseEntity<Map<String, String>> warmup(
            @RequestParam(defaultValue = "200") int messages) throws InterruptedException {
        service.warmup(messages);
        return ResponseEntity.ok(Map.of("status", "warmup complete", "messages", String.valueOf(messages)));
    }

    // --- Baseline benchmarks ---

    @GetMapping("/redis")
    public ResponseEntity<MessagingBenchmarkService.BenchmarkResult> benchmarkRedis(
            @RequestParam(defaultValue = "1000") int messages) throws InterruptedException {
        return ResponseEntity.ok(service.benchmarkRedis(messages));
    }

    @GetMapping("/kafka")
    public ResponseEntity<MessagingBenchmarkService.BenchmarkResult> benchmarkKafka(
            @RequestParam(defaultValue = "1000") int messages) throws InterruptedException {
        return ResponseEntity.ok(service.benchmarkKafka(messages));
    }

    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compare(
            @RequestParam(defaultValue = "500") int messages) throws InterruptedException {
        var redis = service.benchmarkRedis(messages);
        var kafka = service.benchmarkKafka(messages);
        String speedRatio = kafka.publishDurationMs() > 0
            ? String.format("%.1fx", (double) kafka.publishDurationMs() / Math.max(redis.publishDurationMs(), 1))
            : "N/A";
        return ResponseEntity.ok(Map.of(
            "redis", redis,
            "kafka", kafka,
            "redisFasterBy", speedRatio,
            "note", "Redis: low latency, ephemeral. Kafka: durable, replayable, higher latency."
        ));
    }

    // --- Optimized benchmarks ---

    @GetMapping("/redis/optimized")
    public ResponseEntity<MessagingBenchmarkService.BenchmarkResult> redisOptimized(
            @RequestParam(defaultValue = "1000") int messages) {
        return ResponseEntity.ok(service.benchmarkRedisOptimized(messages));
    }

    @GetMapping("/kafka/optimized")
    public ResponseEntity<MessagingBenchmarkService.BenchmarkResult> kafkaOptimized(
            @RequestParam(defaultValue = "1000") int messages) throws InterruptedException {
        return ResponseEntity.ok(service.benchmarkKafkaOptimized(messages));
    }

    // --- Fault injection phases (orchestrated by run-benchmark.sh) ---

    @PostMapping("/fault/{backend}/pre")
    public ResponseEntity<MessagingBenchmarkService.FaultPhaseResult> faultPre(
            @PathVariable String backend,
            @RequestParam(defaultValue = "200") int messages) throws InterruptedException {
        return ResponseEntity.ok(service.faultPhasePre(backend, messages));
    }

    @PostMapping("/fault/{backend}/during")
    public ResponseEntity<MessagingBenchmarkService.FaultPhaseResult> faultDuring(
            @PathVariable String backend,
            @RequestParam(defaultValue = "200") int messages) throws InterruptedException {
        return ResponseEntity.ok(service.faultPhaseDuring(backend, messages));
    }

    @PostMapping("/fault/{backend}/after")
    public ResponseEntity<MessagingBenchmarkService.FaultPhaseResult> faultAfter(
            @PathVariable String backend,
            @RequestParam(defaultValue = "200") int messages) throws InterruptedException {
        return ResponseEntity.ok(service.faultPhaseAfter(backend, messages));
    }
}
