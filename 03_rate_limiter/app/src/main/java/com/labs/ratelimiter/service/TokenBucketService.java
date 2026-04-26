package com.labs.ratelimiter.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Token bucket rate limiter backed by Redis.
 * Each API client (identified by key) gets its own bucket stored in Redis.
 * This ensures rate limiting is consistent across multiple application instances.
 *
 * Strategy: 10 tokens/second with burst capacity of 20.
 */
@Service
public class TokenBucketService {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketService.class);

    private static final int CAPACITY = 20;
    private static final int REFILL_TOKENS = 10;
    private static final Duration REFILL_PERIOD = Duration.ofSeconds(1);

    private final ProxyManager<String> proxyManager;
    private final Counter allowedCounter;
    private final Counter rejectedCounter;

    public TokenBucketService(ProxyManager<String> proxyManager, MeterRegistry meterRegistry) {
        this.proxyManager = proxyManager;
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

    /**
     * Try to consume 1 token from the bucket identified by key.
     * @param clientKey usually the API key or IP address
     */
    public RateLimitResult tryConsume(String clientKey) {
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                .capacity(CAPACITY)
                .refillGreedy(REFILL_TOKENS, REFILL_PERIOD)
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
}
