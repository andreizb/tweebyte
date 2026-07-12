# Architecture

Tweebyte is a Twitter-like microblogging platform implemented twice over the same HTTP API surface so that two concurrency models can be compared head-to-head under identical workloads:

- **`backend/async/`** — Spring Web + Spring Data JPA (blocking I/O, bounded thread pools, classical servlet stack).
- **`backend/reactive/`** — Spring WebFlux + R2DBC (non-blocking, Netty event loops, Reactor pipelines).

The two stacks ship the same four microservices on the same ports against the same data dependencies. API parity is enforced by a shared Cucumber suite under [`testing/functional-equivalence/`](testing/functional-equivalence/).

## Services

| Service | HTTP port (container/host) | Database (host port) | Async stack | Reactive stack |
|---|---:|---|---|---|
| **gateway-service** | 8080 | — | Spring Cloud Gateway Server MVC (Spring Boot 3.3.2, Java 21) | Spring Cloud Gateway (Spring Boot 3.3.2, Java 21) |
| **user-service** | 9091 | `user_service_db` (54321) | Spring Web + JPA, Keycloak-brokered auth | Spring WebFlux + R2DBC, Keycloak-brokered auth |
| **tweet-service** | 9092 | `tweet_service_db` (54322) | Spring Web + JPA + Spring AI 1.0.1, Redis cache | Spring WebFlux + R2DBC + Spring AI 1.0.1, Redis cache |
| **interaction-service** | 9093 | `interaction_service_db` (54323) | Spring Web + JPA, `RestClient`, Redis cache | Spring WebFlux + R2DBC, `WebClient`, Resilience4j, Redis cache |

Service HTTP ports map 1:1 container/host. Each service owns its database; nothing is shared at the relational layer.

Each service follows a layered package structure under `ro.tweebyte.<service>`: `controller/` → `service/` → `repository/`, with `entity/`, `model/` (DTOs), `mapper/` (MapStruct), `config/`, `exception/`, `client/` (inter-service HTTP), `util/`.

## Storage and caches

- **Postgres 16.4-alpine**, one logical database per service. No cross-service joins; the only data sharing happens via HTTP.
- **Redis 7.2.5-alpine**, single shared instance.
  - `tweet-service` uses Redis as a fallback for the followed-id feed cache.
  - `interaction-service` caches `following`/`followed`, recommendations, popular-users, popular-hashtags, and user-summary.
  - Containers reach Redis at `redis:6379`. The host-published port is `localhost:63790`.
  - The `benchmark` profile connects interaction-service direct to plaintext `redis:6379` — the measured path. The **toxiproxy** Redis proxy on `26379` stays defined-and-idle for future latency / fault injection; opt in by exporting `INTERACTION_CACHE_HOST=toxiproxy INTERACTION_CACHE_PORT=26379`.

## Inter-service communication

All cross-service HTTP is wrapped in per-service client classes — `UserClient`, `InteractionClient`, `TweetClient`. Service URLs are injected as env vars (`USER_SERVICE_URL`, `TWEET_SERVICE_URL`, `INTERACTION_SERVICE_URL`) by `./run.sh` / `deployment/docker-compose/compose.sh` and the compose files; nothing is hardcoded.

- **Async** uses Spring `RestClient` (synchronous, blocking) for all inter-service calls.
- **Reactive** uses `WebClient` returning `Mono` / `Flux`, with Resilience4j circuit breakers in front of cache-miss fan-out.

The async stack runs cross-service fan-out on a **bounded** executor (`httpClientExecutor`, `Executors.newFixedThreadPool` + unbounded task queue) sized to the downstream HTTP pool it feeds — `app.concurrency.http-client.pool-size` (512, = total downstream connections). DB CRUD runs on a second bounded pool, `ioExecutor` (`app.concurrency.io.pool-size`, 256, = the DB pool). Both saturate by queueing and waiting — never rejecting, never OOMing. The AI-streaming `streamExecutor` is a bounded `ThreadPoolExecutor` with an explicit reject policy (`app.concurrency.stream.pool-size`, default 200, swept up to 1600 in the AI-streaming benchmark). The reactive stack runs everything on the Netty event-loop group.

## Frontend

The Angular 17 client under `frontend/` is backend-agnostic: every API client uses a relative service path, and startup configuration selects one gateway base URL. Switching between the async and reactive stacks is therefore a configuration change against the same HTTP contract.

