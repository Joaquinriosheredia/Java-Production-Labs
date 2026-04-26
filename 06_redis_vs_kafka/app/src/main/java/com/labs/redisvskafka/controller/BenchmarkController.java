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

    @GetMapping("/redis")
    public ResponseEntity<MessagingBenchmarkService.BenchmarkResult> benchmarkRedis(
            @RequestParam(defaultValue = "1000") int messages) throws InterruptedException {
        return ResponseEntity.ok(service.benchmarkRedis(messages));
    }

    @GetMapping("/kafka")
    public ResponseEntity<MessagingBenchmarkService.BenchmarkResult> benchmarkKafka(
            @RequestParam(defaultValue = "1000") int messages) throws Exception {
        return ResponseEntity.ok(service.benchmarkKafka(messages));
    }

    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compare(
            @RequestParam(defaultValue = "500") int messages) throws Exception {
        var redis = service.benchmarkRedis(messages);
        var kafka = service.benchmarkKafka(messages);
        return ResponseEntity.ok(Map.of(
            "redis", redis,
            "kafka", kafka,
            "redisFasterBy", kafka.publishDurationMs() > 0
                ? String.format("%.1fx", (double) kafka.publishDurationMs() / redis.publishDurationMs())
                : "N/A",
            "note", "Redis: low latency, ephemeral. Kafka: durable, replayable, higher latency."
        ));
    }
}
