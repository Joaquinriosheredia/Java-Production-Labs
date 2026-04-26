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

## Expected Results (100K rows, 5% PENDING)

| Mode | Query Time | Plan |
|------|-----------|------|
| Sequential Scan | ~285ms | Seq Scan, 95K rows filtered |
| Partial Index Scan | ~12ms | Index Scan using idx_events_pending |
| **Speedup** | **~23×** | |

---

## PostgreSQL Tuning Checklist

- [ ] `EXPLAIN (ANALYZE, BUFFERS)` on all slow queries
- [ ] `pg_stat_statements` enabled (see docker-compose.yml)
- [ ] Partial indexes for selective WHERE clauses
- [ ] `VACUUM ANALYZE` after bulk loads
- [ ] Connection pool sized to `(CPU cores × 2) + disk spindles`
