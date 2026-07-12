# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository.

> **For all AI coding agents:** `AGENTS.md` is the single canonical guide for this repo. Do **not** create agent-specific guidance files; everything project-wide lives here and applies universally.

## Directory Docs

- [`ARCHITECTURE.md`](ARCHITECTURE.md) — human-facing system architecture (services, ports, storage, deployment profiles, invariants).
- [`backend/README.md`](backend/README.md) — backend module layout and service-level development commands.
- [`frontend/README.md`](frontend/README.md) — frontend structure, runtime configuration, and development commands.
- [`deployment/README.md`](deployment/README.md) — runtime/deployment entry point (compose files, profiles, `./run.sh`).
- [`testing/README.md`](testing/README.md) — testing entry point (unit, functional-equivalence, performance).
- [`testing/performance/README.md`](testing/performance/README.md) — performance benchmark engines and conventions.
- [`testing/RESULTS.md`](testing/RESULTS.md) — benchmark measurements, methodology, and maintenance rules.
- [`SECURITY.md`](SECURITY.md) — security scope (covered vs. not covered) and vulnerability reporting.

## Project Overview

Tweebyte implements the same microservice API as an **async** servlet/JPA stack and a **reactive** WebFlux/R2DBC stack. Read [`ARCHITECTURE.md`](ARCHITECTURE.md) for the system design; this file owns agent rules and implementation guardrails.

## Drift hygiene

This repo evolves. Docs, comments, and cross-references rot when code moves. When you encounter drift while doing your task, surface it — don't silently work around it. Don't go drift-hunting outside the task either.

Common drift patterns:
- Docs naming deleted/renamed files or commands.
- Comments describing behavior that no longer matches the code.
- Cookbook / RESULTS.md state tables that lag the actual workload state.
- AGENTS.md or per-dir READMEs duplicating `--help` text that has since drifted from the CLI.

