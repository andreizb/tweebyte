#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
STATE_ROOT="${LOCAL_APP_STATE_ROOT:-${REPO_ROOT}/testing-results/runtime/local-app}"
MVN_BIN="${MVN_BIN:-mvn}"

# Shared toxiproxy port map. When BENCH_TOXIPROXY_LATENCY_MS>0 the app JVMs are
# pointed at the proxy listeners instead of the direct datastore/peer ports, so the
# benchmark runs I/O-bound (see toxiproxy-lib.sh). Default off => direct, unchanged.
# shellcheck source=deployment/toxiproxy-lib.sh
source "${SCRIPT_DIR}/../toxiproxy-lib.sh"

usage() {
  cat <<'EOF'
Usage:
  ./run.sh local up   <async|reactive> benchmark <service...|all>
  ./run.sh local down <async|reactive> benchmark <service...|all>
  ./run.sh local ps   [async|reactive] [benchmark] [service...|all]
  ./run.sh local logs <async|reactive> benchmark <service> [tail args...]

Services:
  gateway-service | user-service | tweet-service | interaction-service | all
  Short aliases gateway, user, tweet, interaction are accepted.

Topology:
  Infrastructure stays in Docker. Start it first with:
    ./run.sh runtime up infra benchmark

  Apps run as local Maven Spring Boot processes with benchmark profile,
  host-published DB/Redis ports, and localhost inter-service URLs.

Examples:
  ./run.sh local up async benchmark user-service tweet-service interaction-service
  ./run.sh local ps
  ./run.sh local down reactive benchmark all

Notes:
  - This command is benchmark-only.
  - It refuses to start if the matching app port is already listening.
  - It refuses to start if the Compose app container for that service is running.
  - State is written under testing-results/runtime/local-app/.
EOF
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

require_stack() {
  case "${1:-}" in
    async|reactive) ;;
    *) die "unsupported stack '${1:-}'. Expected async or reactive." ;;
  esac
}

require_profile() {
  [[ "${1:-}" == "benchmark" ]] || die "local app mode is benchmark-only"
}

normalize_service() {
  case "$1" in
    gateway|gateway-service) echo "gateway-service" ;;
    user|user-service) echo "user-service" ;;
    tweet|tweet-service) echo "tweet-service" ;;
    interaction|interaction-service) echo "interaction-service" ;;
    all) echo "all" ;;
    *) die "unsupported service '$1'" ;;
  esac
}

expand_services() {
  if (($# == 0)); then
    die "at least one service or 'all' is required"
  fi

  local raw svc
  for raw in "$@"; do
    svc="$(normalize_service "$raw")"
    if [[ "$svc" == "all" ]]; then
      echo "user-service"
      echo "interaction-service"
      echo "tweet-service"
      echo "gateway-service"
      return 0
    fi
    echo "$svc"
  done
}

service_port() {
  case "$1" in
    gateway-service) echo "8080" ;;
    user-service) echo "9091" ;;
    tweet-service) echo "9092" ;;
    interaction-service) echo "9093" ;;
    *) die "unknown service '$1'" ;;
  esac
}

service_java_var() {
  case "$1" in
    gateway-service) echo "GATEWAY_JAVA_TOOL_OPTIONS" ;;
    user-service) echo "USER_JAVA_TOOL_OPTIONS" ;;
    tweet-service) echo "TWEET_JAVA_TOOL_OPTIONS" ;;
    interaction-service) echo "INTERACTION_JAVA_TOOL_OPTIONS" ;;
    *) die "unknown service '$1'" ;;
  esac
}

service_dir() {
  local stack="$1" service="$2"
  echo "${REPO_ROOT}/backend/${stack}/${service}"
}

service_state_dir() {
  local stack="$1" service="$2"
  echo "${STATE_ROOT}/${stack}/${service}"
}

pid_file() {
  local stack="$1" service="$2"
  echo "$(service_state_dir "$stack" "$service")/app.pid"
}

log_file() {
  local stack="$1" service="$2"
  echo "$(service_state_dir "$stack" "$service")/app.log"
}

receipt_file() {
  local stack="$1" service="$2"
  echo "$(service_state_dir "$stack" "$service")/receipt.env"
}

is_pid_alive() {
  local pid="$1"
  [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1
}

existing_pid() {
  local file="$1"
  [[ -f "$file" ]] || return 1
  local pid
  pid="$(cat "$file" 2>/dev/null || true)"
  is_pid_alive "$pid" || return 1
  printf '%s\n' "$pid"
}

port_listening() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
  else
    return 1
  fi
}

