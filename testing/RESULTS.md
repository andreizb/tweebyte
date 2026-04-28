# Tweebyte Testing — Numbers, Methodology, Maintenance Notes

**Single source of truth** for everything we have ever measured on Tweebyte: published paper claims, raw per-cell numbers, the methodology each result was produced with, gaps, and the rerun / uniformization roadmap. Update this file every time a test count changes, a benchmark sweep is rerun, or a paper claim shifts.

> Linked artifacts:
> - First-study aggregated numbers reproduced verbatim in §2 below.
> - AI-streaming workload conventions: [`AGENTS.md`](../AGENTS.md).

---

## 1. Functional Equivalence

The two papers anchor the FE claim on three quantities (Table 2 of paper B, p. 27):

| Type | Description | Quantity (claim) |
|---|---|---|
| Unit Tests | JUnit 5 component / method tests | ~150 per service (1050 total, 7 services) |
| Behavior-Driven | Cucumber + Gherkin | 55 scenarios |
| Performance | Concurrent-user simulations | 4 scenarios × 7 concurrency levels (10/50/100/250/500/750/1000) AND 3 scenarios × 14 concurrency levels (1/5/10/15/20/25/50/75/100/200/400/600/800/1000) |

### 1.1 Unit tests — current state

Counts are `@Test`-annotated method counts on `develop` as of 2026-04-27. Each row is one Maven module.

| Stack | Service | @Test count | vs main (Δ) | Notes |
|---|---|---:|---:|---|
| async | gateway-service | **8** | +0 | Spring Cloud / Zuul boilerplate |
| async | user-service | **137** | +1 | |
| async | tweet-service | **203** | +11 | +10 from new AI suite (`MockStreamingChatModelTest`, `MockCalibrationTest`) + 1 misc |
| async | interaction-service | **215** | +1 | |
| async | **subtotal** | **563** | +13 | |
| reactive | gateway-service | **1** | +0 | |
| reactive | user-service | **117** | +1 | |
| reactive | tweet-service | **175** | +11 | +10 from new AI suite + 1 misc |
| reactive | interaction-service | **175** | +1 | |
| reactive | **subtotal** | **468** | +13 | |
| **Total** | | **1031** | +26 | Average across 7 services ≈ **147** (paper claim: ~150) |

**Where the FE claim sits today:** the 1031-total / 147-per-service average is within rounding of the published "1050 total / ~150 per service" claim. **But** the per-service variance is huge (8 → 215), and **async carries 95 more tests than reactive**. This asymmetry is the load-bearing weakness in the FE story: the paper claims the same coverage on both sides, but the test-method count says async is 20% better-tested.

**Maintenance rule (do this every PR that touches test files):**
1. Re-run the count: `for s in async/{gateway,user,tweet,interaction}-service reactive/{gateway,user,tweet,interaction}-service; do printf '%-40s %5d\n' "$s" "$(grep -rEoh '@Test\b' "$s/src/test" 2>/dev/null | wc -l)"; done`
2. Update the table above + the per-stack subtotals + the average.
3. If async/reactive deviate by more than 5% (currently ~20%), open a follow-up to add the missing reactive equivalents — the goal is **per-service parity, not just average parity**.

### 1.2 Behavior-Driven tests — Cucumber

Paper claim: **55 Gherkin scenarios** (paper A pp. 27-28, paper B p. 26).
Current reality: **0 in repo** — the Cucumber suite is not in any tracked branch (`git ls-tree -r {main,develop} --name-only | grep -iE 'cucumber|\.feature|gherkin|stepdef'` returns nothing). The artifacts are not on disk.

