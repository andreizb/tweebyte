# Tweebyte

> **AI agents — read [`AGENTS.md`](AGENTS.md) first.** It is the single canonical guide for this repository (commands, conventions, pinned versions, benchmark scope, JVM-flag overrides). Do **not** create agent-specific files (`CLAUDE.md`, `GEMINI.md`, `CODEX.md`, etc.); `AGENTS.md` applies universally.

Tweebyte is a Twitter-like microblogging platform built as a Java microservices system, with two parallel implementations of the same API surface — an `async/` stack (Spring Web + Spring Data JPA, blocking) and a `reactive/` stack (Spring WebFlux + R2DBC, non-blocking). The two stacks are deliberately matched feature-for-feature so the runtime behaviour of each concurrency model can be compared under identical workloads, identical input data, and identical observability.

## Architecture

Both stacks ship the same four microservices on the same ports, against per-service Postgres databases plus a shared Redis cache.

| Service | Port | Async stack | Reactive stack |
|---|---:|---|---|
| **gateway-service** | 8080 | Zuul (Spring Boot 2.7.18, Java 11 — pinned) | Spring Cloud Gateway (Spring Boot 3.3.2) |
| **user-service** | 9091 | Spring Web + JPA, JWT auth | Spring WebFlux + R2DBC, JWT auth |
| **tweet-service** | 9092 | Spring Web + JPA + Spring AI 1.0.1, Redis cache | Spring WebFlux + R2DBC + Spring AI 1.0.1, Redis cache |
| **interaction-service** | 9093 | Spring Web + JPA, JDK `HttpClient` | Spring WebFlux + R2DBC, `WebClient`, Resilience4j |

Inter-service calls are wrapped behind per-service client classes (`UserClient`, `InteractionClient`, `TweetClient`). The async stack uses a bounded `ThreadPoolExecutor` (sized via `APP_CONCURRENCY_TWEET_POOL_SIZE`, default 200, swept up to 1600 in benchmarks); the reactive stack uses Netty event loops. JWT signing keys are keystore-backed; MapStruct handles entity↔DTO mapping.

`AGENTS.md` carries the full per-service layout, env-var matrix, and the pinned-version table (Spring Boot, Spring AI, Java, Docker, k6, model checkpoint SHA-256).

## What's in the repo

- **Both microservice stacks** under `async/` and `reactive/` — each contains its own gateway + three services + per-service Maven pom + Dockerfile.
- **Compose profiles** (`infrastructure/compose/`) — `prod` for normal operation, `benchmark` for performance runs (toxiproxy in front of Redis, GC logging, configurable executor sizing, calibrated mock LLM bind-mount).
- **Functional + integration test surface** (Spring `@Test`, MockMvc/WebTestClient, MapStruct contract tests) — unit tests run via `mvn test` per service.
- **Benchmark workloads** (`testing/performance/k6/workloads/`) — open-loop k6 scripts covering:
  - microblogging CRUD scenarios
  - Redis-backed cache reads (`following-cache`)
  - blocking-I/O file download (`file-download`)
  - CPU-bound image processing (`image-upload`)
  - Spring AI streaming (`ai-streaming`) with three sub-workloads:
    - `W0` — non-AI SSE token-emitter baseline
    - `W1` — pure AI chat streaming
    - `W2` — AI chat streaming with a mid-stream blocking tool call
- **Calibration tooling** (`testing/calibration/`, Maven + picocli) — collects real-LLM TTFT/ITL samples from any OpenAI-compatible endpoint, fits five distribution families via Apache Commons Math (log-normal, gamma, Weibull, shifted log-normal, 2-component log-normal mixture), runs Kolmogorov-Smirnov mock-vs-real validation. Output: `testing/calibration/calibration.json` (1973 TTFT + ~168k ITL samples + fitted parameters; checked in, 1.7 MB, reproducibility-critical).
- **Analysis pipeline** (`testing/analysis/`, Maven + picocli + Apache Commons Math + XChart) — ingests k6 result directories into a flat CSV, computes per-cell bootstrap CIs on per-run p99 + Mann-Whitney U paired tests for async-vs-reactive, emits PNG figures and pgfplots-ready CSVs.
- **Live-LLM realism wrapper** (`infrastructure/realism-backend.sh`) — host-side lifecycle (`up`/`down`/`status`) for Apple `mlx_lm.server` running locally on macOS Metal, used for the AI-streaming workload's live-backend integration cells.

## Quick start

Prerequisites: JDK 21, Docker Desktop, Maven 3.9+, k6 ≥ 1.7 (only for benchmark workloads).

```bash
# Bring up the full async stack on the prod profile.
./run.sh runtime up async prod

# Or the reactive equivalent.
./run.sh runtime up reactive prod

# Stop without deleting volumes.
./run.sh runtime down async prod

# Run a service's unit tests.
cd async/tweet-service && mvn test
```

For the `benchmark` profile, the calibration workflow, the AI-streaming workload (`./run.sh bench k6 --workload ai-streaming`), the live-LLM realism subset, JVM heap overrides, and the full subcommand tree, see [`AGENTS.md`](AGENTS.md).

## Where the canonical numbers live

[`testing/RESULTS.md`](testing/RESULTS.md) is the single source of truth for every measurement and methodology decision in the repository — per-cell tables, bootstrap CIs, Mann-Whitney U verdicts, distribution fits, threats to validity, reproducibility recipes, and a maintenance checklist. Read this file when interpreting any benchmark output or when adding new cells.

`testing-results/` is the gitignored output root for k6 / JMeter runs and figures. The on-disk `testing/calibration/calibration.json` is version-controlled because the fitted parameters drive the calibrated mock used by the AI-streaming workload.

## License

[MIT](LICENSE).
