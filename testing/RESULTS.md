# Tweebyte Testing — Results and Methodology

This file is the source of truth for current functional-equivalence status, benchmark configuration, recorded measurements, and maintenance rules. It contains only the retained result set; older comparison tables and execution notes are intentionally omitted.

## 1. Functional Equivalence

The current FE sign-off rests on three repo-local checks:

| Evidence | Current state |
|---|---|
| Unit tests | 2424 `@Test` methods: 1210 async / 1214 reactive. Unit-only line + branch coverage clears 0.90 on all 8 modules, and every service pair is within 5 % count parity. |
| Cucumber | 400 scenarios under `testing/functional-equivalence/src/test/resources/features/`; the same `.feature` files run on both stacks and passed on async and reactive on 2026-07-05. |
| Cucumber coverage | `coverage-gate.sh cucumber` clears 0.90 line + branch on all 8 modules using the FE black-box boundary denominator. |
| Runtime path isolation | FE uses the `functional-equivalence` compose profile; `prod` and `benchmark` paths do not load the JaCoCo overlay. |

### 1.1 Unit tests — current state

Counts are exact `@Test`-annotated method counts in the service modules. Each row is one Maven module. JaCoCo line + branch are unit-only (`./testing/functional-equivalence/coverage-gate.sh unit`). `@ParameterizedTest` is not included in this count, matching the maintenance grep below.

| Stack | Service | @Test count | parity gap | line | branch | unit gate | count parity |
|---|---|---:|---:|---:|---:|---|---|
| async | gateway-service | **60** | 0.0 % | 0.9806 | 0.9535 | PASS | PASS |
| async | user-service | **300** | 1.3 % | 0.9456 | 0.9579 | PASS | PASS |
| async | tweet-service | **400** | 0.0 % | 0.9839 | 0.9403 | PASS | PASS |
| async | interaction-service | **450** | 0.0 % | 0.9892 | 0.9626 | PASS | PASS |
| async | **subtotal** | **1210** | — | | | | |
| reactive | gateway-service | **60** | 0.0 % | 0.9608 | 0.9355 | PASS | PASS |
| reactive | user-service | **304** | 1.3 % | 0.9401 | 0.9068 | PASS | PASS |
| reactive | tweet-service | **400** | 0.0 % | 0.9817 | 0.9414 | PASS | PASS |
| reactive | interaction-service | **450** | 0.0 % | 0.9763 | 0.9246 | PASS | PASS |
| reactive | **subtotal** | **1214** | — | | | | |
| **Total** | | **2424** | — | | | | |

All 8 modules clear the unit-only line + branch ≥ 0.90 coverage gate. The per-service `@Test` count parity gate now clears on every service.

**FE acceptance criteria:**
1. **Per-service unit-only line + branch ≥ 0.90** on every module (`./testing/functional-equivalence/coverage-gate.sh unit`).
2. **Per-service `@Test`-count parity** between async and reactive: `|async − reactive| / max ≤ 5 %` on every service.
3. **Cucumber FE proof:** the same `.feature` files in `testing/functional-equivalence/src/test/resources/features/` execute on both stacks (`mvn -f testing/functional-equivalence/pom.xml verify -Pasync` and `... -Preactive`) and pass identically.
4. **Cucumber coverage gate:** `./testing/functional-equivalence/coverage-gate.sh cucumber` passes at ≥ 0.90 line + branch per service module on both stacks.

**FE status:** behavior, unit coverage, Cucumber coverage, and test-count parity are green as of 2026-07-05: both Cucumber stack runs passed 400/400 scenarios, all 8 modules clear both coverage gates, and every service pair is within the 5 % `@Test` count parity threshold.

**Maintenance rule (do this every PR that touches test files):**
1. Re-run the count: `for s in backend/async/{gateway,user,tweet,interaction}-service backend/reactive/{gateway,user,tweet,interaction}-service; do printf '%-40s %5d\n' "$s" "$(grep -rEoh '@Test\b' "$s/src/test" 2>/dev/null | wc -l)"; done`
2. Re-run the gate: `for s in backend/async/{gateway,user,tweet,interaction}-service backend/reactive/{gateway,user,tweet,interaction}-service; do (cd "$s" && mvn -B -DskipITs verify >/dev/null); done && ./testing/functional-equivalence/coverage-gate.sh unit`
3. Update the table above + the per-stack subtotals + the total.
4. If async/reactive deviate by more than 5 % on any single service, OR any module drops below 0.90 line or 0.90 branch, the change must restore both before merge — that's the acceptance shape above.

### 1.2 Behavior-Driven tests — Cucumber

**400 scenarios** in `testing/functional-equivalence/src/test/resources/features/`. **Same `.feature` files run on both stacks** - no `@async-only` / `@reactive-only` tags. Cucumber 7.18.1 + JUnit Platform Suite + cucumber-java; the `functional-equivalence` compose profile (separate from `prod` and `benchmark`) layers a JaCoCo agent into each service JVM so end-to-end coverage can be aggregated. Run via `mvn -f testing/functional-equivalence/pom.xml verify -Pasync` and `... -Preactive` (the `mvn -pl …` form does not work from the repo root because there is no parent reactor POM).

Scenario distribution:

| Area | Scenarios | Files |
|---|---:|---|
| user-service: signup, login, profile, search, auth-errors, validation, media, conflicts, edges | 113 | `features/user_service/*.feature` |
| tweet-service: CRUD, search, validation, AI streaming, hashtags, media, edges | 106 | `features/tweet_service/*.feature` |
| interaction-service: likes, retweets, replies, follows, recommendations, edges | 130 | `features/interaction_service/*.feature` |
| gateway: routing, JWT enforcement | 35 | `features/gateway/*.feature` |
| cross-service end-to-end | 16 | `features/cross_service/*.feature` |
| **total** | **400** | many `.feature` files |

Source-of-truth count (must restrict to source resources to avoid double-counting against `target/test-classes/`):
```
find testing/functional-equivalence/src/test/resources/features -name '*.feature' \
  | xargs grep -c '^\s*Scenario' | awk -F: '{s+=$2} END {print s}'
```

**FE design properties enforced by the suite:**
- Async/reactive parity across URL/config injection, Spring/R2DBC setup semantics, security validation, exception handling, Cacheable SpEL, JSR310 serialisation, Reactor non-blocking discipline, update/delete/like authorisation symmetry, GlobalExceptionHandler presence on both stacks, scheduled-cleanup symmetry, async create-tweet residency shape (`thenComposeAsync + allOf` so the mention/hashtag handlers join the response future).
- Coverage exclusions (DTO/entity) are configured per pom.xml; service modules carry `≥ 0.90` line + branch via unit tests alone.
- Cucumber-mode coverage uses the FE black-box boundary denominator and is enforced at ≥ 0.90 line + branch. Fresh measurements on 2026-07-05: async gateway 0.9706/0.9194, async user 1.0000/1.0000, async tweet 1.0000/1.0000, async interaction 1.0000/1.0000; reactive gateway 0.9913/0.9091, reactive user 1.0000/1.0000, reactive tweet 1.0000/1.0000, reactive interaction 1.0000/1.0000.

The FE suite lives under `testing/functional-equivalence/`. 400 scenarios pass on both stacks. The suite covers auth flows, tweet CRUD, follow/like/retweet/reply chains, the Redis-backed caching surface, the CPU-bound media surface, gateway routing/JWT, cross-service end-to-end chains, AND the AI streaming surface (W0/W1/W2). Cucumber connects to gateway-service over HTTP via the `functional-equivalence` compose profile, so the same `.feature` files exercise both stacks and equivalence is observable per scenario, not just through test-count parity.

Forward maintenance:
1. New scenarios go under `testing/functional-equivalence/src/test/resources/features/<service>/`. Step phrasing follows the existing `Auth/User/Tweet/Interaction Steps.java` catalogue — extend if an idiom isn't represented yet.
2. Any FE divergence (a scenario passes on one stack but not the other) must be fixed in production code; intentional behavioural differences must be documented in the commit message with severity, repro, and rationale.
3. Re-run the cucumber-mode coverage gate (`./testing/functional-equivalence/coverage-gate.sh cucumber <stack>`) when the suite expands; keep line + branch ≥ 0.90 on every service module.

### 1.3 AI streaming tests

Both stacks ship 10 unit tests per stack covering the AI surface:
- `MockStreamingChatModelTest` (4 tests): token-count, indexed-token order, call-concatenation, accessor parity.
- `MockCalibrationTest` (6 tests): blank/null path → defaults, missing file → defaults, incomplete JSON → defaults, parse-error → defaults, valid JSON → fitted params.

Counts are included in the per-service table in §1.1.

**Maintenance rule:** if you add an AI endpoint or a new mock parameter, write the unit test in **both** stacks symmetrically. The 10/10 split is intentional and maintained as an explicit parity invariant.

---

## 2. Benchmark methodology

All recorded service-workload measurements use the Spring `benchmark` profile and the `native-local` topology: PostgreSQL, Redis, Vault, application JVMs, and the load generator run on the host. JMeter and k6 call the backend services directly on `localhost:9091`, `localhost:9092`, or `localhost:9093`; the gateway is outside the measured path.

The service workloads use a closed-loop 14-level concurrency grid:

`1 5 10 15 20 25 50 75 100 200 400 600 800 1000`

Each cell has two repetitions, a 60-second warmup, and a 120-second measured interval. JMeter drives the CRUD and database-heavy workloads. k6 `constant-vus` drives the cache, CPU, blocking-I/O, and HTTP fan-out workloads. RPS is calculated from requests completed during the measured interval. Resource figures are process CPU and JVM heap-used averages sampled through Actuator.

