#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"

echo "================================================"
echo " Lab 02 — Resilience Benchmark"
echo "================================================"

if ! command -v k6 &>/dev/null; then
  echo "ERROR: k6 is not installed."
  exit 1
fi

mkdir -p results

# Phase 1: healthy
echo "--- Phase 1: Healthy service (0% failure) ---"
curl -s -X POST "$BASE_URL/api/v1/admin/failure-rate?percent=0" > /dev/null
k6 run --env BASE_URL="$BASE_URL" --out json=results/healthy.json load-test.js

# Phase 2: critical failure — trigger circuit breaker
echo "--- Phase 2: Critical failure (80%) — observe circuit open ---"
curl -s -X POST "$BASE_URL/api/v1/admin/failure-rate?percent=80" > /dev/null
k6 run --env BASE_URL="$BASE_URL" --out json=results/critical.json load-test.js

# Reset
curl -s -X POST "$BASE_URL/api/v1/admin/failure-rate?percent=0" > /dev/null
echo "Done. See benchmark/results/"
