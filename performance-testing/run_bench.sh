# #!/usr/bin/env bash
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
KEYS_FILE="${KEYS_FILE:-$KEYS_FILE_DEFAULT}"
TOTAL_KEYS="${TOTAL_KEYS:-10000}"
HOT_COUNT="${HOT_COUNT:-1000}"
HOT_RATIO="${HOT_RATIO:-0.9}"
WARMUP_SECS="${WARMUP_SECS:-60s}"
DURATION="${DURATION:-3m}"
CONCURRENCIES=(${CONCURRENCIES:-1 5 10 15 20 25 50 75 100 200 400 600 800 1000})

[[ -f "$SCRIPT_JS" ]] || { echo "Missing $SCRIPT_JS"; exit 1; }
[[ -f "$KEYS_FILE"  ]] || { echo "Missing KEYS_FILE: $KEYS_FILE"; exit 1; }

LINES=$(wc -l < "$KEYS_FILE" | tr -d ' ')
if (( LINES == 0 )); then echo "KEYS_FILE is empty"; exit 1; fi

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
echo "  SCRIPT_JS=$SCRIPT_JS"
echo "  KEYS_FILE=$KEYS_FILE (lines=$LINES)"
echo "  TOTAL_KEYS=$TOTAL_KEYS  HOT_COUNT=$HOT_COUNT  HOT_RATIO=$HOT_RATIO"
echo "  WARMUP=$WARMUP_SECS  DURATION=$DURATION"
echo "  Results: $OUT_DIR"
echo

for c in "${CONCURRENCIES[@]}"; do
  for i in {1..5}; do
    echo "Running concurrency $c, iteration $i..."
    "$K6_BIN" run \
      -e BASE_URL="$BASE_URL" \
      -e KEYS_FILE="$KEYS_FILE" \
      -e TOTAL_KEYS="$TOTAL_KEYS" \
      -e HOT_COUNT="$HOT_COUNT" \
      -e HOT_RATIO="$HOT_RATIO" \
      -e CONCURRENCY="$c" \
      -e WARMUP_SECS="$WARMUP_SECS" \
      -e DURATION="$DURATION" \
      "$SCRIPT_JS" > "$OUT_DIR/${c}_${i}.txt"
  done
done

echo "Done. Logs in: $OUT_DIR"