#!/usr/bin/env bash
# Bench runner for dbread-heavy-tweet-search.
#
# Usage:
#   ./run.sh canonical   - 14 cells x 2 runs x (60s warmup + 120s steady) ~= 1.9 hr/stack
#                          Full sweep; the reportable default. Report mean + min/max RANGE
#                          (two-run range, NOT inferential CI95). If a headline cell's two runs
#                          differ by >5%, or the effect size is itself ~5%, rerun confidence
#                          before claiming a winner.
#   ./run.sh confidence  - 14 cells x 5 runs x (60s warmup + 180s steady) ~= 4.7 hr/stack
#                          Tight CI95 for disputed/close results; rarely run.
#
# Locked methodology:
#   - Mode-1: JMeter hits tweet-service direct on
#     http://localhost:9092, no app-side toxiproxy proxy in path.
#   - Endpoint: GET /tweets/search/{searchTerm} — pg_trgm trigram-similarity
#     scan (`<% ` operator) + GIN index on content + ORDER BY word_similarity
#     DESC, created_at DESC, id DESC LIMIT/OFFSET. Each request blocks on a
#     non-trivial indexed similarity scan and sort. This is the
#     DB-execution-bound profile: no fan-out, just tweet-service vs its own DB.
#   - DB pool min-idle 10 / max-size 200 (auto-grow, 3600s acquire) on both
#     stacks; reactive enables app.r2dbc.disable-colocation=true.
#   - Default seed shape: 1000 users × 1000 tweets/user = 1M tweet rows.
#     Each tweet content embeds one of 20 common
#     English words, so every search term matches ~50 000 rows — enough to
#     force a real similarity ranking on every query.
#   - Search terms: payload/search-terms.csv (20 common words, cycled by
#     JMeter's CSV Data Set Config with recycle=true).
#
# Pre-run safety: forces a re-seed of the tweets table (TRUNCATE CASCADE
# then INSERT) and asserts the row count matches the expected shape before
# launching.
set -u

PROFILE="${1:-}"
case "$PROFILE" in
  canonical)
    CONCS="1 5 10 15 20 25 50 75 100 200 400 600 800 1000"
    RUNS=2; WARMUP=60; MAIN=120
    ORDER="reactive async"
    ;;
  confidence)
    CONCS="1 5 10 15 20 25 50 75 100 200 400 600 800 1000"
    RUNS=5; WARMUP=60; MAIN=180
    ORDER="reactive async"
    ;;
  *)
    echo "Usage: $0 <canonical|confidence>" >&2
    exit 1
    ;;
esac

WORKLOAD="dbread-heavy-tweet-search"
USER_COUNT=1000
TWEETS_PER_USER=1000
EXPECTED_ROWS=$(( USER_COUNT * TWEETS_PER_USER ))
BENCH_URL="http://localhost:9092"
SEARCH_TERMS_CSV="testing/performance/jmeter/workloads/dbread-heavy-tweet-search/payload/search-terms.csv"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "${SCRIPT_DIR}/../../../../.." && pwd)"
cd "$REPO"
export JAVA_HOME=/opt/homebrew/Cellar/sdkman-cli/5.19.0/libexec/candidates/java/21.0.7-tem

# Shared cleanup contract — see testing/performance/lib/cleanup.sh.
source "$REPO/testing/performance/lib/cleanup.sh"
source "$REPO/testing/performance/lib/topology.sh"
if topology_uses_native_infra; then INFRA_TOPOLOGY="native-local"; else INFRA_TOPOLOGY="docker"; fi
cleanup_sweep_init

LOG_DIR="/tmp/${WORKLOAD//-/_}_${PROFILE}"
mkdir -p "$LOG_DIR"
exec > >(tee -a "$LOG_DIR/run.log") 2>&1

echo "[$(date)] ===== ${WORKLOAD} ${PROFILE} START ====="
echo "[$(date)] config: topology=$(topology_label) concurrencies=\"$CONCS\" runs=$RUNS warmup=$WARMUP main=$MAIN base-url=$BENCH_URL"
echo "[$(date)] payload: ${USER_COUNT} users × ${TWEETS_PER_USER} tweets-per-user (= ${EXPECTED_ROWS} rows)"

# Force a re-seed and sanity-check the DB shape. prepare.py writes
# tweet_service_db (TRUNCATE CASCADE then INSERT). The shape assertion reaches
# tweet_service_db via host psql on its published port (54322), identical
# whether infra is Docker-published or native-local.
seed_and_verify() {
  echo "[$(date)] forcing re-seed of tweets table (search-term-embedded content)..."
  python3 testing/performance/jmeter/workloads/dbread-heavy-tweet-search/prepare.py \
    --count "$USER_COUNT" --seed-tweets 1 --tweets-per-user "$TWEETS_PER_USER" 2>&1 | tail -10

  local ROWS
  ROWS=$(PGPASSWORD=postgres psql -h 127.0.0.1 -p 54322 -U postgres -d tweet_service_db -t -A \
    -c "SELECT count(*) FROM tweets;" 2>&1)
  echo "[$(date)] tweets rows=$ROWS (expected ${EXPECTED_ROWS})"
  if [[ "$ROWS" -ne "$EXPECTED_ROWS" ]]; then
    echo "[$(date)] ERROR: seed shape doesn't match expected ${EXPECTED_ROWS}; aborting" >&2
    return 1
  fi
}

