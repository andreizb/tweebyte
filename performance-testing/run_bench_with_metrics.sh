#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_JS="${SCRIPT_DIR}/redis-cache.js"
KEYS_FILE_DEFAULT="${SCRIPT_DIR}/follower_ids.txt"

K6_BIN="/opt/homebrew/bin/k6"
if [[ ! -x "$K6_BIN" ]]; then
  if command -v k6 >/dev/null 2>&1; then K6_BIN="$(command -v k6)"; else
    echo "ERROR: k6 not found. brew install k6"; exit 1
  fi
fi

BASE_URL="${BASE_URL:-http://localhost:9093}"
ACTUATOR_URL="${ACTUATOR_URL:-$BASE_URL/actuator/prometheus}"

KEYS_FILE="${KEYS_FILE:-$KEYS_FILE_DEFAULT}"
TOTAL_KEYS="${TOTAL_KEYS:-10000}"
HOT_COUNT="${HOT_COUNT:-1000}"
HOT_RATIO="${HOT_RATIO:-0.9}"
WARMUP_SECS_RAW="${WARMUP_SECS:-60s}"
DURATION="${DURATION:-3m}"

CONCURRENCIES=(${CONCURRENCIES:-1 5 10 15 20 25 50 75 100 200 400 600 800 1000})

SAMPLE_EVERY_SECS="${SAMPLE_EVERY_SECS:-1}"

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

WARMUP_SECS="$(secs_from_duration "$WARMUP_SECS_RAW")"

[[ -f "$SCRIPT_JS" ]] || { echo "Missing $SCRIPT_JS"; exit 1; }
[[ -f "$KEYS_FILE"  ]] || { echo "Missing KEYS_FILE: $KEYS_FILE"; exit 1; }

LINES=$(wc -l < "$KEYS_FILE" | tr -d ' ')
(( LINES > 0 )) || { echo "KEYS_FILE is empty"; exit 1; }

if (( TOTAL_KEYS > LINES )); then
  echo "TOTAL_KEYS=$TOTAL_KEYS > file lines=$LINES; using TOTAL_KEYS=$LINES"
  TOTAL_KEYS=$LINES
fi
if (( HOT_COUNT > TOTAL_KEYS )); then
  echo "HOT_COUNT=$HOT_COUNT > TOTAL_KEYS=$TOTAL_KEYS; using HOT_COUNT=$(( TOTAL_KEYS / 10 ))"
  HOT_COUNT=$(( TOTAL_KEYS / 10 ))
fi

OUT_DIR="${SCRIPT_DIR}/results_get_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$OUT_DIR"

echo "Using:"
echo "  BASE_URL=$BASE_URL"
echo "  ACTUATOR_URL=$ACTUATOR_URL"
echo "  SCRIPT_JS=$SCRIPT_JS"
echo "  KEYS_FILE=$KEYS_FILE (lines=$LINES)"
echo "  TOTAL_KEYS=$TOTAL_KEYS  HOT_COUNT=$HOT_COUNT  HOT_RATIO=$HOT_RATIO"
echo "  WARMUP=$WARMUP_SECS_RAW (parsed=${WARMUP_SECS}s)  DURATION=$DURATION"
echo "  SAMPLE_EVERY_SECS=$SAMPLE_EVERY_SECS"
echo "  Results: $OUT_DIR"
echo

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

for c in "${CONCURRENCIES[@]}"; do
   for i in {1..5}; do
    echo "Running concurrency $c, iteration $i..."

    RUN_TXT="$OUT_DIR/${c}_${i}.txt"
    RES_CSV="$OUT_DIR/${c}_${i}_resources.csv"

    "$K6_BIN" run \
      -e BASE_URL="$BASE_URL" \
      -e KEYS_FILE="$KEYS_FILE" \
      -e TOTAL_KEYS="$TOTAL_KEYS" \
      -e HOT_COUNT="$HOT_COUNT" \
      -e HOT_RATIO="$HOT_RATIO" \
      -e CONCURRENCY="$c" \
      -e WARMUP_SECS="$WARMUP_SECS_RAW" \
      -e DURATION="$DURATION" \
      "$SCRIPT_JS" > "$RUN_TXT" 2>&1 &

    K6_PID=$!

    (
      sleep "$WARMUP_SECS"
      if kill -0 "$K6_PID" >/dev/null 2>&1; then
        sample_resources "$K6_PID" "$c" "$i" "$RES_CSV" "$ACTUATOR_URL" "$SAMPLE_EVERY_SECS"
      fi
    ) & SAMP_PID=$!

    wait "$K6_PID" || true
    sleep 1
    kill "$SAMP_PID" >/dev/null 2>&1 || true
    wait "$SAMP_PID" 2>/dev/null || true
  done
done

echo "Done. Logs in: $OUT_DIR"