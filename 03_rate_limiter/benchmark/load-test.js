import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const rejectionRate = new Rate('rejection_rate');

export const options = {
  stages: [
    { duration: '10s', target: 30 },
    { duration: '30s', target: 30 },
    { duration: '10s', target: 0  },
  ],
};

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/resource`, {
    headers: { 'X-API-Key': `k6-client-${__VU % 5}` }, // 5 shared keys
  });
  rejectionRate.add(res.status === 429);
  check(res, {
    'status 200 or 429': (r) => r.status === 200 || r.status === 429,
    'has rate limit header': (r) => r.headers['X-Ratelimit-Remaining'] !== undefined,
  });
  sleep(0.05);
}
