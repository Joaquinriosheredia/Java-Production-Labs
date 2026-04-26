# Benchmark — Lab 08: Kafka Streams

## Metric

**Throughput** (events/second processed) and **end-to-end latency** (event publish → metric output).

---

## Run

```bash
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run

bash benchmark/run-benchmark.sh
```

---

## Expected Results

| Messages | Rate | Processing Lag |
|----------|------|---------------|
| 10,000 | 5,000/s | <500ms |
| 100,000 | 10,000/s | <1s |

Throughput scales with Kafka partition count (add partitions → add stream threads).
