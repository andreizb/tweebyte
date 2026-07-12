#!/usr/bin/env bash
# Bench runner for cacheread-following (k6) — topology-aware, host-side k6.
#
# TOPOLOGY (testing/performance/lib/topology.sh) selects how the SUT runs:
#   - local-app (default): infra (postgres, redis, vault) in Docker; user-service +
#     interaction-service as host JVMs via ./run.sh local. Canonical.
#   - native-local: apps as host JVMs AND infra host-native (deployment/local/local-infra.sh)
#     on the same published ports — no Docker at all. DBs start empty, so the schema is
#     created by app Flyway at boot and the cohort is seeded AFTER boot, per stack.
#   - all-docker: legacy; apps in containers too.
# In every topology k6 is a host-side binary (ulimit -n 65536 to survive 1000 VUs on
# macOS) driving interaction-service (the SUT) on its published port :9093, and the
# seeder/hygiene reach Postgres/Redis via host psql/redis-cli on the published ports.
#
# Usage:
#   ./run.sh canonical   — 14 cells × 2 runs × (60s warmup + 120s steady) ~= 1.9 hr/stack
#                          Full sweep; the reportable default. Report mean + min/max RANGE
#                          (two-run range, NOT inferential CI95). If a headline cell's two runs
#                          differ by >5%, or the effect size is itself ~5%, rerun confidence
#                          before claiming a winner.
#   ./run.sh confidence  — 14 cells × 5 runs × (60s warmup + 180s steady) ~= 4.7 hr/stack
#                          Tight CI95 for disputed/close results; rarely run.
#
# Seed shape (1M follow rows): 100,000 keys × 10 follows-per-key.
#
# Cache model — natural 60s caching (no artificial pre-warm): each stack's Redis is
# FLUSHALL'd cold, then k6's own warmup phase populates the caches the way production
# would. The first request per key MISSES -> getFollowing() reads the follow rows and
# fans out one getUserSummary per followed user; both the assembled following_cache blob
# and each user-summary are then cached at the default spring.cache.redis.time-to-live
# (60s), so the steady window is served from warm caches with periodic 60s re-fills.
# Direct to plaintext Redis (INTERACTION_CACHE_HOST=redis:6379, the benchmark default) —
# the measured path. The toxiproxy Redis proxy (26379) stays defined-and-idle for future
# fault-injection; export INTERACTION_CACHE_HOST=toxiproxy INTERACTION_CACHE_PORT=26379 to
# route through it. Keep follows-per-key=10 so the per-request fan-out and cached
# blob shape stay fixed.
#
# Data model — self-contained, consistent cohort (each seeder owns its world):
#   - seed: additively seeds the KEY_COUNT benchmark users into user_service_db
#     (real rows, deterministic bench_user_<seq> handles) AND the follows graph in
#     interaction_service_db. Real user rows mean a production-like run never hits
#     getFollowing()'s missing-user 404->500 footgun, and warm reads the REAL
#     username back from user_service_db (not a reconstructed handle).
#   - clean (after BOTH stacks): truncates users + follows and FLUSHALLs redis.
#     Workloads run serially, so the next one re-seeds whatever world it needs.
#
# Pre-run safety: re-seeds + asserts follows row count before launching.
set -u

PROFILE="${1:-}"
case "$PROFILE" in
  canonical)
    CONCS="1 5 10 15 20 25 50 75 100 200 400 600 800 1000"
    RUNS=2; WARMUP="60s"; DURATION="120s"
    ORDER="reactive async"
    ;;
  confidence)
    CONCS="1 5 10 15 20 25 50 75 100 200 400 600 800 1000"
    RUNS=5; WARMUP="60s"; DURATION="180s"
    ORDER="reactive async"
    ;;
  *)
    echo "Usage: $0 <canonical|confidence>" >&2
    exit 1
    ;;
esac

# Optional env overrides to narrow a profile to a focused cell/rep/order set without
# editing the profiles, e.g. CONCS_OVERRIDE="600 800 1000" ORDER_OVERRIDE="async reactive"
# keeps canonical's 5 reps + 60s/180s windows but only the high-conc cells.
CONCS="${CONCS_OVERRIDE:-$CONCS}"
RUNS="${RUNS_OVERRIDE:-$RUNS}"
ORDER="${ORDER_OVERRIDE:-$ORDER}"

WORKLOAD="cacheread-following"
# 1M follow rows = 100,000 keys × 10 follows/key (10,000 hot keys = 10%, hot ratio 0.9).
KEY_COUNT=100000
FOLLOWS_PER_KEY=10
HOT_COUNT=10000
HOT_RATIO="0.9"
BENCH_URL="http://localhost:9093"
EXPECTED_ROWS=$((KEY_COUNT * FOLLOWS_PER_KEY))

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "${SCRIPT_DIR}/../../../../.." && pwd)"
cd "$REPO"
export JAVA_HOME=/opt/homebrew/Cellar/sdkman-cli/5.19.0/libexec/candidates/java/21.0.7-tem
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-tweebyte}"

