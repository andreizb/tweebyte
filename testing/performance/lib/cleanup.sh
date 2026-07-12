#!/usr/bin/env bash
# Shared cleanup helpers sourced by every workload wrapper under
# testing/performance/{jmeter,k6}/workloads/<workload>/run.sh.
#
# Why this exists: JMeter raw .jtl + _filtered.jtl files can reach 1+ GB per
# cell at conc=1000. A 14-cell × 5-run canonical produces ~35 GB/stack of
# samples that are 100% disposable after cells.csv is aggregated. Operators
# kept running out of disk. This lib centralises the "delete .jtl after
# successful aggregation" + "remove partial dirs on abort" contract so every
# wrapper inherits identical behaviour.
#
# Operator override: set KEEP_RAW=1 on the calling shell to preserve raw
# samples (e.g., when you need to re-aggregate with different percentile cuts
# from the same run without re-executing the bench). Default behaviour deletes
# them after aggregation.
#
# Bash 3.2 safe (macOS default shell) — no associative arrays, careful array
# expansions, set -u tolerant.

# Operator override — preserve raw .jtl/_filtered.jtl after aggregation.
KEEP_RAW="${KEEP_RAW:-0}"

# Marker file for the trap; set by cleanup_sweep_init, removed by
# cleanup_sweep_finish. Result dirs created with mtime newer than this marker
# are considered "part of this sweep" by the abort trap.
SWEEP_MARKER=""

# Find the most recently created result dir matching the given workload + tool.
# Mirrors the `ls -dt ... | head -1` pattern that already lives at the bottom
# of every wrapper, so behaviour stays consistent.
#
# Args: <repo> <tool>  <workload>
#   tool:     'jmeter' or 'k6'
#   workload: 'dbwrite-light-follow-create', 'dbwrite-heavy-tweet-update', etc. For
#             k6 the lib normalises dashes to underscores to match run_bench.sh's dir naming.
find_latest_result_dir() {
    local repo="$1" tool="$2" workload="$3"
    local wl_normalized
    if [[ "$tool" == "k6" ]]; then
        wl_normalized="${workload//-/_}"
    else
        wl_normalized="$workload"
    fi
    ls -dt "${repo}/testing-results/performance/${tool}/results_${wl_normalized}_2026"* 2>/dev/null | head -1
}

# Delete the raw sample files in a completed result dir while preserving:
#   * cells.csv          — aggregated per-cell metrics (the keeper)
#   * figures/*.png      — generated plots
#   * summary.txt        — run summary
#   * *_jmeter.log       — JMeter logs (KB each, useful for debugging)
#   * *_resources.csv    — per-cell heap/cpu samples (KB each)
#   * *_prom.csv         — k6 Prometheus snapshot (KB)
#   * *_validation.txt   — k6 validation output (KB)
#   * <conc>_<run>.txt   — k6 textual report
#   * boot-receipt.*     — effective startup metadata
# Deletes (the JMeter bulk that's 99 % of disk):
#   * *.jtl              — raw JMeter samples (already in cells.csv)
#   * *_filtered.jtl     — filtered intermediate
#
# Args: <result_dir>
cleanup_raw_samples() {
    local dir="${1:-}"
    if [[ -z "$dir" || ! -d "$dir" ]]; then
        echo "[cleanup] no result dir to clean (got: '${dir:-<empty>}')" >&2
        return 0
    fi
    if [[ "$KEEP_RAW" == "1" ]]; then
        echo "[cleanup] KEEP_RAW=1 — preserving raw samples in $dir" >&2
        return 0
    fi
    local before after
    before=$(du -sh "$dir" 2>/dev/null | awk '{print $1}')
    # -maxdepth 2 in case wrappers ever introduce a per-cell subdir layout
    find "$dir" -maxdepth 2 -type f \( -name '*.jtl' -o -name '*_filtered.jtl' \) -delete 2>/dev/null
    after=$(du -sh "$dir" 2>/dev/null | awk '{print $1}')
    echo "[cleanup] $dir: $before -> $after (raw .jtl removed)" >&2
}

