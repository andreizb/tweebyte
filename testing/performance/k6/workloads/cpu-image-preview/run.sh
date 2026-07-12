#!/usr/bin/env bash
# Bench runner for cpu-image-preview (k6, CPU-bound).
#
# Usage:
#   ./run.sh canonical   - 14 cells x 2 runs x (60s warmup + 120s steady) ~= 1.9 hr/stack
#                          Full sweep; the reportable default. Report mean + min/max RANGE
#                          (two-run range, NOT inferential CI95). If a headline cell's two runs
#                          differ by >5%, or the effect size is itself ~5%, rerun confidence
#                          before claiming a winner.
#   ./run.sh confidence  - 14 cells x 5 runs x (60s warmup + 180s steady) ~= 4.7 hr/stack
#                          Tight CI95 for disputed/close results; rarely run.
set -u

PROFILE="${1:-}"
case "$PROFILE" in
  canonical)
    # Full 14-cell grid for cross-task comparison.
    # CPU-bound throughput plateaus past ~50 users so anything above conc=100
    # is "plateau confirmation" rather than scaling discovery, but we keep the
    # grid identical to the other canonicals (01/02/03) for methodology symmetry.
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

WORKLOAD="cpu-image-preview"
# The CPU pipeline (Gaussian blur + Sobel + resize + JPEG) and the
# POST /media/{srcId}/preview endpoint are served by user-service (9091); the
# resource collector scrapes ${BENCH_URL}/actuator/prometheus, so this is also the
# SUT memory/CPU is sampled from.
BENCH_URL="http://localhost:9091"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "${SCRIPT_DIR}/../../../../.." && pwd)"
cd "$REPO"
export JAVA_HOME=/opt/homebrew/Cellar/sdkman-cli/5.19.0/libexec/candidates/java/21.0.7-tem

# Shared cleanup contract — see testing/performance/lib/cleanup.sh.
source "$REPO/testing/performance/lib/cleanup.sh"
source "$REPO/testing/performance/lib/topology.sh"
cleanup_sweep_init

LOG_DIR="/tmp/${WORKLOAD//-/_}_${PROFILE}"
mkdir -p "$LOG_DIR"
exec > >(tee -a "$LOG_DIR/run.log") 2>&1

echo "[$(date)] ===== ${WORKLOAD} ${PROFILE} START ====="
echo "[$(date)] config: topology=$(topology_label) concurrencies=\"$CONCS\" runs=$RUNS warmup=$WARMUP duration=$DURATION base-url=$BENCH_URL"

# Effective infra-topology tag for result metadata (native vs Docker-published).
if topology_uses_native_infra; then INFRA_TOPOLOGY="native-local"; else INFRA_TOPOLOGY="docker"; fi

# Seed the ONE Lena source original (DATA only — the schema is Flyway-owned, created by
# user-service at boot). Reaches Postgres via host psql on the published user-DB port,
# identical whether infra is Docker-published or native-local.
seed_payload() {
  echo "[$(date)] seeding cpu-image-preview source original (Lena 256x256; schema owned by user-service Flyway)..."
  python3 testing/performance/k6/workloads/cpu-image-preview/prepare.py seed 2>&1 | tail -10
}

# --- Pre-run: bring up infra (topology-aware), then seed when the schema already exists.
# media_assets + its PK/unique/FK are owned by Flyway and applied by user-service at boot
# (V1__baseline.sql). topology_infra_up brings up Docker infra (runtime up infra) for
# local-app/all-docker, or host-native infra (deployment/local/local-infra.sh) for native-local.
# Docker DB volumes persist the Flyway-created schema across runs, so those topologies seed
# now, before the apps boot. native-local creates empty DBs every time, so its schema does
# not exist until user-service migrates it — it seeds inside run_stack, after boot.
topology_infra_up 2>&1 | tail -3

if ! topology_uses_native_infra; then seed_payload; fi

run_stack() {
  local stack="$1"
  echo "[$(date)] ----- $stack: bringing stack up -----"
  topology_start_stack "$stack" user-service
  local up_after=0
  for i in $(seq 1 180); do
    if curl -fs "${BENCH_URL}/actuator/health" 2>/dev/null | grep -q UP; then
      up_after=$i
      echo "[$(date)] $stack service UP after ${i}s"
      break
    fi
    sleep 1
  done
  if (( up_after == 0 )); then
    echo "[$(date)] ERROR: $stack user-service did not report UP within 180s ($(topology_label))" >&2
    topology_stop_stack "$stack" user-service
    return 1
  fi

  if topology_uses_native_infra; then
    # native DBs started empty; user-service's Flyway just created the media_assets schema
    # at boot (the health-wait above already confirmed user-service is up). Seed this stack
    # now. Re-seeding per stack is fine: the seed is deterministic, so both stacks measure
    # the identical source original.
    seed_payload
  fi

  # The payload id file only exists post-seed, which for native happens inside run_stack.
  local SRC_ID
  SRC_ID=$(cat testing/performance/k6/workloads/cpu-image-preview/payload/source_media_id.txt)
  echo "[$(date)] source_media_id=$SRC_ID"

  # The CPU pipeline runs on every request (it is the measured work), but the source
  # read and the content-addressed insert happen once. Warm per stack: one
  # POST /media/{srcId}/preview caches the source (resolveAsset DB-miss → cache) and
  # inserts+caches the derived preview (store DB-miss → bcrypt → save). Without it the
  # first k6 wave — up to conc=1000 — would herd: N concurrent source reads, N bcrypt
  # encodes, and N racing inserts on the uq_media_assets_checksum UNIQUE. The cache is
  # in-JVM and died with the prior stack's container, so this is per stack.
  echo "[$(date)] ----- $stack: warming source + preview (per stack) -----"
  python3 testing/performance/k6/workloads/cpu-image-preview/prepare.py warm --base-url "$BENCH_URL" 2>&1 | tail -3

  echo "[$(date)] ----- $stack: launching k6 ${PROFILE} -----"
  BENCHMARK_TOPOLOGY="$TOPOLOGY" BENCHMARK_INFRA_TOPOLOGY="$INFRA_TOPOLOGY" ./testing/performance/k6/run_bench.sh --workload "$WORKLOAD" \
    --base-url "$BENCH_URL" \
    --api-path "/media/$SRC_ID/preview" \
    --concurrencies "$CONCS" \
    --runs "$RUNS" --warmup "$WARMUP" --duration "$DURATION" \
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
  topology_stop_stack "$stack" user-service
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
# 'local down'/'runtime down' only stops the app services), so this seeder owns its world —
# the seeded Lena original plus the one derived preview row the run's content-addressed
# insert created (every later identical POST is a pure-CPU cache hit) — and deletes both
# here. DELETE (not TRUNCATE) honors the profile_picture_id FK and preserves the
# default-avatar sentinel.
if topology_uses_native_infra; then
  echo "[$(date)] ===== ${WORKLOAD} ${PROFILE}: teardown (native infra down — wipes DBs/redis/vault) ====="
  topology_infra_down
else
  echo "[$(date)] ===== ${WORKLOAD} ${PROFILE}: teardown (delete media_assets, sentinel preserved) ====="
  python3 testing/performance/k6/workloads/cpu-image-preview/prepare.py clean --base-url "$BENCH_URL" 2>&1 | tail -5
fi

cleanup_sweep_finish