AI streaming uses a separate open-loop arrival-rate design documented in §4.

---

## 3. Service workload results

All ten service-workload tables use the same schema and follow the AI-grid ordering where the metrics overlap: load, stack, repetitions, RPS, latency, errors, CPU, and heap.

### 3.1 User summary

Workload: `throughput-user-summary`, direct `GET /users/{id}/summary` on user-service.

| Conc | Stack | n | RPS | Avg ms | p95 ms | Errors | CPU avg % | Heap used avg MB |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | async | 2 | 78.31 | 12.702 | 14.000 | 0.00% | 0.74 | 92.09 |
| 1 | reactive | 2 | 144.61 | 6.876 | 8.000 | 0.00% | 1.17 | 72.56 |
| 5 | async | 2 | 389.76 | 12.775 | 15.000 | 0.00% | 2.82 | 100.95 |
| 5 | reactive | 2 | 708.33 | 7.018 | 9.000 | 0.00% | 4.34 | 73.31 |
| 10 | async | 2 | 766.53 | 12.999 | 15.000 | 0.00% | 3.93 | 214.73 |
| 10 | reactive | 2 | 1,516.54 | 6.555 | 9.000 | 0.00% | 7.03 | 74.27 |
| 15 | async | 2 | 1,175.20 | 12.716 | 15.000 | 0.00% | 4.91 | 216.08 |
| 15 | reactive | 2 | 2,341.47 | 6.365 | 8.000 | 0.00% | 10.00 | 78.74 |
| 20 | async | 2 | 1,681.62 | 11.849 | 15.000 | 0.00% | 6.49 | 215.00 |
| 20 | reactive | 2 | 3,207.68 | 6.197 | 8.000 | 0.00% | 12.32 | 79.08 |
| 25 | async | 2 | 2,257.20 | 11.040 | 14.000 | 0.00% | 8.20 | 215.77 |
| 25 | reactive | 2 | 4,097.47 | 6.072 | 8.000 | 0.00% | 13.62 | 82.20 |
| 50 | async | 2 | 4,908.45 | 10.167 | 12.000 | 0.00% | 10.21 | 223.68 |
| 50 | reactive | 2 | 8,955.40 | 5.566 | 8.000 | 0.00% | 17.05 | 219.79 |
| 75 | async | 2 | 8,176.62 | 9.158 | 10.000 | 0.00% | 14.48 | 231.21 |
| 75 | reactive | 2 | 16,013.66 | 4.668 | 5.000 | 0.00% | 29.63 | 221.75 |
| 100 | async | 2 | 11,063.31 | 9.022 | 10.000 | 0.00% | 20.79 | 237.27 |
| 100 | reactive | 2 | 21,756.78 | 4.582 | 5.000 | 0.00% | 40.03 | 234.99 |
| 200 | async | 2 | 20,823.69 | 9.587 | 11.000 | 0.00% | 40.26 | 255.39 |
| 200 | reactive | 2 | 33,871.72 | 5.892 | 8.000 | 0.00% | 65.08 | 252.10 |
| 400 | async | 2 | 23,817.86 | 16.773 | 19.000 | 0.00% | 43.55 | 319.23 |
| 400 | reactive | 2 | 35,312.43 | 11.308 | 31.000 | 0.00% | 64.00 | 285.82 |
| 600 | async | 2 | 23,735.64 | 25.248 | 28.000 | 0.00% | 43.81 | 329.88 |
| 600 | reactive | 2 | 36,680.71 | 16.330 | 55.500 | 0.00% | 63.45 | 318.30 |
| 800 | async | 2 | 23,627.97 | 33.823 | 37.000 | 0.00% | 43.89 | 362.57 |
| 800 | reactive | 2 | 36,905.90 | 21.648 | 56.500 | 0.00% | 62.89 | 329.88 |
| 1000 | async | 2 | 23,445.94 | 42.609 | 47.000 | 0.00% | 44.00 | 388.00 |
| 1000 | reactive | 2 | 36,586.41 | 27.298 | 53.500 | 0.00% | 62.50 | 368.80 |

Headline at conc=1000: reactive RPS is **1.56x** async (36,586.41 / 23,445.94), and reactive heap-used average is **0.95x** async (368.80 MB / 388.00 MB).

### 3.2 User-profile fan-out

Workload: `dbread-fanout-user-profile`, direct `GET /users/{id}` on user-service, including downstream tweet and interaction data.

| Conc | Stack | n | RPS | Avg ms | p95 ms | Errors | CPU avg % | Heap used avg MB |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | async | 2 | 19.63 | 50.653 | 56.000 | 0.00% | 0.55 | 86.42 |
| 1 | reactive | 2 | 19.39 | 51.278 | 56.500 | 0.00% | 0.54 | 72.44 |
| 5 | async | 2 | 101.85 | 48.933 | 53.500 | 0.00% | 2.34 | 141.81 |
| 5 | reactive | 2 | 100.73 | 49.499 | 54.500 | 0.00% | 1.85 | 71.74 |
| 10 | async | 2 | 214.29 | 46.562 | 50.000 | 0.00% | 3.60 | 194.05 |
| 10 | reactive | 2 | 203.75 | 48.983 | 54.000 | 0.00% | 3.21 | 70.84 |
| 15 | async | 2 | 340.06 | 44.032 | 48.000 | 0.00% | 3.96 | 241.09 |
| 15 | reactive | 2 | 317.05 | 47.224 | 51.500 | 0.00% | 3.58 | 74.45 |
| 20 | async | 2 | 469.59 | 42.523 | 46.000 | 0.00% | 4.09 | 244.92 |
| 20 | reactive | 2 | 438.83 | 45.502 | 49.000 | 0.00% | 3.84 | 73.47 |
| 25 | async | 2 | 605.84 | 41.216 | 43.000 | 0.00% | 4.58 | 244.76 |
| 25 | reactive | 2 | 567.72 | 43.969 | 48.000 | 0.00% | 3.84 | 74.59 |
| 50 | async | 2 | 1,293.66 | 38.603 | 41.000 | 0.00% | 8.02 | 250.01 |
| 50 | reactive | 2 | 1,210.68 | 41.253 | 44.000 | 0.00% | 5.77 | 80.32 |
| 75 | async | 2 | 1,951.61 | 38.382 | 41.000 | 0.00% | 11.13 | 257.86 |
| 75 | reactive | 2 | 1,944.88 | 38.522 | 41.000 | 0.00% | 8.23 | 218.25 |
| 100 | async | 2 | 2,381.41 | 41.941 | 46.000 | 0.00% | 15.42 | 267.65 |
| 100 | reactive | 2 | 2,633.47 | 37.927 | 41.000 | 0.00% | 11.48 | 217.12 |
| 200 | async | 2 | 2,952.41 | 67.663 | 80.000 | 0.00% | 17.33 | 280.98 |
| 200 | reactive | 2 | 3,722.59 | 53.663 | 63.000 | 0.00% | 18.17 | 239.74 |
| 400 | async | 2 | 3,048.51 | 131.082 | 147.500 | 0.00% | 17.87 | 307.74 |
| 400 | reactive | 2 | 4,030.72 | 99.129 | 129.000 | 0.00% | 18.27 | 270.69 |
| 600 | async | 2 | 3,058.44 | 195.966 | 215.500 | 0.00% | 17.27 | 353.29 |
| 600 | reactive | 2 | 4,050.39 | 147.916 | 181.500 | 0.00% | 18.20 | 290.07 |
| 800 | async | 2 | 3,028.39 | 263.878 | 285.000 | 0.00% | 17.42 | 365.24 |
| 800 | reactive | 2 | 4,020.80 | 198.707 | 233.000 | 0.00% | 18.18 | 315.45 |
| 1000 | async | 2 | 3,004.35 | 332.502 | 356.000 | 0.00% | 17.48 | 359.12 |
| 1000 | reactive | 2 | 4,004.89 | 249.303 | 284.500 | 0.00% | 18.09 | 346.18 |

Headline at conc=1000: reactive RPS is **1.33x** async (4,004.89 / 3,004.35), and reactive heap-used average is **0.96x** async (346.18 MB / 359.12 MB).

### 3.3 Follow creation

Workload: `dbwrite-light-follow-create`, direct `POST /follows/{targetId}/{id}` on interaction-service.

