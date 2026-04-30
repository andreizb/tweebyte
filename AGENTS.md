# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository.

> **For all AI coding agents:** `AGENTS.md` is the single canonical guide for this repo. Do **not** create agent-specific guidance files; everything project-wide lives here and applies universally.

## Directory Docs

Subsystem-specific details live in directory-local READMEs:

- [`infrastructure/README.md`](infrastructure/README.md) — runtime profiles, compose overlays, dispatcher scripts, coverage gate.
- [`testing/README.md`](testing/README.md) — testing directory layout (index).
- [`testing/equivalence/README.md`](testing/equivalence/README.md) — Cucumber FE suite, stack profile selection, scenario count.
- [`testing/calibration/README.md`](testing/calibration/README.md) — live-backend sampling, distribution fitting, mock validation, `calibration.json` schema.
- [`testing/analysis/README.md`](testing/analysis/README.md) — k6 result ingestion, per-cell reporting, plotting, campaign manifest.
- [`testing/performance/README.md`](testing/performance/README.md) — k6 + JMeter benchmark surfaces (workloads, runners, output convention).
  - [`testing/performance/k6/README.md`](testing/performance/k6/README.md) — k6 cookbook for the `following-cache` benchmark.
- [`testing/RESULTS.md`](testing/RESULTS.md) — benchmark measurements, methodology, and maintenance rules.

This file is the command/convention hub; the READMEs above carry the per-subsystem detail.

## Project Overview

Tweebyte is a Twitter-like microblogging platform built as a Java microservices architecture. It has two parallel implementations: **async** (servlet-based) and **reactive** (WebFlux/R2DBC-based).

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

# Functional-equivalence test mode (Cucumber suite under testing/equivalence/)
# — layers a JaCoCo agent into each service JVM via the fe-test.yml overlay.
# ZERO impact on prod / benchmark paths.
./run.sh runtime up async fe-test
./run.sh runtime up reactive fe-test
mvn -f testing/equivalence/pom.xml verify -Pasync
mvn -f testing/equivalence/pom.xml verify -Preactive

# Stop without deleting volumes/images
./run.sh runtime down async benchmark

# Destructive teardown
./run.sh runtime destroy reactive prod

# Inspect running services
./run.sh runtime ps async
./run.sh runtime logs reactive --tail 100

# Run benchmark suites
./run.sh bench k6 --help
./run.sh bench jmeter --help

# Prepare benchmark payloads
./run.sh prepare k6 --help
./run.sh prepare jmeter --help

# Build a service
cd async/user-service && mvn clean install
cd reactive/tweet-service && mvn clean install

# Run a single service locally
./run.sh local async user-service prod

# Run the benchmark target locally with the validated benchmark JVM flags
./run.sh local reactive interaction-service benchmark

# Run all tests in a service
mvn test

# Run a single test class
mvn test -Dtest=UserControllerTest

