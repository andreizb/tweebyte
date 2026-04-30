# testing/performance

Benchmark surfaces. k6 is the primary runner; JMeter covers the CRUD workload plans.

## k6

- `k6/workloads/` — JS workloads: `following-cache`, `file-download`, `image-upload`, `ai-streaming-benchmark.js`.
- `k6/run_bench.sh` — workload runner; invoked via `./run.sh bench k6 --workload <name> ...` from the repo root.
- `k6/prepare_payload.py` — generates payloads under gitignored `k6/payload/`.
- `k6/README.md` — operator cookbook for the `following-cache` benchmark.
- Outputs: `testing-results/performance/k6/`.

## JMeter

- `jmeter/workloads/` — `.jmx` plans: `user-summary.jmx`, `user-summary-alt.jmx`, `user-create.jmx`, `follow-create.jmx`, `tweet-update.jmx`, `tweets-get.jmx`.
- `jmeter/run_bench.sh` — plan runner; invoked via `./run.sh bench jmeter --workload <name> ...`. Run `./testing/performance/jmeter/run_bench.sh --help` for the full flag list.
- `jmeter/prepare_payload.py` — generates workload-specific CSV payloads under gitignored `jmeter/payload/`.
- Outputs: `testing-results/performance/jmeter/`.

Generated artifacts must not be written into `testing/performance/`. They go under the gitignored `testing-results/performance/<runner>/` tree.