# Shared cleanup contract — see testing/performance/lib/cleanup.sh.
source "$REPO/testing/performance/lib/cleanup.sh"
source "$REPO/testing/performance/lib/topology.sh"
cleanup_sweep_init

LOG_DIR="/tmp/${WORKLOAD//-/_}_${PROFILE}"
mkdir -p "$LOG_DIR"
exec > >(tee -a "$LOG_DIR/run.log") 2>&1

echo "[$(date)] ===== ${WORKLOAD} ${PROFILE} START (topology=$(topology_label) + host-side k6) ====="
echo "[$(date)] config: topology=$(topology_label) concurrencies=\"$CONCS\" runs=$RUNS warmup=$WARMUP duration=$DURATION base-url=$BENCH_URL"
echo "[$(date)] payload: ${KEY_COUNT} keys × ${FOLLOWS_PER_KEY} follows-per-key (${EXPECTED_ROWS} follow rows)"

# Try to raise file-descriptor limit so local k6 survives conc=800+ on macOS.
if ulimit -n 65536 2>/dev/null; then
  echo "[$(date)] ulimit -n set to $(ulimit -n)"
else
  echo "[$(date)] WARN: could not raise ulimit -n above $(ulimit -n); local k6 may flake above conc=400-800"
fi

# Effective infra-topology tag for result metadata (native vs Docker-published).
if topology_uses_native_infra; then INFRA_TOPOLOGY="native-local"; else INFRA_TOPOLOGY="docker"; fi

# Seed the self-contained cohort (user rows + follows graph) and assert the follows row
# count. DATA only — the schema is Flyway-owned, created by the apps at boot. Reaches
# Postgres via host psql on the published interaction-DB port (54323), identical whether
# infra is Docker-published or native-local.
seed_and_verify() {
  echo "[$(date)] seeding cohort + follows graph (data only; schema is Flyway-owned)..."
  python3 testing/performance/k6/workloads/cacheread-following/prepare.py \
    --key-count "$KEY_COUNT" --follows-per-key "$FOLLOWS_PER_KEY" \
    --keys-out "testing/performance/k6/workloads/cacheread-following/payload/n${KEY_COUNT}_k${FOLLOWS_PER_KEY}/keys.txt" 2>&1 | tail -10
  local rows
  rows=$(PGPASSWORD=postgres psql -h 127.0.0.1 -p 54323 -U postgres -d interaction_service_db -t -A \
    -c "SELECT count(*) FROM follows;" 2>&1)
  echo "[$(date)] follows table rows = $rows"
  if [[ ! "$rows" =~ ^[0-9]+$ ]] || [[ "$rows" -lt "$EXPECTED_ROWS" ]]; then
    echo "[$(date)] ERROR: seed didn't produce expected ${EXPECTED_ROWS} rows" >&2
    return 1
  fi
}

# --- Pre-run: bring up infra (topology-aware), then seed when the schema already exists.
# topology_infra_up brings up Docker infra (./run.sh runtime up infra) for local-app/
# all-docker, or host-native infra (deployment/local/local-infra.sh) for native-local. Docker DB
# volumes persist the Flyway-created schema across runs, so those topologies seed now,
# before the apps boot. native-local creates empty DBs every time, so its schema does not
# exist until an app migrates it — it seeds inside run_stack, after boot.
topology_infra_up 2>&1 | tail -3

if ! topology_uses_native_infra; then
  seed_and_verify || exit 1
fi