# Run a single test method
mvn test -Dtest=UserControllerTest#testGetUserProfile
```

## Architecture

Both `async/` and `reactive/` contain the same four microservices:

| Service | HTTP port (container/host) | Database (host port) | Purpose |
|---------|------|-----------------|---------|
| **gateway-service** | 8080 | — | API gateway (Zuul for async, Spring Cloud Gateway for reactive) |
| **user-service** | 9091 | user_service_db (54321) | User management, JWT auth |
| **tweet-service** | 9092 | tweet_service_db (54322) | Tweets, hashtags, mentions, AI streaming, feed/user-tweets caches |
| **interaction-service** | 9093 | interaction_service_db (54323) | Likes, retweets, replies, follows, recommendations, follow/popular/user-summary caches |

Each service follows a standard layered package structure under `ro.tweebyte.<service>`:
`controller/` → `service/` → `repository/` with `entity/`, `model/` (DTOs), `mapper/` (MapStruct), `config/`, `exception/`, `client/` (inter-service HTTP), `util/`.

### Key Differences Between Stacks

- **Async**: Spring Web, Spring Data JPA/Hibernate (`ddl-auto=update`), `java.net.http.HttpClient` (`send()` blocking for `UserClient`, `sendAsync()` + `thenApplyAsync` for `InteractionClient`) — mixed pattern, deliberate. Custom `ThreadConfiguration` for executor pools.
- **Reactive**: Spring WebFlux, Spring Data R2DBC (no Hibernate), WebClient returning `Mono`/`Flux`, Resilience4j circuit breakers.

Inter-service URLs, DB hosts, and cache hosts are injected via env vars (`USER_SERVICE_URL`, `TWEET_SERVICE_URL`, `INTERACTION_SERVICE_URL`, `DB_HOST`/`DB_PORT`, `CACHE_HOST`/`CACHE_PORT`) — set by `run.sh dispatch_local` for local runs and by the compose files for containerized runs. Nothing is hardcoded in code.

## Key Libraries & Patterns

- **Java 21**, **Spring Boot 3.3.2** (services), **Spring Boot 2.7.18** (gateway)
- **JWT**: Auth0 java-jwt (v4.3.0) with keystore-based signing; validated via `JwtRequestFilter`
- **MapStruct** (v1.5.5.Final) for entity↔DTO mapping
- **Lombok** for boilerplate reduction (`@Data`, `@Builder`)
- **Redis** for service caches (followed-id feed fallback in tweet-service; following/followed, recommendations, popular-users, popular-hashtags, user-summary in interaction-service). Containers use `redis:6379`; the host-published port is `localhost:63790`. Benchmark interaction-service traffic can route through toxiproxy on `26379`.
- Inter-service communication via per-service client classes (`UserClient`, `InteractionClient`, `TweetClient`). Async wraps `java.net.http.HttpClient` + `ClientUtil` JSON parsing. Reactive uses `WebClient` directly.

## Testing

The `benchmark` profile is not just a JVM-tuning flag: it swaps the interaction-service's Redis target to **toxiproxy** (`INTERACTION_CACHE_HOST=toxiproxy`, port `26379`) so latency/fault injection can be layered on top of Redis, and it caps the containerized interaction-service at a 3g heap (`INTERACTION_JAVA_TOOL_OPTIONS="-Xms3g -Xmx3g -XX:+AlwaysPreTouch"`) by default. **For the AI streaming benchmark + the §5.13 realism subset, override the heap caps to 1500 MB max** (`-Xms1g -Xmx1500m`) — the default `-Xms3g` collides with the 4 GB compose `mem_limit` once thread stacks + Spring AI + Hibernate metaspace are accounted for, producing OOM-kills mid-batch. `infrastructure/run.sh` honours the `TWEET_/INTERACTION_JAVA_TOOL_OPTIONS` env override; export the cap before `runtime up` (also documented in `infrastructure/realism-backend.sh`'s comment block + RESULTS.md §5.13.6). Local benchmark runs via `./run.sh local ... benchmark` use a 4g heap on the host JVM instead. `prod` profile bypasses toxiproxy and talks to Redis directly (`redis:6379` from containers, host-published as `localhost:63790`).

### Functional-equivalence (FE) test suite

Cucumber 7 suite under `testing/equivalence/` runs the **same `.feature` files** against both async and reactive stacks. Profile `fe-test` (alongside `prod` / `benchmark`) layers a JaCoCo agent via `infrastructure/compose/fe-test.yml`; `prod` and `benchmark` paths are untouched and see no instrumentation. The suite goes through the gateway (port 8080) so JWT validation is exercised. Run on either stack with:

```bash
mvn -f testing/equivalence/pom.xml verify -Pasync
mvn -f testing/equivalence/pom.xml verify -Preactive
```

(`mvn -pl testing/equivalence …` does not work from repo root because there is no parent reactor POM. Use the `-f` form, or `cd testing/equivalence && mvn …`.)

Both maven invocations bring up the stack via `./run.sh runtime up <stack> fe-test` in a Cucumber `@BeforeAll`, wait for all 4 services to report healthy, run the scenarios, then tear down. **316 scenarios pass on both stacks** (85 user-service / 99 tweet-service / 106 interaction-service / 11 gateway / 15 cross-service). The source-of-truth count comes from the `.feature` files under `src/test/resources/features/` only:

```bash
find testing/equivalence/src/test/resources/features -name '*.feature' \
  | xargs grep -c '^\s*Scenario' | awk -F: '{s+=$2} END {print s}'
