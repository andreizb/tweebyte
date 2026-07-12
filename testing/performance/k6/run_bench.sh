#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
WORKLOADS_ROOT="${SCRIPT_DIR}/workloads"
RESULTS_ROOT="${RESULTS_ROOT:-${REPO_ROOT}/testing-results/performance/k6}"

K6_BIN="${K6_BIN:-/opt/homebrew/bin/k6}"
if [[ ! -x "$K6_BIN" ]]; then
  if command -v k6 >/dev/null 2>&1; then
    K6_BIN="$(command -v k6)"
  else
    echo "ERROR: k6 not found. brew install k6"
    exit 1
  fi
fi

WORKLOAD="${WORKLOAD:-cacheread-following}"
BASE_URL="${BASE_URL:-}"
ACTUATOR_URL="${ACTUATOR_URL:-}"
BENCHMARK_TOPOLOGY="${BENCHMARK_TOPOLOGY:-${TOPOLOGY:-unknown}}"
# Infra provider follows the app topology: native-local runs host-native infra (no
# Docker), local-app/all-docker run Docker infra. An explicit env override still wins.
BENCHMARK_INFRA_TOPOLOGY="${BENCHMARK_INFRA_TOPOLOGY:-$([[ "$BENCHMARK_TOPOLOGY" == "native-local" ]] && echo native-local || echo docker)}"

# --- Execution-mode flags (see AGENTS.md §"Dockerized k6 (mandatory at high VUs
# for some workloads)") --------------------------------------------------------
# MODE=local: invoke host-side $K6_BIN. Works up to ~400-800 VUs on macOS.
# MODE=docker: invoke k6 as a sibling container inside the compose network.
#   Required for canonical sweeps that reach conc>=800 because macOS's local
#   transport exhausts ephemeral ports / fd limits and produces dial-timeout
#   storms that look like SUT failures. Docker mode bypasses two host-side
#   failure modes:
#     1. host-side fd starvation -> --ulimit nofile=1048576:1048576 on the k6 container
#     2. Docker embedded DNS dying at >500 VUs -> resolve TARGET_CONTAINER's
#        IP up front and rewrite BASE_URL to use that IP, skipping the
#        DNS round-trip per request entirely.
#   ACTUATOR_URL is NOT rewritten because the bash wrapper's curl probes
#   (readiness_gate, prom_poll_loop, sample_resources) run host-side and reach
#   the SUT via the host-published localhost port. On Docker Desktop / macOS,
#   container IPs are not directly reachable from the host bridge, so host-side
#   probes MUST keep using localhost:port.
MODE="${MODE:-local}"
COMPOSE_NETWORK="${COMPOSE_NETWORK:-tweebyte_default}"
TARGET_CONTAINER="${TARGET_CONTAINER:-tweebyte-interaction-service-1}"
K6_DOCKER_IMAGE="${K6_DOCKER_IMAGE:-grafana/k6:1.7.1}"
# Set by resolve_docker_addressing(); empty in local mode.
K6_BASE_URL=""
DOCKER_K6_ARGS=()

CONCURRENCIES_RAW="${CONCURRENCIES:-1 5 10 15 20 25 50 75 100 200 400 600 800 1000}"
RUNS="${RUNS:-5}"
WARMUP_SECS_RAW="${WARMUP_SECS:-60s}"
DURATION="${DURATION:-3m}"
COLLECT_RESOURCES="${COLLECT_RESOURCES:-1}"
SAMPLE_EVERY_SECS="${SAMPLE_EVERY_SECS:-1}"
AUTO_PREPARE="${AUTO_PREPARE:-1}"

PATH_PREFIX="${PATH_PREFIX:-}"
KEYS_FILE="${KEYS_FILE:-}"
KEY_COUNT="${KEY_COUNT:-}"
FOLLOWS_PER_KEY="${FOLLOWS_PER_KEY:-}"
TOTAL_KEYS="${TOTAL_KEYS:-}"
HOT_COUNT="${HOT_COUNT:-}"
HOT_RATIO="${HOT_RATIO:-0.9}"

API_PATH="${API_PATH:-}"

SCRIPT_JS=""
WORKLOAD_SLUG=""
RECAP_ROWS=()
CONCURRENCY_LIST=()

usage() {
  cat <<'EOF'
Usage:
  ./testing/performance/k6/run_bench.sh \
    --workload <cacheread-following|blockio-file-download|cpu-image-preview|http-fanout-get-tweet|ai-stream-summarize> \
    --base-url <url> \
    --concurrencies "1000" \
    --runs 1 \
    --warmup 60s \
    --duration 3m \
    [workload-specific flags...]

Generic flags:
  --workload <name>
    cacheread-following     Redis-backed interaction-service cache benchmark (I/O-bound)
    blockio-file-download   Blocking download benchmark (blocking I/O)
    cpu-image-preview       Gateway image filter benchmark (CPU-bound)
    http-fanout-get-tweet   Cross-service fan-out single-tweet read (GET /tweets/{id})
    ai-stream-summarize     AI streaming benchmark (open-loop, SSE + buffered + cancel, W0/W1/W2)
  --base-url <url>
  --actuator-url <url>
  --concurrencies "1 5 10"
  --runs <count>
  --warmup <duration>
  --duration <duration>
  --collect-resources <0|1>
  --sample-every <seconds>
  --results-root <path>
  --auto-prepare <0|1>

Execution mode (default: local; see AGENTS.md §"Dockerized k6"):
  --mode <local|docker>          Where to run k6.
  --compose-network <name>       Docker network for sibling container (default: tweebyte_default).
  --target-container <name>      Container to resolve IP from (default: tweebyte-interaction-service-1).
  --docker-image <name>          k6 image (default: grafana/k6:1.7.1).

cacheread-following flags:
  --path-prefix <path>
  --keys-file <path>
  --key-count <N>
  --follows-per-key <K>
  --total-keys <count>
  --hot-count <count>
  --hot-ratio <ratio>

blockio-file-download flags:
  --api-path <path>

cpu-image-preview flags:
  --api-path <path>              /media/{srcId}/preview — the seeded source original

ai-stream-summarize flags:
  --ai-workload <W0|W1|W2>       W0=mock-stream baseline, W1=pure chat, W2=chat+tool-call
  --ai-workload-variant <name>   Descriptive variant label recorded in the summary JSON
                                 (e.g. W0-equal vs W0-long). Defaults: derived in script.js.
  --ai-transport <sse|buffered>  sse=streaming (default), buffered=REST control.
                                 Valid combos: W0×sse, W1×{sse,buffered}, W2×sse.
                                 W0 has no buffered endpoint; W2+buffered is refused
                                 because there is no stream for the mid-stream tool call.
  --ai-cancel-rate <0.0..1.0>    Fraction of requests to abort mid-stream (default 0.0)
  --ai-target-rps <rate>         Arrival rate; falls back to --concurrencies if omitted
  --ai-user-id <uuid>            UUID to use for W2 tool call
  --ai-prompt <text>             Chat prompt text
  --ai-mock-tokens <N>           W0 token count (default 150)
  --ai-mock-itl-ms <ms>          W0 inter-token latency in ms (default 40)
  --ai-tokens-per-response <N>   Output-token axis metadata: the app's effective
                                 app.ai.mock.tokens-per-response (W1/W2 mock output count).
                                 Recorded in the summary JSON as tokens_per_response so the
                                 analysis pipeline separates short/canonical/long output cells.
                                 k6 does not change the count — set AI_MOCK_TOKENS_PER_RESPONSE
                                 on the app to actually change it; pass the same value here so
                                 the recorded metadata matches. Default 150.
  --ai-prompt-variant <name>     Prompt-length axis label (canonical|short|medium|long, or any
                                 tag). Recorded as prompt_variant; pair with --ai-prompt for the
                                 actual text. Default canonical.
  --ai-backend <mock|live>       Backend tag recorded as ai_backend so mock and live cells never
                                 pool. This is the RECORDED tag; AI_BACKEND on the app selects the
                                 real backend. Default mock.
  --ai-prealloc-vus <N>          Override k6 preAllocatedVUs for AI open-loop runs
  --ai-max-vus <N>               Override k6 maxVUs for AI open-loop runs
  --ai-calibration-tag <name>    Tag identifying the mock-parameter batch this run
                                 belongs to. Surfaced in the k6 JSON summary under
                                 "calibration_tag" so the analysis pipeline can
                                 distinguish e.g. mock-defaults vs qwen-3.5-4b-mlx-v1
                                 cells without pooling them. Default: mock-defaults.
  --ai-campaign <name>           Campaign label for this run (e.g. "headline-5rep-2026-04-28"
                                 or "diagonal-2026-04-28"). Distinct from calibration_tag:
                                 same calibration parameters can appear in multiple
                                 campaigns, and the analysis pipeline treats them as
                                 separate cells. Default: ad-hoc.
  --readiness-grace <secs>       Seconds the SUT must respond to /actuator/health = UP
                                 and /actuator/prometheus = 200 before warmup begins.
                                 If either fails during this window, the cell is
                                 skipped (cell_status=READINESS_FAIL). Default: 5.
  --prom-poll-secs <secs>        In-run Prometheus poll interval; produces a per-run
                                 time-series at <OUT>/<c>_<i>_prom.csv containing
                                 pool_active / queue_depth / pool_max_size /
                                 queue_capacity / rejections_total /
                                 tasks_completed columns. Default: 1.
EOF
}