When you spot drift:
- If small + in your task's scope: fix it in the same change.
- If fixing would balloon scope: flag it (file:line, what's stale) and ask before touching it.
- Always cite location (file:line). Never silently work around stale text.

## CLI source of truth

For every CLI in this repo (`./run.sh`, `./testing/performance/{k6,jmeter}/run_bench.sh`, the per-workload `run.sh` files, `python3 .../prepare.py`, the analysis + calibration Maven jars), `--help` is the source of truth for current flags and defaults. Prefer running `--help` over relying on docs.

## Spring profile / property-file contract — DO NOT INVENT `prod`

This repo intentionally has **no `application-prod.properties` file**. Do not add one, do not ask for one, and do not claim prod is missing.

- `application.properties` is the production/default configuration. It is loaded when `SPRING_PROFILES_ACTIVE` is empty, which is what the `./run.sh runtime ... prod` deployment label does for app services. It is also the base file underneath functional-equivalence unless a test resource shadows it.
- `application-benchmark.properties` is the benchmark overlay. It is loaded only when `SPRING_PROFILES_ACTIVE=benchmark`, which `./run.sh runtime ... benchmark` and `./run.sh local ... benchmark` set for app services.
- `prod` in `./run.sh runtime up <stack> prod` is a deployment label, not a Spring profile file name. In prod mode the app should read base `application.properties` plus env-injected topology/secrets. There is no separate Spring `prod` property file.
- `functional-equivalence` is a deployment/test profile with a JaCoCo compose overlay. It must not be used as a reason to create a prod property file.

When changing profile behaviour: put production/default ON values in `application.properties`; put benchmark-neutralising OFF values and measured benchmark knobs in `application-benchmark.properties`; use env vars only for topology, secrets, and per-run experimental values.

## Build & Run Commands

```bash
# Start infrastructure only
./run.sh runtime up infra prod
./run.sh runtime up infra benchmark

# Start a full stack
./run.sh runtime up async prod
./run.sh runtime up async benchmark
./run.sh runtime up reactive prod
./run.sh runtime up reactive benchmark

# Functional-equivalence test mode (Cucumber suite under testing/functional-equivalence/)
# — layers a JaCoCo agent into each service JVM via the functional-equivalence.yml overlay.
# ZERO impact on prod / benchmark paths.
./run.sh runtime up async functional-equivalence
./run.sh runtime up reactive functional-equivalence
mvn -f testing/functional-equivalence/pom.xml verify -Pasync
mvn -f testing/functional-equivalence/pom.xml verify -Preactive

# Stop without deleting volumes/images
./run.sh runtime down async benchmark

# Destructive teardown
./run.sh runtime destroy reactive prod

# Inspect running services
./run.sh runtime ps async
./run.sh runtime logs reactive --tail 100

# Run benchmark suites (engine-level help)
./testing/performance/k6/run_bench.sh --help
./testing/performance/jmeter/run_bench.sh --help

# Prepare benchmark payloads (per-workload entry)
python3 testing/performance/k6/workloads/<name>/prepare.py --help
python3 testing/performance/jmeter/workloads/<name>/prepare.py --help

# Build a service
cd backend/async/user-service && mvn clean install
cd backend/reactive/tweet-service && mvn clean install

# Bring a full app stack up in Docker
./run.sh runtime up async prod                          # whole async stack, prod profile
./run.sh runtime up reactive benchmark                  # whole reactive stack, benchmark profile

# Benchmark local-app topology: Docker infra, app JVMs local
./run.sh runtime up infra benchmark
./run.sh local up async benchmark user-service tweet-service interaction-service
./run.sh local down async benchmark user-service tweet-service interaction-service

# Native-local infra: host Postgres + Redis + dev Vault, no Docker. Wired into the
# benchmark wrappers via TOPOLOGY=native-local (testing/performance/lib/topology.sh).
# Refuses to start if Docker or another process already owns these host ports.
deployment/local/local-infra.sh up
deployment/local/local-infra.sh down

# Bring up a single service (+ its dependencies) by naming it as an extra compose arg
./run.sh runtime up reactive benchmark interaction-service

# Run all tests in a service
mvn test

# Run a single test class
mvn test -Dtest=UserControllerTest

# Run a single test method
mvn test -Dtest=UserControllerTest#testGetUserProfile
```

## Implementation map

Both `backend/async/` and `backend/reactive/` contain gateway, user, tweet, and interaction modules. [`ARCHITECTURE.md`](ARCHITECTURE.md) owns their ports, responsibilities, data flow, and invariants; [`backend/README.md`](backend/README.md) owns the local module layout and build commands.

All service modules follow `controller/` → `service/` → `repository/` under `ro.tweebyte.<service>`, with supporting `entity/`, `model/`, `mapper/`, `config/`, `exception/`, `client/`, and `util/` packages. Async uses Spring Web, JPA/Hibernate, blocking `RestClient`, and explicit executors. Reactive uses WebFlux, R2DBC, `WebClient`, Reactor, and Resilience4j. Service-local Flyway migrations own relational schemas on both stacks.

Inter-service URLs, database hosts, and cache hosts are topology values injected by Compose or `deployment/local/local-app.sh`; never hardcode them in application code.

### Configuration & feature-gating rule

Features are gated by **Spring profile, never by env-var defaults.** There are two distinct uses of `${...}` placeholders, and only the first is allowed:

- **Topology / secrets / tuning (env-injected — keep):** *where* to connect and *what* the per-run values are — `DB_HOST`/`DB_PORT`, `*_SERVICE_URL`, `CACHE_HOST`/`CACHE_PORT`, `SERVER_PORT`, `VAULT_URI`/`VAULT_TOKEN`, `KEYCLOAK_BASE_URI`, `OTLP_TRACES_ENDPOINT`, and the `AI_MOCK_*` / `LIVE_LLM_*` / `GATEWAY_RATE_LIMIT_*` knobs. These are deployment wiring and per-experiment parameters, set by compose / `run.sh`.
- **Feature on/off (profile-gated — NOT env):** whether a prod-grade capability (TLS/mTLS, tracing, resilience filters, access logs, Swagger, security auto-config) is active.

The mechanism: the literal **ON** value lives in base `application.properties` (prod/default runtime; also the base beneath functional-equivalence). The benchmark overlay `application-benchmark.properties` flips it literally **OFF**. Unit tests shadow base via `src/test/resources` and default features off unless leaving one off would drop coverage. So never write `feature.enabled=${SOMETHING:true}` to gate a feature — write `feature.enabled=true` in base and `feature.enabled=false` in the benchmark overlay. Benchmark neutrality comes from the overlay, not from an operator remembering to flip an env var. Both stacks carry the identical knob name and value.

### Gateway security guardrails

[`ARCHITECTURE.md`](ARCHITECTURE.md#edge-gateway--validation-ownership-hardening) owns the security design. When editing gateway routes, preserve these code-level invariants: public paths are exact allow-list entries; JWTs are Keycloak-issued RS256 tokens verified by issuer and `kid`; owner checks compare the `user_id` claim, not `sub`; `OwnershipRuleSet` uses `PathPattern` so owner routes cannot consume sibling batch routes; `401` responses carry `WWW-Authenticate`, while ownership `403` responses do not. Service authorization remains authoritative.

Keep servlet and reactive filters behaviorally symmetric. The async filter is a `OncePerRequestFilter`; the reactive filter is an ordered `GlobalFilter` ahead of routing.

### Media model (user-service)

Media lives entirely in **user-service** as a content-addressed store (`media_assets`, owned by the user-service Flyway baseline migration). An asset's id is `UUID.nameUUIDFromBytes(bytes)` and its `checksum` (CRC32, `BIGINT`, `uq_media_assets_checksum` UNIQUE) is the dedup key, so re-storing identical bytes returns the existing row. `MediaController` is mounted at `/media` (reached through the gateway at `/user-service/media`, JWT-protected; k6 benchmarks hit the service directly on 9091, bypassing the gateway). Endpoints:

- `POST /media` — **pure upload**. Multipart `file`; stores the bytes as-is (`source_media_id` NULL, `access_hash` NULL, content-type from the part; accepts anything). Returns `{id}`. Not a benchmark hot path — this is how product flows and seeders create the originals previews derive from.
- `POST /media/{id}/preview` — **CPU image-preview workload**. Takes only `{id}` + the gate password (no content type from the client). Loads original `{id}`'s bytes and dispatches on the **stored original's** content type (the `derivePreview` switch; `resolveAsset` supplies the asset cache → DB), then runs the unchanged CPU pipeline (toRGB → 3× gaussian blur → sobel → resize 256×256 → JPEG 0.8), content-addresses the result, and stores it as a derived asset with `source_media_id={id}` plus a **bcrypt** `access_hash` of the required password. Returns `{id}` (the preview). bcrypt runs ONLY on the genuine content-addressed INSERT (the store factory fires on a cache+DB miss), so re-previewing the same seeded source is a pure-CPU cache hit after warmup — no bcrypt, no DB write. **Per-content-type dispatch:** only `image/*` has a registered pipeline today; any other stored content type hits the default branch → `UnsupportedMediaTypeException` → **415** (add a `case` + handler method to extend to a new media type). 404 if the source is missing; **500** only when an `image/*` source carries undecodable bytes (`ImageIO.read` → null).
- `POST /media/{id}/reveal` — bcrypt-verifies the password against the preview's `access_hash`, then streams the **stored original** (via `source_media_id`) — the pipeline is one-way, so reveal returns the original, not a reversal. 403 on mismatch, 404 if `{id}` is not a gated preview. Not a benchmark path.
- `GET /media/{id}` — **blocking file-download workload**. Streams the resident bytes through a 64 KiB-chunk / 6.25 MB/s `LockSupport.parkNanos` throttle (the one sanctioned artificial-latency knob). 206 with a `Content-Range`, 404 if absent.
- `GET /media/{id}/exists` → `{exists}`; `DELETE /media/cache` flushes the in-JVM cache (204).

Media is reachable from four reference sources — `users.profile_picture_id` (in-DB FK, `ON DELETE SET NULL`), `tweets.media_ids` (tweet-service), `replies`/`retweets.media_ids` (interaction-service), and the transitive `media_assets.source_media_id` edge (a surviving preview pins its original). `StaleMediaCleanupService` unions all four (plus the permanent all-zeros default-avatar sentinel) before deleting; it fails closed and is disabled under `app.cleanup.enabled=false` in the benchmark profile.

## Key Libraries & Patterns

- **Java 21**, **Spring Boot 3.3.2** (all services and both gateways)
- **JWT**: tokens are issued by a dev-mode **Keycloak** realm. The gateways validate them against the realm JWKS via Auth0 java-jwt (v4.3.0) + jwks-rsa (v0.22.1, cached `JwkProvider` keyed by `kid`) and ownership-gate at the edge in `JwtTokenValidationFilter`. Services trust tokens on the direct east-west path — there is no server-side JWT filter; where a service needs the caller identity it decodes the `user_id` claim from the bearer (unsigned decode, signing-agnostic).
- **MapStruct** (v1.5.5.Final) for entity↔DTO mapping
- **Lombok** for boilerplate reduction (`@Data`, `@Builder`)
- **Redis** for service caches (followed-id feed fallback in tweet-service; following/followed, recommendations, popular-users, popular-hashtags, user-summary in interaction-service). Containers use `redis:6379`; the host-published port is `localhost:63790`. Benchmark interaction-service traffic connects direct to `redis:6379`; the toxiproxy Redis proxy on `26379` stays defined-but-idle for future fault-injection (opt in by exporting `INTERACTION_CACHE_HOST=toxiproxy INTERACTION_CACHE_PORT=26379`).
- Inter-service communication via per-service client classes (`UserClient`, `InteractionClient`, `TweetClient`). Async uses Spring `RestClient`; reactive uses `WebClient`.

## Observability and infrastructure guardrails

[`ARCHITECTURE.md`](ARCHITECTURE.md#observability--messaging) owns the component inventory. Agent-facing invariants: observability remains out of the benchmark request path; tracing is profile-gated ON in base and OFF in the benchmark overlay; Prometheus client authentication must match backend mTLS; Kafka remains provisioned but unwired unless messaging is explicitly requested.

## Load-tool execution modes — KNOWLEDGE PRESERVE

Operational knowledge that took hours of mid-run debugging to recover. Do not delete; if reorganized, preserve every fact below somewhere discoverable. Each knob exists because of a specific failure mode — find the failure before removing the knob.

**Host-side fd limit (local k6 / local JMeter):**
Before invoking the local load tool, raise the shell open-file limit:
```bash
ulimit -n 65536
```
macOS default (~256-1024) is too low. Without this, high-concurrency runs emit `too many open files` / `lookup ... no such host` failures that look like service problems but are actually local fd starvation.

**Host-side listen backlog + servlet connector posture (canonical benchmark runtime posture):**
Before any benchmark that opens many concurrent/long-lived connections (AI streaming especially), raise the kernel listen backlog:
```bash
sudo sysctl -w kern.ipc.somaxconn=4096
```
macOS default is **128**, which caps the servlet accept queue far below what long-residency streaming needs. When it overflows, k6 reports `dial: i/o timeout` while the stream executor may show zero rejections because the connection never reached it. Pair the host setting with `server.tomcat.accept-count=4096` and `server.tomcat.max-connections=16384` in every async service's `application-benchmark.properties`; the OS backlog caps the effective value. `server.tomcat.threads.max` stays at Spring Boot's default 200 because the request thread only submits and returns the `SseEmitter`. The k6 harness quarantines transport-dominated cells as `cell_status=TRANSPORT_LIMITED` when transport errors exceed 0.5% or 50 requests; clean cells must stay at or below 0.1%.

**Dockerized k6 (available for high-VU transport probes):**
At conc ≥ 500-800, local k6 can hit a wall on macOS. The Docker-bridge mode remains available for workloads/probes that need it:
```bash
docker run --network <project>_default \
  --ulimit nofile=1048576:1048576 \
  -v <workload-dir>:/work -w /work \
  grafana/k6:1.7.1 run /work/script.js [args...]
```
- `--ulimit nofile=1048576:1048576` is **essential** (`soft:hard`). The k6 container hits its own internal fd limit otherwise, producing `dial: i/o timeout` failures with zero bytes read.
- `<project>_default` is the compose-generated bridge network. Default project name is `tweebyte`, so the network is `tweebyte_default`.
- Mount the workload dir so the container sees `script.js` and any payload files.

**DNS at high VUs (>500):**
Even inside the Docker bridge, Docker's embedded DNS fails under bursty load — symptom is `lookup interaction-service: no such host` for ~all requests. Workaround: bypass DNS entirely, use the container IP:
```bash
INTERACTION_IP=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' tweebyte-interaction-service-1)
# pass --base-url http://${INTERACTION_IP}:9093 to k6
```

**Post-health settle (cold-start guard):**
`/actuator/health = UP` is too eager — Tomcat's accept queue isn't warmed up at health-up, so an immediate 800-conn burst times out per VU. `k6/run_bench.sh` enforces this via `--readiness-grace <secs>` (default 5s; checks UP **AND** `/actuator/prometheus = 200` stably for N consecutive seconds before each cell). **JMeter's `run_bench.sh` has no equivalent gate** — port it over if a JMeter canonical sweep ever shows cold-start failures.

**Topology and load transport:**
- The retained service and AI result grids use `TOPOLOGY=native-local`: host-native Postgres, Redis, and dev Vault; host application JVMs; host JMeter/k6; no Docker on the measured path.
- `TOPOLOGY=local-app` keeps infrastructure in Docker and applications on the host. `TOPOLOGY=all-docker` runs both through Compose. Both remain supported, but cells from different topologies or load-tool transports must never be pooled.
- `testing/performance/lib/topology.sh` integrates native-local with `deployment/local/local-infra.sh` and `./run.sh local`. Native Postgres must use the short `/tmp` socket root and `LC_ALL=C`; seeders use the shared host-client `bench_hygiene` primitives.
- k6 `--mode local|docker` is independent of application topology. Native-local requires local mode. Docker mode is reserved for an explicitly selected container transport.
- JMeter runs on the host. Do not containerize it without evidence that the host transport is limiting the run.
- Compact smoke profiles are safe locally; full-grid and confidence profiles use the exact workload wrapper definitions in `testing/performance/README.md`.

**Cross-stack symmetry:** async and reactive cells for one workload must use the same application topology, load-tool transport, input data, duration, and tuning values.
## Testing

The `benchmark` profile is a JVM-tuning and plaintext-path profile; load tools call backend services directly. Under `native-local`, applications connect to host-native Postgres and Redis on the published localhost ports and obtain boot secrets from the local dev Vault. Under `local-app`, those ports are published by Docker infrastructure. Under `all-docker`, applications use Compose service names and internal ports. Every application defaults to `-Xmx4g`; `-Xms` remains at the JVM default. Service-specific `*_JAVA_TOOL_OPTIONS` overrides are allowed only for an isolated experiment and must be applied symmetrically unless the measured mechanism itself is stack-specific.

### Functional-equivalence (FE) test suite

Cucumber 7 suite under `testing/functional-equivalence/` runs the **same `.feature` files** against both async and reactive stacks. Profile `functional-equivalence` (alongside `prod` / `benchmark`) layers a JaCoCo agent via `deployment/docker-compose/functional-equivalence.yml`; `prod` and `benchmark` paths are untouched and see no instrumentation. The suite goes through the gateway (port 8080) so JWT validation is exercised. Run on either stack with:

```bash
mvn -f testing/functional-equivalence/pom.xml verify -Pasync
mvn -f testing/functional-equivalence/pom.xml verify -Preactive
```

(`mvn -pl testing/functional-equivalence …` does not work from repo root because there is no parent reactor POM. Use the `-f` form, or `cd testing/functional-equivalence && mvn …`.)

Both maven invocations bring up the stack via `./run.sh runtime up <stack> functional-equivalence` in a Cucumber `@BeforeAll`, wait for all 4 services to report healthy, run the scenarios, then tear down. **400 scenarios pass on both stacks** (113 user-service / 106 tweet-service / 130 interaction-service / 35 gateway / 16 cross-service). The source-of-truth count comes from the `.feature` files under `src/test/resources/features/` only:

```bash
find testing/functional-equivalence/src/test/resources/features -name '*.feature' \
  | xargs grep -c '^\s*Scenario' | awk -F: '{s+=$2} END {print s}'
```

(Including `target/test-classes/` would double-count after a build.) See `testing/RESULTS.md` §1.2 for the area breakdown.

**Iteration helpers:** `StackProfile.current()` reads the `-P<stack>` Maven profile (or the `fe.stack` system property) and selects which compose stack `ComposeLifecycle` brings up — add a new stack by extending `StackProfile` and the `ComposeLifecycle.run(...)` invocation; do not add stack-specific tags to `.feature` files. Set `-Dfe.repo.root=…` to point Cucumber at a different repo checkout, or `-DFE_REUSE_STACK=true` to skip the up/down cycle when iterating against an already-running stack.

Coverage-gate verification: `./testing/functional-equivalence/coverage-gate.sh <unit|cucumber> [<async|reactive>]`. The script reads each service module's JaCoCo CSV (unit mode) or aggregates the in-container `.exec` files dropped under `testing-results/functional-equivalence/jacoco/<stack>/` during functional-equivalence runs (cucumber mode), and asserts both line + branch coverage are ≥ 90 % per service per stack. Unit mode uses the raw service module denominator; cucumber mode uses the FE black-box boundary denominator so HTTP-unreachable boot-time/fault-injection/config internals do not cap the signal. Override the threshold with `COVERAGE_THRESHOLD=0.85` etc.

**Coverage policy:**
- **Unit-only gate is the load-bearing structural gate** and is enforced at `≥ 0.90 line + branch` on every service module on both stacks. All 8 modules currently PASS — see `testing/RESULTS.md` §1.1 for the per-module numbers.
- **Cucumber-only gate passes at `≥ 0.90 line + branch` on every service module on both stacks** using the FE black-box boundary denominator. The FE proof surface is the **unit gate + 400/400 Cucumber behavior pass + Cucumber coverage gate + per-service test-count parity**.

### Centralized results and maintenance

`testing/RESULTS.md` is the source of truth for current FE counts, coverage, complete service-workload grids, the AI streaming grid, effective methodology, and maintenance rules.

Update it when:
- a `@Test` is added or removed: recount §1.1 and keep each async/reactive service pair within 5%;
- a Cucumber scenario changes: recount source feature files and update §1.2;
- a benchmark is rerun: replace the affected complete table, never append a partial or mixed-topology grid;
- methodology changes: update the methodology section before replacing numbers.

Current counts are async **1210**, reactive **1214**, and **400** Cucumber scenarios. New performance work must keep failed-request latency separate, report dropped arrivals, derive confidence from per-run tail latency, use Mann–Whitney U as the async/reactive significance gate, pin the benchmark JVM/GC posture, and seed fixtures from the harness rather than application startup.

### Benchmark result-dir cleanup contract (DON'T SKIP, disk-protective)

[`testing/performance/README.md`](testing/performance/README.md#result-dir-cleanup-dont-skip--fills-disks-fast) owns the cleanup lifecycle, preserved-file list, failure behavior, and `KEEP_RAW=1` escape hatch. Agents must additionally enforce this after every benchmark run:

1. Find the latest result dir: `ls -dt testing-results/performance/{jmeter,k6}/results_*/ 2>/dev/null | head`.
2. If `*.jtl` or `*_filtered.jtl` files survived inside it AND the sweep is over (no active `run.sh`), the wrapper trap failed — delete them: `find testing-results/performance -name '*.jtl' -delete`.
3. If the operator cancelled mid-sweep and a partial dir survived, `rm -rf` the entire dir (it has incomplete data).
4. Never let raw `.jtl` accumulate — disk fills up fast and the data is already in `cells.csv`.

### AI streaming benchmark

The AI surface lives in tweet-service on both stacks:

- **Endpoints:** under `/tweets/ai/users/{userId}/conversations/{conversationId}`: `GET /mock-stream` (W0 non-AI SSE), `POST /summarize` (W1 chat), `POST /buffered` (W1 buffered control), and `POST /summarize-with-tool` (W2 chat with a mid-stream user-summary call).
- **Backend:** `AI_BACKEND=mock` registers the calibrated `MockStreamingChatModel`; `AI_BACKEND=live` uses Spring AI's `OpenAiChatModel`. Native-local live probes use host-local `mlx_lm.server` at `http://localhost:8081`.
- **Model pin:** `mlx-community/Qwen3.5-4B-MLX-4bit`, Hugging Face revision `32f3e8ecf65426fc3306969496342d504bfa13f3`; `model.safetensors` sha256 `5fb9acd0246866381cf8c5c354c6db1019f6498eec4ccb4f5edcc71ffeacb2db`; `config.json` sha256 `f3efc81b2ea8d96a45301037d3ccccbcccdef44a961845c87f286aaddbc6eaaa`.
- **Calibration:** tag `qwen-3.5-4b-mlxlm-v2`; checked-in `calibration.json` sha256 `bd67945e84987a5eafbf5fc6f5c7fdeaa0e8266675d740f83637c3c4ac0ff7c6`. The calibration Maven module collects TTFT/ITL samples, fits candidate distributions, and validates mock output. Run its jar with `--help` for current flags.
- **Async executor:** base pool 200, queue 0, `caller-runs`; benchmark cells pass the measured pool/queue values and use `abort`. The retained primary grid uses T=400 and Q=4000. The runner asserts executor, backend, and token configuration from Prometheus before load.
- **Input:** W2 uses seeded UUID `00000000-0000-0000-0000-000000000001`; seeding is idempotent and owned by `prepare.py`.
- **Load:** the retained W0/W1/W2 primary grid uses target rates `10 25 50 75 100 125 150 175 190 200 210 225 250 300 350 400 500 650`, open-loop ramping to constant arrival rate, 60-second warmup, and 180-second measurement.
- **Cell identity:** stack, workload, transport, target rate, pool, reject policy, cancellation rate, calibration, campaign, output tokens, prompt variant, and backend. These analysis dimensions prevent accidental pooling; they are operational metadata rather than result labels.
- **Analysis:** the Maven analysis module ingests k6 summaries, calculates per-run-p99 bootstrap intervals and Mann–Whitney U comparisons, and generates CSV/PNG outputs. Run each subcommand with `--help`.
- **Live backend:** `testing/performance/k6/workloads/ai-stream-summarize/realism-backend.sh up|down|status` manages host `mlx_lm.server`; it stays outside Compose because Docker Desktop cannot expose Apple Metal.

### Pinned versions (benchmark reproducibility — everything we ran the benchmark matrix on)

| Layer | Component | Pinned version | Where |
|---|---|---|---|
| Host | macOS | 26.4.1 (build 25E253) | — |
| Host | Docker Engine / Client | 29.4.0 / 29.4.0 | — |
| Host | docker compose | 5.1.1 | — |
| Host | k6 | v1.7.1 | `/opt/homebrew/bin/k6` |
| Host | JDK | Temurin 21.0.7 | `sdkman` (also baked into Dockerfiles) |
| Host | Maven | 3.9.10 (3.9.9 inside Docker builds) | `sdkman` |
| Host | LM Studio | app `0.4.12+1`, CLI commit `0b2a176` | — |
| Compose | `postgres` | `16.4-alpine` | `deployment/docker-compose/infrastructure.yml` |
| Compose | `redis` | `7.2.5-alpine` | same |
| Compose | `toxiproxy` | `ghcr.io/shopify/toxiproxy:2.9.0` | same |
| Compose | `curlimages/curl` | `8.7.1` | same |
| Dockerfile | JRE | `eclipse-temurin:21-jre-jammy` | each service `Dockerfile` |
| Dockerfile | Maven builder | `maven:3.9.9-eclipse-temurin-21` | each service `Dockerfile` |
| Spring Boot | parent | `3.3.2` | all service POMs |
| Spring AI | BOM | `1.0.1` | `tweet-service` POMs, property `spring-ai.version` |
| Spring Cloud | dependencies | `2023.0.3` (both gateways) | gateway POMs |
| Java | language level | `21` everywhere | POM `<java.version>` |
| Pinned libs | `lombok` `1.18.30`, `mapstruct` `1.5.5.Final`, `lombok-mapstruct-binding` `0.2.0`, `auth0:java-jwt` `4.3.0`, `r2dbc-postgresql` `1.0.7.RELEASE`, `r2dbc-pool` `1.0.1.RELEASE`, `resilience4j-*` `2.2.0`, `commons-math3` `3.6.1`, `jackson-databind` `2.17.2` (analysis/calibration modules), `picocli` `4.7.6`, `xchart` `3.8.8`, `maven-shade-plugin` `3.6.0`, `maven-compiler-plugin` `3.12.1` | | service POMs |
| LLM | `mlx-community/Qwen3.5-4B-MLX-4bit` via `mlx_lm.server`; revision and SHA-256 pins are listed in the AI section above; calibration tag `qwen-3.5-4b-mlxlm-v2`. | see AI streaming section | — |

Transitive deps inherited from `spring-boot-starter-parent:3.3.2` (Netty, Reactor, Micrometer, Jackson, etc.) are fixed via Spring Boot's BOM — bumping the parent is the only way they shift. Both gateways run Spring Boot 3.3.2 / Java 21 / Spring Cloud 2023.0.3 (async on `spring-cloud-starter-gateway-mvc`, reactive on `spring-cloud-starter-gateway`); the gateway sits off the benchmark path (k6/JMeter hit services directly on 9091/9092/9093), so its framework version has no bearing on the benchmark numbers.

### Benchmark code map

[`testing/performance/README.md`](testing/performance/README.md) owns engine commands, profiles, output paths, and workload orchestration. Preserve these implementation mappings when editing code or workload wrappers:

- `cacheread-following` targets the Redis-backed following-ID read on interaction-service port 9093.
- `blockio-file-download` targets throttled `GET /media/{id}` on user-service port 9091.
- `cpu-image-preview` targets `POST /media/{srcId}/preview` on user-service port 9091.
- `http-fanout-get-tweet` targets `GET /tweets/{tweetId}` on tweet-service port 9092 and fans out to interaction-service and user-service.
- `ai-stream-summarize` targets the AI controller under `/tweets/ai/users/{userId}/conversations/{conversationId}` on tweet-service port 9092.

All benchmark workloads deliberately hit service ports 9091–9093 directly, bypassing gateway routing and JWT validation. There is no `/media/**` gateway route. Generated output belongs under gitignored `testing-results/`, never under `testing/`.
