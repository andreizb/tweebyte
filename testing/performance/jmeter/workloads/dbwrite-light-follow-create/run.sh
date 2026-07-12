#!/usr/bin/env bash
# Bench runner for dbwrite-light-follow-create.
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
# Locked methodology (as of 2026-05-12 - see cookbook "Successful fixes"):
#   - Mode-1: JMeter hits interaction-service direct on
#     http://localhost:9093, no app-side toxiproxy proxy in path.
#   - Cache bypass: FollowService.follow() reads fresh privacy via
#     userService.fetchUserSummary(...) — the cached @Cacheable path is
#     correct only for read endpoints; write paths must read live state.
#   - 204-no-content fix: follow() returns Void on both stacks; no FollowDto
#     allocation per request.
#   - DB pool min-idle 10 / max-size 200 (auto-grow, 3600s acquire) on both
#     stacks. Reactive interaction-service enables the colocation fix
#     (app.r2dbc.disable-colocation=true) to fan connection work across all
#     event-loop workers; the r2dbcPoolAcquisitionScheduler bean is gated off
#     (no benchmark sets app.r2dbc.pool.acquisition-scheduler.enabled).
#   - Reactive interaction-service also exposes an optional, benchmark-gated
#     WebClient event-loop split (`app.http.user-client.loop.enabled`, off by
#     default) as a follow-create transport probe.
#   - Per-cell reset truncates `follows` and drops stale read-path / duplicate
#     write indexes; no Redis `users::*` flush is needed because the
#     follow-create write path uses the uncached fetchUserSummary path.
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

# Optional env overrides to narrow a profile for a quick validation cell without editing it,
# e.g. CONCS_OVERRIDE=10 WARMUP_OVERRIDE=5 MAIN_OVERRIDE=15 ORDER_OVERRIDE=async.
CONCS="${CONCS_OVERRIDE:-$CONCS}"
RUNS="${RUNS_OVERRIDE:-$RUNS}"
WARMUP="${WARMUP_OVERRIDE:-$WARMUP}"
MAIN="${MAIN_OVERRIDE:-$MAIN}"
ORDER="${ORDER_OVERRIDE:-$ORDER}"

WORKLOAD="dbwrite-light-follow-create"
BENCH_URL="http://localhost:9093"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "${SCRIPT_DIR}/../../../../.." && pwd)"
cd "$REPO"
export JAVA_HOME=/opt/homebrew/Cellar/sdkman-cli/5.19.0/libexec/candidates/java/21.0.7-tem

# Shared cleanup contract — see testing/performance/lib/cleanup.sh.
# Sets the abort trap (INT/TERM → rm -rf partial result dirs) and exposes the
# helpers that run_stack uses after each bench: cleanup_raw_samples (delete
# .jtl on success), remove_partial_result_dir (rm -rf on per-stack failure).
# Operator can preserve raw samples with KEEP_RAW=1 ./run.sh ...
source "$REPO/testing/performance/lib/cleanup.sh"
source "$REPO/testing/performance/lib/topology.sh"
if topology_uses_native_infra; then INFRA_TOPOLOGY="native-local"; else INFRA_TOPOLOGY="docker"; fi
cleanup_sweep_init

LOG_DIR="/tmp/${WORKLOAD//-/_}_${PROFILE}"
mkdir -p "$LOG_DIR"
exec > >(tee -a "$LOG_DIR/run.log") 2>&1

echo "[$(date)] ===== ${WORKLOAD} ${PROFILE} START ====="
echo "[$(date)] config: topology=$(topology_label) concurrencies=\"$CONCS\" runs=$RUNS warmup=$WARMUP main=$MAIN base-url=$BENCH_URL"

