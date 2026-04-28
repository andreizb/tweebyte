#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
PAYLOAD_TOOL="${SCRIPT_DIR}/prepare_payload.py"
WORKLOAD_DIR="${SCRIPT_DIR}/workloads"
RESULTS_ROOT="${RESULTS_ROOT:-${REPO_ROOT}/testing-results/performance/k6}"

K6_BIN="/opt/homebrew/bin/k6"
if [[ ! -x "$K6_BIN" ]]; then
  if command -v k6 >/dev/null 2>&1; then
    K6_BIN="$(command -v k6)"
  else
    echo "ERROR: k6 not found. brew install k6"
    exit 1
  fi
fi

WORKLOAD="${WORKLOAD:-following-cache}"
BASE_URL="${BASE_URL:-}"
ACTUATOR_URL="${ACTUATOR_URL:-}"
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
PAYLOAD_FILE="${PAYLOAD_FILE:-}"
IMAGE_WIDTH="${IMAGE_WIDTH:-}"
IMAGE_HEIGHT="${IMAGE_HEIGHT:-}"
IMAGE_FORMAT="${IMAGE_FORMAT:-jpg}"

SCRIPT_JS=""
WORKLOAD_SLUG=""
WORKLOAD_DESC=""
RECAP_ROWS=()
CONCURRENCY_LIST=()

usage() {
  cat <<'EOF'
Usage:
  ./testing/performance/k6/run_bench.sh \
    --workload <following-cache|file-download|image-upload|ai-streaming> \
    --base-url <url> \
    --concurrencies "1000" \
    --runs 1 \
    --warmup 60s \
    --duration 3m \
    [workload-specific flags...]

Generic flags:
  --workload <name>
    following-cache  Redis-backed interaction-service cache benchmark (I/O-bound)
    file-download    Legacy blocking download benchmark (blocking I/O)
    image-upload     Gateway image filter benchmark (CPU-bound)
    ai-streaming     AI streaming benchmark (open-loop, SSE + buffered + cancel, W0/W1/W2)
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

following-cache flags:
  --path-prefix <path>
  --keys-file <path>
  --key-count <N>
  --follows-per-key <K>
  --total-keys <count>
  --hot-count <count>
  --hot-ratio <ratio>

file-download flags:
  --api-path <path>

image-upload flags:
  --api-path <path>
  --payload-file <path>
  --image-width <pixels>
  --image-height <pixels>
  --image-format <jpg|jpeg|png>

ai-streaming flags:
  --ai-workload <W0|W1|W2>       W0=mock-stream baseline, W1=pure chat, W2=chat+tool-call
  --ai-transport <sse|buffered>  sse=streaming (default), buffered=REST control.
                                 Valid combos: {W0,W1}×{sse,buffered}, W2×{sse} only
                                 (W2+buffered is refused — a buffered response has
                                 no stream for the mid-stream tool call to interrupt).
  --ai-cancel-rate <0.0..1.0>    Fraction of requests to abort mid-stream (default 0.0)
  --ai-target-rps <rate>         Arrival rate; falls back to --concurrencies if omitted
  --ai-user-id <uuid>            UUID to use for W2 tool call
  --ai-prompt <text>             Chat prompt text
  --ai-mock-tokens <N>           W0 token count (default 150)
  --ai-mock-itl-ms <ms>          W0 inter-token latency in ms (default 40)
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
                                 pool_active / queue_depth / rejections_total /
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

      --path-prefix) PATH_PREFIX="$2"; shift 2 ;;
      --keys-file) KEYS_FILE="$2"; shift 2 ;;
      --key-count) KEY_COUNT="$2"; shift 2 ;;
      --follows-per-key) FOLLOWS_PER_KEY="$2"; shift 2 ;;
      --total-keys) TOTAL_KEYS="$2"; shift 2 ;;
      --hot-count) HOT_COUNT="$2"; shift 2 ;;
      --hot-ratio) HOT_RATIO="$2"; shift 2 ;;

      --api-path) API_PATH="$2"; shift 2 ;;
      --payload-file) PAYLOAD_FILE="$2"; shift 2 ;;
      --image-width) IMAGE_WIDTH="$2"; shift 2 ;;
      --image-height) IMAGE_HEIGHT="$2"; shift 2 ;;
      --image-format) IMAGE_FORMAT="$2"; shift 2 ;;

      --ai-workload) AI_WORKLOAD="$2"; shift 2 ;;
      --ai-transport) AI_TRANSPORT="$2"; shift 2 ;;
      --ai-cancel-rate) AI_CANCEL_RATE="$2"; shift 2 ;;
      --ai-target-rps) AI_TARGET_RPS="$2"; shift 2 ;;
      --ai-user-id) AI_USER_ID="$2"; shift 2 ;;
      --ai-prompt) AI_PROMPT="$2"; shift 2 ;;
      --ai-mock-tokens) AI_MOCK_TOKENS="$2"; shift 2 ;;
      --ai-mock-itl-ms) AI_MOCK_ITL_MS="$2"; shift 2 ;;
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
    KEYS_FILE="${SCRIPT_DIR}/payload/following-cache/n${KEY_COUNT}_k${FOLLOWS_PER_KEY}/keys.txt"
  else
    KEYS_FILE="$(resolve_path "$KEYS_FILE")"
  fi

  if [[ ! -f "$KEYS_FILE" ]]; then
    if [[ "$AUTO_PREPARE" != "1" ]]; then
      echo "ERROR: missing keys file '$KEYS_FILE' and auto-prepare is disabled." >&2
      exit 1
    fi

    if [[ -z "$KEY_COUNT" || -z "$FOLLOWS_PER_KEY" ]]; then
      echo "ERROR: auto-prepare for following-cache requires --key-count and --follows-per-key when the keys file is missing." >&2
      exit 1
    fi

    echo "Auto-preparing following-cache payload..."
    python3 "$PAYLOAD_TOOL" following-cache \
      --key-count "$KEY_COUNT" \
      --follows-per-key "$FOLLOWS_PER_KEY" \
      --keys-out "$KEYS_FILE"
  fi
}

