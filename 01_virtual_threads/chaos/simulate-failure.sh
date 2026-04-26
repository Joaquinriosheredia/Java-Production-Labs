#!/usr/bin/env bash
# Lab 01 — Chaos: Thread Pool Starvation
#
# Demonstrates what happens when all platform threads are occupied with
# long-running I/O tasks. New requests queue up and timeout.
# With virtual threads this scenario is a non-issue.
#
# Expected observable effect:
#   - Platform endpoint: HTTP 503 or very high latency (>10s)
#   - Virtual endpoint:  HTTP 200 with ~1s latency (100 tasks × 100ms / concurrency)
#   - Metric: jvm_threads_states_threads{state="blocked"} spikes on platform mode

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
CONCURRENCY="${CONCURRENCY:-50}"

echo "================================================"
echo " Lab 01 — Chaos: Thread Pool Starvation"
echo " Firing $CONCURRENCY concurrent requests..."
echo " Platform threads pool is capped at 20."
echo " Virtual threads have no cap."
echo "================================================"
echo ""
echo "Recovery: Ctrl+C or wait for requests to drain."
echo ""

echo "--- Flooding PLATFORM endpoint (pool=20, tasks=200, latency=500ms) ---"
echo "Sending $CONCURRENCY concurrent requests..."
for i in $(seq 1 $CONCURRENCY); do
  curl -s -o /dev/null -w "req-$i: HTTP %{http_code} in %{time_total}s\n" \
    "$BASE_URL/api/v1/threads/platform?tasks=200&latencyMs=500&poolSize=20" &
done
wait

echo ""
echo "--- Same load on VIRTUAL endpoint (unlimited, tasks=200, latency=500ms) ---"
for i in $(seq 1 $CONCURRENCY); do
  curl -s -o /dev/null -w "req-$i: HTTP %{http_code} in %{time_total}s\n" \
    "$BASE_URL/api/v1/threads/virtual?tasks=200&latencyMs=500" &
done
wait

echo ""
echo "Chaos scenario complete. Compare the response times above."
echo "Check metrics: curl $BASE_URL/actuator/prometheus | grep lab_thread_duration"
