// Gateway image filter benchmark.
// This is the CPU-bound workload that uploads an image for processing.

import http from 'k6/http';
import { Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';
import { sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_PATH = __ENV.API_PATH || '/media/filter';
const CONCURRENCY = Number(__ENV.CONCURRENCY || 8);
const WARMUP_SECS = __ENV.WARMUP_SECS || '60s';
const DURATION = __ENV.DURATION || '3m';
const PAYLOAD_FILE = __ENV.PAYLOAD_FILE;

if (!PAYLOAD_FILE) {
  throw new Error('PAYLOAD_FILE is required for image-upload-cpu-bound-benchmark.js');
}

const FILE_BYTES = open(PAYLOAD_FILE, 'b');
const fileName = PAYLOAD_FILE.split('/').pop() || 'payload.jpg';
const ext = fileName.split('.').pop()?.toLowerCase() || 'jpg';
const mimeType = ext === 'png' ? 'image/png' : 'image/jpeg';

const mainLatency = new Trend('main_http_req_duration', true);
const mainHttpReqs = new Counter('main_http_reqs');
const mainHttpErrors = new Counter('main_http_errors');

export const options = {
  discardResponseBodies: true,
  systemTags: ['status', 'method', 'name', 'group', 'scenario', 'check', 'error'],
  scenarios: {
    warmup: {
      executor: 'constant-vus',
      vus: CONCURRENCY,
      duration: WARMUP_SECS,
      exec: 'uploadImage',
      tags: { phase: 'warmup' },
      gracefulStop: '0s',
    },
    main: {
      executor: 'constant-vus',
      vus: CONCURRENCY,
      duration: DURATION,
      startTime: WARMUP_SECS,
      exec: 'uploadImage',
      tags: { phase: 'main' },
      gracefulStop: '0s',
    },
  },
  summaryTrendStats: ['count', 'min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function uploadImage() {
  const isMain = exec.scenario.name === 'main';
  const url = `${BASE_URL}${API_PATH}`;
  const res = http.post(url, {
    file: http.file(FILE_BYTES, fileName, mimeType),
  }, {
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
