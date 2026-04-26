#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TASKS="${TASKS:-200}"
LATENCY_MS="${LATENCY_MS:-100}"
POOL_SIZE="${POOL_SIZE:-20}"

echo "============================================"
echo " Lab 01 — Virtual Threads Benchmark"
echo " Base URL  : $BASE_URL"
echo " Tasks     : $TASKS"
echo " Latency   : ${LATENCY_MS}ms per task"
echo "============================================"

if ! command -v k6 &>/dev/null; then
  echo "ERROR: k6 is not installed. Install from https://k6.io/docs/get-started/installation/"
  exit 1
fi

mkdir -p results

echo ""
echo "--- Phase 1: Virtual Threads ---"
k6 run \
  --env BASE_URL="$BASE_URL" \
  --env TASKS="$TASKS" \
  --env LATENCY_MS="$LATENCY_MS" \
  --env MODE="virtual" \
  --out json=results/virtual-threads.json \
  load-test.js

echo ""
echo "--- Phase 2: Platform Threads (pool=$POOL_SIZE) ---"
k6 run \
  --env BASE_URL="$BASE_URL" \
  --env TASKS="$TASKS" \
  --env LATENCY_MS="$LATENCY_MS" \
  --env MODE="platform" \
  --env POOL_SIZE="$POOL_SIZE" \
  --out json=results/platform-threads.json \
  load-test.js

echo ""
echo "Results saved to benchmark/results/"
echo "See benchmark/README.md for interpretation guide."
