// CPU-bound preview benchmark.
// POST /media/{srcId}/preview derives a degraded "sensitive-media preview" of a
// resident original: user-service runs the CPU pipeline (3× Gaussian blur + Sobel
// + resize + JPEG) over the seeded original's bytes and content-addresses the
// result. The benchmark re-previews the SAME seeded source, so after warmup every
// request is the pipeline (the measured CPU work) followed by a pure cache HIT on
// the content-addressed store — no bcrypt, no DB write. It hits the SERVICE DIRECTLY
// (port 9091), bypassing the gateway: there is no /media/** gateway route, and the
// gateway's JWT filter would reject this unauthenticated POST anyway. The benchmark
// intentionally measures pure user-service CPU work, not gateway/JWT overhead.
//
// run.sh seeds the source original + passes --api-path /media/{srcId}/preview; the
// default below is the same deterministic id (uuid5
// "tweebyte-benchmark-media:image-upload-source") so a standalone k6 run hits the
// seeded source too. PASSWORD must match prepare.py's BENCHMARK_PASSWORD.

import http from 'k6/http';
import { Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';
import { sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9091';
const API_PATH = __ENV.API_PATH || '/media/74a7f7fc-0581-50c0-ae79-ad8d24fb2579/preview';
const PASSWORD = __ENV.PASSWORD || 'preview-gate-benchmark';
const CONCURRENCY = Number(__ENV.CONCURRENCY || 8);
const WARMUP_SECS = __ENV.WARMUP_SECS || '60s';
const DURATION = __ENV.DURATION || '3m';

const PAYLOAD = JSON.stringify({ password: PASSWORD });
const PARAMS = {
  headers: { 'Content-Type': 'application/json', Connection: 'keep-alive' },
  tags: { name: API_PATH },
};

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
      exec: 'requestPreview',
      tags: { phase: 'warmup' },
      gracefulStop: '0s',
    },
    main: {
      executor: 'constant-vus',
      vus: CONCURRENCY,
      duration: DURATION,
      startTime: WARMUP_SECS,
      exec: 'requestPreview',
      tags: { phase: 'main' },
      gracefulStop: '0s',
    },
  },
  summaryTrendStats: ['count', 'min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function requestPreview() {
  const isMain = exec.scenario.name === 'main';
  const url = `${BASE_URL}${API_PATH}`;
  const res = http.post(url, PAYLOAD, PARAMS);

  if (isMain) {
    mainLatency.add(res.timings.duration);
    mainHttpReqs.add(1);
    if (res.status >= 400 || res.error) {
      mainHttpErrors.add(1);
    }
  }

  if (res.timings.duration < 5) sleep(0.005);
}
