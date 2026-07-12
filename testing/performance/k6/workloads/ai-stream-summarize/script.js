// AI streaming benchmark — open-loop arrival rate over the AI endpoints.
// Sweep axis 1: target arrival rate (RPS) × thread-pool-size.
//
// All AI endpoints live under the hierarchical prefix
//   /tweets/ai/users/{userId}/conversations/{conversationId}
// {userId} is the seeded benchmark user (USER_ID); {conversationId} is a fresh
// UUID per iteration so each request opens a distinct conversation.
//
// Env knobs:
//   WORKLOAD: W0 | W1 | W2 (default W1)
//     W0  → GET  .../mock-stream (non-AI streaming baseline)
//     W1  → POST .../summarize   (pure chat streaming)
//     W2  → POST .../summarize-with-tool (mid-stream UserClient call — H1 surface)
//   TRANSPORT: sse | buffered (default sse)
//     sse      → streaming endpoint (as above)
//     buffered → .../buffered (REST control for W1)
//   CANCEL_RATE: 0.0..1.0 fraction of requests to abort mid-stream (default 0.0)
//   TARGET_RPS: arrival rate (default 100)
//   USER_ID: seeded benchmark user UUID used as the {userId} path segment; the W2
//            tool call fetches this user (default 00000000-0000-0000-0000-000000000001)
//   TOKENS_PER_RESPONSE: app's configured mock output count (recorded as metadata; W1/W2)
//   PROMPT_VARIANT: label for the prompt sent (canonical|short|medium|long; default canonical)
//   AI_BACKEND_TAG: mock|live, recorded as ai_backend so mock and live cells never pool
//
// Uses ramping-arrival-rate → constant-arrival-rate (open-loop).
// Do NOT switch to constant-vus (closed-loop invalidates H1 claims).

import http from 'k6/http';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9092';
const WORKLOAD = (__ENV.WORKLOAD || 'W1').toUpperCase();
const TRANSPORT = (__ENV.TRANSPORT || 'sse').toLowerCase();
const POOL_SIZE_TAG = __ENV.POOL_SIZE_TAG || '';
const STACK_TAG = __ENV.STACK_TAG || '';
const REJECT_POLICY_TAG = __ENV.REJECT_POLICY_TAG || '';
const CALIBRATION_TAG = __ENV.CALIBRATION_TAG || 'mock-defaults';
const CAMPAIGN = __ENV.CAMPAIGN || 'ad-hoc';
const CANCEL_RATE = Number(__ENV.CANCEL_RATE || 0.0);
const TARGET_RPS = Number(__ENV.TARGET_RPS || __ENV.CONCURRENCY || 100);
const WARMUP_SECS = __ENV.WARMUP_SECS || '60s';
const DURATION = __ENV.DURATION || '180s';
const RAMP_SECS = __ENV.RAMP_SECS || '20s';
const GRACEFUL_STOP = __ENV.GRACEFUL_STOP || '60s';
const PROMPT = __ENV.PROMPT || (
  'You are an assistant summarizing a user\'s recent activity on a microblogging ' +
  'platform. Review the context below and write a concise, friendly recap. Over the ' +
  'past week this user published several short posts about distributed systems, ' +
  'reactive programming, and a weekend hiking trip. They replied to threads debating ' +
  'database connection pooling, liked posts about Kubernetes operators from accounts ' +
  'they follow, and retweeted an article on event-loop concurrency. Identify the ' +
  'dominant themes, note which people they interacted with most, call out any shift ' +
  'in tone from technical to personal, and suggest two or three topics they might ' +
  'post about next. Keep the summary under one hundred words and write in a warm, ' +
  'encouraging voice.'
);
// Default to the UUID seeded by ai-streaming/prepare.py so direct invocations
// (without the run_bench.sh wrapper) don't 404 on W2 tool calls. run_bench.sh
// seeds this same id before launching k6; for a hand-run, run `prepare.py seed`.
const USER_ID = __ENV.USER_ID || '00000000-0000-0000-0000-000000000001';
const MOCK_TOKENS = Number(__ENV.MOCK_TOKENS || 150);
const MOCK_ITL_MS = Number(__ENV.MOCK_ITL_MS || 40);
// Descriptive variant label so W0-equal (short ITL, ~same residency as W1) is never
// confused with the legacy W0-long (150×40≈6s) in machine-readable output. Defaults
// are derived when unset: W0 with itl≤20ms → W0-equal, else W0-long; W1/W2 → workload id.
const WORKLOAD_VARIANT = __ENV.WORKLOAD_VARIANT || (
  WORKLOAD === 'W0' ? (MOCK_ITL_MS <= 20 ? 'W0-equal' : 'W0-long') : WORKLOAD
);
// Output-token / prompt / backend metadata recorded in the summary JSON so the
// analysis pipeline can separate cells along these axes. These are RECORDED, not
// enforced, by k6: TOKENS_PER_RESPONSE mirrors the app's app.ai.mock.tokens-per-response
// (the W1/W2 mock chat output count — the app, not k6, produces the chunks), PROMPT_VARIANT
// labels which prompt was sent, and AI_BACKEND tags mock vs the live mlx_lm.server backend.
const TOKENS_PER_RESPONSE = Number(__ENV.TOKENS_PER_RESPONSE || 150);
const PROMPT_VARIANT = __ENV.PROMPT_VARIANT || 'canonical';
const AI_BACKEND = (__ENV.AI_BACKEND_TAG || 'mock').toLowerCase();
const REQ_TIMEOUT = __ENV.REQ_TIMEOUT || '60s';
const PREALLOC_VUS = Number(__ENV.PREALLOC_VUS || Math.max(100, TARGET_RPS * 5));
const MAX_VUS = Number(__ENV.MAX_VUS || PREALLOC_VUS * 3);

