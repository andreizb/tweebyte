#!/usr/bin/env bash
# Exact-run cleanup shared by benchmark drivers and workload wrappers.
# Bash 3.2 compatible. Raw JTL is disposable only after complete aggregation;
# failed/cancelled runs and all KEEP_RAW=1 runs retain their diagnostic data.

KEEP_RAW="${KEEP_RAW:-0}"

_cleanup_registry_init() {
    if [[ -z "${BENCH_SWEEP_STATE:-}" ]]; then
        BENCH_SWEEP_STATE="$(umask 077; mktemp -d "${TMPDIR:-/tmp}/tweebyte-sweep.XXXXXX")"
        BENCH_SWEEP_STATE="$(cd "$BENCH_SWEEP_STATE" && pwd -P)"
        : > "$BENCH_SWEEP_STATE/results"
        export BENCH_SWEEP_STATE
    fi
    [[ -d "$BENCH_SWEEP_STATE" && ! -L "$BENCH_SWEEP_STATE" &&
       -f "$BENCH_SWEEP_STATE/results" && ! -L "$BENCH_SWEEP_STATE/results" ]]
}

# Immediately after the driver creates its output directory.
# Args: <dir> <engine> [runs] [space-separated concurrencies]
cleanup_result_init() {
    local dir="$1" engine="$2"
    CLEANUP_OWNS_REGISTRY=0
    [[ -n "${BENCH_SWEEP_STATE:-}" ]] || CLEANUP_OWNS_REGISTRY=1
    _cleanup_registry_init || return 1
    [[ -d "$dir" && ! -L "$dir" ]] || return 1
    CLEANUP_RESULT_DIR="$(cd "$dir" && pwd -P)"
    [[ "$CLEANUP_RESULT_DIR" != *$'\n'* ]] || return 1
    printf '%s\n' "$BENCH_SWEEP_STATE" > "$CLEANUP_RESULT_DIR/.cleanup-owner"
    printf '%s\n' "$$" > "$CLEANUP_RESULT_DIR/.cleanup-writers"
    printf '%s\n' "$engine" "${3:-}" "${4:-}" > "$CLEANUP_RESULT_DIR/.cleanup-expected"
    printf '%s\n' "$CLEANUP_RESULT_DIR" >> "$BENCH_SWEEP_STATE/results"
    trap '_cleanup_result_exit "$?"' EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM
}

cleanup_track_writer() {
    printf '%s\n' "$1" >> "$CLEANUP_RESULT_DIR/.cleanup-writers"
}

_cleanup_owned() {
    local dir="${1:-}" registered
    [[ -n "${BENCH_SWEEP_STATE:-}" && -f "$BENCH_SWEEP_STATE/results" &&
       -d "$dir" && ! -L "$dir" &&
       -f "$dir/.cleanup-owner" && ! -L "$dir/.cleanup-owner" &&
       -f "$dir/.cleanup-writers" && ! -L "$dir/.cleanup-writers" ]] || return 1
    [[ "$(cd "$dir" && pwd -P)" == "$dir" &&
       "$(cat "$dir/.cleanup-owner")" == "$BENCH_SWEEP_STATE" ]] || return 1
    while IFS= read -r registered; do
        [[ "$registered" != "$dir" ]] || return 0
    done < "$BENCH_SWEEP_STATE/results"
    return 1
}

_cleanup_writers_idle() {
    local dir="$1" pid
    while IFS= read -r pid; do
        case "$pid" in ''|*[!0-9]*) return 1 ;; esac
        # Only the driver's own EXIT handler exempts the exiting driver.
        [[ "$pid" != "${CLEANUP_EXIT_PID:-}" ]] || continue
        if kill -0 "$pid" 2>/dev/null; then return 1; fi
    done < "$dir/.cleanup-writers"
}

# Call only after the driver's final aggregation/recap returns.
cleanup_result_complete() {
    : > "$CLEANUP_RESULT_DIR/.cleanup-complete"
}

