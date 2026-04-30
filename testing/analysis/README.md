# testing/analysis

Maven module + picocli CLI that ingests k6 result directories, computes per-cell statistics, and emits PNG figures + plot-ready CSVs.

## Workflow

```bash
java -jar testing/analysis/target/analysis-0.0.1-SNAPSHOT.jar ingest \
  --results-root testing-results/performance/k6 \
  --out          testing-results/runs.csv \
  --campaign-manifest testing/analysis/campaign-manifest.tsv

java -jar testing/analysis/target/analysis-0.0.1-SNAPSHOT.jar report \
  --runs-csv testing-results/runs.csv \
  --out      testing-results/cells.csv \
  --include-status OK

java -jar testing/analysis/target/analysis-0.0.1-SNAPSHOT.jar plot \
  --stats-csv testing-results/cells.csv \
  --out-dir   testing-results/figures
```

## Subcommands

| Subcommand | Input | Output |
|---|---|---|
| `ingest` | Result-batch directories under `--results-root` | Flat `runs.csv` (one row per k6 run) |
| `report` | `runs.csv` | Per-cell `cells.csv`: bootstrap CI on per-run p99, MW-U + Welch's t-test for paired async/reactive cells |
| `plot` | `cells.csv` | PNG figures (concurrency scaling, pool-size scaling, H1 validation scatter) |

## Cell-key dimensions

Cells are keyed by `(stack, workload, transport, target_rps, pool_size, reject_policy, cancel_rate, calibration_tag, campaign)`. Both `calibration_tag` and `campaign` are part of the key so operationally distinct batches stay separate even when their load dimensions match.

## Campaign manifest

`testing/analysis/campaign-manifest.tsv` maps `<result-batch-dir-name>` → `<campaign-label>` and overrides the campaign label baked into the run's k6 handleSummary JSON. Use it when the execution-time `--ai-campaign` should not be the analysis-time campaign for a given batch.

## Status filter

`--include-status` (default `OK,NO_VALIDATION_SIDECAR`) drops runs whose `cell_status` is not in the list. Set to `OK` for a strict OK-only export, or include `CONTAMINATED` to keep flagged batches in the aggregate.

## Untagged-run backfill

`--untagged-calibration-cutoff`, `--untagged-pre-cutoff-tag`, `--untagged-post-cutoff-tag` assign a `calibration_tag` to result dirs whose JSON summary doesn't carry one explicitly. Only relevant for result dirs without an explicit calibration tag in their k6 handleSummary JSON.