// Valid combos: W0 × sse, W1 × {sse, buffered}, W2 × sse.
// - W2 + buffered: refused because W2 measures a mid-stream tool call;
//   collapsing the stream into a buffered REST response defeats the measurement.
// - W0 + buffered: refused because W0 is the pure non-AI streaming baseline
//   (.../mock-stream). It has no buffered counterpart endpoint, so the
//   combination is invalid — refuse it explicitly rather than silently falling
//   through to the SSE endpoint and emitting rows tagged transport=buffered
//   that actually exercise SSE.
if (WORKLOAD === 'W2' && TRANSPORT === 'buffered') {
  throw new Error(
    'WORKLOAD=W2 is incompatible with TRANSPORT=buffered. W2 measures a mid-stream tool call; ' +
    'a buffered response has no stream to interrupt. Use {W0,W1} with buffered for the REST control, ' +
    'or keep W2 on TRANSPORT=sse.'
  );
}
if (WORKLOAD === 'W0' && TRANSPORT === 'buffered') {
  throw new Error(
    'WORKLOAD=W0 is incompatible with TRANSPORT=buffered. W0 is the non-AI mock-stream baseline ' +
    '(.../mock-stream); there is no buffered counterpart endpoint. Use W1 for buffered REST control.'
  );
}

// Steady-state metrics — emitted ONLY from the `main` scenario so warmup ramp
// samples (which include cold JIT, lazy class loading, and unsteady arrivals)
// don't leak into the per-run p99 reported in handleSummary. Errors and cancels
// are also gated to `main` so the per-cell error rate matches the latency window.
const ttft = new Trend('ai_ttft_ms', true);
const e2e = new Trend('ai_e2e_ms', true);
const e2eFailed = new Trend('ai_e2e_failed_ms', true);
const reqCount = new Counter('ai_requests');
const errCount = new Counter('ai_errors');
const cancelCount = new Counter('ai_cancels');
const dropCount = new Counter('ai_dropped_arrivals');

// Error CLASSIFICATION — split the failed population so transport/admission
// collapse (k6 ↔ connector) is never silently counted as application-level
// AbortPolicy rejection. status>=500 ≈ app failure (incl. the streamExecutor's
// AbortPolicy 5xx); status==0 carries a Go transport error string we bucket by
// signature. The authoritative AbortPolicy count is Prometheus reject_delta
// (added by the analysis ingester), not anything k6 can see.
const errHttp4xx = new Counter('ai_err_http_4xx');
const errHttp5xx = new Counter('ai_err_http_5xx');
const errDialTimeout = new Counter('ai_err_dial_timeout');
const errReqTimeout = new Counter('ai_err_req_timeout');
const errAddrUnavail = new Counter('ai_err_addr_unavail');
const errConnReset = new Counter('ai_err_conn_reset');
const errConnRefused = new Counter('ai_err_conn_refused');
const errEof = new Counter('ai_err_eof');
const errLgen = new Counter('ai_err_lgen');   // load-generator failure (k6 fd/DNS) — SUT never saw it
const errOther = new Counter('ai_err_other');

