#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8084}"
echo "=== Lab 05 — Saga Benchmark ==="
if ! command -v k6 &>/dev/null; then echo "ERROR: k6 required"; exit 1; fi
mkdir -p results
k6 run --env BASE_URL="$BASE_URL" --out json=results/saga.json load-test.js
echo "Saga stats: $(curl -s $BASE_URL/api/v1/saga/stats)"
