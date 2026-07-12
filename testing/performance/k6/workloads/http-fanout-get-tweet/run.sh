#!/usr/bin/env bash
# Bench runner for http-fanout-get-tweet (k6) — topology-aware, host-side k6.
#
# WORKLOAD: GET /tweets/{tweetId} on tweet-service (:9092) — the cross-service-fan-out
# single-tweet READ. The backend enrichSingleTweetDto fans out PER REQUEST to
# interaction-service (likes/replies/retweets counts + the replies list) and, via the
# replies list, to user-service (a getUserSummary per reply author), plus a local
# tweet_service_db read of the tweet's hashtag + mention rows. So unlike following-cache
# (interaction-service only, a pure Redis read), this stack needs ALL THREE app services
# up — tweet-service AND its fan-out deps interaction-service + user-service — and a
# fixture that seeds real rows on every fan-out leg per tweet (see prepare.py).
#
#   ./run.sh canonical   - 14 cells x 2 runs x (60s warmup + 120s steady) ~= 1.9 hr/stack
#                          Full sweep; the reportable default. Report mean + min/max RANGE
#                          (two-run range, NOT inferential CI95). If a headline cell's two runs
#                          differ by >5%, or the effect size is itself ~5%, rerun confidence
#                          before claiming a winner.
#   ./run.sh confidence  - 14 cells x 5 runs x (60s warmup + 180s steady) ~= 4.7 hr/stack
#                          Tight CI95 for disputed/close results; the 5-rep tie-breaker.
#
# TOPOLOGY (testing/performance/lib/topology.sh) selects how the SUT runs:
#   - local-app (default): infra (postgres x3, redis, vault) in Docker; user-service +
#     interaction-service + tweet-service as host JVMs via ./run.sh local. Canonical.
#   - native-local: apps as host JVMs AND infra host-native (deployment/local/local-infra.sh)
#     on the same published ports — no Docker at all. DBs start empty, so each app's Flyway
#     creates its schema at boot and the cohort is seeded AFTER boot, per stack.
#   - all-docker: legacy; apps in containers too.
# In every topology k6 is a host-side binary (ulimit -n 65536 to survive 1000 VUs on
# macOS) driving tweet-service (the SUT) on its published port :9092, and the seeder/
# hygiene reach the three Postgres instances + Redis via host psql/redis-cli on the
# published ports (54321/54322/54323 + 63790), identical whether infra is Docker or native.
#
# Differentiated-toxiproxy aware: like every workload it inherits BENCH_TOXIPROXY_LATENCY_MS
# (off by default => direct, the measured path). When enabled, the PINNED profile adds Redis
# 1ms / Postgres 2ms / inter-service HTTP 3ms per direction. getTweet is the workload that
# feels the HTTP tier most: the single-tweet path makes FOUR inter-service hops to
# interaction-service (likes/replies/retweets counts + replies list) plus one user-service
# hop per reply, so a 3ms HTTP toxic adds ~2x(3ms) per hop and weighs heavily on the
# per-request fan-out — expect a markedly larger toxiproxy delta here than on a single-hop
# workload. Per-tier overrides: BENCH_TOXIPROXY_{REDIS,PG,HTTP}_MS.
#
# AUDIT CAVEAT (finding F8 — un-consolidated single-tweet interaction fan-out): the
# single-tweet GET path still issues the FOUR separate interaction-service GETs
# (getLikesCount/getRepliesCount/getRetweetsCount/getRepliesForTweet), whereas the page/feed
# path was consolidated onto ONE call (POST /tweets/interactions -> findInteractionsForTweets,
# the unnest+LATERAL one-shot query). getTweet therefore characterises the UN-consolidated
# fan-out. If F8 is later fixed (reuse the consolidated read for a single id), getTweet's
# character shifts (one HTTP hop + one connection-acquire instead of four), which would also
# shrink the toxiproxy-HTTP sensitivity above. Build it now; flag the dependency.
#
# Seed shape (per tweet, all fan-out legs non-empty): TWEET_COUNT tweets, each with one
# hashtag link + one mention (tweet_service_db) and REPLIES_PER_TWEET replies +
# LIKES_PER_TWEET likes + RETWEETS_PER_TWEET retweets (interaction_service_db), all
# referencing a shared USER_POOL seeded in user_service_db so the per-reply user-summary
# fan-out resolves (a missing reply author 404s -> 500s the GET). See prepare.py.
#
# Cache model — natural 60s caching (no artificial pre-warm): each stack's Redis is
# FLUSHALL'd cold, then k6's warmup phase populates the interaction-service count-caches +
# user-service summary-cache the production way (cold miss -> DB read + fan-out -> cache at
# the default 60s TTL). By the steady window the hot working set is warm with periodic 60s
# re-fills. There is no pre-warmed blob here (that is following-cache).
set -u

