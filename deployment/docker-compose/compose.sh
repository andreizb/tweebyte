#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$SCRIPT_DIR"
DEPLOYMENT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${DEPLOYMENT_DIR}/.." && pwd)"
COMPOSE_ARGS=()
ENV_PREFIX=()

# Shared toxiproxy port map + spec builder. The Docker benchmark path puts toxiproxy
# on the I/O path only when BENCH_TOXIPROXY_LATENCY_MS>0 (default off => direct, the
# pre-toxiproxy benchmark routing). See deployment/toxiproxy-lib.sh.
# shellcheck source=deployment/toxiproxy-lib.sh
source "${DEPLOYMENT_DIR}/toxiproxy-lib.sh"

usage() {
  cat <<'EOF'
Usage:
  Canonical root syntax:
    ./run.sh runtime up <infra|async|reactive> <prod|benchmark|functional-equivalence> [extra docker compose args...]
    ./run.sh runtime down <infra|async|reactive> <prod|benchmark|functional-equivalence> [extra docker compose args...]
    ./run.sh runtime destroy <infra|async|reactive> <prod|benchmark|functional-equivalence> [extra docker compose args...]
    ./run.sh runtime ps <infra|async|reactive> [extra docker compose args...]
    ./run.sh runtime logs <infra|async|reactive> [extra docker compose args...]

  Compatibility aliases still supported:
    ./run.sh up <infra|async|reactive> <prod|benchmark|functional-equivalence> [extra docker compose args...]
    ./run.sh down <infra|async|reactive> <prod|benchmark|functional-equivalence> [extra docker compose args...]
    ./run.sh destroy <infra|async|reactive> <prod|benchmark|functional-equivalence> [extra docker compose args...]
    ./run.sh ps <infra|async|reactive> [extra docker compose args...]
    ./run.sh logs <infra|async|reactive> [extra docker compose args...]

Profiles:
  prod      — normal runtime (no instrumentation, no toxiproxy targeting).
              Compose infra --profile full: all services (Keycloak, SonarQube,
              Grafana/Prometheus/Loki/Promtail/Alertmanager, Jaeger, Kafka).
  benchmark — performance-test profile (toxiproxy in front of Redis + Postgres,
              JVM heap caps, k6 hits services directly bypassing the gateway).
              Does NOT load any FE-test instrumentation.
              Compose infra: core only (postgres×3 + redis + toxiproxy + vault).
              No --profile full — Grafana, Keycloak, Sonar, Kafka, Jaeger omitted.
  functional-equivalence
            — functional-equivalence profile, used by the Cucumber suite
              under testing/functional-equivalence/. Layers a JaCoCo agent into each
              service JVM so coverage from end-to-end scenarios can be
              aggregated. Layers cleanly on top of the prod-style topology
              (no toxiproxy, no JVM heap caps). NEVER affects the prod or
              benchmark paths — its compose overlay is only loaded when this
              profile is selected.
              Compose infra --profile full: all services (same as prod).

Overlays (opt-in env flags, async/reactive targets only):
  WITH_FRONTEND=1  chain the Nginx-served Angular SPA (frontend.yml). Pair with the
                   prod / functional-equivalence profile so --profile full brings up
                   Keycloak — the SPA delegates login/register to it.
  DIAG=1           chain the JFR/JMX/async-profiler diagnostics overlay.

Examples:
  ./run.sh runtime up infra benchmark
  ./run.sh runtime up reactive prod
  ./run.sh runtime up async functional-equivalence
  WITH_FRONTEND=1 ./run.sh runtime up async prod      # backend + SPA + Keycloak
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
    prod|benchmark|functional-equivalence) ;;
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

  COMPOSE_ARGS=(-f "${COMPOSE_DIR}/infrastructure.yml")

  # benchmark = core infra only (postgres×3 + redis + toxiproxy + vault).
  # prod and functional-equivalence = full stack: activate the 'full' compose
  # profile to also start Keycloak, SonarQube, Grafana/Prometheus/Loki, Jaeger, Kafka.
  if [[ "$profile" != "benchmark" ]]; then
    COMPOSE_ARGS+=(--profile full)
  else
    # benchmark: activate the 'benchmark' compose profile so the toxiproxy-config
    # one-shot runs (it installs the latency proxies). prod / functional-equivalence
    # never activate it, so their toxiproxy container stays an uninitialized idle
    # forwarder — no proxy traffic in prod.
    COMPOSE_ARGS+=(--profile benchmark)
  fi

  case "$target" in
    infra) ;;
    async)
      COMPOSE_ARGS+=(-f "${COMPOSE_DIR}/async.yml")
      ;;
    reactive)
      COMPOSE_ARGS+=(-f "${COMPOSE_DIR}/reactive.yml")
      ;;
  esac

  # The functional-equivalence overlay layers a JaCoCo agent onto each service JVM so the
  # Cucumber suite under testing/functional-equivalence/ can collect end-to-end coverage.
  # It is ONLY loaded for functional-equivalence runs; prod and benchmark never see it.
  if [[ "$profile" == "functional-equivalence" && "$target" != "infra" ]]; then
    COMPOSE_ARGS+=(-f "${COMPOSE_DIR}/functional-equivalence.yml")
  fi

  # The diagnostics overlay (JFR + JMX + async-profiler + heap-dump-on-OOM + JDWP, host
  # /diag volume) is OPT-IN for INVESTIGATING a bad benchmark cell — set DIAG=1 to layer
  # it on. Default off: measured smokes run on the plain images so profiling overhead can
  # never taint the numbers. Threaded here (not auto) so the profiled stack inherits the
  # exact benchmark routing env via build_env_prefix. See diagnostics.yml.
  if [[ "${DIAG:-0}" != "0" && "$target" != "infra" ]]; then
    COMPOSE_ARGS+=(-f "${COMPOSE_DIR}/diagnostics.yml")
  fi

  # The frontend overlay (Nginx-served Angular SPA, same-origin reverse-proxy to
  # gateway-service) is OPT-IN for a UI deploy — set WITH_FRONTEND=1 to chain it on
  # top of a stack. It needs gateway-service, so it is only added for the async /
  # reactive targets, not bare infra. Use it with prod / functional-equivalence
  # (which activate --profile full above, bringing up Keycloak): the SPA delegates
  # login/register to Keycloak, so without the full profile authentication breaks.
  if [[ "${WITH_FRONTEND:-0}" != "0" && "$target" != "infra" ]]; then
    COMPOSE_ARGS+=(-f "${COMPOSE_DIR}/frontend.yml")
  fi
}

