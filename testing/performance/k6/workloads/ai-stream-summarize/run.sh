#!/usr/bin/env bash
# Workload-level runner for the AI streaming benchmark. It owns the primary grid
# and focused control profiles, delegating k6 execution to run_bench.sh.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "${SCRIPT_DIR}/../../../../.." && pwd)"
WORKLOAD="ai-stream-summarize"
BENCH_URL="${AI_BASE_URL:-http://localhost:9092}"
ACTUATOR_URL="${AI_ACTUATOR_URL:-${BENCH_URL}/actuator/prometheus}"
RESULTS_ROOT="${RESULTS_ROOT:-${REPO}/testing-results/performance/k6}"
CAMPAIGN_ROOT="${AI_CAMPAIGN_ROOT:-${RESULTS_ROOT}/ai-streaming-campaigns}"
ORIGINAL_TWEET_JAVA_TOOL_OPTIONS="${TWEET_JAVA_TOOL_OPTIONS:-}"
CURRENT_STACK=""
LOG_PATH=""

usage() {
  cat <<'EOF'
Usage:
  testing/performance/k6/workloads/ai-stream-summarize/run.sh <profile> [async|reactive|both]

Profiles — primary grid:
  full-current       FULL exhaustive matrix: W0-equal/W1/W2 SSE, pool 400, 5 reps,
                     18-point RPS grid, W0=150×11ms (~2.0s incl SSE emit overhead),
                     queue 4000, reject abort, VUs 15000/30000. ~2 uninterrupted days, 108 cells.
  one-day-current    Dense SHAPE PASS of the operator's ~one-day budget plan: same pins
                     and 18-point grid as full-current but 1 rep (108 cells). Follow with a
                     targeted AI_RPS_LIST/AI_RUNS run on selected knee/headline cells.
  pilot              1-rep pre-flight: W0-equal/W1/W2 SSE, pool 400,
                     rps 50/190/200/210/225/250/500, same pins. Run before either above.

Profiles — focused controls (not pooled into the primary aggregate):
  output-token-scout Output-token sensitivity: W1+W2 over tokens-per-response 64/150/400
                     (app reboots per level), small RPS set, 1 rep. Shows rps_crit shift.
  reactive-ceiling   Reactive-ONLY high-RPS scout (800..2500) to find the first reactive
                     bend or honestly record the native-local rig limit first. 1 rep.
  realism-live       Real-MLX live-backend subset + prompt-length probe: AI_BACKEND=live
                     vs host-local mlx_lm.server, LOW rps (2/5/10), W1+W2 × short/medium/long
                     prompts, native-local only. Labelled realism, NOT the H1 cliff.
  buffered-rep       Representative W1 buffered REST control (below/near/above threshold).

Profiles — narrow diagnostics:
  smoke              Small one-cell sanity run.
  threshold-fill     W1 SSE at rps 200/300/400, pool tag 400, 5 reps.
  prom-cliff         Cliff rerun with per-run Prometheus traces enabled.
  buffered-control   W1 buffered REST control at rps 500, pool tag 400, 5 reps.
  w0-pool1600-redrop W0/pool1600 rerun with explicit k6 VU ceiling.
  canonical          W0/W1/W2 SSE pool sweep at rps=500; legacy pool-sensitivity pass.

Controls:
  AI_DRY_RUN=1             Print commands without starting stacks or k6.
  AI_MANAGE_STACK=0        Use an already-running pinned stack.
  AI_ASSERT_CONFIG=0       Skip Prometheus effective-config assertions (pool + tokens + backend).
  AI_QUEUE_CAPACITY=<N>    Queue-capacity method parameter, default 0.
  AI_REJECT_POLICY=<name>  Reject policy method parameter, default abort.
  AI_RUNS=<N>              Override repetitions for the selected profile.
  AI_RPS_LIST="..."        Override target arrival rates.
  AI_POOL_SIZES="..."      Override pool tags / async configured pool sizes.
  AI_WORKLOADS="..."       Override W0/W1/W2 set.
  AI_TRANSPORT=<mode>      Override transport, default depends on profile.
  AI_TOKENS_LEVELS="..."   Output-token levels; each reboots the app with that
                              AI_MOCK_TOKENS_PER_RESPONSE. Default 150 (single level).
  AI_PROMPT_VARIANTS="..."  Prompt-length variants per cell (canonical short medium long).
                              Default canonical (the k6 script's realistic default prompt).
  AI_BACKEND=mock|live     Backend; live needs LIVE_LLM_BASE_URL + LIVE_LLM_MODEL and
                              native-local AI_K6_MODE=local. Default mock.
  AI_LIVE_MAX_RPS=<N>      Refuse live cells above this rps (default 25 in realism-live).
  AI_PREALLOC_VUS=<N>      Explicit k6 preAllocatedVUs for AI open-loop runs.
  AI_MAX_VUS=<N>           Explicit k6 maxVUs for AI open-loop runs.
  AI_K6_MODE=local|docker  Load-tool transport, default local for the primary grid.
  TOPOLOGY=local-app|native-local|all-docker
                              App topology. The primary-grid and focused-control profiles
                              (full-current, one-day-current, pilot, output-token-scout,
                              reactive-ceiling, realism-live, buffered-rep) DEFAULT to
                              native-local (host-native infra via local-infra.sh, no Docker)
                              and REFUSE a non-native-local topology unless
                              AI_ALLOW_NON_NATIVE_LOCAL=1. Narrow diagnostic profiles
                              (smoke, threshold-fill, prom-cliff, buffered-control,
                              w0-pool1600-redrop, canonical) default to local-app.

Examples:
  AI_DRY_RUN=1 TOPOLOGY=native-local ./run.sh one-day-current both
  AI_DRY_RUN=1 ./run.sh output-token-scout both
  LIVE_LLM_BASE_URL=http://localhost:8081 LIVE_LLM_MODEL=/path/to/model \
    TOPOLOGY=native-local ./run.sh realism-live both
EOF
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

log() {
  local line
  line="[$(date '+%Y-%m-%d %H:%M:%S')] $*"
  printf '%s\n' "$line"
  if [[ -n "${LOG_PATH:-}" ]]; then
    printf '%s\n' "$line" >> "$LOG_PATH"
  fi
}

duration_to_secs() {
  local value="$1"
  if [[ "$value" =~ ^[0-9]+$ ]]; then
    printf '%s\n' "$value"
  elif [[ "$value" =~ ^([0-9]+)s$ ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
  elif [[ "$value" =~ ^([0-9]+)m$ ]]; then
    printf '%s\n' "$(( ${BASH_REMATCH[1]} * 60 ))"
  else
    die "unsupported duration '$value'"
  fi
}

require_positive_int() {
  local name="$1" value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]] || (( value <= 0 )); then
    die "${name} must be a positive integer, got '${value}'"
  fi
}

require_non_negative_int() {
  local name="$1" value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    die "${name} must be a non-negative integer, got '${value}'"
  fi
}

normalize_stack_arg() {
  case "${1:-both}" in
    both) printf '%s\n' "async reactive" ;;
    async|reactive) printf '%s\n' "$1" ;;
    *) die "stack must be async, reactive, or both" ;;
  esac
}

