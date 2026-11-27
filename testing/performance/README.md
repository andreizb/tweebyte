# Performance benchmarks

Two load engines drive the perf surface. Each engine owns a top-level `run_bench.sh` driver plus per-workload subdirectories with their own `run.sh` and `prepare.py`.

The `benchmark` profile deliberately disables production security features and drives backend ports directly. Run it only on a trusted machine/network; see [`../../SECURITY.md`](../../SECURITY.md).

## Engines

| Engine | Location | When to use it |
|---|---|---|
| **JMeter** | `jmeter/` | CRUD, fan-out, serialization, and database-heavy workloads. Local-process load generator. |
| **k6** | `k6/` | Cache, CPU, blocking-I/O, HTTP fan-out, and AI-streaming workloads. |

Workloads are stored as semantically-named subdirectories under each engine's `workloads/` tree (e.g. `cacheread-following`, `cpu-image-preview`, `blockio-file-download`, `ai-stream-summarize`). Workload names describe the bottleneck being measured, not the endpoint.

## Drivers

```bash
./testing/performance/k6/run_bench.sh --help
./testing/performance/jmeter/run_bench.sh --help
```

Per-workload entry points (preferred over invoking the engine directly):

```bash
./testing/performance/k6/workloads/<name>/run.sh {smoke|canonical}
./testing/performance/jmeter/workloads/<name>/run.sh {smoke|canonical}
```

Workload payload generators:

```bash
python3 testing/performance/k6/workloads/<name>/prepare.py --help
python3 testing/performance/jmeter/workloads/<name>/prepare.py --help
```

`--help` is the source of truth for current flags and defaults.

## App Topology

The retained result grids in [`../RESULTS.md`](../RESULTS.md) use:

```bash
TOPOLOGY=native-local
```

That means PostgreSQL, Redis, Vault, application JVMs, and the load generator run on the host. Application services use the Spring `benchmark` profile through `./run.sh local`. `local-app` remains available when Docker-hosted infrastructure is useful, and `all-docker` runs the application stack in Compose:

```bash
TOPOLOGY=local-app ./testing/performance/jmeter/workloads/dbread-fanout-user-profile/run.sh smoke
TOPOLOGY=all-docker ./testing/performance/jmeter/workloads/dbread-fanout-user-profile/run.sh smoke
```

Rules:

- Keep async and reactive on the same `TOPOLOGY` within a workload.
- Do not pool cells produced with different topologies or load-tool transports.
- Result directories include `run_metadata.env` with `benchmark_topology=<value>`.
- k6's `--mode local|docker` is a separate load-tool transport axis. `native-local` requires host-side k6/JMeter against localhost.

## Smoke, full-grid, and confidence profiles

Service workload wrappers expose these shapes; run each wrapper with `--help` or without arguments to confirm its supported profile names:

| Profile | Concurrencies | Runs / cell | Warmup | Main | Wallclock per stack |
|---|---|---:|---|---|---|
| **smoke** | `10 25 50 100 200 400` (6 cells; compact `10 50 100` smoke for CPU-bound `cpu-image-preview`) | 1 | 30 s | 60 s | ~9–10 min |
| **canonical/full grid** | `1 5 10 15 20 25 50 75 100 200 400 600 800 1000` | 2 | 60 s | 120 s | ~1.9 h per stack |
| **confidence** | same 14 levels | 5 | 60 s | 180 s | ~4.7 h per stack |

Smoke confirms a change quickly. The full grid matches the retained service tables in `testing/RESULTS.md`; confidence runs add repetitions and a longer measured interval. **Wrap unattended full-grid or confidence runs with `caffeinate -dimsu`** on macOS.

AI streaming uses an open-loop 18-rate grid instead of closed-loop concurrency: `10 25 50 75 100 125 150 175 190 200 210 225 250 300 350 400 500 650`. Its runner owns the W0/W1/W2, executor-size, token-count, backend, and prompt variants.

## Outputs

> **Invariant.** Generated benchmark outputs, figures, logs, JTL files, payloads, and temporary analysis artifacts must not be committed. Run outputs belong under the gitignored `testing-results/` tree; workload-local generated `payload/` directories are gitignored and disposable.

Benchmark results land under `testing-results/` (gitignored at the repo root). One directory per run, time-stamped, with JMeter `cells.csv` or k6 engine-native artifacts:

```
testing-results/performance/k6/results_<workload>_<ts>_<hash>/
testing-results/performance/jmeter/results_<workload>_<ts>_<hash>/
```

