# Testing

Entry point for the three test surfaces in this repo.

## Surfaces

| Surface | Location | Purpose |
|---|---|---|
| Unit + integration tests | `backend/async/<service>/src/test/`, `backend/reactive/<service>/src/test/` | Per-service `@Test` and Spring slice tests. Run via `mvn test`. |
| Functional-equivalence (FE) suite | [`functional-equivalence/`](functional-equivalence/README.md) | Cucumber 7 suite. The same `.feature` files exercise both stacks through gateway-service over HTTP. |
| Performance benchmarks | `performance/jmeter/`, `performance/k6/` | Two load engines. See [`performance/README.md`](performance/README.md). |

## Unit tests

Run a service's tests directly:

```bash
cd backend/async/tweet-service && mvn test
cd backend/reactive/interaction-service && mvn test
```

Single class / single method:

```bash
mvn test -Dtest=UserControllerTest
mvn test -Dtest=UserControllerTest#testGetUserProfile
```

The unit-coverage gate runs across every service module on both stacks and asserts line + branch ≥ 0.90:

```bash
./testing/functional-equivalence/coverage-gate.sh unit
```

Counts and per-module coverage live in [`RESULTS.md`](RESULTS.md) §1.1.

## Functional equivalence

Cucumber 7 suite under [`functional-equivalence/`](functional-equivalence/README.md). The same `.feature` files (`functional-equivalence/src/test/resources/features/`) run on both stacks under the `functional-equivalence` compose profile (JaCoCo overlay). Connection is over HTTP through gateway-service on port 8080, so JWT validation is exercised end-to-end. See [`functional-equivalence/README.md`](functional-equivalence/README.md) for the standalone entry point.

```bash
mvn -f testing/functional-equivalence/pom.xml verify -Pasync
mvn -f testing/functional-equivalence/pom.xml verify -Preactive
```

(The `-f` form is mandatory — there is no parent reactor POM, so `mvn -pl testing/functional-equivalence …` does not resolve from the repo root.)

Each invocation brings the stack up via `./run.sh runtime up <stack> functional-equivalence`, waits for the four services to report healthy, runs the scenarios, then tears down. Override with `-DFE_REUSE_STACK=true` to skip the up/down cycle against an already-running stack.

The Cucumber coverage gate uses the FE black-box boundary denominator and requires every service module to clear 0.90 line + branch coverage:

```bash
./testing/functional-equivalence/coverage-gate.sh cucumber <async|reactive>
```

Scenario count, area distribution, and gate state are tracked in [`RESULTS.md`](RESULTS.md) §1.2.

## Performance benchmarks

JMeter and k6 engines under `performance/`. Each engine has a top-level `run_bench.sh` driver and per-workload subdirectories with their own `run.sh` and `prepare.py`. See [`performance/README.md`](performance/README.md) for the engine layout, smoke vs full-grid conventions, topology, and shared methodology.

Benchmark outputs land under `testing-results/` (gitignored — see below).

## Outputs and results

> **Invariant.** Generated benchmark outputs, figures, logs, JTL files, payloads, and temporary analysis artifacts must not be written under `testing/`. Keep generated outputs under the gitignored `testing-results/` tree. Workload-local generated payload directories are also gitignored and may be deleted after a run.

- **`testing-results/`** is the gitignored output root at the repo root. JMeter `.jtl`, k6 JSON summaries, generated CSVs, PNG figures, and JaCoCo coverage exports all land there.
- **[`RESULTS.md`](RESULTS.md)** is the in-tree source of truth for FE status, benchmark methodology, and the complete retained service and AI result grids.

## Related docs

- [`../ARCHITECTURE.md`](../ARCHITECTURE.md) — what is being tested.
- [`../deployment/README.md`](../deployment/README.md) — how stacks come up under each profile.
- [`../AGENTS.md`](../AGENTS.md) — testing rules, when to update RESULTS.md, FE update checklist.
- [`functional-equivalence/README.md`](functional-equivalence/README.md) — FE suite entry point (commands, coverage gate, parity rules).
- [`performance/README.md`](performance/README.md) — load-engine entry point.
- [`RESULTS.md`](RESULTS.md) — measurements and methodology ledger.