| Conc | Stack | n | RPS | Avg ms | p95 ms | Errors | CPU avg % | Heap used avg MB |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | async | 2 | 22.88 | 43.137 | 47.000 | 0.00% | 0.57 | 114.81 |
| 1 | reactive | 2 | 32.59 | 30.249 | 34.000 | 0.00% | 0.65 | 80.81 |
| 5 | async | 2 | 117.03 | 42.359 | 48.000 | 0.00% | 1.98 | 114.56 |
| 5 | reactive | 2 | 164.31 | 30.165 | 35.000 | 0.00% | 2.12 | 80.52 |
| 10 | async | 2 | 251.04 | 39.564 | 48.000 | 0.00% | 3.12 | 136.75 |
| 10 | reactive | 2 | 345.55 | 28.779 | 35.500 | 0.00% | 3.07 | 81.28 |
| 15 | async | 2 | 416.49 | 35.795 | 42.000 | 0.00% | 4.43 | 137.12 |
| 15 | reactive | 2 | 530.11 | 28.094 | 34.500 | 0.00% | 4.00 | 82.77 |
| 20 | async | 2 | 558.00 | 35.635 | 40.000 | 0.00% | 6.23 | 140.07 |
| 20 | reactive | 2 | 725.04 | 27.392 | 33.000 | 0.00% | 5.05 | 81.70 |
| 25 | async | 2 | 731.69 | 33.982 | 38.000 | 0.00% | 6.92 | 141.37 |
| 25 | reactive | 2 | 916.98 | 27.083 | 32.000 | 0.00% | 5.80 | 84.59 |
| 50 | async | 2 | 1,579.59 | 31.495 | 35.000 | 0.00% | 9.51 | 149.26 |
| 50 | reactive | 2 | 1,985.83 | 25.045 | 28.000 | 0.00% | 7.23 | 89.06 |
| 75 | async | 2 | 2,495.31 | 29.909 | 32.000 | 0.00% | 14.41 | 284.85 |
| 75 | reactive | 2 | 3,044.69 | 24.519 | 26.000 | 0.00% | 7.80 | 118.65 |
| 100 | async | 2 | 3,336.87 | 29.820 | 31.500 | 0.00% | 18.83 | 295.03 |
| 100 | reactive | 2 | 4,242.34 | 23.459 | 25.000 | 0.00% | 10.66 | 149.93 |
| 200 | async | 2 | 5,586.74 | 35.627 | 39.500 | 0.00% | 34.02 | 309.88 |
| 200 | reactive | 2 | 9,015.00 | 22.062 | 24.000 | 0.00% | 29.45 | 271.26 |
| 400 | async | 2 | 6,551.10 | 60.784 | 73.500 | 0.00% | 41.80 | 365.01 |
| 400 | reactive | 2 | 11,455.75 | 34.744 | 44.500 | 0.00% | 34.16 | 311.28 |
| 600 | async | 2 | 7,096.14 | 84.155 | 100.000 | 0.00% | 39.86 | 390.30 |
| 600 | reactive | 2 | 11,957.45 | 49.901 | 67.000 | 0.00% | 33.75 | 373.53 |
| 800 | async | 2 | 7,023.82 | 113.386 | 130.000 | 0.00% | 39.83 | 407.91 |
| 800 | reactive | 2 | 11,997.85 | 66.255 | 92.500 | 0.00% | 33.55 | 398.19 |
| 1000 | async | 2 | 7,025.13 | 141.723 | 160.500 | 0.00% | 40.08 | 506.63 |
| 1000 | reactive | 2 | 11,900.30 | 83.516 | 119.500 | 0.00% | 33.47 | 488.65 |

Headline at conc=1000: reactive RPS is **1.69x** async (11,900.30 / 7,025.13). Errors are 0.00% throughout the grid.

### 3.4 Tweet update

Workload: `dbwrite-heavy-tweet-update`, direct `PUT /tweets/{userId}/{tweetId}` on tweet-service.

| Conc | Stack | n | RPS | Avg ms | p95 ms | Errors | CPU avg % | Heap used avg MB |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | async | 2 | 14.82 | 67.23 | 72.00 | 0.00% | 0.58 | 131.48 |
| 1 | reactive | 2 | 15.79 | 63.12 | 68.00 | 0.00% | 0.62 | 83.47 |
| 5 | async | 2 | 75.25 | 66.29 | 72.50 | 0.00% | 1.94 | 126.54 |
| 5 | reactive | 2 | 81.88 | 60.91 | 67.00 | 0.00% | 2.25 | 83.94 |
| 10 | async | 2 | 151.05 | 66.08 | 75.00 | 0.00% | 3.06 | 129.43 |
| 10 | reactive | 2 | 161.50 | 61.76 | 73.50 | 0.00% | 3.42 | 85.04 |
| 15 | async | 2 | 237.19 | 63.14 | 72.00 | 0.00% | 4.28 | 130.12 |
| 15 | reactive | 2 | 252.95 | 59.16 | 71.50 | 0.00% | 4.67 | 88.69 |
| 20 | async | 2 | 329.41 | 60.62 | 69.50 | 0.00% | 5.38 | 133.55 |
| 20 | reactive | 2 | 353.90 | 56.40 | 67.50 | 0.00% | 5.73 | 85.59 |
| 25 | async | 2 | 420.10 | 59.42 | 66.00 | 0.00% | 6.59 | 192.75 |
| 25 | reactive | 2 | 450.59 | 55.38 | 66.00 | 0.00% | 6.53 | 86.74 |
| 50 | async | 2 | 935.81 | 53.37 | 59.00 | 0.00% | 8.12 | 245.32 |
| 50 | reactive | 2 | 993.20 | 50.28 | 57.50 | 0.00% | 7.61 | 111.97 |
| 75 | async | 2 | 1,446.39 | 51.76 | 55.00 | 0.00% | 13.48 | 251.98 |
| 75 | reactive | 2 | 1,528.87 | 49.00 | 55.00 | 0.00% | 9.07 | 231.53 |
| 100 | async | 2 | 2,049.18 | 48.72 | 53.50 | 0.00% | 16.77 | 257.96 |
| 100 | reactive | 2 | 2,139.65 | 46.67 | 50.00 | 0.00% | 12.12 | 240.89 |
| 200 | async | 2 | 3,915.63 | 50.98 | 55.00 | 0.00% | 31.16 | 286.60 |
| 200 | reactive | 2 | 4,543.64 | 43.95 | 47.00 | 0.00% | 31.71 | 266.35 |
| 400 | async | 2 | 4,441.27 | 89.97 | 100.50 | 0.00% | 33.41 | 330.55 |
| 400 | reactive | 2 | 5,420.78 | 73.72 | 91.00 | 0.00% | 37.95 | 321.51 |
| 600 | async | 2 | 4,402.69 | 136.14 | 149.00 | 0.00% | 33.61 | 352.95 |
| 600 | reactive | 2 | 5,377.22 | 111.49 | 129.50 | 0.00% | 37.86 | 332.36 |
| 800 | async | 2 | 4,407.35 | 181.40 | 195.50 | 0.00% | 33.26 | 370.97 |
| 800 | reactive | 2 | 5,308.40 | 150.58 | 170.00 | 0.00% | 37.50 | 356.94 |
| 1000 | async | 2 | 4,344.30 | 230.02 | 246.50 | 0.00% | 33.38 | 373.38 |
| 1000 | reactive | 2 | 5,254.72 | 190.12 | 211.00 | 0.00% | 37.28 | 352.16 |

Headline at conc=1000: reactive RPS is **1.21x** async (5,254.72 / 4,344.30), and reactive heap-used average is **0.94x** async (352.16 MB / 373.38 MB). Errors are 0.00% throughout the grid.

### 3.5 Bulk tweet summaries

Workload: `serialize-tweets-bulk`, direct `GET /tweets/user/{id}?size=1000` on tweet-service.

| Conc | Stack | n | RPS | Avg ms | p95 ms | Errors | CPU avg % | Heap used avg MB |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | async | 2 | 11.65 | 85.65 | 89.00 | 0.00% | 1.2 | 138 |
| 1 | reactive | 2 | 10.02 | 99.51 | 105.50 | 0.00% | 3.7 | 101 |
| 5 | async | 2 | 108.83 | 45.87 | 115.50 | 0.00% | 10.6 | 231 |
| 5 | reactive | 2 | 58.44 | 85.46 | 121.50 | 0.00% | 23.3 | 239 |
| 10 | async | 2 | 192.56 | 51.87 | 131.50 | 0.00% | 20.1 | 272 |
| 10 | reactive | 2 | 101.86 | 98.07 | 151.00 | 0.00% | 44.9 | 248 |
| 15 | async | 2 | 214.66 | 69.78 | 181.50 | 0.00% | 24.0 | 363 |
| 15 | reactive | 2 | 108.82 | 137.66 | 219.50 | 0.00% | 49.7 | 302 |
| 20 | async | 2 | 226.62 | 88.17 | 224.00 | 0.00% | 25.6 | 409 |
| 20 | reactive | 2 | 109.58 | 182.26 | 300.50 | 0.00% | 50.3 | 357 |
| 25 | async | 2 | 227.11 | 109.94 | 291.50 | 0.00% | 26.0 | 460 |
| 25 | reactive | 2 | 110.97 | 225.04 | 359.50 | 0.00% | 50.4 | 409 |
| 50 | async | 2 | 246.92 | 202.47 | 531.50 | 0.00% | 28.2 | 645 |
| 50 | reactive | 2 | 107.41 | 464.96 | 779.00 | 0.00% | 49.9 | 650 |
| 75 | async | 2 | 247.41 | 302.64 | 719.00 | 0.00% | 28.6 | 885 |
| 75 | reactive | 2 | 105.26 | 711.91 | 1,267.50 | 0.00% | 50.8 | 787 |
| 100 | async | 2 | 244.98 | 408.03 | 968.50 | 0.00% | 28.8 | 1,266 |
| 100 | reactive | 2 | 103.48 | 965.55 | 1,761.50 | 0.00% | 50.6 | 1,013 |
| 200 | async | 2 | 239.14 | 836.25 | 1,827.00 | 0.00% | 31.0 | 1,603 |
| 200 | reactive | 2 | 102.12 | 1,950.05 | 3,808.50 | 0.00% | 50.5 | 1,468 |
| 400 | async | 2 | 228.09 | 1,750.69 | 2,853.00 | 0.00% | 32.6 | 2,883 |
| 400 | reactive | 2 | 97.09 | 4,076.51 | 6,704.50 | 0.00% | 49.0 | 1,844 |
| 600 | async | 2 | 218.53 | 2,733.79 | 3,915.00 | 0.00% | 32.5 | 2,910 |
| 600 | reactive | 2 | 96.44 | 6,089.65 | 8,599.50 | 0.00% | 48.1 | 2,332 |
| 800 | async | 2 | 212.85 | 3,734.26 | 5,030.50 | 0.00% | 32.7 | 2,856 |
| 800 | reactive | 2 | 94.47 | 8,276.85 | 10,989.50 | 0.00% | 47.4 | 2,562 |
| 1000 | async | 2 | 208.98 | 4,760.86 | 5,935.50 | 0.00% | 32.5 | 3,019 |
| 1000 | reactive | 2 | 93.48 | 10,382.73 | 13,537.50 | 0.00% | 47.2 | 2,726 |

