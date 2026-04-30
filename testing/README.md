# Testing Layout

> **Numbers and methodology live in [`RESULTS.md`](RESULTS.md)** — per-cell benchmark tables, current FE status, methodology comparison, and maintenance checklist. This file is an index.

The repo keeps source-controlled test assets under `testing/` and generated outputs under `testing-results/`.

Top-level convention:
- `./run.sh` is the workspace entrypoint
- runtime orchestration is namespaced under `./run.sh runtime ...`
- benchmarks and payload prep are dispatched through `./run.sh bench ...` and `./run.sh prepare ...`
- `./run.sh up/down/destroy/ps/logs ...` are compatibility aliases for `./run.sh runtime up/down/destroy/ps/logs ...`

## Subdirectories

| Path | Purpose | Reference |
|---|---|---|
| `testing/equivalence/` | Cucumber 7 + JUnit Platform Suite; same `.feature` files run on both stacks | [`equivalence/README.md`](equivalence/README.md) |
| `testing/calibration/` | Real-LLM TTFT/ITL sampling, distribution fitting, mock validation | [`calibration/README.md`](calibration/README.md) |
| `testing/analysis/` | k6 result ingestion, per-cell statistics, plot-ready CSVs + PNG figures | [`analysis/README.md`](analysis/README.md) |
| `testing/performance/` | k6 + JMeter benchmark surfaces | [`performance/README.md`](performance/README.md) |

Generated artifacts must not be written into `testing/`. Put them under the gitignored `testing-results/` tree instead.
