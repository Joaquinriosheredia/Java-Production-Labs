#!/usr/bin/env bash
# Lab 03 — Chaos: Redis Outage
#
# Stops Redis to simulate a distributed rate limiter backend failure.
#
# Actual behaviour (fail-CLOSED):
#   When Redis is unreachable the Lettuce connection pool exhausts its 1s timeout
#   and the request hangs until the TCP connection is dropped — callers receive no
#   response (HTTP 000 / connection refused) rather than being silently allowed through.
#   The Resilience4j CircuitBreaker detects repeated failures, opens the circuit, and
#   subsequent calls fail immediately with HTTP 503 (<1 ms) instead of timing out.
#
# Expected observable effect:
#   - First N failures (sliding-window-size=10, threshold=60%): requests time out
#   - Circuit opens after threshold: requests return 503 instantly
#   - Redis restart: circuit transitions OPEN → HALF_OPEN → CLOSED automatically
#   - Metric: redis connection errors spike, then circuitbreaker.state transitions

set -euo pipefail

echo "================================================"
echo " Lab 03 — Chaos: Redis Outage"
echo "================================================"

echo "[1] Stopping Redis container..."
docker stop lab03-redis 2>/dev/null || docker stop labs-redis 2>/dev/null || echo "Redis container not found via docker"

echo ""
echo "[2] Firing 5 requests while Redis is down..."
BASE_URL="${BASE_URL:-http://localhost:8082}"
for i in 1 2 3 4 5; do
  result=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/resource" -H "X-API-Key: chaos-test")
  echo "  Request $i: HTTP $result"
done

echo ""
echo "[3] Restarting Redis..."
docker start lab03-redis 2>/dev/null || docker start labs-redis 2>/dev/null || echo "Restart Redis manually"

echo ""
echo "Recovery: wait a few seconds, then requests should succeed again."
echo "See logs: docker logs lab03-rate-limiter | grep -i redis"
