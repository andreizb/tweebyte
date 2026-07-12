#!/usr/bin/env bash
# Bench runner for dbwrite-heavy-tweet-update.
#
# PUT /tweets/{userId}/{tweetId} with a payload that carries hashtag tokens,
# exercising the relation-maintenance code path on both stacks:
#   - async: TweetUpdateService loads tweet via findByIdAndUserId@EntityGraph, mutates
#     hashtags collection, Hibernate dirty-checking emits the DELETE/INSERT
#     on tweet_hashtag at flush time. @Transactional on the helper service
#     keeps the entity-load + flush in one TX.
#   - reactive: one eager-fetch join loads the tweet + its hashtag/mention
#     relations (the R2DBC stand-in for @EntityGraph), then the reconcile diffs
#     existing-vs-desired and emits a single multi-row DELETE ... IN (...) for
#     stale links and a single multi-row INSERT ... VALUES (...) for new ones.
#
# Mentions: app code supports @ updates, but the benchmark payload contains
# only # tokens to keep the workload tweet-service-local (no user-service
# fanout for username→userId lookups).
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
# Pre-cell reset rebuilds tweet content + tweet_hashtag links to the old-form
# state so every cell starts from the same baseline. JMeter recycles CSV rows
# during a 180 s cell, but precell_reset.sh runs only between cells; every PUT
# inside a cell does real relation maintenance regardless of CSV cursor.
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

# Optional env overrides to narrow a profile for focused repair slices without editing
# the canonical profile itself, e.g. CONCS_OVERRIDE="50 75 100" ORDER_OVERRIDE=async.
CONCS="${CONCS_OVERRIDE:-$CONCS}"
RUNS="${RUNS_OVERRIDE:-$RUNS}"
WARMUP="${WARMUP_OVERRIDE:-$WARMUP}"
MAIN="${MAIN_OVERRIDE:-$MAIN}"
ORDER="${ORDER_OVERRIDE:-$ORDER}"

WORKLOAD="dbwrite-heavy-tweet-update"
BENCH_URL="http://localhost:9092"
# Default tweet count = the shared 1M-row fixture, which also serves as the
# anti-recycle guard: the first pass through the JMeter CSV (RPS × cell_duration)
# must NOT complete before the cell ends. A 240s cell (60s warm + 180s steady)
# at 1M rows tolerates up to ~4166 rps before the CSV recycles. This workload's
# per-PUT content UPDATE + tweet_hashtag DELETE+INSERT relation maintenance
# saturates well under that at the benchmark DB pool (min-idle 10 / max-size
# 200), so it stays under the 1M margin. Once
# the CSV recycles, repeated new-to-new PUTs let
# Hibernate skip the content UPDATE (dirty-check on equal value), measuring a
# cheaper workload than intended. precell_reset restores old-form between cells;
# the row count keeps every PUT in real old-to-new transition mode within a cell.
# Watch-item: confirm no CSV recycle once the post-reset smoke lands; bump if a
# cell ever sustains >4166 rps.
TWEET_COUNT="${TWEET_COUNT:-1000000}"
POOL_SIZE="${POOL_SIZE:-1024}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "${SCRIPT_DIR}/../../../../.." && pwd)"
cd "$REPO"
export JAVA_HOME=/opt/homebrew/Cellar/sdkman-cli/5.19.0/libexec/candidates/java/21.0.7-tem
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-tweebyte}"
export TWEET_COUNT POOL_SIZE

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
echo "[$(date)] payload: tweets=${TWEET_COUNT} hashtag-pool=${POOL_SIZE}"

CSV="testing/performance/jmeter/workloads/dbwrite-heavy-tweet-update/payload/tweet-updates.csv"

# Seed the fixtures and assert the row counts. prepare.py writes user_service_db (users)
# AND tweet_service_db (hashtag dictionary, tweets, tweet_hashtag links); the row-count
# checks reach tweet_service_db via host psql on its published port (54322), identical
# whether infra is Docker-published or native-local.
seed_and_verify() {
  echo "[$(date)] seeding fixtures (users, hashtag dictionary, tweets, tweet_hashtag links)..."
  python3 testing/performance/jmeter/workloads/dbwrite-heavy-tweet-update/prepare.py \
    --count "$TWEET_COUNT" --pool-size "$POOL_SIZE" --seed-tweets 1 2>&1 | tail -10

  local ROWS LINK_ROWS TAG_ROWS
  ROWS=$(PGPASSWORD=postgres psql -h 127.0.0.1 -p 54322 -U postgres -d tweet_service_db -t -A \
    -c "SELECT count(*) FROM tweets;" 2>&1 | tr -d '[:space:]')
  LINK_ROWS=$(PGPASSWORD=postgres psql -h 127.0.0.1 -p 54322 -U postgres -d tweet_service_db -t -A \
    -c "SELECT count(*) FROM tweet_hashtag;" 2>&1 | tr -d '[:space:]')
  TAG_ROWS=$(PGPASSWORD=postgres psql -h 127.0.0.1 -p 54322 -U postgres -d tweet_service_db -t -A \
    -c "SELECT count(*) FROM hashtags;" 2>&1 | tr -d '[:space:]')
  echo "[$(date)] tweets=$ROWS  tweet_hashtag=$LINK_ROWS  hashtags=$TAG_ROWS"
  if [[ "$ROWS" -lt "$TWEET_COUNT" ]] || [[ "$LINK_ROWS" -lt $((TWEET_COUNT * 2)) ]]; then
    echo "[$(date)] ERROR: seed produced fewer rows than expected; aborting" >&2
    return 1
  fi
}

# --- Pre-run: bring up infra, seed when the schema already exists ------------
# Docker DB volumes persist the Flyway schema across runs, so those topologies seed now,
# before the apps boot. native-local creates empty DBs every time — its schema does not
# exist until each owning app's Flyway migrates it at boot, so it seeds inside run_stack,
# after boot.
topology_infra_up 2>&1 | tail -3

if ! topology_uses_native_infra; then
  seed_and_verify || exit 1
fi

# --- Per-stack runner --------------------------------------------------------
run_stack() {
  local stack="$1"
  # tweet-service is the SUT (BENCH_URL :9092). The seed also writes user_service_db, so
  # under native-local (empty DBs, schema only after each app's Flyway boots) user-service
  # must be up too before we can seed — add it to the boot/teardown set in that topology.
  local apps=(tweet-service)
  topology_uses_native_infra && apps+=(user-service)
  echo "[$(date)] ----- $stack: bringing stack up (apps: ${apps[*]}) -----"
  topology_start_stack "$stack" "${apps[@]}"

  # Wait for the SUT (:9092) always, plus user-service (:9091) when native (so its
  # users-table schema exists before seed_and_verify).
  local health_ports=(9092)
  topology_uses_native_infra && health_ports+=(9091)
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
    --payload-count "$TWEET_COUNT" --seed-users 0 --seed-tweets 0 \
    --tweet-updates-file "$CSV" \
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
