#!/usr/bin/env bash
# Bench runner for dbread-fanout-user-profile. Replaces the prior smoke.sh + canonical.sh
# pair; same script with a profile arg.
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
# Methodology shared by all profiles:
#   - No client-side pacing: the closed-loop workload runs at maximum
#     pressure with no residency added between samples.
#   - JMeter hits user-service direct on http://localhost:9091. No
#     app-side toxiproxy proxy in path.
#   - benchmark.properties carry principled symmetric knobs on both stacks: DB pool
#     min-idle 10 / max-size 200 (auto-grow, 3600s acquire), downstream HTTP pool
#     1000 per route / 2000 total / -1 pending-acquire (via @Value), bounded async
#     executors (io 200, http-client 2000), and on reactive app.r2dbc.disable-colocation
#     =true. Container mem_limit 8g; Postgres max_connections=300 stays above the pool.
#   - JVM `-Xmx4g` only (compose); no `-Xms`, no AlwaysPreTouch, no virtual threads.
#
# Order: reactive first on both modes (saves a stack switch when reactive is
# already up from a prior run). Caffeinate for unattended overnight runs.
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

WORKLOAD="dbread-fanout-user-profile"
BENCH_URL="http://localhost:9091"
# The profile read fans out to tweet-service (9092) and interaction-service
# (9093); wait on all three before driving load, not just user-service.
HEALTH_URLS="http://localhost:9091/actuator/health http://localhost:9092/actuator/health http://localhost:9093/actuator/health"
# 1M cohort with the minimal-fanout seed: 1 tweet/user + a 1-follower/1-following
# ring (prepare.py --seed-users 1). Asserted below so the profile fan-out always
# hits non-empty downstream data instead of whatever a prior run left behind.
USER_COUNT=1000000

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
echo "[$(date)] payload: ${USER_COUNT} users x (1 tweet + 1 follower + 1 following)"

# --- Pre-run: bring up infra (topology-aware), then seed the cohort + fan-out data.
# prepare.py --seed-users 1 creates the user cohort, one tweet per user, and a follow
# ring (every user gets exactly one following and one follower). Without this the profile
# fan-out reads empty tweet/follow tables and degenerates to a single findById. The schema
# is Flyway-owned (created by each app at boot): Docker DB volumes persist it so those
# topologies seed before boot; native-local has empty DBs every run, so it seeds inside
# run_stack after the three apps migrate their schemas. Verifies use host psql on the
# published ports (54321/54322/54323), identical under Docker-published or native infra.
seed_and_verify() {
  echo "[$(date)] seeding cohort + downstream fan-out data (users, 1 tweet/user, follow ring)..."
  python3 testing/performance/jmeter/workloads/dbread-fanout-user-profile/prepare.py \
    --count "$USER_COUNT" --seed-users 1 2>&1 | tail -10

  local tweet_rows follow_rows users
  tweet_rows=$(PGPASSWORD=postgres psql -h 127.0.0.1 -p 54322 -U postgres -d tweet_service_db -t -A \
    -c "SELECT count(*) FROM tweets;" 2>&1 | tr -d '[:space:]')
  follow_rows=$(PGPASSWORD=postgres psql -h 127.0.0.1 -p 54323 -U postgres -d interaction_service_db -t -A \
    -c "SELECT count(*) FROM follows;" 2>&1 | tr -d '[:space:]')
  users=$(PGPASSWORD=postgres psql -h 127.0.0.1 -p 54321 -U postgres -d user_service_db -t -A \
    -c "SELECT count(*) FROM users;" 2>&1 | tr -d '[:space:]')
  echo "[$(date)] users=$users  tweets=$tweet_rows  follows=$follow_rows (expected ${USER_COUNT} each)"
  if [[ "$users" -lt "$USER_COUNT" ]] || [[ "$tweet_rows" -lt "$USER_COUNT" ]] || [[ "$follow_rows" -lt "$USER_COUNT" ]]; then
    echo "[$(date)] ERROR: seed produced fewer rows than expected ${USER_COUNT}" >&2
    return 1
  fi
}

topology_infra_up 2>&1 | tail -3

if ! topology_uses_native_infra; then
  seed_and_verify || exit 1
fi

run_stack() {
  local stack="$1"
  echo "[$(date)] ----- $stack: bringing stack up -----"
  topology_start_stack "$stack" user-service tweet-service interaction-service
  local up=0
  for i in $(seq 1 180); do
    local all_up=1
    for url in $HEALTH_URLS; do
      curl -fs "$url" 2>/dev/null | grep -q UP || { all_up=0; break; }
    done
    if [[ "$all_up" -eq 1 ]]; then up=$i; break; fi
    sleep 1
  done
  if (( up == 0 )); then
    echo "[$(date)] ERROR: $stack user/tweet/interaction services did not all report UP within 180s" >&2
    topology_stop_stack "$stack" user-service tweet-service interaction-service
    return 1
  fi
  echo "[$(date)] $stack user/tweet/interaction services UP after ${up}s"

  if topology_uses_native_infra; then
    # native DBs started empty; the three apps just created their schemas via Flyway — seed now.
    if ! seed_and_verify; then
      topology_stop_stack "$stack" user-service tweet-service interaction-service
      return 1
    fi
  fi

  # Cold cache per stack — clear the prior stack's (incompatibly-serialized)
  # Redis entries before this stack warms its own. See lib/cleanup.sh flush_cache.
  flush_cache
  # Every seeded top reply is authored by the same benchmark companion. Install
  # that user's interaction-service summary after FLUSHALL with no expiry, so the
  # measured profile graph never re-enters user-service merely to resolve the
  # author name. The bare JSON is shared by both stacks.
  python3 testing/performance/jmeter/workloads/dbread-fanout-user-profile/prepare.py \
    --seed-companion-cache-only

  echo "[$(date)] ----- $stack: launching JMeter ${PROFILE} -----"
  BENCHMARK_TOPOLOGY="$TOPOLOGY" BENCHMARK_INFRA_TOPOLOGY="$INFRA_TOPOLOGY" \
    ./testing/performance/jmeter/run_bench.sh --workload "$WORKLOAD" \
    --concurrencies "$CONCS" \
    --runs "$RUNS" --warmup "$WARMUP" --main "$MAIN" \
    --payload-count "$USER_COUNT" --seed-users 0 --seed-tweets 0 \
    --user-ids-file testing/performance/jmeter/workloads/dbread-fanout-user-profile/payload/user-ids.csv \
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
  topology_stop_stack "$stack" user-service tweet-service interaction-service
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

# native-local infra is ephemeral — bring it down to wipe DBs/redis/vault wholesale.
# (Docker topologies leave shared infra up; user-profile relies on the next workload's
# seeder TRUNCATE to reset its data, so there is no per-run clean step here.)
if topology_uses_native_infra; then
  echo "[$(date)] ===== ${WORKLOAD} ${PROFILE}: teardown (native infra down — wipes DBs/redis/vault) ====="
  topology_infra_down
fi

cleanup_sweep_finish
