package com.labs.k8s.controller;

import com.labs.k8s.service.WorkloadSimulator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Validated
public class WorkloadController {

    private final WorkloadSimulator simulator;

    public WorkloadController(WorkloadSimulator simulator) {
        this.simulator = simulator;
    }

    /**
     * Simulates a request requiring workMs of processing.
     * Under k6 load, activeRequests metric rises → HPA scales up pods.
     */
    @GetMapping("/work")
    public ResponseEntity<WorkloadSimulator.WorkResult> doWork(
            @RequestParam(defaultValue = "100") @Min(0) @Max(30000) long workMs) throws InterruptedException {
        return ResponseEntity.ok(simulator.processRequest(workMs));
    }

    /**
     * Simulate queue depth spike (triggers HPA without actual requests).
     */
    @PostMapping("/chaos/queue-depth")
    public ResponseEntity<Map<String, Object>> setQueueDepth(
            @RequestParam int depth) {
        simulator.setQueueDepth(depth);
        return ResponseEntity.ok(Map.of(
            "queueDepth", depth,
            "message", "HPA should scale up if depth > threshold"
        ));
    }

    @GetMapping("/metrics/snapshot")
    public ResponseEntity<Map<String, Object>> metricsSnapshot() {
        return ResponseEntity.ok(Map.of(
            "activeRequests", simulator.getActiveRequests(),
            "queueDepth", simulator.getQueueDepth(),
            "prometheusEndpoint", "/actuator/prometheus",
            "hpaMetric", "lab_active_requests_gauge"
        ));
    }
}
