# Lab 03 — Rate Limiter: Distributed Token Bucket

## Problem

A public API receives 10× normal traffic from a burst of clients.
Without rate limiting, the service is overwhelmed and degrades for all users.
An in-memory rate limiter breaks when multiple app instances run behind a load balancer.

**How do you enforce per-client rate limits consistently across a distributed system?**

---

## Architecture

```mermaid
graph LR
    C1[Client A] -->|X-API-Key: A| LB[Load Balancer]
    C2[Client B] -->|X-API-Key: B| LB
    LB --> App1[App Instance 1]
    LB --> App2[App Instance 2]
    App1 -->|GET bucket:A| Redis[(Redis)]
    App2 -->|GET bucket:A| Redis
    Redis -->|20 tokens, refill 10/s| App1
    App1 -->|429 if empty| C1
```

---

## How to Run

```bash
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run

# Test: first 20 requests pass, then 429
for i in $(seq 1 25); do
  echo -n "req-$i: "
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8082/api/v1/resource -H "X-API-Key: my-key"
done
```

---

## Rate Limit Policy

| Parameter | Value |
|-----------|-------|
| Bucket capacity (burst) | 20 tokens |
| Refill rate | 10 tokens/second |
| Strategy | Token bucket (greedy refill) |
| State store | Redis |
| Client key | `X-API-Key` header |

---

## How to Break It

```bash
bash chaos/simulate-failure.sh
```

Stops Redis to demonstrate fail-open behavior.

---

## How to Measure

```bash
bash benchmark/run-benchmark.sh
```

---

## Observability

```bash
curl http://localhost:8082/actuator/prometheus | grep lab_ratelimiter
```

Metrics: `lab_ratelimiter_requests_total{outcome="allowed|rejected"}`
