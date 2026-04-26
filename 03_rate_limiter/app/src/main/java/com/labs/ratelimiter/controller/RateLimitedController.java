package com.labs.ratelimiter.controller;

import com.labs.ratelimiter.service.TokenBucketService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class RateLimitedController {

    private final TokenBucketService rateLimiter;

    public RateLimitedController(TokenBucketService rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /**
     * Rate-limited endpoint.
     * Client identified by X-API-Key header (fallback: "anonymous").
     *
     * Returns standard rate limit headers:
     *   X-RateLimit-Remaining
     *   X-RateLimit-Retry-After-Seconds (on 429)
     */
    @GetMapping("/resource")
    public ResponseEntity<Map<String, Object>> getResource(
            @RequestHeader(value = "X-API-Key", defaultValue = "anonymous") String apiKey) {

        var result = rateLimiter.tryConsume(apiKey);

        if (result.allowed()) {
            return ResponseEntity.ok()
                .header("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()))
                .body(Map.of(
                    "message", "Request processed",
                    "clientKey", apiKey,
                    "remainingTokens", result.remainingTokens()
                ));
        } else {
            long retryAfterSeconds = result.nanosToRefill() / 1_000_000_000L + 1;
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("X-RateLimit-Remaining", "0")
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .body(Map.of(
                    "error", "Too Many Requests",
                    "clientKey", apiKey,
                    "retryAfterSeconds", retryAfterSeconds
                ));
        }
    }

    @GetMapping("/health-check")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
