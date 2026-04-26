#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8085}"
MESSAGES="${MESSAGES:-1000}"
echo "=== Lab 06 — Redis vs Kafka Benchmark ($MESSAGES messages) ==="
echo ""
echo "--- Redis ---"
curl -s "$BASE_URL/api/v1/benchmark/redis?messages=$MESSAGES" | python3 -m json.tool 2>/dev/null || \
  curl -s "$BASE_URL/api/v1/benchmark/redis?messages=$MESSAGES"
echo ""
echo "--- Kafka ---"
curl -s "$BASE_URL/api/v1/benchmark/kafka?messages=$MESSAGES" | python3 -m json.tool 2>/dev/null || \
  curl -s "$BASE_URL/api/v1/benchmark/kafka?messages=$MESSAGES"
echo ""
echo "--- Comparison ---"
curl -s "$BASE_URL/api/v1/benchmark/compare?messages=$MESSAGES"
