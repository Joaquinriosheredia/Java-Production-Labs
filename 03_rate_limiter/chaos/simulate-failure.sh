#!/usr/bin/env bash
# Lab 03 — Chaos: Redis Outage
#
# Stops Redis to simulate a distributed rate limiter backend failure.
# Demonstrates the "fail open vs fail closed" decision:
# Current implementation: fail open (allow all requests when Redis is down)
# Alternative: fail closed (reject all when Redis is down)
#
# Expected observable effect:
#   - Redis down: requests may succeed (fail-open) or fail 500 (fail-closed)
#   - Metric: redis connection errors spike

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
