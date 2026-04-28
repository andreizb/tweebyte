# Testing Layout

> **Numbers, methodology, paper-claim cross-check live in [`RESULTS.md`](RESULTS.md)** — that file is the single source of truth for everything we have ever measured (per-cell tables for both published papers, current `@Test` counts, FE-claim status, methodology comparison + uniformization plan, and the maintenance checklist). This file (`testing/README.md`) is structure-only.

The repo keeps source-controlled test assets under `testing/` and generated outputs under `testing-results/`.

Top-level convention:
- `./run.sh` is the workspace entrypoint
- runtime orchestration is namespaced under `./run.sh runtime ...`
- benchmarks and payload prep are dispatched through `./run.sh bench ...` and `./run.sh prepare ...`
- the older `./run.sh up/down/destroy/ps/logs ...` forms still work as compatibility aliases

Current structure:
- `testing/performance/k6` for the modern k6 benchmark surface
  - `README.md` is the operator cookbook for the cache benchmark demo
  - `run_bench.sh` is the k6 runner
  - `prepare_payload.py` prepares workload payloads for `following-cache` / `file-download` / `image-upload`
  - `workloads/ai-streaming-benchmark.js` is the paper-extension AI workload: open-loop arrival rate, W0/W1/W2 × SSE/buffered × cancellation via env flags. No prepare step — prompts are generated in-script.
- `testing/performance/jmeter` for the older JMeter benchmark surface
  - `run_bench.sh` is the JMeter entrypoint
  - `workloads/` contains the `.jmx` plans
  - `prepare_payload.py` prepares workload-specific CSV payloads under gitignored `payload/`
- `testing/calibration` (Maven module, picocli CLI) — drives an OpenAI-compatible backend (LM Studio for the canonical calibration source; can also point at `mlx_lm.server` etc.), collects real-LLM TTFT/ITL samples, fits log-normal / gamma / Weibull / shifted-log-normal / 2-component log-normal mixture distributions via Apache Commons Math, writes `calibration.json`. The `validate` subcommand runs a two-sample Kolmogorov-Smirnov test of mock-draws vs real samples (selectable family via `--itl-family={gamma,shifted_lognormal,lognormal_mixture}`).
- `testing/analysis` (Maven module, picocli CLI) — ingests k6 result directories, computes per-cell bootstrap CIs on per-run p99 + Welch's t-test / Mann-Whitney U for paired async-vs-reactive cells, emits PNG figures via XChart and CSVs for LaTeX/pgfplots.

Reserved structure:
- `testing/functional` for future functional suites
- `testing/equivalence` for future async-vs-reactive equivalence suites

Generated artifacts should not be written into `testing/`. Put them under the gitignored `testing-results/` tree instead.