```

(Including `target/test-classes/` would double-count after a build.) See `testing/RESULTS.md` §1.2 for the area breakdown.

Coverage-gate verification: `./infrastructure/coverage-gate.sh <unit|cucumber> [<async|reactive>]`. The script reads each service module's JaCoCo CSV (unit mode) or aggregates the in-container `.exec` files dropped under `testing-results/equivalence/jacoco/` during fe-test runs (cucumber mode), and asserts both line + branch coverage are ≥ 90 % per service per stack. Override the threshold with `COVERAGE_THRESHOLD=0.85` etc.

**Coverage policy:**
- **Unit-only gate is the load-bearing one** and is enforced at `≥ 0.90 line + branch` on every service module on both stacks. All 8 modules currently PASS — see `testing/RESULTS.md` §1.1 for the per-module numbers.
- **Cucumber-only gate runs in advisory mode.** Both gateway modules clear it; the six service modules don't, capped by structural ceilings on classes that aren't HTTP-reachable: Resilience4j `TransientFailureClassifier` (only fires inside the retry path), `MockCalibration` / `MockStreamingChatModel` boot-time JSON parse + gamma sampling, `AiLatencyMetrics` / `PoolOccupancyMetrics` outcome tagging, `CleanupService` `@Scheduled` tick, `MediaService.isBenignClientAbort` socket-abort branches, client 5xx-arms. None of these branches are reachable from black-box gateway HTTP without fault injection or test-only endpoints. The FE proof surface is the **combined unit + 316/316 cucumber** (all `@Test` methods + all Cucumber scenarios + integration tests).

### Centralized test results, methodology, FE status (READ THIS WHEN A TEST OR BENCHMARK CHANGES)

`testing/RESULTS.md` is the single source of truth for: per-cell numbers across the benchmark result sets, current FE counts, parity, and coverage gates, the methodology each result set runs under (JMeter CRUD pass — JMeter + VisualVM, single shot; k6 service-workload pass — k6 + Actuator, 5 reps + CI95; AI-streaming pass — open-loop k6 + per-run p99 bootstrap CI + MW-U), the rerun checklist that lists what each result set adopts on its next run, and a maintenance checklist. Update RESULTS.md every time:
- a `@Test` is added or removed on either stack — re-run the count snippet in §1.1 and update the table.
- a benchmark sweep is rerun — append the cell-by-cell numbers to the relevant §3 (caching/CPU/blocking-I/O) or §5 (AI streaming) section using the documented column shape.
- methodology shifts (new metric, new stat method, new load tool) — patch §4 first and link from the affected result table.
- a Cucumber scenario is added or removed — re-run the count snippet in §1.2.
- the per-stack `@Test` count divergence exceeds 5% on any single service — restore parity before merge by adding tests on the missing side. Counts: async **782**, reactive **774**, per-service gaps gateway 0% / user 1.8% / tweet 3.1% / interaction 4.0% — all under the 5% threshold. Per-service parity is the load-bearing goal. `testing/RESULTS.md` §1.1 carries the per-service breakdown.

Per-stack methodology invariants (also documented in `testing/RESULTS.md` §4): always use open-loop arrival-rate executors for new benchmarks, never average p99 across runs (only mean of per-run p99s with bootstrap CI), record failed-request latency to a separate Trend so reject-policy 5xx don't compress success p99, gate significance on Mann-Whitney U (Welch's t-test is computed for reference only; MW-U is the gate), pin GC log on benchmark profile, keep `BenchmarkDataInitializer` `@Profile("benchmark")`-gated so prod boots don't seed fixtures.

### AI streaming benchmark

The Spring-AI-integrated AI streaming workload adds a fourth class to the benchmark matrix, scoped to `tweet-service` on both stacks:

- **Endpoints** under `/tweets/ai/` — `mock-stream` (W0 non-AI baseline), `summarize` (W1 pure chat), `buffered` (REST control), `summarize-with-tool?userId=<uuid>` (W2 mid-stream blocking `UserClient.getUserSummary` call — the H1 measurement surface).
- **Backend switch** via `AI_BACKEND={mock|live}`. `mock` registers a calibrated `MockStreamingChatModel implements ChatModel` (log-normal TTFT, zero-inflated gamma ITL with `p_burst`) as `@Primary`. `live` drops the mock bean and lets Spring AI auto-config create `OpenAiChatModel` pointed at the live OpenAI-compatible endpoint configured via `LIVE_LLM_BASE_URL` (default `http://host.docker.internal:1234` in Docker, `http://localhost:1234` local; for the §5.13 realism subset override to `http://host.docker.internal:8081` for `mlx_lm.server`). Default `LIVE_LLM_MODEL=qwen3.5-4b-mlx` matches the identifier LM Studio exposes on `/v1/models`; for `mlx_lm.server` use the on-disk model path. `LIVE_LLM_MAX_TOKENS` (default 200) caps response length so per-request service time stays under the live backend's sustainable throughput ceiling.
- **LM Studio pinned config (benchmark reproducibility):**
  - LM Studio app version: `0.4.12+1`; LM Studio CLI commit: `0b2a176`
  - Model repo: [`mlx-community/Qwen3.5-4B-MLX-4bit`](https://huggingface.co/mlx-community/Qwen3.5-4B-MLX-4bit) (MLX 4-bit, ~3.06 GB on disk, `qwen3_5` arch)
  - HuggingFace revision (git commit): `32f3e8ecf65426fc3306969496342d504bfa13f3` (HF last-modified: 2026-03-02). This is the canonical revision pin — `git clone https://huggingface.co/mlx-community/Qwen3.5-4B-MLX-4bit && git checkout 32f3e8e` reproduces the exact model.
  - `model.safetensors` sha256 (tamper-evident): `5fb9acd0246866381cf8c5c354c6db1019f6498eec4ccb4f5edcc71ffeacb2db`
  - `config.json` sha256: `f3efc81b2ea8d96a45301037d3ccccbcccdef44a961845c87f286aaddbc6eaaa`
  - Model id exposed on `/v1/models`: `qwen3.5-4b-mlx` (← use this string in `LIVE_LLM_MODEL` env)
  - Context length: 8192; temperature: default (see `spring.ai.openai.chat.options.temperature` in `application.properties`)
- **Thread-pool sweep** via `APP_CONCURRENCY_TWEET_POOL_SIZE={200|400|800|1600}` (async only), `APP_CONCURRENCY_TWEET_QUEUE_CAPACITY`, `APP_CONCURRENCY_TWEET_REJECT_POLICY={abort|caller-runs|discard|discard-oldest}` (benchmark profile defaults to `abort`).
- **Seeded benchmark user** UUID `00000000-0000-0000-0000-000000000001` auto-inserted by `BenchmarkDataInitializer` in `user-service` on startup so W2 has a known tool-call target.
- **Calibration**: `testing/calibration/` (Maven module) collects real-LLM samples from LM Studio, fits distributions via Apache Commons Math, emits `calibration.json`. Point `AI_MOCK_CALIBRATION_JSON=/path/to/calibration.json` at it to replace mock defaults on startup. The fitter emits five families per ITL distribution (log-normal, gamma, Weibull, shifted log-normal, 2-component log-normal mixture); the production mock consumes `gamma` only — see RESULTS.md §5.2.1 for the bimodal/comb-quantization empirical finding that motivated the alternative families. The K-S validator accepts `--itl-family={gamma,shifted_lognormal,lognormal_mixture}` to compare candidate families.
- **k6 workload**: `./run.sh bench k6 --workload ai-streaming --ai-workload W1 --ai-target-rps 500 --concurrencies "500" --runs 5 --warmup 60s --duration 180s --ai-calibration-tag qwen-3.5-4b-mlx-v1 --ai-campaign headline-5rep-2026-04-28`. Open-loop `ramping-arrival-rate` → `constant-arrival-rate`. Pool-size tag propagates to the JSON summary via `--ai-pool-size-tag N`. The `--ai-calibration-tag` and `--ai-campaign` flags are *cell-key dimensions* — they prevent cross-batch pooling in the analysis pipeline. Valid `WORKLOAD × TRANSPORT` combos: `W0 × {sse}`, `W1 × {sse, buffered}`, `W2 × {sse}` only. W2 + buffered is rejected at k6 start — W2 measures a mid-stream tool call, which a buffered REST response has no stream to interrupt. W0 + buffered is also rejected — W0 is the non-AI mock-stream baseline (`/tweets/ai/mock-stream`) with no buffered counterpart endpoint.
- **Analysis**: `testing/analysis/` (Maven module) ingests k6 result directories → per-cell bootstrap CIs on per-run p99 → PNG figures (concurrency scaling, pool-size scaling, H1 validation scatter) + plot-ready CSVs. Supports a `--campaign-manifest` TSV that overrides campaign labels for analysis grouping.
- **Realism subset (§5.13)**: live-backend integration check via Apple's `mlx_lm.server` (same MLX-4bit weights as the calibration source, served via continuous batching since LM Studio's MLX runtime hard-rejects `--parallel > 1` for vision-architecture models). Host-side wrapper `infrastructure/realism-backend.sh up|down|status` manages the lifecycle; can't go in `infrastructure/run.sh` because Docker Desktop on macOS cannot pass through Apple Silicon Metal. The realism subset's role is **integration evidence + runtime-comparability documentation**, NOT mock-vs-live distributional equivalence — see §5.13 in RESULTS.md for the explicit "supports / does not support" lists.

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
| Compose | `postgres` | `16.4-alpine` | `infrastructure/compose/infrastructure.yml` |
| Compose | `redis` | `7.2.5-alpine` | same |
| Compose | `toxiproxy` | `ghcr.io/shopify/toxiproxy:2.9.0` | same |
| Compose | `curlimages/curl` | `8.7.1` | same |
| Dockerfile | JRE | `eclipse-temurin:21-jre-jammy` (async gateway: `11-jre-jammy`) | each service `Dockerfile` |
| Dockerfile | Maven builder | `maven:3.9.9-eclipse-temurin-21` (async gateway: `-11`) | each service `Dockerfile` |
| Spring Boot | parent | `3.3.2` (async gateway: `2.7.18` — Zuul baseline) | all service POMs |
| Spring AI | BOM | `1.0.1` | `tweet-service` POMs, property `spring-ai.version` |
| Spring Cloud | dependencies | `2021.0.9` (async gateway), `2023.0.3` (reactive gateway) | gateway POMs |
| Java | language level | `21` everywhere except async gateway `11` | POM `<java.version>` |
| Pinned libs | `lombok` `1.18.30`, `mapstruct` `1.5.5.Final`, `lombok-mapstruct-binding` `0.2.0`, `auth0:java-jwt` `4.3.0`, `r2dbc-postgresql` `1.0.7.RELEASE`, `r2dbc-pool` `1.0.1.RELEASE`, `resilience4j-*` `2.2.0`, `commons-math3` `3.6.1`, `jackson-databind` `2.17.2` (analysis/calibration modules), `picocli` `4.7.6`, `xchart` `3.8.8`, `maven-shade-plugin` `3.6.0`, `maven-compiler-plugin` `3.12.1` | | service POMs |
| LLM | `mlx-community/Qwen3.5-4B-MLX-4bit`, HF rev `32f3e8ecf65426fc3306969496342d504bfa13f3`, `model.safetensors` sha256 `5fb9acd0246866381cf8c5c354c6db1019f6498eec4ccb4f5edcc71ffeacb2db`, `config.json` sha256 `f3efc81b2ea8d96a45301037d3ccccbcccdef44a961845c87f286aaddbc6eaaa` | see LM Studio pinned config above | — |