PROFILE="${1:-}"
case "$PROFILE" in
  canonical)
    CONCS="1 5 10 15 20 25 50 75 100 200 400 600 800 1000"
    RUNS=2; WARMUP="60s"; DURATION="120s"
    ORDER="reactive async"
    ;;
  confidence)
    CONCS="1 5 10 15 20 25 50 75 100 200 400 600 800 1000"
    RUNS=5; WARMUP="60s"; DURATION="180s"
    ORDER="reactive async"
    ;;
  *)
    echo "Usage: $0 <canonical|confidence>" >&2
    exit 1
    ;;
esac

# Optional env overrides to narrow a profile for a quick validation cell without editing it,
# e.g. CONCS_OVERRIDE=10 WARMUP_OVERRIDE=5s DURATION_OVERRIDE=15s ORDER_OVERRIDE=async.
CONCS="${CONCS_OVERRIDE:-$CONCS}"
RUNS="${RUNS_OVERRIDE:-$RUNS}"
WARMUP="${WARMUP_OVERRIDE:-$WARMUP}"
DURATION="${DURATION_OVERRIDE:-$DURATION}"
ORDER="${ORDER_OVERRIDE:-$ORDER}"

WORKLOAD="http-fanout-get-tweet"
# Cross-service-fan-out single-tweet read. 100,000 tweets, each with its hashtag + mention
# and 2 replies / 3 likes / 2 retweets (hot 10,000 keys = 10%, hot ratio 0.9). The interaction
# rows total TWEET_COUNT x (replies+likes+retweets); the replies are what the per-request
# user-summary fan-out dereferences, so they are seeded by real pool users.
TWEET_COUNT=100000
REPLIES_PER_TWEET=2
LIKES_PER_TWEET=3
RETWEETS_PER_TWEET=2
HOT_COUNT=10000
HOT_RATIO="0.9"
PATH_PREFIX="/tweets"
BENCH_URL="http://localhost:9092"
# Fan-out dependency actuators (must be UP before driving load; tweet-service calls them).
INTERACTION_URL="http://localhost:9093"
USER_URL="http://localhost:9091"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "${SCRIPT_DIR}/../../../../.." && pwd)"
cd "$REPO"

# Absolute: k6's open() resolves relative to the script dir (not CWD), so a repo-relative
# path doubles into a missing file. The seeder (--keys-out) and k6 (--keys-file) share this.
KEYS_FILE="$REPO/testing/performance/k6/workloads/http-fanout-get-tweet/payload/n${TWEET_COUNT}_r${REPLIES_PER_TWEET}/keys.txt"
export JAVA_HOME=/opt/homebrew/Cellar/sdkman-cli/5.19.0/libexec/candidates/java/21.0.7-tem
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-tweebyte}"

# Shared cleanup contract — see testing/performance/lib/cleanup.sh.
source "$REPO/testing/performance/lib/cleanup.sh"
source "$REPO/testing/performance/lib/topology.sh"
cleanup_sweep_init

LOG_DIR="/tmp/${WORKLOAD}_${PROFILE}"
mkdir -p "$LOG_DIR"
exec > >(tee -a "$LOG_DIR/run.log") 2>&1

echo "[$(date)] ===== ${WORKLOAD} ${PROFILE} START (topology=$(topology_label) + host-side k6) ====="
echo "[$(date)] config: topology=$(topology_label) concurrencies=\"$CONCS\" runs=$RUNS warmup=$WARMUP duration=$DURATION base-url=$BENCH_URL"
echo "[$(date)] payload: ${TWEET_COUNT} tweets x (${REPLIES_PER_TWEET} replies + ${LIKES_PER_TWEET} likes + ${RETWEETS_PER_TWEET} retweets) + 1 hashtag-link + 1 mention each"

# Try to raise file-descriptor limit so local k6 survives conc=800+ on macOS.
if ulimit -n 65536 2>/dev/null; then
  echo "[$(date)] ulimit -n set to $(ulimit -n)"
else
  echo "[$(date)] WARN: could not raise ulimit -n above $(ulimit -n); local k6 may flake above conc=400-800"
fi

# Effective infra-topology tag for result metadata (native vs Docker-published).
if topology_uses_native_infra; then INFRA_TOPOLOGY="native-local"; else INFRA_TOPOLOGY="docker"; fi