prepare_image_payload_if_needed() {
  if [[ -z "$IMAGE_WIDTH" ]]; then
    IMAGE_WIDTH="256"
  fi
  if [[ -z "$IMAGE_HEIGHT" ]]; then
    IMAGE_HEIGHT="$IMAGE_WIDTH"
  fi
  require_positive_int "IMAGE_WIDTH" "$IMAGE_WIDTH"
  require_positive_int "IMAGE_HEIGHT" "$IMAGE_HEIGHT"

  if [[ -z "$PAYLOAD_FILE" ]]; then
    PAYLOAD_FILE="${SCRIPT_DIR}/payload/image-upload/${IMAGE_WIDTH}x${IMAGE_HEIGHT}.${IMAGE_FORMAT}"
  else
    PAYLOAD_FILE="$(resolve_path "$PAYLOAD_FILE")"
  fi

  if [[ ! -f "$PAYLOAD_FILE" ]]; then
    if [[ "$AUTO_PREPARE" != "1" ]]; then
      echo "ERROR: missing payload file '$PAYLOAD_FILE' and auto-prepare is disabled." >&2
      exit 1
    fi

    echo "Auto-preparing image-upload payload..."
    python3 "$PAYLOAD_TOOL" image-upload \
      --width "$IMAGE_WIDTH" \
      --height "$IMAGE_HEIGHT" \
      --image-format "$IMAGE_FORMAT" \
      --output "$PAYLOAD_FILE"
  fi
}

configure_workload() {
  local lines

  case "$WORKLOAD" in
    following-cache)
      WORKLOAD_SLUG="following_cache"
      WORKLOAD_DESC="Redis-backed interaction-service cache benchmark (I/O-bound)"
      SCRIPT_JS="${WORKLOAD_DIR}/following-cache-redis-io-benchmark.js"
      BASE_URL="${BASE_URL:-http://localhost:9093}"
      PATH_PREFIX="${PATH_PREFIX:-/follows}"
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
    file-download)
      WORKLOAD_SLUG="file_download"
      WORKLOAD_DESC="Legacy blocking download benchmark (blocking I/O)"
      SCRIPT_JS="${WORKLOAD_DIR}/file-download-blocking-io-benchmark.js"
      BASE_URL="${BASE_URL:-http://localhost:9091}"
      API_PATH="${API_PATH:-/media/}"
      ;;
    image-upload)
      WORKLOAD_SLUG="image_upload"
      WORKLOAD_DESC="Gateway image filter benchmark (CPU-bound)"
      SCRIPT_JS="${WORKLOAD_DIR}/image-upload-cpu-bound-benchmark.js"
      BASE_URL="${BASE_URL:-http://localhost:8080}"
      API_PATH="${API_PATH:-/media/filter}"
      prepare_image_payload_if_needed
      [[ -f "$PAYLOAD_FILE" ]] || { echo "ERROR: missing PAYLOAD_FILE: $PAYLOAD_FILE" >&2; exit 1; }
      ;;
    ai-streaming)
      WORKLOAD_SLUG="ai_streaming"
      WORKLOAD_DESC="AI streaming benchmark (open-loop, SSE/buffered, W0/W1/W2)"
      SCRIPT_JS="${WORKLOAD_DIR}/ai-streaming-benchmark.js"
      BASE_URL="${BASE_URL:-http://localhost:9092}"
      ;;
    *)
      echo "ERROR: unsupported workload '$WORKLOAD'. Use following-cache, file-download, image-upload, or ai-streaming." >&2
      exit 1
      ;;
  esac

  [[ -f "$SCRIPT_JS" ]] || { echo "ERROR: missing workload script: $SCRIPT_JS" >&2; exit 1; }
}