Headline at conc=1000: reactive RPS is **0.45x** async (93.48 / 208.98), while reactive heap-used average is **0.90x** async. This result reflects the ORM-style relationship loading limitation.

### 3.6 Database-bound tweet search

Workload: `dbread-heavy-tweet-search`, direct PostgreSQL `pg_trgm` similarity search over the one-million-row tweet seed.

| Conc | Stack | n | RPS | Avg ms | p95 ms | Errors | CPU avg % | Heap used avg MB |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | async | 2 | 2.74 | 364.126 | 455.000 | 0.00% | 0.11 | 95.92 |
| 1 | reactive | 2 | 2.78 | 359.577 | 383.000 | 0.00% | 0.07 | 83.73 |
| 5 | async | 2 | 13.23 | 377.623 | 465.000 | 0.00% | 0.19 | 98.21 |
| 5 | reactive | 2 | 13.41 | 372.508 | 467.500 | 0.00% | 0.17 | 84.69 |
| 10 | async | 2 | 26.20 | 381.236 | 480.000 | 0.00% | 0.26 | 98.72 |
| 10 | reactive | 2 | 26.51 | 377.072 | 411.000 | 0.00% | 0.27 | 84.21 |
| 15 | async | 2 | 36.23 | 413.482 | 483.000 | 0.00% | 0.66 | 98.64 |
| 15 | reactive | 2 | 36.49 | 410.702 | 554.500 | 0.00% | 0.74 | 83.50 |
| 20 | async | 2 | 38.33 | 521.396 | 658.500 | 0.00% | 0.65 | 99.20 |
| 20 | reactive | 2 | 38.33 | 521.626 | 653.000 | 0.00% | 0.73 | 85.22 |
| 25 | async | 2 | 38.25 | 652.663 | 801.500 | 0.00% | 0.69 | 99.63 |
| 25 | reactive | 2 | 38.22 | 653.029 | 780.500 | 0.00% | 0.79 | 85.20 |
| 50 | async | 2 | 38.81 | 1285.514 | 1629.000 | 0.00% | 0.70 | 101.09 |
| 50 | reactive | 2 | 38.75 | 1287.242 | 1615.500 | 0.00% | 0.80 | 85.94 |
| 75 | async | 2 | 38.30 | 1954.574 | 2407.500 | 0.00% | 0.64 | 105.40 |
| 75 | reactive | 2 | 38.23 | 1956.350 | 2426.000 | 0.00% | 0.72 | 88.69 |
| 100 | async | 2 | 37.94 | 2624.868 | 3192.500 | 0.00% | 0.61 | 111.62 |
| 100 | reactive | 2 | 37.92 | 2625.206 | 3219.500 | 0.00% | 0.69 | 90.59 |
| 200 | async | 2 | 37.62 | 5271.974 | 6098.500 | 0.00% | 0.56 | 134.62 |
| 200 | reactive | 2 | 37.50 | 5286.725 | 6160.500 | 0.00% | 0.68 | 122.50 |
| 400 | async | 2 | 37.55 | 10572.433 | 11756.000 | 0.00% | 0.54 | 180.09 |
| 400 | reactive | 2 | 37.42 | 10607.745 | 11731.000 | 0.00% | 0.66 | 180.16 |
| 600 | async | 2 | 37.74 | 15821.733 | 16924.500 | 0.00% | 0.53 | 225.55 |
| 600 | reactive | 2 | 37.42 | 15964.280 | 17104.500 | 0.00% | 0.66 | 211.56 |
| 800 | async | 2 | 37.78 | 21096.653 | 22229.500 | 0.00% | 0.53 | 279.29 |
| 800 | reactive | 2 | 37.32 | 21345.933 | 22502.000 | 0.00% | 0.65 | 251.55 |
| 1000 | async | 2 | 37.75 | 26401.039 | 27453.000 | 0.00% | 0.53 | 330.81 |
| 1000 | reactive | 2 | 37.35 | 26688.914 | 27820.500 | 0.00% | 0.66 | 298.03 |

Headline at conc=1000: reactive RPS is **0.99x** async (37.35 / 37.75), with both stacks pinned by the database search path. This is database-bound parity.

### 3.7 Following-ID cache read

Workload: `cacheread-following`, direct Redis-backed following-ID lookup on interaction-service.

| Conc | Stack | n | RPS | Avg ms | p95 ms | Errors | CPU avg % | Heap used avg MB |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | async | 2 | 26.83 | 36.72 | 50.71 | 0.00% | 1.20 | 122.50 |
| 1 | reactive | 2 | 30.29 | 32.44 | 45.54 | 0.00% | 0.93 | 84.09 |
| 5 | async | 2 | 337.74 | 11.61 | 33.14 | 0.00% | 2.32 | 122.45 |
| 5 | reactive | 2 | 371.08 | 10.19 | 28.62 | 0.00% | 1.86 | 81.51 |
| 10 | async | 2 | 842.97 | 7.99 | 30.73 | 0.00% | 3.21 | 123.26 |
| 10 | reactive | 2 | 909.35 | 7.07 | 26.48 | 0.00% | 2.55 | 83.11 |
| 15 | async | 2 | 1,398.22 | 6.60 | 29.52 | 0.00% | 4.66 | 123.57 |
| 15 | reactive | 2 | 1,493.08 | 5.88 | 25.39 | 0.00% | 3.35 | 86.80 |
| 20 | async | 2 | 1,964.62 | 5.91 | 29.23 | 0.00% | 6.05 | 124.65 |
| 20 | reactive | 2 | 2,078.07 | 5.33 | 25.21 | 0.00% | 4.21 | 88.26 |
| 25 | async | 2 | 2,553.19 | 5.43 | 28.93 | 0.00% | 7.71 | 124.48 |
| 25 | reactive | 2 | 2,691.47 | 4.90 | 24.87 | 0.00% | 5.17 | 87.37 |
| 50 | async | 2 | 5,484.88 | 4.55 | 28.97 | 0.00% | 15.90 | 124.93 |
| 50 | reactive | 2 | 5,754.84 | 4.11 | 24.66 | 0.00% | 9.17 | 214.86 |
| 75 | async | 2 | 8,320.76 | 4.34 | 12.45 | 0.00% | 27.31 | 185.34 |
| 75 | reactive | 2 | 8,886.95 | 3.76 | 11.76 | 0.00% | 12.69 | 242.61 |
| 100 | async | 2 | 10,574.60 | 4.77 | 13.72 | 0.00% | 41.31 | 192.65 |
| 100 | reactive | 2 | 12,041.01 | 3.56 | 11.71 | 0.00% | 16.23 | 236.78 |
| 200 | async | 2 | 14,639.41 | 13.48 | 32.30 | 0.00% | 59.98 | 230.13 |
| 200 | reactive | 2 | 24,395.43 | 3.41 | 6.65 | 0.00% | 29.81 | 308.38 |
| 400 | async | 2 | 17,962.52 | 21.88 | 44.62 | 0.00% | 61.96 | 355.52 |
| 400 | reactive | 2 | 45,394.11 | 5.79 | 11.87 | 0.00% | 45.46 | 574.50 |
| 600 | async | 2 | 18,727.91 | 31.24 | 60.24 | 0.00% | 61.80 | 436.87 |
| 600 | reactive | 2 | 52,761.41 | 10.82 | 18.56 | 0.00% | 48.28 | 729.15 |
| 800 | async | 2 | 18,841.85 | 41.31 | 75.74 | 0.00% | 61.96 | 640.24 |
| 800 | reactive | 2 | 52,388.38 | 14.13 | 24.66 | 0.00% | 47.47 | 732.03 |
| 1000 | async | 2 | 18,910.68 | 51.10 | 90.19 | 0.00% | 61.90 | 677.18 |
| 1000 | reactive | 2 | 51,864.20 | 17.14 | 29.70 | 0.00% | 46.93 | 766.31 |

Headline at conc=1000: reactive RPS is **2.74x** async (51,864.20 / 18,910.68), while reactive heap-used average is **1.13x** async (766.31 MB / 677.18 MB). The reactive heap increase at high concurrency should be read alongside the much higher completed request rate and lower CPU.

### 3.8 CPU image preview

Workload: `cpu-image-preview`, direct media-preview generation on user-service.

