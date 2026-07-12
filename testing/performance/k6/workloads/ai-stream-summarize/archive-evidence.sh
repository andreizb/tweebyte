#!/usr/bin/env bash
# Package AI streaming benchmark data and reproducibility metadata.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "${SCRIPT_DIR}/../../../../.." && pwd)"
OUT_DIR=""
RUNS_CSV=""
CELLS_CSV=""
FIGURES_DIR=""
CALIBRATION_JSON=""
CAMPAIGN_LOG=""
# Default the topology label from the env the runner exports; --topology overrides.
TOPOLOGY_LABEL="${BENCHMARK_TOPOLOGY:-${TOPOLOGY:-unknown}}"
RESULT_DIRS=()

usage() {
  cat <<'EOF'
Usage:
  archive-evidence.sh [options]

Options:
  --runs-csv <path>          Analysis runs.csv to archive.
  --cells-csv <path>         Analysis cells.csv to archive.
  --figures-dir <path>       Directory containing generated figures.
  --result-dir <path>        Raw k6 result directory. May be repeated.
  --calibration-json <path>  Calibration JSON used by the mock backend.
  --campaign-log <path>      Runner log or commands.tsv to archive. May be repeated
                             by passing --campaign-log more than once.
  --topology <label>         Topology label recorded in the manifest (default: from
                             BENCHMARK_TOPOLOGY/TOPOLOGY env, else "unknown"). The retained
                             primary and companion profiles use native-local.
  --out-dir <path>           Output evidence directory.
  --help
EOF
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

resolve_path() {
  local value="$1"
  if [[ "$value" == /* ]]; then
    printf '%s\n' "$value"
  else
    printf '%s\n' "${REPO}/${value}"
  fi
}

copy_file_if_set() {
  local label="$1" source="$2" dest_dir="$3"
  [[ -n "$source" ]] || return 0
  [[ -f "$source" ]] || die "${label} not found: ${source}"
  cp "$source" "$dest_dir/"
}

copy_dir_if_set() {
  local label="$1" source="$2" dest_dir="$3"
  [[ -n "$source" ]] || return 0
  [[ -d "$source" ]] || die "${label} not found: ${source}"
  mkdir -p "$dest_dir"
  cp -R "$source" "$dest_dir/"
}

# Space-joined sorted-unique values of a named CSV column (header-resolved), so the
# manifest records exactly which calibration tags / campaigns / token levels / backends /
# pins the archived evidence spans. Empty when the file or column is absent.
distinct_col_values() {
  local csv="$1" col="$2"
  [[ -f "$csv" ]] || return 0
  awk -F, -v want="$col" '
    NR==1 { for (i=1;i<=NF;i++) if ($i==want) c=i; next }
    c && $c!="" { print $c }
  ' "$csv" | sort -u | paste -sd' ' -
}

while (($# > 0)); do
  case "$1" in
    --runs-csv) RUNS_CSV="$(resolve_path "$2")"; shift 2 ;;
    --cells-csv) CELLS_CSV="$(resolve_path "$2")"; shift 2 ;;
    --figures-dir) FIGURES_DIR="$(resolve_path "$2")"; shift 2 ;;
    --result-dir) RESULT_DIRS+=("$(resolve_path "$2")"); shift 2 ;;
    --calibration-json) CALIBRATION_JSON="$(resolve_path "$2")"; shift 2 ;;
    --campaign-log) CAMPAIGN_LOG="${CAMPAIGN_LOG}${CAMPAIGN_LOG:+:}$(resolve_path "$2")"; shift 2 ;;
    --topology) TOPOLOGY_LABEL="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$(resolve_path "$2")"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown argument '$1'" ;;
  esac
done

if [[ -z "$OUT_DIR" ]]; then
  OUT_DIR="${REPO}/testing-results/performance/k6/evidence_ai_streaming_$(date +%Y%m%d_%H%M%S)"
fi

mkdir -p "$OUT_DIR"/{analysis,result-dirs,figures,logs,provenance}

copy_file_if_set "runs CSV" "$RUNS_CSV" "$OUT_DIR/analysis"
copy_file_if_set "cells CSV" "$CELLS_CSV" "$OUT_DIR/analysis"
copy_file_if_set "calibration JSON" "$CALIBRATION_JSON" "$OUT_DIR/provenance"

if [[ -n "$FIGURES_DIR" ]]; then
  copy_dir_if_set "figures directory" "$FIGURES_DIR" "$OUT_DIR/figures"
fi

IFS=':' read -r -a CAMPAIGN_LOGS <<<"$CAMPAIGN_LOG"
for log_path in "${CAMPAIGN_LOGS[@]}"; do
  [[ -n "$log_path" ]] || continue
  copy_file_if_set "campaign log" "$log_path" "$OUT_DIR/logs"
done

for result_dir in "${RESULT_DIRS[@]}"; do
  [[ -d "$result_dir" ]] || die "result directory not found: ${result_dir}"
  cp -R "$result_dir" "$OUT_DIR/result-dirs/"
done

# Prefer cells.csv (one row per cell) to derive the axis sets the evidence spans; fall
# back to runs.csv if only that was provided.
META_CSV="${CELLS_CSV:-$RUNS_CSV}"

{
  echo "created_at=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo "repo=$REPO"
  echo "git_commit=$(git -C "$REPO" rev-parse HEAD 2>/dev/null || true)"
  echo "git_branch=$(git -C "$REPO" rev-parse --abbrev-ref HEAD 2>/dev/null || true)"
  echo "git_dirty_files=$(git -C "$REPO" status --short 2>/dev/null | wc -l | tr -d ' ')"
  echo "topology=${TOPOLOGY_LABEL}"
  echo "runs_csv=${RUNS_CSV}"
  echo "cells_csv=${CELLS_CSV}"
  echo "figures_dir=${FIGURES_DIR}"
  echo "calibration_json=${CALIBRATION_JSON}"
  # Axis/pin sets the archived evidence actually spans (header-resolved from META_CSV).
  echo "calibration_tags=$(distinct_col_values "$META_CSV" calibration_tag)"
  echo "campaigns=$(distinct_col_values "$META_CSV" campaign)"
  echo "workloads=$(distinct_col_values "$META_CSV" workload)"
  echo "transports=$(distinct_col_values "$META_CSV" transport)"
  echo "tokens_per_response_levels=$(distinct_col_values "$META_CSV" tokens_per_response)"
  echo "prompt_variants=$(distinct_col_values "$META_CSV" prompt_variant)"
  echo "ai_backends=$(distinct_col_values "$META_CSV" ai_backend)"
  echo "pool_sizes=$(distinct_col_values "$META_CSV" pool_size)"
  echo "reject_policies=$(distinct_col_values "$META_CSV" reject_policy)"
  printf 'result_dirs='
  printf '%s ' "${RESULT_DIRS[@]}"
  printf '\n'
} > "$OUT_DIR/provenance/manifest.properties"

git -C "$REPO" status --short > "$OUT_DIR/provenance/git-status-short.txt" 2>/dev/null || true
git -C "$REPO" diff --stat > "$OUT_DIR/provenance/git-diff-stat.txt" 2>/dev/null || true
# Benchmark-relevant code surface: the k6 workload (incl. analysis module) + engine, plus
# the tweet-service AI config + metrics (mock/live wiring and the asserted config gauges).
git -C "$REPO" diff -- \
  testing/performance/k6/workloads/ai-stream-summarize \
  testing/performance/k6/run_bench.sh \
  backend/async/tweet-service/src/main/java/ro/tweebyte/tweetservice/config/AiConfiguration.java \
  backend/async/tweet-service/src/main/java/ro/tweebyte/tweetservice/metrics \
  backend/reactive/tweet-service/src/main/java/ro/tweebyte/tweetservice/config/AiConfiguration.java \
  backend/reactive/tweet-service/src/main/java/ro/tweebyte/tweetservice/metrics \
  > "$OUT_DIR/provenance/benchmark-code.diff" 2>/dev/null || true

{
  echo "java:"
  java -version 2>&1 || true
  echo
  echo "k6:"
  if command -v k6 >/dev/null 2>&1; then
    k6 version 2>&1 || true
  elif [[ -x /opt/homebrew/bin/k6 ]]; then
    /opt/homebrew/bin/k6 version 2>&1 || true
  else
    echo "not found"
  fi
  echo
  echo "docker:"
  docker version 2>&1 || true
} > "$OUT_DIR/provenance/tool-versions.txt"

echo "Evidence archived in: $OUT_DIR"
