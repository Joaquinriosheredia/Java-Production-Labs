# Lab 01 — Virtual Threads (Project Loom)

## Problem
A high-traffic API handles hundreds of concurrent I/O-bound requests (DB queries, external service calls).
With a fixed platform thread pool, throughput is capped by pool size: beyond the limit, requests queue or timeout.
Scaling the pool increases memory and OS scheduler pressure.

**Can we achieve high concurrency with blocking, synchronous code?** Yes — with Java 21 Virtual Threads.

---

## Architecture

```mermaid
graph TD
    A[HTTP Request] -->|Tomcat| B{Thread Executor}
    B -->|Virtual Thread Executor| C[Virtual Thread #N]
    B -->|Fixed Pool Executor| D[Platform Thread Pool]
    C -->|sleep / I/O| E[JVM Carrier Thread freed]
    D -->|sleep / I/O| F[OS Thread blocked]
    E --> G[Other Virtual Threads run]
    F --> H[Pool exhausted → queue]
```

---

## Technical Decision

Virtual threads (Project Loom, Java 21) are JVM-managed threads that unmount from OS carrier threads when blocking on I/O. This allows massive concurrency with synchronous code — no reactive programming needed.

See [ADR-0001](docs/adr/ADR-0001.md) for full trade-off analysis.

---

## Stack

- Java 21 (Virtual Threads — stable)
- Spring Boot 3.2 (`spring.threads.virtual.enabled=true`)
- Micrometer + Prometheus metrics
- Spring Boot Actuator

---

## How to Run

```bash
# Option 1: Direct run
./mvnw spring-boot:run

# Option 2: With Docker
docker compose -f docker/docker-compose.yml up -d

# Verify virtual threads are active
curl http://localhost:8080/api/v1/threads/info
# → {"isVirtual": true, ...}
```

---

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/threads/virtual?tasks=200&latencyMs=100` | Run N tasks on virtual threads |
| GET | `/api/v1/threads/platform?tasks=200&latencyMs=100&poolSize=20` | Run N tasks on platform pool |
| GET | `/api/v1/threads/info` | Thread info for current request |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/prometheus` | Prometheus metrics |

---

## How to Break It (Failure Mode)

```bash
bash chaos/simulate-failure.sh
```

**What it does:** Floods both endpoints with 50 concurrent requests of 200 tasks each with 500ms latency.

**Expected result:**
- Platform threads (pool=20): requests serialize into batches → 5000ms+ response times
- Virtual threads: all complete near-simultaneously → ~500ms response times

---

## How to Measure

```bash
# Prerequisites: k6 installed
bash benchmark/run-benchmark.sh

# Custom scenario
TASKS=500 LATENCY_MS=100 POOL_SIZE=10 bash benchmark/run-benchmark.sh
```

---

## Results (Real benchmark — WSL2 Ubuntu, 16 CPUs, 15.57GB RAM)

3-run average, 200 VUs × 30s, JVM: `-Xms512m -Xmx512m`, Java 21, Spring Boot 3.2

| Metric | Virtual Threads | Platform (pool=20) | Δ |
|--------|----------------|--------------------|---|
| p50 latency | 103.8 ms | 1280.4 ms | +1134% |
| p95 latency | 113.6 ms | 1718.3 ms | +1413% |
| p99 latency | 167.5 ms | 5817.5 ms | +3372% |
| Throughput | 1057.9 req/s | 142.3 req/s | 7.4× |
| Error rate | 0.00% | 0.00% | — |

**Key insight:** With pool=20 and 200 concurrent VUs, platform threads batch requests into 10 rounds × 100ms = ~1000ms minimum. Under sustained load, p99 spiked to 13.9s (thread-pool exhaustion). Virtual threads park on I/O and immediately resume — wall-clock time ≈ single I/O latency regardless of concurrency.

Platform threads match virtual thread throughput only when pool size ≥ concurrent tasks — but 200 threads × ~1MB stack = ~200MB memory overhead, permanently reserved.

---

## Observability

- Grafana: http://localhost:3000
- Prometheus: http://localhost:9090
- Metrics: `lab_thread_duration_seconds{type="virtual"}` vs `{type="platform"}`
