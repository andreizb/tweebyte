#!/usr/bin/env bash
# JMeter benchmark runner — sweeps concurrency, repeats N times per cell,
# samples Actuator/Prometheus during the steady-state phase, and emits a
# per-cell summary with mean / sigma / p95 / CI95.
#
# Methodology mirrors the k6 runner so JMeter and k6 results are directly
# comparable. Plan files use ${__P(concurrency,N)} / ${__P(ramp_time,60)} /
# ${__P(duration,240)} and are passed through via -J flags
# below. Default duration = 240 s = ramp_time(60) + main_phase(180); the inline
# JTL filter drops the first 60 s so analysis is on the 180 s steady state.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
WORKLOADS_ROOT="${SCRIPT_DIR}/workloads"
RESULTS_ROOT="${RESULTS_ROOT:-${REPO_ROOT}/testing-results/performance/jmeter}"

JMETER_BIN="${JMETER_BIN:-}"

WORKLOAD="${WORKLOAD:-dbread-fanout-user-profile}"
BASE_URL="${BASE_URL:-}"
ACTUATOR_URL="${ACTUATOR_URL:-}"
BENCHMARK_TOPOLOGY="${BENCHMARK_TOPOLOGY:-${TOPOLOGY:-unknown}}"
BENCHMARK_INFRA_TOPOLOGY="${BENCHMARK_INFRA_TOPOLOGY:-docker}"
CONCURRENCIES_RAW="${CONCURRENCIES:-10 50 100 250 500 750 1000}"
RUNS="${RUNS:-5}"
WARMUP_SECS="${WARMUP_SECS:-60}"
MAIN_SECS="${MAIN_SECS:-180}"
COLLECT_RESOURCES="${COLLECT_RESOURCES:-1}"
SAMPLE_EVERY_SECS="${SAMPLE_EVERY_SECS:-1}"
AUTO_PREPARE="${AUTO_PREPARE:-1}"
PAYLOAD_COUNT="${PAYLOAD_COUNT:-1000}"
TWEETS_PER_USER="${TWEETS_PER_USER:-1000}"
CONTENT_LENGTH="${CONTENT_LENGTH:-120}"
SEED_USERS="${SEED_USERS:-1}"
SEED_TWEETS="${SEED_TWEETS:-1}"
USER_IDS_FILE="${USER_IDS_FILE:-}"
TARGET_USER_IDS_FILE="${TARGET_USER_IDS_FILE:-}"
TWEET_UPDATES_FILE="${TWEET_UPDATES_FILE:-}"
SEARCH_TERMS_FILE="${SEARCH_TERMS_FILE:-}"

PLAN=""
PAYLOAD_DIR=""
TARGET_SCHEME=""
TARGET_HOST=""
TARGET_PORT=""
OUT_DIR=""
RECAP_ROWS=()
CONCURRENCY_LIST=()

