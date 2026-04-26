import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8089';
const latency = new Trend('request_latency', true);

export const options = {
  stages: [
    { duration: '30s', target: 20 },   // ramp-up: trigger HPA scale-up
    { duration: '60s', target: 20 },   // steady: sustain load
    { duration: '30s', target: 0  },   // ramp-down: trigger HPA scale-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/work?workMs=200`);
  latency.add(res.timings.duration);
  check(res, { 'status 200': (r) => r.status === 200 });
  sleep(0.1);
}
