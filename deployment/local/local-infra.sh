#!/usr/bin/env bash
set -euo pipefail

# PostgreSQL 18 aborts startup if the postmaster becomes multithreaded (fork-unsafe),
# and on macOS an unset/empty runtime locale makes libsystem spawn a thread during
# startup — the server dies with "postmaster became multithreaded during startup"
# (HINT: set LC_ALL to a valid locale). The clusters below are initdb'd with
# --locale=C, so pin the process locale to C to match; this affects only collation
# byte-ordering, never the UTF8 data encoding, and leaves Redis/Vault unaffected.
export LC_ALL=C LANG=C

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Shared toxiproxy port map + spec builder. native-local puts a host toxiproxy-server
# on the benchmark I/O path only when BENCH_TOXIPROXY_LATENCY_MS>0 (see toxiproxy-lib.sh).
# shellcheck source=deployment/toxiproxy-lib.sh
source "${SCRIPT_DIR}/../toxiproxy-lib.sh"
TOXIPROXY_CONFIG_SCRIPT="${REPO_ROOT}/deployment/docker-compose/toxiproxy-config.sh"

STATE_ROOT="${LOCAL_INFRA_STATE_ROOT:-${REPO_ROOT}/testing-results/runtime/local-infra}"
ACTIVE_LINK="${STATE_ROOT}/active"
KEEP_STATE="${LOCAL_INFRA_KEEP_STATE:-0}"
PG_MAX_CONNECTIONS="${LOCAL_INFRA_PG_MAX_CONNECTIONS:-300}"
PG_PASSWORD="${LOCAL_INFRA_PG_PASSWORD:-postgres}"
REDIS_PORT="${LOCAL_INFRA_REDIS_PORT:-63790}"
# Host-native dev Vault. 8200 is what the app's spring.cloud.vault.uri defaults to
# (and what local-app.sh exports as VAULT_URI/VAULT_TOKEN), so the host JVMs reach it
# with no config change — the only difference from Docker is that this is a host
# process instead of a container.
VAULT_PORT="${LOCAL_INFRA_VAULT_PORT:-8200}"
VAULT_DEV_TOKEN="${LOCAL_INFRA_VAULT_TOKEN:-root}"
VAULT_INIT_SCRIPT="${REPO_ROOT}/deployment/docker-compose/vault-init.sh"

DB_SPECS=(
  "user:54321:user_service_db"
  "tweet:54322:tweet_service_db"
  "interaction:54323:interaction_service_db"
)

usage() {
  cat <<'EOF'
Usage:
  deployment/local/local-infra.sh up
  deployment/local/local-infra.sh down
  deployment/local/local-infra.sh ps
  deployment/local/local-infra.sh logs [lines]
  deployment/local/local-infra.sh env

Starts the native hot-path benchmark infrastructure only:
  - local Postgres clusters on 127.0.0.1:54321/54322/54323
  - local Redis on 127.0.0.1:63790
  - local dev Vault on 127.0.0.1:8200 (in-memory, seeded with secret/tweebyte)
  - host toxiproxy-server (admin 127.0.0.1:8474) ONLY when BENCH_TOXIPROXY_LATENCY_MS>0:
    puts latency proxies in front of the 3 DBs + Redis + the 3 inter-service hops to
    drive the bench I/O-bound. Load generators + seeders always connect direct.

This is intentionally separate from ./run.sh while the benchmark wrappers still
target Docker-managed infrastructure. It is inert until explicitly invoked.

Important:
  - The runner refuses to start if any benchmark datastore port is already bound.
  - Databases are created empty; application Flyway migrations must create schema.
  - Vault runs in dev mode (ephemeral, plaintext HTTP) so native-local needs no
    Docker at all; Keycloak / observability are still not managed here.
  - 'down' removes the temp state by default. Set LOCAL_INFRA_KEEP_STATE=1 to keep logs/data.
EOF
}

