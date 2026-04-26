import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TASKS = parseInt(__ENV.TASKS || '200');
const LATENCY_MS = parseInt(__ENV.LATENCY_MS || '100');
const MODE = __ENV.MODE || 'virtual';
const POOL_SIZE = parseInt(__ENV.POOL_SIZE || '20');

const requestDuration = new Trend('request_duration', true);
const errorRate = new Rate('error_rate');

export const options = {
  stages: [
    { duration: '10s', target: 50 },   // ramp-up
    { duration: '30s', target: 50 },   // steady state
    { duration: '10s', target: 0 },    // ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<5000'],
    error_rate: ['rate<0.01'],
  },
};

export default function () {
  let url;
  if (MODE === 'virtual') {
    url = `${BASE_URL}/api/v1/threads/virtual?tasks=${TASKS}&latencyMs=${LATENCY_MS}`;
  } else {
    url = `${BASE_URL}/api/v1/threads/platform?tasks=${TASKS}&latencyMs=${LATENCY_MS}&poolSize=${POOL_SIZE}`;
  }

  const res = http.get(url, { timeout: '30s' });

  requestDuration.add(res.timings.duration);
  errorRate.add(res.status !== 200);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has throughputTps': (r) => {
      const body = JSON.parse(r.body);
      return body.throughputTps > 0;
    },
  });

  sleep(0.1);
}
