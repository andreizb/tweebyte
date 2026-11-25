# AGENTS.md

This is the single canonical guide for all AI coding agents in Tweebyte. Do not create agent-specific guidance files; project-wide agent rules belong here.

Tweebyte implements the same microservice API with an async servlet/JPA stack and a reactive WebFlux/R2DBC stack. Preserve behavioral parity while allowing each stack's implementation model.

## Read docs when the task needs them

Use the relevant sections of these references; this list is not a mandatory reading sequence.

| Task touches | Reference |
|---|---|
| System design, service boundaries, security, or cross-service flow | [`ARCHITECTURE.md`](ARCHITECTURE.md) |
| Backend implementation, configuration, media, or module commands | [`backend/README.md`](backend/README.md) |
| Frontend behavior or browser tests | [`frontend/README.md`](frontend/README.md), and [`frontend/e2e/README.md`](frontend/e2e/README.md) for E2E work |
| Runtime, deployment profiles, or topology | [`deployment/README.md`](deployment/README.md) |
| Choosing verification and updating test evidence | [`testing/README.md`](testing/README.md#choose-verification-for-the-change) |
| Shared Cucumber behavior or FE coverage | [`testing/functional-equivalence/README.md`](testing/functional-equivalence/README.md) |
| Preparing, running, or changing benchmarks | Relevant sections of [`testing/performance/README.md`](testing/performance/README.md): execution modes, cleanup, methodology, AI streaming, and recorded pins |
| Recorded measurements or changed counts/methodology | Affected sections of [`testing/RESULTS.md`](testing/RESULTS.md) |
| Security scope or vulnerability reporting | [`SECURITY.md`](SECURITY.md) |

For a CLI you need to invoke or change, use its `--help` as the source of truth for flags/defaults. Reuse that information during the task unless the CLI changes; do not run unrelated CLIs simply to satisfy this guide.

## Scope and completion

Continue authorized implementation, verification, and documentation until the requested outcome is complete. Reuse authorization already given in the conversation. If an action needs additional permission, first complete independent preparation so that approval concerns a concrete action; keep unrelated authorized work moving. Follow the host's actual permission rules.

Fix small, task-related documentation or comment drift in the same change. For drift that would expand scope, report the file and line and defer that extra change while continuing the task. Do not search for unrelated drift. Stale commands, moved files, duplicated CLI defaults, and old result tables are common examples.

## Implementation guardrails

- Both `backend/async/` and `backend/reactive/` contain gateway, user, tweet, and interaction modules. Service-local Flyway migrations own schemas. Keep inter-service URLs, database/cache hosts, and secrets in deployment wiring; never hardcode topology in application code.
- **There is no `application-prod.properties`.** Do not create or request one or report it as missing. `prod` is a deployment label: app services use base `application.properties` with no active Spring profile. Benchmark mode adds `application-benchmark.properties`; FE adds its isolated JaCoCo deployment overlay. See the [property-file contract](deployment/README.md#spring-property-files).
- **Feature switches are literal profile values.** Production/default ON belongs in base and benchmark-neutralising OFF in the benchmark overlay. Do not replace this with `feature.enabled=${SOMETHING:true}`. Environment placeholders are for topology, secrets, and per-run tuning. Unit-test resources may shadow base, defaulting features off unless that would lose needed coverage. Both stacks use the same knob names and values; [configuration details](backend/README.md#configuration-and-feature-gating).
- When editing gateway routes/filters, preserve exact public allow-list entries, Keycloak RS256 validation by issuer and `kid`, ownership by `user_id` rather than `sub`, and `OwnershipRuleSet`/`PathPattern` matching that cannot consume sibling batch routes. `401` carries `WWW-Authenticate`; ownership `403` does not. Service authorization stays authoritative. Keep servlet `OncePerRequestFilter` and reactive ordered `GlobalFilter` behavior symmetric and ahead of routing.
- When editing media, preserve content addressing, pure upload versus derived preview behavior, password-gated original reveal, and fail-closed cleanup of all reference sources. The complete endpoint, cache, deduplication, and reference contract is in the [backend media model](backend/README.md#media-model-user-service).
- Observability stays outside the benchmark request path; tracing is ON in base and OFF in benchmark. Prometheus authentication must match backend mTLS. Kafka stays provisioned but unwired unless messaging is requested.

## Verification and benchmark evidence

Choose checks using the [verification scope](testing/README.md#choose-verification-for-the-change). Keep service line and branch coverage ≥ 0.90, shared FE behavior, and per-service test-count parity within 5%. Do not interpret a focused passing check as a fresh whole-system sign-off. Reuse passing checks until new changes or failures justify repeating them.

`testing/RESULTS.md` owns counts, coverage measurements, retained benchmark tables, and methodology. Update only the evidence affected by the change. Recount when tests/scenarios change; replace benchmark tables with complete new grids rather than partial or mixed-topology results.

For benchmark work, preserve the recorded failure modes, environment pins, and mitigations in the [performance guide](testing/performance/README.md#load-tool-execution-modes). Keep async/reactive topology, load transport, input data, duration, and comparable tuning symmetric; explicitly document stack-specific controls. Keep failed-request latency separate, report dropped arrivals, derive confidence from per-run tail latency, and use Mann–Whitney U for significance. Seed fixtures from the harness rather than application startup.

Cleanup applies only to the exact owned result directory after aggregation and writer exit, and always honors `KEEP_RAW=1`. Preserve incomplete results and report pending cleanup; never delete by global latest-directory or modification-time searches. See the [cleanup lifecycle](testing/performance/README.md#cleanup-lifecycle).

Generated outputs belong under gitignored `testing-results/`. The explicit exception is existing gitignored workload-local `payload/` directories used by seeders. Do not commit either kind of generated output.
