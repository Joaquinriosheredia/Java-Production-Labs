#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8082}"
echo "=== Lab 03 — Rate Limiter Benchmark ==="
if ! command -v k6 &>/dev/null; then echo "ERROR: k6 required"; exit 1; fi
mkdir -p results
k6 run --env BASE_URL="$BASE_URL" --out json=results/rate-limiter.json load-test.js
echo "Results in benchmark/results/"
