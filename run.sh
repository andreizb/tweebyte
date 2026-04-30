#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR"
INFRA_RUNNER="${REPO_ROOT}/infrastructure/run.sh"
K6_RUNNER="${REPO_ROOT}/testing/performance/k6/run_bench.sh"
JMETER_RUNNER="${REPO_ROOT}/testing/performance/jmeter/run_bench.sh"
K6_PREPARE="${REPO_ROOT}/testing/performance/k6/prepare_payload.py"
JMETER_PREPARE="${REPO_ROOT}/testing/performance/jmeter/prepare_payload.py"

usage() {
  cat <<'EOF'
Usage:
  ./run.sh runtime up <infra|async|reactive> <prod|benchmark|fe-test> [extra docker compose args...]
  ./run.sh runtime down <infra|async|reactive> <prod|benchmark|fe-test> [extra docker compose args...]
  ./run.sh runtime destroy <infra|async|reactive> <prod|benchmark|fe-test> [extra docker compose args...]
  ./run.sh runtime ps <infra|async|reactive> [extra docker compose args...]
  ./run.sh runtime logs <infra|async|reactive> [extra docker compose args...]

  ./run.sh bench <k6|jmeter> [runner args...]
  ./run.sh prepare <k6|jmeter> [payload args...]
  ./run.sh local <async|reactive> <gateway-service|user-service|tweet-service|interaction-service> <prod|benchmark> [extra mvn args...]

Profiles:
  prod      — normal runtime (no instrumentation).
  benchmark — performance-test profile (toxiproxy, GC log, JVM heap caps;
              k6 hits services directly bypassing the gateway).
  fe-test   — functional-equivalence profile (JaCoCo agent layered into each
              service JVM for the Cucumber suite under testing/equivalence/).
              Cleanly isolated from prod/benchmark — overlay only loaded when
              this profile is selected.

Compatibility aliases:
  ./run.sh up|down|destroy|ps|logs ... still work unchanged

Examples:
  ./run.sh runtime up infra benchmark
  ./run.sh bench k6 --workload following-cache --base-url http://127.0.0.1:9093 --concurrencies "1000" --runs 1 --warmup 60s --duration 3m
  ./run.sh prepare k6 following-cache --key-count 9999 --follows-per-key 10
  ./run.sh prepare jmeter user-summary --count 1000
  ./run.sh local reactive interaction-service benchmark
EOF
}

print_command() {
  printf 'Running:'
  for item in "$@"; do
    printf ' %q' "$item"
  done
  printf '\n'
}

print_local_command() {
  local service_dir="$1"
  shift
  local -a env_prefix=("${!1}")
  shift
  local -a cmd=("${!1}")

  printf 'Running: cd %q &&' "$service_dir"
  for item in "${env_prefix[@]}"; do
    printf ' %q' "$item"
  done
  for item in "${cmd[@]}"; do
    printf ' %q' "$item"
  done
  printf '\n'
}

require_stack() {
  case "$1" in
    async|reactive) ;;
    *)
      echo "Unsupported stack: $1" >&2
      usage
      exit 1
      ;;
  esac
}

require_profile() {
  case "$1" in
    prod|benchmark) ;;
    *)
      echo "Unsupported profile: $1" >&2
      usage
      exit 1
      ;;
  esac
}

require_service() {
  case "$1" in
    gateway-service|user-service|tweet-service|interaction-service) ;;
    *)
      echo "Unsupported service: $1" >&2
      usage
      exit 1
      ;;
  esac
}

require_bench_tool() {
  case "$1" in
    k6|jmeter) ;;
    *)
      echo "Unsupported benchmark tool: $1" >&2
      usage
      exit 1
      ;;
  esac
}

delegate_infra() {
  exec bash "$INFRA_RUNNER" "$@"
}

dispatch_bench() {
  if (($# < 1)); then
    usage
    exit 1
  fi

  local tool="$1"
  shift
  require_bench_tool "$tool"

  local -a cmd
  case "$tool" in
    k6) cmd=("bash" "$K6_RUNNER" "$@") ;;
    jmeter) cmd=("bash" "$JMETER_RUNNER" "$@") ;;
  esac

  print_command "${cmd[@]}"
  exec "${cmd[@]}"
}

