# Deployment

Runtime entry point for the Tweebyte stacks. Docker Compose owns infrastructure and can still own full app stacks; benchmark workloads can also run app services as local JVMs against Docker-managed infra.

## Entry script

```bash
./run.sh runtime up   <infra|async|reactive> <prod|benchmark|functional-equivalence> [extra docker compose args...]
./run.sh runtime down <infra|async|reactive> <prod|benchmark|functional-equivalence> [extra docker compose args...]
./run.sh local up     <async|reactive> benchmark <service...|all>
./run.sh local down   <async|reactive> benchmark <service...|all>
deployment/local/local-infra.sh <up|down|ps|logs|env>
```

The repo-root `./run.sh` is the public dispatcher. Its `runtime` path forwards to `deployment/docker-compose/compose.sh`, the Docker Compose implementation that assembles the compose files below; use the root command in normal docs and scripts.

Common commands:

```bash
# Bring up infrastructure (Postgres, Redis, toxiproxy) only.
./run.sh runtime up infra prod

# Full async stack on the prod profile.
./run.sh runtime up async prod

# Full reactive stack on the benchmark profile.
./run.sh runtime up reactive benchmark

# Functional-equivalence profile (JaCoCo overlay) for the Cucumber suite.
./run.sh runtime up async functional-equivalence

# Backend + the Angular SPA (frontend.yml overlay). Use a profile that activates
# --profile full so Keycloak comes up — the SPA delegates login/register to it.
WITH_FRONTEND=1 ./run.sh runtime up async prod

# Stop without deleting volumes/images.
./run.sh runtime down async benchmark

# Destructive teardown (drops volumes/images).
./run.sh runtime destroy reactive prod

# Inspect.
./run.sh runtime ps async
./run.sh runtime logs reactive --tail 100

# Benchmark local-app topology: Docker infra, app JVMs local.
./run.sh runtime up infra benchmark
./run.sh local up async benchmark user-service tweet-service interaction-service
./run.sh local down async benchmark user-service tweet-service interaction-service

# Native-local benchmark infrastructure: local Postgres, Redis, and dev Vault.
# Do not run while Docker infra is using the benchmark datastore ports.
deployment/local/local-infra.sh up
deployment/local/local-infra.sh down
```

The `./run.sh up …` / `./run.sh down …` forms (without `runtime`) are compatibility aliases and still work.

For per-service local launches, see `./run.sh local --help`.

## Benchmark App Topologies

| Topology | How to select | What runs where |
|---|---|---|
| `native-local` | `TOPOLOGY=native-local` in performance wrappers | Ephemeral host Postgres, Redis, and dev Vault; host application JVMs through `./run.sh local`; host JMeter/k6. This is the topology used by the retained result grids. |
| `local-app` | `TOPOLOGY=local-app` | Postgres/Redis/Vault/toxiproxy run in Docker; application services run as local Maven Spring Boot processes on ports 8080/9091/9092/9093. |
| `all-docker` | `TOPOLOGY=all-docker` | Infrastructure and application services run through Docker Compose. |

The local application runner writes pid/log/effective-config receipts under `testing-results/runtime/local-app/<stack>/<service>/`. It refuses to start if the matching app container is running or the service port is already bound. Result directories include `run_metadata.env` with `benchmark_topology=...`. Async and reactive cells for one workload must use the same topology and load-tool transport.

### Native Hot-Path Infra

`deployment/local/local-infra.sh` provides the disposable host infrastructure used by `TOPOLOGY=native-local`:

- `user_service_db` on `127.0.0.1:54321`
- `tweet_service_db` on `127.0.0.1:54322`
- `interaction_service_db` on `127.0.0.1:54323`
- Redis on `127.0.0.1:63790`
- dev Vault on `127.0.0.1:8200`, seeded with `secret/tweebyte`

The shared performance topology layer starts it automatically for `TOPOLOGY=native-local`. It refuses to start if required ports are already bound, so it cannot silently collide with Docker-managed infrastructure. It creates disposable state under `testing-results/runtime/local-infra/active`; `down` removes that state by default.

Current boundaries:

- Databases are created empty. Application Flyway migrations own schema creation on app boot; the runner does not pre-apply schema SQL or spoof Flyway history.
- Seeders and hygiene scripts use the shared host-client primitives for `psql` and Redis when this topology is active.
- Application services still run with the normal Spring `benchmark` profile.
- Keycloak, Kafka, Sonar, Grafana, Loki, Jaeger, and Docker-managed toxiproxy are not started by native-local infrastructure.