compose_files_for_inspect() {
  local target="$1"

  # Always include --profile full so ps/logs show the complete set of defined
  # services regardless of which profile was used to start the stack.
  COMPOSE_ARGS=(-f "${COMPOSE_DIR}/infrastructure.yml" --profile full)

  case "$target" in
    infra) ;;
    async)
      COMPOSE_ARGS+=(
        -f "${COMPOSE_DIR}/async.yml"
      )
      ;;
    reactive)
      COMPOSE_ARGS+=(
        -f "${COMPOSE_DIR}/reactive.yml"
      )
      ;;
  esac
}

build_env_prefix() {
  local target="$1"
  local profile="$2"

  ENV_PREFIX=()
  if [[ "$profile" == "functional-equivalence" && "$target" != "infra" ]]; then
    ENV_PREFIX+=(
      "FE_STACK=${target}"
      "STACK_PROFILE=functional-equivalence"
    )
  fi
  [[ "$profile" == "benchmark" ]] || return 0

  # toxiproxy I/O-path mode — opt-in via BENCH_TOXIPROXY_LATENCY_MS>0. The
  # toxiproxy-config container (benchmark compose profile only) installs latency
  # proxies in front of the three DBs, Redis, and the three inter-service hops to
  # drive the bench I/O-bound; the apps are routed at the proxy listeners below.
  # Default (knob unset): services connect DIRECT and the toxiproxy container stays
  # an uninitialized idle forwarder. The proxy spec + latency are emitted for every
  # benchmark target so `up infra benchmark` configures the proxies before the apps
  # start. Load generators (k6/JMeter) always hit the services directly, never proxied.
  if toxiproxy_enabled; then
    ENV_PREFIX+=(
      "BENCH_TOXIPROXY_LATENCY_MS=$(toxiproxy_latency_ms)"
      "TOXIPROXY_JITTER_MS=$(toxiproxy_jitter_ms)"
      "TOXIPROXY_PROXIES=$(toxiproxy_build_spec docker)"
    )
  fi

  [[ "$target" != "infra" ]] || return 0

  # Cache/DB/peer endpoints. Benchmark turns Redis SSL off, so the plaintext port
  # 6379 is pinned here (the compose default is 6380, the prod/FE TLS port). With
  # toxiproxy on, the DB/cache/peer endpoints become the proxy listeners (host
  # `toxiproxy`); off, the compose ${VAR:-default} fallbacks keep them direct.
  # JVM args (-Xmx4g; no -Xms/AlwaysPreTouch/virtual-threads) live in the compose
  # files as the single source of truth — this wrapper emits only routing env.
  local cache_host="redis" cache_port="6379"
  ENV_PREFIX+=(
    "STACK_PROFILE=benchmark"
    # W3 TLS: benchmark runs edge TLS + east-west mTLS OFF (server.ssl.enabled=false
    # / app.mtls.enabled=false in the overlay), so the downstream scheme must be http
    # (the compose default is https for prod/FE).
    "SVC_SCHEME=http"
  )
  if toxiproxy_enabled; then
    cache_host="toxiproxy"
    cache_port="${TOXIPROXY_REDIS_PORT}"
    ENV_PREFIX+=(
      "USER_DB_HOST=${USER_DB_HOST:-toxiproxy}"
      "USER_DB_PORT=${USER_DB_PORT:-${TOXIPROXY_PG_USER_PORT}}"
      "TWEET_DB_HOST=${TWEET_DB_HOST:-toxiproxy}"
      "TWEET_DB_PORT=${TWEET_DB_PORT:-${TOXIPROXY_PG_TWEET_PORT}}"
      "INTERACTION_DB_HOST=${INTERACTION_DB_HOST:-toxiproxy}"
      "INTERACTION_DB_PORT=${INTERACTION_DB_PORT:-${TOXIPROXY_PG_INTERACTION_PORT}}"
      "USER_SERVICE_URL=${USER_SERVICE_URL:-http://toxiproxy:${TOXIPROXY_SVC_USER_PORT}/}"
      "TWEET_SERVICE_URL=${TWEET_SERVICE_URL:-http://toxiproxy:${TOXIPROXY_SVC_TWEET_PORT}/}"
      "INTERACTION_SERVICE_URL=${INTERACTION_SERVICE_URL:-http://toxiproxy:${TOXIPROXY_SVC_INTERACTION_PORT}/}"
    )
  fi
  ENV_PREFIX+=(
    "INTERACTION_CACHE_HOST=${INTERACTION_CACHE_HOST:-${cache_host}}"
    "INTERACTION_CACHE_PORT=${INTERACTION_CACHE_PORT:-${cache_port}}"
    "TWEET_CACHE_HOST=${TWEET_CACHE_HOST:-${cache_host}}"
    "TWEET_CACHE_PORT=${TWEET_CACHE_PORT:-${cache_port}}"
  )
}

