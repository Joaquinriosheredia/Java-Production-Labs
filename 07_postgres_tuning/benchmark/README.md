# Benchmark — Lab 07: PostgreSQL Tuning

## Metric

**Query execution time** with and without partial index on 100K–1M row table.

---

## Run

```bash
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run

# Seed 100K rows
curl -X POST "http://localhost:8086/api/v1/postgres/seed?rows=100000"

# Compare
curl "http://localhost:8086/api/v1/postgres/compare?limit=100"

# EXPLAIN ANALYZE
curl "http://localhost:8086/api/v1/postgres/explain?query=pending_no_index"
```

---

## Results (100K rows, 5% PENDING)

`bash benchmark/run-benchmark.sh` on a fresh database seeds the rows, runs `ANALYZE`, saves `EXPLAIN (ANALYZE, BUFFERS)` of both modes, and fails unless the index mode is an Index Scan on `idx_events_pending` and the table scan counters show one seq scan and one index scan per run. It writes [`results/summary.md`](results/summary.md) (median and range of 10 runs) and the raw data to `results/raw/`.
The script runs `psql` in the `lab07-postgres` container (`PG_CONTAINER` to override).
Last run (2026-10-03): seq scan 7.45 ms, partial index 1.52 ms, **4.9×**.

---

## PostgreSQL Tuning Checklist

- [ ] `EXPLAIN (ANALYZE, BUFFERS)` on all slow queries
- [ ] `pg_stat_statements` enabled (see docker-compose.yml)
- [ ] Partial indexes for selective WHERE clauses
- [ ] `VACUUM ANALYZE` after bulk loads
- [ ] Connection pool sized to `(CPU cores × 2) + disk spindles`
