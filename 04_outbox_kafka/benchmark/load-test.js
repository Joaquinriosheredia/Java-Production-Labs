import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8083';

export const options = {
  stages: [
    { duration: '10s', target: 20 },
    { duration: '30s', target: 20 },
    { duration: '10s', target: 0  },
  ],
};

export default function () {
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ customerId: `customer-${__VU}`, amount: 99.99 }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(res, { 'order created (201)': (r) => r.status === 201 });
  sleep(0.1);
}