# Primary-grid and focused-control profiles are native-local-only. They default to
# TOPOLOGY=native-local and refuse a different topology unless explicitly overridden.
is_primary_profile() {
  case "$1" in
    full-current|one-day-current|pilot|output-token-scout|reactive-ceiling|realism-live|buffered-rep) return 0 ;;
    *) return 1 ;;
  esac
}

profile_defaults() {
  local profile="$1"
  case "$profile" in
    smoke)
      RPS_LIST="${AI_RPS_LIST:-10}"
      POOL_SIZES="${AI_POOL_SIZES:-200}"
      RUNS="${AI_RUNS:-1}"
      WARMUP="${AI_WARMUP:-10s}"
      DURATION="${AI_DURATION:-30s}"
      AI_WORKLOADS_EFFECTIVE="${AI_WORKLOADS:-W1}"
      AI_TRANSPORT_EFFECTIVE="${AI_TRANSPORT:-sse}"
      ;;
    threshold-fill)
      RPS_LIST="${AI_RPS_LIST:-200 300 400}"
      POOL_SIZES="${AI_POOL_SIZES:-400}"
      RUNS="${AI_RUNS:-5}"
      WARMUP="${AI_WARMUP:-60s}"
      DURATION="${AI_DURATION:-180s}"
      AI_WORKLOADS_EFFECTIVE="${AI_WORKLOADS:-W1}"
      AI_TRANSPORT_EFFECTIVE="${AI_TRANSPORT:-sse}"
      ;;
    prom-cliff)
      RPS_LIST="${AI_RPS_LIST:-500}"
      POOL_SIZES="${AI_POOL_SIZES:-400}"
      RUNS="${AI_RUNS:-5}"
      WARMUP="${AI_WARMUP:-60s}"
      DURATION="${AI_DURATION:-180s}"
      AI_WORKLOADS_EFFECTIVE="${AI_WORKLOADS:-W1}"
      AI_TRANSPORT_EFFECTIVE="${AI_TRANSPORT:-sse}"
      ;;
    buffered-control)
      RPS_LIST="${AI_RPS_LIST:-500}"
      POOL_SIZES="${AI_POOL_SIZES:-400}"
      RUNS="${AI_RUNS:-5}"
      WARMUP="${AI_WARMUP:-60s}"
      DURATION="${AI_DURATION:-180s}"
      AI_WORKLOADS_EFFECTIVE="${AI_WORKLOADS:-W1}"
      AI_TRANSPORT_EFFECTIVE="${AI_TRANSPORT:-buffered}"
      ;;
    w0-pool1600-redrop)
      RPS_LIST="${AI_RPS_LIST:-500}"
      POOL_SIZES="${AI_POOL_SIZES:-1600}"
      RUNS="${AI_RUNS:-5}"
      WARMUP="${AI_WARMUP:-60s}"
      DURATION="${AI_DURATION:-180s}"
      AI_WORKLOADS_EFFECTIVE="${AI_WORKLOADS:-W0}"
      AI_TRANSPORT_EFFECTIVE="${AI_TRANSPORT:-sse}"
      AI_PREALLOC_VUS="${AI_PREALLOC_VUS:-5000}"
      AI_MAX_VUS="${AI_MAX_VUS:-12000}"
      ;;
    full-current|pilot|one-day-current)
      # W0-equal/W1/W2, pool 400, dense 18-point RPS grid. Three repetition
      # shapes share one
      # set of locked pins:
      #   full-current    — 5 reps (exhaustive reproducibility matrix, ~2 laptop days)
      #   one-day-current — 1 rep over the full grid (the dense "shape pass" of the
      #                     operator's ~one-day budget plan; selected knee/headline cells
      #                     get their extra reps via a follow-up AI_RPS_LIST/AI_RUNS run)
      #   pilot           — 1 rep over a 7-point subset (pre-run go/no-go review)
      # All canonical pins are defaulted here so AI_DRY_RUN prints the full contract.
      if [[ "$profile" == "pilot" ]]; then
        RPS_LIST="${AI_RPS_LIST:-50 190 200 210 225 250 500}"
        RUNS="${AI_RUNS:-1}"
      else
        # 18-point grid with a low-load anchor so the latency curve has an explicit
        # near-idle point and the figures visibly start near the origin.
        RPS_LIST="${AI_RPS_LIST:-10 25 50 75 100 125 150 175 190 200 210 225 250 300 350 400 500 650}"
        if [[ "$profile" == "one-day-current" ]]; then
          RUNS="${AI_RUNS:-1}"
        else
          RUNS="${AI_RUNS:-5}"
        fi
      fi
      POOL_SIZES="${AI_POOL_SIZES:-400}"
      WARMUP="${AI_WARMUP:-60s}"
      DURATION="${AI_DURATION:-180s}"
      AI_WORKLOADS_EFFECTIVE="${AI_WORKLOADS:-W0 W1 W2}"
      AI_TRANSPORT_EFFECTIVE="${AI_TRANSPORT:-sse}"
      # W0-equal: matched to W1's MEASURED residency (~2.0s), not the sleep budget.
      # Pilot (2026-06-14) measured W0 rps=50 p50=2343ms at itl=13 — i.e. the 150
      # explicit SseEmitter.send() calls add ~390ms of emit overhead on top of the
      # tokens×itlMs sleep. itl=11 → 150×11 + ~390 ≈ 2040ms ≈ W1's ~2.0s (knee≈196),
      # whereas itl=13 over-shot to ~2.34s (knee≈171, misaligned with W1's ~205).
      AI_MOCK_TOKENS="${AI_MOCK_TOKENS:-150}"
      AI_MOCK_ITL_MS="${AI_MOCK_ITL_MS:-11}"
      # Locked primary-grid pins (overridable for diagnostics only).
      AI_QUEUE_CAPACITY="${AI_QUEUE_CAPACITY:-4000}"
      AI_REJECT_POLICY="${AI_REJECT_POLICY:-abort}"
      AI_PREALLOC_VUS="${AI_PREALLOC_VUS:-15000}"
      AI_MAX_VUS="${AI_MAX_VUS:-30000}"
      ;;
    output-token-scout)
      # Companion probe — output-token / response-length sensitivity. W1+W2 over
      # short/canonical/long mock output counts at a small RPS set, 1 rep. Each token
      # level reboots the app with that AI_MOCK_TOKENS_PER_RESPONSE; the analysis keys
      # cells on tokens_per_response, so the threshold shift (rps_crit≈T/E[response],
      # E[response]∝tokens) is visible: short output stays clean where long output cliffs.
      # NOT pooled into the primary H1 aggregate.
      RPS_LIST="${AI_RPS_LIST:-100 200 400}"
      POOL_SIZES="${AI_POOL_SIZES:-400}"
      RUNS="${AI_RUNS:-1}"
      WARMUP="${AI_WARMUP:-60s}"
      DURATION="${AI_DURATION:-180s}"
      AI_WORKLOADS_EFFECTIVE="${AI_WORKLOADS:-W1 W2}"
      AI_TRANSPORT_EFFECTIVE="${AI_TRANSPORT:-sse}"
      TOKENS_LEVELS="${AI_TOKENS_LEVELS:-64 150 400}"
      AI_QUEUE_CAPACITY="${AI_QUEUE_CAPACITY:-4000}"
      AI_REJECT_POLICY="${AI_REJECT_POLICY:-abort}"
      AI_PREALLOC_VUS="${AI_PREALLOC_VUS:-15000}"
      AI_MAX_VUS="${AI_MAX_VUS:-30000}"
      ;;
    reactive-ceiling)
      # Companion probe — reactive-ceiling boundary scout. Reactive ONLY, high RPS, to
      # find the first reactive p99 bend OR record honestly that the native-local rig
      # (host k6 / loopback transport) limits arrive first. NOT pooled into H1.
      RPS_LIST="${AI_RPS_LIST:-800 1000 1250 1500 2000 2500}"
      POOL_SIZES="${AI_POOL_SIZES:-400}"
      RUNS="${AI_RUNS:-1}"
      WARMUP="${AI_WARMUP:-60s}"
      DURATION="${AI_DURATION:-180s}"
      AI_WORKLOADS_EFFECTIVE="${AI_WORKLOADS:-W1 W2}"
      AI_TRANSPORT_EFFECTIVE="${AI_TRANSPORT:-sse}"
      AI_MOCK_TOKENS="${AI_MOCK_TOKENS:-150}"
      AI_MOCK_ITL_MS="${AI_MOCK_ITL_MS:-11}"
      AI_QUEUE_CAPACITY="${AI_QUEUE_CAPACITY:-4000}"
      AI_REJECT_POLICY="${AI_REJECT_POLICY:-abort}"
      AI_PREALLOC_VUS="${AI_PREALLOC_VUS:-30000}"
      AI_MAX_VUS="${AI_MAX_VUS:-60000}"
      FORCE_STACK="reactive"
      ;;
    realism-live)
      # Companion probe — real-MLX live-backend subset + input-prompt-length probe.
      # AI_BACKEND=live against host-local mlx_lm.server, LOW RPS only (the real model,
      # not the Java stack, is the bottleneck). W1+W2 × short/medium/long prompts.
      # native-local only; clearly labelled; this is integration/realism + prompt
      # sensitivity evidence, NOT the high-RPS H1 cliff campaign.
      RPS_LIST="${AI_RPS_LIST:-2 5 10}"
      POOL_SIZES="${AI_POOL_SIZES:-400}"
      RUNS="${AI_RUNS:-3}"
      WARMUP="${AI_WARMUP:-30s}"
      DURATION="${AI_DURATION:-60s}"
      AI_WORKLOADS_EFFECTIVE="${AI_WORKLOADS:-W1 W2}"
      AI_TRANSPORT_EFFECTIVE="${AI_TRANSPORT:-sse}"
      PROMPT_VARIANTS_EFFECTIVE="${AI_PROMPT_VARIANTS:-short medium long}"
      AI_BACKEND_EFFECTIVE="live"
      # Record the live token cap as tokens_per_response (the live model owns the count).
      TOKENS_LEVELS="${AI_TOKENS_LEVELS:-${LIVE_LLM_MAX_TOKENS:-200}}"
      AI_QUEUE_CAPACITY="${AI_QUEUE_CAPACITY:-4000}"
      AI_REJECT_POLICY="${AI_REJECT_POLICY:-abort}"
      # Hard guard: a real model on consumer Apple Silicon cannot sustain the H1 grid.
      AI_LIVE_MAX_RPS="${AI_LIVE_MAX_RPS:-25}"
      ;;
    buffered-rep)
      # Companion probe — representative W1 buffered REST control. Below/near/above
      # threshold, both stacks. Tests whether residency (not SSE framing alone) drives
      # the cliff. A control, not a full matrix. NOT pooled into the SSE H1 aggregate.
      RPS_LIST="${AI_RPS_LIST:-100 200 400}"
      POOL_SIZES="${AI_POOL_SIZES:-400}"
      RUNS="${AI_RUNS:-3}"
      WARMUP="${AI_WARMUP:-60s}"
      DURATION="${AI_DURATION:-180s}"
      AI_WORKLOADS_EFFECTIVE="${AI_WORKLOADS:-W1}"
      AI_TRANSPORT_EFFECTIVE="${AI_TRANSPORT:-buffered}"
      AI_QUEUE_CAPACITY="${AI_QUEUE_CAPACITY:-4000}"
      AI_REJECT_POLICY="${AI_REJECT_POLICY:-abort}"
      AI_PREALLOC_VUS="${AI_PREALLOC_VUS:-15000}"
      AI_MAX_VUS="${AI_MAX_VUS:-30000}"
      ;;
    canonical)
      RPS_LIST="${AI_RPS_LIST:-500}"
      POOL_SIZES="${AI_POOL_SIZES:-200 400 800 1600}"
      RUNS="${AI_RUNS:-5}"
      WARMUP="${AI_WARMUP:-60s}"
      DURATION="${AI_DURATION:-180s}"
      AI_WORKLOADS_EFFECTIVE="${AI_WORKLOADS:-W0 W1 W2}"
      AI_TRANSPORT_EFFECTIVE="${AI_TRANSPORT:-sse}"
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