print_config() {
  echo "Using:"
  echo "  WORKLOAD=$WORKLOAD"
  echo "  WORKLOAD_DESC=$WORKLOAD_DESC"
  echo "  BASE_URL=$BASE_URL"
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
    following-cache)
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
    file-download)
      echo "  API_PATH=$API_PATH"
      ;;
    image-upload)
      echo "  API_PATH=$API_PATH"
      echo "  PAYLOAD_FILE=$PAYLOAD_FILE"
      echo "  IMAGE_WIDTH=$IMAGE_WIDTH"
      echo "  IMAGE_HEIGHT=$IMAGE_HEIGHT"
      echo "  IMAGE_FORMAT=$IMAGE_FORMAT"
      ;;
    ai-streaming)
      echo "  AI_WORKLOAD=${AI_WORKLOAD:-W1}"
      echo "  AI_TRANSPORT=${AI_TRANSPORT:-sse}"
      echo "  AI_CANCEL_RATE=${AI_CANCEL_RATE:-0.0}"
      echo "  AI_TARGET_RPS=${AI_TARGET_RPS:-unset→CONCURRENCY}"
      echo "  AI_USER_ID=${AI_USER_ID:-00000000-0000-0000-0000-000000000001}"
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

  echo "ts_iso,concurrency,run,cpu_usage_pct,heap_used_bytes,heap_used_mb" > "$csv"

  while kill -0 "$k6_pid" >/dev/null 2>&1; do
    local ts_iso cpu_raw cpu_pct heap_bytes heap_mb page
    ts_iso="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

    if ! page="$(curl -fsS "$actuator" 2>/dev/null)"; then
      echo "$ts_iso,$c,$i,NaN,NaN,NaN" >> "$csv"
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

    echo "$ts_iso,$c,$i,$cpu_pct,$heap_bytes,$heap_mb" >> "$csv"
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
    printf '%s\t%s\t%s\t%s\n' "-" "-" "-" "-"
    return 0
  fi

  python3 - "$res_csv" <<'PY'
import csv
import sys
from pathlib import Path

path = Path(sys.argv[1])
rows = list(csv.DictReader(path.open()))

cpu = [float(r["cpu_usage_pct"]) for r in rows if r["cpu_usage_pct"] != "NaN"]
heap = [float(r["heap_used_mb"]) for r in rows if r["heap_used_mb"] != "NaN"]

if not rows or not cpu or not heap:
    print("\t".join(["-", "-", "-", "-"]))
else:
    print("\t".join([
        f"{sum(cpu) / len(cpu):.2f}",
        f"{max(cpu):.2f}",
        f"{sum(heap) / len(heap):.2f}",
        f"{max(heap):.2f}",
    ]))
PY
}

print_pretty_summary() {
  local run_txt="$1" res_csv="$2" c="$3" i="$4"
  local count rps avg p95 errors avg_cpu max_cpu avg_heap max_heap

  IFS=$'\t' read -r count rps avg p95 errors <<<"$(parse_k6_summary "$run_txt" "$MAIN_SECS")"
  IFS=$'\t' read -r avg_cpu max_cpu avg_heap max_heap <<<"$(parse_resources_summary "$res_csv")"

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
  fi

  RECAP_ROWS+=("${c}|${i}|${count}|${rps}|${avg}|${p95}|${errors}|${avg_cpu}|${max_cpu}|${avg_heap}|${max_heap}")
  echo
}