| Conc | Stack | n | RPS | Avg ms | p95 ms | Errors | CPU avg % | Heap used avg MB |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | async | 2 | 26.45 | 37.75 | 38.75 | 0.00% | 6.4 | 96 |
| 1 | reactive | 2 | 26.40 | 37.83 | 38.80 | 0.00% | 6.3 | 83 |
| 5 | async | 2 | 124.52 | 40.09 | 41.06 | 0.00% | 31.2 | 228 |
| 5 | reactive | 2 | 105.37 | 47.38 | 79.69 | 0.00% | 26.4 | 220 |
| 10 | async | 2 | 221.62 | 45.07 | 46.88 | 0.00% | 62.1 | 232 |
| 10 | reactive | 2 | 209.90 | 47.59 | 54.98 | 0.00% | 58.9 | 216 |
| 15 | async | 2 | 276.64 | 54.13 | 61.52 | 0.00% | 91.6 | 231 |
| 15 | reactive | 2 | 271.06 | 55.23 | 69.36 | 0.00% | 87.5 | 206 |
| 20 | async | 2 | 283.74 | 70.38 | 87.10 | 0.00% | 95.1 | 250 |
| 20 | reactive | 2 | 281.10 | 71.03 | 121.30 | 0.00% | 93.8 | 230 |
| 25 | async | 2 | 284.00 | 87.90 | 105.61 | 0.00% | 94.5 | 264 |
| 25 | reactive | 2 | 282.13 | 88.47 | 183.52 | 0.00% | 93.6 | 284 |
| 50 | async | 2 | 284.47 | 175.55 | 192.11 | 0.00% | 95.1 | 326 |
| 50 | reactive | 2 | 283.61 | 175.91 | 521.91 | 0.00% | 95.0 | 278 |
| 75 | async | 2 | 284.15 | 263.56 | 281.43 | 0.00% | 95.1 | 336 |
| 75 | reactive | 2 | 283.53 | 263.43 | 784.79 | 0.00% | 94.6 | 332 |
| 100 | async | 2 | 284.03 | 351.45 | 369.02 | 0.00% | 94.9 | 356 |
| 100 | reactive | 2 | 283.49 | 351.22 | 929.92 | 0.00% | 95.2 | 343 |
| 200 | async | 2 | 282.53 | 705.65 | 722.75 | 0.00% | 94.8 | 389 |
| 200 | reactive | 2 | 283.19 | 702.11 | 1,530.00 | 0.00% | 94.6 | 350 |
| 400 | async | 2 | 280.17 | 1,410.00 | 1,430.00 | 0.00% | 95.0 | 443 |
| 400 | reactive | 2 | 283.00 | 1,400.00 | 2,315.00 | 0.00% | 95.1 | 359 |
| 600 | async | 2 | 278.75 | 2,130.00 | 2,135.00 | 0.00% | 94.4 | 510 |
| 600 | reactive | 2 | 282.60 | 2,095.00 | 2,965.00 | 0.00% | 95.1 | 333 |
| 800 | async | 2 | 276.97 | 2,850.00 | 2,840.00 | 0.00% | 95.2 | 514 |
| 800 | reactive | 2 | 283.28 | 2,780.00 | 3,550.00 | 0.00% | 95.1 | 402 |
| 1000 | async | 2 | 275.93 | 3,560.00 | 3,545.00 | 0.00% | 95.3 | 670 |
| 1000 | reactive | 2 | 284.07 | 3,460.00 | 4,215.00 | 0.00% | 94.9 | 535 |

Headline at conc=1000: reactive RPS is **1.03x** async (284.07 / 275.93). Treat this as CPU-bound RPS parity. Reactive tail latency remains higher at high concurrency, which is the expected event-loop caveat for synchronous CPU work.

### 3.9 Blocking file download

Workload: `blockio-file-download`, direct throttled 256 KiB media download on user-service.

| Conc | Stack | n | RPS | Avg ms | p95 ms | Errors | CPU avg % | Heap used avg MB |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | async | 2 | 16.05 | 62.02 | 66.47 | 0.00% | 1.3 | 79 |
| 1 | reactive | 2 | 16.74 | 59.49 | 63.75 | 0.00% | 0.3 | 76 |
| 5 | async | 2 | 87.41 | 57.02 | 63.42 | 0.00% | 1.3 | 77 |
| 5 | reactive | 2 | 88.45 | 56.34 | 62.42 | 0.00% | 1.0 | 74 |
| 10 | async | 2 | 178.05 | 56.01 | 62.36 | 0.00% | 1.8 | 90 |
| 10 | reactive | 2 | 181.88 | 54.80 | 61.08 | 0.00% | 1.1 | 74 |
| 15 | async | 2 | 270.99 | 55.20 | 61.67 | 0.00% | 2.8 | 97 |
| 15 | reactive | 2 | 277.84 | 53.84 | 60.42 | 0.00% | 1.6 | 73 |
| 20 | async | 2 | 366.38 | 54.45 | 61.04 | 0.00% | 3.4 | 100 |
| 20 | reactive | 2 | 376.33 | 53.00 | 59.84 | 0.00% | 1.9 | 74 |
| 25 | async | 2 | 466.50 | 53.47 | 60.12 | 0.00% | 4.8 | 99 |
| 25 | reactive | 2 | 474.16 | 52.60 | 59.45 | 0.00% | 2.3 | 75 |
| 50 | async | 2 | 915.58 | 54.54 | 61.17 | 0.00% | 8.7 | 105 |
| 50 | reactive | 2 | 954.30 | 52.29 | 59.17 | 0.00% | 3.8 | 79 |
| 75 | async | 2 | 1,449.66 | 51.66 | 58.02 | 0.00% | 14.0 | 221 |
| 75 | reactive | 2 | 1,437.15 | 52.09 | 59.33 | 0.00% | 4.9 | 76 |
| 100 | async | 2 | 1,862.82 | 53.60 | 61.20 | 0.00% | 17.8 | 242 |
| 100 | reactive | 2 | 1,912.03 | 52.20 | 59.89 | 0.00% | 7.0 | 77 |
| 200 | async | 2 | 4,042.28 | 49.41 | 53.93 | 0.00% | 33.5 | 270 |
| 200 | reactive | 2 | 4,039.68 | 49.44 | 56.70 | 0.00% | 11.1 | 77 |
| 400 | async | 2 | 7,205.43 | 55.24 | 72.81 | 0.00% | 70.5 | 348 |
| 400 | reactive | 2 | 8,460.95 | 47.20 | 52.28 | 0.00% | 27.3 | 234 |
| 600 | async | 2 | 8,298.51 | 70.81 | 99.77 | 0.00% | 76.8 | 397 |
| 600 | reactive | 2 | 12,695.71 | 47.15 | 53.06 | 0.00% | 57.0 | 279 |
| 800 | async | 2 | 8,599.45 | 89.25 | 135.75 | 0.00% | 76.3 | 535 |
| 800 | reactive | 2 | 11,433.75 | 68.94 | 116.66 | 0.00% | 73.4 | 328 |
| 1000 | async | 2 | 8,735.35 | 107.59 | 176.75 | 0.00% | 77.0 | 657 |
| 1000 | reactive | 2 | 10,350.73 | 93.97 | 168.73 | 0.00% | 75.7 | 312 |

Headline at conc=1000: reactive RPS is **1.18x** async (10,350.73 / 8,735.35), and reactive heap-used average is **0.48x** async (312 MB / 657 MB). The memory reduction is the load-bearing result; heap-committed remains an audit metric only because G1 commitment policy is noisy for this workload.

### 3.10 Single-tweet HTTP fan-out

Workload: `http-fanout-get-tweet`, direct single-tweet read on tweet-service with downstream interaction fan-out.

| Conc | Stack | n | RPS | Avg ms | p95 ms | Errors | CPU avg % | Heap used avg MB |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | async | 2 | 18.65 | 53.38 | 60.81 | 0.00% | 1.09 | 138.37 |
| 1 | reactive | 2 | 24.94 | 39.92 | 46.20 | 0.00% | 0.88 | 84.10 |
| 5 | async | 2 | 146.91 | 33.95 | 44.02 | 0.00% | 2.43 | 133.56 |
| 5 | reactive | 2 | 192.11 | 25.94 | 34.20 | 0.00% | 3.01 | 82.42 |
| 10 | async | 2 | 341.65 | 29.18 | 41.41 | 0.00% | 4.22 | 135.79 |
| 10 | reactive | 2 | 439.34 | 22.69 | 32.66 | 0.00% | 4.70 | 86.00 |
| 15 | async | 2 | 545.82 | 27.40 | 41.00 | 0.00% | 6.39 | 134.82 |
| 15 | reactive | 2 | 702.24 | 21.29 | 32.14 | 0.00% | 6.90 | 86.85 |
| 20 | async | 2 | 748.30 | 26.64 | 41.17 | 0.00% | 8.72 | 139.79 |
| 20 | reactive | 2 | 964.45 | 20.66 | 32.09 | 0.00% | 9.41 | 86.33 |
| 25 | async | 2 | 944.42 | 26.39 | 41.58 | 0.00% | 11.44 | 166.95 |
| 25 | reactive | 2 | 1,229.47 | 20.27 | 32.07 | 0.00% | 10.93 | 129.20 |
| 50 | async | 2 | 1,638.34 | 30.44 | 48.73 | 0.00% | 21.72 | 266.43 |
| 50 | reactive | 2 | 2,471.35 | 20.16 | 32.59 | 0.00% | 21.02 | 147.40 |
| 75 | async | 2 | 1,867.55 | 40.08 | 61.69 | 0.00% | 25.77 | 284.11 |
| 75 | reactive | 2 | 3,337.31 | 22.40 | 33.27 | 0.00% | 26.70 | 164.96 |
| 100 | async | 2 | 1,970.78 | 50.64 | 77.15 | 0.00% | 27.48 | 301.20 |
| 100 | reactive | 2 | 3,725.81 | 26.77 | 38.36 | 0.00% | 28.36 | 209.44 |
| 200 | async | 2 | 2,267.62 | 88.06 | 117.19 | 0.00% | 26.94 | 336.07 |
| 200 | reactive | 2 | 3,855.72 | 51.78 | 63.47 | 0.00% | 26.53 | 256.20 |
| 400 | async | 2 | 2,249.36 | 177.59 | 210.25 | 0.00% | 27.36 | 408.06 |
| 400 | reactive | 2 | 3,904.61 | 102.30 | 116.65 | 0.00% | 26.74 | 298.29 |
| 600 | async | 2 | 2,249.99 | 266.19 | 306.86 | 0.00% | 27.22 | 416.35 |
| 600 | reactive | 2 | 3,867.53 | 154.88 | 178.12 | 0.00% | 27.03 | 377.43 |
| 800 | async | 2 | 2,207.98 | 361.56 | 417.42 | 0.00% | 27.39 | 460.14 |
| 800 | reactive | 2 | 3,843.58 | 207.75 | 243.94 | 0.00% | 27.41 | 446.05 |
| 1000 | async | 2 | 2,204.51 | 452.53 | 523.60 | 0.00% | 27.22 | 537.91 |
| 1000 | reactive | 2 | 3,801.10 | 262.38 | 314.45 | 0.00% | 28.67 | 458.99 |

