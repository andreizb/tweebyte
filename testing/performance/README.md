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

Benchmark results land under `testing-results/` (gitignored at the repo root). One directory per run, time-stamped, with a `cells.csv` per workload plus engine-native artifacts:

```
testing-results/performance/k6/results_<workload>_<ts>_<hash>/
testing-results/performance/jmeter/results_<workload>_<ts>_<hash>/
```

The AI-stream-summarize analysis pipeline ingests k6 result directories into a flat CSV, computes per-cell bootstrap CIs on per-run p99 + Mann-Whitney U paired tests for async-vs-reactive, and emits PNG figures and plot-ready CSVs (`k6/workloads/ai-stream-summarize/analysis/`).

## Methodology principles

Load-bearing rules, documented with the retained measurements in [`../RESULTS.md`](../RESULTS.md):

- **Open-loop arrival-rate executors for new benchmarks.** Closed-loop conflates SUT response time with arrival rate at saturation.
- **Never average p99s across runs.** The cell-level statistic is the mean of per-run p99s with bootstrap 95 % CI (10 k resamples).
- **Significance gate: Mann-Whitney U** at α = 0.05 paired async-vs-reactive. Welch's t-test is computed for reference only.
- **Failed-request latency goes to a separate Trend** so reject-policy 5xx don't compress success p99.
- **GC log pinned on the `benchmark` profile** (configured in [`../../deployment/docker-compose/`](../../deployment/docker-compose/)).
- **Stack symmetry on every knob.** Whatever is turned on one stack is turned identically on the other, with exactly one documented asymmetry on file (`disableR2dbcLoopColocation` in reactive `user-service` only).
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

### The contract (lives in [`lib/cleanup.sh`](lib/cleanup.sh))

Every workload wrapper under `{jmeter,k6}/workloads/<workload>/run.sh` sources `lib/cleanup.sh` and calls four hooks:

| Hook | When | What it does |
|---|---|---|
| `cleanup_sweep_init` | top of wrapper | Creates a `mktemp` marker, installs `trap '_on_sweep_abort' INT TERM`. |
| `cleanup_raw_samples <dir>` | after each per-stack `run_bench` returns 0 | `find -delete` on `*.jtl` and `*_filtered.jtl`. |
| `remove_partial_result_dir <dir>` | after each per-stack `run_bench` returns non-zero | `rm -rf <dir>` (partial cells.csv is unusable). |
| `cleanup_sweep_finish` | bottom of wrapper | Drops the marker + clears the trap so post-sweep signals don't trip cleanup. |

The trap (`_on_sweep_abort`) handles Ctrl-C / SIGTERM during the sweep: it finds every `results_*` dir under `testing-results/performance/` with `mtime -newer SWEEP_MARKER` and `rm -rf`s them. Since the marker is `mktemp`'d at sweep start, only dirs created during this sweep match.

### What stays / what goes per result dir

**Preserved** (~100 KB total per dir):
- `cells.csv` — aggregated per-cell metrics, the only file analysis needs
- `figures/*.png` — generated plots
- `summary.txt` — run summary
- `*_jmeter.log` — KB each, per-cell JMeter logs (debugging)
- `*_resources.csv` — KB each, per-cell heap/cpu samples
- `*_prom.csv` — KB each, k6 Prometheus snapshot
- `*_validation.txt` — KB each, k6 validation output
- `<conc>_<run>.txt` — k6 textual reports

**Deleted** (~99 % of disk):
- `*.jtl` — JMeter raw samples (already aggregated into cells.csv)
- `*_filtered.jtl` — JMeter filtered intermediate

### Operator escape hatch

```sh
KEEP_RAW=1 ./testing/performance/jmeter/workloads/dbwrite-light-follow-create/run.sh canonical
```

Preserves raw `.jtl`. Only use when you genuinely need to re-aggregate `cells.csv` with different stat methods (different percentile cuts, alternative bootstrap params). Default behaviour deletes them after aggregation.

### Agent gate (also in [`../../AGENTS.md`](../../AGENTS.md))

After any benchmark run, regardless of wrapper or operator action:
1. Latest result dir = `ls -dt testing-results/performance/{jmeter,k6}/results_*/ | head`.
2. If `*.jtl` files survived and sweep is over → wrapper trap failed, delete: `find testing-results/performance -name '*.jtl' -delete`.
3. If operator cancelled mid-sweep and partial dir survived → `rm -rf` the dir entirely.
4. Never let raw `.jtl` accumulate — `cells.csv` already has everything analysis needs.

### One-time recovery

If `testing-results/` has accumulated raw `.jtl` from pre-cleanup runs:

```sh
find testing-results -name '*.jtl' -delete
```

Typically recovers tens of GB.

## Related docs

- [`../README.md`](../README.md) — testing surface overview.
- [`../RESULTS.md`](../RESULTS.md) — retained measurements and methodology.
- [`../../SECURITY.md`](../../SECURITY.md) — benchmark trust boundary and reporting.
- [`../../AGENTS.md`](../../AGENTS.md) — load-tool execution modes, KNOWLEDGE PRESERVE notes, AI-streaming details, cleanup gate.
