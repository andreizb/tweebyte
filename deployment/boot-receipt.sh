#!/usr/bin/env bash
# Boot receipt — captures exactly what is running RIGHT NOW for a benchmark.
#
# Usage:
#   ./deployment/boot-receipt.sh <stack> <profile> <output-dir>
#
# Writes one directory per service plus a top-level meta.txt. The receipt is
# the answer to "what code, what image, what args, what config was loaded?"
# whenever a benchmark number disagrees with a prior run.
#
# Invariant: this script reads state, never mutates it. Safe to call any time
# the stack is up.

set -u

STACK="${1:-}"
PROFILE="${2:-}"
OUT_DIR="${3:-}"

if [[ -z "$STACK" || -z "$PROFILE" || -z "$OUT_DIR" ]]; then
  echo "Usage: $0 <async|reactive> <prod|benchmark|functional-equivalence> <output-dir>" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_PROJECT="${COMPOSE_PROJECT_NAME:-tweebyte}"

SERVICES=(gateway-service user-service tweet-service interaction-service)
service_port() {
  case "$1" in
    gateway-service) echo 8080 ;;
    user-service) echo 9091 ;;
    tweet-service) echo 9092 ;;
    interaction-service) echo 9093 ;;
  esac
}

mkdir -p "$OUT_DIR"
echo "[boot-receipt] stack=$STACK profile=$PROFILE out=$OUT_DIR"

# ---- top-level meta ----
{
  echo "timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "stack=$STACK"
  echo "profile=$PROFILE"
  echo "compose_project=$COMPOSE_PROJECT"
  echo "host=$(hostname)"
  echo "host_uptime=$(uptime | tr -s ' ' | sed 's/^ //')"
  echo "docker_version=$(docker --version 2>&1)"
  echo "compose_version=$(docker compose version 2>&1 | head -1)"
  echo "git_sha=$(cd "$REPO_ROOT" && git rev-parse HEAD 2>&1)"
  echo "git_branch=$(cd "$REPO_ROOT" && git rev-parse --abbrev-ref HEAD 2>&1)"
  echo "git_dirty_lines=$(cd "$REPO_ROOT" && git status --porcelain 2>&1 | wc -l | tr -d ' ')"
} > "$OUT_DIR/meta.txt"

(cd "$REPO_ROOT" && git status --porcelain) > "$OUT_DIR/git-status.txt" 2>&1
(cd "$REPO_ROOT" && git diff --stat HEAD) > "$OUT_DIR/git-diff-stat.txt" 2>&1

# ---- per-service capture ----
for svc in "${SERVICES[@]}"; do
  svc_dir="$OUT_DIR/$svc"
  mkdir -p "$svc_dir"
  container="${COMPOSE_PROJECT}-${svc}-1"

  echo "[boot-receipt]  svc=$svc container=$container"

  if ! docker inspect "$container" >/dev/null 2>&1; then
    echo "container_not_found=$container" > "$svc_dir/MISSING.txt"
    continue
  fi

  # Image identity (the foot-gun fix verification)
  docker inspect \
    --format '{{json .Image}}' \
    "$container" 2>&1 | tr -d '"' > "$svc_dir/image-id.txt"
  docker inspect \
    --format 'image_ref={{.Config.Image}}{{"\n"}}created={{.Created}}{{"\n"}}started={{.State.StartedAt}}{{"\n"}}status={{.State.Status}}' \
    "$container" 2>&1 > "$svc_dir/container-state.txt"

  # Exact JVM cmdline (proves -Xmx4g and nothing else)
  docker exec "$container" sh -c 'cat /proc/1/cmdline | tr "\0" " "; echo' 2>&1 \
    > "$svc_dir/java-cmdline.txt"

  # Full container env (every var the JVM sees — toxiproxy hostnames, SPRING_PROFILES_ACTIVE,
  # APP_CONCURRENCY_*, JAVA_TOOL_OPTIONS, …)
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container" 2>&1 \
    > "$svc_dir/container-env.txt"

  # Source application.properties + application-benchmark.properties (snapshotted from
  # host — these are exactly what was baked into the image because `up --build` just ran)
  cp "${REPO_ROOT}/${STACK}/${svc}/src/main/resources/application.properties" \
    "$svc_dir/application.properties" 2>/dev/null || \
    echo "missing" > "$svc_dir/application.properties.MISSING"
  cp "${REPO_ROOT}/${STACK}/${svc}/src/main/resources/application-benchmark.properties" \
    "$svc_dir/application-benchmark.properties" 2>/dev/null || \
    echo "(no benchmark profile)" > "$svc_dir/application-benchmark.properties"

  # Actuator: health is exposed everywhere; info usually too. configprops/env
  # require explicit exposure (we'll see if they're there).
  port="$(service_port "$svc")"
  curl -fs --max-time 3 "http://localhost:${port}/actuator/health" \
    > "$svc_dir/actuator-health.json" 2>&1 || \
    echo "actuator-health unreachable on :${port}" > "$svc_dir/actuator-health.json"
  curl -fs --max-time 3 "http://localhost:${port}/actuator/info" \
    > "$svc_dir/actuator-info.json" 2>&1 || true
done

# ---- infra state ----
infra_dir="$OUT_DIR/infrastructure"
mkdir -p "$infra_dir"

for db in user-service-db tweet-service-db interaction-service-db; do
  c="${COMPOSE_PROJECT}-${db}-1"
  if docker inspect "$c" >/dev/null 2>&1; then
    docker exec "$c" psql -U postgres -d postgres -t -A -c \
      "SELECT name||'='||setting FROM pg_settings WHERE name IN ('shared_buffers','work_mem','effective_cache_size','maintenance_work_mem','max_connections') ORDER BY name;" \
      > "$infra_dir/${db}-settings.txt" 2>&1
    docker exec "$c" psql -U postgres -d postgres -t -A -c \
      "SELECT count(*)||' active connections' FROM pg_stat_activity WHERE state='active';" \
      >> "$infra_dir/${db}-settings.txt" 2>&1
  fi
done

if docker inspect "${COMPOSE_PROJECT}-redis-1" >/dev/null 2>&1; then
  docker exec "${COMPOSE_PROJECT}-redis-1" redis-cli dbsize \
    > "$infra_dir/redis-dbsize.txt" 2>&1
  docker exec "${COMPOSE_PROJECT}-redis-1" redis-cli info memory \
    > "$infra_dir/redis-memory.txt" 2>&1
fi

# Toxiproxy state (benchmark profile only — it's the only path that uses it)
if [[ "$PROFILE" == "benchmark" ]]; then
  curl -fs --max-time 3 "http://localhost:8474/proxies" \
    > "$infra_dir/toxiproxy-proxies.json" 2>&1 || \
    echo "toxiproxy admin unreachable on :8474" > "$infra_dir/toxiproxy-proxies.json"
fi

# ---- self-check: print a summary the operator will actually read ----
echo ""
echo "=== boot receipt summary ==="
echo "out: $OUT_DIR"
for svc in "${SERVICES[@]}"; do
  img=$(cat "$OUT_DIR/$svc/image-id.txt" 2>/dev/null | head -1 | cut -c1-19)
  cmdline=$(cat "$OUT_DIR/$svc/java-cmdline.txt" 2>/dev/null | tr -s ' ')
  printf '  %-22s image=%s\n' "$svc" "${img:-MISSING}"
  printf '  %-22s cmdline=%s\n' "" "${cmdline:0:120}"
done
echo "============================"