function classifyError(res) {
  if (res.status >= 500) return errHttp5xx;
  if (res.status >= 400) return errHttp4xx;
  // status 0 — transport failure; the Go net error string carries the cause.
  const e = (res.error || '').toLowerCase();
  // Load-generator-side failures: the SUT never received the request. These invalidate the
  // cell (it measures k6's limits, not the server's) — bucketed apart from server transport.
  if (e.indexOf('too many open files') >= 0 || e.indexOf('no such host') >= 0) return errLgen;
  // Client transport/buffer exhaustion (ephemeral ports / socket buffers).
  if (e.indexOf('assign requested address') >= 0 || e.indexOf('no buffer space') >= 0) return errAddrUnavail;
  // TCP connect / accept-backlog collapse.
  if (e.indexOf('i/o timeout') >= 0 || e.indexOf('operation timed out') >= 0) return errDialTimeout;
  // Connected but no response within REQ_TIMEOUT.
  if (e.indexOf('request timeout') >= 0 || e.indexOf('context deadline') >= 0
      || e.indexOf('client.timeout') >= 0 || e.indexOf('awaiting response headers') >= 0) return errReqTimeout;
  // Mid-stream / write-side close.
  if (e.indexOf('connection reset') >= 0 || e.indexOf('reset by peer') >= 0
      || e.indexOf('broken pipe') >= 0) return errConnReset;
  if (e.indexOf('connection refused') >= 0) return errConnRefused;
  if (e.indexOf('eof') >= 0) return errEof;
  return errOther;
}

// Standard UUIDv4. Math.random is fine here — the conversationId is a path
// uniqueness token, not a secret. A fresh id per call makes every iteration a
// distinct conversation under the seeded user.
function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function resolveCall() {
  // Every endpoint hangs off the hierarchical prefix. {userId} is the seeded
  // benchmark user so the W2 tool call resolves; {conversationId} is fresh per
  // iteration. W0/W1/buffered ignore both path vars server-side, but the real
  // prefix is what routes them to the controller at all.
  const base = `${BASE_URL}/tweets/ai/users/${USER_ID}/conversations/${uuidv4()}`;
  if (WORKLOAD === 'W0') {
    return {
      method: 'GET',
      url: `${base}/mock-stream?tokens=${MOCK_TOKENS}&itlMs=${MOCK_ITL_MS}`,
      body: null,
      headers: { Accept: 'text/event-stream' },
    };
  }
  const path = (WORKLOAD === 'W2')
    ? '/summarize-with-tool'
    : (TRANSPORT === 'buffered' ? '/buffered' : '/summarize');
  const accept = TRANSPORT === 'buffered' ? 'application/json' : 'text/event-stream';
  return {
    method: 'POST',
    url: `${base}${path}`,
    body: JSON.stringify({ prompt: PROMPT }),
    headers: {
      'Content-Type': 'application/json',
      Accept: accept,
    },
  };
}

function parseDurationSecs(s) {
  if (typeof s === 'number') return s;
  const m = /^([0-9]+)(s|m)?$/.exec(s || '0s');
  if (!m) return 0;
  const n = Number(m[1]);
  return (m[2] === 'm') ? n * 60 : n;
}