print_final_recap() {
  local row c i count rps avg p95 errors avg_cpu max_cpu avg_heap max_heap

  echo "Final recap:"
  printf '%-12s %-5s %-12s %-12s %-10s %-10s %-10s %-10s %-10s %-12s %-12s\n' \
    "concurrency" "run" "count" "true_rps" "avg_ms" "p95_ms" "failed" "cpu_avg" "cpu_max" "heap_avg_mb" "heap_max_mb"

  for row in "${RECAP_ROWS[@]}"; do
    IFS='|' read -r c i count rps avg p95 errors avg_cpu max_cpu avg_heap max_heap <<<"$row"
    printf '%-12s %-5s %-12s %-12s %-10s %-10s %-10s %-10s %-10s %-12s %-12s\n' \
      "$c" "$i" "$count" "$rps" "$avg" "$p95" "$errors" "$avg_cpu" "$max_cpu" "$avg_heap" "$max_heap"
  done

  echo
}

build_k6_env_args() {
  local concurrency="$1"

  K6_ENV_ARGS=(
    -e "BASE_URL=$BASE_URL"
    -e "CONCURRENCY=$concurrency"
    -e "WARMUP_SECS=$WARMUP_SECS_RAW"
    -e "DURATION=$DURATION"
  )

  case "$WORKLOAD" in
    following-cache)
      K6_ENV_ARGS+=(
        -e "PATH_PREFIX=$PATH_PREFIX"
        -e "KEYS_FILE=$KEYS_FILE"
        -e "TOTAL_KEYS=$TOTAL_KEYS"
        -e "HOT_COUNT=$HOT_COUNT"
        -e "HOT_RATIO=$HOT_RATIO"
      )
      ;;
    file-download)
      K6_ENV_ARGS+=(
        -e "API_PATH=$API_PATH"
      )
      ;;
    image-upload)
      K6_ENV_ARGS+=(
        -e "API_PATH=$API_PATH"
        -e "PAYLOAD_FILE=$PAYLOAD_FILE"
      )
      ;;
    ai-streaming)
      K6_ENV_ARGS+=(
        -e "WORKLOAD=${AI_WORKLOAD:-W1}"
        -e "TRANSPORT=${AI_TRANSPORT:-sse}"
        -e "CANCEL_RATE=${AI_CANCEL_RATE:-0.0}"
        -e "TARGET_RPS=${AI_TARGET_RPS:-$concurrency}"
        -e "USER_ID=${AI_USER_ID:-00000000-0000-0000-0000-000000000001}"
        -e "PROMPT=${AI_PROMPT:-Summarize recent activity.}"
        -e "MOCK_TOKENS=${AI_MOCK_TOKENS:-150}"
        -e "MOCK_ITL_MS=${AI_MOCK_ITL_MS:-40}"
        -e "POOL_SIZE_TAG=${AI_POOL_SIZE_TAG:-}"
        -e "STACK_TAG=${AI_STACK_TAG:-}"
        -e "REJECT_POLICY_TAG=${AI_REJECT_POLICY_TAG:-}"
        -e "CALIBRATION_TAG=${AI_CALIBRATION_TAG:-mock-defaults}"
        -e "CAMPAIGN=${AI_CAMPAIGN:-ad-hoc}"
      )
      ;;
  esac
}

