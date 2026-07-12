# Functional-equivalence test suite

Cucumber 7 suite that proves the async and reactive stacks are behaviourally equivalent — same routes, same status codes, same JSON shapes, same auth semantics, same end-to-end chains.

## What the suite proves

- **Same `.feature` files on both stacks.** No `@async-only` / `@reactive-only` tags; step definitions are shared. If a scenario passes on one stack and fails on the other, that is a parity defect in service code, not a test problem.
- **Gateway-fronted.** All scenarios connect through gateway-service on port 8080, so JWT validation, gateway routing, and gateway-level filters are exercised end-to-end alongside per-service behaviour.
- **Same compose topology as `prod`, plus a JaCoCo overlay.** The `functional-equivalence` profile layers a JaCoCo agent into each service JVM via `deployment/docker-compose/functional-equivalence.yml`. Coverage `.exec` files drop into `testing-results/functional-equivalence/jacoco/` and can be aggregated by the coverage gate. **The `prod` and `benchmark` profiles never load the JaCoCo overlay** — instrumentation is fully isolated and does not affect those paths.

The shared `.feature` files live under `src/test/resources/features/`. Scenario counts and area distribution are tracked in [`../RESULTS.md`](../RESULTS.md) §1.2.

## Commands

Run the full suite on either stack:

```bash
mvn -f testing/functional-equivalence/pom.xml verify -Pasync
mvn -f testing/functional-equivalence/pom.xml verify -Preactive
```

The `-f` form is mandatory — there is no parent reactor POM at the repo root, so `mvn -pl testing/functional-equivalence …` does not resolve. Either use `-f` from the repo root or `cd testing/functional-equivalence && mvn verify -P<stack>`.

Each `mvn verify` invocation brings the stack up via `./run.sh runtime up <stack> functional-equivalence` in a Cucumber `@BeforeAll`, waits for all four services to report healthy, runs the scenarios, then tears down.

Iteration helpers:

- `-DFE_REUSE_STACK=true` — skip the up/down cycle and run against an already-running stack.
- `-Dfe.repo.root=…` — point Cucumber at a different repo checkout.

## Coverage gate

```bash
./testing/functional-equivalence/coverage-gate.sh unit
./testing/functional-equivalence/coverage-gate.sh cucumber async
./testing/functional-equivalence/coverage-gate.sh cucumber reactive
```

- **Unit-only gate (load-bearing).** Reads each service module's JaCoCo CSV produced by per-service `mvn verify`. Asserts line + branch ≥ 0.90 on every service module on both stacks. Override the threshold with `COVERAGE_THRESHOLD=0.85` etc.
- **Cucumber-only gate.** Aggregates the in-container `.exec` files dropped under `testing-results/functional-equivalence/jacoco/` during an FE run and applies the FE black-box boundary denominator. Every service module on both stacks must clear 0.90 line + branch coverage.

The proof surface is the unit gate, the same 400 Cucumber scenarios passing on both stacks, the Cucumber coverage gate, and per-service test-count parity. Current counts and per-module measurements live only in [`../RESULTS.md`](../RESULTS.md) §1.

The current per-module unit-coverage numbers live in [`../RESULTS.md`](../RESULTS.md) §1.1.

## Related docs

- [`../README.md`](../README.md) — testing surface entry point.
- [`../RESULTS.md`](../RESULTS.md) — FE counts, per-module coverage, scenario distribution, AI-streaming numbers.
- [`../../AGENTS.md`](../../AGENTS.md) — FE-update checklist, when to re-run the count snippets, parity rules.
- [`../../deployment/README.md`](../../deployment/README.md) — how the `functional-equivalence` profile fits next to `prod` and `benchmark`.
