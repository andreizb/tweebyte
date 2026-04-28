#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_ARGS=()
ENV_PREFIX=()

usage() {
  cat <<'EOF'
Usage:
  Canonical root syntax:
    ./run.sh runtime up <infra|async|reactive> <prod|benchmark|fe-test> [extra docker compose args...]
    ./run.sh runtime down <infra|async|reactive> <prod|benchmark|fe-test> [extra docker compose args...]
    ./run.sh runtime destroy <infra|async|reactive> <prod|benchmark|fe-test> [extra docker compose args...]
    ./run.sh runtime ps <infra|async|reactive> [extra docker compose args...]
    ./run.sh runtime logs <infra|async|reactive> [extra docker compose args...]

  Compatibility aliases still supported:
    ./run.sh up <infra|async|reactive> <prod|benchmark|fe-test> [extra docker compose args...]
    ./run.sh down <infra|async|reactive> <prod|benchmark|fe-test> [extra docker compose args...]
    ./run.sh destroy <infra|async|reactive> <prod|benchmark|fe-test> [extra docker compose args...]
    ./run.sh ps <infra|async|reactive> [extra docker compose args...]
    ./run.sh logs <infra|async|reactive> [extra docker compose args...]

Profiles:
  prod      — normal runtime (no instrumentation, no toxiproxy targeting).
  benchmark — performance-test profile (toxiproxy in front of Redis, GC log,
              JVM heap caps, k6 hits services directly bypassing the gateway).
              Does NOT load any FE-test instrumentation.
  fe-test   — functional-equivalence-test profile, used by the Cucumber suite
              under testing/equivalence/. Layers a JaCoCo agent into each
              service JVM so coverage from end-to-end scenarios can be
              aggregated. Layers cleanly on top of the prod-style topology
              (no toxiproxy, no JVM heap caps). NEVER affects the prod or
              benchmark paths — its compose overlay is only loaded when this
              profile is selected.

Examples:
  ./run.sh runtime up infra benchmark
  ./run.sh runtime up reactive prod
  ./run.sh runtime up async fe-test
  ./run.sh runtime down async benchmark
  ./run.sh runtime destroy infra prod
  ./run.sh runtime ps reactive
  ./run.sh runtime logs async --tail 100
EOF
}

require_target() {
  local target="$1"
  case "$target" in
    infra|async|reactive) ;;
    *)
      echo "Unsupported target: $target" >&2
      usage
      exit 1
      ;;
  esac
}

require_profile() {
  local profile="$1"
  case "$profile" in
    prod|benchmark|fe-test) ;;
    *)
      echo "Unsupported profile: $profile" >&2
      usage
      exit 1
      ;;
  esac
}

compose_files_for_mode() {
  local target="$1"
  local profile="$2"

  COMPOSE_ARGS=(-f "${SCRIPT_DIR}/compose/infrastructure.yml")

  case "$target" in
    infra) ;;
    async)
      COMPOSE_ARGS+=(-f "${SCRIPT_DIR}/compose/async.yml")
      ;;
    reactive)
      COMPOSE_ARGS+=(-f "${SCRIPT_DIR}/compose/reactive.yml")
      ;;
  esac

  # The fe-test overlay layers a JaCoCo agent onto each service JVM so the
  # Cucumber suite under testing/equivalence/ can collect end-to-end coverage.
  # It is ONLY loaded for fe-test runs; prod and benchmark never see it.
  if [[ "$profile" == "fe-test" && "$target" != "infra" ]]; then
    COMPOSE_ARGS+=(-f "${SCRIPT_DIR}/compose/fe-test.yml")
  fi
}

compose_files_for_inspect() {
  local target="$1"

  COMPOSE_ARGS=(-f "${SCRIPT_DIR}/compose/infrastructure.yml")

  case "$target" in
    infra) ;;
    async)
      COMPOSE_ARGS+=(
        -f "${SCRIPT_DIR}/compose/async.yml"
      )
      ;;
    reactive)
      COMPOSE_ARGS+=(
        -f "${SCRIPT_DIR}/compose/reactive.yml"
      )
      ;;
  esac
}