die() {
  echo "local-infra: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

check_dependencies() {
  require_cmd initdb
  require_cmd pg_ctl
  require_cmd pg_isready
  require_cmd createdb
  require_cmd psql
  require_cmd postgres
  require_cmd redis-server
  require_cmd redis-cli
  require_cmd vault
  if toxiproxy_enabled; then
    require_cmd toxiproxy-server
    require_cmd curl
  fi
}

port_listening() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
  else
    nc -z 127.0.0.1 "${port}" >/dev/null 2>&1
  fi
}

require_ports_free() {
  local spec name port db
  for spec in "${DB_SPECS[@]}"; do
    IFS=: read -r name port db <<<"${spec}"
    if port_listening "${port}"; then
      die "port ${port} is already bound (${name} DB). Stop Docker/local infra before native up."
    fi
  done
  if port_listening "${REDIS_PORT}"; then
    die "port ${REDIS_PORT} is already bound (Redis). Stop Docker/local infra before native up."
  fi
  if port_listening "${VAULT_PORT}"; then
    die "port ${VAULT_PORT} is already bound (Vault). Stop Docker/local infra before native up."
  fi
  if toxiproxy_enabled; then
    local tport
    for tport in $(toxiproxy_listen_ports); do
      if port_listening "${tport}"; then
        die "port ${tport} is already bound (toxiproxy). Stop Docker/local infra before native up."
      fi
    done
  fi
}

pid_alive() {
  local pid="$1"
  [ -n "${pid}" ] && kill -0 "${pid}" >/dev/null 2>&1
}

active_run_dir() {
  [ -L "${ACTIVE_LINK}" ] || return 1
  readlink "${ACTIVE_LINK}"
}