metric_value() {
  local snap="$1" metric="$2"
  printf '%s\n' "$snap" | awk -v want="$metric" '
    $1 == want { n += $2; seen = 1; next }
    $1 ~ "^" want "[{]" {
      sub(/^[^}]*\}[[:space:]]*/, "")
      n += $1
      seen = 1
      next
    }
    END { if (seen) print n }
  '
}

metric_as_int() {
  local value="$1"
  awk -v v="$value" 'BEGIN { printf "%.0f", v }'
}

assert_metric_int() {
  local snap="$1" metric="$2" expected="$3" label="$4"
  local raw actual
  raw="$(metric_value "$snap" "$metric")"
  [[ -n "$raw" ]] || die "missing Prometheus metric ${metric}; rebuild/restart async tweet-service with current instrumentation before running primary-grid cells"
  actual="$(metric_as_int "$raw")"
  [[ "$actual" == "$expected" ]] || die "${label} mismatch: expected ${expected}, Prometheus reports ${actual}"
}

assert_reject_policy() {
  local snap="$1" expected="$2"
  if ! printf '%s\n' "$snap" | grep -Eq '^tweebyte_pool_reject_policy_info\{.*policy="'"$expected"'".*\}[[:space:]]+1(\.0)?([[:space:]]|$)'; then
    die "reject-policy mismatch: expected '${expected}' in tweebyte_pool_reject_policy_info"
  fi
}

