package com.labs.resilience.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates calls to an unreliable external service.
 * Demonstrates Circuit Breaker → Retry → Bulkhead composition.
 *
 * Failure injection: use /admin/failure-rate to control how often calls fail.
 */
@Service
public class ExternalServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalServiceClient.class);

    private final AtomicInteger callCount = new AtomicInteger(0);
    private volatile int failureRatePercent = 0;

    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter fallbackCounter;

    public ExternalServiceClient(MeterRegistry meterRegistry) {
        this.successCounter = Counter.builder("lab.external.calls")
            .tag("lab", "02_resilience")
            .tag("outcome", "success")
            .register(meterRegistry);
        this.failureCounter = Counter.builder("lab.external.calls")
            .tag("lab", "02_resilience")
            .tag("outcome", "failure")
            .register(meterRegistry);
        this.fallbackCounter = Counter.builder("lab.external.calls")
            .tag("lab", "02_resilience")
            .tag("outcome", "fallback")
            .register(meterRegistry);
    }

    /**
     * Main call with Circuit Breaker + Retry + Bulkhead composition.
     * Order: Bulkhead → CircuitBreaker → Retry (outermost to innermost).
     */
    @Bulkhead(name = "externalService", fallbackMethod = "bulkheadFallback")
    @CircuitBreaker(name = "externalService", fallbackMethod = "circuitBreakerFallback")
    @Retry(name = "externalService")
    public String callExternalService(String requestId) {
        int call = callCount.incrementAndGet();
        log.debug("External call #{} for request {}", call, requestId);

        if (shouldFail()) {
            failureCounter.increment();
            throw new RuntimeException("External service unavailable (simulated, failure-rate=" + failureRatePercent + "%)");
        }

        successCounter.increment();
        return "OK from external service [call=" + call + ", requestId=" + requestId + "]";
    }

    public String circuitBreakerFallback(String requestId, Exception e) {
        fallbackCounter.increment();
        log.warn("Circuit breaker fallback for request {}: {}", requestId, e.getMessage());
        return "FALLBACK: Circuit breaker open — cached response for " + requestId;
    }

    public String bulkheadFallback(String requestId, Exception e) {
        fallbackCounter.increment();
        log.warn("Bulkhead fallback for request {}: {}", requestId, e.getMessage());
        return "FALLBACK: Bulkhead full — rejected " + requestId;
    }

    public void setFailureRate(int percent) {
        this.failureRatePercent = Math.min(100, Math.max(0, percent));
        log.info("Failure rate set to {}%", this.failureRatePercent);
    }

    public int getFailureRate() {
        return failureRatePercent;
    }

    private boolean shouldFail() {
        if (failureRatePercent == 0) return false;
        if (failureRatePercent == 100) return true;
        return (callCount.get() % 100) < failureRatePercent;
    }
}