run_has_live_pids() {
  local run_dir="$1"
  local pidfile pid
  [ -d "${run_dir}" ] || return 1
  for pidfile in "${run_dir}"/pids/*.pid; do
    [ -f "${pidfile}" ] || continue
    pid="$(cat "${pidfile}" 2>/dev/null || true)"
    if pid_alive "${pid}"; then
      return 0
    fi
  done
  return 1
}

refuse_live_active_run() {
  local run_dir
  if [ -e "${ACTIVE_LINK}" ] && [ ! -L "${ACTIVE_LINK}" ]; then
    die "${ACTIVE_LINK} exists and is not a symlink"
  fi
  if run_dir="$(active_run_dir)"; then
    if run_has_live_pids "${run_dir}"; then
      die "native local infra already appears to be running at ${run_dir}; use 'down' first"
    fi
    echo "local-infra: removing stale active link -> ${run_dir}"
    rm -f "${ACTIVE_LINK}"
  fi
}

start_postgres() {
  local run_dir="$1"
  local name="$2"
  local port="$3"
  local db="$4"
  local data_dir="${run_dir}/postgres/${name}"
  local socket_dir
  socket_dir="$(cat "${run_dir}/sockets_root")/${name}"
  local log_file="${run_dir}/logs/postgres-${name}.log"

  mkdir -p "${data_dir}" "${socket_dir}"
  initdb -D "${data_dir}" --username=postgres --auth=trust --encoding=UTF8 --locale=C >>"${log_file}" 2>&1

  {
    echo ""
    echo "# Tweebyte native benchmark override"
    echo "listen_addresses = '127.0.0.1'"
    echo "port = ${port}"
    echo "max_connections = ${PG_MAX_CONNECTIONS}"
    echo "unix_socket_directories = '${socket_dir}'"
  } >>"${data_dir}/postgresql.conf"

  pg_ctl -D "${data_dir}" -l "${log_file}" start -w >>"${log_file}" 2>&1
  # Feed the ALTER USER via stdin, not -c: psql only performs :'var' interpolation in
  # file/stdin mode, so a -c "... :'pass'" string is sent literally and the server
  # rejects it with "syntax error at or near ':'". Stdin keeps the safe quoted-literal
  # interpolation for arbitrary passwords.
  printf "ALTER USER postgres PASSWORD :'pass';\n" \
    | psql -h 127.0.0.1 -p "${port}" -U postgres -d postgres \
        -v ON_ERROR_STOP=1 -v pass="${PG_PASSWORD}" >>"${log_file}" 2>&1
  createdb -h 127.0.0.1 -p "${port}" -U postgres "${db}" >>"${log_file}" 2>&1
  pg_isready -h 127.0.0.1 -p "${port}" -U postgres -d "${db}" >>"${log_file}" 2>&1

  head -n 1 "${data_dir}/postmaster.pid" >"${run_dir}/pids/postgres-${name}.pid"
}

start_redis() {
  local run_dir="$1"
  local data_dir="${run_dir}/redis"
  local log_file="${run_dir}/logs/redis.log"
  local pid_file="${run_dir}/pids/redis.pid"

  mkdir -p "${data_dir}"
  redis-server \
    --bind 127.0.0.1 \
    --port "${REDIS_PORT}" \
    --dir "${data_dir}" \
    --save "" \
    --appendonly no \
    --daemonize yes \
    --pidfile "${pid_file}" \
    --logfile "${log_file}"
  redis-cli -h 127.0.0.1 -p "${REDIS_PORT}" PING >>"${log_file}" 2>&1
}

start_vault() {
  local run_dir="$1"
  local log_file="${run_dir}/logs/vault.log"
  local pid_file="${run_dir}/pids/vault.pid"
  # Dev-mode server: in-memory, auto-unsealed, KV v2 mounted at secret/, plaintext
  # HTTP (no TLS) — the exact same shape as the Docker dev Vault, just a host process.
  # It holds nothing on disk and dies on 'down', so it is temporary by construction.
  VAULT_ADDR="http://127.0.0.1:${VAULT_PORT}" nohup vault server -dev \
    -dev-root-token-id="${VAULT_DEV_TOKEN}" \
    -dev-listen-address="127.0.0.1:${VAULT_PORT}" \
    >>"${log_file}" 2>&1 &
  echo "$!" >"${pid_file}"
}

seed_vault() {
  local run_dir="$1"
  local log_file="${run_dir}/logs/vault-init.log"
  # Reuse the canonical KV seeder (single source of the secret keys) so native-local
  # and Docker write byte-identical secret/tweebyte. Provide the same values Docker's
  # vault-init service does: the gitignored .env if present, then the dev defaults.
  if [ -f "${REPO_ROOT}/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "${REPO_ROOT}/.env"
    set +a
  fi
  VAULT_ADDR="http://127.0.0.1:${VAULT_PORT}" \
  VAULT_TOKEN="${VAULT_DEV_TOKEN}" \
  TWEEBYTE_DB_PASSWORD="${TWEEBYTE_DB_PASSWORD:-${PG_PASSWORD}}" \
  TWEEBYTE_ACTUATOR_PASSWORD="${TWEEBYTE_ACTUATOR_PASSWORD:-changeme-actuator}" \
  TWEEBYTE_TLS_KEYSTORE_PASSWORD="${TWEEBYTE_TLS_KEYSTORE_PASSWORD:-changeit}" \
  TWEEBYTE_TLS_TRUSTSTORE_PASSWORD="${TWEEBYTE_TLS_TRUSTSTORE_PASSWORD:-changeit}" \
  TWEEBYTE_KC_CLIENT_SECRET="${TWEEBYTE_KC_CLIENT_SECRET:-tweebyte-app-secret}" \
  TWEEBYTE_LLM_API_KEY="${TWEEBYTE_LLM_API_KEY:-live-llm}" \
    sh "${VAULT_INIT_SCRIPT}" >>"${log_file}" 2>&1
}

start_toxiproxy() {
  local run_dir="$1"
  local log_file="${run_dir}/logs/toxiproxy.log"
  local pid_file="${run_dir}/pids/toxiproxy.pid"
  local admin_url="http://127.0.0.1:${TOXIPROXY_ADMIN_PORT}"

  # Host toxiproxy-server: the native-local counterpart of the Docker toxiproxy
  # container. Admin API on loopback; the 7 proxy listeners are created by the
  # shared config script below against host-process upstreams.
  nohup toxiproxy-server -host=127.0.0.1 -port="${TOXIPROXY_ADMIN_PORT}" \
    >>"${log_file}" 2>&1 &
  echo "$!" >"${pid_file}"

  local up=0 i
  for i in $(seq 1 50); do
    if curl -fsS "${admin_url}/version" >/dev/null 2>&1; then up=1; break; fi
    sleep 0.2
  done
  [ "${up}" = "1" ] || die "toxiproxy-server admin API did not come up on ${admin_url}"

  # Same init script the Docker toxiproxy-config container runs; native upstreams +
  # the swept latency come from toxiproxy-lib.sh.
  TOXIPROXY_URL="${admin_url}" \
  TOXIPROXY_PROXIES="$(toxiproxy_build_spec native)" \
    sh "${TOXIPROXY_CONFIG_SCRIPT}" >>"${log_file}" 2>&1
}

stop_toxiproxy() {
  local run_dir="$1"
  local pidfile="${run_dir}/pids/toxiproxy.pid"
  local pid=""
  [ -f "${pidfile}" ] && pid="$(cat "${pidfile}" 2>/dev/null || true)"
  # toxiproxy-server holds nothing on disk; a plain kill is the clean teardown.
  if pid_alive "${pid}"; then
    kill "${pid}" >/dev/null 2>&1 || true
  fi
}

write_receipt() {
  local run_dir="$1"
  local receipt="${run_dir}/receipt.env"
  local spec name port db

  {
    echo "benchmark_infra_topology=native-local"
    echo "state_dir=${run_dir}"
    echo "postgres_version=$(postgres --version)"
    echo "redis_version=$(redis-server --version | awk '{print $3}')"
    echo "postgres_user=postgres"
    echo "postgres_password=${PG_PASSWORD}"
    echo "postgres_auth=trust"
    echo "postgres_max_connections=${PG_MAX_CONNECTIONS}"
    for spec in "${DB_SPECS[@]}"; do
      IFS=: read -r name port db <<<"${spec}"
      echo "${name}_db_host=localhost"
      echo "${name}_db_port=${port}"
      echo "${name}_db_name=${db}"
    done
    echo "redis_host=localhost"
    echo "redis_port=${REDIS_PORT}"
    if toxiproxy_enabled; then
      echo "toxiproxy=on"
      echo "toxiproxy_admin=http://localhost:${TOXIPROXY_ADMIN_PORT}"
      echo "toxiproxy_latency_ms=$(toxiproxy_latency_ms)"
      echo "toxiproxy_jitter_ms=$(toxiproxy_jitter_ms)"
      echo "toxiproxy_redis_port=${TOXIPROXY_REDIS_PORT}"
      echo "toxiproxy_pg_user_port=${TOXIPROXY_PG_USER_PORT}"
      echo "toxiproxy_pg_tweet_port=${TOXIPROXY_PG_TWEET_PORT}"
      echo "toxiproxy_pg_interaction_port=${TOXIPROXY_PG_INTERACTION_PORT}"
      echo "toxiproxy_svc_user_port=${TOXIPROXY_SVC_USER_PORT}"
      echo "toxiproxy_svc_tweet_port=${TOXIPROXY_SVC_TWEET_PORT}"
      echo "toxiproxy_svc_interaction_port=${TOXIPROXY_SVC_INTERACTION_PORT}"
    else
      echo "toxiproxy=off"
    fi
    echo "vault_addr=http://localhost:${VAULT_PORT}"
    echo "vault_mode=dev"
    echo "vault_version=$(vault version | awk '{print $2}')"
    echo "secret_provider=hashicorp_vault_dev_native"
    echo "schema_owner=application_flyway_on_app_boot"
  } >"${receipt}"
}

stop_postgres() {
  local run_dir="$1"
  local spec name port db data_dir pidfile pid
  for spec in "${DB_SPECS[@]}"; do
    IFS=: read -r name port db <<<"${spec}"
    data_dir="${run_dir}/postgres/${name}"
    pidfile="${run_dir}/pids/postgres-${name}.pid"
    if [ -d "${data_dir}" ]; then
      pg_ctl -D "${data_dir}" stop -m fast -w >/dev/null 2>&1 || true
    elif [ -f "${pidfile}" ]; then
      pid="$(cat "${pidfile}" 2>/dev/null || true)"
      if pid_alive "${pid}"; then
        kill "${pid}" >/dev/null 2>&1 || true
      fi
    fi
  done
}

stop_redis() {
  local run_dir="$1"
  local pidfile="${run_dir}/pids/redis.pid"
  local pid=""
  [ -f "${pidfile}" ] && pid="$(cat "${pidfile}" 2>/dev/null || true)"
  redis-cli -h 127.0.0.1 -p "${REDIS_PORT}" SHUTDOWN NOSAVE >/dev/null 2>&1 || true
  sleep 1
  if pid_alive "${pid}"; then
    kill "${pid}" >/dev/null 2>&1 || true
  fi
}

stop_vault() {
  local run_dir="$1"
  local pidfile="${run_dir}/pids/vault.pid"
  local pid=""
  [ -f "${pidfile}" ] && pid="$(cat "${pidfile}" 2>/dev/null || true)"
  # Dev Vault is in-memory; there is nothing to flush, so a plain kill is the clean
  # teardown (no `vault` shutdown subcommand exists).
  if pid_alive "${pid}"; then
    kill "${pid}" >/dev/null 2>&1 || true
  fi
}

stop_run_dir() {
  local run_dir="$1"
  [ -d "${run_dir}" ] || return 0
  # Stop the proxy before its upstreams so in-flight proxied connections drop first.
  stop_toxiproxy "${run_dir}"
  stop_vault "${run_dir}"
  stop_redis "${run_dir}"
  stop_postgres "${run_dir}"
  # Remove the short /tmp socket root anchored in cmd_up (its path is recorded inside
  # run_dir, which the caller deletes afterwards). Postgres already unlinked its own
  # socket file on stop; this clears the now-empty per-run directory.
  if [ -f "${run_dir}/sockets_root" ]; then
    rm -rf "$(cat "${run_dir}/sockets_root" 2>/dev/null || true)" 2>/dev/null || true
  fi
}

cmd_up() {
  check_dependencies
  require_ports_free
  mkdir -p "${STATE_ROOT}"
  refuse_live_active_run

  local run_dir
  run_dir="$(mktemp -d "${STATE_ROOT}/run_$(date +%Y%m%d_%H%M%S)_XXXXXX")"
  mkdir -p "${run_dir}/logs" "${run_dir}/pids" "${run_dir}/postgres"
  # macOS caps the Unix-domain socket path (sun_path) at 103 bytes, and the run_dir
  # under the repo already spends ~100 of them — Postgres sockets cannot nest there
  # (server start fails with "Unix-domain socket path is too long"). Apps connect over
  # TCP (127.0.0.1:5432x), so the socket location is benchmark-irrelevant; it only has
  # to be valid and short. Anchor a short per-run root directly under /tmp (NOT
  # ${TMPDIR}, which on macOS is itself a long /var/folders path) and record its path
  # so teardown removes it.
  local sock_root
  sock_root="$(mktemp -d /tmp/twb-pg.XXXXXX)"
  printf '%s\n' "${sock_root}" >"${run_dir}/sockets_root"

  cleanup_failed_up() {
    local code="$?"
    echo "local-infra: up failed; stopping partial native infra at ${run_dir}" >&2
    stop_run_dir "${run_dir}" || true
    rm -rf "${run_dir}"
    exit "${code}"
  }
  trap cleanup_failed_up ERR

  local spec name port db
  for spec in "${DB_SPECS[@]}"; do
    IFS=: read -r name port db <<<"${spec}"
    echo "local-infra: starting Postgres ${db} on 127.0.0.1:${port}"
    start_postgres "${run_dir}" "${name}" "${port}" "${db}"
  done

  echo "local-infra: starting Redis on 127.0.0.1:${REDIS_PORT}"
  start_redis "${run_dir}"

  echo "local-infra: starting Vault (dev) on 127.0.0.1:${VAULT_PORT}"
  start_vault "${run_dir}"
  echo "local-infra: seeding secret/tweebyte"
  seed_vault "${run_dir}"

  if toxiproxy_enabled; then
    echo "local-infra: starting toxiproxy (admin 127.0.0.1:${TOXIPROXY_ADMIN_PORT}, latency $(toxiproxy_latency_ms)ms/dir on DBs+Redis+inter-service)"
    start_toxiproxy "${run_dir}"
  fi

  write_receipt "${run_dir}"
  ln -s "${run_dir}" "${ACTIVE_LINK}"
  trap - ERR

  echo "local-infra: native datastore + Vault infra is up"
  echo "local-infra: state ${run_dir}"
  echo "local-infra: apps boot against this with the benchmark profile (Vault at http://localhost:${VAULT_PORT})"
}

cmd_down() {
  check_dependencies
  local run_dir
  if ! run_dir="$(active_run_dir)"; then
    echo "local-infra: no active native local infra"
    return 0
  fi

  echo "local-infra: stopping ${run_dir}"
  stop_run_dir "${run_dir}"
  rm -f "${ACTIVE_LINK}"
  if [ "${KEEP_STATE}" = "1" ]; then
    echo "local-infra: kept state ${run_dir}"
  else
    rm -rf "${run_dir}"
    echo "local-infra: removed state ${run_dir}"
  fi
}

cmd_ps() {
  local run_dir pidfile pid status name
  if ! run_dir="$(active_run_dir)"; then
    echo "local-infra: no active native local infra"
    return 0
  fi
  echo "state_dir=${run_dir}"
  for pidfile in "${run_dir}"/pids/*.pid; do
    [ -f "${pidfile}" ] || continue
    pid="$(cat "${pidfile}" 2>/dev/null || true)"
    name="$(basename "${pidfile}" .pid)"
    status="stopped"
    pid_alive "${pid}" && status="running"
    echo "${name} pid=${pid} status=${status}"
  done
}

cmd_logs() {
  local lines="${1:-80}"
  local run_dir
  if ! run_dir="$(active_run_dir)"; then
    echo "local-infra: no active native local infra"
    return 0
  fi
  tail -n "${lines}" "${run_dir}"/logs/*.log
}

cmd_env() {
  # Datastore endpoints the apps should use. When toxiproxy is on the I/O path the
  # DB/cache ports become the proxy listeners (host stays localhost); otherwise they
  # are the direct host-process ports.
  local user_db_port=54321 tweet_db_port=54322 interaction_db_port=54323
  local redis_port="${REDIS_PORT}"
  if toxiproxy_enabled; then
    user_db_port="${TOXIPROXY_PG_USER_PORT}"
    tweet_db_port="${TOXIPROXY_PG_TWEET_PORT}"
    interaction_db_port="${TOXIPROXY_PG_INTERACTION_PORT}"
    redis_port="${TOXIPROXY_REDIS_PORT}"
  fi
  cat <<EOF
export DB_HOST=localhost
export CACHE_HOST=localhost
export CACHE_PORT=${redis_port}
export USER_DB_HOST=localhost
export USER_DB_PORT=${user_db_port}
export TWEET_DB_HOST=localhost
export TWEET_DB_PORT=${tweet_db_port}
export INTERACTION_DB_HOST=localhost
export INTERACTION_DB_PORT=${interaction_db_port}
export INTERACTION_CACHE_HOST=localhost
export INTERACTION_CACHE_PORT=${redis_port}
export VAULT_URI=http://localhost:${VAULT_PORT}
export VAULT_TOKEN=${VAULT_DEV_TOKEN}
EOF
  if toxiproxy_enabled; then
    cat <<EOF
export USER_SERVICE_URL=http://localhost:${TOXIPROXY_SVC_USER_PORT}/
export TWEET_SERVICE_URL=http://localhost:${TOXIPROXY_SVC_TWEET_PORT}/
export INTERACTION_SERVICE_URL=http://localhost:${TOXIPROXY_SVC_INTERACTION_PORT}/
EOF
  fi
}

main() {
  local cmd="${1:-}"
  case "${cmd}" in
    up)
      cmd_up
      ;;
    down)
      cmd_down
      ;;
    ps)
      cmd_ps
      ;;
    logs)
      shift || true
      cmd_logs "${1:-80}"
      ;;
    env)
      cmd_env
      ;;
    -h|--help|help|"")
      usage
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
}

main "$@"