container_running_for_service() {
  local service="$1"
  command -v docker >/dev/null 2>&1 || return 1
  docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "${COMPOSE_PROJECT_NAME:-tweebyte}-${service}-1"
}

default_java_options() {
  local stack="$1" service="$2" state_dir="$3"
  local var
  var="$(service_java_var "$service")"
  if [[ -n "${!var:-}" ]]; then
    printf '%s\n' "${!var}"
  elif [[ "$service" == "gateway-service" ]]; then
    printf '%s\n' "-Xmx1g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${state_dir}/diag/"
  else
    # The benchmark profile disables DB SSL (sslMode=DISABLE), but r2dbc-postgresql still
    # validates the sslRootCert path when the option is present, and the app config points it
    # at the container mount (/etc/tweebyte/tls/ca.crt), which is absent on a host JVM. Override
    # it (exact-key -D, covering both r2dbc and jdbc) to the host cert so the option resolves.
    local host_ca="${REPO_ROOT}/deployment/docker-compose/tls/ca.crt"
    printf '%s\n' "-Xmx4g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${state_dir}/diag/ -Dspring.r2dbc.properties.sslRootCert=${host_ca} -Dspring.datasource.hikari.data-source-properties.sslrootcert=${host_ca}"
  fi
}

calibration_json_default() {
  local path="${REPO_ROOT}/testing/performance/k6/workloads/ai-stream-summarize/calibration/calibration.json"
  if [[ -f "$path" ]]; then
    printf '%s\n' "$path"
  else
    printf '%s\n' ""
  fi
}