# Routing receipt — echo the effective DB/cache endpoint each measured service
# will use under the benchmark profile, so the topology can never silently
# regress (D1/D2). Reflects the same ${VAR:-default} resolution as build_env_prefix
# and the compose files, so an operator override (e.g. re-routing through
# toxiproxy for fault-injection) shows up here too.
print_routing_receipt() {
  local target="$1"
  local profile="$2"
  [[ "$profile" == "benchmark" && "$target" != "infra" ]] || return 0
  if toxiproxy_enabled; then
    echo "[routing] benchmark THROUGH toxiproxy @ $(toxiproxy_latency_ms)ms/dir (${target} stack); load gen stays direct:"
    echo "[routing]   user-service        DB=toxiproxy:${TOXIPROXY_PG_USER_PORT}  peers via toxiproxy:${TOXIPROXY_SVC_TWEET_PORT}/${TOXIPROXY_SVC_INTERACTION_PORT}"
    echo "[routing]   tweet-service       DB=toxiproxy:${TOXIPROXY_PG_TWEET_PORT}  cache=toxiproxy:${TOXIPROXY_REDIS_PORT}"
    echo "[routing]   interaction-service DB=toxiproxy:${TOXIPROXY_PG_INTERACTION_PORT}  cache=toxiproxy:${TOXIPROXY_REDIS_PORT}"
    return 0
  fi
  echo "[routing] benchmark effective endpoints (${target} stack), direct:"
  echo "[routing]   user-service        DB=${USER_DB_HOST:-user-service-db}:${USER_DB_PORT:-5432}"
  echo "[routing]   tweet-service       DB=${TWEET_DB_HOST:-tweet-service-db}:${TWEET_DB_PORT:-5432}  cache=redis:${TWEET_CACHE_PORT:-6379}"
  echo "[routing]   interaction-service DB=${INTERACTION_DB_HOST:-interaction-service-db}:${INTERACTION_DB_PORT:-5432}  cache=${INTERACTION_CACHE_HOST:-redis}:${INTERACTION_CACHE_PORT:-6379}"
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
  local project_name="${COMPOSE_PROJECT_NAME:-tweebyte}"
  local -a cmd=("docker" "compose" "--project-directory" "${REPO_ROOT}" "--project-name" "${project_name}" "${COMPOSE_ARGS[@]}" "$@")
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

down_app_services() {
  local -a services=(gateway-service user-service tweet-service interaction-service)

  run_compose stop "${services[@]}"
  run_compose rm -f "${services[@]}"
}

main() {
  if (($# == 1)); then
    case "$1" in
      --help|-h|help)
        usage
        exit 0
        ;;
    esac
  fi

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
          if [[ "$profile" == "functional-equivalence" && "$target" != "infra" ]]; then
            # FE databases are test fixtures, not durable state. Start each Cucumber
            # run from empty volumes so zero-delay cleanup ticks cannot inherit stale
            # rows from a previous run and compete with early scenarios.
            run_compose down -v --remove-orphans
            mkdir -p "${REPO_ROOT}/testing-results/functional-equivalence/jacoco/${target}"
            rm -f "${REPO_ROOT}/testing-results/functional-equivalence/jacoco/${target}"/*.exec
          fi
          # async and reactive targets ALWAYS rebuild: both backend/async/* and
          # backend/reactive/* Dockerfiles produce the same image name
          # (tweebyte-<service>:latest), so without --build a stack switch
          # silently runs the previous stack's code. Docker layer caching
          # makes the no-op rebuild cheap (~5 s) when nothing changed.
          # `infra` uses external images (postgres, redis, toxiproxy), so
          # --build there is a no-op anyway.
          local -a up_extra=()
          if [[ "$target" == "async" || "$target" == "reactive" ]]; then
            up_extra+=(--build)
          fi
          run_compose up -d --remove-orphans ${up_extra[@]+"${up_extra[@]}"} "$@"
          print_routing_receipt "$target" "$profile"
          ;;
        down)
          if [[ "$target" == "infra" ]]; then
            run_compose down --remove-orphans "$@"
          else
            # Keep shared benchmark infra alive when switching async/reactive.
            # Volumes stay intact either way; avoiding infra restarts also avoids
            # needless toxiproxy reconfiguration between paired stack runs.
            down_app_services
          fi
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