assert_backend() {
  local snap="$1" expected="$2"
  if ! printf '%s\n' "$snap" | grep -Eq '^tweebyte_ai_backend_info\{.*backend="'"$expected"'".*\}[[:space:]]+1(\.0)?([[:space:]]|$)'; then
    die "ai-backend mismatch: expected '${expected}' in tweebyte_ai_backend_info — rebuild/restart tweet-service with current AI-config instrumentation"
  fi
}

wait_for_service() {
  local health_url="${BENCH_URL}/actuator/health"
  local deadline=$(( $(date +%s) + ${AI_HEALTH_TIMEOUT_SECS:-120} ))
  while [[ $(date +%s) -lt $deadline ]]; do
    if curl -fs --max-time 2 "$health_url" 2>/dev/null | grep -q '"UP"'; then
      if curl -fs --max-time 2 "$ACTUATOR_URL" >/dev/null 2>&1; then
        return 0
      fi
    fi
    sleep 2
  done
  die "service did not become healthy at ${health_url}"
}

assert_effective_config() {
  local stack="$1" pool="$2" token_level="${3:-150}"
  [[ "$AI_ASSERT_CONFIG" == "1" ]] || return 0

  local snap
  snap="$(curl -fs --max-time 5 "$ACTUATOR_URL")" || die "cannot fetch ${ACTUATOR_URL} for effective-config assertion"
  # tokens-per-response + backend are asserted on BOTH stacks: the runner controls
  # AI_MOCK_TOKENS_PER_RESPONSE + AI_BACKEND for async and reactive alike, and both
  # stacks expose the AI config gauges. This guards the output-token / live-backend
  # probes against a stale JVM that didn't pick up the per-level env.
  assert_metric_int "$snap" "tweebyte_ai_mock_tokens_per_response" "$token_level" "mock tokens-per-response"
  assert_backend "$snap" "$AI_BACKEND_EFFECTIVE"
  # The bounded stream executor + queue/reject policy is an async-only mechanism.
  if [[ "$stack" == "async" ]]; then
    assert_metric_int "$snap" "tweebyte_pool_max_size" "$pool" "stream pool max size"
    assert_metric_int "$snap" "tweebyte_pool_queue_capacity" "$AI_QUEUE_CAPACITY" "stream queue capacity"
    assert_reject_policy "$snap" "$AI_REJECT_POLICY"
    log "asserted async config: pool_size=${pool}, queue_capacity=${AI_QUEUE_CAPACITY}, reject_policy=${AI_REJECT_POLICY}, tokens_per_response=${token_level}, backend=${AI_BACKEND_EFFECTIVE}"
  else
    log "asserted reactive config: tokens_per_response=${token_level}, backend=${AI_BACKEND_EFFECTIVE}"
  fi
}

raise_fd_limit() {
  if ! ulimit -n 65536 >/dev/null 2>&1; then
    log "WARN: could not raise open-file limit to 65536; current soft limit is $(ulimit -n)"
  fi
}

run_logged() {
  printf '%s\t%s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*" >> "$COMMAND_LOG"
  if [[ "$AI_DRY_RUN" == "1" ]]; then
    log "DRY-RUN: $*"
    return 0
  fi
  "$@"
}

# Infra lifecycle, brought up once before the stack loop and (for native-local) torn
# down once after it. local-app shares persistent Docker infra across the async→reactive
# switch; native-local owns host-native ephemeral Postgres/Redis/Vault — its `up` refuses
# if the ports are bound, so it must be a once-per-sweep step, not per-stack. all-docker
# brings its infra up as part of the per-stack compose, so it needs no separate step here.
infra_up() {
  [[ "$AI_MANAGE_STACK" == "1" ]] || return 0
  case "$TOPOLOGY" in
    native-local) run_logged "$REPO/deployment/local/local-infra.sh" up ;;
    local-app) run_logged "$REPO/run.sh" runtime up infra benchmark ;;
  esac
}

infra_down() {
  [[ "$AI_MANAGE_STACK" == "1" ]] || return 0
  # native-local owns ephemeral host infra — tear it down so the ports are free for the
  # next sweep. local-app deliberately leaves shared Docker infra up between runs; the
  # all-docker stack down already removed its infra.
  case "$TOPOLOGY" in
    native-local) run_logged "$REPO/deployment/local/local-infra.sh" down ;;
  esac
}

