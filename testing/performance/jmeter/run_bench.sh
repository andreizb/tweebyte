#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
WORKLOAD_DIR="${SCRIPT_DIR}/workloads"
PAYLOAD_ROOT="${SCRIPT_DIR}/payload"
PAYLOAD_TOOL="${SCRIPT_DIR}/prepare_payload.py"
RESULTS_ROOT="${RESULTS_ROOT:-${REPO_ROOT}/testing-results/performance/jmeter}"

JMETER_BIN="${JMETER_BIN:-}"
FILTER_BIN="${FILTER_BIN:-}"

WORKLOAD="${WORKLOAD:-user-summary}"
BASE_URL="${BASE_URL:-}"
START_OFFSET="${START_OFFSET:-60}"
AUTO_PREPARE="${AUTO_PREPARE:-1}"
PAYLOAD_COUNT="${PAYLOAD_COUNT:-1000}"
CONTENT_LENGTH="${CONTENT_LENGTH:-120}"
SEED_USERS="${SEED_USERS:-0}"
SEED_TWEETS="${SEED_TWEETS:-0}"
USER_IDS_FILE="${USER_IDS_FILE:-}"
TARGET_USER_IDS_FILE="${TARGET_USER_IDS_FILE:-}"
TWEET_UPDATES_FILE="${TWEET_UPDATES_FILE:-}"

PLAN=""
WORKLOAD_DESC=""
DEFAULT_BASE_URL=""
PAYLOAD_DIR=""
TARGET_SCHEME=""
TARGET_HOST=""
TARGET_PORT=""

usage() {
  cat <<'EOF'
Usage:
  ./testing/performance/jmeter/run_bench.sh \
    --workload <user-summary|user-summary-legacy|user-create|follow-create|tweet-update|tweets-get> \
    [--base-url <url>] \
    [--payload-count <count>] \
    [--auto-prepare <0|1>]

Generic flags:
  --workload <name>
  --base-url <url>
  --results-root <path>
  --start-offset <seconds>
  --auto-prepare <0|1>
  --payload-count <count>
  --seed-users <0|1>
  --seed-tweets <0|1>
  --content-length <chars>

Optional explicit payload files:
  --user-ids-file <path>
  --target-user-ids-file <path>
  --tweet-updates-file <path>

Workloads:
  user-summary         GET /users/summary/{id} legacy JMeter benchmark
  user-summary-legacy  older user-summary variant kept for reference
  user-create          legacy login/auth JMeter benchmark
  follow-create        POST /follows/{targetId}/{id} legacy JMeter benchmark
  tweet-update         PUT /tweets/{tweetId} legacy JMeter benchmark
  tweets-get           GET /tweets/user/{id}/summary legacy JMeter benchmark
EOF
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

require_positive_int() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]] || (( value <= 0 )); then
    echo "ERROR: ${name} must be a positive integer, got '${value}'." >&2
    exit 1
  fi
}

parse_args() {
  while (($# > 0)); do
    case "$1" in
      --workload) WORKLOAD="$2"; shift 2 ;;
      --base-url) BASE_URL="$2"; shift 2 ;;
      --results-root) RESULTS_ROOT="$(resolve_path "$2")"; shift 2 ;;
      --start-offset) START_OFFSET="$2"; shift 2 ;;
      --auto-prepare) AUTO_PREPARE="$2"; shift 2 ;;
      --payload-count) PAYLOAD_COUNT="$2"; shift 2 ;;
      --seed-users) SEED_USERS="$2"; shift 2 ;;
      --seed-tweets) SEED_TWEETS="$2"; shift 2 ;;
      --content-length) CONTENT_LENGTH="$2"; shift 2 ;;
      --user-ids-file) USER_IDS_FILE="$2"; shift 2 ;;
      --target-user-ids-file) TARGET_USER_IDS_FILE="$2"; shift 2 ;;
      --tweet-updates-file) TWEET_UPDATES_FILE="$2"; shift 2 ;;
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