Transitive deps inherited from `spring-boot-starter-parent:3.3.2` (Netty, Reactor, Micrometer, Jackson, etc.) are fixed via Spring Boot's BOM — bumping the parent is the only way they shift. The async gateway remains on Java 11 + Spring Boot 2.7.18 for benchmark comparability; changing it invalidates the gateway baseline numbers.

`testing/` is the source-controlled test surface, and `testing-results/` is the gitignored output root. The k6 benchmark flow lives under `testing/performance/k6`: prepare inputs with `./run.sh prepare k6 ...` or `python3 testing/performance/k6/prepare_payload.py ...`, run workloads with `./run.sh bench k6 ...` or `./testing/performance/k6/run_bench.sh --workload ...`, keep benchmark outputs under `testing-results/performance/k6`, keep local benchmark JVM launches on the service process with `JAVA_TOOL_OPTIONS="-Xms4g -Xmx4g -XX:+AlwaysPreTouch"` plus `-Dspring-boot.run.optimizedLaunch=false`, and note that the workload names are intentionally semantic: `following-cache` is the Redis-backed I/O-bound interaction-service benchmark, `file-download` is the blocking-I/O download benchmark, and `image-upload` is the CPU-bound tweet-service `/media/filter` image-processing benchmark (hits port 9092 directly — there is no `/media/**` gateway route, and the workload deliberately bypasses gateway routing + JWT validation overhead, matching the other three direct-port workloads). The JMeter benchmark surface lives under `testing/performance/jmeter`: run plans with `./run.sh bench jmeter ...` or `./testing/performance/jmeter/run_bench.sh --workload ...`, keep `.jmx` plans under `testing/performance/jmeter/workloads`, generate workload CSVs with `./run.sh prepare jmeter ...` or `python3 testing/performance/jmeter/prepare_payload.py ...`, and keep JMeter outputs under `testing-results/performance/jmeter`. `./run.sh runtime up async benchmark` / `./run.sh runtime up reactive benchmark` inject `INTERACTION_JAVA_TOOL_OPTIONS="-Xms3g -Xmx3g -XX:+AlwaysPreTouch"` for the containerized interaction-service. The `./run.sh up ...` form is a compatibility alias for `./run.sh runtime up ...`.
