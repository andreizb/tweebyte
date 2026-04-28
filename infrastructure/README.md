# infrastructure

Runtime orchestration for the dual async/reactive Tweebyte stacks.

## Layout

- `compose/` — docker compose files
  - `infrastructure.yml` — shared databases, Redis, toxiproxy
  - `async.yml`, `reactive.yml` — per-stack service overlays
  - `fe-test.yml` — FE-test overlay that adds the JaCoCo agent to each service JVM
- `run.sh` — workspace dispatcher (`runtime`, `bench`, `prepare`, `local`, …)
- `coverage-gate.sh` — JaCoCo line + branch ≥ 0.90 gate (unit and cucumber modes)
- `realism-backend.sh` — host-side `mlx_lm.server` lifecycle for live-LLM cells

## Runtime profiles

| Profile | Compose overlays | Purpose |
|---|---|---|
| `prod` | `infrastructure.yml`, `<stack>.yml` | Production-shaped runtime; services use `redis:6379`, the host-published port is `localhost:63790`. |
| `benchmark` | `infrastructure.yml`, `<stack>.yml` (with toxiproxy + heap caps) | Benchmark runtime; interaction-service Redis routes through `toxiproxy:26379`. |
| `fe-test` | `infrastructure.yml`, `<stack>.yml`, `fe-test.yml` | FE Cucumber suite; each service JVM loads the JaCoCo agent. |

`fe-test.yml` is loaded **only** for the FE suite. `prod` and `benchmark` paths see no JaCoCo instrumentation.

## Common commands

```bash
./run.sh runtime up   <stack> <profile>
./run.sh runtime down <stack> <profile>
./run.sh runtime ps   <stack>
./run.sh runtime logs <stack> --tail 100
./run.sh bench   k6 --workload <name> ...
./run.sh bench   jmeter --workload <name> ...
./run.sh prepare k6 ...
./run.sh prepare jmeter ...
./run.sh local   <stack> <service> <profile>
```

`<stack>` is `async` or `reactive`; `infra` is also accepted as a shorthand for "infrastructure only".

## Coverage gate

```bash
./infrastructure/coverage-gate.sh unit                  # both stacks
./infrastructure/coverage-gate.sh unit     <stack>      # one stack
./infrastructure/coverage-gate.sh cucumber <stack>      # advisory mode
COVERAGE_THRESHOLD=0.85 ./infrastructure/coverage-gate.sh unit
```

## Output convention

All generated artifacts (k6 results, JMeter output, JaCoCo `.exec` from the FE suite, figures) go under `testing-results/` at the repo root. Nothing should be written into `infrastructure/`.
