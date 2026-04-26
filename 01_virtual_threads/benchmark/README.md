# Benchmark — Lab 01: Virtual Threads

## Metric

**Throughput (TPS)** and **total wall-clock duration** for N concurrent I/O-bound tasks.

---

## Setup

```bash
# Start the app
cd ..
docker compose -f docker/docker-compose.yml up -d
# or
./mvnw spring-boot:run

# Install k6 (if not installed)
# https://k6.io/docs/get-started/installation/
```

---

## Run

```bash
# Default: 200 tasks, 100ms simulated I/O latency, 50 concurrent users
bash run-benchmark.sh

# Custom params
TASKS=500 LATENCY_MS=50 POOL_SIZE=10 bash run-benchmark.sh
```

---

## What It Measures

| Scenario | Configuration |
|----------|--------------|
| Virtual Threads | `Executors.newVirtualThreadPerTaskExecutor()` |
| Platform Threads | `Executors.newFixedThreadPool(20)` |

Both scenarios run `N` tasks, each sleeping `latencyMs` to simulate blocking I/O.

---

## Expected Results

| Scenario | Tasks | Latency | Pool Size | Expected Wall Time |
|----------|-------|---------|-----------|-------------------|
| Virtual Threads | 200 | 100ms | N/A (unlimited) | ~100–150ms |
| Platform Threads | 200 | 100ms | 20 | ~1000–1100ms |

**Why?**
- Virtual threads: 200 tasks run truly concurrently (200 virtual threads, all parking during sleep)
- Platform threads (pool=20): tasks execute in 200/20 = 10 sequential batches × 100ms = ~1000ms

---

## Interpreting Results

Check `results/virtual-threads.json` and `results/platform-threads.json`.

Key metrics:
- `http_req_duration{p(95)}` — tail latency
- `throughputTps` in response body — tasks completed per second

---

## Failure Mode

Run the chaos script to observe platform thread starvation:

```bash
bash ../chaos/simulate-failure.sh
```
