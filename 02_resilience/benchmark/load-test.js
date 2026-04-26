import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const errorRate = new Rate('error_rate');
const fallbackRate = new Rate('fallback_rate');
const latency = new Trend('request_latency', true);

export const options = {
  stages: [
    { duration: '5s',  target: 20 },
    { duration: '20s', target: 20 },
    { duration: '5s',  target: 0  },
  ],
  thresholds: {
    error_rate: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/call?requestId=k6-${__VU}-${__ITER}`);
  latency.add(res.timings.duration);
  errorRate.add(res.status !== 200);

  if (res.status === 200) {
    const body = JSON.parse(res.body);
    fallbackRate.add(body.fallback === true);
  }

  check(res, { 'status 200': (r) => r.status === 200 });
  sleep(0.1);
}