usage() {
  cat <<'EOF'
Usage:
  ./testing/performance/jmeter/run_bench.sh \
    --workload <dbread-fanout-user-profile|throughput-user-summary|dbwrite-light-follow-create|dbwrite-heavy-tweet-update|serialize-tweets-bulk|dbread-heavy-tweet-search> \
    --concurrencies "10 100 1000" \
    --runs 5 \
    [--warmup 60] [--main 180] \
    [--base-url <url>] [--actuator-url <url>] \
    [--collect-resources <0|1>] [--sample-every <seconds>] \
    [--auto-prepare <0|1>] [--payload-count <count>] \
    [--seed-users <0|1>] [--seed-tweets <0|1>] \
    [--tweets-per-user <count>] [--content-length <chars>]

Workloads:
  dbread-fanout-user-profile      GET /users/{id} (fan-out)          user-service:9091
  throughput-user-summary         GET /users/{id}/summary            user-service:9091
  dbwrite-light-follow-create     POST /follows/{targetId}/{id}      interaction-service:9093
  dbwrite-heavy-tweet-update      PUT /tweets/{userId}/{tweetId}     tweet-service:9092
  serialize-tweets-bulk           GET /tweets/user/{id}?size=1000     tweet-service:9092
  dbread-heavy-tweet-search       GET /tweets/search/{term}           tweet-service:9092
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
      --actuator-url) ACTUATOR_URL="$2"; shift 2 ;;
      --concurrencies) CONCURRENCIES_RAW="$2"; shift 2 ;;
      --runs) RUNS="$2"; shift 2 ;;
      --warmup) WARMUP_SECS="$2"; shift 2 ;;
      --main) MAIN_SECS="$2"; shift 2 ;;
      --collect-resources) COLLECT_RESOURCES="$2"; shift 2 ;;
      --sample-every) SAMPLE_EVERY_SECS="$2"; shift 2 ;;
      --results-root) RESULTS_ROOT="$(resolve_path "$2")"; shift 2 ;;
      --auto-prepare) AUTO_PREPARE="$2"; shift 2 ;;
      --payload-count) PAYLOAD_COUNT="$2"; shift 2 ;;
      --tweets-per-user) TWEETS_PER_USER="$2"; shift 2 ;;
      --seed-users) SEED_USERS="$2"; shift 2 ;;
      --seed-tweets) SEED_TWEETS="$2"; shift 2 ;;
      --content-length) CONTENT_LENGTH="$2"; shift 2 ;;
      --user-ids-file) USER_IDS_FILE="$2"; shift 2 ;;
      --target-user-ids-file) TARGET_USER_IDS_FILE="$2"; shift 2 ;;
      --tweet-updates-file) TWEET_UPDATES_FILE="$2"; shift 2 ;;
      --search-terms-file) SEARCH_TERMS_FILE="$2"; shift 2 ;;
      --help|-h) usage; exit 0 ;;
      *)
        echo "ERROR: unknown argument '$1'." >&2
        usage
        exit 1
        ;;
    esac
  done
}

