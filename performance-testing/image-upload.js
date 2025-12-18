import http from 'k6/http';
import { Trend } from 'k6/metrics';
import { sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.3/index.js';

// -------- Config via env --------
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_PATH = __ENV.API_PATH || '/media/filter';
const CONCURRENCY = Number(__ENV.CONCURRENCY || 8);

// choose which image to use: 256 | 1080 | 2160 | 4000
const IMAGE = __ENV.IMAGE || '256';

// local paths (relative to repo root)
const imageMap = {
  '256':  'images/256.jpg',
  '1080': 'images/1080.jpg',
  '2160': 'images/2160.jpg',
  '4000': 'images/4000.jpg',
};
const imagePath = imageMap[IMAGE];
if (!imagePath) {
  throw new Error(`Invalid IMAGE env: ${IMAGE}. Use one of 256|1080|2160|4000`);
}

const FILE_BYTES = open(imagePath, 'b');

// Custom metric that records only the MAIN phase
const mainLatency = new Trend('main_http_req_duration', true);

// -------- Scenarios --------
export const options = {
  discardResponseBodies: true,
  scenarios: {
    warmup: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [{ duration: '1m', target: CONCURRENCY }],
      gracefulRampDown: '0s',
      exec: 'uploadWarmup',
      tags: { phase: 'warmup' },
    },
    main: {
      executor: 'constant-vus',
      vus: CONCURRENCY,
      duration: '3m',
      gracefulStop: '0s',
      exec: 'uploadMain',
      tags: { phase: 'main' },
    },
  },
  summaryTrendStats: ['count','min','avg','med','p(90)','p(95)','p(99)','max'],
};

// ---------- Request ----------
function postImage() {
  const url = `${BASE_URL}${API_PATH}`;  
  const formData = {
    file: http.file(FILE_BYTES, `${IMAGE}.jpg`, 'image/jpeg'),
  };
  return http.post(url, formData);
}

// ---------- Execs ----------
export function uploadWarmup() {
  const res = postImage();
  if (res.timings.duration < 5) sleep(0.005);
}

export function uploadMain() {
  const res = postImage();
  mainLatency.add(res.timings.duration);
  if (res.timings.duration < 5) sleep(0.005);
}

// ---------- Summary: show MAIN phase only (plus k6 default output) ----------
export function handleSummary(data) {
  const mainOnly = {
    metrics: { main_http_req_duration: data.metrics.main_http_req_duration },
    root_group: data.root_group,
  };

  return {
    stdout: [
      '\n===== MAIN PHASE RESULTS (warmup excluded) =====\n',
      textSummary(mainOnly, { indent: '  ', enableColors: true }),
      '\n===============================================\n',
    ].join(''),
    'performance-testing/main_summary.json': JSON.stringify(mainOnly, null, 2),
  };
}