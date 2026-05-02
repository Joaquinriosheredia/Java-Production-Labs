/**
 * Lab 02 — Resilience4j Full Benchmark
 * 200 VUs, 60s, with CB state tracking via fallback detection
 *
 * Failure injection is done externally (see run-full-benchmark.sh):
 *   t=0s  → 0% failure  (CLOSED baseline)
 *   t=20s → 80% failure (trigger OPEN)
 *   t=45s → 0% failure  (HALF_OPEN / recovery)
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// Custom metrics
const errorRate        = new Rate('error_rate');
const fallbackRate     = new Rate('fallback_rate');
const cbOpenRate       = new Rate('cb_open_rate');   // response arrived via CB open fallback
const latency          = new Trend('req_latency_ms', true);
const successCount     = new Counter('success_total');
const fallbackCount    = new Counter('fallback_total');

export const options = {
  stages: [
    { duration: '5s',  target: 50  },   // ramp-up
    { duration: '15s', target: 200 },   // CLOSED baseline at 200 VUs
    { duration: '20s', target: 200 },   // failure injected → CB OPEN
    { duration: '15s', target: 200 },   // recovery → HALF_OPEN → CLOSED
    { duration: '5s',  target: 0   },   // ramp-down
  ],
  thresholds: {
    // We allow high error rate during fault injection — just track it
    error_rate:          ['rate<1.0'],
    req_latency_ms:      ['p(99)<3000'],
    'http_req_duration': ['p(95)<3000'],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/call?requestId=k6-${__VU}-${__ITER}`, {
    timeout: '3s',
    tags: { name: 'resilience_call' },
  });

  const duration = res.timings.duration;
  latency.add(duration);
  errorRate.add(res.status !== 200);

  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      const isFallback = body.fallback === true;
      fallbackRate.add(isFallback);
      cbOpenRate.add(isFallback);

      if (isFallback) {
        fallbackCount.add(1);
      } else {
        successCount.add(1);
      }
    } catch (_) {
      fallbackRate.add(0);
    }
  }

  check(res, {
    'status 200':              (r) => r.status === 200,
    'latency < 500ms (CLOSED)': (r) => r.timings.duration < 500,
  });

  sleep(0.05);  // ~20 rps per VU
}

export function handleSummary(data) {
  const m = data.metrics;

  function val(metric, stat) {
    try { return metric.values[stat] ?? 'N/A'; } catch(_) { return 'N/A'; }
  }

  const summary = {
    timestamp:         new Date().toISOString(),
    duration_s:        60,
    vus_max:           200,
    http_reqs_total:   val(m['http_reqs'], 'count'),
    error_rate_pct:    (val(m['error_rate'], 'rate') * 100).toFixed(2),
    fallback_rate_pct: (val(m['fallback_rate'], 'rate') * 100).toFixed(2),
    latency_p50_ms:    val(m['req_latency_ms'], 'p(50)'),
    latency_p90_ms:    val(m['req_latency_ms'], 'p(90)'),
    latency_p99_ms:    val(m['req_latency_ms'], 'p(99)'),
    latency_max_ms:    val(m['req_latency_ms'], 'max'),
    throughput_rps:    val(m['http_reqs'], 'rate'),
    success_total:     val(m['success_total'], 'count'),
    fallback_total:    val(m['fallback_total'], 'count'),
  };

  return {
    '/home/usuariojoaquin/Java-Production-Labs/02_resilience/benchmark/results/k6-summary.json':
      JSON.stringify(summary, null, 2),
    stdout: JSON.stringify(summary, null, 2) + '\n',
  };
}