secs_from_duration() {
  local d="$1"
  if [[ "$d" =~ ^[0-9]+$ ]]; then
    echo "$d"
  elif [[ "$d" =~ ^([0-9]+)s$ ]]; then
    echo "${BASH_REMATCH[1]}"
  elif [[ "$d" =~ ^([0-9]+)m$ ]]; then
    echo "$(( ${BASH_REMATCH[1]} * 60 ))"
  else
    echo "${d%s}"
  fi
}

resolve_path() {
  local value="$1"
  if [[ "$value" == /* ]]; then
    printf '%s\n' "$value"
  else
    printf '%s\n' "${REPO_ROOT}/${value}"
  fi
}

normalize_bool() {
  local name="$1"
  local value="$2"
  case "$value" in
    0|1) printf '%s\n' "$value" ;;
    true|TRUE|yes|YES) printf '1\n' ;;
    false|FALSE|no|NO) printf '0\n' ;;
    *)
      echo "ERROR: ${name} must be 0/1/true/false, got '${value}'." >&2
      exit 1
      ;;
  esac
}

require_ratio() {
  local name="$1"
  local value="$2"
  python3 - "$name" "$value" <<'PY'
import sys

name = sys.argv[1]
value = sys.argv[2]
try:
    parsed = float(value)
except ValueError:
    print(f"ERROR: {name} must be a number between 0 and 1, got '{value}'.", file=sys.stderr)
    sys.exit(1)

if not (0.0 <= parsed <= 1.0):
    print(f"ERROR: {name} must be between 0 and 1, got '{value}'.", file=sys.stderr)
    sys.exit(1)
PY
}

require_positive_int() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]] || (( value <= 0 )); then
    echo "ERROR: ${name} must be a positive integer, got '${value}'." >&2
    exit 1
  fi
}

default_hot_count() {
  local total_keys="$1"
  local value=$(( total_keys / 10 ))
  if (( value < 1 )); then
    value=1
  fi
  printf '%s\n' "$value"
}

parse_args() {
  while (($# > 0)); do
    case "$1" in
      --workload) WORKLOAD="$2"; shift 2 ;;
      --base-url) BASE_URL="$2"; shift 2 ;;
      --actuator-url) ACTUATOR_URL="$2"; shift 2 ;;
      --concurrencies) CONCURRENCIES_RAW="$2"; shift 2 ;;
      --runs) RUNS="$2"; shift 2 ;;
      --warmup) WARMUP_SECS_RAW="$2"; shift 2 ;;
      --duration) DURATION="$2"; shift 2 ;;
      --collect-resources) COLLECT_RESOURCES="$2"; shift 2 ;;
      --sample-every) SAMPLE_EVERY_SECS="$2"; shift 2 ;;
      --results-root) RESULTS_ROOT="$(resolve_path "$2")"; shift 2 ;;
      --auto-prepare) AUTO_PREPARE="$2"; shift 2 ;;

      --mode) MODE="$2"; shift 2 ;;
      --compose-network) COMPOSE_NETWORK="$2"; shift 2 ;;
      --target-container) TARGET_CONTAINER="$2"; shift 2 ;;
      --docker-image) K6_DOCKER_IMAGE="$2"; shift 2 ;;

      --path-prefix) PATH_PREFIX="$2"; shift 2 ;;
      --keys-file) KEYS_FILE="$2"; shift 2 ;;
      --key-count) KEY_COUNT="$2"; shift 2 ;;
      --follows-per-key) FOLLOWS_PER_KEY="$2"; shift 2 ;;
      --total-keys) TOTAL_KEYS="$2"; shift 2 ;;
      --hot-count) HOT_COUNT="$2"; shift 2 ;;
      --hot-ratio) HOT_RATIO="$2"; shift 2 ;;

      --api-path) API_PATH="$2"; shift 2 ;;

      --ai-workload) AI_WORKLOAD="$2"; shift 2 ;;
      --ai-workload-variant) AI_WORKLOAD_VARIANT="$2"; shift 2 ;;
      --ai-transport) AI_TRANSPORT="$2"; shift 2 ;;
      --ai-cancel-rate) AI_CANCEL_RATE="$2"; shift 2 ;;
      --ai-target-rps) AI_TARGET_RPS="$2"; shift 2 ;;
      --ai-user-id) AI_USER_ID="$2"; shift 2 ;;
      --ai-prompt) AI_PROMPT="$2"; shift 2 ;;
      --ai-mock-tokens) AI_MOCK_TOKENS="$2"; shift 2 ;;
      --ai-mock-itl-ms) AI_MOCK_ITL_MS="$2"; shift 2 ;;
      --ai-tokens-per-response) AI_TOKENS_PER_RESPONSE="$2"; shift 2 ;;
      --ai-prompt-variant) AI_PROMPT_VARIANT="$2"; shift 2 ;;
      --ai-backend) AI_BACKEND_TAG="$2"; shift 2 ;;
      --ai-prealloc-vus) AI_PREALLOC_VUS="$2"; shift 2 ;;
      --ai-max-vus) AI_MAX_VUS="$2"; shift 2 ;;
      --ai-pool-size-tag) AI_POOL_SIZE_TAG="$2"; shift 2 ;;
      --ai-stack-tag) AI_STACK_TAG="$2"; shift 2 ;;
      --ai-reject-policy-tag) AI_REJECT_POLICY_TAG="$2"; shift 2 ;;
      --ai-calibration-tag) AI_CALIBRATION_TAG="$2"; shift 2 ;;
      --ai-campaign) AI_CAMPAIGN="$2"; shift 2 ;;
      --readiness-grace) READINESS_GRACE_SECS="$2"; shift 2 ;;
      --prom-poll-secs) PROM_POLL_SECS="$2"; shift 2 ;;

      --help|-h)
        usage
        exit 0
        ;;
      *)
        echo "ERROR: unknown argument '$1'." >&2
        usage
        exit 1
        ;;
    esac
  done
}

validate_generic_args() {
  local concurrency

  COLLECT_RESOURCES="$(normalize_bool "COLLECT_RESOURCES" "$COLLECT_RESOURCES")"
  AUTO_PREPARE="$(normalize_bool "AUTO_PREPARE" "$AUTO_PREPARE")"
  require_positive_int "RUNS" "$RUNS"
  require_positive_int "SAMPLE_EVERY_SECS" "$SAMPLE_EVERY_SECS"

  read -r -a CONCURRENCY_LIST <<<"$CONCURRENCIES_RAW"
  if ((${#CONCURRENCY_LIST[@]} == 0)); then
    echo "ERROR: at least one concurrency value is required." >&2
    exit 1
  fi
  for concurrency in "${CONCURRENCY_LIST[@]}"; do
    require_positive_int "CONCURRENCY" "$concurrency"
  done

  case "$MODE" in
    local|docker) ;;
    *)
      echo "ERROR: --mode must be 'local' or 'docker' (was '$MODE')." >&2
      exit 1
      ;;
  esac
}

# Build the docker-side addressing used by the k6 container. Skip entirely if
# MODE=local. Called after configure_workload() so $BASE_URL is finalised.
resolve_docker_addressing() {
  if [[ "$MODE" != "docker" ]]; then
    K6_BASE_URL="$BASE_URL"
    return
  fi

  if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: --mode docker requires the docker CLI on PATH." >&2
    exit 1
  fi

  local target_ip
  target_ip="$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$TARGET_CONTAINER" 2>/dev/null | tr -d '[:space:]')"
  if [[ -z "$target_ip" ]]; then
    echo "ERROR: cannot resolve container IP for '$TARGET_CONTAINER'. Is the stack up under project ${COMPOSE_PROJECT_NAME:-tweebyte}?" >&2
    exit 1
  fi

  # Replace localhost / 127.0.0.1 in the URL host part with the container IP.
  # BASE_URL stays as-is for any host-side logging; K6_BASE_URL is what k6 sees.
  # Delimiter is '#' so the alternation pipes in the pattern don't clash with
  # the delimiter (BSD sed -E treats the s|||| form as pattern-end on the
  # first pipe and explodes with "parentheses not balanced").
  local rewritten
  rewritten="$(echo "$BASE_URL" | sed -E "s#//(localhost|127\\.0\\.0\\.1)([:/])#//${target_ip}\\2#")"
  K6_BASE_URL="$rewritten"

  # Rewrite any keys-file path under WORKLOAD_HOME to /work/... so k6 inside the
  # container sees it after the bind mount. Only paths rooted at $WORKLOAD_HOME are
  # rewritten; explicit paths the operator passed elsewhere are left alone (and will
  # fail-loud rather than silently misresolve).
  if [[ -n "$KEYS_FILE" && "$KEYS_FILE" == "$WORKLOAD_HOME"/* ]]; then
    KEYS_FILE="/work/${KEYS_FILE#$WORKLOAD_HOME/}"
  fi

  # Pre-baked docker invocation; the cell loop appends `run <args> /work/script.js`.
  DOCKER_K6_ARGS=(
    run --rm
    --network "$COMPOSE_NETWORK"
    --ulimit "nofile=1048576:1048576"
    -v "${WORKLOAD_HOME}:/work"
    -w /work
    "$K6_DOCKER_IMAGE"
  )

  echo "MODE=docker resolution:"
  echo "  TARGET_CONTAINER=$TARGET_CONTAINER"
  echo "  TARGET_IP=$target_ip"
  echo "  COMPOSE_NETWORK=$COMPOSE_NETWORK"
  echo "  K6_BASE_URL=$K6_BASE_URL  (BASE_URL=$BASE_URL kept for host-side probes)"
  echo "  KEYS_FILE=$KEYS_FILE"
}

prepare_following_cache_if_needed() {
  if [[ -z "$KEYS_FILE" ]]; then
    if [[ -z "$KEY_COUNT" ]]; then
      KEY_COUNT="9999"
    fi
    if [[ -z "$FOLLOWS_PER_KEY" ]]; then
      FOLLOWS_PER_KEY="10"
    fi
    require_positive_int "KEY_COUNT" "$KEY_COUNT"
    require_positive_int "FOLLOWS_PER_KEY" "$FOLLOWS_PER_KEY"
    KEYS_FILE="${WORKLOAD_HOME}/payload/n${KEY_COUNT}_k${FOLLOWS_PER_KEY}/keys.txt"
  else
    KEYS_FILE="$(resolve_path "$KEYS_FILE")"
  fi

  if [[ ! -f "$KEYS_FILE" ]]; then
    if [[ "$AUTO_PREPARE" != "1" ]]; then
      echo "ERROR: missing keys file '$KEYS_FILE' and auto-prepare is disabled." >&2
      exit 1
    fi

    if [[ -z "$KEY_COUNT" || -z "$FOLLOWS_PER_KEY" ]]; then
      echo "ERROR: auto-prepare for cacheread-following requires --key-count and --follows-per-key when the keys file is missing." >&2
      exit 1
    fi

    echo "Auto-preparing cacheread-following payload..."
    python3 "${WORKLOAD_HOME}/prepare.py" \
      --key-count "$KEY_COUNT" \
      --follows-per-key "$FOLLOWS_PER_KEY" \
      --keys-out "$KEYS_FILE"
  fi
}

prepare_ai_streaming_user() {
  # Seed the deterministic AI benchmark user so W2 tool-use calls don't 404.
  # Idempotent (ON CONFLICT DO NOTHING) and cheap (one row), so it always runs
  # regardless of --auto-prepare. The DB is shared by both stacks, so one seed
  # covers async and reactive.
  echo "Seeding ai-stream-summarize benchmark user..."
  python3 "${WORKLOAD_HOME}/prepare.py" seed \
    --user-id "${AI_USER_ID:-00000000-0000-0000-0000-000000000001}"
}

configure_workload() {
  local lines

  # Per-workload directory layout:
  #   workloads/<name>/
  #     script.js     — k6 test script
  #     prepare.py    — payload seeder (only present for workloads that need one)
  #     run.sh        — workload entry point
  #     payload/      — generated payloads (gitignored)
  WORKLOAD_HOME="${WORKLOADS_ROOT}/${WORKLOAD}"
  if [[ ! -d "$WORKLOAD_HOME" ]]; then
    echo "ERROR: unsupported workload '$WORKLOAD'. Use cacheread-following, blockio-file-download, cpu-image-preview, http-fanout-get-tweet, or ai-stream-summarize." >&2
    exit 1
  fi

  SCRIPT_JS="${WORKLOAD_HOME}/script.js"
  [[ -f "$SCRIPT_JS" ]] || { echo "ERROR: missing workload script: $SCRIPT_JS" >&2; exit 1; }

  WORKLOAD_SLUG="${WORKLOAD//-/_}"
  if [[ -z "$BASE_URL" ]]; then
    echo "ERROR: --base-url is required" >&2
    exit 1
  fi

  case "$WORKLOAD" in
    cacheread-following)
      if [[ -z "$PATH_PREFIX" ]]; then
        echo "ERROR: --path-prefix is required for cacheread-following" >&2
        exit 1
      fi
      require_ratio "HOT_RATIO" "$HOT_RATIO"
      prepare_following_cache_if_needed
      [[ -f "$KEYS_FILE" ]] || { echo "ERROR: missing KEYS_FILE: $KEYS_FILE" >&2; exit 1; }

      lines="$(wc -l < "$KEYS_FILE" | tr -d ' ')"
      require_positive_int "KEYS_FILE line count" "$lines"

      if [[ -z "$TOTAL_KEYS" ]]; then
        TOTAL_KEYS="$lines"
      fi
      require_positive_int "TOTAL_KEYS" "$TOTAL_KEYS"
      if (( TOTAL_KEYS > lines )); then
        echo "TOTAL_KEYS=$TOTAL_KEYS > file lines=$lines; using TOTAL_KEYS=$lines"
        TOTAL_KEYS="$lines"
      fi

      if [[ -z "$HOT_COUNT" ]]; then
        HOT_COUNT="$(default_hot_count "$TOTAL_KEYS")"
      fi
      require_positive_int "HOT_COUNT" "$HOT_COUNT"
      if (( HOT_COUNT > TOTAL_KEYS )); then
        echo "HOT_COUNT=$HOT_COUNT > TOTAL_KEYS=$TOTAL_KEYS; using HOT_COUNT=$TOTAL_KEYS"
        HOT_COUNT="$TOTAL_KEYS"
      fi
      ;;
    http-fanout-get-tweet)
      # Generic-GET workload (GET /tweets/{id}); same env shape as cacheread-following, but the
      # http-fanout-get-tweet run.sh seeds its own 3-DB fixture, so no prepare_*_if_needed hook here.
      if [[ -z "$PATH_PREFIX" ]]; then
        echo "ERROR: --path-prefix is required for http-fanout-get-tweet" >&2
        exit 1
      fi
      require_ratio "HOT_RATIO" "$HOT_RATIO"
      [[ -f "$KEYS_FILE" ]] || { echo "ERROR: missing KEYS_FILE: $KEYS_FILE" >&2; exit 1; }

      lines="$(wc -l < "$KEYS_FILE" | tr -d ' ')"
      require_positive_int "KEYS_FILE line count" "$lines"

      if [[ -z "$TOTAL_KEYS" ]]; then
        TOTAL_KEYS="$lines"
      fi
      require_positive_int "TOTAL_KEYS" "$TOTAL_KEYS"
      if (( TOTAL_KEYS > lines )); then
        echo "TOTAL_KEYS=$TOTAL_KEYS > file lines=$lines; using TOTAL_KEYS=$lines"
        TOTAL_KEYS="$lines"
      fi

      if [[ -z "$HOT_COUNT" ]]; then
        HOT_COUNT="$(default_hot_count "$TOTAL_KEYS")"
      fi
      require_positive_int "HOT_COUNT" "$HOT_COUNT"
      if (( HOT_COUNT > TOTAL_KEYS )); then
        echo "HOT_COUNT=$HOT_COUNT > TOTAL_KEYS=$TOTAL_KEYS; using HOT_COUNT=$TOTAL_KEYS"
        HOT_COUNT="$TOTAL_KEYS"
      fi
      ;;
    blockio-file-download)
      if [[ -z "$API_PATH" ]]; then
        echo "ERROR: --api-path is required for blockio-file-download" >&2
        exit 1
      fi
      ;;
    cpu-image-preview)
      if [[ -z "$API_PATH" ]]; then
        echo "ERROR: --api-path is required for cpu-image-preview" >&2
        exit 1
      fi
      ;;
    ai-stream-summarize)
      if [[ -n "${AI_PREALLOC_VUS:-}" ]]; then
        require_positive_int "AI_PREALLOC_VUS" "$AI_PREALLOC_VUS"
      fi
      if [[ -n "${AI_MAX_VUS:-}" ]]; then
        require_positive_int "AI_MAX_VUS" "$AI_MAX_VUS"
      fi
      prepare_ai_streaming_user
      ;;
    *)
      echo "ERROR: unsupported workload '$WORKLOAD'." >&2
      exit 1
      ;;
  esac
}

print_config() {
  echo "Using:"
  echo "  WORKLOAD=$WORKLOAD"
  echo "  MODE=$MODE"
  echo "  BENCHMARK_TOPOLOGY=$BENCHMARK_TOPOLOGY"
  echo "  BASE_URL=$BASE_URL"
  if [[ "$MODE" == "docker" ]]; then
    echo "  K6_BASE_URL=$K6_BASE_URL  (passed to k6 via -e BASE_URL=...)"
    echo "  COMPOSE_NETWORK=$COMPOSE_NETWORK"
    echo "  TARGET_CONTAINER=$TARGET_CONTAINER"
    echo "  K6_DOCKER_IMAGE=$K6_DOCKER_IMAGE"
  fi
  echo "  ACTUATOR_URL=$ACTUATOR_URL"
  echo "  SCRIPT_JS=$SCRIPT_JS"
  echo "  CONCURRENCIES=${CONCURRENCY_LIST[*]}"
  echo "  RUNS=$RUNS"
  echo "  WARMUP=$WARMUP_SECS_RAW"
  echo "  DURATION=$DURATION"
  echo "  COLLECT_RESOURCES=$COLLECT_RESOURCES"
  echo "  AUTO_PREPARE=$AUTO_PREPARE"
  if [[ "$COLLECT_RESOURCES" == "1" ]]; then
    echo "  SAMPLE_EVERY_SECS=$SAMPLE_EVERY_SECS"
  fi

  case "$WORKLOAD" in
    cacheread-following)
      echo "  PATH_PREFIX=$PATH_PREFIX"
      echo "  KEYS_FILE=$KEYS_FILE"
      echo "  TOTAL_KEYS=$TOTAL_KEYS"
      echo "  HOT_COUNT=$HOT_COUNT"
      echo "  HOT_RATIO=$HOT_RATIO"
      if [[ -n "$KEY_COUNT" ]]; then
        echo "  KEY_COUNT=$KEY_COUNT"
      fi
      if [[ -n "$FOLLOWS_PER_KEY" ]]; then
        echo "  FOLLOWS_PER_KEY=$FOLLOWS_PER_KEY"
      fi
      ;;
    http-fanout-get-tweet)
      echo "  PATH_PREFIX=$PATH_PREFIX"
      echo "  KEYS_FILE=$KEYS_FILE"
      echo "  TOTAL_KEYS=$TOTAL_KEYS"
      echo "  HOT_COUNT=$HOT_COUNT"
      echo "  HOT_RATIO=$HOT_RATIO"
      ;;
    blockio-file-download)
      echo "  API_PATH=$API_PATH"
      ;;
    cpu-image-preview)
      echo "  API_PATH=$API_PATH"
      ;;
    ai-stream-summarize)
      echo "  AI_WORKLOAD=${AI_WORKLOAD:-W1}"
      echo "  AI_WORKLOAD_VARIANT=${AI_WORKLOAD_VARIANT:-(derived)}"
      echo "  AI_MOCK_TOKENS=${AI_MOCK_TOKENS:-150}"
      echo "  AI_MOCK_ITL_MS=${AI_MOCK_ITL_MS:-40}"
      echo "  AI_TOKENS_PER_RESPONSE=${AI_TOKENS_PER_RESPONSE:-150}"
      echo "  AI_PROMPT_VARIANT=${AI_PROMPT_VARIANT:-canonical}"
      echo "  AI_BACKEND_TAG=${AI_BACKEND_TAG:-mock}"
      echo "  AI_TRANSPORT=${AI_TRANSPORT:-sse}"
      echo "  AI_CANCEL_RATE=${AI_CANCEL_RATE:-0.0}"
      echo "  AI_TARGET_RPS=${AI_TARGET_RPS:-unset→CONCURRENCY}"
      echo "  AI_USER_ID=${AI_USER_ID:-00000000-0000-0000-0000-000000000001}"
      echo "  AI_PREALLOC_VUS=${AI_PREALLOC_VUS:-auto}"
      echo "  AI_MAX_VUS=${AI_MAX_VUS:-auto}"
      echo "  AI_CALIBRATION_TAG=${AI_CALIBRATION_TAG:-mock-defaults}"
      echo "  AI_CAMPAIGN=${AI_CAMPAIGN:-ad-hoc}"
      echo "  READINESS_GRACE_SECS=${READINESS_GRACE_SECS:-5}"
      echo "  PROM_POLL_SECS=${PROM_POLL_SECS:-1}"
      ;;
  esac

  echo "  Results: $OUT_DIR"
  echo
}

sample_resources() {
  local k6_pid="$1" c="$2" i="$3" csv="$4" actuator="$5" step="$6"

  echo "ts_iso,concurrency,run,cpu_usage_pct,heap_used_bytes,heap_used_mb,heap_committed_bytes,heap_committed_mb" > "$csv"

  while kill -0 "$k6_pid" >/dev/null 2>&1; do
    local ts_iso cpu_raw cpu_pct heap_bytes heap_mb committed_bytes committed_mb page
    ts_iso="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

    if ! page="$(curl -fsS "$actuator" 2>/dev/null)"; then
      echo "$ts_iso,$c,$i,NaN,NaN,NaN,NaN,NaN" >> "$csv"
      sleep "$step"
      continue
    fi

    cpu_raw="$(printf "%s\n" "$page" | awk '/^process_cpu_usage[[:space:]]/{print $2; exit}')"
    if [[ -z "${cpu_raw:-}" ]]; then
      cpu_pct="NaN"
    else
      cpu_pct="$(awk -v v="$cpu_raw" 'BEGIN{printf "%.3f", v*100.0}')"
    fi

    heap_bytes="$(printf "%s\n" "$page" | perl -ne '
      if(/^jvm_memory_used_bytes\{[^}]*area="heap"[^}]*\}\s+([0-9.]+(?:[eE][+-]?\d+)?)/){
        $s += $1
      }
      END { printf("%.0f", $s || 0) }
    ')"
    heap_mb="$(awk -v v="$heap_bytes" 'BEGIN{printf "%.2f", v/1024/1024}')"

    committed_bytes="$(printf "%s\n" "$page" | perl -ne '
      if(/^jvm_memory_committed_bytes\{[^}]*area="heap"[^}]*\}\s+([0-9.]+(?:[eE][+-]?\d+)?)/){
        $s += $1
      }
      END { printf("%.0f", $s || 0) }
    ')"
    committed_mb="$(awk -v v="$committed_bytes" 'BEGIN{printf "%.2f", v/1024/1024}')"

    echo "$ts_iso,$c,$i,$cpu_pct,$heap_bytes,$heap_mb,$committed_bytes,$committed_mb" >> "$csv"
    sleep "$step"
  done
}

parse_k6_summary() {
  local run_txt="$1" main_secs="$2"

  python3 - "$run_txt" "$main_secs" <<'PY'
import pathlib
import re
import sys

path = pathlib.Path(sys.argv[1])
main_secs = float(sys.argv[2])
text = path.read_text()

def convert_to_ms(raw: str, unit: str) -> str:
    value = float(raw)
    if unit == "s":
        value *= 1000.0
    elif unit == "µs":
        value /= 1000.0
    return f"{value:.3f}"

count_match = re.search(r"main_http_reqs\.*:\s+(?:count=)?(\d+)", text)
avg_match = re.search(r"main_http_req_duration\.*:.*?\bavg=([0-9.]+)(µs|ms|s)", text, re.DOTALL)
p95_match = re.search(r"main_http_req_duration\.*:.*?p\(95\)=([0-9.]+)(µs|ms|s)", text, re.DOTALL)
errors_match = re.search(r"http_req_failed\.*:\s+([0-9.]+%)", text)

count = count_match.group(1) if count_match else "-"
rps = f"{int(count) / main_secs:.2f}" if count != "-" else "-"
avg = convert_to_ms(avg_match.group(1), avg_match.group(2)) if avg_match else "-"
p95 = convert_to_ms(p95_match.group(1), p95_match.group(2)) if p95_match else "-"
errors = errors_match.group(1) if errors_match else "-"

print("\t".join([count, rps, avg, p95, errors]))
PY
}

parse_resources_summary() {
  local res_csv="$1"

  if [[ ! -f "$res_csv" ]]; then
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "-" "-" "-" "-" "-" "-"
    return 0
  fi

  python3 - "$res_csv" <<'PY'
import csv
import sys
from pathlib import Path

path = Path(sys.argv[1])
rows = list(csv.DictReader(path.open()))

cpu = [float(r["cpu_usage_pct"]) for r in rows if r["cpu_usage_pct"] != "NaN" and float(r["cpu_usage_pct"]) >= 0]
heap = [float(r["heap_used_mb"]) for r in rows if r["heap_used_mb"] != "NaN"]
# heap_committed_mb is a newer column; tolerate older CSVs missing it.
committed = [float(r["heap_committed_mb"]) for r in rows
             if r.get("heap_committed_mb") not in (None, "", "NaN")]

if not rows or not cpu or not heap:
    print("\t".join(["-", "-", "-", "-", "-", "-"]))
else:
    avg_committed = f"{sum(committed) / len(committed):.2f}" if committed else "-"
    max_committed = f"{max(committed):.2f}" if committed else "-"
    print("\t".join([
        f"{sum(cpu) / len(cpu):.2f}",
        f"{max(cpu):.2f}",
        f"{sum(heap) / len(heap):.2f}",
        f"{max(heap):.2f}",
        avg_committed,
        max_committed,
    ]))
PY
}

print_pretty_summary() {
  local run_txt="$1" res_csv="$2" c="$3" i="$4"
  local count rps avg p95 errors avg_cpu max_cpu avg_heap max_heap avg_committed max_committed

  IFS=$'\t' read -r count rps avg p95 errors <<<"$(parse_k6_summary "$run_txt" "$MAIN_SECS")"
  IFS=$'\t' read -r avg_cpu max_cpu avg_heap max_heap avg_committed max_committed <<<"$(parse_resources_summary "$res_csv")"

  printf 'Summary: workload=%s concurrency=%s run=%s\n' "$WORKLOAD" "$c" "$i"
  printf '  file=%s\n' "$run_txt"
  printf '  main_http_reqs.count=%s\n' "$count"
  printf '  true_main_rps=%s\n' "$rps"
  printf '  main_avg_ms=%s\n' "$avg"
  printf '  main_p95_ms=%s\n' "$p95"
  printf '  http_req_failed=%s\n' "$errors"

  if [[ "$COLLECT_RESOURCES" == "0" ]]; then
    printf '  resources=%s\n' "skipped"
  elif [[ "$avg_cpu" == "-" ]]; then
    printf '  resources=%s\n' "unavailable"
  else
    printf '  avg_cpu_pct=%s\n' "$avg_cpu"
    printf '  max_cpu_pct=%s\n' "$max_cpu"
    printf '  avg_heap_mb=%s\n' "$avg_heap"
    printf '  max_heap_mb=%s\n' "$max_heap"
    printf '  avg_heap_committed_mb=%s\n' "$avg_committed"
    printf '  max_heap_committed_mb=%s\n' "$max_committed"
  fi

  RECAP_ROWS+=("${c}|${i}|${count}|${rps}|${avg}|${p95}|${errors}|${avg_cpu}|${max_cpu}|${avg_heap}|${max_heap}|${avg_committed}|${max_committed}")
  echo
}

print_final_recap() {
  local row c i count rps avg p95 errors avg_cpu max_cpu avg_heap max_heap avg_committed max_committed

  echo "Final recap:"
  printf '%-12s %-5s %-12s %-12s %-10s %-10s %-10s %-10s %-10s %-12s %-12s %-16s %-16s\n' \
    "concurrency" "run" "count" "true_rps" "avg_ms" "p95_ms" "failed" "cpu_avg" "cpu_max" "heap_avg_mb" "heap_max_mb" "committed_avg_mb" "committed_max_mb"

  if ((${#RECAP_ROWS[@]} == 0)); then
    echo "(no completed cells)"
    echo
    return
  fi

  for row in "${RECAP_ROWS[@]}"; do
    IFS='|' read -r c i count rps avg p95 errors avg_cpu max_cpu avg_heap max_heap avg_committed max_committed <<<"$row"
    printf '%-12s %-5s %-12s %-12s %-10s %-10s %-10s %-10s %-10s %-12s %-12s %-16s %-16s\n' \
      "$c" "$i" "$count" "$rps" "$avg" "$p95" "$errors" "$avg_cpu" "$max_cpu" "$avg_heap" "$max_heap" "${avg_committed:--}" "${max_committed:--}"
  done

  echo
}

build_k6_env_args() {
  local concurrency="$1"
  # K6_BASE_URL is the URL the k6 process uses; differs from BASE_URL only
  # when MODE=docker (resolve_docker_addressing rewrote localhost -> container IP).
  local k6_url="${K6_BASE_URL:-$BASE_URL}"

  K6_ENV_ARGS=(
    -e "BASE_URL=$k6_url"
    -e "CONCURRENCY=$concurrency"
    -e "WARMUP_SECS=$WARMUP_SECS_RAW"
    -e "DURATION=$DURATION"
  )

  case "$WORKLOAD" in
    cacheread-following)
      K6_ENV_ARGS+=(
        -e "PATH_PREFIX=$PATH_PREFIX"
        -e "KEYS_FILE=$KEYS_FILE"
        -e "TOTAL_KEYS=$TOTAL_KEYS"
        -e "HOT_COUNT=$HOT_COUNT"
        -e "HOT_RATIO=$HOT_RATIO"
      )
      ;;
    http-fanout-get-tweet)
      K6_ENV_ARGS+=(
        -e "PATH_PREFIX=$PATH_PREFIX"
        -e "KEYS_FILE=$KEYS_FILE"
        -e "TOTAL_KEYS=$TOTAL_KEYS"
        -e "HOT_COUNT=$HOT_COUNT"
        -e "HOT_RATIO=$HOT_RATIO"
      )
      ;;
    blockio-file-download)
      K6_ENV_ARGS+=(
        -e "API_PATH=$API_PATH"
      )
      ;;
    cpu-image-preview)
      K6_ENV_ARGS+=(
        -e "API_PATH=$API_PATH"
      )
      ;;
    ai-stream-summarize)
      K6_ENV_ARGS+=(
        -e "WORKLOAD=${AI_WORKLOAD:-W1}"
        -e "TRANSPORT=${AI_TRANSPORT:-sse}"
        -e "WORKLOAD_VARIANT=${AI_WORKLOAD_VARIANT:-}"
        -e "CANCEL_RATE=${AI_CANCEL_RATE:-0.0}"
        -e "TARGET_RPS=${AI_TARGET_RPS:-$concurrency}"
        -e "USER_ID=${AI_USER_ID:-00000000-0000-0000-0000-000000000001}"
        -e "MOCK_TOKENS=${AI_MOCK_TOKENS:-150}"
        -e "MOCK_ITL_MS=${AI_MOCK_ITL_MS:-40}"
        -e "TOKENS_PER_RESPONSE=${AI_TOKENS_PER_RESPONSE:-150}"
        -e "PROMPT_VARIANT=${AI_PROMPT_VARIANT:-canonical}"
        -e "AI_BACKEND_TAG=${AI_BACKEND_TAG:-mock}"
        -e "POOL_SIZE_TAG=${AI_POOL_SIZE_TAG:-}"
        -e "STACK_TAG=${AI_STACK_TAG:-}"
        -e "REJECT_POLICY_TAG=${AI_REJECT_POLICY_TAG:-}"
        -e "CALIBRATION_TAG=${AI_CALIBRATION_TAG:-mock-defaults}"
        -e "CAMPAIGN=${AI_CAMPAIGN:-ad-hoc}"
      )
      # The realistic microblogging activity-summary prompt is the default. It lives
      # canonically in script.js (single source of truth); only forward PROMPT when the
      # operator overrode it via --ai-prompt, so the wrapper never reintroduces the old
      # short "Summarize recent activity." default.
      if [[ -n "${AI_PROMPT:-}" ]]; then
        K6_ENV_ARGS+=(-e "PROMPT=$AI_PROMPT")
      fi
      if [[ -n "${AI_PREALLOC_VUS:-}" ]]; then
        K6_ENV_ARGS+=(-e "PREALLOC_VUS=$AI_PREALLOC_VUS")
      fi
      if [[ -n "${AI_MAX_VUS:-}" ]]; then
        K6_ENV_ARGS+=(-e "MAX_VUS=$AI_MAX_VUS")
      fi
      ;;
  esac
}

readiness_gate() {
  # Pre-flight check before each run: the SUT must respond to /actuator/health
  # = UP AND /actuator/prometheus = 200 stably for READINESS_GRACE_SECS seconds
  # before we start the k6 warmup. Any failure inside the window aborts the cell
  # rather than letting a "container not ready" or post-rebuild timing window
  # silently produce an all-error run that pollutes cells.csv. The gate
  # guards against the case where /actuator is reachable but Spring has
  # not yet started accepting benchmark traffic.
  local actuator_url="$1"
  local grace="${READINESS_GRACE_SECS:-5}"
  local health_url="${actuator_url%/prometheus}/health"
  local probe_start probe_end probe
  probe_start=$(date +%s)
  probe_end=$((probe_start + grace))
  while [[ $(date +%s) -lt $probe_end ]]; do
    if ! curl -fs --max-time 2 "$health_url" 2>/dev/null | grep -q '"UP"'; then
      echo "READINESS_FAIL: $health_url not UP at $(date +%H:%M:%S); cell aborted."
      return 1
    fi
    if ! curl -fs --max-time 2 "$actuator_url" >/dev/null 2>&1; then
      echo "READINESS_FAIL: $actuator_url unreachable at $(date +%H:%M:%S); cell aborted."
      return 1
    fi
    sleep 1
  done
  return 0
}

prom_poll_loop() {
  # Background loop: scrape /actuator/prometheus every PROM_POLL_SECS and
  # append a CSV row to OUT_CSV. Captures pool_active, queue_depth,
  # rejections_total, tasks_completed (async tweet-service) and the analogous
  # reactive_streams_in_flight / completed / cancelled / errored on reactive.
  # The "_total" Prometheus counters accumulate across the whole stack lifetime
  # — to compute per-cell deltas, the analysis pipeline subtracts the first
  # row from the last for each cell. e2e_seconds histogram buckets are
  # excluded to keep the CSV small.
  local actuator_url="$1"
  local out_csv="$2"
  local interval="${PROM_POLL_SECS:-1}"
  echo "t_secs,pool_active,queue_depth,pool_size,pool_core_size,pool_max_size,queue_capacity,tasks_completed,rejections_total,reactive_in_flight,reactive_completed,reactive_cancelled,reactive_errored" > "$out_csv"
  local t0
  t0=$(date +%s)
  while :; do
    local snap
    snap="$(curl -fs --max-time 2 "$actuator_url" 2>/dev/null || true)"
    local now t pa qd ps pcs pms qc tc rt rin rcomp rcanc rerr
    now=$(date +%s)
    t=$((now - t0))
    # Aggregate values across label sets (sum gauges and counters; for histograms
    # we'd take a single bucket — but those are excluded above).
    # Sum the metric value across all label-sets for each WANT name. The
    # metric line shape is either `name 0.0` (unlabeled) or `name{...} 0.0`
    # (labeled). Match both: awk's $1 catches the unlabeled form (Prometheus
    # exposition format guarantees the leading token is the bare name there),
    # and the trailing regex catches the labeled form. awk receives literal
    # backslashes from bash, so keep the regex simple.
    extract() {
      printf '%s\n' "$snap" | awk -v want="$1" '
        $1 == want { n += $2; next }
        $1 ~ "^" want "[{]" {
          # find the value AFTER the closing brace + space
          sub(/^[^}]*\}[[:space:]]*/, "")
          n += $1; next
        }
        END { print n+0 }
      '
    }
    pa=$(extract "tweebyte_pool_active")
    qd=$(extract "tweebyte_pool_queue_depth")
    ps=$(extract "tweebyte_pool_size")
    pcs=$(extract "tweebyte_pool_core_size")
    pms=$(extract "tweebyte_pool_max_size")
    qc=$(extract "tweebyte_pool_queue_capacity")
    tc=$(extract "tweebyte_pool_tasks_completed")
    rt=$(extract "tweebyte_pool_rejections_total")
    rin=$(extract "tweebyte_reactive_streams_in_flight")
    rcomp=$(extract "tweebyte_reactive_streams_completed_total")
    rcanc=$(extract "tweebyte_reactive_streams_cancelled_total")
    rerr=$(extract "tweebyte_reactive_streams_errored_total")
    printf '%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' "$t" "$pa" "$qd" "$ps" "$pcs" "$pms" "$qc" "$tc" "$rt" "$rin" "$rcomp" "$rcanc" "$rerr" >> "$out_csv"
    sleep "$interval"
  done
}