validate_args() {
  COLLECT_RESOURCES="$(normalize_bool "COLLECT_RESOURCES" "$COLLECT_RESOURCES")"
  AUTO_PREPARE="$(normalize_bool "AUTO_PREPARE" "$AUTO_PREPARE")"
  SEED_USERS="$(normalize_bool "SEED_USERS" "$SEED_USERS")"
  SEED_TWEETS="$(normalize_bool "SEED_TWEETS" "$SEED_TWEETS")"
  require_positive_int "RUNS" "$RUNS"
  require_positive_int "WARMUP_SECS" "$WARMUP_SECS"
  require_positive_int "MAIN_SECS" "$MAIN_SECS"
  require_positive_int "SAMPLE_EVERY_SECS" "$SAMPLE_EVERY_SECS"
  require_positive_int "PAYLOAD_COUNT" "$PAYLOAD_COUNT"
  require_positive_int "TWEETS_PER_USER" "$TWEETS_PER_USER"
  require_positive_int "CONTENT_LENGTH" "$CONTENT_LENGTH"

  read -r -a CONCURRENCY_LIST <<<"$CONCURRENCIES_RAW"
  if ((${#CONCURRENCY_LIST[@]} == 0)); then
    echo "ERROR: at least one concurrency value is required." >&2
    exit 1
  fi
  local c
  for c in "${CONCURRENCY_LIST[@]}"; do
    require_positive_int "CONCURRENCY" "$c"
  done
}

resolve_workload() {
  # Per-workload directory layout:
  #   workloads/<name>/
  #     plan.jmx              — JMeter test plan
  #     prepare.py            — payload seeder; called for --auto-prepare
  #     run.sh                — workload entry point
  #     precell_reset.sh      — optional, sourced before each cell
  #     payload/              — generated CSVs (gitignored)
  WORKLOAD_HOME="${WORKLOADS_ROOT}/${WORKLOAD}"
  if [[ ! -d "$WORKLOAD_HOME" ]]; then
    echo "ERROR: unsupported workload '${WORKLOAD}' — no directory at ${WORKLOAD_HOME}" >&2
    usage
    exit 1
  fi

  PLAN="${WORKLOAD_HOME}/plan.jmx"
  PAYLOAD_DIR="${WORKLOAD_HOME}/payload"
  PREPARE_TOOL="${WORKLOAD_HOME}/prepare.py"
  PRECELL_RESET_HOOK="${WORKLOAD_HOME}/precell_reset.sh"

  if [[ ! -f "$PLAN" ]]; then
    echo "ERROR: JMeter plan not found: $PLAN" >&2
    exit 1
  fi

  if [[ -z "$BASE_URL" ]]; then
    echo "ERROR: --base-url is required" >&2
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

  # JMeter's launcher script requires JAVA_HOME or JRE_HOME. If neither is set
  # but `java` is on PATH, derive JAVA_HOME from its install root so the user
  # doesn't have to remember to export it before each run.
  if [[ -z "${JAVA_HOME:-}" && -z "${JRE_HOME:-}" ]]; then
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
      local detected
      if detected="$(/usr/libexec/java_home 2>/dev/null)"; then
        export JAVA_HOME="$detected"
      fi
    fi
    if [[ -z "${JAVA_HOME:-}" ]] && command -v java >/dev/null 2>&1; then
      local java_bin java_real
      java_bin="$(command -v java)"
      java_real="$(readlink "$java_bin" 2>/dev/null || printf '%s' "$java_bin")"
      if [[ "$java_real" != /* ]]; then
        java_real="$(cd "$(dirname "$java_bin")" && cd "$(dirname "$java_real")" && pwd)/$(basename "$java_real")"
      fi
      export JAVA_HOME="$(cd "$(dirname "$java_real")/.." && pwd)"
    fi
  fi

  if [[ -z "${JAVA_HOME:-}" && -z "${JRE_HOME:-}" ]]; then
    echo "ERROR: JAVA_HOME / JRE_HOME unset and could not be auto-detected." >&2
    exit 1
  fi
}

# Filter a JTL file by start-offset seconds — keeps rows whose timeStamp is
# at least (test_start + offset) seconds. JMeter's separately-distributed
# FilterResults.sh (cmdrunner-tools plugin) does this; doing it inline keeps
# the runner self-contained and avoids a brittle plugin dependency.
filter_jtl() {
  local input="$1" output="$2" offset_secs="$3"

  python3 - "$input" "$output" "$offset_secs" <<'PY'
import csv
import sys
from pathlib import Path

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
offset_ms = int(float(sys.argv[3]) * 1000)

if not src.exists() or src.stat().st_size == 0:
    dst.write_text("")
    sys.exit(0)

with src.open() as fh:
    reader = csv.reader(fh)
    try:
        header = next(reader)
    except StopIteration:
        dst.write_text("")
        sys.exit(0)

    if "timeStamp" not in header:
        # JMeter wrote a CSV without a header (jmeter.save.saveservice.print_field_names=false)
        # or in an unexpected shape — pass through as-is.
        dst.write_text(src.read_text())
        sys.exit(0)

    ts_idx = header.index("timeStamp")

    rows = []
    first_ts = None
    for row in reader:
        if not row or len(row) <= ts_idx:
            continue
        try:
            ts = int(row[ts_idx])
        except ValueError:
            continue
        if first_ts is None:
            first_ts = ts
        if ts - first_ts >= offset_ms:
            rows.append(row)

with dst.open("w", newline="") as out:
    writer = csv.writer(out)
    writer.writerow(header)
    writer.writerows(rows)
PY
}

prepare_payload_if_needed() {
  # Each workload's prepare.py owns its own seeding logic and its own CSV
  # filenames. The runner only checks whether the expected CSVs exist and
  # forwards the right --seed-* / --tweets-per-user / --content-length flags.
  if [[ ! -f "$PREPARE_TOOL" ]]; then
    echo "ERROR: payload prep tool not found: $PREPARE_TOOL" >&2
    exit 1
  fi

  local needs_prepare="0"
  case "$WORKLOAD" in
    dbread-fanout-user-profile|throughput-user-summary|serialize-tweets-bulk)
      [[ -f "$USER_IDS_FILE" ]] || needs_prepare="1"
      ;;
    dbwrite-light-follow-create)
      # Single cohort CSV now (plan.jmx derives each follow pair per-iteration from a
      # global counter indexed into it); the old rotated target file is gone.
      [[ -f "$USER_IDS_FILE" ]] || needs_prepare="1"
      ;;
    dbwrite-heavy-tweet-update)
      [[ -f "$TWEET_UPDATES_FILE" ]] || needs_prepare="1"
      ;;
    dbread-heavy-tweet-search)
      [[ -f "$SEARCH_TERMS_FILE" ]] || needs_prepare="1"
      ;;
  esac

  # Force-prepare when --seed-users 1 / --seed-tweets 1 was passed even if the
  # CSV exists, because CSV existence does not guarantee the DB is seeded
  # (e.g. someone destroyed infra between runs and the CSV is now stale).
  # Seeding SQL is idempotent.
  if [[ "$SEED_USERS" == "1" || "$SEED_TWEETS" == "1" ]]; then
    needs_prepare="1"
  fi

  if [[ "$needs_prepare" == "0" ]]; then
    return
  fi

  echo "ERROR: required payload is missing for workload '${WORKLOAD}'. Run prepare.py manually before invoking the runner." >&2
  exit 1
}

# Background poller — scrapes /actuator/prometheus while JMeter runs and
# writes one CSV row per sample. Started AFTER the warm-up so the resource
# numbers reflect the steady-state phase (matching k6's pattern).
sample_resources() {
  local jmeter_pid="$1" c="$2" i="$3" csv="$4" actuator="$5" step="$6"

  # Columns: heap_used_* is jvm_memory_used_bytes (live objects), heap_committed_*
  # is jvm_memory_committed_bytes (heap pages committed by the JVM). Emit both so
  # reports can distinguish live occupancy from the JVM's commitment policy.
  echo "ts_iso,concurrency,run,cpu_usage_pct,heap_used_bytes,heap_used_mb,heap_committed_bytes,heap_committed_mb" > "$csv"

  while kill -0 "$jmeter_pid" >/dev/null 2>&1; do
    local ts_iso cpu_raw cpu_pct heap_bytes heap_mb heap_committed_bytes heap_committed_mb page
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

    heap_committed_bytes="$(printf "%s\n" "$page" | perl -ne '
      if(/^jvm_memory_committed_bytes\{[^}]*area="heap"[^}]*\}\s+([0-9.]+(?:[eE][+-]?\d+)?)/){
        $s += $1
      }
      END { printf("%.0f", $s || 0) }
    ')"
    heap_committed_mb="$(awk -v v="$heap_committed_bytes" 'BEGIN{printf "%.2f", v/1024/1024}')"

    echo "$ts_iso,$c,$i,$cpu_pct,$heap_bytes,$heap_mb,$heap_committed_bytes,$heap_committed_mb" >> "$csv"
    sleep "$step"
  done
}

# Parse a filtered .jtl file into a tab-separated row:
#   total_count  success_count  success_rps  avg_ms  p90_ms  p95_ms  failed_pct
# The filtered.jtl is a CSV with JMeter sample rows; first column is timestamp,
# second is `elapsed` in ms, the `success` boolean tells us whether the request
# succeeded. Throughput and latency are based on successful samples only so a
# 500/404 storm cannot look like high performance.
parse_jtl_summary() {
  local jtl="$1" main_secs="$2"

  python3 - "$jtl" "$main_secs" <<'PY'
import csv
import sys
from pathlib import Path

path = Path(sys.argv[1])
main_secs = float(sys.argv[2])

if not path.exists():
    print("\t".join(["-"] * 7))
    sys.exit(0)

successful_elapsed = []
failed = 0
total = 0
with path.open() as fh:
    reader = csv.DictReader(fh)
    for row in reader:
        total += 1
        success = row.get("success", "true").lower() == "true"
        if not success:
            failed += 1
            continue
        try:
            successful_elapsed.append(float(row["elapsed"]))
        except (KeyError, ValueError):
            continue

success_count = len(successful_elapsed)
if total == 0:
    print("\t".join(["0", "0", "0.00", "-", "-", "-", "-"]))
    sys.exit(0)

successful_elapsed.sort()
def pct(p):
    if not successful_elapsed:
        return None
    k = max(0, min(len(successful_elapsed) - 1, int(round((p / 100.0) * (len(successful_elapsed) - 1)))))
    return successful_elapsed[k]

def fmt(value, prec=3):
    return "-" if value is None else f"{value:.{prec}f}"

avg = sum(successful_elapsed) / success_count if success_count else None
p90 = pct(90)
p95 = pct(95)
rps = success_count / main_secs if main_secs > 0 else 0.0
fail_pct = 100.0 * failed / total if total else 0.0
print(f"{total}\t{success_count}\t{rps:.2f}\t{fmt(avg)}\t{fmt(p90)}\t{fmt(p95)}\t{fail_pct:.2f}%")
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

# Backward-compatible: heap_committed_mb may be absent in resource csvs
# produced by pre-patch runs. Fall back to "-" placeholders in that case.
cpu = [float(r["cpu_usage_pct"]) for r in rows if r.get("cpu_usage_pct", "NaN") != "NaN"]
heap = [float(r["heap_used_mb"]) for r in rows if r.get("heap_used_mb", "NaN") != "NaN"]
heap_committed = [float(r["heap_committed_mb"]) for r in rows if r.get("heap_committed_mb", "NaN") != "NaN"]

if not rows or not cpu or not heap:
    print("\t".join(["-", "-", "-", "-", "-", "-"]))
else:
    if heap_committed:
        avg_committed = f"{sum(heap_committed) / len(heap_committed):.2f}"
        max_committed = f"{max(heap_committed):.2f}"
    else:
        avg_committed = "-"
        max_committed = "-"
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

print_run_summary() {
  local jtl="$1" res_csv="$2" c="$3" i="$4"
  local total_count success_count rps avg p90 p95 failed
  local avg_cpu max_cpu avg_heap max_heap avg_heap_committed max_heap_committed

  IFS=$'\t' read -r total_count success_count rps avg p90 p95 failed <<<"$(parse_jtl_summary "$jtl" "$MAIN_SECS")"
  IFS=$'\t' read -r avg_cpu max_cpu avg_heap max_heap avg_heap_committed max_heap_committed <<<"$(parse_resources_summary "$res_csv")"

  printf 'Summary: workload=%s concurrency=%s run=%s\n' "$WORKLOAD" "$c" "$i"
  printf '  filtered_jtl=%s\n' "$jtl"
  printf '  total_count=%s\n' "$total_count"
  printf '  success_count=%s\n' "$success_count"
  printf '  success_throughput_rps=%s\n' "$rps"
  printf '  avg_ms=%s\n' "$avg"
  printf '  p90_ms=%s\n' "$p90"
  printf '  p95_ms=%s\n' "$p95"
  printf '  failed_pct=%s\n' "$failed"

  if [[ "$COLLECT_RESOURCES" == "0" ]]; then
    printf '  resources=%s\n' "skipped"
  elif [[ "$avg_cpu" == "-" ]]; then
    printf '  resources=%s\n' "unavailable"
  else
    printf '  avg_cpu_pct=%s\n' "$avg_cpu"
    printf '  max_cpu_pct=%s\n' "$max_cpu"
    printf '  avg_heap_mb=%s\n' "$avg_heap"
    printf '  max_heap_mb=%s\n' "$max_heap"
    printf '  avg_heap_committed_mb=%s\n' "$avg_heap_committed"
    printf '  max_heap_committed_mb=%s\n' "$max_heap_committed"
  fi

  RECAP_ROWS+=("${c}|${i}|${total_count}|${success_count}|${rps}|${avg}|${p90}|${p95}|${failed}|${avg_cpu}|${max_cpu}|${avg_heap}|${max_heap}|${avg_heap_committed}|${max_heap_committed}")
  echo
}

# Aggregate per-cell stats across runs and write cells.csv. CI95 is computed
# as mean ± 1.96·σ/√n (normal approximation), matching the convention used
# in the §3 k6 baseline tables.
print_final_recap() {
  local cells_csv="${OUT_DIR}/cells.csv"

  python3 - "$cells_csv" "${RECAP_ROWS[@]}" <<'PY'
import math
import sys
from collections import defaultdict
from pathlib import Path

cells_path = Path(sys.argv[1])
rows = sys.argv[2:]

per_cell = defaultdict(list)
for raw in rows:
    parts = raw.split("|")
    # 15 fields: 13 original + 2 new committed-heap fields appended by
    # print_run_summary. RECAP_ROWS produced by older runs would have 13;
    # those would error here, but the patch ships RECAP_ROWS and aggregator
    # together, so the producer/consumer are version-locked per invocation.
    (c, i, total_count, success_count, rps, avg, p90, p95, failed_pct,
     avg_cpu, max_cpu, avg_heap, max_heap,
     avg_heap_committed, max_heap_committed) = parts
    per_cell[int(c)].append({
        "total_count": int(total_count) if total_count not in {"-", ""} else None,
        "success_count": int(success_count) if success_count not in {"-", ""} else None,
        "rps": float(rps) if rps not in {"-", ""} else None,
        "avg": float(avg) if avg not in {"-", ""} else None,
        "p90": float(p90) if p90 not in {"-", ""} else None,
        "p95": float(p95) if p95 not in {"-", ""} else None,
        "failed_pct": float(failed_pct.rstrip("%")) if failed_pct not in {"-", ""} else None,
        "cpu": float(avg_cpu) if avg_cpu not in {"-", ""} else None,
        "heap": float(avg_heap) if avg_heap not in {"-", ""} else None,
        "heap_committed": float(avg_heap_committed) if avg_heap_committed not in {"-", ""} else None,
    })

def stats(values):
    xs = [v for v in values if v is not None]
    if not xs:
        return None, None, None, None
    n = len(xs)
    mean = sum(xs) / n
    var = sum((x - mean) ** 2 for x in xs) / (n - 1) if n > 1 else 0.0
    sigma = math.sqrt(var)
    half = 1.96 * sigma / math.sqrt(n) if n > 0 else 0.0
    return mean, sigma, mean - half, mean + half

print("Final recap (per-cell aggregate over runs):")
# Columns appended at the end for backward compatibility with prior cells.csv
# consumers. p90 was already computed per-run but never aggregated; now is.
# heap_committed_mean_mb pairs with the existing heap_mean_mb (= used).
header = ["concurrency", "n", "total_count", "success_count", "failed_pct_mean",
          "rps_mean", "rps_ci95_lo", "rps_ci95_hi",
          "avg_ms_mean", "avg_ms_sigma", "p95_ms_mean", "p95_ms_sigma",
          "cpu_mean", "cpu_sigma", "heap_mean_mb", "heap_sigma_mb",
          "p90_ms_mean", "p90_ms_sigma",
          "heap_committed_mean_mb", "heap_committed_sigma_mb"]
print("\t".join(header))

with cells_path.open("w") as out:
    out.write(",".join(header) + "\n")

    for c in sorted(per_cell.keys()):
        cell = per_cell[c]
        n = len(cell)
        total_count = sum(r["total_count"] or 0 for r in cell)
        success_count = sum(r["success_count"] or 0 for r in cell)
        failed_m, _, _, _ = stats([r["failed_pct"] for r in cell])
        rps_m, rps_s, rps_lo, rps_hi = stats([r["rps"] for r in cell])
        avg_m, avg_s, _, _ = stats([r["avg"] for r in cell])
        p90_m, p90_s, _, _ = stats([r["p90"] for r in cell])
        p95_m, p95_s, _, _ = stats([r["p95"] for r in cell])
        cpu_m, cpu_s, _, _ = stats([r["cpu"] for r in cell])
        heap_m, heap_s, _, _ = stats([r["heap"] for r in cell])
        heap_c_m, heap_c_s, _, _ = stats([r["heap_committed"] for r in cell])

        def fmt(v, prec=2):
            return f"{v:.{prec}f}" if v is not None else "-"

        line = [
            str(c), str(n), str(total_count), str(success_count), fmt(failed_m),
            fmt(rps_m), fmt(rps_lo), fmt(rps_hi),
            fmt(avg_m, 3), fmt(avg_s, 3),
            fmt(p95_m, 3), fmt(p95_s, 3),
            fmt(cpu_m), fmt(cpu_s),
            fmt(heap_m), fmt(heap_s),
            fmt(p90_m, 3), fmt(p90_s, 3),
            fmt(heap_c_m), fmt(heap_c_s),
        ]
        print("\t".join(line))
        out.write(",".join(line) + "\n")

print()
print(f"cells.csv: {cells_path}")
PY
}

precell_reset() {
  # Each workload may ship a precell_reset.sh in its directory. We source it
  # so it can use shell vars like $REPO_ROOT, $WORKLOAD, $PAYLOAD_DIR, etc.
  # Workloads that don't need a reset simply don't ship the file.
  if [[ -f "$PRECELL_RESET_HOOK" ]]; then
    # shellcheck disable=SC1090
    source "$PRECELL_RESET_HOOK"
  fi
}

run_one_cell() {
  local c="$1" i="$2"
  local total_secs=$(( WARMUP_SECS + MAIN_SECS ))
  local jtl="${OUT_DIR}/${c}_${i}.jtl"
  local filtered_jtl="${OUT_DIR}/${c}_${i}_filtered.jtl"
  local res_csv="${OUT_DIR}/${c}_${i}_resources.csv"
  local jmeter_log="${OUT_DIR}/${c}_${i}_jmeter.log"

  precell_reset

  echo "Running workload=$WORKLOAD concurrency=$c iteration=$i (total ${total_secs}s = ${WARMUP_SECS}s ramp + ${MAIN_SECS}s steady)..."

  export HEAP="${HEAP:--Xmx4g}"
  "$JMETER_BIN" -n -t "$PLAN" -l "$jtl" -j "$jmeter_log" \
    -Jconcurrency="$c" \
    -Jramp_time="$WARMUP_SECS" \
    -Jduration="$total_secs" \
    -Jpage_size="$TWEETS_PER_USER" \
    -Jpayload_dir="$PAYLOAD_DIR" \
    -Jresults_jtl="$jtl" \
    -Jfiltered_jtl="$filtered_jtl" \
    -Jtarget_scheme="$TARGET_SCHEME" \
    -Jtarget_host="$TARGET_HOST" \
    -Jtarget_port="$TARGET_PORT" \
    -Juser_ids_file="${USER_IDS_FILE:-}" \
    -Jtarget_user_ids_file="${TARGET_USER_IDS_FILE:-}" \
    -Jtweet_updates_file="${TWEET_UPDATES_FILE:-}" \
    -Jsearch_terms_file="${SEARCH_TERMS_FILE:-}" \
    >/dev/null &
  local jmeter_pid=$!

  local samp_pid=""
  if [[ "$COLLECT_RESOURCES" == "1" ]]; then
    (
      sleep "$WARMUP_SECS"
      if kill -0 "$jmeter_pid" >/dev/null 2>&1; then
        sample_resources "$jmeter_pid" "$c" "$i" "$res_csv" "$ACTUATOR_URL" "$SAMPLE_EVERY_SECS"
      fi
    ) &
    samp_pid=$!
  fi

  wait "$jmeter_pid" || true
  sleep 1

  if [[ -n "$samp_pid" ]]; then
    kill "$samp_pid" >/dev/null 2>&1 || true
    wait "$samp_pid" 2>/dev/null || true
  fi

  if [[ -f "$jtl" ]]; then
    filter_jtl "$jtl" "$filtered_jtl" "$WARMUP_SECS"
  else
    echo "  WARN: results JTL missing — JMeter may have failed; check $jmeter_log"
  fi

  print_run_summary "$filtered_jtl" "$res_csv" "$c" "$i"
}

print_config() {
  echo "Using:"
  echo "  WORKLOAD=$WORKLOAD"
  echo "  PLAN=$PLAN"
  echo "  BASE_URL=$BASE_URL"
  echo "  BENCHMARK_TOPOLOGY=$BENCHMARK_TOPOLOGY"
  echo "  ACTUATOR_URL=$ACTUATOR_URL"
  echo "  CONCURRENCIES=${CONCURRENCY_LIST[*]}"
  echo "  RUNS=$RUNS"
  echo "  WARMUP_SECS=$WARMUP_SECS  MAIN_SECS=$MAIN_SECS"
  echo "  COLLECT_RESOURCES=$COLLECT_RESOURCES"
  echo "  PAYLOAD_DIR=$PAYLOAD_DIR"
  [[ -n "$USER_IDS_FILE" ]] && echo "  USER_IDS_FILE=$USER_IDS_FILE"
  [[ -n "$TARGET_USER_IDS_FILE" ]] && echo "  TARGET_USER_IDS_FILE=$TARGET_USER_IDS_FILE"
  [[ -n "$TWEET_UPDATES_FILE" ]] && echo "  TWEET_UPDATES_FILE=$TWEET_UPDATES_FILE"
  [[ -n "$SEARCH_TERMS_FILE" ]] && echo "  SEARCH_TERMS_FILE=$SEARCH_TERMS_FILE"
  if [[ "$WORKLOAD" == "serialize-tweets-bulk" || "$WORKLOAD" == "dbread-heavy-tweet-search" ]]; then
    echo "  TWEETS_PER_USER=$TWEETS_PER_USER"
  fi
  echo "  JMETER_BIN=$JMETER_BIN"
  echo "  JAVA_HOME=${JAVA_HOME:-}"
  echo "  OUT_DIR=$OUT_DIR"
  echo
}

main() {
  parse_args "$@"
  validate_args
  resolve_workload
  parse_base_url
  resolve_bins

  if [[ -z "$ACTUATOR_URL" ]]; then
    ACTUATOR_URL="${BASE_URL}/actuator/prometheus"
  fi

  if [[ -n "$USER_IDS_FILE" ]]; then USER_IDS_FILE="$(resolve_path "$USER_IDS_FILE")"; fi
  if [[ -n "$TARGET_USER_IDS_FILE" ]]; then TARGET_USER_IDS_FILE="$(resolve_path "$TARGET_USER_IDS_FILE")"; fi
  if [[ -n "$TWEET_UPDATES_FILE" ]]; then TWEET_UPDATES_FILE="$(resolve_path "$TWEET_UPDATES_FILE")"; fi
  if [[ -n "$SEARCH_TERMS_FILE" ]]; then SEARCH_TERMS_FILE="$(resolve_path "$SEARCH_TERMS_FILE")"; fi

  prepare_payload_if_needed

  mkdir -p "$RESULTS_ROOT"
  OUT_DIR="$(mktemp -d "${RESULTS_ROOT}/results_${WORKLOAD}_$(date +%Y%m%d_%H%M%S)_XXXXXX")"
  {
    echo "benchmark_topology=${BENCHMARK_TOPOLOGY}"
    echo "benchmark_infra_topology=${BENCHMARK_INFRA_TOPOLOGY}"
    echo "workload=${WORKLOAD}"
    echo "base_url=${BASE_URL}"
    echo "actuator_url=${ACTUATOR_URL}"
    echo "created_at=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  } > "${OUT_DIR}/run_metadata.env"

  print_config

  local c i
  for c in "${CONCURRENCY_LIST[@]}"; do
    for i in $(seq 1 "$RUNS"); do
      run_one_cell "$c" "$i"
    done
  done

  print_final_recap
  echo "Done. Logs in: $OUT_DIR"
}

main "$@"
