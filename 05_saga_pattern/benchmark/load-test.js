import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8084';

export const options = {
  stages: [
    { duration: '10s', target: 10 },
    { duration: '30s', target: 10 },
    { duration: '10s', target: 0  },
  ],
};

export default function () {
  const res = http.post(
    `${BASE_URL}/api/v1/saga/orders`,
    JSON.stringify({ customerId: `customer-${__VU}`, amount: 49.99 }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(res, { 'saga started (202)': (r) => r.status === 202 });
  sleep(0.5);
}
