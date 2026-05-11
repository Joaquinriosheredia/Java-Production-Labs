package com.labs.ratelimiter.service;

import com.labs.ratelimiter.RedisUnavailableException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Token bucket rate limiter backed by Redis.
 * Each API client (identified by key) gets its own bucket stored in Redis.
 * This ensures rate limiting is consistent across multiple application instances.
 *
 * Strategy: configurable capacity / refill rate (defaults: 20 capacity, 10 tokens/s).
 */
@Service
public class TokenBucketService {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketService.class);

    private final int capacity;
    private final int refillTokens;
    private final Duration refillPeriod;

    private final ProxyManager<String> proxyManager;
    private final Counter allowedCounter;
    private final Counter rejectedCounter;

    public TokenBucketService(
            ProxyManager<String> proxyManager,
            MeterRegistry meterRegistry,
            @Value("${rate-limiter.capacity:20}") int capacity,
            @Value("${rate-limiter.refill-tokens:1}") int refillTokens,
            @Value("${rate-limiter.refill-period-millis:100}") long refillPeriodMillis) {
        this.proxyManager = proxyManager;
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriod = Duration.ofMillis(refillPeriodMillis);
        this.allowedCounter = Counter.builder("lab.ratelimiter.requests")
            .tag("lab", "03_rate_limiter")
            .tag("outcome", "allowed")
            .register(meterRegistry);
        this.rejectedCounter = Counter.builder("lab.ratelimiter.requests")
            .tag("lab", "03_rate_limiter")
            .tag("outcome", "rejected")
            .register(meterRegistry);
    }

    public record RateLimitResult(boolean allowed, long remainingTokens, long nanosToRefill) {}

    @CircuitBreaker(name = "redis", fallbackMethod = "tryConsumeFallback")
    public RateLimitResult tryConsume(String clientKey) {
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokens, refillPeriod)
                .build())
            .build();

        var bucket = proxyManager.builder().build(clientKey, configSupplier);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            allowedCounter.increment();
            log.debug("Rate limit ALLOWED for key={}, remaining={}", clientKey, probe.getRemainingTokens());
            return new RateLimitResult(true, probe.getRemainingTokens(), 0);
        } else {
            rejectedCounter.increment();
            log.debug("Rate limit REJECTED for key={}, nanosToWait={}", clientKey, probe.getNanosToWaitForRefill());
            return new RateLimitResult(false, 0, probe.getNanosToWaitForRefill());
        }
    }

    // Called by Resilience4j when the circuit is OPEN or when tryConsume throws.
    // Throwing here propagates RedisUnavailableException to the controller → 503.
    private RateLimitResult tryConsumeFallback(String clientKey, Throwable ex) {
        log.warn("Redis circuit breaker triggered for key={}: {}", clientKey, ex.getMessage());
        throw new RedisUnavailableException("Rate limiter backend unavailable", ex);
    }
}
