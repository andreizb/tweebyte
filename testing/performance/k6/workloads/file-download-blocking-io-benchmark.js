// Blocking file-download benchmark.
// This represents the blocking-I/O workload shape kept for comparison.

import http from 'k6/http';
import { Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';
import { sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9091';
const API_PATH = __ENV.API_PATH || '/media/';
const CONCURRENCY = Number(__ENV.CONCURRENCY || 8);
const WARMUP_SECS = __ENV.WARMUP_SECS || '60s';
const DURATION = __ENV.DURATION || '3m';

const mainLatency = new Trend('main_http_req_duration', true);
const mainHttpReqs = new Counter('main_http_reqs');
const mainHttpErrors = new Counter('main_http_errors');

export const options = {
  discardResponseBodies: false,
  systemTags: ['status', 'method', 'name', 'group', 'scenario', 'check', 'error'],
  scenarios: {
    warmup: {
      executor: 'constant-vus',
      vus: CONCURRENCY,
      duration: WARMUP_SECS,
      exec: 'getMedia',
      tags: { phase: 'warmup' },
      gracefulStop: '0s',
    },
    main: {
      executor: 'constant-vus',
      vus: CONCURRENCY,
      duration: DURATION,
      startTime: WARMUP_SECS,
      exec: 'getMedia',
      tags: { phase: 'main' },
      gracefulStop: '0s',
    },
  },
  summaryTrendStats: ['count', 'min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function getMedia() {
  const isMain = exec.scenario.name === 'main';
  const url = `${BASE_URL}${API_PATH}`;
  const res = http.get(url, {
    headers: { Connection: 'keep-alive' },
    tags: { name: API_PATH },
  });

  if (isMain) {
    mainLatency.add(res.timings.duration);
    mainHttpReqs.add(1);
    if (res.status >= 400 || res.error) {
      mainHttpErrors.add(1);
    }
  }

  if (res.timings.duration < 5) sleep(0.005);
}
