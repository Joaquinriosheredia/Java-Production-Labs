#!/usr/bin/env python3
"""Median and range of one metric across benchmark runs.

Usage: bench_stats.py <json-path-in-file> <file>...
  The path is dot-separated, e.g. metrics.http_reqs.rate
Prints: median min max n
"""
import json
import statistics
import sys


def get(doc, path):
    for key in path.split("."):
        doc = doc[key]
    return float(doc)


path, files = sys.argv[1], sys.argv[2:]
values = [get(json.load(open(f)), path) for f in files]
print(f"{statistics.median(values):.2f} {min(values):.2f} {max(values):.2f} {len(values)}")