resolve_workload() {
  case "$WORKLOAD" in
    user-summary)
      PLAN="${WORKLOAD_DIR}/user-summary.jmx"
      WORKLOAD_DESC="GET /users/summary/{id} legacy JMeter benchmark"
      DEFAULT_BASE_URL="http://localhost:9091"
      ;;
    user-summary-legacy)
      PLAN="${WORKLOAD_DIR}/user-summary-legacy.jmx"
      WORKLOAD_DESC="Older user-summary JMeter variant kept for reference"
      DEFAULT_BASE_URL="http://localhost:9091"
      ;;
    user-create)
      PLAN="${WORKLOAD_DIR}/user-create.jmx"
      WORKLOAD_DESC="Legacy auth/login JMeter benchmark"
      DEFAULT_BASE_URL="http://localhost:9091"
      ;;
    follow-create)
      PLAN="${WORKLOAD_DIR}/follow-create.jmx"
      WORKLOAD_DESC="POST /follows/{targetId}/{id} legacy JMeter benchmark"
      DEFAULT_BASE_URL="http://localhost:9093"
      ;;
    tweet-update)
      PLAN="${WORKLOAD_DIR}/tweet-update.jmx"
      WORKLOAD_DESC="PUT /tweets/{tweetId} legacy JMeter benchmark"
      DEFAULT_BASE_URL="http://localhost:9092"
      ;;
    tweets-get)
      PLAN="${WORKLOAD_DIR}/tweets-get.jmx"
      WORKLOAD_DESC="GET /tweets/user/{id}/summary legacy JMeter benchmark"
      DEFAULT_BASE_URL="http://localhost:9092"
      ;;
    *)
      echo "ERROR: unsupported workload '${WORKLOAD}'." >&2
      usage
      exit 1
      ;;
  esac

  PAYLOAD_DIR="${PAYLOAD_ROOT}/${WORKLOAD}"
  BASE_URL="${BASE_URL:-$DEFAULT_BASE_URL}"

  if [[ ! -f "$PLAN" ]]; then
    echo "ERROR: JMeter plan not found: $PLAN" >&2
    exit 1
  fi
}

parse_base_url() {
  read -r TARGET_SCHEME TARGET_HOST TARGET_PORT < <(
    python3 - "$BASE_URL" <<'PY'
from urllib.parse import urlparse
import sys

raw = sys.argv[1]
parsed = urlparse(raw)
if not parsed.scheme or not parsed.hostname:
    print(f"ERROR: invalid --base-url '{raw}'. Use something like http://localhost:9091", file=sys.stderr)
    raise SystemExit(1)

port = parsed.port
if port is None:
    port = 443 if parsed.scheme == "https" else 80

print(parsed.scheme, parsed.hostname, port)
PY
  )
}

resolve_bins() {
  if [[ -z "$JMETER_BIN" ]]; then
    if command -v jmeter >/dev/null 2>&1; then
      JMETER_BIN="$(command -v jmeter)"
    else
      echo "ERROR: jmeter not found in PATH" >&2
      exit 1
    fi
  fi

  if [[ -z "$FILTER_BIN" ]]; then
    if command -v FilterResults.sh >/dev/null 2>&1; then
      FILTER_BIN="$(command -v FilterResults.sh)"
    else
      local jmeter_dir
      jmeter_dir="$(cd "$(dirname "$JMETER_BIN")" && pwd)"
      if [[ -x "${jmeter_dir}/FilterResults.sh" ]]; then
        FILTER_BIN="${jmeter_dir}/FilterResults.sh"
      else
        echo "ERROR: FilterResults.sh not found; set FILTER_BIN explicitly" >&2
        exit 1
      fi
    fi
  fi
}

prepare_payload_if_needed() {
  if [[ ! -f "$PAYLOAD_TOOL" ]]; then
    echo "ERROR: payload tool not found: $PAYLOAD_TOOL" >&2
    exit 1
  fi

  local needs_prepare="0"

  case "$WORKLOAD" in
    user-summary|user-summary-legacy|tweets-get)
      USER_IDS_FILE="${USER_IDS_FILE:-${PAYLOAD_DIR}/user-ids.csv}"
      [[ -f "$USER_IDS_FILE" ]] || needs_prepare="1"
      ;;
    follow-create)
      USER_IDS_FILE="${USER_IDS_FILE:-${PAYLOAD_DIR}/follower-user-ids.csv}"
      TARGET_USER_IDS_FILE="${TARGET_USER_IDS_FILE:-${PAYLOAD_DIR}/followed-user-ids.csv}"
      [[ -f "$USER_IDS_FILE" && -f "$TARGET_USER_IDS_FILE" ]] || needs_prepare="1"
      ;;
    tweet-update)
      TWEET_UPDATES_FILE="${TWEET_UPDATES_FILE:-${PAYLOAD_DIR}/tweet-updates.csv}"
      [[ -f "$TWEET_UPDATES_FILE" ]] || needs_prepare="1"
      ;;
    user-create)
      ;;
  esac

  if [[ "$needs_prepare" == "0" || "$WORKLOAD" == "user-create" ]]; then
    return
  fi

  if [[ "$AUTO_PREPARE" != "1" ]]; then
    echo "ERROR: required payload is missing for workload '${WORKLOAD}' and auto-prepare is disabled." >&2
    exit 1
  fi

  mkdir -p "$PAYLOAD_DIR"
  echo "Auto-preparing JMeter payload for workload '${WORKLOAD}'..."

  case "$WORKLOAD" in
    user-summary|user-summary-legacy)
      python3 "$PAYLOAD_TOOL" user-summary \
        --count "$PAYLOAD_COUNT" \
        --output-dir "$PAYLOAD_DIR" \
        --seed-users "$SEED_USERS"
      ;;
    follow-create)
      python3 "$PAYLOAD_TOOL" follow-create \
        --count "$PAYLOAD_COUNT" \
        --output-dir "$PAYLOAD_DIR" \
        --seed-users "$SEED_USERS"
      ;;
    tweet-update)
      python3 "$PAYLOAD_TOOL" tweet-update \
        --count "$PAYLOAD_COUNT" \
        --output-dir "$PAYLOAD_DIR" \
        --seed-users "$SEED_USERS" \
        --seed-tweets "$SEED_TWEETS" \
        --content-length "$CONTENT_LENGTH"
      ;;
    tweets-get)
      python3 "$PAYLOAD_TOOL" tweets-get \
        --count "$PAYLOAD_COUNT" \
        --output-dir "$PAYLOAD_DIR" \
        --seed-users "$SEED_USERS" \
        --seed-tweets "$SEED_TWEETS"
      ;;
  esac
}

