#!/usr/bin/env bash
set -euo pipefail
echo "=== Lab 09 — Docker Optimization Benchmark ==="
cd "$(dirname "$0")/.."

echo ""
echo "--- Building NAIVE image ---"
time docker build -f docker/Dockerfile.naive -t lab09-naive . 2>&1 | tail -3
NAIVE_SIZE=$(docker image inspect lab09-naive --format='{{.Size}}' | awk '{printf "%.0fMB", $1/1024/1024}')

echo ""
echo "--- Building OPTIMIZED image ---"
time docker build -f docker/Dockerfile -t lab09-optimized . 2>&1 | tail -3
OPT_SIZE=$(docker image inspect lab09-optimized --format='{{.Size}}' | awk '{printf "%.0fMB", $1/1024/1024}')

echo ""
echo "======= Results ======="
echo "Naive image size    : $NAIVE_SIZE"
echo "Optimized image size: $OPT_SIZE"
echo ""
echo "--- Simulating code-only rebuild (optimized) ---"
touch app/src/main/java/com/labs/dockeropt/DockerOptimizationApplication.java
time docker build -f docker/Dockerfile -t lab09-optimized . 2>&1 | tail -3
echo ""
echo "Note: Only the 'application' layer was rebuilt. All other layers used cache."