Headline at conc=1000: reactive RPS is **1.72x** async (3,801.10 / 2,204.51), and reactive heap-used average is **0.85x** async (458.99 MB / 537.91 MB). Errors are 0.00% throughout the grid.

---

## 4. AI streaming results

The AI measurements use `native-local` with host PostgreSQL, Redis, Vault, application JVMs, k6, and a host-local `mlx_lm.server` for live-backend probes. The primary grid uses the calibrated mock backend `qwen-3.5-4b-mlxlm-v2`, Spring `benchmark`, `-Xmx4g`, and an async `streamExecutor` with T=400, Q=4000, and `AbortPolicy`.

k6 ramps to a constant open-loop arrival rate with a 60-second warmup and a 180-second measured interval. `AI_PREALLOC_VUS=15000`, `AI_MAX_VUS=30000`, host `kern.ipc.somaxconn=4096`, Tomcat `accept-count=4096`, and Tomcat `max-connections=16384`. Effective backend, token, and executor configuration is asserted through Prometheus before each cell on both stacks.

### 4.1 Primary result

The 18 target rates are:

`10 25 50 75 100 125 150 175 190 200 210 225 250 300 350 400 500 650`

The grid covers W0 non-AI streaming, W1 chat, and W2 chat with a tool call on both stacks: 108 primary cells. All 108 cells passed the transport-quality gate; dropped arrivals were 0.00%, and transport errors were below 0.001% overall. Knee cells have five repetitions for bootstrap CI and Mann–Whitney U comparison.

| Workload | Clean knee rps | Async p99 ms | Reactive p99 ms | Ratio | Interpretation |
|---|---:|---:|---:|---:|---|
| W1 chat | 200 | 8,958 | 1,975 | **4.5x** | predicted knee, `400 / 2.0 s` |
| W1 chat | 210 | 18,113 | 1,974 | **9.2x** | just beyond the knee |
| W0 non-AI stream | 225 | 15,112 | 1,775 | **8.5x** | no LLM involved |
| W2 chat + tool | 150 | 18,386 | 1,982 | **9.3x** | tool call extends residency |

Reactive p99 remains near 1.7–2.0 seconds across the grid while CPU and heap rise with offered work. Beyond the async residency boundary, p99 and application errors rise sharply; the zero-error knee rows above isolate the latency transition from rejection behavior.

### 4.2 Full 18-point grid

