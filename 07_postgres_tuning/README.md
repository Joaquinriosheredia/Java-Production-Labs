# Lab 07 — PostgreSQL Tuning

## Problem

A background poller queries `SELECT * FROM events WHERE status = 'PENDING'` every second.
With 1M rows and only 5% PENDING, PostgreSQL scans 950K rows to return 50K.
Every query does a full table scan. Under load this causes CPU spikes and slow responses.

**How do you optimize a selective query on a large table?**

---

## Architecture

```mermaid
graph LR
    A[Poller every 1s] -->|WHERE status=PENDING| B[Seq Scan\n950K rows filtered]
    A -->|WHERE status=PENDING| C[Partial Index Scan\nidx_events_pending]
    B --> D[Result: first 100 PENDING rows]
    C --> D
```

---

## Key Technique: Partial Index

```sql
-- Only indexes PENDING rows (~5% of table)
-- Stays small as events are processed
CREATE INDEX idx_events_pending ON events(occurred_at ASC)
    WHERE status = 'PENDING';
```

Measured on 100K rows (5% PENDING): 7.45 ms with a sequential scan vs 1.52 ms with an Index Scan on the partial index, **4.9×** (medians of 10 runs) — [`benchmark/results/summary.md`](benchmark/results/summary.md).

---

## How to Run

```bash
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run

# Seed 100K rows
curl -X POST "http://localhost:8086/api/v1/postgres/seed?rows=100000"

# Compare
curl "http://localhost:8086/api/v1/postgres/compare"

# EXPLAIN ANALYZE
curl "http://localhost:8086/api/v1/postgres/explain?query=pending_no_index"
```

---

## How to Break It

```bash
bash chaos/simulate-failure.sh
```

Drops the partial index to simulate a missing migration. Shows seq scan regression.

---

## Observability

```sql
-- pg_stat_statements (requires shared_preload_libraries=pg_stat_statements)
SELECT query, calls, mean_exec_time, total_exec_time
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 10;
```

See [ADR-0001](docs/adr/ADR-0001.md) for full EXPLAIN ANALYZE before/after.