# Seed the user cohort the follows reference. createFollow fetches the followed user's
# summary from user-service, so those users must exist. Unlike the other jmeter workloads
# this runner had no seed step (it assumed a pre-seeded cohort); in the self-contained
# serial flow each workload truncates its own data, so seed here. prepare.py talks to
# user-service-db directly via the published port — this also regenerates the payload CSVs.
seed_and_verify() {
  for _ in $(seq 1 60); do pg_isready -h 127.0.0.1 -p 54321 -U postgres >/dev/null 2>&1 && break; sleep 1; done
  python3 testing/performance/jmeter/workloads/dbwrite-light-follow-create/prepare.py --count 1000000 --seed-users 1 2>&1 | tail -5
}

# Bring up infra (topology-aware). Docker DB volumes persist the Flyway schema across runs,
# so those topologies seed now, before the apps boot. native-local creates empty DBs every
# time, so its schema does not exist until an app's Flyway migrates it at boot — it seeds
# inside run_stack, after boot.
echo "[$(date)] ----- pre-run: bring up infra -----"
topology_infra_up 2>&1 | tail -3

if ! topology_uses_native_infra; then
  echo "[$(date)] ----- pre-run: seed user cohort -----"
  seed_and_verify || exit 1
fi

run_stack() {
  local stack="$1"
  local apps=(user-service interaction-service)
  echo "[$(date)] ----- $stack: bringing stack up -----"
  topology_start_stack "$stack" "${apps[@]}"

  # follow-create needs both interaction-service (POST /follows) AND
  # user-service (cross-service GET /users/summary on cache-miss / fetch path).
  # The seed writes only user_service_db (the cohort), but both apps own DBs whose
  # Flyway schema must exist under native-local before we can seed, so wait for both.
  for port in 9091 9093; do
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
    # native DBs started empty; the apps' Flyway just created the schema at boot.
    # Seed the cohort now (deterministic, so both stacks measure the identical world).
    echo "[$(date)] $stack: native infra — seeding cohort after boot..."
    if ! seed_and_verify; then
      topology_stop_stack "$stack" "${apps[@]}"
      return 1
    fi
  fi

  # Boot receipt — captures image digest, java cmdline, container env, and
  # application*.properties for every service. Proves what is actually running
  # before the bench starts. If a run's numbers ever surprise you, diff two
  # receipts to find the delta the working notes might have missed.
  local receipt_dir="$REPO/testing-results/boot-receipts/${WORKLOAD}_${PROFILE}_${stack}_$(date +%Y%m%d_%H%M%S)"
  topology_boot_receipt "$stack" benchmark "$receipt_dir" "${apps[@]}" 2>&1 | tail -20

  # Cold cache per stack — clear the prior stack's (incompatibly-serialized)
  # Redis entries before this stack warms its own. See lib/cleanup.sh flush_cache.
  flush_cache

  echo "[$(date)] ----- $stack: launching JMeter ${PROFILE} -----"
  BENCHMARK_TOPOLOGY="$TOPOLOGY" BENCHMARK_INFRA_TOPOLOGY="$INFRA_TOPOLOGY" \
    ./testing/performance/jmeter/run_bench.sh --workload "$WORKLOAD" \
    --concurrencies "$CONCS" \
    --runs "$RUNS" --warmup "$WARMUP" --main "$MAIN" \
    --payload-count 1000000 --seed-users 0 --seed-tweets 0 \
    --user-ids-file testing/performance/jmeter/workloads/dbwrite-light-follow-create/payload/cohort-user-ids.csv \
    --collect-resources 1 --base-url "$BENCH_URL"

  local status=$?

  # Find the result dir run_bench just produced. On success: aggregate is
  # already in cells.csv → drop the raw .jtl bulk (~1 GB/cell at conc=1000).
  # On failure: the partial dir is junk → remove it entirely.
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

# Sweep finished — drop the marker so any post-completion INT/TERM (e.g.,
# during the operator's terminal-close after a long canonical) doesn't trip
# the abort trap and delete the cleaned-up result dirs.
cleanup_sweep_finish