| Workload | Target rps | Stack | n | Delivered rps | p50 ms | p95 ms | p99 ms | Errors | Dropped | CPU avg % | Heap used avg MB |
| --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| W0 non-AI stream | 10 | async | 1 | 10.0 | 1,937 | 2,177 | 2,203 | 0.00% | 0.00% | 1.7 | 136.7 |
| W0 non-AI stream | 10 | reactive | 1 | 10.0 | 2,041 | 2,070 | 2,096 | 0.00% | 0.00% | 0.8 | 107.7 |
| W0 non-AI stream | 25 | async | 1 | 25.0 | 2,086 | 2,133 | 2,154 | 0.00% | 0.00% | 1.6 | 147.2 |
| W0 non-AI stream | 25 | reactive | 1 | 25.0 | 1,957 | 1,979 | 1,991 | 0.00% | 0.00% | 1.3 | 103.3 |
| W0 non-AI stream | 50 | async | 1 | 50.0 | 2,039 | 2,082 | 2,101 | 0.00% | 0.00% | 2.8 | 161.8 |
| W0 non-AI stream | 50 | reactive | 1 | 50.0 | 1,880 | 1,900 | 1,917 | 0.00% | 0.00% | 2.3 | 110.7 |
| W0 non-AI stream | 75 | async | 1 | 75.0 | 1,981 | 2,020 | 2,040 | 0.00% | 0.00% | 5.0 | 202.8 |
| W0 non-AI stream | 75 | reactive | 1 | 75.0 | 1,886 | 1,919 | 1,933 | 0.00% | 0.00% | 3.0 | 117.9 |
| W0 non-AI stream | 100 | async | 1 | 100.0 | 1,944 | 1,983 | 2,003 | 0.00% | 0.00% | 6.2 | 251.9 |
| W0 non-AI stream | 100 | reactive | 1 | 100.0 | 1,810 | 1,833 | 1,848 | 0.00% | 0.00% | 3.7 | 118.4 |
| W0 non-AI stream | 125 | async | 1 | 125.0 | 1,930 | 1,968 | 1,988 | 0.00% | 0.00% | 7.2 | 295.9 |
| W0 non-AI stream | 125 | reactive | 1 | 125.0 | 1,796 | 1,822 | 1,856 | 0.00% | 0.00% | 4.2 | 119.8 |
| W0 non-AI stream | 150 | async | 1 | 150.0 | 1,920 | 1,955 | 1,973 | 0.00% | 0.00% | 9.2 | 336.2 |
| W0 non-AI stream | 150 | reactive | 1 | 150.0 | 1,792 | 1,816 | 1,833 | 0.00% | 0.00% | 5.0 | 122.2 |
| W0 non-AI stream | 175 | async | 1 | 175.0 | 1,889 | 1,932 | 1,955 | 0.00% | 0.00% | 9.6 | 373.6 |
| W0 non-AI stream | 175 | reactive | 1 | 175.0 | 1,751 | 1,769 | 1,783 | 0.00% | 0.00% | 5.4 | 121.8 |
| W0 non-AI stream | 190 | async | 1 | 190.0 | 1,908 | 1,950 | 1,971 | 0.00% | 0.00% | 11.1 | 397.1 |
| W0 non-AI stream | 190 | reactive | 1 | 190.0 | 1,748 | 1,763 | 1,774 | 0.00% | 0.00% | 4.7 | 121.7 |
| W0 non-AI stream | 200 | async | 1 | 200.0 | 1,898 | 1,937 | 1,957 | 0.00% | 0.00% | 11.7 | 418.6 |
| W0 non-AI stream | 200 | reactive | 1 | 200.0 | 1,750 | 1,764 | 1,775 | 0.00% | 0.00% | 5.1 | 122.9 |
| W0 non-AI stream | 210 | async | 5 | 210.0 | 1,910 | 1,977 | 2,008 | 0.00% | 0.00% | 12.5 | 434.4 |
| W0 non-AI stream | 210 | reactive | 5 | 210.0 | 1,751 | 1,766 | 1,778 | 0.00% | 0.00% | 5.2 | 121.7 |
| W0 non-AI stream | 225 | async | 5 | 225.0 | 7,675 | 14,472 | 15,112 | 0.00% | 0.00% | 13.1 | 770.7 |
| W0 non-AI stream | 225 | reactive | 5 | 225.0 | 1,750 | 1,764 | 1,775 | 0.00% | 0.00% | 5.5 | 124.7 |
| W0 non-AI stream | 250 | async | 1 | 250.0 | 16,942 | 21,093 | 21,137 | 6.80% | 0.00% | 13.4 | 992.1 |
| W0 non-AI stream | 250 | reactive | 1 | 250.0 | 1,740 | 1,754 | 1,765 | 0.00% | 0.00% | 5.7 | 126.2 |
| W0 non-AI stream | 300 | async | 1 | 300.0 | 20,774 | 20,985 | 21,119 | 21.90% | 0.00% | 14.0 | 1,159.9 |
| W0 non-AI stream | 300 | reactive | 1 | 300.0 | 1,728 | 1,740 | 1,750 | 0.00% | 0.00% | 6.4 | 134.8 |
| W0 non-AI stream | 350 | async | 1 | 350.0 | 20,416 | 20,790 | 20,996 | 32.00% | 0.00% | 13.6 | 1,224.7 |
| W0 non-AI stream | 350 | reactive | 1 | 350.0 | 1,716 | 1,727 | 1,738 | 0.00% | 0.00% | 7.3 | 141.9 |
| W0 non-AI stream | 400 | async | 1 | 400.0 | 20,482 | 20,763 | 20,996 | 40.60% | 0.00% | 13.5 | 1,233.7 |
| W0 non-AI stream | 400 | reactive | 1 | 400.0 | 1,709 | 1,720 | 1,729 | 0.00% | 0.00% | 7.9 | 153.7 |
| W0 non-AI stream | 500 | async | 1 | 500.0 | 20,275 | 20,589 | 20,875 | 52.00% | 0.00% | 13.2 | 1,380.3 |
| W0 non-AI stream | 500 | reactive | 1 | 500.0 | 1,704 | 1,715 | 1,723 | 0.00% | 0.00% | 10.2 | 167.8 |
| W0 non-AI stream | 650 | async | 1 | 650.0 | 20,074 | 20,542 | 20,880 | 62.70% | 0.00% | 13.2 | 1,578.1 |
| W0 non-AI stream | 650 | reactive | 1 | 650.0 | 1,691 | 1,700 | 1,708 | 0.00% | 0.00% | 13.4 | 182.3 |
| W1 chat | 10 | async | 1 | 10.0 | 2,287 | 2,329 | 2,358 | 0.00% | 0.00% | 2.6 | 152.9 |
| W1 chat | 10 | reactive | 1 | 10.0 | 2,283 | 2,323 | 2,360 | 0.00% | 0.00% | 1.0 | 86.1 |
| W1 chat | 25 | async | 1 | 25.0 | 2,156 | 2,188 | 2,205 | 0.00% | 0.00% | 6.9 | 161.5 |
| W1 chat | 25 | reactive | 1 | 25.0 | 2,137 | 2,166 | 2,188 | 0.00% | 0.00% | 1.7 | 86.9 |
| W1 chat | 50 | async | 1 | 50.0 | 2,042 | 2,080 | 2,101 | 0.00% | 0.00% | 10.9 | 177.2 |
| W1 chat | 50 | reactive | 1 | 50.0 | 2,023 | 2,055 | 2,074 | 0.00% | 0.00% | 3.4 | 93.5 |
| W1 chat | 75 | async | 1 | 75.0 | 1,977 | 2,005 | 2,025 | 0.00% | 0.00% | 13.0 | 214.9 |
| W1 chat | 75 | reactive | 1 | 75.0 | 1,969 | 1,998 | 2,013 | 0.00% | 0.00% | 3.8 | 100.9 |
| W1 chat | 100 | async | 1 | 100.0 | 1,967 | 1,994 | 2,009 | 0.00% | 0.00% | 20.0 | 261.6 |
| W1 chat | 100 | reactive | 1 | 100.0 | 1,956 | 1,984 | 1,998 | 0.00% | 0.00% | 4.6 | 113.3 |
| W1 chat | 125 | async | 1 | 125.0 | 1,964 | 1,990 | 2,004 | 0.00% | 0.00% | 29.0 | 309.4 |
| W1 chat | 125 | reactive | 1 | 125.0 | 1,949 | 1,975 | 1,987 | 0.00% | 0.00% | 5.7 | 120.0 |
| W1 chat | 150 | async | 1 | 150.0 | 1,970 | 1,997 | 2,009 | 0.00% | 0.00% | 41.7 | 347.4 |
| W1 chat | 150 | reactive | 1 | 150.0 | 1,943 | 1,969 | 1,981 | 0.00% | 0.00% | 6.9 | 130.3 |
| W1 chat | 175 | async | 1 | 175.0 | 2,007 | 2,036 | 2,049 | 0.00% | 0.00% | 53.1 | 383.3 |
| W1 chat | 175 | reactive | 1 | 175.0 | 1,940 | 1,966 | 1,977 | 0.00% | 0.00% | 8.1 | 139.8 |
| W1 chat | 190 | async | 5 | 190.0 | 2,052 | 2,091 | 2,108 | 0.00% | 0.00% | 55.4 | 406.1 |
| W1 chat | 190 | reactive | 5 | 190.0 | 1,938 | 1,964 | 1,975 | 0.00% | 0.00% | 8.9 | 145.1 |
| W1 chat | 200 | async | 5 | 200.0 | 5,500 | 8,695 | 8,958 | 0.00% | 0.00% | 57.8 | 514.8 |
| W1 chat | 200 | reactive | 5 | 200.0 | 1,937 | 1,963 | 1,975 | 0.00% | 0.00% | 9.3 | 147.4 |
| W1 chat | 210 | async | 5 | 210.0 | 10,114 | 17,452 | 18,113 | 0.00% | 0.00% | 59.5 | 737.7 |
| W1 chat | 210 | reactive | 5 | 210.0 | 1,937 | 1,962 | 1,974 | 0.00% | 0.00% | 10.0 | 152.6 |
| W1 chat | 225 | async | 1 | 225.0 | 16,489 | 23,010 | 23,043 | 4.70% | 0.00% | 60.7 | 1,020.7 |
| W1 chat | 225 | reactive | 1 | 225.0 | 1,936 | 1,962 | 1,973 | 0.00% | 0.00% | 10.8 | 150.7 |
| W1 chat | 250 | async | 1 | 250.0 | 22,901 | 23,027 | 23,058 | 14.30% | 0.00% | 63.2 | 1,066.2 |
| W1 chat | 250 | reactive | 1 | 250.0 | 1,936 | 1,961 | 1,972 | 0.00% | 0.00% | 12.7 | 162.7 |
| W1 chat | 300 | async | 1 | 300.0 | 22,926 | 23,001 | 23,026 | 28.60% | 0.00% | 64.3 | 1,019.9 |
| W1 chat | 300 | reactive | 1 | 300.0 | 1,935 | 1,960 | 1,971 | 0.00% | 0.00% | 15.9 | 201.4 |
| W1 chat | 350 | async | 1 | 350.0 | 23,097 | 23,189 | 23,216 | 39.00% | 0.00% | 62.8 | 1,147.2 |
| W1 chat | 350 | reactive | 1 | 350.0 | 1,933 | 1,959 | 1,970 | 0.00% | 0.00% | 19.2 | 238.1 |
| W1 chat | 400 | async | 1 | 400.0 | 23,009 | 23,074 | 23,096 | 46.60% | 0.00% | 64.9 | 1,244.7 |
| W1 chat | 400 | reactive | 1 | 400.0 | 1,932 | 1,957 | 1,968 | 0.00% | 0.00% | 22.1 | 229.6 |
| W1 chat | 500 | async | 1 | 500.0 | 23,115 | 23,207 | 23,243 | 57.30% | 0.00% | 65.1 | 1,322.6 |
| W1 chat | 500 | reactive | 1 | 500.0 | 1,929 | 1,954 | 1,966 | 0.00% | 0.00% | 31.1 | 243.0 |
| W1 chat | 650 | async | 1 | 650.0 | 23,239 | 23,380 | 23,416 | 67.40% | 0.00% | 64.5 | 1,443.0 |
| W1 chat | 650 | reactive | 1 | 650.0 | 1,936 | 1,962 | 1,973 | 0.00% | 0.00% | 39.6 | 265.3 |
| W2 chat + tool | 10 | async | 1 | 10.0 | 2,292 | 2,332 | 2,365 | 0.00% | 0.00% | 4.0 | 145.5 |
| W2 chat + tool | 10 | reactive | 1 | 10.0 | 2,280 | 2,322 | 2,355 | 0.00% | 0.00% | 1.2 | 108.0 |
| W2 chat + tool | 25 | async | 1 | 25.0 | 2,183 | 2,215 | 2,235 | 0.00% | 0.00% | 10.6 | 152.4 |
| W2 chat + tool | 25 | reactive | 1 | 25.0 | 2,128 | 2,159 | 2,180 | 0.00% | 0.00% | 1.6 | 107.2 |
| W2 chat + tool | 50 | async | 1 | 50.0 | 2,086 | 2,114 | 2,131 | 0.00% | 0.00% | 19.8 | 315.5 |
| W2 chat + tool | 50 | reactive | 1 | 50.0 | 2,020 | 2,053 | 2,077 | 0.00% | 0.00% | 3.2 | 113.4 |
| W2 chat + tool | 75 | async | 1 | 75.0 | 1,992 | 2,020 | 2,039 | 0.00% | 0.00% | 24.9 | 362.4 |
| W2 chat + tool | 75 | reactive | 1 | 75.0 | 1,969 | 1,997 | 2,014 | 0.00% | 0.00% | 4.3 | 117.5 |
| W2 chat + tool | 100 | async | 1 | 100.0 | 2,000 | 2,028 | 2,042 | 0.00% | 0.00% | 41.8 | 397.8 |
| W2 chat + tool | 100 | reactive | 1 | 100.0 | 1,957 | 1,983 | 1,997 | 0.00% | 0.00% | 4.7 | 124.2 |
| W2 chat + tool | 125 | async | 5 | 125.0 | 2,129 | 2,179 | 2,200 | 0.00% | 0.00% | 57.3 | 410.3 |
| W2 chat + tool | 125 | reactive | 5 | 125.0 | 1,949 | 1,975 | 1,988 | 0.00% | 0.00% | 5.9 | 129.4 |
| W2 chat + tool | 150 | async | 5 | 150.0 | 10,697 | 17,927 | 18,386 | 0.00% | 0.00% | 62.5 | 553.5 |
| W2 chat + tool | 150 | reactive | 5 | 150.0 | 1,943 | 1,969 | 1,982 | 0.00% | 0.00% | 7.1 | 136.5 |
| W2 chat + tool | 175 | async | 1 | 175.0 | 24,884 | 32,063 | 32,158 | 8.50% | 0.00% | 65.4 | 889.8 |
| W2 chat + tool | 175 | reactive | 1 | 175.0 | 1,940 | 1,966 | 1,977 | 0.00% | 0.00% | 8.2 | 146.0 |
| W2 chat + tool | 190 | async | 1 | 190.0 | 31,249 | 32,026 | 32,130 | 15.60% | 0.00% | 67.7 | 856.5 |
| W2 chat + tool | 190 | reactive | 1 | 190.0 | 1,938 | 1,964 | 1,975 | 0.00% | 0.00% | 9.0 | 148.0 |
| W2 chat + tool | 200 | async | 1 | 200.0 | 31,713 | 32,115 | 32,219 | 20.00% | 0.00% | 69.0 | 1,103.2 |
| W2 chat + tool | 200 | reactive | 1 | 200.0 | 1,938 | 1,964 | 1,975 | 0.00% | 0.00% | 9.6 | 146.1 |
| W2 chat + tool | 210 | async | 1 | 210.0 | 31,746 | 32,086 | 32,189 | 23.70% | 0.00% | 69.3 | 1,137.8 |
| W2 chat + tool | 210 | reactive | 1 | 210.0 | 1,937 | 1,963 | 1,975 | 0.00% | 0.00% | 10.2 | 153.4 |
| W2 chat + tool | 225 | async | 1 | 225.0 | 31,725 | 31,994 | 32,077 | 28.60% | 0.00% | 69.6 | 1,179.3 |
| W2 chat + tool | 225 | reactive | 1 | 225.0 | 1,936 | 1,962 | 1,973 | 0.00% | 0.00% | 11.0 | 153.6 |
| W2 chat + tool | 250 | async | 1 | 250.0 | 31,799 | 32,037 | 32,117 | 35.70% | 0.00% | 69.8 | 1,297.2 |
| W2 chat + tool | 250 | reactive | 1 | 250.0 | 1,936 | 1,961 | 1,973 | 0.00% | 0.00% | 12.6 | 164.3 |
| W2 chat + tool | 300 | async | 1 | 300.0 | 31,840 | 32,055 | 32,132 | 46.50% | 0.00% | 70.2 | 1,565.8 |
| W2 chat + tool | 300 | reactive | 1 | 300.0 | 1,935 | 1,960 | 1,971 | 0.00% | 0.00% | 16.2 | 264.2 |
| W2 chat + tool | 350 | async | 1 | 350.0 | 31,952 | 32,192 | 32,287 | 54.20% | 0.00% | 71.4 | 1,695.7 |
| W2 chat + tool | 350 | reactive | 1 | 350.0 | 1,934 | 1,959 | 1,971 | 0.00% | 0.00% | 19.4 | 263.1 |
| W2 chat + tool | 400 | async | 1 | 400.0 | 32,014 | 32,306 | 32,429 | 60.00% | 0.00% | 70.8 | 1,629.9 |
| W2 chat + tool | 400 | reactive | 1 | 400.0 | 1,932 | 1,957 | 1,968 | 0.00% | 0.00% | 22.1 | 282.5 |
| W2 chat + tool | 500 | async | 1 | 500.0 | 32,251 | 32,583 | 32,723 | 68.20% | 0.00% | 70.5 | 1,646.7 |
| W2 chat + tool | 500 | reactive | 1 | 500.0 | 1,930 | 1,955 | 1,966 | 0.00% | 0.00% | 27.2 | 248.8 |
| W2 chat + tool | 650 | async | 1 | 650.0 | 32,591 | 32,879 | 33,024 | 75.70% | 0.00% | 70.1 | 1,739.4 |
| W2 chat + tool | 650 | reactive | 1 | 650.0 | 1,937 | 1,964 | 1,975 | 0.00% | 0.00% | 40.4 | 286.8 |