The client uses standalone components, RxJS for request and stream orchestration, and Signals/SignalStore for template state. Authentication is brokered through user-service; the browser stores one bearer token in session storage and applies it through an interceptor. Client guards improve navigation but do not replace gateway or service authorization.

AI streaming is SSE over authenticated `POST` requests with a JSON prompt body. Because `EventSource` is GET-only, `AiStreamService` reads `fetch()` response streams and uses `AbortController` for cancellation. MSW provides a deterministic in-browser API for local development and Playwright; live browser tests exercise the same bundle against a configured gateway.

See [`frontend/README.md`](frontend/README.md) for frontend structure, runtime configuration, and commands.

## Security posture

The production-grade security layer is kept outside the measured request path or disabled symmetrically by the `benchmark` overlay. Every per-request-expensive control is ON in `prod`/`functional-equivalence` and OFF in `benchmark`; boot-time controls such as Vault and Flyway stay enabled without adding steady-state request work.

For the security scope of the repository as a whole — what this layer does and does not protect against, and how to report a vulnerability — see [`SECURITY.md`](SECURITY.md).

### Identity & tokens — Keycloak

A dev-mode **Keycloak 25** realm (`tweebyte`, its own Postgres) is the credential authority. `user-service` no longer signs tokens in-app; it brokers Keycloak:

- **login / register** drive the Resource-Owner-Password-Credentials grant via `KeycloakClient` (async `RestClient` / reactive `WebClient`, byte-identical realm contract).
- **register** additionally provisions the Keycloak user through the Admin API (`client_credentials` on the `tweebyte-app` service account), writing the local profile UUID as the Keycloak `user_id` attribute. A realm protocol mapper surfaces it as the `user_id` claim, so the edge enforces ownership on the same id the services do.
- The whole `/auth` surface (controller, service, `KeycloakClient`) is gated by `app.keycloak.enabled` — ON in base, OFF in the benchmark overlay (the benchmark seeds users via SQL and never logs in, so no Keycloak bean loads on the measured path).
- The `tweebyte-app` client secret lives only in the gitignored `.env` and in Vault; it is env-substituted into `realm-export.json` at import and never committed.

### Edge gateway — validation, ownership, hardening

Both stacks front their three services with a Spring Cloud Gateway instance on port 8080 (async: Spring Cloud Gateway Server MVC on the servlet/Tomcat stack; reactive: Spring Cloud Gateway on Netty). Each gateway strips the `/<service>` path prefix, drops `Cookie`/`Set-Cookie` on forward, and runs the same edge filter ahead of routing:

1. **JWT validation** — every routed request must carry a Keycloak-issued RS256 bearer token, verified against the realm JWKS resolved by `kid` through a cached, rate-limited `JwkProvider`, with an issuer check. The two exact public paths `/user-service/auth/login` and `/user-service/auth/register`, the unified Swagger doc paths, and (on the servlet gateway) `/actuator/**` bypass it. Failures return `401`.
2. **Ownership gating (defense-in-depth)** — on mutating routes carrying an owner `{userId}` segment, the token's **`user_id`** claim must equal that segment or the request is rejected with `403`. The owner-route table is matched with Spring `PathPattern`s so single-segment `{userId}` captures never collide with sibling batch routes.

