import http from 'k6/http';
import { Trend } from 'k6/metrics';
import { sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.3/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9091';
const API_PATH = __ENV.API_PATH || '/media/';
const CONCURRENCY = Number(__ENV.CONCURRENCY || 8);

const mainLatency = new Trend('main_http_req_duration', true);

export const options = {
  discardResponseBodies: false,
  scenarios: {
    warmup: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [{ duration: '1m', target: CONCURRENCY }],
      gracefulRampDown: '0s',
      exec: 'getWarmup',
      tags: { phase: 'warmup' },
    },
    main: {
      executor: 'constant-vus',
      vus: CONCURRENCY,
      duration: '3m',
      gracefulStop: '0s',
      exec: 'getMain',
      tags: { phase: 'main' },
    },
  },
  summaryTrendStats: ['count','min','avg','med','p(90)','p(95)','p(99)','max'],
};

function getMedia() {
  const url = `${BASE_URL}${API_PATH}`;
  const params = { headers: { Connection: 'keep-alive' } };
  return http.get(url, params);
}

export function getWarmup() {
  const res = getMedia();
  if (res.timings.duration < 5) sleep(0.005);
}

export function getMain() {
  const res = getMedia();
  mainLatency.add(res.timings.duration);
  if (res.timings.duration < 5) sleep(0.005);
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
