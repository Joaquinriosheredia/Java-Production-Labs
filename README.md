# Java Production Labs

**Clone it, run it, demo it in 45 minutes.**

Ten real, self-contained Spring Boot 3 + Java 21 labs that demonstrate
production engineering decisions — concurrency, resilience, messaging, persistence, and deployment.
Each lab is runnable from cold start, measurable under load, and has a documented failure mode.

---

## CI Status

![CI](https://github.com/Joaquinriosheredia/Java-Production-Labs/actions/workflows/ci.yml/badge.svg)

---

## Stack

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?logo=spring)
![Docker](https://img.shields.io/badge/Docker-24+-blue?logo=docker)
![Testcontainers](https://img.shields.io/badge/Testcontainers-ready-blue)
![k6](https://img.shields.io/badge/k6-load--testing-purple)
![Prometheus](https://img.shields.io/badge/Prometheus-monitoring-orange)
![Grafana](https://img.shields.io/badge/Grafana-dashboards-yellow)

---

## Quick Start

```bash
git clone https://github.com/Joaquinriosheredia/Java-Production-Labs
cd Java-Production-Labs/01_virtual_threads
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run
# Verify: http://localhost:8080/api/v1/threads/info
```

See `make help` for all available commands.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21 | SDKMAN: `sdk install java 21-tem` |
| Maven | 3.9+ | Included via `./mvnw` wrapper |
| Docker | 24+ | With Compose v2 |
| k6 | latest | For benchmark scripts |
| Make | any | Optional — convenience wrapper |

---

## Labs

| # | Lab | Core Concept | Port | Status | Benchmark real |
|---|-----|-------------|------|--------|:--------------:|
| 01 | [Virtual Threads](01_virtual_threads/) | Concurrency with Project Loom | 8080 | ✅ | ✅ |
| 02 | [Resilience](02_resilience/) | Circuit Breaker, Retry, Bulkhead | 8081 | ✅ | ✅ |
| 03 | [Rate Limiter](03_rate_limiter/) | Distributed token bucket (Redis) | 8082 | ✅ | ✅ |
| 04 | [Transactional Outbox](04_outbox_kafka/) | At-least-once event delivery | 8083 | ✅ | ✅ |
| 05 | [Saga Pattern](05_saga_pattern/) | Distributed transactions + compensation | 8084 | ✅ | ✅ |
| 06 | [Redis vs Kafka](06_redis_vs_kafka/) | Messaging trade-off benchmark | 8085 | ✅ | ✅ |
| 07 | [PostgreSQL Tuning](07_postgres_tuning/) | Partial indexes, EXPLAIN ANALYZE | 8086 | ✅ | ✅ |
| 08 | [Kafka Streams](08_kafka_streams/) | Real-time windowed aggregation | 8087 | ✅ | ✅ |
| 09 | [Docker Optimization](09_docker_optimization/) | Layered JARs, 62% smaller images | 8088 | ✅ | ✅ |
| 10 | [Kubernetes Autoscaling](10_kubernetes_autoscaling/) | HPA on custom Prometheus metrics | 8089 | ✅ | ✅ |

---

## Quality Matrix

Every lab ships all of these:

| Lab | ADR | Tests | Testcontainers | Benchmark | Metrics | Chaos |
|-----|-----|-------|----------------|-----------|---------|-------|
| 01 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 02 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 03 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 04 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 05 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 06 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 07 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 08 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 09 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 10 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## Real Benchmark Results

Executed on local hardware: WSL2 Ubuntu, 16 CPUs, 15.57 GB RAM, Docker in-process.
Full methodology and raw data in each lab's `benchmark/results/summary.md`.

### Lab 01 — Virtual Threads

| Metric | Platform Threads | Virtual Threads |
|--------|:----------------:|:---------------:|
| Throughput | baseline | **7.4× higher** |
| p99 latency | 5,817 ms | **167 ms** |

Virtual threads eliminate the pool-size bottleneck under I/O-bound concurrency.
No reactive programming required.

### Lab 02 — Resilience4j

| Metric | Value |
|--------|-------|
| Circuit OPEN duration | 27 s |
| Calls blocked while OPEN | 90,680 |
| Recovery (HALF_OPEN → CLOSED) | automatic, < 5 s |

Bulkhead + CircuitBreaker + Retry chain measured end-to-end under injected failure rate.

### Lab 03 — Redis Rate Limiter

| Metric | Value |
|--------|-------|
| Sustained throughput | 460 req/s |
| p99 latency | 4.55 ms |
| Redis outage response | HTTP 503 in < 1 ms |

Distributed token bucket — correct under multiple app instances sharing the same Redis.

### Lab 04 — Transactional Outbox

| Metric | Value |
|--------|-------|
| Throughput | 163.8 req/s |
| Data loss under Kafka kill | zero |
| Drain rate improvement (tuned poller) | +70% |

A silent data-loss bug (order created, event never enqueued) was found and fixed via chaos testing.
The outbox pattern prevents the dual-write race condition at the DB transaction boundary.

### Lab 05 — Saga Pattern

| Metric | Value |
|--------|-------|
| Orders stuck in `STARTED` | 71.8% |
| Race condition demonstrated | dual-write without saga |
| HTTP response during failure | 200 OK (silent failure) |

Choreography-based saga over Kafka. The benchmark intentionally demonstrates the failure mode —
services appearing healthy while distributed state is inconsistent.

### Lab 06 — Redis Pub/Sub vs Kafka

| Metric | Redis Pub/Sub | Kafka |
|--------|:-------------:|:-----:|
| Throughput (default API) | 2,227 msg/s | 47,619 msg/s |
| Messages lost on broker crash | **200 / 200** | 0 committed |
| Recovery after restart | impossible | 100% (full replay) |

The throughput gap reflects an API asymmetry: Redis `convertAndSend()` blocks per-message
for a full TCP round-trip (~419 µs); Kafka `send()` enqueues to an in-memory buffer (~7 µs)
and flushes asynchronously. In sync-equivalent mode, Redis is 4–10× faster than Kafka.
Redis wins on latency (< 1 ms end-to-end); Kafka wins on durability guarantees.

### Lab 07 — PostgreSQL Optimization

| Metric | Before | After |
|--------|:------:|:-----:|
| Query latency | 285 ms | 12 ms |
| Speedup | — | **26×** |
| Technique | sequential scan | partial index |

100K-row table, 5% PENDING rows. Partial index on `(status)` WHERE `status = 'PENDING'`
eliminates the full table scan. `EXPLAIN (ANALYZE, BUFFERS)` output in ADR-0001.

### Lab 08 — Kafka Streams

| Metric | Baseline | Post-Recovery |
|--------|:--------:|:-------------:|
| Throughput | **46.8 rps** | **109.7 rps** |
| p99 latency | **4 ms** | **4 ms** |
| Error rate | **0%** | **0%** |
| Consumer lag | **0** | **0** |
| Recovery time | — | **< 200 ms** (changelog replay) |

Critical finding: `actuator/health` reports `UP` and Streams state shows `RUNNING` even when Kafka is unreachable — standard health probes are false positives.
During Kafka outage: `kafkaTemplate.send().get()` blocks HTTP threads synchronously; 60% request failure rate at 10 s timeout.
`at_least_once` guarantee with no duplicates on clean restart; lag=0 immediately after broker recovery.

### Lab 09 — Docker Optimization

| Metric | Naive Image | Optimized Image |
|--------|:-----------:|:---------------:|
| Tamaño imagen | 233 MB | **79 MB** (−66.1%) |
| Startup | 2917 ms | **2445 ms** (−472 ms) |
| Rebuild en CI con BuildKit | 10 s | **3-5 s** (4-8× speedup) |
| Seguridad | root | **nonroot (65532)** |

- **Memoria**: ZGC pre-reserva heap correctamente para containers (comportamiento esperado).

### Lab 10 — Kubernetes HPA

- CPU con 50 VUs: 110m (threshold: 350m) — HPA nunca disparó
- 0% error rate con 1 solo pod durante toda la prueba
- Antipattern confirmado: CPU-based HPA es inservible con Virtual Threads
- Recovery real: 25s hasta readinessProbe (no 5s hasta Running)
- Solución: custom metrics HPA con lab_active_requests_gauge

---

## What Each Lab Demonstrates

### 01 · Virtual Threads
Java 21 Project Loom in production. 7.4× throughput improvement measured under I/O-bound load.
p99 latency drops from 5,817 ms (platform thread pool) to 167 ms (virtual threads).
No reactive programming needed.

### 02 · Resilience
Resilience4j 2.x: composable `Bulkhead → CircuitBreaker → Retry`.
Inject failure rate via REST API, watch circuit transition `CLOSED → OPEN → HALF_OPEN → CLOSED`.
All state visible in `/actuator/health` and Prometheus.

### 03 · Rate Limiter
Distributed token bucket backed by Redis. Each API client gets an isolated bucket.
Works correctly with multiple app instances behind a load balancer.
Returns `Retry-After` and `X-RateLimit-Remaining` headers.

### 04 · Transactional Outbox
Order + outbox event written in one DB transaction.
Kill Kafka mid-flight: orders are created without errors, events queue in the outbox.
When Kafka recovers the poller drains the backlog — zero data loss.

### 05 · Saga Pattern
Choreography-based saga over Kafka topics.
Inject inventory failure: watch `STARTED → PAYMENT_APPROVED → INVENTORY_FAILED → COMPENSATED`.
Full state machine visible in a single DB query.

### 06 · Redis vs Kafka
Side-by-side benchmark: Redis Pub/Sub (2,227 msg/s, ephemeral) vs Kafka (47,619 msg/s async, durable).
100% message loss in Redis during broker crash; Kafka recovers with full replay from committed offsets.
The throughput gap is an API asymmetry, not a speed claim — see benchmark results for full analysis.

### 07 · PostgreSQL Tuning
100K rows, 5% PENDING. Sequential scan: 285ms. Partial index: 12ms. 26× improvement.
`EXPLAIN (ANALYZE, BUFFERS)` output before and after included in ADR-0001.

### 08 · Kafka Streams
Tumbling 60-second window counting orders per user. 46.8 rps baseline, p99=4ms, lag=0.
Kafka broker killed mid-load: 60% error rate, health check false-positive (reports UP while Kafka is down).
Recovery to RUNNING in < 200ms via changelog topic replay — no duplicates, lag=0 immediately after restart.

### 09 · Docker Optimization
Naive image: 520MB, 3-minute rebuilds, runs as root, ignores container memory limits.
Optimized image: 195MB, 15-second code-only rebuilds, non-root, `-XX:MaxRAMPercentage=75.0`.

### 10 · Kubernetes Autoscaling
HPA v2 on custom Prometheus metric `lab_active_requests_gauge`.
CPU stays low under virtual threads — CPU-only HPA would never trigger.
Graceful shutdown: in-flight requests complete before pod terminates.

---

## Running All Tests

```bash
make test-all
```

Or per lab:
```bash
cd 01_virtual_threads && ./mvnw verify
```

Tests use Testcontainers — no mocks for infrastructure (PostgreSQL, Redis, Kafka).

---

## Observability

All labs expose:
- `/actuator/health` — liveness + readiness probes
- `/actuator/prometheus` — Prometheus metrics scrape endpoint
- `/actuator/metrics` — Spring metrics

Shared Grafana + Prometheus stack:
```bash
docker compose up -d  # starts shared prometheus + grafana
# Grafana: http://localhost:3000  (admin/admin)
# Prometheus: http://localhost:9090
```

Lab 01 includes a pre-built Grafana dashboard: `01_virtual_threads/docker/grafana/dashboards/lab01-virtual-threads.json`.

---

## For Recruiters / Technical Evaluators

This repository is designed to be evaluated, not just read:

| Signal | Where to look |
|--------|--------------|
| Technical decisions with trade-offs | `<lab>/docs/adr/ADR-0001.md` |
| Production-grade tests | `<lab>/app/src/test/` |
| Real infrastructure in tests | Testcontainers — no mocks |
| Measurable performance claims | `<lab>/benchmark/README.md` |
| Failure scenarios | `<lab>/chaos/simulate-failure.sh` |
| CI pipeline | `.github/workflows/ci.yml` |

---

## How this was built

This repository was developed with AI assistance (Claude) for scaffolding, code generation, and test setup. All labs have been reviewed, compiled, tested locally, and benchmarked by the author.

The benchmark results are real — executed on local hardware (WSL2 Ubuntu, 16 CPUs, 15.57GB RAM). Every architectural decision is documented in the ADRs and defensible in a technical interview.

---

## License

MIT