export const options = {
  discardResponseBodies: false,
  systemTags: ['status', 'method', 'name', 'group', 'scenario', 'check', 'error'],
  scenarios: {
    warmup: {
      executor: 'ramping-arrival-rate',
      startRate: Math.max(1, Math.floor(TARGET_RPS / 10)),
      timeUnit: '1s',
      preAllocatedVUs: Math.max(50, Math.floor(PREALLOC_VUS / 2)),
      maxVUs: MAX_VUS,
      stages: [
        { target: TARGET_RPS, duration: RAMP_SECS },
        { target: TARGET_RPS, duration: WARMUP_SECS },
      ],
      exec: 'hitWarmup',
      tags: { phase: 'warmup' },
      // Non-zero gracefulStop so long SSE requests in flight at the warmup→main
      // boundary aren't truncated; truncated tails would silently censor the
      // very latencies H1 needs.
      gracefulStop: GRACEFUL_STOP,
    },
    main: {
      executor: 'constant-arrival-rate',
      rate: TARGET_RPS,
      timeUnit: '1s',
      preAllocatedVUs: PREALLOC_VUS,
      maxVUs: MAX_VUS,
      duration: DURATION,
      // Push main start past the warmup drain window. k6's `gracefulStop`
      // lets warmup iterations finish but does NOT delay later scenarios —
      // long-running warmup SSE requests would otherwise still be consuming
      // tweet-service capacity during early-main p99 buckets and contaminate
      // overload cells. Adding GRACEFUL_STOP gives those iterations a quiet
      // gap to drain before the steady-state measurement window opens.
      startTime: `${parseDurationSecs(RAMP_SECS) + parseDurationSecs(WARMUP_SECS) + parseDurationSecs(GRACEFUL_STOP)}s`,
      exec: 'hit',
      tags: { phase: 'main' },
      gracefulStop: GRACEFUL_STOP,
    },
  },
  summaryTrendStats: ['count', 'min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)', 'max'],
};

// Warmup invocations exercise the same endpoint but emit no Trend / Counter
// data so warmup samples never touch the per-cell p99 reported in the summary.
export function hitWarmup() {
  doRequest({ recordMetrics: false });
}

export function hit() {
  doRequest({ recordMetrics: true });
}

function doRequest({ recordMetrics }) {
  const call = resolveCall();
  // k6 has no true mid-stream cancellation; emulate by using a very short timeout
  // on a configurable fraction of requests to force client-side abort.
  const shouldCancel = CANCEL_RATE > 0 && Math.random() < CANCEL_RATE;
  const params = {
    headers: call.headers,
    timeout: shouldCancel ? '100ms' : REQ_TIMEOUT,
    tags: { cancelled: shouldCancel ? 'true' : 'false' },
  };

  const started = Date.now();
  let res;
  if (call.method === 'GET') {
    res = http.get(call.url, params);
  } else {
    res = http.post(call.url, call.body, params);
  }
  const finished = Date.now();

  if (!recordMetrics) {
    return;
  }

  reqCount.add(1);

  // Capture observed e2e for ALL outcomes (success, cancel, error) so the
  // per-cell distribution doesn't censor pool-overflow rejections (which
  // typically come back fast — censoring them collapses the p99 we want
  // to show). Successes feed the headline Trend; non-successes feed a
  // separate Trend so the latency-of-failures distribution is auditable.
  const observedMs = finished - started;

  if (shouldCancel) {
    cancelCount.add(1);
    // Short-timeout aborts land as status 0; do not feed e2e so the
    // headline distribution isn't dragged down by emulated cancellations.
    return;
  }
  if (res.status === 0 || res.status >= 400) {
    errCount.add(1);
    e2eFailed.add(observedMs);
    classifyError(res).add(1);
    return;
  }

  // TTFT source depends on transport:
  //   - buffered: server returns X-Tweebyte-TTFT-Ms after collecting the stream.
  //   - sse:      server can't set headers after flushing, so we approximate via
  //               res.timings.waiting (time between send-complete and first byte
  //               received, a.k.a. TTFB). For SSE the first byte is the first
  //               data chunk, which is effectively the first token.
  //
  //   Authoritative server-side TTFT lives in the Prometheus histogram
  //   tweebyte_ai_ttft_seconds. The k6 value here is the client-observed pair
  //   to cross-check Prometheus against.
  const ttftHeader = res.headers['X-Tweebyte-Ttft-Ms'] || res.headers['X-Tweebyte-TTFT-Ms'];
  if (ttftHeader) {
    ttft.add(Number(ttftHeader));
  } else if (TRANSPORT === 'sse' && res.timings && typeof res.timings.waiting === 'number') {
    ttft.add(res.timings.waiting);
  }
  const e2eHeader = res.headers['X-Tweebyte-E2E-Ms'];
  if (e2eHeader) {
    e2e.add(Number(e2eHeader));
  } else {
    e2e.add(observedMs);
  }
}