The AI-stream-summarize analysis pipeline ingests k6 result directories into a flat CSV, computes per-cell bootstrap CIs on per-run p99 + Mann-Whitney U paired tests for async-vs-reactive, and emits PNG figures and plot-ready CSVs (`k6/workloads/ai-stream-summarize/analysis/`).

## Methodology principles

Load-bearing rules, documented with the retained measurements in [`../RESULTS.md`](../RESULTS.md):

- **Open-loop arrival-rate executors for new benchmarks.** Closed-loop conflates SUT response time with arrival rate at saturation.
- **Use per-run tail latency for inference.** Report the mean of per-run p99s with a bootstrap 95 % CI (10 k resamples); label it as that statistic, not as the p99 of pooled requests.
- **Significance gate: Mann-Whitney U** at α = 0.05 paired async-vs-reactive. Welch's t-test is computed for reference only.
- **Failed-request latency goes to a separate Trend** so reject-policy 5xx don't compress success p99.
- **GC log pinned on the `benchmark` profile** (configured in [`../../deployment/docker-compose/`](../../deployment/docker-compose/)).
- **Match comparable resources and controls across stacks.** Keep topology, transport, inputs, duration, and comparable tuning symmetric. Document stack-specific mechanisms explicitly: all three reactive backend services use `app.r2dbc.disable-colocation=true` and `app.r2dbc.pool.acquisition-scheduler.enabled=true` in the benchmark overlay. These controls have no servlet/JPA counterpart; see the architecture diagnostic controls.
- **Pre-run announcement.** Before starting a long full-grid or confidence sweep, show the exact invocation and expected wall clock.
- **`--help` is the CLI source of truth.** Flags drift; docs lag.

## Cross-workload validation

If service code, pool sizing, JVM flags, or benchmark properties change, smoke the affected workloads before replacing a result table:

| Surface changed | Workloads to smoke |
|---|---|
| user-service | user summary, user-profile fan-out, follow-create cache-miss path, file download, image preview |
| tweet-service | tweet update, bulk tweet summaries, tweet search, single-tweet fan-out, AI streaming |
| interaction-service | follow create, following cache, user-profile fan-out, single-tweet fan-out |
| gateway-service | functional-equivalence suite when gateway behavior is affected |
| shared infrastructure or JVM posture | every workload using the changed dependency |

[`../RESULTS.md`](../RESULTS.md) contains the complete retained grids. Replace an affected table only with a complete, topology-consistent rerun.

## Result-dir cleanup (DON'T SKIP — fills disks fast)

### The problem

JMeter raw samples per cell (`*.jtl` + `*_filtered.jtl`) typically reach 100 MB–1 GB at conc=1000. A 14-cell, five-run confidence sweep is 70 cells/stack × 2 stacks = 140 cells and can create roughly 70 GB of raw samples that are unnecessary after `cells.csv` is aggregated. The repo's `testing-results/` previously hit 43 GB from accumulated runs.

### Cleanup lifecycle

The engine drivers and workload wrappers use [`lib/cleanup.sh`](lib/cleanup.sh). Each sweep gets a private registry; each driver registers the exact directory it creates and records its writer processes. Cleanup never selects directories by modification time or by a global "latest run" search.

- After a successful JMeter aggregation, remove only that owned directory's raw `*.jtl` files, including `*_filtered.jtl`, after checking `cells.csv` contains data and all registered writers have exited.
- `KEEP_RAW=1` preserves raw files on success, failure, and cancellation.
- On failure or cancellation, retain incomplete results and diagnostic artifacts. Do not delete an entire result directory because it is partial. A surviving writer or missing aggregation leaves raw data intact and produces a cleanup notice.
- k6 text/JSON summaries and validation records are retained; k6 does not produce JTL files or a driver-level `cells.csv`. Its analysis pipeline aggregates the retained artifacts later.
- Completed results from earlier cells, other sweeps, or other operators remain outside the current run's cleanup authority.

### What stays

Keep `cells.csv`, `figures/*.png`, `summary.txt`, `*_jmeter.log`, `*_resources.csv`, `*_prom.csv`, `*_validation.txt`, `<conc>_<run>.txt`, k6 JSON summaries, `run_metadata.env`, and `boot-receipt.*`. The raw JMeter samples usually account for about 99% of completed-run disk usage; the retained logs and summaries are comparatively small.

### Operator escape hatch

```sh
KEEP_RAW=1 ./testing/performance/jmeter/workloads/dbwrite-light-follow-create/run.sh canonical
```