# Apps launch identically for local-app and native-local — host JVMs via ./run.sh local;
# only the infra provider differs (handled by infra_up/infra_down). all-docker runs the
# whole stack (incl. infra) in Compose.
start_stack() {
  local stack="$1" pool="$2" token_level="${3:-150}"
  [[ "$AI_MANAGE_STACK" == "1" ]] || return 0

  local base_java_opts
  base_java_opts="${AI_TWEET_JAVA_TOOL_OPTIONS:-${ORIGINAL_TWEET_JAVA_TOOL_OPTIONS:--Xmx4g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/diag/}}"
  export TWEET_JAVA_TOOL_OPTIONS="${base_java_opts} -Dapp.concurrency.stream.pool-size=${pool} -Dapp.concurrency.stream.queue-capacity=${AI_QUEUE_CAPACITY} -Dapp.concurrency.stream.reject-policy=${AI_REJECT_POLICY}"
  # Output-token count and backend selection reach the tweet-service JVM via env
  # (deployment/local/local-app.sh and the compose files forward AI_MOCK_TOKENS_PER_RESPONSE
  # + AI_BACKEND + LIVE_LLM_* to the app). Both are asserted from Prometheus after boot
  # (tweebyte_ai_mock_tokens_per_response / tweebyte_ai_backend_info) for both stacks.
  export AI_MOCK_TOKENS_PER_RESPONSE="$token_level"
  export AI_BACKEND="$AI_BACKEND_EFFECTIVE"
  # Live backend: bind the model's response cap to the SAME value recorded as
  # tokens_per_response, so the metadata can never drift from what the model is actually
  # asked to produce — even if AI_TOKENS_LEVELS is overridden away from LIVE_LLM_MAX_TOKENS.
  # (The mock backend reads tokens-per-response directly, so this only matters for live.)
  if [[ "$AI_BACKEND_EFFECTIVE" == "live" ]]; then
    export LIVE_LLM_MAX_TOKENS="$token_level"
  fi

  log "starting ${stack}/benchmark topology=${TOPOLOGY} backend=${AI_BACKEND_EFFECTIVE} tokens_per_response=${token_level} with TWEET_JAVA_TOOL_OPTIONS='${TWEET_JAVA_TOOL_OPTIONS}'"
  CURRENT_STACK="$stack"
  if [[ "$TOPOLOGY" == "local-app" || "$TOPOLOGY" == "native-local" ]]; then
    run_logged "$REPO/run.sh" local up "$stack" benchmark user-service tweet-service
  else
    run_logged "$REPO/run.sh" runtime up "$stack" benchmark
  fi
}

stop_stack() {
  local stack="$1"
  [[ "$AI_MANAGE_STACK" == "1" ]] || return 0
  log "stopping ${stack}/benchmark topology=${TOPOLOGY}"
  if [[ "$TOPOLOGY" == "local-app" || "$TOPOLOGY" == "native-local" ]]; then
    run_logged "$REPO/run.sh" local down "$stack" benchmark user-service tweet-service
  else
    run_logged "$REPO/run.sh" runtime down "$stack" benchmark
  fi
  CURRENT_STACK=""
}

on_exit() {
  local status=$?
  if [[ $status -ne 0 && "${AI_MANAGE_STACK:-0}" == "1" && -n "${CURRENT_STACK:-}" ]]; then
    log "exit status ${status}; attempting to stop ${CURRENT_STACK}/benchmark"
    if [[ "${TOPOLOGY:-local-app}" == "local-app" || "${TOPOLOGY:-}" == "native-local" ]]; then
      "$REPO/run.sh" local down "$CURRENT_STACK" benchmark user-service tweet-service || true
    else
      "$REPO/run.sh" runtime down "$CURRENT_STACK" benchmark || true
    fi
  fi
  # native-local owns ephemeral infra; tear it down on any exit so a failed sweep does
  # not leave native Postgres/Redis/Vault bound and block the next sweep's infra_up.
  if [[ "${AI_MANAGE_STACK:-0}" == "1" && "${TOPOLOGY:-}" == "native-local" ]]; then
    "$REPO/deployment/local/local-infra.sh" down || true
  fi
  cleanup_sweep_finish
}

# Maps a prompt-variant label to the actual prompt text. `canonical` returns empty
# so the k6 script falls back to its single-source-of-truth realistic default; the
# short/medium/long texts are real microblogging-summary prompts of increasing length
# for the live-backend prompt-length probe (the mock ignores prompt length — only the
# live model's prefill responds to it, so these only move numbers under AI_BACKEND=live).
prompt_text_for_variant() {
  case "$1" in
    short)
      printf '%s' "Summarize this user's recent microblog activity in one short sentence."
      ;;
    medium)
      printf '%s' "You are summarizing a microblogging user's recent activity. Over the past week they posted about distributed systems and reactive programming, replied to threads on database connection pooling, and liked posts about Kubernetes operators. Write a concise, friendly two-sentence recap naming the dominant themes."
      ;;
    long)
      printf '%s' "You are an assistant writing a detailed weekly recap for a microblogging user. Context: over the past seven days this user published eleven short posts spanning distributed systems, reactive programming with Project Reactor and WebFlux, R2DBC connection-pool sizing, JVM platform-thread versus virtual-thread trade-offs, and a weekend hiking trip in the Carpathians. They replied to twenty-three threads, the busiest debating Hikari pool tuning and bounded-queue back-pressure, liked forty-one posts (mostly Kubernetes operators and event-loop concurrency from accounts they follow), and retweeted four long-form articles on streaming SSE under load. Identify the dominant technical themes, note which accounts they interacted with most, call out any shift in tone from technical to personal, summarize the sentiment of their replies, and suggest four or five specific topics they might post about next week. Write in a warm, encouraging voice and keep the body under two hundred words."
      ;;
    *)
      printf '%s' ""   # canonical / unknown → k6 default prompt
      ;;
  esac
}