# Seed the self-contained cohort across the three service DBs (user pool + tweets+relations +
# replies/likes/retweets) and assert the tweet + reply row counts. DATA only — the schema is
# Flyway-owned, created by each app at boot. Reaches the three Postgres instances via host psql
# on the published ports, identical whether infra is Docker-published or native-local.
seed_and_verify() {
  echo "[$(date)] seeding cohort: user pool + tweets(+relations) + interactions (data only; schema is Flyway-owned)..."
  python3 testing/performance/k6/workloads/http-fanout-get-tweet/prepare.py \
    --tweet-count "$TWEET_COUNT" \
    --replies-per-tweet "$REPLIES_PER_TWEET" \
    --likes-per-tweet "$LIKES_PER_TWEET" \
    --retweets-per-tweet "$RETWEETS_PER_TWEET" \
    --keys-out "$KEYS_FILE" 2>&1 | tail -15

  local tweets replies
  tweets=$(PGPASSWORD=postgres psql -h 127.0.0.1 -p 54322 -U postgres -d tweet_service_db -t -A \
    -c "SELECT count(*) FROM tweets;" 2>&1)
  replies=$(PGPASSWORD=postgres psql -h 127.0.0.1 -p 54323 -U postgres -d interaction_service_db -t -A \
    -c "SELECT count(*) FROM replies;" 2>&1)
  echo "[$(date)] tweets table rows = $tweets ; replies table rows = $replies"
  if [[ ! "$tweets" =~ ^[0-9]+$ ]] || [[ "$tweets" -lt "$TWEET_COUNT" ]]; then
    echo "[$(date)] ERROR: seed didn't produce expected ${TWEET_COUNT} tweet rows" >&2
    return 1
  fi
  local expected_replies=$((TWEET_COUNT * REPLIES_PER_TWEET))
  if [[ ! "$replies" =~ ^[0-9]+$ ]] || [[ "$replies" -lt "$expected_replies" ]]; then
    echo "[$(date)] ERROR: seed didn't produce expected ${expected_replies} reply rows" >&2
    return 1
  fi
}

# Wait for one service's actuator health to report UP, up to 180s. Args: <label> <base-url>.
wait_health() {
  local label="$1" url="$2" i
  for i in $(seq 1 180); do
    if curl -fs "${url}/actuator/health" 2>/dev/null | grep -q UP; then
      echo "[$(date)] $label UP after ${i}s on ${url}"
      return 0
    fi
    sleep 1
  done
  echo "[$(date)] ERROR: $label did not report UP within 180s on ${url} ($(topology_label))" >&2
  return 1
}

# --- Pre-run: bring up infra (topology-aware), then seed when the schema already exists.
# Each app's V1__baseline.sql owns its schema. topology_infra_up brings up Docker infra
# (runtime up infra) for local-app/all-docker, or host-native infra (deployment/local/local-infra.sh)
# for native-local. Docker DB volumes persist the Flyway-created schema across runs, so those
# topologies seed now, before the apps boot. native-local creates empty DBs every time, so the
# schema does not exist until the apps migrate it — it seeds inside run_stack, after boot.
topology_infra_up 2>&1 | tail -3

if ! topology_uses_native_infra; then
  seed_and_verify || exit 1
fi