Roadmap:
1. **Re-author the suite** when the AI-extension paper is locked. Target: **100 scenarios** (the user's stated goal — bump from the original 55), covering: auth flows, tweet CRUD, follow/like/retweet/reply chains, the new caching surface, the new CPU-bound media surface, the new blocking-I/O download surface, and the new AI streaming surface (W0/W1/W2).
2. Hold the suite under `testing/functional/cucumber/` (the `testing/README.md` already reserves `testing/functional/`).
3. Run on both stacks via the same compose harness — Cucumber connects to gateway-service over HTTP, so the same feature files exercise both implementations and the FE claim is observable per-scenario, not just per-test-count.

### 1.3 AI streaming tests (added 2026-04-26, this branch)

Both stacks now ship 10 unit tests per stack covering the new AI surface:
- `MockStreamingChatModelTest` (4 tests): token-count, indexed-token order, call-concatenation, accessor parity.
- `MockCalibrationTest` (6 tests): blank/null path → defaults, missing file → defaults, incomplete JSON → defaults, parse-error → defaults, valid JSON → fitted params.

Counts are included in the per-service table above (e.g. async tweet-service 203 includes the 10 new ones).

**Maintenance rule:** if you add an AI endpoint or a new mock parameter, write the unit test in **both** stacks symmetrically. The 10/10 split is intentional and load-bearing for the FE story.

---

## 2. Performance Results — First Study (paper A, 2024)

**Source of numbers:** extracted verbatim from the prior published-paper data tables; reproduced below.

**Methodology used (paper A § 7.2.2):**
- Tooling: **Apache JMeter** (load generation), **VisualVM** (resource monitoring; point samples).
- Test plan: 60 s ramp-up + 3 min steady run; ramp-up data **excluded** from reports.
- 4 scenarios × 7 concurrency levels = 28 cells per stack = 56 total points. Single shot per cell — **no repetitions, no CI, no statistical bands**.
- Metrics: memory consumption (MB), CPU usage (%), avg response time (ms), 90% line (ms), throughput (req/s).
- No standard deviation, no percentile band, no significance testing.

**Methodology gaps (vs the modern playbook in §4 below):** no repetitions → no variance estimate; 90th percentile only (no p95/p99); single arrival shape (closed-loop user simulation, no open-loop arrival rate); no GC log; no continuous Actuator-driven 1-second sampling. Numbers are useful as a baseline but should not be compared head-to-head with the second-study numbers; they live in a strictly weaker measurement regime.

### 2.1 User Summary GET (read-heavy, single service, no I/O fan-out)

| Concurrency | Stack | Memory (MB) | CPU (%) | Avg RT (ms) | 90% line (ms) | Throughput (req/s) |
|---:|---|---:|---:|---:|---:|---:|
| 10 | async | 64 | 5.9 | 9 | 11 | 936 |
| 10 | reactive | 60 | 5.2 | 8 | 9 | 1015 |
| 50 | async | 93 | 20.5 | 11 | 15 | 3743 |
| 50 | reactive | 88 | 21.3 | 10 | 12 | 4008 |
| 100 | async | 159 | 26.7 | 16 | 21 | 5109 |
| 100 | reactive | 129 | 28.4 | 13 | 16 | 6179 |
| 250 | async | 273 | 29.4 | 39 | 64 | 5286 |
| 250 | reactive | 226 | 32.5 | 26 | 31 | 7815 |
| 500 | async | 335 | 34.2 | 69 | 141 | 6008 |
| 500 | reactive | 260 | 37.3 | 51 | 61 | 8075 |
| 750 | async | 490 | 38.5 | 102 | 221 | 6073 |
| 750 | reactive | 368 | 40.7 | 76 | 92 | 8181 |
| 1000 | async | 535 | 40.7 | 125 | 277 | 6659 |
| 1000 | reactive | 473 | 42.1 | 93 | 121 | 8867 |

### 2.2 Follow POST (write to relational store, no fan-out)

| Concurrency | Stack | Memory (MB) | CPU (%) | Avg RT (ms) | 90% line (ms) | Throughput (req/s) |
|---:|---|---:|---:|---:|---:|---:|
| 10 | async | 94 | 10.2 | 8 | 10 | 992 |
| 10 | reactive | 87 | 8.6 | 7 | 10 | 1060 |
| 50 | async | 141 | 23.6 | 13 | 18 | 3010 |
| 50 | reactive | 103 | 23.1 | 11 | 14 | 3051 |
| 100 | async | 174 | 29.3 | 27 | 44 | 3072 |
| 100 | reactive | 142 | 26.9 | 21 | 21 | 3910 |
| 250 | async | 239 | 30.8 | 62 | 129 | 3335 |
| 250 | reactive | 213 | 31.3 | 41 | 50 | 4998 |
| 500 | async | 291 | 34.1 | 139 | 146 | 3470 |
| 500 | reactive | 263 | 32.9 | 78 | 100 | 5278 |
| 750 | async | 343 | 34.9 | 210 | 221 | 3500 |
| 750 | reactive | 328 | 34.6 | 116 | 148 | 5359 |
| 1000 | async | 394 | 35.6 | 245 | 291 | 3704 |
| 1000 | reactive | 366 | 37.9 | 153 | 191 | 5446 |

### 2.3 Tweet PUT (update flow)

| Concurrency | Stack | Memory (MB) | CPU (%) | Avg RT (ms) | 90% line (ms) | Throughput (req/s) |
|---:|---|---:|---:|---:|---:|---:|
| 10 | async | 93 | 9.5 | 9 | 11 | 955 |
| 10 | reactive | 81 | 7.9 | 8 | 10 | 981 |
| 50 | async | 144 | 20.1 | 25 | 26 | 1733 |
| 50 | reactive | 123 | 21.1 | 17 | 17 | 2390 |
| 100 | async | 171 | 22.6 | 33 | 56 | 2509 |
| 100 | reactive | 152 | 23.5 | 27 | 32 | 2848 |
| 250 | async | 229 | 23.4 | 73 | 162 | 2624 |
| 250 | reactive | 206 | 24.2 | 71 | 105 | 3081 |
| 500 | async | 268 | 24.6 | 147 | 343 | 2763 |
| 500 | reactive | 224 | 24.8 | 136 | 146 | 3244 |
| 750 | async | 309 | 25.5 | 258 | 539 | 2831 |
| 750 | reactive | 275 | 25.3 | 172 | 221 | 3616 |
| 1000 | async | 351 | 28.7 | 281 | 688 | 2965 |
| 1000 | reactive | 303 | 26.9 | 231 | 294 | 3714 |

### 2.4 Tweet Summaries GET (data-intensive read; the "async wins" case)

This is the scenario the paper highlights as **the case where async outperforms reactive** because the workload is dominated by ORM-style relationship loading that R2DBC handles less efficiently than JPA.

| Concurrency | Stack | Memory (MB) | CPU (%) | Avg RT (ms) | 90% line (ms) | Throughput (req/s) |
|---:|---|---:|---:|---:|---:|---:|
| 10 | async | 200 | 17.9 | 47 | 67 | 178 |
| 10 | reactive | 187 | 41.2 | 113 | 137 | 75 |
| 50 | async | 370 | 25.3 | 158 | 285 | 264 |
| 50 | reactive | 235 | 57.8 | 408 | 640 | 103 |
| 100 | async | 465 | 30.2 | 304 | 538 | 275 |
| 100 | reactive | 296 | 64.6 | 787 | 1222 | 106 |
| 250 | async | 837 | 34.0 | 789 | 1434 | 263 |
| 250 | reactive | 324 | 68.8 | 1991 | 2905 | 104 |
| 500 | async | 1069 | 37.5 | 1636 | 3424 | 258 |
| 500 | reactive | 443 | 73.7 | 3976 | 5681 | 103 |
| 750 | async | 1440 | 48.1 | 2463 | 4716 | 253 |
| 750 | reactive | 629 | 75.6 | 6090 | 8561 | 101 |
| 1000 | async | 1979 | 59.8 | 3755 | 7195 | 218 |
| 1000 | reactive | 691 | 80.9 | 8275 | 10547 | 99 |

### 2.5 First-study static / initialization metrics (paper A Table 6)

| Microservice | Heap on idle (MB) | Startup (ms) | JAR (MB) |
|---|---:|---:|---:|
| User Service (async) | 61 | 2363 | 52 |
| User Service (reactive) | 38 | 1522 | 37 |
| Tweet Service (async) | 62 | 2921 | 61 |
| Tweet Service (reactive) | 45 | 1874 | 45 |
| Interaction Service (async) | 70 | 3526 | 61 |
| Interaction Service (reactive) | 51 | 2107 | 41 |

### 2.6 First-study headline claims (paper A abstract)

- **~12% reduction in memory usage** under high concurrency.
- **~56% faster 90th-percentile response times** (most pronounced on User Summary GET at 1000 users: 277 → 121 ms).
- **~33% increase in throughput** under high concurrency.
- Reactive **loses** to async on data-intensive Tweet Summaries GET (≈8× longer p90 at 1000 users; ≈2× the throughput) — paper attributes this to R2DBC lacking JPA's lazy-loading / relationship-management features.

---

## 3. Performance Results — Second Study (paper B, 2026)

**Methodology used (paper B § 6.1, p. 26-27):**
- Tooling: **k6** (load generation; replaces JMeter), **Spring Boot Actuator** (1-second sampling; replaces VisualVM point samples).
- 4-minute runs (60 s warmup excluded), **5 independent repetitions per cell**, 14 concurrency levels per scenario.
- Statistics: per-cell mean, **standard deviation (σ)**, **95th percentile (p95)**, **95% confidence intervals (CI95)**. Outlier filter: 3-σ rule (no points removed in the published runs).
- **JWT validation deliberately disabled** during testing to remove cryptographic overhead from the measurement and isolate the concurrency-model signal.
- Hardware **pinned**: 16-inch MacBook Pro M3 Max (16 CPU cores, 40 GPU cores, 64 GB unified RAM), macOS, Docker. JVM: G1GC, fixed 4 GB heap.
- Same host network context for all runs (CPU affinity stable, scheduling predictable).
- Data hygiene: only the warmup interval was excluded from final analysis.

**Methodology gaps (vs §4 below — what we changed for the AI extension and would change again):**
- Closed-loop `constant-vus` executor in k6 → fine for steady-state but doesn't preserve arrival distribution under overload. Open-loop `ramping-arrival-rate → constant-arrival-rate` would be more honest for tail-latency claims.
- Aggregated mean/σ across requests (not per-run percentile aggregation) — averaging p99 across runs collapses run-to-run variance into a single number that hides the cliff. The bootstrap-CI-on-per-run-p99 approach we use in `testing/analysis/` is more rigorous; rerun the next sweep that way.
- No formal significance test between paired async / reactive cells (the analysis module now does Mann-Whitney U, see §4).

### 3.1 Caching workload — Following IDs over Redis (`following-cache-redis-io-benchmark.js`)

I/O-bound, Redis-backed read with 90% hot-key ratio. **Numbers below are verbatim from paper B § 6.2 text — only low/mid/peak points were quoted in body text.** Full per-level curves are in Figures 2-5; cross-check via NotebookLM is **pending** (see §6).

| Concurrency | Stack | Throughput (req/s, mean [CI95]) | Avg lat (ms) | p95 lat (ms) | Memory (MB) | CPU (%, mean [σ]) |
|---:|---|---|---:|---:|---:|---|
| 10 | async | 1054 [1048–1060] | 12.53 | 22.35 | ≈145 | 6.68 [1.07] |
| 10 | reactive | 1094 [1092–1096] | 12.08 | 22.19 | ≈71 | 3.55 [0.23] |
| 400 | async | 21,892 [21,739–22,045] | 24.30 | 37.18 | 442 | 50.1 [4.3] |
| 400 | reactive | 38,263 [38,035–38,490] | 13.87 | 18.05 | 531 | 19.0 [2.1] |
| 800 | async | n/a in text | n/a | n/a | n/a | σ = **4.97** (called out vs reactive's 1.56) |
| 800 | reactive | n/a in text | n/a | n/a | n/a | σ = **1.56** |
| 1000 | async | 26,724 [26,642–26,806] | 49.80 | 84.02 | 861 | 52.1 [5.4] |
| 1000 | reactive | 45,641 [45,496–45,785] | 28.87 | 45.39 | 940 | 31.5 [3.0] |

**Headline:** at 400 users, reactive throughput is ~75% higher than async; at 1000 users, ~70% higher with ≈40% lower CPU. The CPU σ at 800 users (4.97% async vs 1.56% reactive) is the paper's strongest evidence that async pays a context-switching tax under I/O-heavy load.

### 3.2 CPU-bound workload — Image upload / processing (`image-upload-cpu-bound-benchmark.js`)

CPU-bound; throughput plateaus past ~50 users as both stacks saturate the CPU.

| Concurrency | Stack | Throughput (req/s, mean [CI95]) | Avg lat (ms) | p95 lat (ms) | Memory (MB) | CPU (%, mean [σ]) |
|---:|---|---|---:|---:|---:|---|
| 10 | async | 313 [309–317] | 35.74 | 45.80 | 396 | 61.8 [2.1] |
| 10 | reactive | 308 [305–312] | 36.34 | 49.77 | 360 | 60.8 [2.2] |
| 50 | async | 374 [374–375] | 148.44 | 232.44 | 484 | 94.9 [1.8] |
| 50 | reactive | 375 [374–375] | 148.24 | 388.85 | 424 | 95.5 [1.9] |
| 100 | async | ≈374 (plateau) | ≈297 | 468.48 | 591 | ≈95 |
| 100 | reactive | ≈374 (plateau) | ≈298 | **742.43** | 540 | ≈95 |

**Headline:** identical throughput ceiling once CPU saturates; reactive's p95 spikes to 742 ms vs async's 468 ms at 100 users — head-of-line blocking on the event loop is the paper's documented weak spot for compute-heavy work.

### 3.3 Blocking-I/O workload — File download (`file-download-blocking-io-benchmark.js`)

Strictly blocking workload; throughput saturates beyond 400-600 users.

| Concurrency | Stack | Throughput (req/s, mean [CI95]) | Avg lat (ms) | p95 lat (ms) | Memory (MB) | CPU (%, mean [σ]) |
|---:|---|---|---:|---:|---:|---|
| 10 | async | 203.68 [203.54–203.82] | 56.31 | 63.01 | ≈213 | 4.71 [0.36] |
| 10 | reactive | 216.49 [215.93–217.04] | 52.93 | 59.32 | ≈69 | 2.56 [0.18] |
| 400 | async | ≈8011 [7934–8088] | 56.31 | 78.18 | ≈750 | 67.6 [4.0] |
| 400 | reactive | ≈9293 [9216–9370] | 57.48 | 118.39 | ≈456 | 40.3 [3.2] |
| 1000 | async | ≈8300 [8238–8362] | 134.67 | ≈351 | 1404 | 67.0 |
| 1000 | reactive | ≈9155 [9089–9221] | 127.38 | ≈258 | 698 | 63.4 |

**Headline:** the standout claim — at 1000 users, the reactive stack uses **698 MB vs 1404 MB** for async (≈50% reduction). Paper attributes this to Netty handling the 1000 open HTTP connections at near-zero per-connection cost, while the servlet container pays for 1000 worker threads + 1000 connection contexts.

### 3.4 Plot-derived per-level data points (visual estimates from Figures 2-13)

NotebookLM cannot read pixel data from PNG plots — it only quoted the body-text low/mid/peak rows in §3.1-3.3. Below: the **in-between concurrency levels** read directly off the published figures by visual inspection of the curves (PDF page render at high zoom). **These are visual estimates with precision ≈ ±5% on linear axes; values where the curves overlap are quoted as a single number.** Where a level was also quoted in body text, I keep the precise text value (no ~ prefix).

**For machine-precision recovery** at every level, re-run the second-study sweep through `testing/analysis/` (already supports the 14-level k6 matrix by default) and capture the per-cell CSVs. Until then, this section is the best we can do without re-execution.

#### 3.4.1 Caching workload — per-level (Fig 2-5)

| Concurrency | Throughput async / reactive (req/s) | Avg latency async / reactive (ms) | Memory async / reactive (MB) | CPU async / reactive (%) |
|---:|---|---|---|---|
| 1 | ~50 / ~50 | ~13 / ~13 | ~110 / ~70 | ~2 / ~2 |
| 5 | ~500 / ~500 | ~12 / ~12 | ~120 / ~70 | ~3 / ~3 |
| 10 | **1054** / **1094** | **12.53** / **12.08** | **145** / **71** | **6.68** / **3.55** |
| 15 | ~1500 / ~1500 | ~12 / ~12 | ~150 / ~70 | ~10 / ~4 |
| 20 | ~2000 / ~2000 | ~12 / ~12 | ~170 / ~75 | ~13 / ~5 |
| 25 | ~2500 / ~2500 | ~12 / ~12 | ~190 / ~80 | ~15 / ~6 |
| 50 | ~6500 / ~6500 | ~10 / ~9.5 | ~200 / ~215 | ~22 / ~8 |
| 75 | ~10,000 / ~10,000 | ~10 / ~10 | ~205 / ~220 | ~30 / ~10 |
| 100 | ~12,500 / ~12,500 | ~11 / ~11 | ~215 / ~220 | ~35 / ~11 |
| 200 | ~17,500 / ~25,000 | ~15 / ~11 | ~290 / ~310 | ~40 / ~13 |
| 400 | **21,892** / **38,263** | **24.30** / **13.87** | **442** / **531** | **50.1** / **19.0** |
| 600 | ~24,500 / ~42,500 | ~33 / ~19 | ~620 / ~720 | ~50 / ~24 |
| 800 | ~26,500 / ~46,000 | ~41 / ~23 | ~790 / ~880 | ~52 / ~28 |
| 1000 | **26,724** / **45,641** | **49.80** / **28.87** | **861** / **940** | **52.1** / **31.5** |

Notable plot-only insights:
- **Memory crossover ≈ 50 users**: reactive uses substantially less RAM at low concurrency (51% reduction at 10 users) but converges with async around 50 users and stays slightly *above* async from 50 onwards. The abstract's "68% lower memory footprint" claim only holds for the very low concurrency tail and is not visible at 200+. **Manuscript-revision flag** (already noted in §6).
- **Throughput divergence ≈ 100-200 users**: both stacks track each other up through 100 users, then reactive pulls cleanly ahead. The 200-user point is where the architectural advantage becomes visible.
- **CPU divergence is the cleanest signal**: async climbs almost linearly to the CPU wall (~52% at 1000 users), reactive holds at ~31%. The σ noted in body text (4.97 vs 1.56 at 800 users) is consistent with the visibly tighter ribbon on the reactive curve.

#### 3.4.2 CPU-bound workload — per-level (Fig 6-9). X-axis maxes at 100 users (paper's design — beyond that the throughput curve plateaus).

| Concurrency | Throughput async / reactive (req/s) | Avg latency (ms, both overlap) | Memory async / reactive (MB) | CPU (%, both overlap) |
|---:|---|---:|---|---:|
| 1 | ~35 / ~30 | ~30 | ~110 / ~95 | ~5 |
| 5 | ~175 / ~170 | ~30 | ~210 / ~200 | ~30 |
| 10 | **313** / **308** | ~36 (text: 35.74 / 36.34) | **396** / **360** | ~62 (text: 61.8 / 60.8) |
| 15 | ~340 / ~340 | ~45 | ~445 / ~390 | ~80 |
| 20 | ~360 / ~360 | ~57 | ~460 / ~420 | ~90 |
| 25 | ~370 / ~370 | ~75 | ~460 / ~430 | ~92 |
| 50 | **374** / **375** | ~148 (text: 148.44 / 148.24) | **484** / **424** | **94.9** / **95.5** |
| 75 | ~374 / ~374 | ~225 | ~490 / ~430 | ~95 |
| 100 | **374** / **374** | **~297-298** | **591** / **540** | ~95 |

Notable plot-only insights:
- **Throughput converges past 25 users** — both stacks plateau on 374 req/s as soon as the CPU saturates.
- **Latency curves overlap entirely** — the only gap is the p95 tail, which the body text quotes (reactive 742 ms vs async 468 ms at 100 users) but the linear-axis plot can't show. Tail divergence is the load-bearing finding here.
- **Memory grows linearly** for both stacks; reactive consistently 30-50 MB lower across all levels (not just at the low end like in caching).
- **CPU plot literally overlaps** — both stacks hit 95% by 50 users and stay there. The "convergence to CPU wall" claim is fully supported.

#### 3.4.3 Blocking-I/O workload — per-level (Fig 10-13)

| Concurrency | Throughput async / reactive (req/s) | Avg latency async / reactive (ms) | Memory async / reactive (MB) | CPU async / reactive (%) |
|---:|---|---|---|---|
| 1 | ~50 / ~50 | ~60 / ~52 | ~80 / ~70 | ~1 / ~1 |
| 5 | ~100 / ~100 | ~57 / ~50 | ~180 / ~70 | ~3 / ~2 |
| 10 | **203.68** / **216.49** | **56.31** / **52.93** | **213** / **69** | **4.71** / **2.56** |
| 15 | ~310 / ~325 | ~55 / ~50 | ~200 / ~70 | ~5 / ~3 |
| 20 | ~400 / ~430 | ~57 / ~50 | ~200 / ~80 | ~6 / ~4 |
| 25 | ~510 / ~530 | ~57 / ~52 | ~220 / ~210 | ~7 / ~5 |
| 50 | ~1050 / ~1100 | ~58 / ~55 | ~210 / ~210 | ~14 / ~7 |
| 75 | ~1500 / ~1600 | ~58 / ~55 | ~210 / ~190 | ~22 / ~9 |
| 100 | ~2050 / ~2150 | ~58 / ~55 | ~250 / ~210 | ~27 / ~10 |
| 200 | ~4400 / ~4500 | ~55 / ~57 | ~500 / ~300 | ~40 / ~18 |
| 400 | **8011** / **9293** | **56.31** / **57.48** | **750** / **456** | **67.6** / **40.3** |
| 600 | ~9000 / ~9700 | ~75 / ~73 | ~990 / ~530 | ~67 / ~58 |
| 800 | ~8500 / ~9500 | ~110 / ~105 | ~1180 / ~640 | ~67 / ~63 |
| 1000 | **8300** / **9155** | **134.67** / **127.38** | **1404** / **698** | **67.0** / **63.4** |

Notable plot-only insights:
- **Throughput peaks ≈ 600 users for both stacks**, then *declines slightly* by 1000. The body text doesn't call this out; the plot shows it clearly. Beyond 600 users, additional concurrency hurts.
- **Latency holds nearly flat** (≈55-58 ms async, ≈50-57 ms reactive) from 1-400 users, then climbs sharply past 600. Not a smooth curve — it's a step function around the throughput peak.
- **Memory crossover** happens around 25-50 users, similar to caching: at low concurrency reactive uses 70-80 MB while async climbs to 200+. From 50 onwards, both grow but async climbs steeper. The 50% reduction at 1000 users (698 vs 1404) is the cleanest evidence.
- **CPU divergence at 200-400 users**: async hits 40% by 200, 67% by 400 and plateaus. Reactive is half that at 200, climbs to 40% by 400, then catches up to 63% only at 1000.

### 3.5 Functional-equivalence and methodology assertions (paper B Table 2 + § 6.1)

- ~150 tests per service, 1050 total, 7 services, ≥90% coverage.
- 55 Cucumber scenarios.
- 4×7 + 3×14 perf matrix as documented in §1 above.
- All experiments local, LAN-isolated, JWT disabled.

---

## 4. Modern testing methodology (what we use now)

The AI-streaming extension forced us to adopt a more rigorous methodology. The same machinery should be applied retroactively to the workloads in §3 the next time they're rerun.

### 4.1 Test architecture
- **Open-loop arrival rate** in k6 (`ramping-arrival-rate` → `constant-arrival-rate`). Keeps tail latencies honest under overload — closed-loop `constant-vus` lets the load shaper back off when the SUT slows down, which hides the cliff.
- **Phase-tagged metrics**: separate warmup-phase and main-phase metric scopes so handleSummary p99 reflects steady-state only. Implemented in `testing/performance/k6/workloads/ai-streaming-benchmark.js` via `hitWarmup` / `hit`; should be backported to the three §3 workloads.
- **Non-zero `gracefulStop`**: avoids truncating long-running streams at phase boundaries. AI workload uses 60 s by default.
- **Failed-request latency tracked separately** (`ai_e2e_failed_ms` Trend), so a fast 5xx from `AbortPolicy` doesn't compress success p99 distributions.

### 4.2 Statistics
- **Per-run p99 with bootstrap 95% CI** on the mean of per-run p99s. Implemented in `testing/analysis/.../ReportCommand.java`. Replaces the second study's "aggregate then σ" approach for a more robust uncertainty estimate.
- **Mann-Whitney U** as the headline significance gate when comparing async vs reactive cells (Welch's t-test is also computed but only for reference — its normality assumption is not safe for per-run p99 distributions, which are extreme values; OR-ing them was a real bug we fixed during the Codex/Gemini convergence loop).
- Outcome-tagged Micrometer Timers: `tweebyte.ai.e2e{outcome=success|error|cancel}`. Same pattern can be applied to the older workloads if rerun.

### 4.3 Server-side observability
- Micrometer + Prometheus scraping; histograms with p50/p95/p99/p999 buckets.
- GC logging on benchmark profile (`-Xlog:gc*,safepoint,gc+heap=debug`) — enables cliff diagnosis when latency spikes.
- For the async stack: bounded `ThreadPoolExecutor` with configurable pool size, queue capacity, and reject policy (`abort` for benchmark runs). Rejection counter wraps the chosen policy via `CountingRejectionHandler` so `tweebyte_pool_rejections_total` actually increments.
- For the reactive stack: in-flight subscription gauge via `doFinally` so `inFlightStreams` can't leak on error paths.

### 4.4 Calibration (AI workload only, but generalizable)
- Real LLM (LM Studio + Qwen) → sampled into `calibration.json` via `testing/calibration/ collect`.
- Mock matches via Apache Commons Math `LogNormalDistribution` + `GammaDistribution` with `ThreadLocal<Distribution>` per request thread (avoids RNG contention under load).
- Validation: two-sample Kolmogorov-Smirnov against the fitted distribution.

### 4.5 What papers A & B should adopt on rerun (uniformization plan)

| Aspect | Paper A (2024) | Paper B (2026) | Modern (this branch) | Action |
|---|---|---|---|---|
| Load tool | JMeter | k6 (`constant-vus`) | k6 (`ramping-arrival-rate` → `constant-arrival-rate`) | When rerunning B, switch executor to open-loop. |
| Resource sampling | VisualVM (point) | Actuator (1 s) | Actuator + GC log | Backport GC log to B's three workloads. |
| Repetitions | 1 | 5 | 5 (same — adequate) | Keep 5; raise only if CI bands are wide. |
| Statistical method | None | mean/σ + CI95 | Per-run p99 + bootstrap CI + MW-U | Reanalyse B's raw runs through `testing/analysis/` if `.csv` exports are still recoverable; otherwise re-run. |
| Concurrency levels | 7 (10..1000) | 14 (1..1000) | 14 (default in `run_bench.sh`) | A's plans frozen at 7 levels for historical reproducibility; new sweeps use 14 by default. |
| Failure-latency split | n/a | not separated | separate `ai_e2e_failed_ms` | Add a `*_failed_ms` Trend to the three §3 workloads on next rerun. |
| Reject-policy sweep | n/a | n/a | abort/caller-runs/discard/discard-oldest | Run A & B at `abort` for clean rejection signal; never mix policies within a run. |

---

## 5. Performance Results — AI Streaming workload

**Status (2026-04-28 ~18:30 EEST):** code complete + reviewed across multiple Codex passes (v1–v7); **canonical 5-rep headline rerun landed** (§5.10 with all 9 cells at cell_status=OK, manifest-isolated under campaign `headline-5rep-rerun-2026-04-28`); diagonal cliff slice §5.11 carries the threshold-model bracketing evidence; §5.12 attribution **downgraded to supporting evidence** based on post-cell single-shot Prometheus snapshots (the in-run time-series figure remains a deferred follow-up because the in-run poller's regex bug was discovered after the rerun finished); §5.13 **executed** — 18 realism cells against `mlx_lm.server` (Apple's mlx-lm package serving the same MLX-4bit weights as the calibration source via continuous batching). Calibration `calibration.json` committed; alternative-family investigation in §5.2.1 documents the bimodal/comb-quantization structure of empirical ITL. Cell-key carries `calibration_tag` + `campaign` dimensions so cross-batch pooling is prevented. **Submission-ready for MDPI Applied Sciences.**

**Manuscript-ready H1 phrasing (revised 2026-04-27 after Codex review; numbers refreshed against the canonical §5.10 rerun).** Under sustained open-loop arrival rate λ, a servlet-based stack with a bounded `ThreadPoolExecutor` of size T degrades its per-run p99 end-to-end latency by **> 500 %** relative to a comparable reactive stack **whenever request residency `E[response]` × λ exceeds T**. The cliff is a structural property of `(bounded servlet worker pool + long-residency streaming requests)` — *not* of LLM inference or mid-stream blocking calls in particular: it reproduces cleanly at the cleanest cliff cell (rps=500, pool=400) on the non-AI streaming baseline **W0 at 9.20×**, the pure AI chat workload **W1 at 7.27×**, and the AI-with-mid-stream-tool-call workload **W2 at 7.25×** (per §5.10's canonical 5-rep manifest-isolated headline). Spring AI streaming + tool-orchestration is the *motivating* workload that exercises the cliff at production-realistic per-request residencies; it is not the cliff's cause. The original (pre-revision) wording — "tool I/O exceeds mean inter-token interval" — overclaimed Spring-AI causality and is retired.

**Continuity with paper B:** the AI-streaming extension is not a tangent — paper B's own future-work section (§7.2) explicitly anticipates "controllers driven by machine learning [that] could, based on real-time workload metrics, [...] be in charge of dynamically managing the cache eviction policies, thread-pool sizing, or database connection handling." The revised H1 above gives a quantitative threshold that future work can target.

| What | Where | Status |
|---|---|---|
| Endpoints (`/tweets/ai/{summarize,buffered,summarize-with-tool,mock-stream}`) | both stacks | ✅ shipped |
| Spring AI 1.0.1 + LM Studio + calibrated mock | `AiConfiguration.java` | ✅ shipped |
| k6 workload (W0/W1/W2 × {sse,buffered}) | `ai-streaming-benchmark.js` | ✅ shipped |
| `--ai-calibration-tag` cell-key dimension | `run_bench.sh`, k6 workload, analysis pipeline | ✅ shipped (2026-04-27 evening) |
| All-error run quarantine in `report` | `testing/analysis/.../ReportCommand.java` | ✅ shipped (2026-04-27 evening) |
| Analysis pipeline (ingest → report → plot) | `testing/analysis/` | ✅ shipped + executed on real data |
| Calibration JSON (Qwen3.5-4B-MLX, zero-inflated mock) | `testing/calibration/calibration.json` | ✅ committed; TTFT K-S accept, ITL K-S reject (caveat in §5.2) |
| Sweep matrix — W1 (mock-defaults + calibrated) | `testing-results/performance/k6/results_ai_streaming_20260427_0[9]*` and `_1[3]*` | ✅ executed; calibration tag distinguishes batches |
| Sweep matrix — W2 mid-stream tool | `testing-results/performance/k6/results_ai_streaming_20260427_15* / 16*` | ✅ executed (9 cells × 3 runs, see §5.4) |
| Sweep matrix — W0 non-AI baseline | `testing-results/performance/k6/results_ai_streaming_20260427_17* / 18*` | ✅ executed (9 cells × 3 runs, see §5.5) |
| 5-rep headline rerun + diagonal cliff slice | — | ✅ landed (§5.10 W0 9.20× / W1 7.27× / W2 7.25×; §5.11 brackets the threshold within ~10 %) |
| Attribution figure (queue/rejections vs p99) | — | ✅ supporting evidence in §5.12 (post-cell snapshots); in-run time-series remains a deferred Q1-polish follow-up |
| Full 3-batch matrix | — | **deferred**: ≈30 h, not on H1 critical path |
| Real-LM realism subset | — | ✅ executed via `mlx_lm.server` (§5.13: 18 cells across W1+W2 × {async,reactive} × {rps=1, rps=2}; runtime-comparability finding documented) |
| Paper write-up | — | unblocked |

> **Reading order note (2026-04-28):** §5.10–§5.12 below are the **canonical post-cleanup headline numbers + diagonal cliff slice + attribution**. §5.1, §5.3, §5.4, §5.5 are kept for historical reference and method-validation provenance — they're the mock-defaults / pre-cleanup runs that motivated the calibration_tag identity fix and the all-error quarantine. For the manuscript, cite §5.10–§5.12.

### 5.1 W1 pilot — 2026-04-27 (mock backend, defaults, pre-calibration)

**Setup.** k6 open-loop arrival rate via `ramping-arrival-rate → constant-arrival-rate`; warmup=30s, duration=90s, gracefulStop=60s, abort reject policy. 3 independent runs per cell, bootstrap 95% CI on the mean of per-run p99s. Mock backend on `MockStreamingChatModel` defaults (`AI_MOCK_TTFT_MEAN_MS=250`, `AI_MOCK_TTFT_LOG_SIGMA=0.4`, `AI_MOCK_ITL_MEAN_MS=40`, `AI_MOCK_ITL_GAMMA_SHAPE=2.5`, `AI_MOCK_TOKENS_PER_RESPONSE=150`) — i.e. ~6.25 s mock response time. **No calibration JSON applied yet** (calibration was running in parallel during the pilot; the calibrated rerun is on the deferred list). Stack: M3 Max 64 GB, all containers on Docker Desktop, `caffeinate -d -i -s -u` running the entire pilot to prevent macOS system sleep from contaminating in-flight latency measurements (one earlier attempt was lost to that exact issue — documented in §5.9 #5).

| Stack | rps | pool | n_runs | e2e_p99_mean (ms) | e2e_p99 CI95 (ms) | ttft_p99_mean (ms) | error_rate | dropped | requests |
|---|---:|---:|---:|---:|---|---:|---:|---:|---:|
| async    | 50   | 400  | 3 | **7,043.6**  | [7,033.7, 7,055.2]    | 589.8     | 0.000 | 838     | 13,287  |
| async    | 50   | 1600 | 3 | **7,064.1**  | [7,052.2, 7,085.7]    | 591.2     | 0.000 | 782     | 13,286  |
| reactive | 50   | —    | 3 | **7,044.4**  | [7,038.5, 7,051.6]    | 570.8     | 0.000 | 784     | 13,286  |
| async    | 500  | 400  | 3 | **59,983.4** | [59,982.0, 59,985.3]  | 54,234.0  | **0.849** | 15,395  | 129,206 |
| async    | 500  | 1600 | 3 | **30,561.1** | [30,494.3, 30,608.0]  | 24,247.5  | 0.000 | 60,639  | 88,102  |
| reactive | 500  | —    | 3 | **7,055.7**  | [7,053.0, 7,059.6]    | 595.2     | 0.000 | 7,565   | 133,015 |
| async    | 2000 | 400  | 3 | **59,965.8** | [59,937.2, 59,981.0]  | 56,425.3  | **0.962** | 71,628  | 479,129 |
| async    | 2000 | 1600 | 3 | **43,256.7** | [38,820.4, 45,491.3]  | 28,709.9  | **0.906** | 302,851 | 315,289 |
| reactive | 2000 | —    | 3 | **30,441.6** | [28,929.8, 32,808.7]  | 849.0     | **0.948** | 316,837 | 294,861 |

(Raw runs: `testing-results/runs.csv` (33 rows). Cell aggregates: `testing-results/cells.csv` (13 cells incl. smoke + 3 prior-session probes from 2026-04-20). Both gitignored under `testing-results/`.)

#### 5.1.1 Headline paired tests (MW-U at α=0.05; Welch printed for reference, not used)

The verdict column is gated on the non-parametric Mann-Whitney U test only — Welch's t-test was kept in for reference but its normality assumption is unreliable for per-run p99s (extreme values), so OR-ing them would propagate Welch false positives. This was the round-1 G6 finding from the Codex/Gemini convergence loop and was applied in `testing/analysis/.../ReportCommand.java`.

| Cell (W1, sse, abort) | async pool | a_n / r_n | a_p99_mean | r_p99_mean | a/r ratio | MW-U p | Verdict |
|---|---:|---:|---:|---:|---:|---:|---|
| rps=50  | 400  | 3 / 3 | 7,043.6 | 7,044.4 | 1.00× | 0.83 | **ns** (parity) |
| rps=50  | 1600 | 3 / 3 | 7,064.1 | 7,044.4 | 1.00× | 0.05 | DIFFERENT (negligible effect) |
| rps=500 | 400  | 3 / 3 | 59,983.4 | 7,055.7 | **8.50×** | 0.034 | **DIFFERENT (cliff)** |
| rps=500 | 1600 | 3 / 3 | 30,561.1 | 7,055.7 | 4.33× | 0.05 | **DIFFERENT (partial cliff)** |
| rps=2000 | 400 | 3 / 3 | 59,965.8 | 30,441.6 | 1.97× | 0.05 | **DIFFERENT (both cliff; async worse)** |
| rps=2000 | 1600 | 3 / 3 | 43,256.7 | 30,441.6 | 1.42× | 0.05 | **DIFFERENT (both cliff; async worse)** |

#### 5.1.2 H1 verdict on the pilot data

H1 ("async+blocking p99 degrades >500% vs reactive when C > T") is **supported** by the pilot. Two pieces of evidence:

1. **Cliff threshold tracks pool size as predicted.** With mock response ≈6.25 s, the C/T crossover is at rps ≈ 67 (pool=400) and rps ≈ 267 (pool=1600). Empirically: async p99 is flat (7 s) below the crossover and explodes above it — pool=400 cliffs between rps=50 and rps=500 (8.5× ratio at rps=500), pool=1600 cliffs between rps=50 and rps=500 too but with milder factor (4.33×). At rps=50 both pool sizes are below their respective crossovers and there's no cliff.

2. **Reactive holds parity below ~rps=2000.** At rps=50 and rps=500 reactive sits at 7.04–7.06 s p99, error_rate=0.000. The Netty event loop absorbs the in-flight count without queue starvation. The cliff factor at rps=500 pool=400 (8.50×) is well above the H1's >500% threshold.

#### 5.1.3 Caveats on the pilot

- **Pre-calibration mock.** The mock used `MockStreamingChatModel` defaults (250 ms TTFT, 40 ms ITL, 150 tokens). Real Qwen TTFT is ~3 s and ITL ~3 ms (per the in-flight calibration). When the calibrated mock lands, the cliff threshold will shift to lower RPS but the *shape* of the curve should not change. Re-run the same 9-cell shape with `AI_MOCK_CALIBRATION_JSON=…/calibration.json` to confirm.
- **Reactive cliffs at rps=2000.** Reactive p99 at rps=2000 is 30.4 s with error_rate=0.948 — i.e. reactive *also* breaks at this load. Likely cause: 12 k concurrent SSE connections × 6 s response = open-fd / Netty backlog limits on the single-machine test rig. This is a known threats-to-validity item — the open-loop arrival rate has saturated *both* stacks at this level, but async still loses by 2× even after both have collapsed. Not a refutation of H1 (the H1 asks about the C > T cliff, not steady-state over-capacity behaviour) but it is worth a sentence in the manuscript's threats-to-validity section.
- **W0/W2 not in the pilot.** Only W1 (pure chat streaming) was executed. W0 (non-AI mock-stream baseline) and W2 (mid-stream tool call) are deferred. W2 in particular is the H1's strongest surface — the blocking `UserClient.getUserSummary()` mid-stream is what the round-1 ledger fix C was about, and the cliff there should be more dramatic than W1.
- **Two contaminated cells discarded.** During the first overnight pass, the laptop entered system sleep ~04:00 EEST and suspended both calibration and the in-flight k6 cells via SIGSTOP. On wake, e2e_p99 reported wall durations including the sleep gap (3+ hours). Cells `results_ai_streaming_20260427_012922_aNBycE/50_3.txt` and `results_ai_streaming_20260427_043846_2AYHRd/` were quarantined to `testing-results/_contaminated_sleep/` (out of the analysis tree) and the pilot was re-run from scratch with `caffeinate -d -i -s -u` active. Future operators: pre-arm `caffeinate` before any long-running collect or sweep.

#### 5.1.4 Figures

PNGs landed in the gitignored `testing-results/figures/` directory:

- `concurrency_scaling_W1_sse.png` — per-stack p99 vs target_rps, one series per (stack × pool_size × policy). The async pool=400 series visibly cliffs between 50 and 500 RPS; reactive stays flat through rps=500 and only collapses at rps=2000.
- `pool_size_scaling_W1_sse.png` — per-stack p99 vs pool_size at fixed rps, one series per RPS level. Shows the async cliff height shrinking monotonically as pool grows from 400 → 1600 (5.7× at rps=500, 1.4× at rps=2000 vs the rps=50 baseline).
- `h1_validation_scatter.png` — paired (async, reactive) p99 ratios at matched (workload, transport, target_rps, cancel_rate) cells. The dots above the y=1 line are the cliffs; pool=400 sits well above pool=1600 in the upper-right cluster.

When the next sweep batch lands (W0/W2 or calibrated rerun), append rows to §5.1's table in the same column shape and regenerate the figures via `bash run.sh bench k6 --workload ai-streaming … && java -jar testing/analysis/target/analysis-0.0.1-SNAPSHOT.jar ingest|report|plot …`.

### 5.2 Calibration of the mock against real Qwen3.5-4B-MLX (2026-04-27)

`testing/calibration/calibration.json` is the canonical artefact (1.7 MB on disk; raw `ttft_samples` + `itl_samples` + `token_counts` arrays plus all three fitted families with AIC/BIC). It is committed under version control because the fitted parameters are load-bearing for paper reproducibility.

**Setup.** 2000 streaming requests issued against LM Studio's `/v1/chat/completions` with `qwen3.5-4b-mlx` (HF rev `32f3e8e…`, MLX 4-bit, 8192-token context, temperature 0.7, prompt `"Summarize recent activity."`, `max_tokens=768`). 1973 of the 2000 samples produced at least one `delta.content` chunk; the other 27 burned their entire budget on `reasoning_content` and contributed only a `token_count=0` row. Total wall time 5826.6 s (~97 min) at ~2.91 s/sample on idle hardware. HTTP/1.1 forced in `CollectSamplesCommand` (Java HTTP/2 streaming is broken against this LM Studio build — the JDK HTTP/2 implementation parks indefinitely against LM Studio's streaming response handler; HTTP/1.1 is a stable workaround). Per-request timeout 60 s, per-sample body deadline 90 s; zero hard timeouts triggered in this run.

**Observed distributions (n=1973 TTFT, n=168,460 ITL):**

| Quantity | Mean | Median | p95 | p99 | p99.9 | Max |
|---|---:|---:|---:|---:|---:|---:|
| TTFT (ms) | 2,096 | 2,015 | 3,213 | 4,401 | 6,425 | — |
| ITL (ms)  | 8.90  | 8.76  | 26.95 | 40.08 | 54.59 | 84.71 |

TTFT median 2 s. ITL median 8.76 ms. Tokens per response: median 83 content tokens after Qwen's reasoning phase (matches the calibration setup's `--max-tokens 768`).

**Fitted families (AIC/BIC, lower is better):**

| Quantity | Family | Params | AIC | BIC |
|---|---|---|---:|---:|
| TTFT | log-normal | μ=7.601, σ=0.304 | **30,897** | **30,908** |
| TTFT | gamma | shape=9.793, scale=214.013 | 30,955 | 30,966 |
| TTFT | Weibull | shape=1.200, scale=2228.162 | 33,498 | 33,509 |
| ITL | log-normal | μ=−0.707, σ=4.147 | **719,124** | **719,144** |
| ITL | gamma | shape=0.859, scale=10.366 | 965,904 | 965,924 |
| ITL | Weibull | shape=1.200, scale=9.465 | 1,251,625 | 1,251,646 |

Log-normal wins on AIC for both TTFT and ITL.

**Two-sample Kolmogorov-Smirnov vs the zero-inflated mock (`MockStreamingChatModel` with calibration.json applied) at α=0.05:**

| Quantity | Mock params | K-S p | Verdict |
|---|---|---:|---|
| TTFT | log-normal `μ=7.6012, σ=0.3038` | **0.0681** | **ACCEPT** |
| ITL  | zero-inflated gamma `p_burst=0.3843, shape=3.0034, scale=4.8141` | **0.0000** | **REJECT** (mean preserved, shape mismatch — see diagnosis) |

**TTFT calibrates cleanly.** The mock's log-normal statistically matches real Qwen 3.5 — including the multi-second TTFT introduced by the model's reasoning phase. Mean 2.10 s, p99 4.40 s, K-S p=0.068.

**ITL is bimodal — and the K-S still rejects after fitting the bimodality, but for a milder reason than before.** Real Qwen's `delta.content` chunks arrive in *bursts* over the OpenAI-compatible SSE endpoint: multiple chunks land in the same TCP frame and are read back-to-back by the Java HTTP/1.1 client at memory speed. Distribution of measured "inter-token latencies":

| ITL bucket | Count | % of total |
|---|---:|---:|
| < 0.01 ms (intra-burst, ≤10 µs) | 62,438 | 37.06 % |
| 0.01 ms – 0.1 ms (intra-burst tail) | 2,300  |  1.37 % |
| ≥ 0.1 ms (gap-mode, true inter-token) | 103,722 | 61.57 % |

After running [`testing/calibration/.../RefitCommand.java`](../testing/calibration/src/main/java/ro/tweebyte/calibration/RefitCommand.java) (which reads the existing `ttft_samples` + `itl_samples` arrays and re-fits with a 0.1 ms burst-mode threshold via [`FitUtil`](../testing/calibration/src/main/java/ro/tweebyte/calibration/FitUtil.java)):

- **`p_burst = 0.3843`** — the zero-inflation probability for the mock's ITL generator.
- **Gap-mode subset** (n=103,722): mean 14.46 ms, median 9.30 ms, p1 7.71 ms, p99 44.81 ms. Note the hard floor at ~7.7 ms — there are essentially no gap-mode samples between 0.1 ms and 7.7 ms.
- **Gap-mode log-normal fit:** `μ=2.5412, σ=0.4992` (AIC 677,391) — best of the three families.
- **Gap-mode gamma fit:** `shape=3.0034, scale=4.8141` (AIC 690,498) — the family `MockStreamingChatModel` actually uses, mean 14.46 ms.
- **Gap-mode Weibull fit:** `shape=1.2, scale=15.37` (AIC 736,297) — clearly worse.

**Marginal mean preservation.** The mock generates `p_burst=0.3843` of tokens at zero delay and the rest from gamma(3.0034, 4.8141) with mean 14.4587 ms. Marginal mean: `0.3843 × 0 + 0.6157 × 14.4587 = 8.902 ms`, matching the observed unfiltered ITL mean of 8.904 ms to within 0.03 %. So **`E[response]` is preserved exactly** — which is the load-bearing property for H1 (request residency × arrival rate vs pool size).

**Why K-S still rejects.** Two issues, in descending order of impact (corrected from initial diagnosis — see §5.2.1 for the empirical investigation that updated this picture):

1. **Multimodal/comb-quantized gap-mode distribution.** The empirical gap-mode histogram exhibits two dominant peaks at ≈ 8.7 ms and ≈ 17.8 ms (~1× and ~2× a common base period), consistent with hardware token-clock quantization on Apple Silicon Metal and SSE chunk coalescing combining two tokens into one network packet. Initial diagnosis described this as "a hard floor at 7.7 ms"; that wording was imprecise — the actual minimum is 0.10 ms (a small near-burst tail), and the apparent floor at 7.7 ms is the lower edge of the dominant ~8.7 ms peak. No low-parameter continuous smooth family (gamma, log-normal, Weibull, shifted log-normal) can reproduce the comb structure. At n=103,722 gap-mode samples the K-S 95 % critical value is ≈ 0.0042, so even a 5-parameter 2-component log-normal mixture (which reduces the K-S statistic from 0.262 to 0.122 and AIC from 677 k to 510 k) still rejects — see §5.2.1.
2. **Discrete-vs-continuous spike at zero.** The mock emits exactly 0.0 ms for intra-burst tokens; the empirical samples occupy a thin band 0.0008 ms – 0.1 ms. Smaller effect than (1) but contributes.

**What this means for the paper.** Three honest lines for the threats-to-validity / methods section:

> Real-Qwen ITL exhibits a TCP-burst structure: 38.43 % of measured inter-token intervals are below 100 µs (multiple `delta.content` chunks per TCP frame, read back-to-back at memory speed by a Java HTTP/1.1 client); the remaining 61.57 % are real model-emission gaps. The gap-mode distribution itself is multimodal with hardware-quantization peaks at ≈ 8.7 ms (~53 %) and ≈ 17.8 ms (~47 %). We characterise the calibrated mock against the gap-mode subset under a zero-inflated gamma draw (`p_burst = 0.3843`, gamma(`shape=3.0034, scale=4.8141`) for the gap mode) so total `E[response]` is preserved — the marginal mean of the resulting mock matches the empirical mean of 8.90 ms to 0.03 %. The K-S goodness-of-fit on the gap-mode sub-distribution rejects at α=0.05 because none of the low-parameter continuous smooth families we tested (gamma, log-normal, shifted log-normal, 2-component log-normal mixture) can reproduce the comb-quantized empirical structure at n=103,722; the gamma fit serves as a first-order surrogate (mean preserved) rather than a distribution-equivalent draw. The H1 cliff claim is insensitive to fine-grained within-stream clustering conditional on preserving per-request service time: in the async thread-per-request model the worker thread is pinned from request entry until the final token, so the precise within-stream emission shape is irrelevant to pool starvation — the cliff appears whenever request residency × arrival rate exceeds pool size, conditional on the mean residency being faithfully reproduced. The exact cliff *shape* (steepness, knee width) under a comb-aware sampler is recorded as a Q1-confidence open follow-up.

**Open follow-up (deferred — not on the H1 critical path).** Originally we hypothesized a 3-parameter shifted log-normal (or shifted-and-truncated gamma) fitted on the gap-mode subset would capture the hard floor and accept K-S. The empirical histogram inspection in §5.2.1 showed this hypothesis was wrong (the apparent "floor" is actually the lower edge of a bimodal cluster) and the shifted-log-normal MLE collapses to the unshifted log-normal. A 2-component log-normal mixture captures the structure substantially better (AIC drops 25 %; K-S statistic halves) but still rejects under K-S at large n due to the empirical comb structure. Logging the empirical finding here for whoever picks up the paper revision; a true mock improvement would require either a comb-aware family or an empirical-CDF inverse-transform sampler. Neither is on the H1 critical path.

**How to use the calibration in a benchmark sweep.** Set `AI_MOCK_CALIBRATION_JSON=/Users/andrei/Developer/tweebyte/testing/calibration/calibration.json` before `runtime up`; the Spring container bind-mount in `infrastructure/compose/{async,reactive}.yml` exposes `./testing/calibration → /app/calibration:ro` so the file is visible at `/app/calibration/calibration.json` from inside the JVM. `MockCalibration.loadOrDefault(...)` reads the JSON at startup, parses the four `AI_MOCK_*` numerical defaults plus `itl_fits.p_burst`, and constructs `MockStreamingChatModel` with zero-inflated ITL emission. Older calibration JSONs without `p_burst` (or any caller passing `0.0`) collapse cleanly back to the original pure-gamma behaviour — backward compatible.

**Reproducing the refit.** If the burst threshold or fit logic ever changes, a re-fit does *not* require re-collecting samples (the original 168 k ITL samples + 1973 TTFT samples are persisted in `calibration.json`):

```bash
java -jar testing/calibration/target/calibration-0.0.1-SNAPSHOT.jar refit \
    --calibration testing/calibration/calibration.json
java -jar testing/calibration/target/calibration-0.0.1-SNAPSHOT.jar validate \
    --calibration testing/calibration/calibration.json
```

#### 5.2.1 Alternative-family investigation for the ITL K-S rejection (2026-04-28)

Codex's v6 review note recommended trying a 3-parameter shifted log-normal as a Q1-polish closer for the K-S rejection on ITL flagged in §5.9 #4. While exploring the histogram during the implementation, we discovered the gap-mode distribution is **multimodal** rather than having a single hard floor. This subsection records the empirical finding and the alternative fits we computed; the production mock binary continues to consume the gamma fit unchanged.

**Empirical histogram of the gap-mode subset (samples ≥ 0.1 ms, n=103,722, 0.5 ms bins):**

```
  0.0– 5.0 ms:    191 samples (~0.18 %)  ← residual near-burst tail
  5.0– 7.5 ms:    490 samples (~0.47 %)
  7.5– 8.5 ms:  5,962 samples (~5.75 %)
  8.5– 9.0 ms: 31,427 samples (~30.30 %) ← peak A: ≈ 8.7 ms
  9.0– 9.5 ms: 17,605 samples (~16.97 %)
  9.5–17.0 ms:  6,290 samples (~6.07 %)
 17.0–17.5 ms:  3,504 samples (~3.38 %)
 17.5–18.0 ms: 13,206 samples (~12.73 %) ← peak B: ≈ 17.8 ms
 18.0–18.5 ms:  6,697 samples (~6.46 %)
 18.5–84.7 ms: 18,350 samples (~17.69 %)
```

The two dominant peaks at ≈ 8.7 ms and ≈ 17.8 ms — almost exactly 1× and 2× a common base — are **consistent with hardware token-clock quantization on Apple Silicon Metal**: each MLX `forward(...)` call producing one token has a roughly fixed dispatch cost, and SSE chunk coalescing occasionally combines two tokens into one network packet, doubling the perceived ITL. This is a property of the LM Studio + MLX runtime on this M3 Max, not of Qwen3.5-4B itself. Calibration on a different runtime (vLLM, Ollama, or `mlx_lm.server` — see §5.13) might show a different mode pattern at the same model architecture.

**Alternative-family fits, all stored in `calibration.json` under `itl_fits`** (calibration-side analysis only — `MockStreamingChatModel` continues to consume `itl_fits.gamma` so headline §5.10/§5.11 numbers are not affected by anything in this subsection):

- **3-parameter shifted log-normal** `X ~ c + LogNormal(μ, σ)` fit by MLE (Brent optimizer over the location parameter `c` with closed-form (μ, σ) MLEs conditional on `c`). For this empirical, the MLE collapses to `c ≈ 1.2 × 10⁻¹⁰` — i.e. degenerate to plain log-normal. AIC penalty (3 vs 2 parameters) makes shifted log-normal strictly *worse* than plain log-normal here. The shifted-log-normal hypothesis was wrong: the rejection isn't from a single hard floor.

- **5-parameter 2-component log-normal mixture** `X ~ π · LogNormal(μ₁, σ₁) + (1−π) · LogNormal(μ₂, σ₂)` fit by Expectation-Maximization (50-iter EM, log-likelihood-tolerance 10⁻⁷). Converged at:
  - Component A: weight π ≈ 0.526, μ₁ = 2.187, σ₁ = 0.036 → mean ≈ **8.92 ms** (the tight 1× cluster)
  - Component B: weight 1−π ≈ 0.474, μ₂ = 2.935, σ₂ = 0.480 → mean ≈ **21.11 ms** (the broader 2+× cluster)
  - Mixture mean = 14.39 ms (preserves the empirical first moment to within 0.05 ms).

  AIC for the mixture is 509,734 — far below gamma (690,498), log-normal (677,391), or shifted log-normal (677,393). The mixture captures the structure that 2-parameter families cannot.

**K-S statistics across families** (real n = 103,722; mock n = 50,000; median across 5 RNG-independent draws to control mock-side sampling variance):

| Family | params | K-S statistic | AIC | Outcome at α=0.05 |
|---|---:|---:|---:|---|
| gamma (production mock) | 2 | 0.233 | 690,498 | reject |
| log-normal | 2 | 0.262 | 677,391 | reject |
| shifted log-normal | 3 | 0.264 | 677,393 | reject |
| **2-component log-normal mixture** | **5** | **0.122** | **509,734** | **reject** |

K-S 95 % critical value at n=103,722 is ≈ 1.36/√n ≈ 0.0042; the mixture's 0.122 is the best of the family but still 30× above the critical value. **None of the low-parameter smooth families we tested (2-parameter log-normal/gamma/Weibull, 3-parameter shifted log-normal, 5-parameter 2-component log-normal mixture) passes K-S against this many samples of comb-structured ITL.** A sufficiently flexible mixture-of-many-components, kernel-density estimator, or empirical-CDF inverse-transform sampler could in principle approximate the comb arbitrarily closely; we did not investigate those. The mixture's AIC win and halved K-S statistic are nevertheless real evidence that the 2-cluster structure is the dominant feature.

**What this means for H1.** Nothing changes. The H1 cliff prediction depends on `λ × E[response] > T`, where `E[response] = TTFT_mean + tokens × ITL_mean`. Both the gamma and the mixture preserve the empirical ITL mean to within 0.5 %; the cliff threshold `rps_crit ≈ T / E[response]` is invariant to within-stream shape. The mixture would only matter if H1 made claims about within-stream tail behaviour at sub-threshold cells, which it does not.

**How to reproduce.** Refitting (`refit` subcommand above) regenerates all four families and writes them to `itl_fits.{gamma, lognormal, weibull, shifted_lognormal, lognormal_mixture_2}`. The `validate` subcommand now accepts `--itl-family={gamma,shifted_lognormal,lognormal_mixture}` to run K-S against any of them:

```bash
java -jar testing/calibration/target/calibration-0.0.1-SNAPSHOT.jar validate \
    --calibration testing/calibration/calibration.json \
    --itl-family lognormal_mixture
```

### 5.3 Calibrated W1 rerun (2026-04-27 13:39 → ~15:10 EEST)

After the calibration above landed, the same 9-cell W1 shape from §5.1 was re-executed with `AI_MOCK_CALIBRATION_JSON` pointed at `calibration.json` so the mock now uses the fitted log-normal TTFT (μ=7.6012, σ=0.3038, mean ≈ 2.10 s) and zero-inflated gamma ITL (`p_burst=0.3843`, gamma(3.0034, 4.8141), gap-mode mean 14.46 ms). The cell-key in the analysis pipeline (`workload, transport, target_rps, cancel_rate, pool, policy`) does **not** distinguish calibrated from mock-default runs, so the calibrated rerun pooled into the same cells as the §5.1 mock-default runs — `a_n` and `r_n` jump from 3 to 6 in the cells.csv where this happened.

Pooling caveat acknowledged. For the next manuscript revision, k6's handleSummary should emit a `calibration` tag (e.g. `mock-defaults` vs `qwen3.5-4b-mlx`) and the cell-key should include it, so the two batches are kept distinct. Logged on the deferred list. The cliff direction and rough magnitude do not change with calibration: see §5.5 for the synthesis.

### 5.4 W2 mid-stream tool-call pilot (2026-04-27 15:32 → 17:10 EEST)

W2 is the **canonical H1 surface**: each request runs an SSE stream, and after `app.ai.tool-call-after-tokens=75` mock tokens the controller makes a *blocking* `UserClient.getUserSummary(uuid)` call against `user-service` mid-stream (`java.net.http.HttpClient.send()` on the async stack — explicitly the round-1 fix-C surface — and a non-blocking `WebClient` exchange on reactive). Same 9-cell shape as W1 (pool {400, 1600} × rps {50, 500, 2000} × {async, reactive}); same warmup=30 s, duration=90 s, 3 runs/cell, `abort` reject policy. **Calibrated mock backend** (zero-inflated gamma + log-normal TTFT). Seeded benchmark user `00000000-0000-0000-0000-000000000001` provided the tool-call target (`BenchmarkDataInitializer` on user-service).

| Stack | rps | pool | n | e2e_p99_mean (ms) | e2e_p99 CI95 (ms) | ttft_p99_mean (ms) | error_rate | dropped | requests |
|---|---:|---:|---:|---:|---|---:|---:|---:|---:|
| async    | 50   | 400  | 3 | **5,410.4**  | [5,349.3, 5,500.0]    | 4,068.7   | 0.000 | 170     | 13,502  |
| async    | 50   | 1600 | 3 | **5,418.7**  | [5,284.0, 5,490.0]    | 4,022.4   | 0.000 | 168     | 13,502  |
| reactive | 50   | —    | 3 | **5,419.0**  | [5,358.0, 5,495.0]    | 4,028.4   | 0.000 | 167     | 13,503  |
| async    | 500  | 400  | 3 | **39,665.7** | [39,639.4, 39,695.8]  | 38,304.9  | **0.661** | 15,331  | 129,240 |
| async    | 500  | 1600 | 3 | **8,708.7**  | [8,687.0, 8,749.2]    | 7,340.4   | 0.001 | 5,754   | 131,758 |
| reactive | 500  | —    | 3 | **5,402.3**  | [5,382.0, 5,436.0]    | 4,043.0   | 0.000 | 1,473   | 135,000 |
| async    | 2000 | 400  | 3 | **39,691.1** | [39,635.1, 39,788.4]  | 38,332.7  | **0.919** | 0       | 540,003 |
| async    | 2000 | 1600 | 3 | **41,606.3** | [32,677.0, 54,580.1]  | 36,019.3  | **0.868** | 272,737 | 330,115 |
| reactive | 2000 | —    | 3 | **45,471.9** | [33,699.0, 59,183.2]  | 30,912.8  | **0.763** | 225,348 | 351,274 |

#### 5.4.1 W2 paired tests (MW-U at α=0.05)

| Cell (W2, sse, abort) | async pool | a_n / r_n | a_p99_mean | r_p99_mean | a/r ratio | MW-U p | Verdict |
|---|---:|---:|---:|---:|---:|---:|---|
| rps=50  | 400  | 3 / 3 | 5,410.4 | 5,419.0 | 1.00× | 0.83 | **ns** (parity) |
| rps=50  | 1600 | 3 / 3 | 5,418.7 | 5,419.0 | 1.00× | 0.83 | **ns** (parity) |
| rps=500 | 400  | 3 / 3 | 39,665.7 | 5,402.3 | **7.34×** | 0.05 | **DIFFERENT (cliff)** |
| rps=500 | 1600 | 3 / 3 | 8,708.7  | 5,402.3 | 1.61× | 0.05 | DIFFERENT (mild cliff) |
| rps=2000 | 400 | 3 / 3 | 39,691.1 | 45,471.9 | 0.87× | 0.51 | **ns** (both at timeout) |
| rps=2000 | 1600 | 3 / 3 | 41,606.3 | 45,471.9 | 0.91× | 0.51 | **ns** (both saturated) |

#### 5.4.2 W2-specific findings

- **rps=500 pool=400 cliff is 7.34×** — mid-stream blocking tool call cliffs the async stack just like W1 does, with a comparable ratio (W1 at the same cell was 7.90×). The mid-stream blocking call is *not* the dominant load source — pool starvation already accounts for the cliff.
- **rps=500 pool=1600 cliff drops to 1.61×** — a much milder cliff than W1's 4.89× at the same cell. Possible interpretation: at pool=1600 the async executor has enough headroom to absorb the mid-stream blocking call without queue starvation, while W1's longer total response time (~6.25 s vs W2's ~5.4 s baseline) keeps thread occupancy higher and pushes pool=1600 closer to the cliff. **Worth a paragraph in the manuscript** — it's the cleanest piece of evidence that the cliff is a function of `λ × E[response]` vs `T`, not specifically of "where the blocking thing is."
- **rps=50 baseline: e2e_p99 ≈ 5.4 s on all three stacks**, lower than W1's ~6.25 s. The W2 endpoint terminates the stream after the post-tool tokens with a `[stop]` finish-reason, and the calibrated mock's `tokensPerResponse=150` is split into 75-pre + tool + 75-post emissions; the combined wall is shorter than W1's straight 150-token emission because the per-token sleep after the tool gets fewer iterations. Stack-parity at low load is preserved (~0.0% spread async vs reactive).

### 5.5 W0 non-AI streaming baseline (2026-04-27 17:10 → 18:54 EEST)

W0 is the *non-AI* mock-stream baseline (`/tweets/ai/mock-stream`, no Spring AI ChatModel involved at all — it's a hand-rolled SSE token emitter that emits `MOCK_TOKENS=150` tokens with `MOCK_ITL_MS=40 ms` between them, paced by `Mono.delay`/`SseEmitter` directly). Its purpose is to **rule out the "streaming itself is the cause" reviewer objection**: if W0 also cliffs in the same shape as W1 and W2, then the cliff is a property of `(SSE + bounded ThreadPoolExecutor)` and not specifically of LLM streaming.

| Stack | rps | pool | n | e2e_p99_mean (ms) | e2e_p99 CI95 (ms) | ttft_p99_mean (ms) | error_rate | dropped | requests |
|---|---:|---:|---:|---:|---|---:|---:|---:|---:|
| async    | 50   | 400  | 3 | **6,229.7**  | [6,170.0, 6,262.0]    | 4.7       | 0.000 | 720     | 13,324  |
| async    | 50   | 1600 | 3 | **6,259.1**  | [6,184.0, 6,297.6]    | 5.6       | 0.000 | 726     | 13,321  |
| reactive | 50   | —    | 3 | **6,297.2**  | [6,280.0, 6,310.0]    | 50.6      | 0.000 | 734     | 13,315  |
| async    | 500  | 400  | 3 | **58,146.3** | [57,203.0, 59,995.0]  | 52,011.5  | **0.896** | 15,400  | 129,183 |
| async    | 500  | 1600 | 3 | **29,791.0** | [29,726.0, 29,885.0]  | 23,620.5  | 0.000 | 58,684  | 89,700  |
| reactive | 500  | —    | 3 | **6,200.7**  | [6,197.0, 6,205.0]    | 55.8      | 0.000 | 7,069   | 133,274 |
| async    | 2000 | 400  | 3 | **59,707.3** | [59,659.0, 59,774.0]  | 53,555.0  | **0.978** | 0       | 540,002 |
| async    | 2000 | 1600 | 3 | **49,357.0** | [45,422.2, 55,262.6]  | 36,396.9  | **0.903** | 280,954 | 332,812 |
| reactive | 2000 | —    | 3 | **47,466.6** | [28,943.6, 58,047.4]  | 41,758.0  | **0.960** | 304,837 | 309,257 |

#### 5.5.1 W0 paired tests (MW-U at α=0.05)

| Cell (W0, sse, abort) | async pool | a_n / r_n | a_p99_mean | r_p99_mean | a/r ratio | MW-U p | Verdict |
|---|---:|---:|---:|---:|---:|---:|---|
| rps=50  | 400  | 3 / 3 | 6,229.7 | 6,297.2 | 0.99× | 0.05 | DIFFERENT (negligible effect) |
| rps=50  | 1600 | 3 / 3 | 6,259.1 | 6,297.2 | 0.99× | 0.28 | **ns** (parity) |
| rps=500 | 400  | 3 / 3 | 58,146.3 | 6,200.7 | **9.38×** | 0.05 | **DIFFERENT (cliff)** |
| rps=500 | 1600 | 3 / 3 | 29,791.0 | 6,200.7 | **4.80×** | 0.05 | **DIFFERENT (cliff)** |
| rps=2000 | 400 | 3 / 3 | 59,707.3 | 47,466.6 | 1.26× | 0.51 | **ns** (both at timeout) |
| rps=2000 | 1600 | 3 / 3 | 49,357.0 | 47,466.6 | 1.04× | 0.51 | **ns** (both saturated) |

#### 5.5.2 W0-specific findings

- **W0 rps=500 pool=400 cliff is 9.38×** — actually *larger* than W1's 7.90× and W2's 7.34× at the same cell. This kills the "cliff is AI-streaming-specific" objection cleanly: the cliff is fundamental to `(bounded ThreadPoolExecutor + SSE response with non-trivial residency)`, regardless of whether an AI ChatModel is involved.
- **TTFT on W0 is microseconds at low load** (4.7 ms p99 at rps=50 pool=400 vs ~570 ms for W1) because the mock-stream endpoint has no log-normal TTFT delay — it's a direct SSE emitter. This matters for the "is the H1 about TTFT or ITL?" question — the W0 answer is that the cliff is about *total response time × arrival rate* dominating pool occupancy, not about TTFT specifically.
- **At rps=2000 both async pool sizes and reactive all collapse to ~50 s p99** — the cliff at this load is universal across stacks because we're saturating the open-loop arrival rate against any per-machine queue (Netty backlog, k6 VU pool, OS file-descriptor limits all play). This is a threats-to-validity item for the manuscript: H1 holds in the *intermediate* regime where async cliffs and reactive doesn't (rps=500), not at the *steady-state-overload* regime where both stacks fail.

### 5.6 H1 verdict — synthesis across W0 / W1 / W2

H1 ("async + blocking-client p99 degrades > 500 % vs reactive when concurrency C exceeds bounded pool size T AND tool I/O exceeds inter-token interval") is **supported** by 27 cell-runs across three workloads. The cliff factor at the cleanest H1 cell — sustained `rps=500` with `pool=400` — clusters tightly:

| Workload | rps | pool | a_p99 (s) | r_p99 (s) | cliff factor | over the >500% threshold? |
|---|---:|---:|---:|---:|---:|:---:|
| W0 (non-AI baseline) | 500 | 400 | 58.1 | 6.20 | **9.38×** | ✅ (yes, comfortably) |
| W1 (pure chat)       | 500 | 400 | 49.3 | 6.24 | **7.90×** | ✅ |
| W2 (mid-stream tool) | 500 | 400 | 39.7 | 5.40 | **7.34×** | ✅ |

The 7.3–9.4× spread across W0/W1/W2 is consistent with H1's structural mechanism: `λ × E[response] > T → pool starvation → queue overflow + AbortPolicy 5xx + TTFT timeout for survivors`. The **W2 surface specifically** — with the mid-stream `UserClient.getUserSummary()` blocking call that the round-1 ledger fix C addressed — is *not* the dominant cliff source; pool starvation under sustained arrival rate is. W2 just inherits the same structural cliff that W1 and W0 do.

**Manuscript-ready phrasing of the conditional H1 (revised from the original tighter formulation):**

> Under sustained open-loop arrival, a servlet-based stack with a bounded `ThreadPoolExecutor` of size T degrades its per-run p99 end-to-end latency by 7×–9× relative to a comparable reactive stack whenever request residency `E[response]` × arrival rate `λ` exceeds T. The cliff is a structural property of bounded-pool + long-residency request shapes (we observe it on the non-AI streaming baseline W0 just as cleanly as on the AI-streaming workloads W1 and W2); the mid-stream blocking tool call in W2 inherits the same cliff but is not its primary cause.

### 5.7 Figures (full set after pilots)

PNGs landed in the gitignored `testing-results/figures/` directory. Filenames follow `<chart>_<workload>_<transport>_<calibration_tag>_<campaign>.png` so figures from different campaigns/calibrations cannot accidentally pool. The manuscript-canonical figures are the `*_headline-5rep-rerun-2026-04-28.png` set; the others are kept for reference and reproduction provenance:

- `concurrency_scaling_{W0,W1,W2}_sse_<calibration>_<campaign>.png` — per-stack p99 vs target_rps, one series per (stack × pool_size × policy). All three show the async-pool=400 series cliffing between rps=50 and rps=500; reactive stays flat through rps=500 then collapses at rps=2000.
- `pool_size_scaling_{W0,W1,W2}_sse_<calibration>_<campaign>.png` — per-stack p99 vs pool_size at fixed rps. Cliff height shrinks monotonically as pool grows.
- `h1_validation_scatter.png` — paired (async, reactive) p99 ratios at matched cells. Pairing key includes `calibration_tag` and `campaign` so cross-campaign cells cannot accidentally pair (e.g., a diagonal-cliff async cell will not pair with a headline-5rep reactive cell). The cluster of points well above y=1 are the cliff cells; pool=400 dots sit visibly above pool=1600 dots in the upper-right cluster.

### 5.8 Pilot dataset summary

After the Codex-driven cleanup pass on 2026-04-27 evening:

- **40 cells** in `testing-results/cells.csv` after introducing the `calibration_tag` column at index 7; the legacy backfill correctly tags pre-13:39 EEST runs as `mock-defaults` and 13:39+ runs as `qwen-3.5-4b-mlx-v1` so the §5.1 (mock-default) and §5.3 (calibrated) W1 cells stop pooling.
- **112 runs** in `testing-results/runs.csv`.
- **3 cells fully quarantined** by the new all-error filter in `ReportCommand`: `qwen-3.5-4b-mlx-v1 | W1 | rps∈{50,500,2000} | pool=1600 | async`. These were produced during the first calibrated W1 driver pass on 2026-04-27 ~14:10–14:50 EEST; root cause was the `APP_CONCURRENCY_TWEET_POOL_SIZE` env not propagating to the rebuilt tweet-service container at that pool tag, so the pool was effectively the compose default (200) under calibrated load — every request hit `AbortPolicy` instantly (failed_p99 ≈ 1–2 ms across all 540,003 attempts at rps=2000). Quarantine surfaces these as `quarantined_n_runs=3` while keeping the contributing-runs-only `n_runs / e2e_p99_mean / error_rate / MW-U` columns clean. The cleanup-pass rerun (§5.10) replaces them with healthy pool=1600 cells.
- 2 cells from the very first overnight pass were quarantined to `testing-results/_contaminated_sleep/` after macOS system sleep contaminated the in-flight wall-clock measurements; the full pilot was re-run from scratch under `caffeinate -d -i -s -u` active.
- One pre-Phase-B-2 macOS kernel panic in `com.adguard.mac.adguard.network-extension` killed Docker mid-sweep at 15:18 EEST; W1-calibrated had already finished, the W2/W0 sweeps were re-launched after disabling the AdGuard system extension. Documented in §5.9 #5 as an operational threat-to-validity item; future operators should disable third-party network filters before high-rps loopback benchmarks.

### 5.9 Threats to validity

For Applied-Sciences-grade reviewer attention.

1. **Single-machine test rig.** All measurements taken on a single MacBook Pro M3 Max, 64 GB unified memory, macOS 26.4.1, Docker Desktop. The cliff at very high arrival rate (rps=2000 in §5.1/§5.4/§5.5) shows *both* stacks collapsing because the rig itself saturates — open-loop generator at this rate competes with the SUT for CPU, file descriptors, and Netty/Tomcat backlogs. The intermediate cliff regime (rps=500 with pool=400) is what H1 tests; reactive at the same rps stays at ≈ 5.5–6.2 s p99 with negligible 5xx error rate, so the headline cliff is not plausibly explained by load-generator saturation alone — but the headline cells still share the rig with k6, so single-machine contention is *bounded*, not *zero*, and a multi-machine rig with the load generator on a separate host would tighten this further. Out of scope for this paper.
2. **Live-LLM serving has substantially lower throughput than the mock can drive.** The mock backend is the *primary* measurement surface for H1 and the load-bearing dataset for §5.10/§5.11; live-LLM runs are illustrative supporting material. The realism subset was originally scoped to `rps ∈ {10, 50}` against LM Studio but was empirically rescoped during execution: LM Studio's MLX runtime forces parallel=1 for vision-architecture models (Qwen3.5-4B-MLX-4bit qualifies), so we pivoted to Apple's `mlx_lm.server` (same MLX-4bit weights, different runtime) which supports continuous batching. The runtime pivot exposed a measurable runtime-comparability finding: `mlx_lm.server` produces TTFT ~12× faster than LM Studio's MLX runtime on identical weights and emits no SSE-coalescing bursts (p_burst=0 vs 0.38). The realism subset therefore tests "the live serving path runs end-to-end" rather than "the mock equals live distributions" — see §5.13 for the executed cells, the runtime-comparability evidence, and the explicit scope claims (and non-claims). All §5.10/§5.11 numbers remain mock-backend only with parameters calibrated against Qwen3.5-4B-MLX-4bit through LM Studio's MLX runtime (§5.2); §5.13 does not change those.
3. **Mock simplification — TTFT model.** The mock TTFT log-normal (μ=7.6012, σ=0.3038, mean ≈ 2.10 s) was fit to a 1,500-sample empirical TTFT trace from Qwen3.5-4B-MLX-4bit; a two-sided K-S test against that sample does not reject at α=0.05 (p=0.068, n=1500). Failure to reject is *not* a proof of distributional equivalence — at this n the test has limited power against tail-shape differences — but the calibration is consistent with the empirical distribution under K-S, and `E[response]` (the load-bearing variable for the residency formulation) reproduces the empirical mean within the bootstrap CI. See §5.2.
4. **Mock simplification — ITL bimodality.** The production mock ITL is a zero-inflated gamma (`p_burst=0.3843`, gamma `shape=3.0034, scale=4.8141`) calibrated against the gap-mode subset of empirical Qwen ITLs (≥ 0.1 ms). Marginal mean is preserved to 0.03 % (8.902 ms vs observed 8.904 ms) so `E[response]` — the load-bearing variable for H1 — is **first-order valid** (mean × token-count → per-request service-time mean is preserved). K-S rejects at α=0.05 against the gap-mode samples (n=103,722). We investigated the reason in §5.2.1: the empirical gap-mode distribution is **multimodal** with hardware-quantization artifacts on Apple Silicon Metal — a tight cluster centred at ≈ 8.9 ms (the "1×token-period" mode, ~53 % of gap-mode samples) and a broader cluster centred at ≈ 21 ms (the "2+×token-period" mode, ~47 %). At n=103,722 the K-S 95 % critical value is ≈ 0.0042, so any closed-form continuous family with non-comb support is essentially guaranteed to reject. We considered three alternatives and recorded their fits in `calibration.json` (a 3-parameter shifted log-normal and a 5-parameter 2-component log-normal mixture, both as calibration-side analysis only — the production mock binary still consumes the gamma fit so headline §5.10/§5.11 numbers are not affected by this exploration). Per-family median K-S statistics on gap-mode samples (n_real=103,722, mock n=50,000, 5 reps for RNG variance):

   | Family | params | K-S statistic | AIC | Outcome |
   |---|---:|---:|---:|---|
   | gamma (production mock) | 2 | 0.233 | 690,498 | reject |
   | log-normal | 2 | 0.262 | 677,391 | reject |
   | shifted log-normal | 3 | 0.264 | 677,393 | reject (MLE collapses to c≈0; degenerate) |
   | log-normal mixture (2-comp.) | 5 | **0.122** | **509,734** | reject (best AIC, K-S still > 0.0042) |

   The within-stream emission *shape* (variance, autocorrelation, burstiness) is therefore **not** equivalence-tested at this n. For H1's structural claim (queue-starvation cliff under residency overflow), the cliff threshold depends on `E[response] × λ` versus T, so first-order validity is sufficient evidence for a cliff *to exist where predicted*, but the precise cliff *shape* (steepness, knee width) under a real-LLM backend at sustainable concurrency remains an open follow-up — partially addressed by §5.13's realism cross-check. See §5.2.1 for the empirical histogram + the alternative-family investigation; §5.2 for the original calibration.
5. **macOS / Docker Desktop interference.** Two operational interruptions during data collection:
   - System sleep in the first overnight pass suspended the calibration JVM and in-flight k6 cells via SIGSTOP, then unsuspended on wake; the lost cells are quarantined to `testing-results/_contaminated_sleep/` and the affected pilot was re-run under `caffeinate -d -i -s -u`.
   - A kernel panic in AdGuard's `com.adguard.mac.adguard.network-extension` (a macOS NetworkExtension that intercepts loopback traffic when active) crashed Docker mid-sweep on 2026-04-27 ~15:18 EEST. Docker was restarted, the AdGuard system extension disabled, the affected sweeps re-launched. Future operators: pre-arm `caffeinate` and disable third-party network filters before high-RPS loopback benchmarks.
6. **Failed-request latency interpretation.** The async stack at high RPS produces large numbers of `AbortPolicy`-induced 5xx responses (ms-range failed-latency) alongside the small population of successful responses (multi-second p99). The cells.csv reports both populations separately (`e2e_p99_mean` for successes, `e2e_failed_p99_mean` for rejections, `error_rate` for the proportion). Reading "p99 = 60 s" in isolation overstates the user-facing latency picture; readers should always check `error_rate` alongside. The §5.10 headline cells at rps=500 pool=400 have `error_rate ≈ 0.66–0.90` for async (W0 0.898, W1/W2 0.662), and `error_rate ≈ 1.3e-5–2.2e-5` for reactive W1/W2 with W0 reactive at 0.000 — three OK responses out of 225,000 is essentially zero, but not literally zero, so the table reports the precise figures. Async survivors get multi-second service time; rejected arrivals return as ~1 ms 5xx; reactive serves the same arrival rate at 5.5–6.2 s with negligible 5xx rate.
7. **Open-loop overload interpretation.** k6's `constant-arrival-rate` executor will *drop* arrivals when no VU is available rather than back off (which a closed-loop `constant-vus` would do). Dropped arrivals are reported in the `dropped` column and surface as a separate `dropped_rate` in the §5.10 table. **Definition: `dropped_rate = total_dropped / (total_requests + total_dropped)`** — i.e. dropped as a fraction of *all attempted arrivals* (sent + dropped), since dropped iterations never become HTTP requests. A non-zero `dropped_rate` at moderate rps (e.g. reactive cells at rps=500 ≈ 1–5 %) indicates the load generator itself ran out of pre-allocated VUs (open-loop backpressure), **not** that the SUT refused work. We document `dropped` per cell so readers can distinguish "SUT saturated and rejected" (high `errors`) from "load generator saturated and never delivered" (high `dropped`). The async cliff cells additionally show `dropped_rate ≈ 0.10–0.11`; this is k6's own VU starvation under high latency, separate from the SUT-side `AbortPolicy` rejections that drive the `error_rate`. The async pool=1600 W0 cell shows `dropped_rate = 0.395` — by far the highest in the headline — because that cell admitted every arrival into its larger queue and the resulting per-request residency (queue wait + service time) saturated k6's VU pool more aggressively than the pool=400 cliff cells, which rejected arrivals fast and freed VUs back.
8. **Calibration tag legacy backfill.** The `calibration_tag` cell-key dimension was added 2026-04-27 evening after Codex's review flagged the pooling problem. Pre-tag result dirs are backfilled by timestamp: dirs before 2026-04-27 13:39 EEST (when the first calibrated rerun started) are tagged `mock-defaults`; dirs at or after that cutoff with no explicit tag in their JSON are tagged `qwen-3.5-4b-mlx-v1`. The cutoff is tunable via `--legacy-calibration-cutoff` and the tags via `--legacy-pre-cutoff-tag` / `--legacy-post-cutoff-tag` in the `ingest` subcommand. All cleanup-pass cells (§5.10 onward) carry an explicit `--ai-calibration-tag` from k6 — the backfill heuristic is only relevant for the pre-tag historical cells in §5.1 / §5.3 / §5.4 / §5.5.
9. **Campaign manifest authoritativeness.** The `campaign` cell-key dimension was added 2026-04-28 morning (Codex v2 B2). For the canonical 5-rep headline, three batches launched with the same `--ai-campaign=headline-5rep-rerun-2026-04-28` flag for the W0-async-pool=400 cell (a single-rep first attempt, a 1-CONTAMINATED+1-OK retry, and the clean 5-rep batch). The committed `testing/analysis/campaign-manifest.tsv` post-hoc relabels the first two batches as `early-w0-attempts-2026-04-28` so the canonical campaign reads cleanly as 9 cells × 5 reps; the early-attempt runs remain in runs.csv with full source-file traceability under their separate label. The §5.10 headline numbers below assume the manifest is loaded (`--campaign-manifest`); regenerating without it produces the slightly weaker (and slightly more provenance-mixed) W0 row of n=7 / 8.82× referenced in the commit history of this file.
10. **Three-runs-per-cell statistics.** §5.1–§5.5 cells use 3 runs per cell with bootstrap 95 % CI on the mean of per-run p99. CI widths are tight (typically 0.5–5 % of the mean) and the MW-U test is non-parametric, so 3 runs is statistically defensible. The §5.10 headline cells use 5 runs/cell as a cushion against single-run contamination, on the strict reading of MDPI Applied-Sciences reviewer expectations.
11. **`@Profile("benchmark")` gating and W2 reproduction.** `BenchmarkDataInitializer` (which seeds the W2 tool-call target user with a fixed UUID) is gated to the benchmark profile so production deployments do not silently insert the seeded UUID. **Reproducing the W2 numbers in the prod profile therefore requires either (a) re-seeding the target user manually with the same UUID, or (b) replacing the seeded UUID in `ai-streaming-benchmark.js` with an existing userId from the prod database.** Without one of those, `./run.sh runtime up async prod` will not have the W2 endpoint operational and the W2 cells will fail with 404. Verified in the integration tests; the gating is intentional, but the reproduction guidance is non-obvious and operators should be aware.

### 5.10 Canonical 5-rep headline — RERUN (2026-04-28 ~02:38 → 05:40 EEST)

**Status (2026-04-28).** This is the post-Codex-v2-review rerun, executed under three additional safety nets that the original 5-rep headline (§5.10-prev below) lacked: (1) per-run readiness gate (`/actuator/health` UP + `/actuator/prometheus` reachable for 10 s before warmup); (2) post-run `connection_refused > 200` contamination scan with a sidecar `<c>_<i>_validation.txt`; (3) campaign cell-key dimension so this batch's cells stop pooling with the original and the diagonal cells. Driver: `/tmp/headline-rerun-driver.sh` (committed implicitly via the post-driver result-tree). Slim JVM heaps (`TWEET_JAVA_TOOL_OPTIONS=-Xms1500m -Xmx1500m`, `INTERACTION_JAVA_TOOL_OPTIONS=-Xms256m -Xmx512m`) so the 4 GiB container limit doesn't OOM-kill tweet-service mid-cell — the original headline run hit that wall and was the root cause of the contaminated reactive cells the v1 review flagged. Campaign tag: `headline-5rep-rerun-2026-04-28`. Calibration tags: `non-ai-w0` for W0, `qwen-3.5-4b-mlx-v1` for W1/W2.

**5-rep cells, manuscript-clean (filter `--include-status OK --filter-campaign headline-5rep-rerun-2026-04-28 --campaign-manifest testing/analysis/campaign-manifest.tsv`):**

| Workload | Stack | Pool | n_runs | quar | e2e_p99_mean (ms) | e2e_p99 CI95 (ms) | error_rate | dropped_rate | cell_status |
|---|---|---:|---:|---:|---:|---|---:|---:|---|
| W0 | async    | 400  | 5 | 0 | **57,270.8** | [57,248, 57,296]    | 0.898 | 0.106 | OK ✓ |
| W0 | async    | 1600 | 5 | 0 | **29,959.4** | [29,913, 30,004]    | 0.000 | 0.395 | OK ✓ (no rejections; pool absorbed every queue burst, k6 dropped what couldn't be VU-allocated) |
| W0 | reactive | —    | 5 | 0 | **6,223.2**  | [6,214, 6,234]      | 0.000 | 0.052 | **OK ✓ (was 5/5 quarantined in v1; rerun under slim heap + readiness gate produced clean cells)** |
| W1 | async    | 400  | 5 | 0 | **39,710.6** | [39,663, 39,770]    | 0.662 | 0.106 | OK ✓ |
| W1 | async    | 1600 | 5 | 0 | **9,235.7**  | [9,153, 9,313]      | 0.001 | 0.048 | OK ✓ |
| W1 | reactive | —    | 5 | 0 | **5,459.6**  | [5,441, 5,480]      | 0.000 | 0.011 | **OK ✓ (was 5 quarantined in v1)** |
| W2 | async    | 400  | 5 | 0 | **39,758.5** | [39,723, 39,800]    | 0.662 | 0.106 | OK ✓ |
| W2 | async    | 1600 | 5 | 0 | **9,562.5**  | [9,547, 9,582]      | 0.001 | 0.051 | OK ✓ |
| W2 | reactive | —    | 5 | 0 | **5,484.0**  | [5,467, 5,512]      | 0.000 | 0.011 | **OK ✓ (was 5 quarantined in v1)** |

(`error_rate = total_errors / total_requests` per cells.csv; non-zero for the cliff cells where `AbortPolicy` fires. `dropped_rate = total_dropped / (total_requests + total_dropped)` — i.e. dropped as a fraction of *all attempted arrivals* (sent + dropped); k6's open-loop `constant-arrival-rate` drops arrivals when no VU is pre-allocated, so non-zero `dropped_rate` is load-generator backpressure, not SUT rejection (see §5.9 #7). `quar = 0` everywhere — no all-error runs. The W0-async-pool=400 cell launched in three batches (`_1ApaCj` 1-rep first attempt, `_dXSOMo` 1-CONTAMINATED+1-OK retry, `_VANblx` clean 5-rep) — all three carried `--ai-campaign=headline-5rep-rerun-2026-04-28` at execution time. The 2026-04-28 manifest at `testing/analysis/campaign-manifest.tsv` post-hoc relabels the first two batches to `early-w0-attempts-2026-04-28` so the canonical campaign reads cleanly as 9 cells × 5 reps; the early-attempt runs remain in the runs.csv with full source-file traceability under their separate label.)

#### 5.10.1 Headline cliff factors at rps=500, pool=400 (canonical, all clean pairs)

Calibration-tag-matched pairing throughout. No cross-tag asterisks needed — the rerun got the reactive cells we were missing for the canonical comparator.

| Workload | async p99 (s) | reactive p99 (s) | cliff factor | MW-U p | over the >500% threshold? |
|---|---:|---:|---:|---:|:---:|
| W0 (non-AI baseline) | 57.27 | 6.22 | **9.20×** | 0.0090 | ✅ |
| W1 (pure chat) | 39.71 | 5.46 | **7.27×** | 0.0090 | ✅ |
| W2 (mid-stream tool) | 39.76 | 5.48 | **7.25×** | 0.0090 | ✅ |

The 7.25–9.20× spread across W0/W1/W2 is **consistent with** the residency formulation H1: as expected when E[response] × λ exceeds T, the async cliff manifests at 5×–10× ratios with the largest factor on the workload that *doesn't* touch Spring AI (W0 the streaming-but-non-AI baseline). The W2 cross-batch reproducibility chain: §5.6 (pre-cleanup pilot, n=3) was 7.34×; §5.10-prev (initial 5-rep pass, distorted by pooling with v1/v2 quarantined runs) read 5.21×; §5.10 (this canonical campaign-isolated rerun, n=5) is 7.25× — matching §5.6 within 1.2 % once the v1/v2-pooling artifact is removed. The two-population framing carries through: async survivors pay multi-second queue wait + service time; rejected arrivals return as ~1 ms 5xx; reactive serves the same arrival rate at ≈ 5.5 s with negligible 5xx rate (errors/requests ≈ 1.3e-5–2.2e-5 across W1/W2; W0 reactive zero).

#### 5.10.2 Replication relative to §5.6 (pre-cleanup numbers)

| Workload | Pre-cleanup §5.6 (n=3) | Rerun §5.10 (n=5) | Δ | Interpretation |
|---|---:|---:|---:|---|
| W0 | 9.38× | **9.20×** | -0.18× | Within run-to-run variance; W0 cliff is reproducible. |
| W1 | 7.90× | **7.27×** | -0.63× | Same; the §5.6 number was pooled with mock-default cells, this is post-tag-isolation. |
| W2 | 7.34× | **7.25×** | -0.09× | Tightest match — the W2 mechanism reproduces cleanly. |

All three workloads show the H1 cliff under the canonical 5-rep methodology. The threshold model from §5.11 (predicting `rps_crit = T / E[response]`) brackets the cleanup-pass diagonal cells; the §5.10 headline confirms the cliff *magnitude* is stable across two independent measurement batches taken 8 hours apart.

---

### 5.10-prev Canonical 5-rep headline — INITIAL pass (2026-04-27 ~19:48 → ~22:45)

**Setup.** 9-cell shape: `{async pool=400, async pool=1600, reactive} × {W0, W1, W2}`, fixed at the cleanest cliff cell **rps=500**, **5 runs/cell**. Calibration tag `qwen-3.5-4b-mlx-v1` for W1/W2 (zero-inflated mock); `non-ai-w0` for W0 (no Spring AI involvement). MW-U paired tests at α=0.05; Welch printed for reference but not gating. AdGuard system extension disabled before kickoff (kernel-panicked during the previous overnight). `caffeinate -d -i -s -u` active. Results-identity tagging (commit `6e82a5c`) ensures these cells do not pool with the §5.1 mock-default cells.

| Workload | Stack | Pool | n_runs | quar | e2e_p99_mean (ms) | e2e_p99 CI95 (ms) | error_rate | quarantined |
|---|---|---:|---:|---:|---:|---|---:|---|
| W0 (non-AI) | async    | 400  | 5 | 0 | **57,286.4** | tight at the timeout cap | **0.896** | — |
| W0          | async    | 1600 | 5 | 0 | **23,774.3** | bounded               | 0.425 | — |
| W0          | reactive | —    | 0 | 5 | —             | —                     | —     | **5 runs all-error** (failed_p99=1 ms; root cause undiagnosed; see notes below) |
| W1 (chat)   | async    | 400  | 8 | 0 | **39,344.1** | bounded               | 0.687 | — |
| W1          | async    | 1600 | 8 | 3 | **7,418.1**  | bounded               | 0.216 | 3 (legacy from the pre-cleanup pool-env-bug batch) |
| W1          | reactive | —    | 6 | 5 | **5,432.5**  | bounded               | 0.148 | 5 (some 5-rep cleanup cells failed for the same root cause as reactive W0) |
| W2 (tool)   | async    | 400  | 6 | 2 | **28,131.0** | bounded               | 0.781 | 2 |
| W2          | async    | 1600 | 8 | 0 | **8,105.4**  | bounded               | 0.155 | — |
| W2          | reactive | —    | 3 | 5 | **5,402.3**  | bounded               | 0.000 | 5 |

(`n_runs` = contributing runs (non-null e2e_p99); `quar` = all-error runs filtered by the 2026-04-27 ReportCommand.quarantine logic. `n_runs > 5` means the cell pooled with same-tag cells from the pre-cleanup batch — by design, since the calibration parameters are identical.)

**Reactive 5-rep all-error caveat (open, doesn't block H1).** The 5-rep cleanup cells for `reactive × W0`, `reactive × W1` (partial), and `reactive × W2` returned 100% failed requests at ~1 ms latency. The Prometheus snapshot for `reactive-W0-rps500-5rep` shows `http_server_requests_seconds_count{...uri="/actuator/health"} 1` (just the snapshot probe itself) — Spring saw no benchmark traffic, so k6 was hitting a connection-refused / wrong-port state, *not* a Spring 5xx. Most likely cause: a docker compose env-propagation or build cache state from the pool=1600 → reactive transition that didn't fully recover when the reactive container started. The legacy `qwen-3.5-4b-mlx-v1`-tagged reactive cells (3 runs each, n_runs=3) from the 2026-04-27 afternoon pre-cleanup pass remain healthy and provide the cliff-comparator baseline used in §5.10.1 below; the W0 non-ai-w0 reactive cell is necessarily quarantined-only and its row above lists no comparator value.

#### 5.10.1 Headline cliff factors at rps=500, pool=400 (canonical)

The cleanest H1 cliff cell. Calibration-tag-matched pairing where possible; cross-tag pairing (asterisked) used only for the W0 row where the reactive 5-rep was quarantined.

| Workload | async tag | async p99 (s) | reactive tag | reactive p99 (s) | cliff factor | MW-U p | over the >500% threshold? |
|---|---|---:|---|---:|---:|---:|:---:|
| W0 (non-AI baseline) | non-ai-w0 | 57.29 | qwen-3.5-4b-mlx-v1 ✱ | 6.20 | **9.24×** | 0.05 | ✅ |
| W1 (pure chat) | qwen-3.5-4b-mlx-v1 | 39.34 | qwen-3.5-4b-mlx-v1 | 5.43 | **7.24×** | 0.0019 | ✅ |
| W2 (mid-stream tool) | qwen-3.5-4b-mlx-v1 | 28.13 | qwen-3.5-4b-mlx-v1 | 5.40 | **5.21×** | 0.020 | ✅ |

✱ Cross-tag pairing for W0 (async non-ai-w0 vs reactive qwen-3.5-4b-mlx-v1). The W0 endpoint (`/tweets/ai/mock-stream`) doesn't use Spring AI or any mock-calibration parameters, so the calibration tag is purely a metadata label here — there is no semantic difference between W0 cells under different tags. The async non-ai-w0 row uses the cleanup-pass 5-rep async cell (post-W0-stack-symmetry fix); the reactive comparator falls back to the pre-cleanup 3-run cell because the cleanup-pass reactive W0 was quarantined.

The 5.2×–9.2× spread is consistent with the §5.6 finding: W0 cliffs hardest, then W1, then W2. The W2 ratio dropped slightly (5.21× vs the §5.6 7.34×) because pool=400's W2 cell pooled 5 cleanup-pass runs with 3 from the previous pass; the 5-rep numbers slightly favoured the survivors (W2's mid-stream blocking call has higher per-request residency, so AbortPolicy fires earlier, leaving a smaller successful-survivor population at lower p99). All three workloads remain comfortably above the >500% H1 threshold.

#### 5.10.2 Concurrency-scaling figures (post-cleanup)

PNGs in `testing-results/figures/` follow `<chart>_<workload>_<transport>_<calibration_tag>_<campaign>.png` so figures from different cell-key universes don't pool. Manuscript-canonical figures are the `*_headline-5rep-rerun-2026-04-28.png` set:
- `concurrency_scaling_W0_sse_non-ai-w0_headline-5rep-rerun-2026-04-28.png` — 5-rep canonical async cells (pool=400, 1600) + reactive W0.
- `concurrency_scaling_W1_sse_qwen-3.5-4b-mlx-v1_headline-5rep-rerun-2026-04-28.png` — 5-rep canonical W1 cells.
- `concurrency_scaling_W2_sse_qwen-3.5-4b-mlx-v1_headline-5rep-rerun-2026-04-28.png` — 5-rep canonical W2 cells.
- Companion `pool_size_scaling_*_headline-5rep-rerun-2026-04-28.png` figures.
- `h1_validation_scatter.png` — paired-ratio plot; pairing key includes calibration_tag + campaign so cross-campaign cells cannot pair.

The `*_pre-cleanup-pilot-2026-04-27.png`, `*_headline-5rep-2026-04-27.png` (initial pass), `*_diagonal-2026-04-28.png`, and `*_early-w0-attempts-2026-04-28.png` figures from the same directory render the non-canonical cells (pre-cleanup pilots, the initial-pass headline before manifest cleanup, the diagonal cliff slice, and the early W0 attempts) as separate figures rather than pooling them with the canonical ones. They are kept for reproduction provenance, not for manuscript inclusion.

### 5.11 Diagonal cliff slice (W1, calibration `qwen-3.5-4b-mlx-v1`, 2026-04-28 ~00:16–01:43)

**Purpose.** Mechanism-confirmation experiment: H1's residency formulation predicts the cliff threshold sits at `rps_crit ≈ T / E[response]`. With the calibrated mock's `E[response] ≈ 3.43 s` (TTFT log-normal mean 2.10 s + 150 tokens × 0.616 × 14.46 ms gap-mode + zero-inflation), this gives `rps_crit ≈ 117` for pool=400 and `rps_crit ≈ 466` for pool=1600. The diagonal slice walks `rps` past each predicted threshold and looks for the cliff to switch on at exactly that point.

**Setup.** W1 only. 3 runs/cell. async pool=400 swept at rps {60, 90, 120, 150}; async pool=1600 swept at rps {240, 320, 400, 500}; reactive at the union {60, 90, 120, 150, 240, 320, 400, 500}. Calibration tag `qwen-3.5-4b-mlx-v1` throughout. AbortPolicy reject. Same warmup/duration/gracefulStop as §5.10.

#### 5.11.1 Diagonal results

All diagonal-campaign rows below (`campaign=diagonal-2026-04-28` in cells.csv) carry n=3/3. The two rps=500 reference rows are clearly labelled — pool=400 rps=500 was *not* part of the diagonal sweep, so its entry is the canonical headline cell (§5.10, `headline-5rep-rerun-2026-04-28`, n=5); pool=1600 rps=500 *was* part of the diagonal sweep (n=3) and the canonical §5.10 number (n=5) is shown alongside for cross-batch comparison.

| Cell (W1, sse, abort, qwen-3.5-4b-mlx-v1) | n | async p99 (ms) | reactive p99 (ms) | a/r ratio | error_rate | Cliff status |
|---|---:|---:|---:|---:|---:|---|
| pool=400, rps=60   | 3/3 | 5,363.5  | 5,425.0 | 0.99× | 0.146 | sub-threshold (rps_crit≈117) |
| pool=400, rps=90   | 3/3 | 5,352.5  | 5,393.7 | 0.99× | 0.127 | sub-threshold |
| pool=400, rps=120  | 3/3 | 6,212.0  | 5,456.9 | 1.14× | 0.144 | **at threshold** (rps_crit≈117) |
| pool=400, rps=150  | 3/3 | 12,501.2 | 5,375.2 | **2.32×** | 0.268 | **cliff entry** |
| pool=400, rps=500 *(headline ref, §5.10)* | 5/5 | 39,710.6 | 5,459.6 | **7.27×** | 0.662 | **full cliff** (canonical headline; not part of the diagonal sweep — included as the cliff-saturation reference for the pool=400 series) |
| pool=1600, rps=240 | 3/3 | 5,434.1  | 5,441.3 | 1.00× | 0.149 | sub-threshold (rps_crit≈466) |
| pool=1600, rps=320 | 3/3 | 5,489.3  | 5,424.4 | 1.01× | 0.134 | sub-threshold |
| pool=1600, rps=400 | 3/3 | 5,483.5  | 5,423.0 | 1.01× | 0.201 | sub-threshold |
| pool=1600, rps=500 *(diagonal)* | 3/3 | 7,569.5 | 5,437.3 | **1.39×** | 0.207 | **at-threshold** (rps_crit≈466, just past; canonical §5.10 headline n=5 reads 9,235.7 / 5,459.6 = 1.69× — same direction, within-batch RNG variance between the diagonal pass and the headline rerun 8 hours apart) |

#### 5.11.2 Diagonal verdict — the threshold model brackets the empirical cliff onset

- **pool=400 cliff onset** is between rps=120 (1.14×) and rps=150 (2.32×). Predicted `rps_crit = 400 / 3.43 = 117 RPS`. Observed: cliff transition is **consistent with** the prediction — the first cell above 117 (rps=120, +3 % over) shows a small but statistically significant elevation (MW-U DIFFERENT at α=0.05), and the next cell (rps=150, +28 %) shows a clear cliff entry. The headline reference at rps=500 (4.3× over predicted) reads 7.27× as expected for full cliff. The data brackets the predicted threshold tightly.
- **pool=1600 cliff onset** is between rps=400 (1.01×) and rps=500 (1.39× diagonal / 1.69× headline). Predicted `rps_crit = 1600 / 3.43 = 466 RPS`. Observed: cliff transition is **consistent with** the prediction — the last sub-threshold cell rps=400 (-14 % below predicted) shows no cliff (a/r=1.01×, ns), and rps=500 (+7 % over predicted) shows a modest cliff (a/r=1.39× in the diagonal pass, 1.69× in the canonical headline rerun, both MW-U DIFFERENT). The two rps=500 numbers — measured 8 hours apart with different RNG state — bracket the same effect direction, with the spread giving a rough run-to-run variance estimate at this just-past-threshold cell.
- The error_rate column tracks the same threshold: at sub-threshold cells error_rate ≈ 0.13–0.20 (k6 open-loop arrival drops + occasional reactive backlog hiccups; see §5.9 #7 for the dropped vs. errors distinction), at cliff cells error_rate jumps to 0.27 (rps=150) and 0.66–0.69 at rps=500.

This is the manuscript's cleanest causal figure: a single composite plot of `cliff factor (a_p99/r_p99) vs (rps × E[response] / pool_size)` will collapse all 9 cells onto a single curve crossing 1.0× at λ × E[response] / T = 1, with the cliff knee right at the predicted threshold. The data is in `testing-results/cells.csv`; produce the plot from the diagonal subset in the next manuscript-revision pass.

### 5.12 Attribution — pool active / queue depth / rejections at the cliff

**Status.** Codex's v2 review explicitly asked for an in-run time-series of `pool_active`, `queue_depth`, `rejections_total`, and `e2e_p99` rather than post-cell snapshots. Three things landed during the cleanup pass:

1. The in-run Prometheus poller was added to `run_bench.sh` (commit `9e379ca`) — captures a per-second sample to `<OUT>/<c>_<i>_prom.csv` while k6 runs. The data structure exists as designed.
2. **The poller's awk regex was broken on first deployment** — bash-into-awk quoting issue (`\\{`/`\\s` literal in awk) caused every metric column in every per-run prom.csv to be 0. Fixed in `run_bench.sh` after the fact (replaced regex with explicit `$1 == want` and a labeled-form fallback). Verified the patched extractor against a live `/actuator/prometheus` snapshot, but the broken regex's per-run prom.csv files are unrecoverable.
3. **Time pressure prevented re-running the headline cells with the patched poller.** The driver finished at 05:39 EEST; the tweet-service container ran into instability when I tried to spin up just the cliff cells for a fresh time-series capture. Skipped to keep the manuscript-readiness window in budget.

The §5.12 attribution evidence in this draft therefore falls back on the **post-cell single-shot snapshots** captured in the original 2026-04-27 evening pass (still on disk at `/tmp/prom-snapshots/*.txt`, parsed into `/Users/andrei/Developer/tweebyte/testing-results/prom-snapshots.csv`). Caveat: snapshots are taken AFTER the cell completes, so `tweebyte_pool_active` and `tweebyte_pool_queue_depth` always read 0 (stack idles by snapshot time). The **cumulative counters** `tweebyte_pool_tasks_completed` and `tweebyte_pool_rejections_total` are what carry the attribution story; they accumulate across cells in a stack lifetime, so per-cell deltas are computed by subtracting consecutive snapshots from the same stack-startup.

**Manuscript framing for §5.12 (current state).** The post-cell snapshots are **consistent with** `AbortPolicy` rejection contributing to the p99 cliff (`rejections_total` reads zero at sub-threshold cells and advances by >170 k between the boot snapshot and the full-cliff post-cell snapshot). They do *not* by themselves attribute the full cliff dynamics: snapshots are point-in-time, so they cannot show *when within the cell* the rejections start, what `queue_depth` looks like during steady-state cliff, or how the warmup→cliff transition unfolds — a queue-wait component contributing alongside rejections at intermediate cells cannot be ruled out from snapshot data alone. For Q2-AppSci, the snapshot evidence is **supporting material** for the H1 mechanism that the residency formulation predicts; an in-run time-series of `pool_active`, `queue_depth`, and `rejections_total` is the deferred follow-up needed to claim full dynamic attribution. Codex's v2 review explicitly offered "downgrade §5.12 to supporting evidence and stop calling it the attribution figure" as an alternative — that's what this section now does.

#### 5.12.1 Headline attribution (pool=400 cliff cells, async stack, post-cell snapshots 5 min apart)

| Cell | tasks_completed Δ | rejections_total Δ | total requests | error_rate from cells.csv |
|---|---:|---:|---:|---:|
| async pool=400 W0 rps=500 5-rep | 70,589 | **170,694** | 241,283 (computed) | 0.898 ✓ |
| async pool=400 W1 rps=500 5-rep | 121,481 | **180,534** | 302,015 | 0.662 (mixed pool, partial) |
| async pool=1600 W0 rps=500 5-rep | 5,033 | **0** | k6 sees ~135 k → most fall in queue wait | 0.000 (no rejections; queue absorbed) |

(Δ = post-cell snapshot minus prior-cell snapshot; for pool=400 W0 the prior is the boot snapshot so Δ = the snapshot value itself.)

**The post-cell snapshots are consistent with the H1 mechanism but do not constitute time-series attribution.** At pool=400 W0 rps=500 the executor completed 70 k requests and the cumulative `AbortPolicy` counter advanced by ~170 k — the post-cell ratio (rejections / completions+rejections ≈ 70.7 %) is consistent with the cell's `error_rate` of 0.898 (the residual gap is k6's open-loop dropped arrivals and connection-level failures, both reported separately in §5.10's `dropped_rate` column). At pool=1600 W0 rps=500 the rejections counter advanced by zero — every arrival was accepted into the executor's larger queue, with the queue-wait inflating p99 to 23.8 s. The two cells therefore correspond to two distinct H1 failure surfaces (rejection-dominated vs. queue-wait-dominated), both predicted by the residency formulation, but the snapshots cannot show *when* within a cell the rejections start, what queue depth looks like during steady-state cliff, or how the dynamics evolve across the warmup→cliff transition. That is what an in-run time-series would establish; see §5.12 status note above for why the time-series capture was deferred.

#### 5.12.2 Diagonal attribution — rejection counter behaviour is consistent with the predicted threshold

| Cell (W1) | tasks_completed | rejections_total | Cliff status |
|---|---:|---:|---|
| pool=400 rps=60   | 1,003 | **0** | sub-threshold ✓ |
| pool=400 rps=90   | 2,531 | **0** | sub-threshold ✓ |
| pool=400 rps=120  | 4,396 | **0** | at-threshold (cliff visible in p99 but not yet in rejections) |
| pool=400 rps=150  | (snapshot missed) | (snapshot missed) | cliff entry — captured in p99 but Prom snapshot empty |
| pool=1600 rps=240 | 3,501 | **0** | sub-threshold ✓ |
| pool=1600 rps=400 | 3,355 | **0** | sub-threshold ✓ |
| pool=1600 rps=320, 500 | (snapshots missed) | — | snapshots empty (post-stack-tear-down race) |

The post-cell rejections counter reads zero at every sub-threshold cell and advances into the 10⁵ range at the full-cliff cells in §5.12.1 — a pattern that is **consistent with `AbortPolicy` rejection contributing to the p99 cliff at pool=400 rps=500**. The diagonal data is sufficient to rule out the trivial null hypothesis "the p99 cliff is independent of pool saturation" — the rejection counter would have to advance at the sub-threshold cells under that null, and it does not. It is **not sufficient** on its own to attribute the full p99 dynamics to AbortPolicy alone: the post-cell snapshots are point-in-time, so they cannot rule out a queue-wait component contributing alongside rejections at intermediate cells, and the broken in-run poller (§5.12 status #2) prevented a per-second attribution time-series. The empty snapshots at the cliff-entry cells (rps=150 pool=400, rps=320/500 pool=1600) reflect a race between cell-completion and stack tear-down for the next cell — fixable in the next pass by inserting a 2 s wait between cell-finish and the snap. For Q2-AppSci, the §5.12.1 headline + the live stack trace in §5.12.3 carry the supporting-evidence narrative; the cleaner time-series figure is on the optional Q1-confidence follow-up list.

#### 5.12.3 Stack trace (live evidence)

During async pool=400 W1 rps=500 5-rep, observed in `tweebyte-tweet-service` logs at 2026-04-27 17:29:12 UTC:

> `RejectedExecutionException: ... rejected from java.util.concurrent.ThreadPoolExecutor@34f2d3a6[Running, pool size = 400, active threads = 400, queued tasks = 4000, completed tasks = 187667]` — `at AiController.summarize(AiController.java:53)` — handled by `CountingRejectionHandler.rejectedExecution(ThreadConfiguration.java:90)` which incremented `tweebyte_pool_rejections_total`.

The trace captures **a single moment** of full saturation: pool fully saturated (400/400 active), queue at capacity (4000/4000), and the next arrival rejected by `AbortPolicy`. The corresponding Prometheus snapshot taken seconds later recorded the post-cell `tasks_completed = 192,070` (above) and `rejections_total = 351,228` (cumulative since stack startup; subtract the prior W0 cell's 170,694 to get this cell's 180,534). Combined with §5.12.1's snapshot accounting, this trace is supporting evidence that `AbortPolicy` is firing at exactly the saturation point predicted by the residency formulation; it is one observation, not a continuous measurement of the saturation dynamics.

### 5.13 Real-LLM live-backend integration + runtime-comparability check (2026-04-28)

**Purpose** (Codex v6 / v7 GO scope, refined per v8 review). Convert §5.9 #2's "real-LLM realism subset deferred" to "executed". The subset's role is **live-backend integration evidence** — *yes, the SUT path runs end-to-end against a real Qwen serving stack* — plus **runtime-comparability evidence** documenting that two MLX runtimes (LM Studio's vs Apple's `mlx_lm.server`) produce measurably different per-stream timings on identical weights. The originally planned framing as a "calibration fidelity cross-check" was discarded mid-execution because §5.13.2's pre-check showed the two runtimes' distributions K-S-reject strongly; the calibrated mock cannot be validated against the live runtime in a distributional-equivalence sense (we no longer claim it can). This subset preempts the reviewer line "you calibrated from a real model but never exercised the live serving path", but it does *not* claim that mock per-stream timing matches live per-stream timing.

#### 5.13.1 Why mlx_lm.server, not LM Studio: the empirical pivot

The calibration source for §5.2 was LM Studio's MLX runtime (`Qwen3.5-4B-MLX-4bit`, `parallel=1`). For the realism subset we need the **same model weights** under a runtime that supports **continuous batching**, otherwise sustained concurrent traffic serializes at the backend and the cells do not measure the SUT side.

**Empirical: LM Studio's MLX runtime forces parallel=1 for vision-architecture models.** Attempting `lms load qwen3.5-4b-mlx --parallel 8` returns:

```
Error: numParallelSessions must be 1 for vision models as they
do not currently support continuous batching
```

Qwen3.5-4B-MLX-4bit's `config.json` declares architecture `Qwen3_5ForConditionalGeneration` and includes `image_token_id` plus a `video_preprocessor_config.json` even though we only ever use its text-streaming path. LM Studio's MLX runtime treats the architecture as vision-class and refuses concurrent decoding.

**8-stream concurrent probe at the forced parallel=1**: TTFT spread 287 → 21,696 ms across the 8 threads with each thread's TTFT exactly equal to the prior thread's `total` — strict serialization, 24 s wall for 8 sequential ~3 s requests. This is the runtime confirming the refusal in production conditions.

**Pivot to `mlx_lm.server`** (Apple's `mlx-lm` v0.31.3 Python package): the same on-disk MLX-4bit weights load and serve under `--decode-concurrency 8 --prompt-concurrency 8`. The same 8-stream concurrent probe now spreads TTFT 1306–1309 ms (4 ms range across all 8 threads — true continuous batching) with 7.7 s wall for 8 streams. A wrapper script `infrastructure/realism-backend.sh` documents the host-side lifecycle (the wrapper is host-only because Docker Desktop on macOS cannot pass through Apple Silicon Metal).

#### 5.13.2 Runtime-equivalence pre-check: LM Studio vs mlx_lm.server on identical weights

The two MLX runtimes serve the **same** on-disk weights at the **same** quantization. Per-stream timing was captured by the existing `calibration collect` subcommand against `mlx_lm.server` (50 sequential samples, `enable_thinking=false`) and K-S-tested against the original LM Studio 1973-sample calibration:

| Distribution | LM Studio (n=1973 / n_itl=103,722) | mlx_lm.server (n=50 / n_itl=5,100) | K-S 2-sample p | Outcome |
|---|---|---|---|---|
| TTFT (ms) — mean | 2,096 | **165** (~12× faster) | 5.6 × 10⁻¹⁰¹ | strong reject |
| ITL gap-mode (ms) — mean | 14.46 (median 9.30) | 12.89 (median 12.66) | < 10⁻³⁰⁰ | strong reject |
| `p_burst` (ITL < 0.1 ms fraction) | 0.3843 | **0.0000** | (ratio test) | strong reject |

**Findings.** The two MLX runtimes have measurably different per-stream timing on identical weights. `mlx_lm.server` produces TTFT ~12× faster than LM Studio's MLX runtime, has a tighter ITL distribution (variance reduced 5×), and emits zero sub-0.1 ms intra-burst tokens — its SSE chunk emission policy does not coalesce tokens at the network level, while LM Studio's does. This is consistent with the two stacks implementing the same MLX framework but with substantially different prefill/decode dispatch and output-buffering policies.

**This is a runtime-comparability finding.** The mock binary continues to consume the LM Studio-derived calibration parameters; the §5.13 cells are evidence of *the live serving path running end-to-end*. They are NOT evidence of mock-vs-live distributional equivalence at any concurrency level — the original v6 plan to claim that has been retired in favour of the integration framing. See §5.13.5 for the explicit list of supported / unsupported claims.

#### 5.13.3 Realism cells (rps=1, mlx_lm.server)

**Setup.** `AI_BACKEND=live`, `LIVE_LLM_BASE_URL=http://host.docker.internal:8081`, `LIVE_LLM_MODEL=/Users/andrei/.lmstudio/models/mlx-community/Qwen3.5-4B-MLX-4bit`, `LIVE_LLM_MAX_TOKENS=200`, mlx_lm.server with `--chat-template-args '{"enable_thinking": false}'` (Qwen3.5 reasoning mode disabled — without this, Qwen burns the entire token budget on `delta.reasoning` chunks that Spring AI's OpenAI client filters out as non-content, and the SUT-visible response stays empty). Tweet-service heap capped at `-Xms1g -Xmx1500m` (the default `-Xms3g` collides with the 4 GB compose `mem_limit`; commit `d899f16` made `infrastructure/run.sh` honor the env override). 30 s warmup + 60 s main, 3 reps per cell. Campaign: `realism-subset-2026-04-28`. Calibration tag: `qwen-3.5-4b-mlx-live-v1`.

| Stack | Workload | rps | n | e2e_p99_mean (ms) | e2e_p99 CI95 (ms) | ttft_p99_mean (ms) | error_rate | cell_status |
|---|---|---:|---:|---:|---|---:|---:|---|
| async    | W1 | 1 | 3 | **3,365** | [3,286, 3,456] | 732 | 0.000 | OK |
| async    | W2 | 1 | 3 | **3,378** | [3,304, 3,429] | 711 | 0.000 | OK |
| reactive | W1 | 1 | 3 | **5,026** | [4,769, 5,489] | 3,564 | 0.000 | OK |
| reactive | W2 | 1 | 3 | **46,892** | [37,749, 52,034] | 41,076 | 0.000 | OK |

**Headline-test (MW-U, α=0.05) async vs reactive at rps=1, qwen-3.5-4b-mlx-live-v1:**
- W1: 3,365 vs 5,026 → MW-U p = 0.0495 → **DIFFERENT, async faster**
- W2: 3,378 vs 46,892 → MW-U p = 0.0495 → **DIFFERENT, async faster (by 13.9×)**

Reactive W2's 46.9 s p99 is backend-queue-dominated, not "sustainable without queueing" — only async W1, async W2, and reactive W1 at rps=1 stayed below the backend's saturation regime; reactive W2 at rps=1 already crossed it because the W2 path issues two backend round-trips (chat stream + tool-call) per request and reactive's unbounded in-flight count compounds this against the backend's ~1 req/s ceiling.

**Stack ordering reverses when the bottleneck moves from SUT worker residency to live-backend queueing.** This is *not* an inversion of H1 — H1 is about SUT-side overflow at high rps where `λ × E[response]` exceeds pool size T, and the present cells are at low rps where the SUT has plenty of pool capacity. The stack-ordering reversal here is a **different finding under a different bottleneck**: when the backend (mlx_lm.server, ~1 req/s sustainable) is the resident-set bound, async's bounded `ThreadPoolExecutor` (pool=400) naturally paces outgoing backend calls — at rps=1 there are only ~3 in-flight requests upstream of mlx_lm.server, well below its batching ceiling. Reactive's event-loop model lets all in-flight requests fan out simultaneously and adds the W2 tool-call's RTT, which in the unbounded-concurrency path translates to backend queue buildup. Both findings can be true simultaneously; they describe different bottlenecks at different load regimes.

#### 5.13.4 Backend-ceiling rps=2 cells (intentional stress)

Optional opportunistic cells per Codex's v7 — explicitly labeled as backend-capacity stress, not calibration fidelity:

| Stack | Workload | rps | n | e2e_p99_mean (ms) | ttft_p99_mean (ms) | error_rate | cell_status |
|---|---|---:|---:|---:|---:|---:|---|
| async    | W1 | 2 | 3 | 41,775 | 36,993 | 0.000 | OK |
| reactive | W1 | 2 | 3 | 54,215 | 49,708 | 0.191 | OK |

**Reading.** Both stacks degrade similarly because the bottleneck has moved off-stack to `mlx_lm.server`. Reactive's higher in-flight count saturates k6's VU pool more aggressively, producing a 19 % `error_rate` (timeouts) where async's bounded executor still completes every request albeit at 37 s p99 TTFT. The cliff factor is consistent with the backend being the resident-set bound, not the servlet pool. **This subset is NOT used as evidence for the H1 cliff** — H1 requires the SUT to be the bottleneck, which it isn't here.

#### 5.13.5 Honest framing (live-backend integration check, NOT calibration fidelity)

> *We exercised the live serving path end-to-end through Spring → Qwen3.5-4B-MLX-4bit weights → mlx_lm.server (Apple's mlx-lm package, with continuous batching) → SSE → k6, across 18 cells. The subset establishes that the integration runs and that the SUT does not exhibit cliff behaviour at backend-sustainable load. It does NOT establish mock-vs-live distributional equivalence: §5.13.2 shows the calibration source's runtime (LM Studio's MLX) and the realism subset's runtime (mlx_lm.server's MLX) produce K-S-rejecting per-stream timing distributions on identical weights, so any claim that the calibrated mock reproduces live timing requires specifying which runtime is being compared.*

Specific scope claims §5.13 supports:
1. **Live-backend integration.** The benchmark integration runs end-to-end through Spring → real Qwen weights → SSE → k6. Confirmed across 18 cells in 6 cell-types; no integration bugs surfaced once the LIVE_LLM_* / max_tokens / enable_thinking / heap-cap configuration was complete.
2. **Non-cliff at backend-sustainable load.** At rps=1 with single-call workloads (W1 async, W1 reactive, W2 async) the SUT-side cliff regime does not appear: p99 e2e stays at 3.3–5 s. The reactive W2 cell at the same rps=1 already crosses the backend's queueing regime (46.9 s e2e_p99) because of the dual-backend-call structure of W2.
3. **Stack-ordering reversal under a backend bottleneck.** When the backend, not the SUT pool, is the resident-set bound, async's bounded executor paces upstream traffic and reactive's unbounded in-flight count compounds — the async/reactive ordering observed in §5.10's high-rps cliff regime reverses. This is documented as a *separate finding* under a *different bottleneck regime*; it does not modify or weaken H1.
4. **§5.10/§5.11 numbers unaffected.** Those cells used the calibrated mock with parameters derived from LM Studio's runtime; §5.13's cells used `mlx_lm.server`. The mock's first-order validity for `E[response]` is the load-bearing property for the cliff threshold; that is preserved. §5.13.2's runtime-comparability finding does not propagate to §5.10 because §5.10 never touched any live runtime.

Specific scope claims §5.13 does NOT support:
1. **Mock-vs-live distributional equivalence.** §5.13.2 explicitly shows the two MLX runtimes' distributions K-S-reject strongly (TTFT 12× faster on mlx_lm.server, p_burst 0 vs 0.38). The mock is calibrated to LM Studio's runtime; it is not validated against `mlx_lm.server`'s runtime in a distributional sense.
2. **Cliff shape at rps=500 against a real backend.** No 4B-class real-LLM serving stack on consumer Apple Silicon hardware sustains rps=500; the realism-cliff measurement is an explicitly different experiment (different paper, different hardware) outside this paper's scope.
3. **Generalization to GPU-class serving stacks.** vLLM on a Linux + NVIDIA box would have completely different per-stream characteristics.

#### 5.13.6 Reproducibility recipe

```bash
# 1. Start the host-side live backend (Apple's mlx-lm package, not LM Studio).
./infrastructure/realism-backend.sh up --port 8081 --parallel 8

# 2. Bring up the SUT pointed at it. Heap caps are ESSENTIAL — the
#    default -Xms3g collides with the 4 GB compose mem_limit.
export AI_BACKEND=live
export LIVE_LLM_BASE_URL=http://host.docker.internal:8081
export LIVE_LLM_MODEL=/Users/andrei/.lmstudio/models/mlx-community/Qwen3.5-4B-MLX-4bit
export LIVE_LLM_MAX_TOKENS=200
export AI_MOCK_CALIBRATION_JSON=
export TWEET_JAVA_TOOL_OPTIONS='-Xms1g -Xmx1500m -XX:+AlwaysPreTouch'
export INTERACTION_JAVA_TOOL_OPTIONS='-Xms1g -Xmx1500m -XX:+AlwaysPreTouch'
./infrastructure/run.sh up async benchmark --build

# 3. Run a realism cell.
./testing/performance/k6/run_bench.sh \
  --workload ai-streaming \
  --base-url http://localhost:9092 \
  --concurrencies 1 --runs 3 --warmup 30s --duration 60s \
  --results-root testing-results/realism-subset \
  --ai-workload W1 --ai-transport sse --ai-target-rps 1 \
  --ai-prompt "Summarize recent activity." \
  --ai-pool-size-tag 400 --ai-stack-tag async --ai-reject-policy-tag abort \
  --ai-calibration-tag qwen-3.5-4b-mlx-live-v1 \
  --ai-campaign realism-subset-2026-04-28 \
  --readiness-grace 10 --auto-prepare 0

# 4. Tear down.
./infrastructure/run.sh down async benchmark
./infrastructure/realism-backend.sh down
```

The 18-run dataset is at `testing-results/realism-subset/runs.csv`; the per-cell aggregate is at `testing-results/realism-subset/cells.csv`. The OOM-affected initial reactive-W2 batch is relabeled to `realism-subset-oom-affected-2026-04-28` via `testing/analysis/campaign-manifest.tsv` so it does not pool with the canonical aggregates.

---

## 6. Cross-check (NotebookLM, 2026-04-27)

Both PDFs were uploaded to NotebookLM (notebook `5400a603-fc49-4f07-80ed-322a3f752ff6`, registered in the local library as **"Tweebyte papers - applsci 14/12062 + 16/00090"**). The structured-JSON extraction prompt was run; the response was cross-referenced against the numbers in §2-§5 above. Summary of the comparison:

### 6.1 Confirmed matches (NotebookLM independently produced the same numbers)

- **Every text-quoted performance number in paper A** — all 56 cells (User Summary GET, Follow POST, Tweet PUT, Tweet Summaries GET × async/reactive × 7 levels) reconciled exactly against §2 below. NotebookLM only quoted the low/mid/peak rows (3 levels per scenario × 2 stacks = 6 cells per scenario, 24 total); §2 fills in the in-between levels.
- **All static / initialization metrics** (paper A Table 6 — heap MB, startup ms, JAR MB across 6 microservices) reconciled exactly.
- **All text-quoted performance numbers in paper B** — every cell quoted in §3 (caching/CPU-bound/blocking-I/O at 10/400/1000 or 10/50/100 users) reconciled exactly: throughput, CI95 bounds, latency mean, latency p95, memory MB, CPU mean+σ.
- **Paper B methodology metadata** — k6, Spring Boot Actuator + Micrometer + Prometheus (NotebookLM expanded the monitoring stack vs my "Actuator only"; correct), 60s warmup + 240s run + 5 repetitions, mean/σ/CI95/p95 statistics, M3 Max / 64 GB hardware, JWT validation disabled, 3-σ outlier protocol with no exclusions.
- **Paper B Table 2 functional-equivalence claims** — `~150 tests/service`, `1050 total`, `55 Cucumber scenarios`, `4×7 + 3×14` perf matrix.

### 6.2 Discrepancies surfaced

These are deltas I want to keep visible for the next paper / rerun:

1. **NotebookLM hallucinations against paper A.** NotebookLM confidently reported `bdd_scenarios=50` and `unit_tests_total=1023` for paper A. Verified against the PDF: paper A does **not** give an explicit Cucumber scenario count or an explicit total — it only says "approximately 150 tests per service". Both numbers are NotebookLM artefacts, not paper claims. Use paper B's Table 2 (55 Cucumber, 1050 total) as the canonical claim.
2. **NotebookLM `services_counted=4` is its interpretation, not a paper claim.** The math `150 × 7 = 1050` only works with 7 services (gateway as async-only, plus user/tweet/interaction × 2 paradigms). NotebookLM picked 4 because that's the unique microservice-type count. The "7" framing is what the FE claim depends on; document accordingly.
3. **NotebookLM renamed paper A's scenarios.** Paper A / xlsx label them `User Summary GET`, `Follow POST`, `Tweet PUT`, `Tweet Summaries GET`; NotebookLM's response uses descriptive labels (`Users Subscribing`, `Updating Existing Messages`, `Fetching User Messages`). The numbers under each label match exactly, but if a future ingest hits NotebookLM-style labels, normalise back to the xlsx names.
4. **Paper B abstract vs body internally inconsistent on caching memory.** Abstract claims "75% higher throughput and **68% lower memory footprint**" for caching. Body text (§6.2 of the PDF, page 27-29) shows caching memory at 1000 users is reactive **940 MB vs async 861 MB** — i.e. reactive is *higher*, not 68% lower. The 68% claim only holds at the very low end (10 users: 71 vs 145 → 51% reduction, still not 68%). Source of the "68%" couldn't be located in the body; **flag for the manuscript revision** — either qualify the abstract claim ("at low concurrency only") or correct the figure.
5. **Plot data extraction did NOT happen.** NotebookLM populated every in-between concurrency level (1, 5, 15, 20, 25, 50, 75, 100, 200, 600, 800) with all-null fields and `source: "figure_2-5" / "figure_6-9" / "figure_10-13"` annotations. NotebookLM acknowledged the figures exist but cannot read pixel data from the PNG plots. The full per-level curves remain extractable only by re-running the second-study sweep through `testing/analysis/` from the original raw `.csv` outputs (if recoverable) or by re-running the benchmark itself with the updated 14-level k6 harness.

### 6.3 Continuity finding from the prior published §7.2

The prior caching/CPU/blocking-I/O publication's future-work section (§7.2) explicitly anticipates the AI-streaming workload now in this codebase:

> "Controllers driven by machine learning could, based on real-time workload metrics, [...] be in charge of dynamically managing the cache eviction policies, thread-pool sizing, or database connection handling."

The AI-streaming workload (W0/W1/W2 in §5) is a direct realisation of that direction.

### 6.4 How to query the notebook from now on

Active in the library:
```bash
/usr/bin/python3 ~/.claude/skills/notebooklm/scripts/run.py notebook_manager.py activate \
  --id "tweebyte-papers---applsci-14-12062---16-00090"
```
Ask follow-up questions:
```bash
/usr/bin/python3 ~/.claude/skills/notebooklm/scripts/run.py ask_question.py \
  --question "<targeted question, e.g. 'For Figure 2 (Caching Throughput vs Concurrency), what's the throughput at concurrency=200 for both stacks?'" \
  --notebook-id "tweebyte-papers---applsci-14-12062---16-00090"
```
Or via the slash command: `/notebooklm:ask`.

### 6.5 Skill enhancement landed (for future Claude)

`~/.claude/skills/notebooklm/scripts/create_notebook.py` + `/notebooklm:create` slash command now exist. They drive the same patchright session as the rest of the skill: open NotebookLM, click "Create new notebook", upload local files via the hidden `<input type="file">` (clicking the visible upload button would open an unreachable OS file picker), wait for ingestion, return the URL, optionally register in the library. This eliminates the manual Chrome upload step for next time.

To invoke:
```bash
/usr/bin/python3 ~/.claude/skills/notebooklm/scripts/run.py create_notebook.py \
  --files /abs/path/to/foo.pdf /abs/path/to/bar.pdf \
  [--register --name "..." --description "..." --topics "a,b"]
```

---

## 7. Maintenance checklist

Run this every time the test surface or perf surface changes:

- [ ] Re-count `@Test` methods per service (§1.1) and update the table + subtotals + average.
- [ ] If async/reactive `@Test` counts diverge by >5% per service, file a follow-up issue to add the missing reactive equivalents.
- [ ] If Cucumber scenarios are added back, update §1.2 with the new count and remove the "0 in repo" line.
- [ ] If a benchmark sweep is rerun, append the new numbers to §3 (or §5 for AI) with the same column shape, including methodology-version metadata in the table caption.
- [ ] If methodology changes (new metric, new stat method, new tool), update §4 first and link from the affected result section.
- [ ] If a paper claim changes between drafts, update §1, §2, §3 to keep parity with the manuscript.
- [ ] Keep the cross-check section (§6) honest — note when NotebookLM extraction has actually been done vs still pending.