build_env() {
  local stack="$1" service="$2" state_dir="$3"
  local port java_opts tls_dir calibration_json
  port="$(service_port "$service")"
  java_opts="$(default_java_options "$stack" "$service" "$state_dir")"
  tls_dir="${REPO_ROOT}/deployment/docker-compose/tls"
  calibration_json="${AI_MOCK_CALIBRATION_JSON:-$(calibration_json_default)}"

  # Datastore/peer endpoint defaults. With the toxiproxy I/O path on, the app JVMs
  # default to the proxy listeners (host stays localhost); off, to the direct host
  # ports. Explicit LOCAL_*/_SERVICE_URL overrides still win in either mode. The
  # load generators and seeders never read these — they always hit the real ports.
  local def_user_db_port=54321 def_tweet_db_port=54322 def_interaction_db_port=54323
  local def_redis_port=63790
  local def_user_url="http://localhost:9091/"
  local def_tweet_url="http://localhost:9092/"
  local def_interaction_url="http://localhost:9093/"
  if toxiproxy_enabled; then
    def_user_db_port="${TOXIPROXY_PG_USER_PORT}"
    def_tweet_db_port="${TOXIPROXY_PG_TWEET_PORT}"
    def_interaction_db_port="${TOXIPROXY_PG_INTERACTION_PORT}"
    def_redis_port="${TOXIPROXY_REDIS_PORT}"
    def_user_url="http://localhost:${TOXIPROXY_SVC_USER_PORT}/"
    def_tweet_url="http://localhost:${TOXIPROXY_SVC_TWEET_PORT}/"
    def_interaction_url="http://localhost:${TOXIPROXY_SVC_INTERACTION_PORT}/"
  fi

  LOCAL_ENV=(
    "SERVER_PORT=${port}"
    "SPRING_PROFILES_ACTIVE=benchmark"
    "VAULT_URI=${VAULT_URI:-http://localhost:8200}"
    "VAULT_TOKEN=${VAULT_TOKEN:-root}"
    "KEYCLOAK_BASE_URI=${KEYCLOAK_BASE_URI:-http://localhost:8090}"
    "USER_SERVICE_URL=${USER_SERVICE_URL:-${def_user_url}}"
    "TWEET_SERVICE_URL=${TWEET_SERVICE_URL:-${def_tweet_url}}"
    "INTERACTION_SERVICE_URL=${INTERACTION_SERVICE_URL:-${def_interaction_url}}"
    "JAVA_TOOL_OPTIONS=${java_opts}"
    "SPRING_SSL_BUNDLE_JKS_TWEEBYTE_TRUSTSTORE_LOCATION=file:${tls_dir}/truststore.p12"
  )

  case "$service" in
    gateway-service)
      LOCAL_ENV+=(
        "CACHE_HOST=${LOCAL_CACHE_HOST:-localhost}"
        "CACHE_PORT=${LOCAL_CACHE_PORT:-${def_redis_port}}"
        "SPRING_SSL_BUNDLE_JKS_TWEEBYTE_KEYSTORE_LOCATION=file:${tls_dir}/gateway-service.p12"
      )
      ;;
    user-service)
      LOCAL_ENV+=(
        "DB_HOST=${LOCAL_USER_DB_HOST:-localhost}"
        "DB_PORT=${LOCAL_USER_DB_PORT:-${def_user_db_port}}"
        "SPRING_SSL_BUNDLE_JKS_TWEEBYTE_KEYSTORE_LOCATION=file:${tls_dir}/user-service.p12"
      )
      ;;
    tweet-service)
      LOCAL_ENV+=(
        "DB_HOST=${LOCAL_TWEET_DB_HOST:-localhost}"
        "DB_PORT=${LOCAL_TWEET_DB_PORT:-${def_tweet_db_port}}"
        "CACHE_HOST=${LOCAL_TWEET_CACHE_HOST:-localhost}"
        "CACHE_PORT=${LOCAL_TWEET_CACHE_PORT:-${def_redis_port}}"
        "AI_BACKEND=${AI_BACKEND:-mock}"
        "LIVE_LLM_BASE_URL=${LIVE_LLM_BASE_URL:-http://localhost:1234}"
        "LIVE_LLM_API_KEY=${LIVE_LLM_API_KEY:-live-llm}"
        "LIVE_LLM_MODEL=${LIVE_LLM_MODEL:-qwen3.5-4b-mlx}"
        "LIVE_LLM_TEMPERATURE=${LIVE_LLM_TEMPERATURE:-0.7}"
        "LIVE_LLM_MAX_TOKENS=${LIVE_LLM_MAX_TOKENS:-200}"
        "AI_MOCK_TTFT_MEAN_MS=${AI_MOCK_TTFT_MEAN_MS:-250}"
        "AI_MOCK_TTFT_LOG_SIGMA=${AI_MOCK_TTFT_LOG_SIGMA:-0.4}"
        "AI_MOCK_ITL_MEAN_MS=${AI_MOCK_ITL_MEAN_MS:-40}"
        "AI_MOCK_ITL_GAMMA_SHAPE=${AI_MOCK_ITL_GAMMA_SHAPE:-2.5}"
        "AI_MOCK_ITL_P_BURST=${AI_MOCK_ITL_P_BURST:-0.0}"
        "AI_MOCK_TOKENS_PER_RESPONSE=${AI_MOCK_TOKENS_PER_RESPONSE:-150}"
        "AI_MOCK_CALIBRATION_JSON=${calibration_json}"
        "SPRING_SSL_BUNDLE_JKS_TWEEBYTE_KEYSTORE_LOCATION=file:${tls_dir}/tweet-service.p12"
      )
      ;;
    interaction-service)
      LOCAL_ENV+=(
        "DB_HOST=${LOCAL_INTERACTION_DB_HOST:-localhost}"
        "DB_PORT=${LOCAL_INTERACTION_DB_PORT:-${def_interaction_db_port}}"
        "CACHE_HOST=${LOCAL_INTERACTION_CACHE_HOST:-localhost}"
        "CACHE_PORT=${LOCAL_INTERACTION_CACHE_PORT:-${def_redis_port}}"
        "SPRING_SSL_BUNDLE_JKS_TWEEBYTE_KEYSTORE_LOCATION=file:${tls_dir}/interaction-service.p12"
      )
      ;;
  esac
}

write_receipt() {
  local stack="$1" service="$2" state_dir="$3" log="$4" pid="$5"
  local receipt
  receipt="$(receipt_file "$stack" "$service")"
  {
    echo "timestamp=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo "topology=local-app"
    echo "stack=${stack}"
    echo "service=${service}"
    echo "pid=${pid}"
    echo "port=$(service_port "$service")"
    echo "module=$(service_dir "$stack" "$service")"
    echo "log=${log}"
    echo "maven=${MVN_BIN}"
    echo "java_version=$({ java -version; } 2>&1 | head -1)"
    printf '%s\n' "${LOCAL_ENV[@]}" | sort
  } > "$receipt"
}

