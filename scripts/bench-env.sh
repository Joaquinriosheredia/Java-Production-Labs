#!/usr/bin/env bash
# Writes the benchmark environment (date, commit, hardware, tool versions) to $1.
# Sourced by the labs' run-benchmark.sh so every result file records where it came from.
set -euo pipefail
out="$1"
{
  echo "date_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "commit=$(git rev-parse HEAD 2>/dev/null || echo unknown)"
  echo "worktree_dirty=$( [ -n "$(git status --porcelain -- . 2>/dev/null)" ] && echo yes || echo no)"
  echo "os=$(uname -sr)"
  echo "cpu=$(grep -m1 'model name' /proc/cpuinfo 2>/dev/null | cut -d: -f2 | sed 's/^ //' || echo unknown)"
  echo "cpus=$(nproc 2>/dev/null || echo unknown)"
  echo "mem_total=$(free -h 2>/dev/null | awk '/^Mem:/{print $2}' || echo unknown)"
  echo "java=$(java -version 2>&1 | head -1 || echo none)"
  echo "docker=$(docker version --format '{{.Server.Version}}' 2>/dev/null || echo none)"
  echo "k6=$(k6 version 2>/dev/null | head -1 || echo none)"
} > "$out"