# Flush the shared Redis before a stack's load run, giving each stack a COLD
# cache it then warms itself during its own warmup phase.
#
# Why this exists: the benchmark reuses ONE Redis container across the
# async->reactive switch (`runtime down <stack>` keeps infra up). The two stacks
# serialize some cache values incompatibly — e.g. tweet-service's `userIds` cache
# is JdkSerialization on async (defaultCacheConfig) but JSON on reactive — so a
# stack reading the other stack's leftover entries throws
# `SerializationException: Could not read JSON: Unexpected character ('¬')`
# (0xAC = JDK stream magic) and 500s every request until the entry's TTL expires.
# Production deploys each stack with its OWN Redis, so this collision is a
# benchmark-only artifact of sharing one Redis; flushing before each stack's load
# is the symmetric cold-start (the k6 caching workload already
# does its own FLUSHALL + warmup for the same reason).
#
# Args: (none) — targets the shared Redis via host redis-cli on its published port
# 127.0.0.1:63790, identical whether infra is Docker-published or native-local (no
# docker exec, so it works under TOPOLOGY=native-local where there is no container).
flush_cache() {
    if redis-cli -h 127.0.0.1 -p 63790 FLUSHALL >/dev/null 2>&1; then
        echo "[cleanup] flushed Redis — cold cache for this stack" >&2
    else
        echo "[cleanup] WARN: Redis FLUSHALL failed (is Redis up on 127.0.0.1:63790?)" >&2
    fi
}

# Remove a result dir entirely. Used on per-stack failure (status != 0).
# Args: <result_dir>
remove_partial_result_dir() {
    local dir="${1:-}"
    if [[ -n "$dir" && -d "$dir" ]]; then
        echo "[cleanup] removing partial result dir: $dir" >&2
        rm -rf "$dir"
    fi
}

# Trap handler — fires on INT (Ctrl-C) or TERM (kill). Finds any result dirs
# under testing-results/performance/ that were created since the sweep started
# (mtime newer than SWEEP_MARKER) and removes them entirely. The marker is
# unset by cleanup_sweep_finish on the success path so post-sweep signals
# don't accidentally delete cleaned-up dirs.
_on_sweep_abort() {
    local exit_code=$?
    echo "" >&2
    echo "[cleanup] sweep aborted (exit=$exit_code) — checking for partial result dirs" >&2
    if [[ -n "$SWEEP_MARKER" && -f "$SWEEP_MARKER" ]]; then
        local repo_root
        repo_root="$(cd "$(dirname "$SWEEP_MARKER")/../.." 2>/dev/null && pwd || true)"
        # Fall back to PWD if we can't derive repo from the marker's location
        [[ -z "$repo_root" || ! -d "${repo_root}/testing-results" ]] && repo_root="${REPO:-$PWD}"

        local count=0
        while IFS= read -r d; do
            [[ -z "$d" ]] && continue
            echo "[cleanup]   rm -rf $d" >&2
            rm -rf "$d"
            count=$((count + 1))
        done < <(find "${repo_root}/testing-results/performance" -maxdepth 3 -type d -name 'results_*' -newer "$SWEEP_MARKER" 2>/dev/null)
        echo "[cleanup] removed $count partial result dir(s)" >&2
        rm -f "$SWEEP_MARKER"
    else
        echo "[cleanup] no sweep marker — nothing to roll back" >&2
    fi
    exit "$exit_code"
}

# Call once at the top of a wrapper, after computing $REPO and $WORKLOAD.
# Creates the sweep marker file and registers the abort trap.
cleanup_sweep_init() {
    SWEEP_MARKER="$(mktemp -t tweebyte-sweep.XXXXXX)"
    trap '_on_sweep_abort' INT TERM
}

# Call at the end of a successful sweep — removes the marker so that any
# later INT/TERM (e.g., during teardown or after the script logically
# finishes) doesn't trigger spurious cleanup of completed result dirs.
cleanup_sweep_finish() {
    if [[ -n "$SWEEP_MARKER" && -f "$SWEEP_MARKER" ]]; then
        rm -f "$SWEEP_MARKER"
    fi
    SWEEP_MARKER=""
    trap - INT TERM
}