# --- Pre-run: bring up infra, seed when the schema already exists ------------
topology_infra_up 2>&1 | tail -3

if ! topology_uses_native_infra; then
  seed_and_verify || exit 1
fi

# --- Per-stack runner --------------------------------------------------------
run_stack() {
  local stack="$1"
  # tweet-service is the SUT (BENCH_URL :9092); user-service serves the per-page author
  # enrichment (computeTweetsFromPage -> getUserSummaries batch POST) on EVERY search
  # response, so it must be up in ALL topologies — not just native-local for the seed schema.
  # No interaction-service fan-out on this endpoint (search enriches authors, not counts).
  local apps=(tweet-service user-service)
  echo "[$(date)] ----- $stack: bringing stack up (apps: ${apps[*]}) -----"
  topology_start_stack "$stack" "${apps[@]}"

  # Wait for the SUT (:9092) and user-service (:9091) — both serve the request path.
  local health_ports=(9092 9091)
  local port
  for port in "${health_ports[@]}"; do
    local up_after=0 i
    for i in $(seq 1 180); do
      if curl -fs "http://localhost:${port}/actuator/health" 2>/dev/null | grep -q UP; then
        up_after=$i
        break
      fi
      sleep 1
    done
    if (( up_after == 0 )); then
      echo "[$(date)] ERROR: $stack svc:${port} did not report UP within 180s ($(topology_label))" >&2
      topology_stop_stack "$stack" "${apps[@]}"
      return 1
    fi
    echo "[$(date)] $stack svc:${port} UP after ${up_after}s"
  done

  if topology_uses_native_infra; then
    # native DBs started empty; the apps' Flyway just created the schema at boot. Seed now.
    echo "[$(date)] $stack: native infra — seeding fixtures after boot..."
    if ! seed_and_verify; then
      topology_stop_stack "$stack" "${apps[@]}"
      return 1
    fi
  fi

  # Cold cache per stack — clear the prior stack's (incompatibly-serialized)
  # Redis entries before this stack warms its own. See lib/cleanup.sh flush_cache.
  flush_cache

  echo "[$(date)] ----- $stack: launching JMeter ${PROFILE} -----"
  BENCHMARK_TOPOLOGY="$TOPOLOGY" BENCHMARK_INFRA_TOPOLOGY="$INFRA_TOPOLOGY" \
    ./testing/performance/jmeter/run_bench.sh --workload "$WORKLOAD" \
    --concurrencies "$CONCS" \
    --runs "$RUNS" --warmup "$WARMUP" --main "$MAIN" \
    --payload-count "$USER_COUNT" --seed-users 0 --seed-tweets 0 \
    --tweets-per-user "$TWEETS_PER_USER" \
    --search-terms-file "$SEARCH_TERMS_CSV" \
    --collect-resources 1 --base-url "$BENCH_URL"

  local status=$?

  local result_dir
  result_dir=$(find_latest_result_dir "$REPO" "jmeter" "$WORKLOAD")
  if [[ $status -eq 0 ]]; then
    cleanup_raw_samples "$result_dir"
  else
    remove_partial_result_dir "$result_dir"
  fi

  echo "[$(date)] ----- $stack: bench exit=$status, bringing stack down -----"
  topology_stop_stack "$stack" "${apps[@]}"
  return $status
}

ASYNC=0; REACTIVE=0
for stack in $ORDER; do
  run_stack "$stack"
  rc=$?
  case "$stack" in
    async)    ASYNC=$rc ;;
    reactive) REACTIVE=$rc ;;
  esac
done

echo "[$(date)] ===== ${WORKLOAD} ${PROFILE} COMPLETE (async=$ASYNC reactive=$REACTIVE) ====="
ls -dt "$REPO/testing-results/performance/jmeter/results_${WORKLOAD}_2026"* 2>/dev/null | head -2

# Teardown. native-local infra is ephemeral, so bringing it down wipes the DBs + redis +
# vault wholesale. The Docker topologies leave shared infra up between runs (their app-only
# down doesn't touch it); the next serial workload re-seeds whatever world it needs.
if topology_uses_native_infra; then
  echo "[$(date)] ===== ${WORKLOAD} ${PROFILE}: teardown (native infra down — wipes DBs/redis/vault) ====="
  topology_infra_down
fi

cleanup_sweep_finish