The edge also carries the prod-on hardening (off the benchmark path by construction): **token-bucket rate limiting**, **CORS**, **request-size limits**, a **unified Swagger portal** aggregating the three service specs, and **security response headers** on every proxied response — HSTS (`max-age=31536000; includeSubDomains`), CSP, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`. Auth failures and 403 ownership denials emit structured security-audit events.

Edge gating is **additive**: every service still performs its own authoritative ownership checks, and benchmark / functional-equivalence traffic that targets services directly on 9091/9092/9093 bypasses the gateway, so the edge filter adds zero benchmark-path overhead.

### Secrets — Vault

A dev-mode **HashiCorp Vault** seeds `secret/tweebyte` (KV v2) from the gitignored `.env`; all 8 modules fetch DB / actuator / TLS / Keycloak-client secrets from Vault at boot via Spring Cloud Vault. The import is **non-optional and fail-fast** — a service will not start if Vault is unreachable. Static KV, no lifecycle threads: secrets land in the `Environment` once at startup and nothing touches the measured request path. Unit tests disable Vault and carry inline placeholders.

### Transport — TLS edge + mTLS east-west

In `prod`/`functional-equivalence` each service presents a per-service PKCS12 keystore that is **both** its server identity and its east-west mTLS client identity, chaining to a shared dev-CA truststore; backend services require a client cert (`server.ssl.client-auth=need`) and the gateway presents its cert on the outbound proxy hop. Cert material is compose-mounted at `/etc/tweebyte/tls` (never baked into images); the keystore/truststore passwords come from Vault. The **benchmark overlay turns every SSL surface off** (plain HTTP, `app.mtls.enabled=false`, `SVC_SCHEME=http`), so the measured path carries no handshake cost.

### Actuator plane

The metrics-bearing actuator endpoints are gated behind **HTTP Basic** (`ACTUATOR` role); `health` and `info` stay open for container / load-driver probes. bcrypt therefore never touches the request hot path. The benchmark overlay excludes Spring Security auto-configuration entirely, so no security filter sits on the measured path and the credential is never consulted.

## Observability & messaging

The infrastructure compose file provisions an out-of-band observability stack and an unwired message broker (none of it sits on the measured request path):

- **Prometheus** (host `9090`) is configured to scrape `/actuator/prometheus` on all services + both gateways, evaluates the provisioned SLO / RED-USE alert rules, and forwards to **Alertmanager** (host `9193`, null-sink receiver in dev). The gateway scrape works with the shipped TLS assets; the three backend jobs still require a mounted Prometheus client certificate because those services enforce mTLS.
- **Grafana** (host `3000`) with provisioned Prometheus / Loki / Jaeger datasources and RED/USE dashboards.
- **Loki + Promtail** (host `3100`) collect container logs via Docker service-discovery. Services emit **structured JSON logs** at INFO with `service` + `traceId`/`spanId` in the MDC so log lines join to traces; the benchmark profile drops the root logger to WARN, so the measured path logs nothing.
- **Jaeger** (host `16686`) receives OTLP traces. Distributed tracing is **profile-gated** (not env-gated): ON in `prod`/`functional-equivalence` (`management.tracing.enabled=true`), OFF in the benchmark overlay, so the measured path carries zero span overhead.
- **Kafka** in single-node KRaft mode (host `9094`) is provisioned for future use and intentionally **not wired** — no producers or consumers in any service.

## Deployment profiles

Three compose profiles share the same topology and differ only in instrumentation. The canonical entry point is `./run.sh runtime up <stack> <profile>`.

| Profile | Purpose | Side effects |
|---|---|---|
| `prod` | Normal runtime. | No instrumentation. Interaction-service talks to Redis directly. |
| `benchmark` | Performance runs. | Direct DB + Redis path (toxiproxy proxies defined-but-idle for future fault-injection), GC logging on, JVM heap caps, configurable executor sizing. k6 / JMeter hit services directly (gateway bypassed). |
| `functional-equivalence` | Cucumber FE suite. | JaCoCo agent layered into each service JVM. Cleanly isolated from prod / benchmark — overlay loaded only under this profile. |

### Feature-gating matrix

Feature ON/OFF is decided by **Spring profile, never by an env-var default** (env-var `${...}` placeholders are reserved for topology / secrets / tuning). Compose sets `SPRING_PROFILES_ACTIVE=${STACK_PROFILE:-}`, so `prod` and `functional-equivalence` run with an **empty** active profile and use base `application.properties` (every feature ON); `benchmark` overlays `application-benchmark.properties`, which flips every per-request-expensive feature OFF. The same knob name + value appears on both stacks, so the async-vs-reactive comparison stays symmetric.

| Feature | `prod` (base) | `benchmark` (overlay) | `functional-equivalence` (base + JaCoCo) | unit tests |
|---|---|---|---|---|
| Vault secrets — boot | ON, fail-fast | ON, boot-only | ON, fail-fast | OFF (inline fallback) |
| Keycloak auth `app.keycloak.enabled` | ON | **OFF** (no `/auth` bean) | ON | ON (no network) |
| TLS edge + mTLS east-west | ON | **OFF** (plain HTTP) | ON | OFF |
| Actuator HTTP Basic | ON | **OFF** (security autoconfig excluded) | ON | ON (prod config) |
| Edge hardening — rate-limit/CORS/headers | ON | n/a (gateway bypassed) | ON | n/a |
| Resilience4j inter-service guards | ON | **OFF** (neutralized via `configs.default` — never-trip breaker etc.) | ON | ON |
| Flyway migration — boot | ON | ON, boot-only | ON | OFF (H2/already-migrated) |
| Structured JSON logging + audit | INFO JSON | **WARN/off on measured path** | INFO JSON | OFF |
| Tracing → Jaeger | ON | **OFF** | ON | OFF |
| OpenAPI / Swagger | ON | **OFF** (endpoints disabled) | ON | n/a |
| Graceful shutdown | ON | ON | ON | n/a |
| Gateway in request path | north-south only | **bypassed** (k6/JMeter hit 9091/2/3 direct) | FE traverses the gateway | n/a |
| Metrics | full RED/USE | JVM/process + live scrape | full RED/USE + JaCoCo | n/a |

Boot-time features are safe in every profile because they add no steady-state request work. Per-request-expensive features are ON in base and explicitly OFF in the benchmark overlay; the table above is the source of truth for that contract.

See [`deployment/README.md`](deployment/README.md) for compose-file layout and runtime commands.

## Invariants

These are load-bearing for the benchmark study; do not break them silently.

- **API parity.** Every endpoint exists on both stacks with the same path, method, request/response shape, and status codes. The Cucumber suite (`testing/functional-equivalence/src/test/resources/features/`) executes the same `.feature` files against both stacks.
- **Stack symmetry.** Any tuning knob (pool size, heap cap, JVM flag, cache TTL, properties value) applied to one stack must be mirrored on the other, and the benchmark values are connection-anchored and applied symmetrically across all three services (`user`, `tweet`, `interaction`) on both stacks. The downstream HTTP pool (256 connections per host/route, 45s acquire) is declared in base `application.properties` on both stacks (`app.http.downstream.max-connections=256`); the async Apache HttpClient additionally caps the global total at 512 (`max-connections-total`, = 2 routes × 256), while Reactor Netty pools per host with no global-total equivalent. The DB pool (min-idle 10, max-size 256, effectively-infinite acquire) and the 3600-second acquire override live in `application-benchmark.properties` on both stacks. The reactive stack ships two opt-in R2DBC diagnostic toggles with no JDBC/Hikari equivalent — `disableR2dbcLoopColocation` and `r2dbcPoolAcquisitionScheduler` — and the benchmark overlay enables both. Rationale and operating rules live in [`AGENTS.md`](AGENTS.md) and [`testing/performance/README.md`](testing/performance/README.md).
- **Saturation behavior.** The async AI-streaming path uses a bounded `ThreadPoolExecutor` with an explicit reject policy (`abort` under the benchmark profile) so stream saturation surfaces as a clean 5xx. The other async pools — DB CRUD (`ioExecutor`) and cross-service fan-out (`httpClientExecutor`) — are bounded fixed pools (`Executors.newFixedThreadPool`) sized to the connection pool each feeds (`ioExecutor` = DB pool, `httpClientExecutor` = total downstream HTTP connections), backed by an unbounded task queue: under saturation tasks queue and wait — never reject, never OOM. The reactive stack inherits Netty's event-loop backpressure throughout.
- **Per-service unit-coverage gate.** Line + branch ≥ 0.90 on every service module on both stacks (`testing/functional-equivalence/coverage-gate.sh unit`).
- **`@Test`-count parity.** Per-service `|async − reactive| / max ≤ 5 %`.

## Related docs

- [`AGENTS.md`](AGENTS.md) — agent workflow, full env-var matrix, pinned versions, AI-streaming workload internals.
- [`backend/README.md`](backend/README.md) — backend module layout and development commands.
- [`frontend/README.md`](frontend/README.md) — frontend structure, runtime configuration, and development commands.
- [`deployment/README.md`](deployment/README.md) — runtime commands and compose-file layout.
- [`SECURITY.md`](SECURITY.md) — supported line, security boundary, and vulnerability reporting.
- [`testing/README.md`](testing/README.md) — unit, FE, and performance test surfaces.
- [`testing/RESULTS.md`](testing/RESULTS.md) — current verification state, benchmark methodology, and complete retained grids.
- [`testing/performance/README.md`](testing/performance/README.md) — workload profiles, topology, cross-workload validation, and output cleanup.
