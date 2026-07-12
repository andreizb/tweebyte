#!/usr/bin/env bash

# Shared app-topology helpers for workload wrappers.
#
# TOPOLOGY=local-app (default): infra stays Docker-managed, app services run as
# local Maven Spring Boot processes through ./run.sh local.
# TOPOLOGY=native-local: app services run the same way (./run.sh local), but infra
# is native too — deployment/local/local-infra.sh starts host Postgres/Redis/Vault on the
# same published ports, so no Docker is involved at all. Datastores start empty, so
# the schema is created by app Flyway at boot and seeding happens AFTER boot.
# TOPOLOGY=all-docker: legacy Compose app stack.

: "${TOPOLOGY:=local-app}"

TOPOLOGY_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOPOLOGY_REPO_ROOT="$(cd "${TOPOLOGY_LIB_DIR}/../../.." && pwd)"
# shellcheck source=../../../deployment/toxiproxy-lib.sh
source "${TOPOLOGY_REPO_ROOT}/deployment/toxiproxy-lib.sh"

topology_validate() {
  case "$TOPOLOGY" in
    local-app|native-local|all-docker) ;;
    *) echo "ERROR: unsupported TOPOLOGY='$TOPOLOGY' (expected local-app, native-local or all-docker)" >&2; exit 1 ;;
  esac
}

topology_configure_canonical_io_path() {
  topology_validate

  # Canonical native-local benchmark runs must use the pinned differentiated
  # toxiproxy I/O path. The latency profile itself lives in deployment/toxiproxy-lib.sh:
  # Redis 1 ms, Postgres 2 ms, inter-service HTTP 3 ms, jitter 0. This helper only
  # enables that profile for full-grid sweeps so direct-DB and proxied cells cannot
  # be pooled accidentally.
  case "${PROFILE:-}" in
    canonical|confidence)
      if [[ "$TOPOLOGY" == "native-local" ]]; then
        if [[ -z "${BENCH_TOXIPROXY_LATENCY_MS+x}" || -z "$BENCH_TOXIPROXY_LATENCY_MS" ]]; then
          export BENCH_TOXIPROXY_LATENCY_MS=1
        elif ! [[ "$BENCH_TOXIPROXY_LATENCY_MS" =~ ^[0-9]+$ ]] || (( BENCH_TOXIPROXY_LATENCY_MS <= 0 )); then
          echo "ERROR: TOPOLOGY=native-local PROFILE=$PROFILE requires BENCH_TOXIPROXY_LATENCY_MS>0 for canonical toxiproxy I/O routing" >&2
          exit 1
        else
          export BENCH_TOXIPROXY_LATENCY_MS
        fi

        export LOCAL_USER_DB_HOST=localhost
        export LOCAL_USER_DB_PORT="${TOXIPROXY_PG_USER_PORT}"
        export LOCAL_TWEET_DB_HOST=localhost
        export LOCAL_TWEET_DB_PORT="${TOXIPROXY_PG_TWEET_PORT}"
        export LOCAL_INTERACTION_DB_HOST=localhost
        export LOCAL_INTERACTION_DB_PORT="${TOXIPROXY_PG_INTERACTION_PORT}"
        export LOCAL_CACHE_HOST=localhost
        export LOCAL_CACHE_PORT="${TOXIPROXY_REDIS_PORT}"
        export LOCAL_TWEET_CACHE_HOST=localhost
        export LOCAL_TWEET_CACHE_PORT="${TOXIPROXY_REDIS_PORT}"
        export LOCAL_INTERACTION_CACHE_HOST=localhost
        export LOCAL_INTERACTION_CACHE_PORT="${TOXIPROXY_REDIS_PORT}"
        export USER_SERVICE_URL="http://localhost:${TOXIPROXY_SVC_USER_PORT}/"
        export TWEET_SERVICE_URL="http://localhost:${TOXIPROXY_SVC_TWEET_PORT}/"
        export INTERACTION_SERVICE_URL="http://localhost:${TOXIPROXY_SVC_INTERACTION_PORT}/"
      fi
      ;;
  esac
}

topology_configure_canonical_io_path

# native-local is the only topology whose infra is host-native + ephemeral (rather
# than Docker-managed + persistent); callers branch seed timing and teardown on it.
topology_uses_native_infra() {
  [[ "$TOPOLOGY" == "native-local" ]]
}

topology_label() {
  printf '%s\n' "$TOPOLOGY"
}

# Apps launch identically for local-app and native-local — host JVMs via ./run.sh
# local; only the infra provider differs (handled by topology_infra_up/down below).
topology_start_stack() {
  topology_validate
  local stack="$1"
  shift
  if [[ "$TOPOLOGY" == "local-app" || "$TOPOLOGY" == "native-local" ]]; then
    "$REPO/run.sh" local up "$stack" benchmark "$@"
  else
    "$REPO/run.sh" runtime up "$stack" benchmark
  fi
}

topology_stop_stack() {
  topology_validate
  local stack="$1"
  shift
  if [[ "$TOPOLOGY" == "local-app" || "$TOPOLOGY" == "native-local" ]]; then
    "$REPO/run.sh" local down "$stack" benchmark "$@"
  else
    "$REPO/run.sh" runtime down "$stack" benchmark
  fi
}

# Infra lifecycle. native-local owns its host-native infra (Postgres/Redis/Vault) and
# tears it down after the run; the Docker topologies bring up shared Compose infra and
# intentionally leave it up between runs (the workload's own clean step resets data).
topology_infra_up() {
  topology_validate
  if topology_uses_native_infra; then
    "$REPO/deployment/local/local-infra.sh" up
  else
    "$REPO/run.sh" runtime up infra benchmark
  fi
}

topology_infra_down() {
  topology_validate
  if topology_uses_native_infra; then
    "$REPO/deployment/local/local-infra.sh" down
  fi
}

topology_boot_receipt() {
  topology_validate
  local stack="$1" profile="$2" receipt_dir="$3"
  shift 3
  if [[ "$TOPOLOGY" == "local-app" || "$TOPOLOGY" == "native-local" ]]; then
    # Both launch host JVMs via ./run.sh local, which writes per-service receipts under
    # local-app/ regardless of the infra provider; the topology label distinguishes them.
    mkdir -p "$receipt_dir"
    local service src
    for service in "$@"; do
      src="$REPO/testing-results/runtime/local-app/${stack}/${service}/receipt.env"
      if [[ -f "$src" ]]; then
        cp "$src" "$receipt_dir/${service}.receipt.env"
      fi
    done
    {
      echo "topology=${TOPOLOGY}"
      echo "stack=${stack}"
      echo "profile=${profile}"
      echo "services=$*"
    } > "$receipt_dir/topology.env"
  else
    "$REPO/deployment/boot-receipt.sh" "$stack" "$profile" "$receipt_dir"
  fi
}