# --- Per-stack runner ---------------------------------------------------------
run_stack() {
  local stack="$1"
  echo "[$(date)] ----- $stack: bringing stack up ($(topology_label)) -----"
  topology_start_stack "$stack" user-service interaction-service

  # interaction-service is the SUT; wait for it to report healthy on its published
  # port (:9093) before driving load at it, regardless of where it runs.
  local up_after=0
  for i in $(seq 1 180); do
    if curl -fs "${BENCH_URL}/actuator/health" 2>/dev/null | grep -q UP; then
      up_after=$i
      break
    fi
    sleep 1
  done
  if (( up_after == 0 )); then
    echo "[$(date)] ERROR: $stack interaction-service did not report UP within 180s ($(topology_label)); last 40 lines of log:" >&2
    if [[ "$(topology_label)" == "local-app" || "$(topology_label)" == "native-local" ]]; then
      tail -40 "$REPO/testing-results/runtime/local-app/${stack}/interaction-service/app.log" 2>&1 >&2 || true
    else
      docker compose --project-name "${COMPOSE_PROJECT_NAME}" \
        --project-directory "${REPO}" \
        -f deployment/docker-compose/infrastructure.yml \
        -f "deployment/docker-compose/${stack}.yml" \
        logs --tail 40 interaction-service 2>&1 | tail -40 >&2 || true
    fi
    topology_stop_stack "$stack" user-service interaction-service
    return 1
  fi
  echo "[$(date)] $stack interaction-service UP after ${up_after}s on ${BENCH_URL}"

  if topology_uses_native_infra; then
    # native DBs started empty; the apps' Flyway just created the schema at boot. Wait for
    # user-service too (its users table must exist before seed_users), then seed this stack.
    # Re-seeding per stack is fine: the seed is deterministic, so both stacks measure the
    # identical cohort + follows graph.
    local us_up=0 j
    for j in $(seq 1 180); do
      if curl -fs "http://localhost:9091/actuator/health" 2>/dev/null | grep -q UP; then us_up=$j; break; fi
      sleep 1
    done
    if (( us_up == 0 )); then
      echo "[$(date)] ERROR: $stack user-service did not report UP within 180s (native-local); cannot seed" >&2
      topology_stop_stack "$stack" user-service interaction-service
      return 1
    fi
    echo "[$(date)] $stack user-service UP after ${us_up}s (schema migrated); seeding cohort..."
    if ! seed_and_verify; then
      topology_stop_stack "$stack" user-service interaction-service
      return 1
    fi
  fi

  redis-cli -h 127.0.0.1 -p 63790 FLUSHALL >/dev/null 2>&1 || true
  echo "[$(date)] redis FLUSHALL done (host redis-cli :63790)"

  # Natural 60s caching: no artificial pre-warm. The cache starts cold (FLUSHALL above)
  # and k6's warmup phase populates it the production way -- the first hit on each key
  # misses, getFollowing() fans out one getUserSummary per followed user, and both the
  # following_cache blob and each user-summary land in Redis at the default 60s TTL.
  # By the steady window the working set is warm; entries re-fill every 60s. The followed
  # users are real seeded cohort rows, so the fan-out resolves (no 404). Redis is infra
  # and persists across stacks; the per-stack FLUSHALL and final teardown wipe the keys.
  echo "[$(date)] ----- $stack: cold cache; k6 warmup populates it at 60s TTL (no pre-warm) -----"

  echo "[$(date)] ----- $stack: launching k6 ${PROFILE} (host-side k6 → :9093) -----"
  BENCHMARK_TOPOLOGY="$TOPOLOGY" BENCHMARK_INFRA_TOPOLOGY="$INFRA_TOPOLOGY" \
    ./testing/performance/k6/run_bench.sh --workload "$WORKLOAD" \
    --mode local \
    --base-url "$BENCH_URL" \
    --path-prefix /follows \
    --concurrencies "$CONCS" \
    --runs "$RUNS" --warmup "$WARMUP" --duration "$DURATION" \
    --hot-ratio "$HOT_RATIO" --hot-count "$HOT_COUNT" \
    --key-count "$KEY_COUNT" --follows-per-key "$FOLLOWS_PER_KEY" \
    --collect-resources 1 \
    --auto-prepare 0

  local status=$?

  local result_dir
  result_dir=$(find_latest_result_dir "$REPO" "k6" "$WORKLOAD")
  if [[ $status -eq 0 ]]; then
    cleanup_raw_samples "$result_dir"
  else
    remove_partial_result_dir "$result_dir"
  fi

  echo "[$(date)] ----- $stack: bench exit=$status, bringing stack down -----"
  topology_stop_stack "$stack" user-service interaction-service
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
ls -dt "$REPO/testing-results/performance/k6/results_${WORKLOAD//-/_}_2026"* 2>/dev/null | head -2

# Teardown. native-local infra is ephemeral, so bringing it down wipes the DBs + redis +
# vault wholesale — nothing to clean. The Docker topologies leave shared infra up (their
# 'local down'/'runtime down' only stops the app services), so the seeder truncates the
# tables it populated (users + follows) and flushes redis instead.
if topology_uses_native_infra; then
  echo "[$(date)] ===== ${WORKLOAD} ${PROFILE}: teardown (native infra down — wipes DBs/redis/vault) ====="
  topology_infra_down
else
  echo "[$(date)] ===== ${WORKLOAD} ${PROFILE}: teardown (truncate users+follows, flush redis) ====="
  python3 testing/performance/k6/workloads/cacheread-following/prepare.py clean \
    --key-count "$KEY_COUNT" --follows-per-key "$FOLLOWS_PER_KEY" 2>&1 | tail -5
fi

cleanup_sweep_finish
