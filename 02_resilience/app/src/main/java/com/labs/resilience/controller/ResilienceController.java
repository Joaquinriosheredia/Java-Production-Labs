package com.labs.resilience.controller;

import com.labs.resilience.service.ExternalServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ResilienceController {

    private static final Logger log = LoggerFactory.getLogger(ResilienceController.class);
    private final ExternalServiceClient client;

    public ResilienceController(ExternalServiceClient client) {
        this.client = client;
    }

    /**
     * Calls the (possibly failing) external service.
     * Resilience4j applies: Bulkhead → CircuitBreaker → Retry automatically.
     */
    @GetMapping("/call")
    public ResponseEntity<Map<String, Object>> call(
            @RequestParam(defaultValue = "req-1") String requestId) {
        String result = client.callExternalService(requestId);
        boolean isFallback = result.startsWith("FALLBACK");
        log.info("Call result for {}: {}", requestId, result);
        return ResponseEntity.ok(Map.of(
            "requestId", requestId,
            "result", result,
            "fallback", isFallback
        ));
    }

    /**
     * Chaos control: set failure rate of external service.
     * POST /api/v1/admin/failure-rate?percent=80
     */
    @PostMapping("/admin/failure-rate")
    public ResponseEntity<Map<String, Object>> setFailureRate(
            @RequestParam int percent) {
        client.setFailureRate(percent);
        return ResponseEntity.ok(Map.of(
            "failureRatePercent", client.getFailureRate(),
            "message", "Failure rate updated. Call /api/v1/call to observe circuit breaker behavior."
        ));
    }

    @GetMapping("/admin/failure-rate")
    public ResponseEntity<Map<String, Object>> getFailureRate() {
        return ResponseEntity.ok(Map.of("failureRatePercent", client.getFailureRate()));
    }
}
