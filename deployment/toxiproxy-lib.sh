#!/usr/bin/env bash
# Shared toxiproxy listen-port map + proxy-spec builders.
#
# Sourced by the host orchestration scripts that put toxiproxy on the benchmark
# I/O path:
#   - deployment/local/local-infra.sh  (native-local: starts a host toxiproxy-server and
#                                  configures it against host-process upstreams)
#   - deployment/local/local-app.sh    (native-local: routes the app JVMs at the proxy
#                                  listen ports)
#   - deployment/docker-compose/compose.sh
#                                 (Docker: routes the app containers + emits the
#                                  Docker proxy spec for the toxiproxy-config container)
#
# The actual proxy creation lives in docker-compose/toxiproxy-config.sh, which is
# substrate-agnostic: it consumes the TOXIPROXY_PROXIES spec these builders emit
# plus a single TOXIPROXY_LATENCY_MS / TOXIPROXY_JITTER_MS knob. That keeps one
# init script for both Docker and native-local (the "shared init script" mandate).
#
# Master enable: BENCH_TOXIPROXY_LATENCY_MS. Unset or 0 => toxiproxy is off the
# measured path entirely (apps connect direct to Postgres/Redis/peers, exactly the
# pre-toxiproxy benchmark behaviour). >0 => the benchmark routes through toxiproxy
# and the DIFFERENTIATED per-tier latencies below are applied (its numeric value is
# just the on/off switch; the per-tier knobs set the actual ms).
#
# PINNED PROFILE (operator, 2026-06-20) — "realistic differentiated, same-region":
#   Redis 1 ms · Postgres 2 ms · inter-service HTTP 3 ms (each direction, jitter 0).
#   Models cache fastest, DB next, and an east-west service hop slowest. Per-tier override:
#   BENCH_TOXIPROXY_REDIS_MS / _PG_MS / _HTTP_MS. To enable: BENCH_TOXIPROXY_LATENCY_MS=1.
# The load generators (JMeter/k6) and the seeders always connect direct.

# Proxy listen ports (host side for native-local; published container ports for
# Docker). Overridable, but the defaults are the canonical map referenced in the
# compose port publishes and the routing receipts.
TOXIPROXY_ADMIN_PORT="${TOXIPROXY_ADMIN_PORT:-8474}"
TOXIPROXY_REDIS_PORT="${TOXIPROXY_REDIS_PORT:-26379}"
TOXIPROXY_PG_USER_PORT="${TOXIPROXY_PG_USER_PORT:-26432}"
TOXIPROXY_PG_TWEET_PORT="${TOXIPROXY_PG_TWEET_PORT:-26433}"
TOXIPROXY_PG_INTERACTION_PORT="${TOXIPROXY_PG_INTERACTION_PORT:-26434}"
# Inter-service inbound proxies. 260xx mirrors the real 90xx service ports so the
# mapping reads at a glance (26091 -> 9091, etc.). Load generators still hit the
# real 90xx ports; only service->service calls are routed here.
TOXIPROXY_SVC_USER_PORT="${TOXIPROXY_SVC_USER_PORT:-26091}"
TOXIPROXY_SVC_TWEET_PORT="${TOXIPROXY_SVC_TWEET_PORT:-26092}"
TOXIPROXY_SVC_INTERACTION_PORT="${TOXIPROXY_SVC_INTERACTION_PORT:-26093}"

# True (0) when the operator has opted the benchmark into the toxiproxy I/O path.
toxiproxy_enabled() {
  local lat="${BENCH_TOXIPROXY_LATENCY_MS:-0}"
  [[ "$lat" =~ ^[0-9]+$ ]] && (( lat > 0 ))
}