main() {
  parse_args "$@"

  AUTO_PREPARE="$(normalize_bool "AUTO_PREPARE" "$AUTO_PREPARE")"
  SEED_USERS="$(normalize_bool "SEED_USERS" "$SEED_USERS")"
  SEED_TWEETS="$(normalize_bool "SEED_TWEETS" "$SEED_TWEETS")"
  require_positive_int "START_OFFSET" "$START_OFFSET"
  require_positive_int "PAYLOAD_COUNT" "$PAYLOAD_COUNT"
  require_positive_int "CONTENT_LENGTH" "$CONTENT_LENGTH"

  resolve_workload
  parse_base_url
  resolve_bins

  if [[ -n "$USER_IDS_FILE" ]]; then
    USER_IDS_FILE="$(resolve_path "$USER_IDS_FILE")"
  fi
  if [[ -n "$TARGET_USER_IDS_FILE" ]]; then
    TARGET_USER_IDS_FILE="$(resolve_path "$TARGET_USER_IDS_FILE")"
  fi
  if [[ -n "$TWEET_UPDATES_FILE" ]]; then
    TWEET_UPDATES_FILE="$(resolve_path "$TWEET_UPDATES_FILE")"
  fi

  prepare_payload_if_needed

  local run_id results_dir results_jtl filtered_jtl
  run_id="$(date +%Y%m%d_%H%M%S_%N)"
  results_dir="${RESULTS_ROOT}/${WORKLOAD}_${run_id}"
  results_jtl="${results_dir}/results.jtl"
  filtered_jtl="${results_dir}/filtered.jtl"

  mkdir -p "$results_dir"
  rm -f "$results_jtl" "$filtered_jtl"

  echo "Using:"
  echo "  WORKLOAD=$WORKLOAD"
  echo "  DESCRIPTION=$WORKLOAD_DESC"
  echo "  PLAN=$PLAN"
  echo "  BASE_URL=$BASE_URL"
  echo "  TARGET_SCHEME=$TARGET_SCHEME"
  echo "  TARGET_HOST=$TARGET_HOST"
  echo "  TARGET_PORT=$TARGET_PORT"
  echo "  PAYLOAD_DIR=$PAYLOAD_DIR"
  [[ -n "$USER_IDS_FILE" ]] && echo "  USER_IDS_FILE=$USER_IDS_FILE"
  [[ -n "$TARGET_USER_IDS_FILE" ]] && echo "  TARGET_USER_IDS_FILE=$TARGET_USER_IDS_FILE"
  [[ -n "$TWEET_UPDATES_FILE" ]] && echo "  TWEET_UPDATES_FILE=$TWEET_UPDATES_FILE"
  echo "  JMETER_BIN=$JMETER_BIN"
  echo "  FILTER_BIN=$FILTER_BIN"
  echo "  RESULTS_DIR=$results_dir"
  echo "  RESULTS_JTL=$results_jtl"
  echo "  FILTERED_JTL=$filtered_jtl"
  echo "  START_OFFSET=$START_OFFSET"

  "$JMETER_BIN" -n -t "$PLAN" -l "$results_jtl" \
    -Jpayload_dir="$PAYLOAD_DIR" \
    -Jresults_jtl="$results_jtl" \
    -Jfiltered_jtl="$filtered_jtl" \
    -Jtarget_scheme="$TARGET_SCHEME" \
    -Jtarget_host="$TARGET_HOST" \
    -Jtarget_port="$TARGET_PORT" \
    -Juser_ids_file="${USER_IDS_FILE:-}" \
    -Jtarget_user_ids_file="${TARGET_USER_IDS_FILE:-}" \
    -Jtweet_updates_file="${TWEET_UPDATES_FILE:-}"

  "$FILTER_BIN" --output-file "$filtered_jtl" --input-file "$results_jtl" --start-offset "$START_OFFSET"

  echo "Done."
  echo "  results_dir=$results_dir"
  echo "  results_jtl=$results_jtl"
  echo "  filtered_jtl=$filtered_jtl"
}

main "$@"