## Compose files

The compose files live under `deployment/docker-compose/`:

| File | Purpose |
|---|---|
| `infrastructure.yml` | Postgres-per-service, shared Redis, toxiproxy. Brought up by every stack. |
| `async.yml` | The four async services. Layered on top of `infrastructure.yml`. |
| `reactive.yml` | The four reactive services. Layered on top of `infrastructure.yml`. |
| `functional-equivalence.yml` | JaCoCo agent overlay used by the Cucumber FE suite. Layered on top of `async.yml` / `reactive.yml` when the `functional-equivalence` profile is selected. |
| `diagnostics.yml` | JFR/JMX/async-profiler/heap-dump/JDWP profiling overlay. Opt-in via `DIAG=1`; layered on top of a stack to investigate a bad benchmark cell. |
| `frontend.yml` | Nginx-served Angular SPA, same-origin reverse-proxy to `gateway-service`. Opt-in via `WITH_FRONTEND=1`; layered on top of a stack. |
| `deploy.yml` | Pull-only deploy overlay: re-declares the four application services (and the frontend) image-only — no build context — so CI/CD pulls prebuilt images from a registry. Chain with `async.yml` / `reactive.yml`. |
| `toxiproxy-config.sh` | Helper script that programs toxiproxy proxies once the container is up. |

## Profiles

| Profile | Topology | Instrumentation |
|---|---|---|
| `prod` | infra + service stack. | None. Interaction-service talks to Redis directly. |
| `benchmark` | Selected independently as `native-local`, `local-app`, or `all-docker`; retained measurements use `native-local`. | Direct service HTTP plus direct DB/Redis wiring for the selected topology. Default app JVM cap is `-Xmx4g`; override service-specific `*_JAVA_TOOL_OPTIONS` only for an explicitly isolated experiment. |
| `functional-equivalence` | Same topology, layered with the JaCoCo overlay. | JaCoCo agent on each service JVM; coverage `.exec` files drop under `testing-results/functional-equivalence/jacoco/`. No toxiproxy, no JVM heap caps. |

The three profiles are mutually exclusive at runtime — `prod` and `benchmark` paths are never instrumented by FE; FE is never tuned by benchmark heap caps. See [`../AGENTS.md`](../AGENTS.md) §Testing for the full override matrix.

### Spring Property Files

`prod` is a deployment label, not a Spring property-file suffix. For app services, prod/default mode intentionally uses base `application.properties` with `SPRING_PROFILES_ACTIVE` empty. There is no `application-prod.properties`, and agents should not create one.

Benchmark mode sets `SPRING_PROFILES_ACTIVE=benchmark`, so Spring loads base `application.properties` plus `application-benchmark.properties`. Put prod/default behaviour in base; put benchmark-only feature neutralisation and measured benchmark defaults in the benchmark overlay.

## Ports

Mapped 1:1 container/host:

| Component | Port |
|---|---:|
| gateway-service | 8080 |
| user-service | 9091 |
| tweet-service | 9092 |
| interaction-service | 9093 |
| Postgres (`user_service_db`) | 54321 |
| Postgres (`tweet_service_db`) | 54322 |
| Postgres (`interaction_service_db`) | 54323 |
| Redis (host-published) | 63790 |
| Toxiproxy (Redis proxy, fault-injection opt-in) | 26379 |
| Toxiproxy admin API | 8474 |

## `--help` is the source of truth

`./run.sh --help`, `./run.sh local --help`, `./run.sh runtime --help`, and `./deployment/docker-compose/compose.sh --help` print the current syntax. Prefer running `--help` over relying on snippets in docs — flags drift, and this README is not regenerated automatically.

## Related docs

- [`../ARCHITECTURE.md`](../ARCHITECTURE.md) — services, ports, invariants.
- [`../testing/README.md`](../testing/README.md) — what each profile is exercised by.
- [`../SECURITY.md`](../SECURITY.md) — trusted-network assumptions, secrets, certificates, and reporting.
- [`../AGENTS.md`](../AGENTS.md) — env-var matrix, JVM-flag overrides, KNOWLEDGE PRESERVE notes.
