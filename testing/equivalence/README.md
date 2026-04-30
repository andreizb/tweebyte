# testing/equivalence

Cucumber 7 + JUnit Platform Suite. The same `.feature` files run against both async and reactive Tweebyte stacks; equivalence is observable per scenario.

## Layout

- `src/test/resources/features/<service>/*.feature` — Gherkin scenarios (single source of truth for the scenario count).
- `src/test/java/.../steps/*.java` — step definitions shared across both stacks.
- `src/test/java/.../hooks/ComposeLifecycle.java` — brings the stack up via `./run.sh runtime up <stack> fe-test` in `@BeforeAll` and tears it down in `@AfterAll`.
- `pom.xml` — failsafe-driven Maven wrapper that selects the stack via `-Pasync` / `-Preactive`.

## Run

```bash
mvn -f testing/equivalence/pom.xml verify -Pasync
mvn -f testing/equivalence/pom.xml verify -Preactive
```

The `mvn -pl testing/equivalence …` form does not work from the repo root because there is no parent reactor POM. Use `-f` or `cd testing/equivalence && mvn …`.

Both invocations bring the chosen stack up under the `fe-test` profile (which loads `infrastructure/compose/fe-test.yml` so each service JVM runs the JaCoCo agent), wait for all four services to report healthy via `/actuator/health`, run the scenarios through the gateway on port 8080, and tear the stack down.

`prod` and `benchmark` runtime paths see no instrumentation.

## Scenario count

Source of truth (must restrict to source resources to avoid double-counting against `target/test-classes/`):

```bash
find testing/equivalence/src/test/resources/features -name '*.feature' \
  | xargs grep -c '^\s*Scenario' | awk -F: '{s+=$2} END {print s}'
```

## Stack selection

`StackProfile.current()` reads the `-P<stack>` Maven profile (or the `fe.stack` system property) and selects which compose stack `ComposeLifecycle` brings up. Add a new stack by extending `StackProfile` and the `ComposeLifecycle.run(...)` invocation; do not add stack-specific tags to `.feature` files.

## Reuse mode

Set `-Dfe.repo.root=…` to point Cucumber at a different repo checkout, or `-DFE_REUSE_STACK=true` to skip the up/down cycle when iterating against an already-running stack.