export function handleSummary(data) {
  const dropped = data.metrics['dropped_iterations'];
  if (dropped && dropped.values && dropped.values.count) {
    dropCount.add(dropped.values.count);
  }
  const summary = {
    workload: WORKLOAD,
    workload_variant: WORKLOAD_VARIANT,
    transport: TRANSPORT,
    cancel_rate: CANCEL_RATE,
    target_rps: TARGET_RPS,
    // W0 residency control (also recorded for W1/W2, where the mock ChatModel
    // owns timing and these only describe the W0 mock-stream query params).
    mock_tokens: MOCK_TOKENS,
    mock_itl_ms: MOCK_ITL_MS,
    // Output-token / prompt-length / backend axes. tokens_per_response is the app's
    // configured mock output count (W1/W2). prompt_approx_tokens is a chars/4 heuristic
    // (the mock does NOT scale TTFT with input tokens — this is metadata only; only the
    // live backend actually consumes the prompt length).
    tokens_per_response: TOKENS_PER_RESPONSE,
    prompt_variant: PROMPT_VARIANT,
    ai_backend: AI_BACKEND,
    prompt_chars: PROMPT.length,
    prompt_approx_tokens: Math.ceil(PROMPT.length / 4),
    pool_size: POOL_SIZE_TAG,
    stack: STACK_TAG,
    reject_policy: REJECT_POLICY_TAG,
    calibration_tag: CALIBRATION_TAG,
    campaign: CAMPAIGN,
    requests: data.metrics['ai_requests']?.values?.count ?? 0,
    errors: data.metrics['ai_errors']?.values?.count ?? 0,
    cancels: data.metrics['ai_cancels']?.values?.count ?? 0,
    dropped: dropped?.values?.count ?? 0,
    // Classified failed population (transport vs application). reject_delta
    // (authoritative AbortPolicy count) is added downstream by the analysis ingester
    // from the per-cell *_prom.csv, since k6 cannot distinguish an AbortPolicy 5xx
    // from any other 5xx.
    errors_by_type: {
      http_4xx: data.metrics['ai_err_http_4xx']?.values?.count ?? 0,
      http_5xx: data.metrics['ai_err_http_5xx']?.values?.count ?? 0,
      dial_timeout: data.metrics['ai_err_dial_timeout']?.values?.count ?? 0,
      req_timeout: data.metrics['ai_err_req_timeout']?.values?.count ?? 0,
      addr_unavail: data.metrics['ai_err_addr_unavail']?.values?.count ?? 0,
      conn_reset: data.metrics['ai_err_conn_reset']?.values?.count ?? 0,
      conn_refused: data.metrics['ai_err_conn_refused']?.values?.count ?? 0,
      eof: data.metrics['ai_err_eof']?.values?.count ?? 0,
      lgen: data.metrics['ai_err_lgen']?.values?.count ?? 0,
      other: data.metrics['ai_err_other']?.values?.count ?? 0,
    },
    ttft_ms: {
      p50: data.metrics['ai_ttft_ms']?.values?.['med'],
      p95: data.metrics['ai_ttft_ms']?.values?.['p(95)'],
      p99: data.metrics['ai_ttft_ms']?.values?.['p(99)'],
    },
    e2e_ms: {
      p50: data.metrics['ai_e2e_ms']?.values?.['med'],
      p95: data.metrics['ai_e2e_ms']?.values?.['p(95)'],
      p99: data.metrics['ai_e2e_ms']?.values?.['p(99)'],
      p999: data.metrics['ai_e2e_ms']?.values?.['p(99.9)'],
    },
    e2e_failed_ms: {
      p50: data.metrics['ai_e2e_failed_ms']?.values?.['med'],
      p95: data.metrics['ai_e2e_failed_ms']?.values?.['p(95)'],
      p99: data.metrics['ai_e2e_failed_ms']?.values?.['p(99)'],
      count: data.metrics['ai_e2e_failed_ms']?.values?.count ?? 0,
    },
  };
  return { stdout: JSON.stringify(summary, null, 2) + '\n' };
}