scan_for_connection_errors() {
  # Post-run, scan the k6 output text for transport failure signatures within
  # the main-scenario window.
  #
  # Threshold-aware classification: CONTAMINATED only on >REFUSED_THRESHOLD
  # connection-refused events. `connection reset by peer` and `unexpected EOF`
  # are normal under cliff load — when the bounded pool's queue overflows and
  # AbortPolicy fires, k6 sees the server close mid-stream and logs reset/EOF
  # for those connections. Treating those as contamination would false-positive
  # on every cell where the cliff is doing what it's supposed to do. Refused
  # is the genuine stack-was-down signal: it means k6 couldn't even open a
  # socket, and that only happens when the SUT process is gone or its listener
  # is closed.
  # TRANSPORT_LIMITED (new): dial i/o timeout, request timeout, and "can't assign
  # requested address" are admission/transport-collapse signatures — the SUT's
  # connector accept-backlog or the host transport (client ephemeral ports) shed the
  # load, so the bounded streamExecutor may never be the binding constraint. Such a
  # cell does not cleanly measure H1 and is quarantined out of clean latency aggregates,
  # distinct from CONTAMINATED (stack-down: connection refused). `connection reset` and
  # `unexpected EOF` stay non-quarantining — they co-occur with a genuine AbortPolicy
  # cliff (server closes mid-stream) — but are counted and reported.
  local run_txt="$1"
  local validation_path="$2"
  local refused_threshold="${REFUSED_THRESHOLD:-200}"
  # Quarantine is RATE-based, not a bare count: transport errors must exceed both an
  # absolute floor (ignore a handful of stray dials on a clean cell) AND a fraction of
  # requests (a cell whose load is shed at the connector/transport rather than the
  # streamExecutor). A deep-overload cell that genuinely AbortPolicy-rejects but has a
  # little transport noise (e.g. 0.6% dials amid 99% real rejections) stays a valid cell.
  local transport_floor="${TRANSPORT_ERROR_FLOOR:-50}"
  local transport_permille="${TRANSPORT_ERROR_PERMILLE:-5}"   # 0.5% of requests (Codex hard-quarantine line)
  local refused reset eof dial_timeout req_timeout addr lgen transport_sum requests main_errors
  local status over_rate src merr_found reset_eof_suspicious reject_delta prom
  # main-window totals from the k6 summary JSON (authoritative; warmup is excluded there).
  # merr_found tracks whether the count actually parsed: a missing JSON count must NOT be
  # trusted as a genuine zero (else a log-grep-only run with no summary looks clean).
  # `|| true`: workloads without a JSON handleSummary (every non-AI k6 script) emit k6's
  # default TEXT summary, so these JSON greps match nothing — that must fall through to the
  # src=loggrep branch below, not abort the run under `set -euo pipefail`.
  requests=$(grep -oE '"requests":[[:space:]]*[0-9]+' "$run_txt" 2>/dev/null | grep -oE '[0-9]+' | tail -1 || true)
  main_errors=$(grep -oE '"errors":[[:space:]]*[0-9]+' "$run_txt" 2>/dev/null | grep -oE '[0-9]+' | tail -1 || true)
  requests="${requests:-0}"
  if [[ -n "$main_errors" ]]; then merr_found=1; else merr_found=0; main_errors=0; fi
  # Prefer the summary's MAIN-ONLY classified counts (errors_by_type); a raw log-line
  # grep also counts warmup-ramp blips and would false-positive a clean cell. Fall back
  # to log greps only for pre-classification (old) runs that lack errors_by_type.
  # `|| true` here too: a missing "dial_timeout" (non-AI default summary) must select the
  # loggrep branch, not abort. The json branch is only entered when the JSON IS present.
  dial_timeout=$(grep -oE '"dial_timeout":[[:space:]]*[0-9]+' "$run_txt" 2>/dev/null | grep -oE '[0-9]+' | tail -1 || true)
  if [[ -n "$dial_timeout" ]]; then
    src="json"
    req_timeout=$(grep -oE '"req_timeout":[[:space:]]*[0-9]+' "$run_txt" | grep -oE '[0-9]+' | tail -1 || true)
    addr=$(grep -oE '"addr_unavail":[[:space:]]*[0-9]+' "$run_txt" | grep -oE '[0-9]+' | tail -1 || true)
    lgen=$(grep -oE '"lgen":[[:space:]]*[0-9]+' "$run_txt" | grep -oE '[0-9]+' | tail -1 || true)
  else
    src="loggrep"
    dial_timeout=$(grep -cE "i/o timeout|operation timed out" "$run_txt" 2>/dev/null || true)
    req_timeout=$(grep -cE "request timeout|context deadline|awaiting response headers" "$run_txt" 2>/dev/null || true)
    addr=$(grep -cE "assign requested address|no buffer space" "$run_txt" 2>/dev/null || true)
    lgen=$(grep -cE "too many open files|no such host" "$run_txt" 2>/dev/null || true)
  fi
  refused=$(grep -c "connection refused" "$run_txt" 2>/dev/null || true)
  reset=$(grep -cE "connection reset|broken pipe" "$run_txt" 2>/dev/null || true)
  eof=$(grep -cE "unexpected EOF|read: EOF" "$run_txt" 2>/dev/null || true)
  refused="${refused:-0}"; reset="${reset:-0}"; eof="${eof:-0}"
  dial_timeout="${dial_timeout:-0}"; req_timeout="${req_timeout:-0}"; addr="${addr:-0}"; lgen="${lgen:-0}"
  transport_sum=$(( dial_timeout + req_timeout + addr + lgen ))
  # Cap at the main-window error total ONLY when that count is trustworthy (JSON present); a
  # log-grep fallback with no parseable main_errors must NOT be capped to 0 and called clean.
  if (( merr_found == 1 )) && (( transport_sum > main_errors )); then transport_sum=$main_errors; fi
  over_rate=0
  if (( requests > 0 )) && (( transport_sum * 1000 > transport_permille * requests )); then over_rate=1; fi

  # reject_delta (AbortPolicy evidence) from the sibling prom.csv, for the reset/eof sanity rule.
  prom="${run_txt%.txt}_prom.csv"
  if [[ -f "$prom" ]]; then
    reject_delta=$(awk -F, '
      NR==1 { for (i=1;i<=NF;i++) if ($i=="rejections_total") c=i; next }
      c && $c!="" { if (first=="") first=$c; last=$c }
      END { if (first!="") printf "%d", last-first }' "$prom")
  fi
  reject_delta="${reject_delta:-}"
  # reset/eof are cliff-normal ONLY when there is app-rejection evidence. A burst with
  # reject_delta=0 means the server closed connections without the pool rejecting — warn (not auto-quarantine).
  reset_eof_suspicious=0
  if [[ -n "$reject_delta" ]] && (( reject_delta == 0 )) && (( reset + eof > transport_floor )); then
    reset_eof_suspicious=1
    echo "  WARN: reset+eof=$(( reset + eof )) with reject_delta=0 in $run_txt — connections closed without AbortPolicy evidence; inspect (not auto-quarantined)."
  fi
  (( lgen > 0 )) && echo "  WARN: load-generator failures (too-many-open-files/no-such-host)=$lgen in $run_txt — k6-side; the SUT never saw these requests."

  if [[ $refused -gt $refused_threshold ]]; then
    status="CONTAMINATED"
    echo "  WARN: connection_refused=$refused exceeds threshold=$refused_threshold in $run_txt — cell flagged CONTAMINATED (stack down)."
  elif (( merr_found == 1 )) && (( main_errors == 0 )); then
    # Trustworthy zero main-window failures → cannot be transport-limited in the measured window.
    status="OK"
  elif (( transport_sum > transport_floor )) || (( over_rate == 1 )); then
    status="TRANSPORT_LIMITED"
    echo "  WARN: transport/admission errors (dial=$dial_timeout reqto=$req_timeout addr=$addr lgen=$lgen sum=$transport_sum; >floor ${transport_floor} or >${transport_permille}permille of ${requests} req) in $run_txt — cell flagged TRANSPORT_LIMITED (excluded from clean H1 aggregates)."
  else
    status="OK"
  fi
  {
    echo "cell_status=$status"
    echo "connection_refused_count=$refused"
    echo "connection_reset_count=$reset"
    echo "eof_count=$eof"
    echo "dial_timeout_count=$dial_timeout"
    echo "request_timeout_count=$req_timeout"
    echo "addr_unavailable_count=$addr"
    echo "lgen_count=$lgen"
    echo "transport_sum=$transport_sum"
    echo "main_requests=$requests"
    echo "main_errors=$main_errors"
    echo "main_errors_found=$merr_found"
    echo "reject_delta=${reject_delta:-NA}"
    echo "reset_eof_suspicious=$reset_eof_suspicious"
    echo "transport_count_source=$src"
    echo "refused_threshold=$refused_threshold"
    echo "transport_error_floor=$transport_floor"
    echo "transport_error_permille=$transport_permille"
  } > "$validation_path"
}

main() {
  local MAIN_SECS RUN_TXT RES_CSV PROM_CSV VAL_TXT K6_PID SAMP_PID PROM_PID c i
  local -a K6_ENV_ARGS=()

  parse_args "$@"
  validate_generic_args
  configure_workload
  if [[ -z "$ACTUATOR_URL" ]]; then
    ACTUATOR_URL="${BASE_URL}/actuator/prometheus"
  fi
  resolve_docker_addressing

  require_positive_int "WARMUP seconds" "$(secs_from_duration "$WARMUP_SECS_RAW")"
  MAIN_SECS="$(secs_from_duration "$DURATION")"
  require_positive_int "DURATION seconds" "$MAIN_SECS"

  mkdir -p "$RESULTS_ROOT"
  OUT_DIR="$(mktemp -d "${RESULTS_ROOT}/results_${WORKLOAD_SLUG}_$(date +%Y%m%d_%H%M%S)_XXXXXX")"
  {
    echo "benchmark_topology=${BENCHMARK_TOPOLOGY}"
    echo "benchmark_infra_topology=${BENCHMARK_INFRA_TOPOLOGY}"
    echo "workload=${WORKLOAD}"
    echo "mode=${MODE}"
    echo "base_url=${BASE_URL}"
    echo "actuator_url=${ACTUATOR_URL}"
    echo "created_at=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  } > "${OUT_DIR}/run_metadata.env"

  print_config

  for c in "${CONCURRENCY_LIST[@]}"; do
    for i in $(seq 1 "$RUNS"); do
      echo "Running workload=$WORKLOAD concurrency=$c iteration=$i..."

      RUN_TXT="$OUT_DIR/${c}_${i}.txt"
      RES_CSV="$OUT_DIR/${c}_${i}_resources.csv"
      PROM_CSV="$OUT_DIR/${c}_${i}_prom.csv"
      VAL_TXT="$OUT_DIR/${c}_${i}_validation.txt"

      # pre-flight readiness gate before warmup. If the SUT can't respond
      # to /actuator/health = UP and /actuator/prometheus = 200 stably for the
      # grace window, skip the cell and write a sentinel.
      if ! readiness_gate "$ACTUATOR_URL" 2>&1 | tee -a "$RUN_TXT"; then
        {
          echo "cell_status=READINESS_FAIL"
          echo "actuator_url=$ACTUATOR_URL"
          echo "skipped_at=$(date +%Y-%m-%dT%H:%M:%S%z)"
        } > "$VAL_TXT"
        echo "  cell_status=READINESS_FAIL — skipping run."
        continue
      fi

      build_k6_env_args "$c"

      # in-run Prometheus poller. Captures pool/queue/rejections time-series
      # alongside the k6 run — the data the §5.12 attribution figure needs.
      prom_poll_loop "$ACTUATOR_URL" "$PROM_CSV" &
      PROM_PID=$!

      if [[ "$MODE" == "docker" ]]; then
        # Run k6 as a sibling container inside the compose network with the
        # host-fd-starvation workaround applied. SCRIPT_JS becomes /work/script.js
        # after the bind-mount of WORKLOAD_HOME. K6_ENV_ARGS already uses
        # K6_BASE_URL (container IP) for BASE_URL.
        docker "${DOCKER_K6_ARGS[@]}" run "${K6_ENV_ARGS[@]}" /work/script.js \
          >> "$RUN_TXT" 2>&1 &
      else
        "$K6_BIN" run "${K6_ENV_ARGS[@]}" "$SCRIPT_JS" >> "$RUN_TXT" 2>&1 &
      fi
      K6_PID=$!

      if [[ "$COLLECT_RESOURCES" == "1" ]]; then
        (
          sleep "$(secs_from_duration "$WARMUP_SECS_RAW")"
          if kill -0 "$K6_PID" >/dev/null 2>&1; then
            sample_resources "$K6_PID" "$c" "$i" "$RES_CSV" "$ACTUATOR_URL" "$SAMPLE_EVERY_SECS"
          fi
        ) &
        SAMP_PID=$!
      else
        SAMP_PID=""
      fi

      wait "$K6_PID" || true
      sleep 1

      if [[ -n "$SAMP_PID" ]]; then
        kill "$SAMP_PID" >/dev/null 2>&1 || true
        wait "$SAMP_PID" 2>/dev/null || true
      fi
      kill "$PROM_PID" >/dev/null 2>&1 || true
      wait "$PROM_PID" 2>/dev/null || true

      # post-run scan for transport-error signatures inside the run text.
      scan_for_connection_errors "$RUN_TXT" "$VAL_TXT"

      print_pretty_summary "$RUN_TXT" "$RES_CSV" "$c" "$i"
    done
  done

  print_final_recap
  echo "Done. Logs in: $OUT_DIR"
}

main "$@"