cleanup_raw_samples() {
    local dir="${1:-}"
    [[ "${KEEP_RAW:-0}" != 1 ]] || return 0
    _cleanup_owned "$dir" || return 0
    [[ -n "$(find "$dir" -maxdepth 2 -type f -name '*.jtl' -print -quit)" ]] || return 0
    if [[ ! -f "$dir/.cleanup-complete" ]] || ! _cleanup_writers_idle "$dir"; then
        echo "[cleanup] retained incomplete or active run: $dir" >&2
        return 0
    fi
    # A header-only, truncated, or partially populated CSV is not proof that
    # every requested cell/run was aggregated.
    if ! python3 - "$dir" <<'PY'
import csv
import sys
from pathlib import Path

directory = Path(sys.argv[1])
try:
    expected_path = directory / '.cleanup-expected'
    csv_path = directory / 'cells.csv'
    if expected_path.is_symlink() or csv_path.is_symlink():
        raise ValueError('symlinked metadata')
    engine, runs, concurrencies = expected_path.read_text().splitlines()
    if engine != 'jmeter':
        raise ValueError('no JMeter aggregate')
    with csv_path.open(newline='') as handle:
        rows = list(csv.DictReader(handle))
    expected = {int(c) for c in concurrencies.split()}
    actual = {int(row['concurrency']) for row in rows}
    if not expected or actual != expected or len(rows) != len(expected):
        raise ValueError('missing or unexpected cells')
    if not all(int(row['n']) == int(runs) and int(row['total_count']) > 0 for row in rows):
        raise ValueError('incomplete cell aggregation')
except (OSError, ValueError, KeyError, TypeError, csv.Error):
    sys.exit(1)
PY
    then
        echo "[cleanup] retained raw samples; aggregation incomplete: $dir" >&2
        return 0
    fi
    # Catch writers outside the driver's direct child list as well.
    # Missing lsof or an inspection failure leaves the samples intact.
    local open_pids lsof_status=0 lsof_errors
    command -v lsof >/dev/null 2>&1 || {
        echo "[cleanup] retained raw samples; lsof unavailable: $dir" >&2
        return 0
    }
    lsof_errors="$(mktemp "$BENCH_SWEEP_STATE/lsof.XXXXXX")"
    open_pids="$(lsof -t +D "$dir" 2>"$lsof_errors")" || lsof_status=$?
    if [[ -n "$open_pids" || -s "$lsof_errors" || "$lsof_status" -gt 1 ]]; then
        rm -f "$lsof_errors"
        echo "[cleanup] retained raw samples; open files or uncertain writer state: $dir" >&2
        return 0
    fi
    rm -f "$lsof_errors"
    find "$dir" -maxdepth 2 -type f -name '*.jtl' -delete
    echo "[cleanup] removed aggregated raw JTL from owned run: $dir" >&2
}

_cleanup_result_exit() {
    local status="$1"
    trap - EXIT
    if ! _cleanup_owned "$CLEANUP_RESULT_DIR"; then
        echo "[cleanup] ownership changed; leaving result untouched: $CLEANUP_RESULT_DIR" >&2
        exit "$status"
    fi
    CLEANUP_EXIT_PID="$$"
    printf 'exit_code=%s\n' "$status" > "$CLEANUP_RESULT_DIR/.cleanup-status"
    if [[ "$status" -ne 0 ]]; then
        rm -f "$CLEANUP_RESULT_DIR/.cleanup-complete"
        echo "[cleanup] retained failed/cancelled run: $CLEANUP_RESULT_DIR" >&2
    fi
    cleanup_raw_samples "$CLEANUP_RESULT_DIR" || true
    if [[ "$CLEANUP_OWNS_REGISTRY" == 1 ]]; then cleanup_sweep_finish; fi
    exit "$status"
}

_on_sweep_abort() {
    local status="$1"
    echo "[cleanup] sweep interrupted; incomplete results are retained" >&2
    cleanup_sweep_finish
    exit "$status"
}

cleanup_sweep_init() {
    _cleanup_registry_init || return 1
    trap '_on_sweep_abort 130' INT
    trap '_on_sweep_abort 143' TERM
}

cleanup_sweep_finish() {
    local dir active=0
    if [[ -n "${BENCH_SWEEP_STATE:-}" && -f "$BENCH_SWEEP_STATE/results" ]]; then
        while IFS= read -r dir; do
            _cleanup_owned "$dir" || continue
            if ! _cleanup_writers_idle "$dir"; then active=1; fi
            cleanup_raw_samples "$dir" || true
        done < "$BENCH_SWEEP_STATE/results"
        if [[ "$active" == 0 ]]; then
            rm -f "$BENCH_SWEEP_STATE/results"
            rmdir "$BENCH_SWEEP_STATE" 2>/dev/null || true
        else
            echo "[cleanup] active writers remain; registry retained: $BENCH_SWEEP_STATE" >&2
        fi
    fi
    unset BENCH_SWEEP_STATE
    trap - INT TERM
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