dispatch_prepare() {
  if (($# < 1)); then
    usage
    exit 1
  fi

  local tool="$1"
  shift
  require_bench_tool "$tool"

  local -a cmd
  case "$tool" in
    k6) cmd=("python3" "$K6_PREPARE" "$@") ;;
    jmeter) cmd=("python3" "$JMETER_PREPARE" "$@") ;;
  esac

  print_command "${cmd[@]}"
  exec "${cmd[@]}"
}

service_port() {
  case "$1" in
    gateway-service) echo "8080" ;;
    user-service) echo "9091" ;;
    tweet-service) echo "9092" ;;
    interaction-service) echo "9093" ;;
  esac
}

db_port() {
  case "$1" in
    user-service) echo "54321" ;;
    tweet-service) echo "54322" ;;
    interaction-service) echo "54323" ;;
    *) echo "" ;;
  esac
}

has_benchmark_profile() {
  local stack="$1"
  local service="$2"
  [[ -f "${REPO_ROOT}/${stack}/${service}/src/main/resources/application-benchmark.properties" ]]
}

dispatch_local() {
  if (($# < 3)); then
    usage
    exit 1
  fi

  local stack="$1"
  local service="$2"
  local profile="$3"
  shift 3

  require_stack "$stack"
  require_service "$service"
  require_profile "$profile"

  local service_dir="${REPO_ROOT}/${stack}/${service}"
  if [[ ! -d "$service_dir" ]]; then
    echo "Service directory not found: $service_dir" >&2
    exit 1
  fi

  local mvn_bin="${MVN_BIN:-mvn}"
  local -a env_prefix=()
  local -a cmd=("$mvn_bin")
  local port
  local database_port

  port="$(service_port "$service")"
  env_prefix+=("SERVER_PORT=${port}")

  database_port="$(db_port "$service")"
  if [[ -n "$database_port" ]]; then
    env_prefix+=("DB_HOST=127.0.0.1" "DB_PORT=${database_port}")
  fi

  case "$service" in
    user-service)
      ;;
    tweet-service)
      env_prefix+=("CACHE_PORT=63790")
      ;;
    interaction-service)
      env_prefix+=(
        "USER_SERVICE_URL=http://127.0.0.1:9091"
        "TWEET_SERVICE_URL=http://127.0.0.1:9092"
        "CACHE_HOST=127.0.0.1"
      )
      if [[ "$profile" == "benchmark" ]]; then
        env_prefix+=(
          "CACHE_PORT=26379"
          "JAVA_TOOL_OPTIONS=-Xms4g -Xmx4g -XX:+AlwaysPreTouch"
        )
        cmd+=("-Dspring-boot.run.optimizedLaunch=false")
      else
        env_prefix+=("CACHE_PORT=63790")
      fi
      ;;
    gateway-service)
      ;;
  esac

  if [[ "$profile" == "benchmark" ]] && has_benchmark_profile "$stack" "$service"; then
    env_prefix+=("SPRING_PROFILES_ACTIVE=benchmark")
  fi

  if (($# > 0)); then
    cmd+=("$@")
  fi
  cmd+=("spring-boot:run")

  print_local_command "$service_dir" env_prefix[@] cmd[@]

  cd "$service_dir"
  env "${env_prefix[@]}" "${cmd[@]}"
}

main() {
  if (($# < 1)); then
    usage
    exit 1
  fi

  case "$1" in
    runtime)
      shift
      if (($# < 1)); then
        usage
        exit 1
      fi
      delegate_infra "$@"
      ;;
    up|down|destroy|ps|logs)
      delegate_infra "$@"
      ;;
    bench)
      shift
      dispatch_bench "$@"
      ;;
    prepare)
      shift
      dispatch_prepare "$@"
      ;;
    local)
      shift
      dispatch_local "$@"
      ;;
    --help|-h|help)
      usage
      ;;
    *)
      echo "Unsupported command: $1" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"
