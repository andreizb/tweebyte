// Redis-backed GET /follows/{id}/following benchmark.
// This is the modern I/O-bound cache workload for interaction-service.

import http from 'k6/http';
import { Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';
import { sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9093';
const PATH_PREFIX = __ENV.PATH_PREFIX || '/follows';
let TOTAL_KEYS = Number(__ENV.TOTAL_KEYS || 10000);
const HOT_COUNT = Number(__ENV.HOT_COUNT || 1000);
const HOT_RATIO = Number(__ENV.HOT_RATIO || 0.90);
const CONCURRENCY = Number(__ENV.CONCURRENCY || 32);
const WARMUP_SECS = __ENV.WARMUP_SECS || '60s';
const DURATION = __ENV.DURATION || '3m';
const KEYS_FILE = __ENV.KEYS_FILE;

const httpDuration = new Trend('get_http_req_duration', true);
const hitsHot = new Counter('keys_hot_requests');
const hitsCold = new Counter('keys_cold_requests');
const mainHttpDuration = new Trend('main_http_req_duration', true);
const mainHitsHot = new Counter('main_keys_hot_requests');
const mainHitsCold = new Counter('main_keys_cold_requests');
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
      exec: 'hit',
      tags: { phase: 'warmup' },
      gracefulStop: '0s',
    },
    main: {
      executor: 'constant-vus',
      vus: CONCURRENCY,
      duration: DURATION,
      startTime: WARMUP_SECS,
      exec: 'hit',
      tags: { phase: 'main' },
      gracefulStop: '0s',
    },
  },
  summaryTrendStats: ['count', 'min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

let ALL_KEYS;
if (KEYS_FILE) {
  const raw = open(KEYS_FILE).split(/\r?\n/).filter(Boolean);

  if (raw.length < TOTAL_KEYS) {
    console.warn(`KEYS_FILE has only ${raw.length} lines, adjusting TOTAL_KEYS from ${TOTAL_KEYS} to ${raw.length}`);
    TOTAL_KEYS = raw.length;
  }
  ALL_KEYS = raw.slice(0, TOTAL_KEYS);
} else {
  function uuidv4() {
    const bytes = new Uint8Array(16);
    for (let i = 0; i < 16; i++) bytes[i] = Math.floor(Math.random() * 256);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
    return `${hex.substr(0, 8)}-${hex.substr(8, 4)}-${hex.substr(12, 4)}-${hex.substr(16, 4)}-${hex.substr(20)}`;
  }
  ALL_KEYS = Array.from({ length: TOTAL_KEYS }, () => uuidv4());
}

const HOT_KEYS = ALL_KEYS.slice(0, HOT_COUNT);
const COLD_KEYS = ALL_KEYS.slice(HOT_COUNT);

function pickKey() {
  const useHot = Math.random() < HOT_RATIO || COLD_KEYS.length === 0;
  if (useHot) {
    hitsHot.add(1);
    return { id: HOT_KEYS[(Math.random() * HOT_KEYS.length) | 0], hot: true };
  }
  hitsCold.add(1);
  return { id: COLD_KEYS[(Math.random() * COLD_KEYS.length) | 0], hot: false };
}

export function hit() {
  const isMain = exec.scenario.name === 'main';
  const { id, hot } = pickKey();
  const url = `${BASE_URL}${PATH_PREFIX}/${id}/following`;

  const res = http.get(url, {
    tags: {
      name: `${PATH_PREFIX}/:id/following`,
      hot: hot ? '1' : '0',
    },
  });

  httpDuration.add(res.timings.duration);

  if (isMain) {
    mainHttpDuration.add(res.timings.duration);
    mainHttpReqs.add(1);
    if (hot) {
      mainHitsHot.add(1);
    } else {
      mainHitsCold.add(1);
    }
    if (res.status >= 400 || res.error) {
      mainHttpErrors.add(1);
    }
  }

  if (res.timings.duration < 5) sleep(0.005);
}