#### 4.2.1 Recorded p99 confidence intervals

The full grid above contains p99 point estimates only. These are all cells with a separately recorded p99 confidence interval.

| Workload | Target rps | Stack | n | p99 ms | p99 CI95 low | p99 CI95 high |
| --- | ---: | --- | ---: | ---: | ---: | ---: |
| W0 non-AI stream | 210 | async | 5 | 2,008 | 1,984 | 2,033 |
| W0 non-AI stream | 210 | reactive | 5 | 1,778 | 1,777 | 1,778 |
| W0 non-AI stream | 225 | async | 5 | 15,112 | 14,944 | 15,281 |
| W0 non-AI stream | 225 | reactive | 5 | 1,775 | 1,773 | 1,777 |
| W1 chat | 190 | async | 5 | 2,108 | 2,072 | 2,129 |
| W1 chat | 200 | async | 5 | 8,958 | 8,887 | 9,049 |
| W1 chat | 200 | reactive | 5 | 1,975 | 1,974 | 1,975 |
| W1 chat | 210 | async | 5 | 18,113 | 18,021 | 18,190 |
| W1 chat | 210 | reactive | 5 | 1,974 | 1,973 | 1,974 |
| W2 chat + tool | 125 | async | 5 | 2,200 | 2,139 | 2,242 |
| W2 chat + tool | 125 | reactive | 5 | 1,988 | 1,987 | 1,988 |
| W2 chat + tool | 150 | async | 5 | 18,386 | 17,920 | 18,766 |
| W2 chat + tool | 150 | reactive | 5 | 1,982 | 1,981 | 1,982 |

### 4.3 Output-token sensitivity

The W1 output-token probe varies generated response length. Shorter responses remain below the executor-residency boundary at higher rates, while longer responses cross it earlier.

| Output tokens | Target rps | Stack | n | p99 ms | Delivered rps | Errors | Dropped |
|---:|---:|---|---:|---:|---:|---:|---:|
| 64 | 100 | async | 1 | 972 | 100.0 | 0.00% | 0.00% |
| 64 | 100 | reactive | 1 | 979 | 100.0 | 0.00% | 0.00% |
| 64 | 200 | async | 1 | 948 | 200.0 | 0.00% | 0.00% |
| 64 | 200 | reactive | 1 | 944 | 200.0 | 0.00% | 0.00% |
| 64 | 400 | async | 1 | 978 | 400.0 | 0.00% | 0.00% |
| 64 | 400 | reactive | 1 | 934 | 400.0 | 0.00% | 0.00% |
| 150 | 100 | async | 1 | 2,005 | 100.0 | 0.00% | 0.00% |
| 150 | 100 | reactive | 1 | 1,997 | 100.0 | 0.00% | 0.00% |
| 150 | 200 | async | 1 | 7,179 | 200.0 | 0.00% | 0.00% |
| 150 | 200 | reactive | 1 | 1,974 | 200.0 | 0.00% | 0.00% |
| 150 | 400 | async | 1 | 23,216 | 400.0 | 46.70% | 0.00% |
| 150 | 400 | reactive | 1 | 1,969 | 400.0 | 0.00% | 0.00% |
| 400 | 100 | async | 1 | 52,882 | 100.0 | 0.00% | 0.00% |
| 400 | 100 | reactive | 1 | 4,999 | 100.0 | 0.00% | 0.00% |
| 400 | 200 | async | 1 | 56,980 | 200.0 | 50.00% | 0.00% |
| 400 | 200 | reactive | 1 | 4,976 | 200.0 | 0.00% | 0.00% |
| 400 | 400 | async | 1 | 58,035 | 400.0 | 75.10% | 0.00% |
| 400 | 400 | reactive | 1 | 5,952 | 400.0 | 0.00% | 1.00% |

### 4.4 Buffered-response control

Buffered REST uses the same W1 residency as SSE. The matching async transition shows that request residency, rather than SSE framing, is the driver.

| Transport | Target rps | Stack | n | p99 ms | Delivered rps | Errors | Dropped |
|---|---:|---|---:|---:|---:|---:|---:|
| SSE primary | 100 | async | 1 | 2,009 | 100.0 | 0.00% | 0.00% |
| SSE primary | 100 | reactive | 1 | 1,998 | 100.0 | 0.00% | 0.00% |
| SSE primary | 200 | async | 5 | 8,958 | 200.0 | 0.00% | 0.00% |
| SSE primary | 200 | reactive | 5 | 1,975 | 200.0 | 0.00% | 0.00% |
| SSE primary | 400 | async | 1 | 23,096 | 400.0 | 46.60% | 0.00% |
| SSE primary | 400 | reactive | 1 | 1,968 | 400.0 | 0.00% | 0.00% |
| buffered | 100 | async | 3 | 2,015 | 100.0 | 0.00% | 0.00% |
| buffered | 100 | reactive | 3 | 2,008 | 100.0 | 0.00% | 0.00% |
| buffered | 200 | async | 3 | 3,300 | 200.0 | 0.00% | 0.00% |
| buffered | 200 | reactive | 3 | 1,976 | 200.0 | 0.00% | 0.00% |
| buffered | 400 | async | 3 | 22,308 | 400.0 | 45.00% | 0.00% |
| buffered | 400 | reactive | 3 | 1,969 | 400.0 | 0.00% | 0.00% |

### 4.5 Live Qwen prompt length

The live-backend probe is a low-rate integration and prompt-sensitivity measurement. Short and medium prompts complete cleanly; the long prompt substantially increases TTFT and produces timeout errors on both stacks.

| Prompt | Stack | n | Prompt chars | Approx input tokens | TTFT p99 ms | E2E p50 ms | E2E p99 ms | Errors |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| short | async | 2 | 70 | 18 | 186 | 435 | 453 | 0.00% |
| short | reactive | 2 | 70 | 18 | 191 | 440 | 463 | 0.00% |
| medium | async | 2 | 310 | 78 | 877 | 1,268 | 1,793 | 0.00% |
| medium | reactive | 2 | 310 | 78 | 884 | 1,268 | 1,781 | 0.00% |
| long | async | 1 | 986 | 247 | 29,565 | 30,074 | 41,810 | 79.30% |
| long | reactive | 2 | 986 | 247 | 29,115 | 34,748 | 40,677 | 45.00% |

---

## 5. Maintenance rules

When the test or performance surface changes:

1. Recount `@Test` methods per service and update §1.1. Keep each async/reactive service pair within 5%.
2. Run the unit coverage gate and keep line and branch coverage at or above 0.90 for every module.
3. Recount Cucumber scenarios from source feature files only, run both stacks, and update §1.2.
4. If a benchmark is rerun, replace the affected table with the complete new grid and record its effective methodology in §2 or §4.
5. Never pool rows produced with different topologies, load-tool transports, calibrations, backends, token counts, or prompt variants.
6. Keep failed-request latency separate from successful latency, report dropped arrivals, and use per-run tail latency for confidence and significance analysis.
