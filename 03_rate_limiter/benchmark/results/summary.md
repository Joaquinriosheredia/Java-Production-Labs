# Lab 03 — Rate Limiter Benchmark Summary

**Date:** 2026-05-11  
**Tool:** k6 v1.6.1  
**Stack:** Spring Boot 3.2.5 + Java 21 VT + Redis 7-alpine (Docker)  
**Config:** 20 token capacity · 1 token / 100 ms greedy refill (10 tokens/s)

## Load Profile

| Stage | Duration | VUs |
|-------|----------|-----|
| Ramp-up | 10 s | 0 → 30 |
| Steady | 30 s | 30 |
| Ramp-down | 10 s | 30 → 0 |

## Throughput

| Metric | Value |
|--------|-------|
| Total requests | 23 009 |
| Test duration | 50 s |
| Throughput | **460 req/s** |
| Checks passed | 100 % (46 018 / 46 018) |

## Latency

| Percentile | All requests | 200 OK only | 429 only |
|------------|-------------|-------------|----------|
| p50 | 1.61 ms | — | — |
| p90 | 2.72 ms | — | — |
| p95 | 3.22 ms | 3.82 ms | 3.15 ms |
| p99 | 4.55 ms | 4.98 ms | 4.38 ms |
| Max | 96.80 ms | 96.80 ms | 28.80 ms |

> 429s are ~0.67 ms faster at p95 than 200s — the Lua token-bucket script
> short-circuits once the bucket is empty, saving the application-logic path.

## Error / Rejection Rate

| Status | Count | % |
|--------|-------|---|
| 200 OK | 2 529 | 11 % |
| 429 Too Many Requests | 20 480 | **89 %** |
| 5xx / connection errors | 0 | 0 % |

89 % rejection is expected: 30 VUs share 5 keys → 6 VUs per key at ~92 req/s,
far above the 10 tokens/s replenishment rate.

## Chaos — Redis Outage

| Phase | Behaviour |
|-------|-----------|
| Redis stopped | HTTP 000 (no response) — Lettuce times out after 1 s |
| Circuit opens (after 6/10 failures) | HTTP 503 returned in < 1 ms |
| Redis restarted | First request → 200 OK — full recovery, no warm-up needed |

## Key Findings

- **p99 < 5 ms** under full load; Redis round-trip is not the bottleneck.
- **Greedy refill (1 token / 100 ms)** eliminates the burst-race seen with the
  previous batch refill (10 tokens / 1 s), which caused p99 outliers up to ~5 ms.
- **Resilience4j CircuitBreaker** (threshold 60 %, window 10) converts Redis
  timeout hangs into sub-millisecond 503 responses once the circuit opens.
- Single Redis is an SPOF; HA options: Redis Sentinel/Cluster, or in-process
  Bucket4j fallback while circuit is open.
