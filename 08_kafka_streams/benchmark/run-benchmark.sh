#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8087}"
MESSAGES="${MESSAGES:-1000}"
echo "=== Lab 08 — Kafka Streams Benchmark ==="
echo "Publishing $MESSAGES orders..."
START=$(date +%s%3N)
for i in $(seq 1 $MESSAGES); do
  curl -s -o /dev/null "$BASE_URL/api/v1/streams/orders?userId=user-$((i % 10))&status=COMPLETED&amount=$((RANDOM % 100 + 1)).00" &
  [ $((i % 50)) -eq 0 ] && wait
done
wait
END=$(date +%s%3N)
ELAPSED=$((END - START))
echo "Published $MESSAGES messages in ${ELAPSED}ms"
echo "Stream status: $(curl -s $BASE_URL/api/v1/streams/status)"
