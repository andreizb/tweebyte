#!/bin/sh
# Toxiproxy bootstrap — the single init script shared by Docker and native-local.
#
# Substrate-agnostic: it reads the proxy spec + admin URL from the environment and
# configures one latency proxy per datastore and per inter-service hop. The two
# callers differ only in the values they pass:
#   - Docker:        deployment/docker-compose/compose.sh exports TOXIPROXY_PROXIES (compose-DNS
#                    upstreams) and this runs in the toxiproxy-config container
#                    (benchmark profile only, so prod's toxiproxy stays an
#                    uninitialized idle container).
#   - native-local:  deployment/local/local-infra.sh exports TOXIPROXY_PROXIES
#                    (127.0.0.1 host-process upstreams) and runs this on the host
#                    against a host toxiproxy-server.
# Both build the spec from deployment/toxiproxy-lib.sh, so the proxy set + ports
# are defined in exactly one place.
#
# Proxies (all at the same operator-chosen latency, both directions):
#   redis_proxy            — Redis
#   pg_user/tweet/interaction_proxy   — the three service Postgres instances
#   svc_user/tweet/interaction_proxy  — inter-service inbound (service->service)
#
# Load generators (JMeter/k6) and the seeders always connect direct to the real
# datastore/service ports; only the apps and their east-west traffic are proxied.
#
# Mounted into the toxiproxy-config container instead of being inlined in
# docker-compose's `command:` field because this Docker Compose version (v5.1.3)
# word-splits multi-line block scalars when paired with an array `entrypoint:`.
# Mounting a script bypasses that quirk and keeps the logic version-controlled.
set -e

TOXIPROXY_URL="${TOXIPROXY_URL:-http://toxiproxy:8474}"

# Proxy spec: one `name:listen_port:upstream_host:upstream_port:latency_ms:jitter_ms`
# line per proxy. Normally exported by the caller (built from toxiproxy-lib.sh). The
# built-in fallback is the Docker upstream set at latency 0 (transparent) so a bare
# run yields a clean forwarder baseline.
PROXIES="${TOXIPROXY_PROXIES:-
redis_proxy:26379:redis:6379:0:0
pg_user_proxy:26432:user-service-db:5432:0:0
pg_tweet_proxy:26433:tweet-service-db:5432:0:0
pg_interaction_proxy:26434:interaction-service-db:5432:0:0
svc_user_proxy:26091:user-service:9091:0:0
svc_tweet_proxy:26092:tweet-service:9092:0:0
svc_interaction_proxy:26093:interaction-service:9093:0:0
}"

# Optional global latency override: when set, it overrides the per-line latency for
# EVERY proxy (so a sweep can re-latency an existing toxiproxy without rebuilding
# the spec). Empty => honour each line's own latency_ms/jitter_ms.
GLOBAL_LATENCY="${TOXIPROXY_LATENCY_MS:-}"
GLOBAL_JITTER="${TOXIPROXY_JITTER_MS:-}"

# Block until the admin API is reachable.
until curl -fsS "${TOXIPROXY_URL}/version" >/dev/null 2>&1; do sleep 0.3; done

for spec in $PROXIES; do
  proxy_name="${spec%%:*}"
  rest="${spec#*:}"
  listen_port="${rest%%:*}"
  rest="${rest#*:}"
  upstream_host="${rest%%:*}"
  rest="${rest#*:}"
  upstream_port="${rest%%:*}"
  rest="${rest#*:}"
  latency_ms="${rest%%:*}"
  jitter_ms="${rest#*:}"
  upstream="${upstream_host}:${upstream_port}"

  # Global override wins when provided.
  [ -n "${GLOBAL_LATENCY}" ] && latency_ms="${GLOBAL_LATENCY}"
  [ -n "${GLOBAL_JITTER}" ] && jitter_ms="${GLOBAL_JITTER}"

  echo "Configuring ${proxy_name} listen=0.0.0.0:${listen_port} upstream=${upstream} latency=${latency_ms}ms jitter=${jitter_ms}ms"

  # Idempotent create. If the proxy already exists this 409s; we ignore.
  curl -fsS -X POST "${TOXIPROXY_URL}/proxies" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"${proxy_name}\",\"listen\":\"0.0.0.0:${listen_port}\",\"upstream\":\"${upstream}\",\"enabled\":true}" \
    >/dev/null 2>&1 || true

  # Confirm it actually exists; bail loud if not.
  curl -fsS "${TOXIPROXY_URL}/proxies/${proxy_name}" >/dev/null

  # Wipe any stale toxics from a previous run.
  curl -fsS -X DELETE "${TOXIPROXY_URL}/proxies/${proxy_name}/toxics/latency_down" >/dev/null 2>&1 || true
  curl -fsS -X DELETE "${TOXIPROXY_URL}/proxies/${proxy_name}/toxics/latency_up"   >/dev/null 2>&1 || true

  # latency 0 => transparent forwarder; skip the toxics entirely so the proxy adds
  # nothing but a TCP relay (the bare-run / fault-injection baseline).
  if [ "${latency_ms}" = "0" ] && [ "${jitter_ms}" = "0" ]; then
    echo "Configured ${proxy_name} on 0.0.0.0:${listen_port} -> ${upstream} (transparent, no toxics)"
    continue
  fi

  # Re-add latency toxics with retries — toxiproxy can race during boot.
  for attempt in 1 2 3 4 5; do
    curl -fsS -X POST "${TOXIPROXY_URL}/proxies/${proxy_name}/toxics" \
      -H "Content-Type: application/json" \
      -d "{\"name\":\"latency_down\",\"type\":\"latency\",\"stream\":\"downstream\",\"attributes\":{\"latency\":${latency_ms},\"jitter\":${jitter_ms}}}" \
      >/dev/null && break
    [ "$attempt" = "5" ] && exit 1
    sleep 0.5
  done
  for attempt in 1 2 3 4 5; do
    curl -fsS -X POST "${TOXIPROXY_URL}/proxies/${proxy_name}/toxics" \
      -H "Content-Type: application/json" \
      -d "{\"name\":\"latency_up\",\"type\":\"latency\",\"stream\":\"upstream\",\"attributes\":{\"latency\":${latency_ms},\"jitter\":${jitter_ms}}}" \
      >/dev/null && break
    [ "$attempt" = "5" ] && exit 1
    sleep 0.5
  done

  # Verify both toxics ended up on the proxy before we declare success.
  toxics="$(curl -fsS "${TOXIPROXY_URL}/proxies/${proxy_name}")"
  echo "$toxics" | grep -q '"latency_down"' || exit 1
  echo "$toxics" | grep -q '"latency_up"'   || exit 1

  echo "Configured ${proxy_name} on 0.0.0.0:${listen_port} -> ${upstream} (latency=${latency_ms}ms jitter=${jitter_ms}ms both dirs)"
done