# --- Per-stack runner ---------------------------------------------------------
run_stack() {
  local stack="$1"
  echo "[$(date)] ----- $stack: bringing stack up ($(topology_label)) -----"
  # tweet-service is the SUT; interaction-service + user-service are its per-request fan-out
  # deps. All three must be up before load (unlike following-cache, which boots only user- +
  # interaction-service). The benchmark profile is enabled on each.
  topology_start_stack "$stack" user-service interaction-service tweet-service

  # Gate on the SUT first, then BOTH fan-out deps — a getTweet request needs all three. If any
  # is down the enrichment fan-out errors and every request 500s, polluting the cells.
  if ! wait_health "$stack tweet-service (SUT)" "$BENCH_URL"; then
    if [[ "$(topology_label)" == "local-app" || "$(topology_label)" == "native-local" ]]; then
      tail -40 "$REPO/testing-results/runtime/local-app/${stack}/tweet-service/app.log" 2>&1 >&2 || true
    else
      docker compose --project-name "${COMPOSE_PROJECT_NAME}" \
        --project-directory "${REPO}" \
        -f deployment/docker-compose/infrastructure.yml \
        -f "deployment/docker-compose/${stack}.yml" \
        logs --tail 40 tweet-service 2>&1 | tail -40 >&2 || true
    fi
    topology_stop_stack "$stack" user-service interaction-service tweet-service
    return 1
  fi
  if ! wait_health "$stack interaction-service (fan-out dep)" "$INTERACTION_URL" \
    || ! wait_health "$stack user-service (fan-out dep)" "$USER_URL"; then
    topology_stop_stack "$stack" user-service interaction-service tweet-service
    return 1
  fi

  if topology_uses_native_infra; then
    # native DBs started empty; the apps' Flyway just created the schemas at boot (the health
    # gates above already confirmed all three are up, so users/tweets/replies all exist). Seed
    # this stack now. Re-seeding per stack is fine: the seed is deterministic, so both stacks
    # measure the identical cohort.
    echo "[$(date)] $stack all three services UP (schemas migrated); seeding cohort..."
    if ! seed_and_verify; then
      topology_stop_stack "$stack" user-service interaction-service tweet-service
      return 1
    fi
  fi

  redis-cli -h 127.0.0.1 -p 63790 FLUSHALL >/dev/null 2>&1 || true
  echo "[$(date)] redis FLUSHALL done (host redis-cli :63790)"

  # Natural 60s caching: no artificial pre-warm. The cache starts cold (FLUSHALL above) and
  # k6's warmup phase populates it the production way -- the first hit on each key misses, the
  # enrichment fans out (four interaction-service counts/replies calls + a user-summary per
  # reply author) and the count-caches + summary-cache land in Redis at the default 60s TTL.
  # By the steady window the hot working set is warm; entries re-fill every 60s. The reply
  # authors are real seeded pool rows, so the user-summary fan-out resolves (no 404).
  echo "[$(date)] ----- $stack: cold cache; k6 warmup populates count/summary caches at 60s TTL (no pre-warm) -----"

  echo "[$(date)] ----- $stack: launching k6 ${PROFILE} (host-side k6 -> :9092) -----"
  # Host-side k6 for both host-JVM topologies (local-app, native-local); the k6 sibling-
  # container mode is only for all-docker. run_bench.sh has a first-class getTweet arm that
  # runs workloads/http-fanout-get-tweet/script.js (URL ${PATH_PREFIX}/${id}) and forwards the generic-GET
  # env (PATH_PREFIX/KEYS_FILE/TOTAL_KEYS/HOT_COUNT/HOT_RATIO) pointed at :9092 /tweets.
  local load_mode="local"
  if [[ "$TOPOLOGY" == "all-docker" ]]; then
    load_mode="docker"
  fi
  BENCHMARK_TOPOLOGY="$TOPOLOGY" BENCHMARK_INFRA_TOPOLOGY="$INFRA_TOPOLOGY" \
    ./testing/performance/k6/run_bench.sh --workload "$WORKLOAD" \
    --mode "$load_mode" \
    --target-container tweebyte-tweet-service-1 \
    --base-url "$BENCH_URL" \
    --path-prefix "$PATH_PREFIX" \
    --keys-file "$KEYS_FILE" \
    --total-keys "$TWEET_COUNT" \
    --concurrencies "$CONCS" \
    --runs "$RUNS" --warmup "$WARMUP" --duration "$DURATION" \
    --hot-ratio "$HOT_RATIO" --hot-count "$HOT_COUNT" \
    --collect-resources 1 \
    --auto-prepare 0

  local status=$?

  local result_dir
  result_dir=$(find_latest_result_dir "$REPO" "k6" "$WORKLOAD")
  if [[ $status -eq 0 ]]; then
    cleanup_raw_samples "$result_dir"
  else
    remove_partial_result_dir "$result_dir"
  fi

  echo "[$(date)] ----- $stack: bench exit=$status, bringing stack down -----"
  topology_stop_stack "$stack" user-service interaction-service tweet-service
  return $status
}

ASYNC=0; REACTIVE=0
for stack in $ORDER; do
  run_stack "$stack"
  rc=$?
  case "$stack" in
    async)    ASYNC=$rc ;;
    reactive) REACTIVE=$rc ;;
  esac
done

echo "[$(date)] ===== ${WORKLOAD} ${PROFILE} COMPLETE (async=$ASYNC reactive=$REACTIVE) ====="
# Results land under the workload slug (run_bench.sh names dirs after --workload).
ls -dt "$REPO/testing-results/performance/k6/results_${WORKLOAD//-/_}_2026"* 2>/dev/null | head -2

# Teardown. native-local infra is ephemeral, so bringing it down wipes the DBs + redis +
# vault wholesale — nothing to clean. The Docker topologies leave shared infra up (their
# 'local down'/'runtime down' only stops the app services), so the seeder truncates the
# tables it populated across the three DBs and flushes redis instead.
if topology_uses_native_infra; then
  echo "[$(date)] ===== ${WORKLOAD} ${PROFILE}: teardown (native infra down — wipes DBs/redis/vault) ====="
  topology_infra_down
else
  echo "[$(date)] ===== ${WORKLOAD} ${PROFILE}: teardown (truncate tweets/relations + interactions + users, flush redis) ====="
  python3 testing/performance/k6/workloads/http-fanout-get-tweet/prepare.py clean \
    --tweet-count "$TWEET_COUNT" 2>&1 | tail -5
fi

cleanup_sweep_finish