run_cell() {
  local stack="$1" pool="$2" workload_id="$3" transport="$4" rps="$5"
  local token_level="${6:-150}" prompt_variant="${7:-canonical}"
  if [[ "$workload_id" == "W2" && "$transport" == "buffered" ]]; then
    die "W2 cannot be run with buffered transport"
  fi
  if [[ "$workload_id" == "W0" && "$transport" == "buffered" ]]; then
    die "W0 cannot be run with buffered transport"
  fi
  # Live-backend safety: a real model on consumer Apple Silicon cannot sustain the
  # high-RPS H1 grid. Refuse high-RPS live cells rather than producing a meaningless
  # backend-saturation result mislabelled as an H1 cliff.
  if [[ "$AI_BACKEND_EFFECTIVE" == "live" && -n "${AI_LIVE_MAX_RPS:-}" ]] && (( rps > AI_LIVE_MAX_RPS )); then
    die "live backend: target rps=${rps} exceeds AI_LIVE_MAX_RPS=${AI_LIVE_MAX_RPS}; live cells are low-rate realism/prompt probes, not the H1 cliff campaign"
  fi

  # W0 residency is tokens × itlMs (deterministic, symmetric across stacks). The
  # variant label keeps W0-equal (short ITL) auditable apart from legacy W0-long
  # in the summary JSON / runs.csv. W1/W2 carry their workload id (the mock
  # ChatModel owns their timing; mock_tokens/itl are forwarded but only shape W0).
  local mock_tokens="${AI_MOCK_TOKENS:-150}" mock_itl_ms="${AI_MOCK_ITL_MS:-40}" variant
  if [[ "$workload_id" == "W0" ]]; then
    if (( mock_itl_ms <= 20 )); then variant="W0-equal"; else variant="W0-long"; fi
  else
    variant="$workload_id"
  fi

  local -a args=(
    "$REPO/testing/performance/k6/run_bench.sh"
    --workload "$WORKLOAD"
    --base-url "$BENCH_URL"
    --actuator-url "$ACTUATOR_URL"
    --concurrencies "$rps"
    --runs "$RUNS"
    --warmup "$WARMUP"
    --duration "$DURATION"
    --collect-resources "$AI_COLLECT_RESOURCES"
    --auto-prepare "$AI_AUTO_PREPARE"
    --mode "$AI_K6_MODE"
    --compose-network "$AI_COMPOSE_NETWORK"
    --target-container "$AI_TARGET_CONTAINER"
    --docker-image "$AI_K6_DOCKER_IMAGE"
    --ai-workload "$workload_id"
    --ai-workload-variant "$variant"
    --ai-mock-tokens "$mock_tokens"
    --ai-mock-itl-ms "$mock_itl_ms"
    --ai-tokens-per-response "$token_level"
    --ai-prompt-variant "$prompt_variant"
    --ai-backend "$AI_BACKEND_EFFECTIVE"
    --ai-transport "$transport"
    --ai-target-rps "$rps"
    --ai-pool-size-tag "$pool"
    --ai-stack-tag "$stack"
    --ai-reject-policy-tag "$AI_REJECT_POLICY"
    --ai-calibration-tag "$AI_CALIBRATION_TAG"
    --ai-campaign "$AI_CAMPAIGN"
    --readiness-grace "$AI_READINESS_GRACE"
    --prom-poll-secs "$AI_PROM_POLL_SECS"
  )

  # Forward an explicit prompt only for the non-canonical variants; canonical keeps
  # the k6 script's realistic default (single source of truth) so the wrapper never
  # reintroduces a short placeholder prompt.
  local prompt_text
  prompt_text="$(prompt_text_for_variant "$prompt_variant")"
  if [[ -n "$prompt_text" ]]; then
    args+=(--ai-prompt "$prompt_text")
  fi

  if [[ -n "${AI_PREALLOC_VUS:-}" ]]; then
    args+=(--ai-prealloc-vus "$AI_PREALLOC_VUS")
  fi
  if [[ -n "${AI_MAX_VUS:-}" ]]; then
    args+=(--ai-max-vus "$AI_MAX_VUS")
  fi

  log "cell stack=${stack} workload=${workload_id} transport=${transport} rps=${rps} pool=${pool} tokens_per_response=${token_level} prompt=${prompt_variant} backend=${AI_BACKEND_EFFECTIVE} runs=${RUNS}"
  export BENCHMARK_TOPOLOGY="$TOPOLOGY"
  run_logged "${args[@]}"
}