readiness_gate() {
  # Pre-flight check before each run: the SUT must respond to /actuator/health
  # = UP AND /actuator/prometheus = 200 stably for READINESS_GRACE_SECS seconds
  # before we start the k6 warmup. Any failure inside the window aborts the cell
  # rather than letting a "container not ready" or post-rebuild timing window
  # silently produce an all-error run that pollutes cells.csv. Codex's 2026-04-28
  # review found contaminated cells where /actuator was reachable but Spring saw
  # no benchmark traffic — this gate closes that window.
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
  echo "t_secs,pool_active,queue_depth,pool_size,tasks_completed,rejections_total,reactive_in_flight,reactive_completed,reactive_cancelled,reactive_errored" > "$out_csv"
  local t0
  t0=$(date +%s)
  while :; do
    local snap
    snap="$(curl -fs --max-time 2 "$actuator_url" 2>/dev/null || true)"
    local now t pa qd ps tc rt rin rcomp rcanc rerr
    now=$(date +%s)
    t=$((now - t0))
    # Aggregate values across label sets (sum gauges and counters; for histograms
    # we'd take a single bucket — but those are excluded above).
    # Sum the metric value across all label-sets for each WANT name. The
    # metric line shape is either `name 0.0` (no labels) or `name{...} 0.0`
    # (with labels) — match either by extracting the leading non-{/space token
    # via awk's $1 (which Prometheus exposition format guarantees is the bare
    # name when there are no labels) and falling back to a regex for the
    # labeled form. Earlier code used `/^name(\\{|\\s)/` which is a bash-into-
    # awk quoting trap: awk saw literal `\{` and `\s` and matched neither the
    # space-separated nor the labeled form, so every column was zero.
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
    tc=$(extract "tweebyte_pool_tasks_completed")
    rt=$(extract "tweebyte_pool_rejections_total")
    rin=$(extract "tweebyte_reactive_streams_in_flight")
    rcomp=$(extract "tweebyte_reactive_streams_completed_total")
    rcanc=$(extract "tweebyte_reactive_streams_cancelled_total")
    rerr=$(extract "tweebyte_reactive_streams_errored_total")
    printf '%d,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' "$t" "$pa" "$qd" "$ps" "$tc" "$rt" "$rin" "$rcomp" "$rcanc" "$rerr" >> "$out_csv"
    sleep "$interval"
  done
}

scan_for_connection_errors() {
  # Codex 2026-04-28 review B1: post-run, scan the k6 output text for transport
  # failure signatures within the main-scenario window.
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
  local run_txt="$1"
  local validation_path="$2"
  local refused_threshold="${REFUSED_THRESHOLD:-200}"
  local refused reset eof
  refused=$(grep -c "connection refused" "$run_txt" 2>/dev/null || echo 0)
  reset=$(grep -c "connection reset" "$run_txt" 2>/dev/null || echo 0)
  eof=$(grep -cE "unexpected EOF|read: EOF" "$run_txt" 2>/dev/null || echo 0)
  if [[ $refused -gt $refused_threshold ]]; then
    {
      echo "cell_status=CONTAMINATED"
      echo "connection_refused_count=$refused"
      echo "connection_reset_count=$reset"
      echo "eof_count=$eof"
      echo "refused_threshold=$refused_threshold"
    } > "$validation_path"
    echo "  WARN: connection_refused=$refused exceeds threshold=$refused_threshold in $run_txt — cell flagged CONTAMINATED."
  else
    {
      echo "cell_status=OK"
      echo "connection_refused_count=$refused"
      echo "connection_reset_count=$reset"
      echo "eof_count=$eof"
      echo "refused_threshold=$refused_threshold"
    } > "$validation_path"
  fi
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

  require_positive_int "WARMUP seconds" "$(secs_from_duration "$WARMUP_SECS_RAW")"
  MAIN_SECS="$(secs_from_duration "$DURATION")"
  require_positive_int "DURATION seconds" "$MAIN_SECS"

  mkdir -p "$RESULTS_ROOT"
  OUT_DIR="$(mktemp -d "${RESULTS_ROOT}/results_${WORKLOAD_SLUG}_$(date +%Y%m%d_%H%M%S)_XXXXXX")"

  print_config

  for c in "${CONCURRENCY_LIST[@]}"; do
    for i in $(seq 1 "$RUNS"); do
      echo "Running workload=$WORKLOAD concurrency=$c iteration=$i..."

      RUN_TXT="$OUT_DIR/${c}_${i}.txt"
      RES_CSV="$OUT_DIR/${c}_${i}_resources.csv"
      PROM_CSV="$OUT_DIR/${c}_${i}_prom.csv"
      VAL_TXT="$OUT_DIR/${c}_${i}_validation.txt"

      # B1: pre-flight readiness gate before warmup. If the SUT can't respond
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

      # B3: in-run Prometheus poller. Captures pool/queue/rejections time-series
      # alongside the k6 run — the data the §5.12 attribution figure needs.
      prom_poll_loop "$ACTUATOR_URL" "$PROM_CSV" &
      PROM_PID=$!

      "$K6_BIN" run "${K6_ENV_ARGS[@]}" "$SCRIPT_JS" >> "$RUN_TXT" 2>&1 &
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

      # B1: post-run scan for transport-error signatures inside the run text.
      scan_for_connection_errors "$RUN_TXT" "$VAL_TXT"

      print_pretty_summary "$RUN_TXT" "$RES_CSV" "$c" "$i"
    done
  done

  print_final_recap
  echo "Done. Logs in: $OUT_DIR"
}

main "$@"