build_env_prefix() {
  local target="$1"
  local profile="$2"

  ENV_PREFIX=()

  if [[ "$profile" == "benchmark" && "$target" != "infra" ]]; then
    ENV_PREFIX+=(
      "STACK_PROFILE=benchmark"
      "INTERACTION_CACHE_HOST=toxiproxy"
      "INTERACTION_CACHE_PORT=26379"
      "INTERACTION_JAVA_TOOL_OPTIONS=${INTERACTION_JAVA_TOOL_OPTIONS:--Xms3g -Xmx3g -XX:+AlwaysPreTouch -Xlog:gc*,safepoint,gc+heap=debug:file=/tmp/gc-interaction.log:time,level,tags:filecount=5,filesize=50m}"
      "TWEET_JAVA_TOOL_OPTIONS=${TWEET_JAVA_TOOL_OPTIONS:--Xms3g -Xmx3g -XX:+AlwaysPreTouch -Xlog:gc*,safepoint,gc+heap=debug:file=/tmp/gc-tweet.log:time,level,tags:filecount=5,filesize=50m}"
      "APP_CONCURRENCY_TWEET_REJECT_POLICY=${APP_CONCURRENCY_TWEET_REJECT_POLICY:-abort}"
    )
  fi
}

print_command() {
  local -a cmd=("$@")
  local rendered=()
  local env_count=0

  if declare -p ENV_PREFIX >/dev/null 2>&1; then
    env_count=${#ENV_PREFIX[@]}
  fi

  if (( env_count > 0 )); then
    for item in "${ENV_PREFIX[@]}"; do
      rendered+=("$item")
    done
  fi
  rendered+=("${cmd[@]}")

  printf 'Running:'
  for item in "${rendered[@]}"; do
    printf ' %q' "$item"
  done
  printf '\n'
}

run_compose() {
  local -a cmd=("docker" "compose" "--project-directory" "${REPO_ROOT}" "${COMPOSE_ARGS[@]}" "$@")
  local env_count=0

  if declare -p ENV_PREFIX >/dev/null 2>&1; then
    env_count=${#ENV_PREFIX[@]}
  fi

  print_command "${cmd[@]}"

  if (( env_count > 0 )); then
    env "${ENV_PREFIX[@]}" "${cmd[@]}"
  else
    "${cmd[@]}"
  fi
}

main() {
  if (($# < 2)); then
    usage
    exit 1
  fi

  local action="$1"
  local target="$2"
  shift 2

  require_target "$target"

  case "$action" in
    up|down|destroy)
      if (($# < 1)); then
        usage
        exit 1
      fi

      local profile="$1"
      shift

      require_profile "$profile"
      compose_files_for_mode "$target" "$profile"
      build_env_prefix "$target" "$profile"

      case "$action" in
        up)
          # fe-test ALWAYS rebuilds: both async/* and reactive/* Dockerfiles
          # produce the same image name (tweebyte-<service>:latest), so without
          # --build a stack switch silently runs the previous stack's code.
          local -a up_extra=()
          if [[ "$profile" == "fe-test" ]]; then
            up_extra+=(--build)
          fi
          run_compose up -d --remove-orphans "${up_extra[@]}" "$@"
          ;;
        down)
          run_compose down --remove-orphans "$@"
          ;;
        destroy)
          run_compose down -v --rmi local --remove-orphans "$@"
          ;;
      esac
      ;;
    ps|logs)
      compose_files_for_inspect "$target"
      ENV_PREFIX=()

      case "$action" in
        ps)
          run_compose ps "$@"
          ;;
        logs)
          run_compose logs "$@"
          ;;
      esac
      ;;
    *)
      echo "Unsupported action: $action" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"