start_service() {
  local stack="$1" service="$2"
  local dir state_dir pidfile log pid port
  dir="$(service_dir "$stack" "$service")"
  [[ -d "$dir" ]] || die "service module not found: $dir"

  state_dir="$(service_state_dir "$stack" "$service")"
  mkdir -p "${state_dir}/diag"
  pidfile="$(pid_file "$stack" "$service")"
  log="$(log_file "$stack" "$service")"
  port="$(service_port "$service")"

  if pid="$(existing_pid "$pidfile")"; then
    die "${stack}/${service} already has local pid ${pid} (${pidfile})"
  fi
  if container_running_for_service "$service"; then
    die "container ${COMPOSE_PROJECT_NAME:-tweebyte}-${service}-1 is running; stop it before local-app start"
  fi
  if port_listening "$port"; then
    die "port ${port} is already listening; refusing to start ${stack}/${service}"
  fi

  build_env "$stack" "$service" "$state_dir"
  : > "$log"
  {
    echo "[$(date)] starting local-app ${stack}/${service}"
    echo "[$(date)] module=${dir}"
  } >> "$log"

  (
    cd "$dir"
    exec env "${LOCAL_ENV[@]}" "$MVN_BIN" -DskipTests spring-boot:run
  ) >> "$log" 2>&1 &
  pid=$!
  echo "$pid" > "$pidfile"

  sleep 2
  if ! is_pid_alive "$pid"; then
    rm -f "$pidfile"
    tail -80 "$log" >&2 || true
    die "${stack}/${service} exited during startup"
  fi

  write_receipt "$stack" "$service" "$state_dir" "$log" "$pid"
  echo "started local-app ${stack}/${service} pid=${pid} port=${port}"
  echo "  log: ${log}"
  echo "  receipt: $(receipt_file "$stack" "$service")"
}

stop_service() {
  local stack="$1" service="$2" pidfile pid
  pidfile="$(pid_file "$stack" "$service")"
  if ! pid="$(existing_pid "$pidfile")"; then
    rm -f "$pidfile"
    echo "not running local-app ${stack}/${service}"
    return 0
  fi

  echo "stopping local-app ${stack}/${service} pid=${pid}"
  kill "$pid" >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do
    if ! is_pid_alive "$pid"; then
      rm -f "$pidfile"
      return 0
    fi
    sleep 1
  done
  echo "pid ${pid} did not exit after SIGTERM; sending SIGKILL"
  kill -9 "$pid" >/dev/null 2>&1 || true
  rm -f "$pidfile"
}

print_service_status() {
  local stack="$1" service="$2" pidfile pid status
  pidfile="$(pid_file "$stack" "$service")"
  if pid="$(existing_pid "$pidfile")"; then
    status="RUNNING"
  else
    pid="-"
    status="STOPPED"
  fi
  printf '%-8s %-20s %-8s port=%-5s pid=%-8s log=%s\n' \
    "$stack" "$service" "$status" "$(service_port "$service")" "$pid" "$(log_file "$stack" "$service")"
}

local_logs() {
  local stack="$1" profile="$2" service="$3"
  shift 3
  require_stack "$stack"
  require_profile "$profile"
  service="$(normalize_service "$service")"
  [[ "$service" != "all" ]] || die "logs requires one concrete service"
  local log
  log="$(log_file "$stack" "$service")"
  [[ -f "$log" ]] || die "log file not found: $log"
  if (($# == 0)); then
    tail -f "$log"
  else
    tail "$@" "$log"
  fi
}

main() {
  if (($# == 0)); then
    usage
    exit 1
  fi

  local action="$1"
  shift

  case "$action" in
    up|down)
      (($# >= 3)) || { usage; exit 1; }
      local stack="$1" profile="$2"
      shift 2
      require_stack "$stack"
      require_profile "$profile"

      services=()
      while IFS= read -r line; do services+=("$line"); done < <(expand_services "$@")
      if [[ "$action" == "down" ]]; then
        local reversed=()
        local i
        for (( i=${#services[@]}-1; i>=0; i-- )); do
          reversed+=("${services[$i]}")
        done
        services=("${reversed[@]}")
      fi

      local service
      for service in "${services[@]}"; do
        if [[ "$action" == "up" ]]; then
          start_service "$stack" "$service"
        else
          stop_service "$stack" "$service"
        fi
      done
      ;;
    ps)
      local stacks=(async reactive)
      local services=(user-service interaction-service tweet-service gateway-service)
      if (($# > 0)); then
        require_stack "$1"
        stacks=("$1")
        shift
        if (($# > 0 && "$1" == "benchmark")); then
          shift
        fi
      fi
      if (($# > 0)); then
        services=()
        while IFS= read -r line; do services+=("$line"); done < <(expand_services "$@")
      fi
      local stack service
      for stack in "${stacks[@]}"; do
        for service in "${services[@]}"; do
          print_service_status "$stack" "$service"
        done
      done
      ;;
    logs)
      (($# >= 3)) || { usage; exit 1; }
      local_logs "$@"
      ;;
    --help|-h|help)
      usage
      ;;
    *)
      die "unsupported local action '$action'"
      ;;
  esac
}

main "$@"