main() {
  local profile="${1:-}"
  local stack_arg="${2:-both}"
  [[ "$profile" != "--help" && "$profile" != "-h" && -n "$profile" ]] || { usage; exit 0; }

  profile_defaults "$profile"

  AI_DRY_RUN="${AI_DRY_RUN:-0}"
  # Primary-grid and focused-control profiles default to native-local instead of
  # the generic local-app default, so an operator who forgets to export TOPOLOGY does not
  # silently run the Docker path (a prior run burned hours on the wrong path).
  if is_primary_profile "$profile"; then
    TOPOLOGY="${TOPOLOGY:-native-local}"
  else
    TOPOLOGY="${TOPOLOGY:-local-app}"
  fi
  source "$REPO/testing/performance/lib/topology.sh"
  topology_validate
  # Hard guard: a primary/control cell on a non-native-local topology pools invisibly
  # with native-local cells (the analysis cell key does not carry topology). Refuse it unless
  # the operator explicitly opts into a deliberate, separately-labelled topology probe.
  if is_primary_profile "$profile" && [[ "$TOPOLOGY" != "native-local" ]]; then
    if [[ "${AI_ALLOW_NON_NATIVE_LOCAL:-0}" == "1" ]]; then
      log "WARN: ${profile} is a native-local-only primary/control profile but TOPOLOGY=${TOPOLOGY}; proceeding only because AI_ALLOW_NON_NATIVE_LOCAL=1 (deliberate topology probe — do NOT pool with native-local cells)."
    else
      die "${profile} requires TOPOLOGY=native-local (no Docker on the measured path). For a deliberate non-native-local probe, set AI_ALLOW_NON_NATIVE_LOCAL=1."
    fi
  fi
  AI_MANAGE_STACK="${AI_MANAGE_STACK:-1}"
  AI_ASSERT_CONFIG="${AI_ASSERT_CONFIG:-1}"
  AI_QUEUE_CAPACITY="${AI_QUEUE_CAPACITY:-0}"
  AI_REJECT_POLICY="${AI_REJECT_POLICY:-abort}"
  AI_CALIBRATION_TAG="${AI_CALIBRATION_TAG:-qwen-3.5-4b-mlxlm-v2}"
  AI_CAMPAIGN="${AI_CAMPAIGN:-${profile}-$(date +%Y%m%d)}"
  AI_READINESS_GRACE="${AI_READINESS_GRACE:-5}"
  AI_PROM_POLL_SECS="${AI_PROM_POLL_SECS:-1}"
  AI_COLLECT_RESOURCES="${AI_COLLECT_RESOURCES:-1}"
  AI_AUTO_PREPARE="${AI_AUTO_PREPARE:-1}"
  AI_K6_MODE="${AI_K6_MODE:-local}"
  AI_COMPOSE_NETWORK="${AI_COMPOSE_NETWORK:-tweebyte_default}"
  AI_TARGET_CONTAINER="${AI_TARGET_CONTAINER:-tweebyte-tweet-service-1}"
  AI_K6_DOCKER_IMAGE="${AI_K6_DOCKER_IMAGE:-grafana/k6:1.7.1}"
  # Companion-probe dimensions. Profile defaults may have set these; otherwise they
  # collapse to the canonical single-level mock shape. TOKENS_LEVELS reboots the app
  # per level (output-token axis); PROMPT_VARIANTS_EFFECTIVE varies per k6 cell (prompt
  # axis); AI_BACKEND_EFFECTIVE selects mock vs the live mlx_lm.server backend.
  TOKENS_LEVELS="${TOKENS_LEVELS:-${AI_TOKENS_LEVELS:-150}}"
  PROMPT_VARIANTS_EFFECTIVE="${PROMPT_VARIANTS_EFFECTIVE:-${AI_PROMPT_VARIANTS:-canonical}}"
  AI_BACKEND_EFFECTIVE="${AI_BACKEND_EFFECTIVE:-${AI_BACKEND:-mock}}"

  require_non_negative_int "AI_QUEUE_CAPACITY" "$AI_QUEUE_CAPACITY"
  require_positive_int "RUNS" "$RUNS"
  require_positive_int "AI_READINESS_GRACE" "$AI_READINESS_GRACE"
  require_positive_int "AI_PROM_POLL_SECS" "$AI_PROM_POLL_SECS"
  duration_to_secs "$WARMUP" >/dev/null
  duration_to_secs "$DURATION" >/dev/null
  if [[ -n "${AI_PREALLOC_VUS:-}" ]]; then
    require_positive_int "AI_PREALLOC_VUS" "$AI_PREALLOC_VUS"
  fi
  if [[ -n "${AI_MAX_VUS:-}" ]]; then
    require_positive_int "AI_MAX_VUS" "$AI_MAX_VUS"
  fi

  read -r -a STACKS <<<"$(normalize_stack_arg "$stack_arg")"
  # Some companion probes are intrinsically single-stack (e.g. reactive-ceiling).
  if [[ -n "${FORCE_STACK:-}" ]]; then
    log "profile ${profile} forces stack=${FORCE_STACK} (overriding stack arg '${stack_arg}')"
    STACKS=("$FORCE_STACK")
  fi
  read -r -a RPS_VALUES <<<"$RPS_LIST"
  read -r -a POOL_VALUES <<<"$POOL_SIZES"
  read -r -a WORKLOAD_VALUES <<<"$AI_WORKLOADS_EFFECTIVE"
  read -r -a TRANSPORT_VALUES <<<"$AI_TRANSPORT_EFFECTIVE"
  read -r -a TOKEN_VALUES <<<"$TOKENS_LEVELS"
  read -r -a PROMPT_VARIANT_VALUES <<<"$PROMPT_VARIANTS_EFFECTIVE"
  ((${#RPS_VALUES[@]} > 0)) || die "empty RPS list"
  ((${#POOL_VALUES[@]} > 0)) || die "empty pool-size list"
  ((${#WORKLOAD_VALUES[@]} > 0)) || die "empty workload list"
  ((${#TRANSPORT_VALUES[@]} > 0)) || die "empty transport list"
  ((${#TOKEN_VALUES[@]} > 0)) || die "empty tokens-per-response level list"
  ((${#PROMPT_VARIANT_VALUES[@]} > 0)) || die "empty prompt-variant list"

  # Pre-flight: reject invalid workload×transport combos before booting any JVM (run_cell
  # also guards each cell as a backstop). Only W1×buffered is valid; W0 has no buffered
  # endpoint and W2 needs a stream for its mid-stream tool call to interrupt.
  local _wl _tr
  for _wl in "${WORKLOAD_VALUES[@]}"; do
    for _tr in "${TRANSPORT_VALUES[@]}"; do
      if [[ "$_tr" == "buffered" && "$_wl" != "W1" ]]; then
        die "invalid combo ${_wl}×buffered: only W1×buffered is valid (W0 has no buffered endpoint; W2 needs a stream to interrupt)"
      fi
    done
  done

  # Live-backend pre-flight: native-local only, real endpoint pins required. The model
  # server (realism-backend.sh up) must already be running; we only validate the wiring.
  if [[ "$AI_BACKEND_EFFECTIVE" == "live" ]]; then
    [[ "$AI_K6_MODE" == "local" ]] || die "live backend is native-local only — AI_K6_MODE must be 'local', got '${AI_K6_MODE}'"
    [[ -n "${LIVE_LLM_BASE_URL:-}" ]] || die "AI_BACKEND_EFFECTIVE=live requires LIVE_LLM_BASE_URL (host-local mlx_lm.server, e.g. http://localhost:8081)"
    [[ -n "${LIVE_LLM_MODEL:-}" ]] || die "AI_BACKEND_EFFECTIVE=live requires LIVE_LLM_MODEL (on-disk model path for mlx_lm.server)"
    # native-local INCLUDES the model. The live endpoint MUST be a simple loopback URL.
    # Do NOT try to parse arbitrary URLs: five review rounds each found an off-box bypass
    # because the bash/Python guard parser disagreed with the Spring/Java client that
    # actually dials (userinfo, fragment/query, `127.*` glob, and a backslash `\@` that
    # Python reads as userinfo but UriComponentsBuilder reads as the host `evil.com`).
    # Instead, allowlist the ENTIRE URL against a strict anchored grammar: scheme + a
    # loopback host (localhost, *.localhost [RFC 6761 — reserved, never delegated], the
    # 127.0.0.0/8 numeric quad, or IPv6 [::1]) + optional :port + optional simple path.
    # Any authority-delimiter char (@ \ # ? % , control, whitespace) makes the match fail,
    # so no parser divergence is possible — the accepted set is unambiguous.
    # Portability note: the `*.localhost` branch is safe only because the resolver maps
    # `*.localhost` to loopback (RFC 6761; honoured by macOS mDNSResponder — the documented
    # native-local rig). If this runner is ever moved to a host whose resolver forwards
    # `*.localhost` upstream, drop that branch or re-verify resolution. (The app's own
    # AiConfiguration.isLocalEndpoint trusts `.localhost` the same way, so they stay aligned.)
    local _llm_loop
    _llm_loop="$(LIVE_LLM_BASE_URL="$LIVE_LLM_BASE_URL" python3 - <<'PY'
import os, re
raw = os.environ.get("LIVE_LLM_BASE_URL") or ""
octet = r'(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])'   # 0-255, so 127.999.x is rejected
host = (r'(?:localhost|127\.' + octet + r'\.' + octet + r'\.' + octet + r'|\[::1\]'
        r'|[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.localhost)')
# re.ASCII is load-bearing: Python's \d / \w / IGNORECASE are Unicode-aware, so without it
# `127.٠.٠.١` (Arabic-Indic) / `127.１.２.３` (fullwidth) / a Unicode-digit port would match,
# yet Java/curl don't parse those as IP literals — re-opening the parser divergence. With
# re.ASCII the whole grammar is ASCII-only, so any non-ASCII char fails the match → REMOTE.
# re.fullmatch (not match + $) so a trailing newline can't satisfy the anchor.
print("LOOPBACK" if re.fullmatch(r'https?://' + host + r'(?::[0-9]+)?(?:/[A-Za-z0-9._~/-]*)?', raw, re.IGNORECASE | re.ASCII) else "REMOTE")
PY
)"
    if [[ "$_llm_loop" != "LOOPBACK" ]]; then
      if [[ "${AI_ALLOW_NON_LOCAL_LLM:-0}" == "1" ]]; then
        log "WARN: LIVE_LLM_BASE_URL='${LIVE_LLM_BASE_URL}' is not a simple loopback URL; proceeding only because AI_ALLOW_NON_LOCAL_LLM=1 — you may be routing live AI traffic off-box, which is NOT native-local. Do not pool these cells with the native-local set."
      else
        die "LIVE_LLM_BASE_URL='${LIVE_LLM_BASE_URL}' is not a simple loopback URL — native-local requires the AI model host-local. Use http://localhost:PORT, http://127.0.0.0/8:PORT, http://[::1]:PORT, or http://<name>.localhost:PORT (no userinfo/query/fragment/backslash/encoded chars). Set AI_ALLOW_NON_LOCAL_LLM=1 to deliberately override."
      fi
    fi
    # Fail fast before booting any JVM if the RPS list asks the real model for more than
    # it can sustain (the per-cell guard in run_cell is the defense-in-depth backstop).
    if [[ -n "${AI_LIVE_MAX_RPS:-}" ]]; then
      local _r
      for _r in $RPS_LIST; do
        (( _r <= AI_LIVE_MAX_RPS )) || die "live backend: rps=${_r} in RPS list exceeds AI_LIVE_MAX_RPS=${AI_LIVE_MAX_RPS}; live cells are low-rate realism/prompt probes, not the H1 cliff"
      done
    fi
    # max-tokens is bound per token-level at app start (start_stack exports LIVE_LLM_MAX_TOKENS
    # = the cell's tokens_per_response), so report the level list here rather than a single
    # value that would misread when AI_TOKENS_LEVELS carries more than one level.
    log "LIVE backend: base_url=${LIVE_LLM_BASE_URL} model=${LIVE_LLM_MODEL} max_tokens(per-level)=[${TOKENS_LEVELS}] live_max_rps=${AI_LIVE_MAX_RPS:-unset}; ensure realism-backend.sh is up before this runs."
  fi

  mkdir -p "$CAMPAIGN_ROOT/$AI_CAMPAIGN"
  COMMAND_LOG="$CAMPAIGN_ROOT/$AI_CAMPAIGN/commands.tsv"
  LOG_PATH="$CAMPAIGN_ROOT/$AI_CAMPAIGN/run.log"
  touch "$COMMAND_LOG"
  touch "$LOG_PATH"

  source "$REPO/testing/performance/lib/cleanup.sh"
  cleanup_sweep_init
  trap 'on_exit' EXIT

  cd "$REPO"
  # Pin the build/run JDK to the rig's sdkman Temurin 21 (matches the other four workload
  # run.sh scripts and the AGENTS.md pinned-versions table). Without this the default sdkman
  # `current` symlink resolves to JDK 25, which the service Lombok/Mockito toolchain rejects.
  # Never brew-install a JDK — the pinned 21 already exists under sdkman.
  export JAVA_HOME=/opt/homebrew/Cellar/sdkman-cli/5.19.0/libexec/candidates/java/21.0.7-tem
  raise_fd_limit

  log "AI campaign start: profile=${profile}, campaign=${AI_CAMPAIGN}, topology=${TOPOLOGY}, dry_run=${AI_DRY_RUN}"
  log "matrix: stacks='${STACKS[*]}' workloads='${WORKLOAD_VALUES[*]}' transports='${TRANSPORT_VALUES[*]}' rps='${RPS_VALUES[*]}' pools='${POOL_VALUES[*]}' tokens_per_response='${TOKEN_VALUES[*]}' prompt_variants='${PROMPT_VARIANT_VALUES[*]}' backend=${AI_BACKEND_EFFECTIVE} runs=${RUNS}"
  log "method params: queue_capacity=${AI_QUEUE_CAPACITY}, reject_policy=${AI_REJECT_POLICY}, calibration_tag=${AI_CALIBRATION_TAG}, k6_mode=${AI_K6_MODE}, prealloc=${AI_PREALLOC_VUS:-auto}, max_vus=${AI_MAX_VUS:-auto}"
  # W0-residency line is only meaningful when the profile actually runs a W0 cell;
  # the W1/W2-only companion profiles (output-token-scout, reactive-ceiling, buffered-rep)
  # skip it so the unattended log can't be misread as a phantom W0-long residency.
  if [[ " ${WORKLOAD_VALUES[*]} " == *" W0 "* ]]; then
    local _mt="${AI_MOCK_TOKENS:-150}" _mi="${AI_MOCK_ITL_MS:-40}" _w0v _w0sleep _w0meas
    (( _mi <= 20 )) && _w0v="W0-equal" || _w0v="W0-long"
    _w0sleep=$(( _mt * _mi ))
    # W0 measured residency = tokens×itlMs sleep + ~390ms SSE-emit overhead for 150 sends
    # (pilot-measured 2026-06-14: rps=50 p50=2343ms at 150×13). The cliff onset follows the
    # MEASURED residency, so rps_crit uses it, not the bare sleep budget.
    _w0meas=$(( _w0sleep + 390 ))
    log "W0 timing: mock_tokens=${_mt} mock_itl_ms=${_mi} → sleep-budget≈${_w0sleep}ms, measured residency ≈${_w0meas}ms (incl ~390ms SSE-emit overhead) (${_w0v}); for pool=${POOL_VALUES[0]} rps_crit≈$(( POOL_VALUES[0] * 1000 / _w0meas ))"
  fi

  infra_up

  # tokens-per-response is a JVM-startup config, so each level wraps the pool/stack
  # loop and reboots the app; prompt-variant is per-request, so it nests innermost next
  # to rps. Standard profiles run a single token level (150) and single prompt variant
  # (canonical), so the matrix collapses to the original pool→stack→workload→rps shape.
  local token_level pool stack workload_id transport prompt_variant rps
  for token_level in "${TOKEN_VALUES[@]}"; do
    require_positive_int "tokens-per-response level" "$token_level"
    for pool in "${POOL_VALUES[@]}"; do
      require_positive_int "pool size" "$pool"
      for stack in "${STACKS[@]}"; do
        start_stack "$stack" "$pool" "$token_level"
        if [[ "$AI_DRY_RUN" != "1" ]]; then
          wait_for_service
          assert_effective_config "$stack" "$pool" "$token_level"
        fi
        for workload_id in "${WORKLOAD_VALUES[@]}"; do
          for transport in "${TRANSPORT_VALUES[@]}"; do
            for prompt_variant in "${PROMPT_VARIANT_VALUES[@]}"; do
              for rps in "${RPS_VALUES[@]}"; do
                require_positive_int "target RPS" "$rps"
                run_cell "$stack" "$pool" "$workload_id" "$transport" "$rps" "$token_level" "$prompt_variant"
              done
            done
          done
        done
        stop_stack "$stack"
      done
    done
  done

  infra_down

  cleanup_sweep_finish
  trap - EXIT
  log "AI campaign complete: log=${LOG_PATH}, commands=${COMMAND_LOG}"
}

main "$@"