# Per-tier per-direction latency (ms), differentiated by realism: Redis < Postgres <
# HTTP. Jitter 0 (fixed) so each maps to a clean added RTT (~2x, applied up+down).
# toxiproxy_latency_ms stays the master on/off value (used by toxiproxy_enabled +
# the routing logs).
toxiproxy_latency_ms() { echo "${BENCH_TOXIPROXY_LATENCY_MS:-0}"; }
toxiproxy_redis_ms()   { echo "${BENCH_TOXIPROXY_REDIS_MS:-1}"; }
toxiproxy_pg_ms()      { echo "${BENCH_TOXIPROXY_PG_MS:-2}"; }
toxiproxy_http_ms()    { echo "${BENCH_TOXIPROXY_HTTP_MS:-3}"; }
toxiproxy_jitter_ms()  { echo "${BENCH_TOXIPROXY_JITTER_MS:-0}"; }

# Echo the proxy spec (one `name:listen:upstream_host:upstream_port:lat:jit` line
# per proxy) for a substrate. $1 = native|docker. Differentiated per-tier latency:
# redis_proxy = Redis ms, pg_* = Postgres ms, svc_* = inter-service HTTP ms.
# toxiproxy-config.sh applies non-zero toxics to both streams and skips any 0 ones.
toxiproxy_build_spec() {
  local substrate="$1"
  local redis_lat pg_lat http_lat jit
  redis_lat="$(toxiproxy_redis_ms)"
  pg_lat="$(toxiproxy_pg_ms)"
  http_lat="$(toxiproxy_http_ms)"
  jit="$(toxiproxy_jitter_ms)"

  local pg_user pg_tweet pg_interaction redis svc_user svc_tweet svc_interaction
  if [[ "$substrate" == "native" ]]; then
    # native-local: every upstream is a host loopback process.
    pg_user="127.0.0.1:54321"
    pg_tweet="127.0.0.1:54322"
    pg_interaction="127.0.0.1:54323"
    redis="127.0.0.1:63790"
    svc_user="127.0.0.1:9091"
    svc_tweet="127.0.0.1:9092"
    svc_interaction="127.0.0.1:9093"
  else
    # Docker: upstreams are the compose-network DNS names / internal ports.
    pg_user="user-service-db:5432"
    pg_tweet="tweet-service-db:5432"
    pg_interaction="interaction-service-db:5432"
    redis="redis:6379"
    svc_user="user-service:9091"
    svc_tweet="tweet-service:9092"
    svc_interaction="interaction-service:9093"
  fi

  cat <<EOF
redis_proxy:${TOXIPROXY_REDIS_PORT}:${redis}:${redis_lat}:${jit}
pg_user_proxy:${TOXIPROXY_PG_USER_PORT}:${pg_user}:${pg_lat}:${jit}
pg_tweet_proxy:${TOXIPROXY_PG_TWEET_PORT}:${pg_tweet}:${pg_lat}:${jit}
pg_interaction_proxy:${TOXIPROXY_PG_INTERACTION_PORT}:${pg_interaction}:${pg_lat}:${jit}
svc_user_proxy:${TOXIPROXY_SVC_USER_PORT}:${svc_user}:${http_lat}:${jit}
svc_tweet_proxy:${TOXIPROXY_SVC_TWEET_PORT}:${svc_tweet}:${http_lat}:${jit}
svc_interaction_proxy:${TOXIPROXY_SVC_INTERACTION_PORT}:${svc_interaction}:${http_lat}:${jit}
EOF
}

# The set of TCP listen ports the proxies bind (admin + the 7 proxies), space
# separated. Used by local-infra.sh to assert they are free before starting a host
# toxiproxy-server.
toxiproxy_listen_ports() {
  echo "${TOXIPROXY_ADMIN_PORT} ${TOXIPROXY_REDIS_PORT} ${TOXIPROXY_PG_USER_PORT} ${TOXIPROXY_PG_TWEET_PORT} ${TOXIPROXY_PG_INTERACTION_PORT} ${TOXIPROXY_SVC_USER_PORT} ${TOXIPROXY_SVC_TWEET_PORT} ${TOXIPROXY_SVC_INTERACTION_PORT}"
}