Use this when raw samples are needed for re-aggregation or alternative percentile/statistical analysis. Otherwise the driver removes raw JTL only after successful aggregation and writer exit.

### Recovery after interruption

Inspect the exact output path printed by the driver and its cleanup status. If aggregation or writer termination is incomplete, preserve the files and report the pending cleanup. Older unregistered results require an explicit, reviewed directory list and confirmation that their aggregates are usable and no process is writing. Do not use a repository-wide `find -delete`, an mtime cutoff, or a guessed latest directory as recovery authority.

## Load-tool execution modes

Preserve these measured failure modes and their mitigations when changing benchmark execution. Read this section when preparing or changing load-tool execution; it is not a prerequisite for unrelated tasks.

**Host-side fd limit (local k6 / local JMeter):**
Before invoking the local load tool, raise the shell open-file limit:
```bash
ulimit -n 65536
```
macOS default (~256-1024) is too low. Without this, high-concurrency runs emit `too many open files` / `lookup ... no such host` failures that look like service problems but are actually local fd starvation.

**Host-side listen backlog + servlet connector posture (canonical benchmark runtime posture):**
Before a benchmark that opens many concurrent/long-lived connections (AI streaming especially), inspect `sysctl -n kern.ipc.somaxconn`. The canonical value is 4096. If it already matches, no change is needed. Otherwise, reuse authorization already given for this host setting; if authorization is missing, finish independent preparation and request it only for the privileged change:
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
- Compact smoke profiles are safe locally; full-grid and confidence profiles use the exact workload wrapper definitions in the profile table above.

**Cross-stack symmetry:** async and reactive cells for one workload must use the same application topology, load-tool transport, input data, duration, and comparable tuning values. Document stack-specific mechanisms and their controls explicitly, as in the methodology principles above.

## Benchmark runtime posture

The `benchmark` profile is a JVM-tuning and plaintext-path profile; load tools call backend services directly. Under `native-local`, applications connect to host-native Postgres and Redis on the published localhost ports and obtain boot secrets from the local dev Vault. Under `local-app`, those ports are published by Docker infrastructure. Under `all-docker`, applications use Compose service names and internal ports. Every application defaults to `-Xmx4g`; `-Xms` remains at the JVM default. Service-specific `*_JAVA_TOOL_OPTIONS` overrides are allowed only for an isolated experiment and must be applied symmetrically unless the measured mechanism itself is stack-specific.

## AI streaming benchmark

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

## Pinned versions (recorded benchmark environment)

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
| LLM | `mlx-community/Qwen3.5-4B-MLX-4bit` via `mlx_lm.server`; revision and SHA-256 pins are listed in the AI streaming section above; calibration tag `qwen-3.5-4b-mlxlm-v2`. | see AI streaming section | — |

Transitive deps inherited from `spring-boot-starter-parent:3.3.2` (Netty, Reactor, Micrometer, Jackson, etc.) are fixed via Spring Boot's BOM — bumping the parent is the only way they shift. Both gateways run Spring Boot 3.3.2 / Java 21 / Spring Cloud 2023.0.3 (async on `spring-cloud-starter-gateway-mvc`, reactive on `spring-cloud-starter-gateway`); the gateway sits off the benchmark path (k6/JMeter hit services directly on 9091/9092/9093), so its framework version has no bearing on the benchmark numbers.

## Benchmark code map

Preserve these mappings when editing service code or workload wrappers:

- `cacheread-following` targets the Redis-backed following-ID read on interaction-service port 9093.
- `blockio-file-download` targets throttled `GET /media/{id}` on user-service port 9091.
- `cpu-image-preview` targets `POST /media/{srcId}/preview` on user-service port 9091.
- `http-fanout-get-tweet` targets `GET /tweets/{tweetId}` on tweet-service port 9092 and fans out to interaction-service and user-service.
- `ai-stream-summarize` targets the AI controller under `/tweets/ai/users/{userId}/conversations/{conversationId}` on tweet-service port 9092.

All benchmark workloads deliberately hit service ports 9091–9093 directly, bypassing gateway routing and JWT validation. There is no `/media/**` gateway route. Run outputs belong under gitignored `testing-results/`. The explicit exception is existing gitignored workload-local `payload/` directories used by seeders.

## Related docs

- [`../README.md`](../README.md) — testing surface overview.
- [`../RESULTS.md`](../RESULTS.md) — retained measurements and methodology.
- [`../../SECURITY.md`](../../SECURITY.md) — benchmark trust boundary and reporting.
- [`../../AGENTS.md`](../../AGENTS.md) — project-wide implementation rules and conditional documentation routing.
