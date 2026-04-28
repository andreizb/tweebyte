# Cache Benchmark Demo Cookbook

This is the exact copy-paste flow for the `GET /follows/{id}/following` cache demo, using the modern benchmark CLI.

The demo benchmark shape is:
- workload: `following-cache` (Redis-backed, I/O-bound)
- `1000` concurrent users
- `60s` warmup
- `3m` measured
- `9999` benchmark keys
- `10` follows per key
- `1000` hot keys
- hot ratio `0.9`
- benchmark hits `interaction-service` directly on `9093`
- Redis latency goes through toxiproxy on `26379`
- fresh logs and resources are written under `/Users/andrei/Developer/tweebyte/testing-results/performance/k6/`

Important:
- the magic Maven flag is mandatory: `-Dspring-boot.run.optimizedLaunch=false`
- the magic local JVM heap flags are mandatory: `JAVA_TOOL_OPTIONS="-Xms4g -Xmx4g -XX:+AlwaysPreTouch"`
- Docker is only for infra in this cookbook
- the benchmark runner is now `./run.sh bench k6`
- benchmark payloads are generated under gitignored `testing/performance/k6/payload/`

## Clean Start Tomorrow Morning

```bash
cd /Users/andrei/Developer/tweebyte
./run.sh runtime destroy infra benchmark
```

## Before You Join The Teams Call

Open a terminal in the repo root:

```bash
cd /Users/andrei/Developer/tweebyte
ulimit -n 65536
```

Start database infra, Redis, and toxiproxy:

```bash
./run.sh runtime up infra benchmark
```

Prepare the demo follow-cache dataset once:

```bash
./run.sh prepare k6 following-cache --key-count 9999 --follows-per-key 10
```

Expected key payload:

```text
/Users/andrei/Developer/tweebyte/testing/performance/k6/payload/following-cache/n9999_k10/keys.txt
```

Optional quick sanity check after seeding:

```bash
docker compose --project-directory /Users/andrei/Developer/tweebyte \
  -f /Users/andrei/Developer/tweebyte/infrastructure/compose/infrastructure.yml exec -T interaction_service_db \
  psql -U postgres -d interaction_service_db -c "select count(*) from follows;"
```

Expected result:
- about `99990` rows

## Async Run

Open a new terminal for the async service:

```bash
cd /Users/andrei/Developer/tweebyte
ulimit -n 65536
./run.sh local async interaction-service benchmark
```

Wait until health is up:

```bash
curl http://127.0.0.1:9093/actuator/health
```

Back in the repo-root terminal, clear Redis before the run:

```bash
docker compose --project-directory /Users/andrei/Developer/tweebyte \
  -f /Users/andrei/Developer/tweebyte/infrastructure/compose/infrastructure.yml exec -T redis redis-cli FLUSHALL
```

### Async benchmark without resource sampling

```bash
cd /Users/andrei/Developer/tweebyte
./run.sh bench k6 \
  --workload following-cache \
  --base-url http://127.0.0.1:9093 \
  --actuator-url http://127.0.0.1:9093/actuator/prometheus \
  --concurrencies "1000" \
  --runs 1 \
  --warmup 60s \
  --duration 3m \
  --collect-resources 0 \
  --key-count 9999 \
  --follows-per-key 10 \
  --total-keys 9999 \
  --hot-count 1000 \
  --hot-ratio 0.9
```

### Async benchmark with resource sampling

```bash
cd /Users/andrei/Developer/tweebyte
./run.sh bench k6 \
  --workload following-cache \
  --base-url http://127.0.0.1:9093 \
  --actuator-url http://127.0.0.1:9093/actuator/prometheus \
  --concurrencies "1000" \
  --runs 1 \
  --warmup 60s \
  --duration 3m \
  --collect-resources 1 \
  --sample-every 1 \
  --key-count 9999 \
  --follows-per-key 10 \
  --total-keys 9999 \
  --hot-count 1000 \
  --hot-ratio 0.9
```

This creates a new `/Users/andrei/Developer/tweebyte/testing-results/performance/k6/results_following_cache_*` directory with:
- `1000_1.txt`
- `1000_1_resources.csv` when resource sampling is enabled
- parsed per-run summary and final recap printed directly to the terminal

## Reactive Run

Stop the async service with `Ctrl+C`.

Open a new terminal for the reactive service:

```bash
cd /Users/andrei/Developer/tweebyte
ulimit -n 65536
./run.sh local reactive interaction-service benchmark
```

Wait until health is up:

```bash
curl http://127.0.0.1:9093/actuator/health
```

Back in the repo-root terminal, clear Redis before the reactive run:

```bash
docker compose --project-directory /Users/andrei/Developer/tweebyte \
  -f /Users/andrei/Developer/tweebyte/infrastructure/compose/infrastructure.yml exec -T redis redis-cli FLUSHALL
```

### Reactive benchmark without resource sampling

```bash
cd /Users/andrei/Developer/tweebyte
./run.sh bench k6 \
  --workload following-cache \
  --base-url http://127.0.0.1:9093 \
  --actuator-url http://127.0.0.1:9093/actuator/prometheus \
  --concurrencies "1000" \
  --runs 1 \
  --warmup 60s \
  --duration 3m \
  --collect-resources 0 \
  --key-count 9999 \
  --follows-per-key 10 \
  --total-keys 9999 \
  --hot-count 1000 \
  --hot-ratio 0.9
```

### Reactive benchmark with resource sampling

```bash
cd /Users/andrei/Developer/tweebyte
./run.sh bench k6 \
  --workload following-cache \
  --base-url http://127.0.0.1:9093 \
  --actuator-url http://127.0.0.1:9093/actuator/prometheus \
  --concurrencies "1000" \
  --runs 1 \
  --warmup 60s \
  --duration 3m \
  --collect-resources 1 \
  --sample-every 1 \
  --key-count 9999 \
  --follows-per-key 10 \
  --total-keys 9999 \
  --hot-count 1000 \
  --hot-ratio 0.9
```

## What To Read In The Results

For every run, `run_bench.sh` prints:
- `main_http_reqs.count`
- true main-phase `RPS`
- main avg latency
- main p95 latency
- `http_req_failed`
- avg/max CPU and avg/max heap when resource sampling is enabled

It also prints a final recap table for the whole output directory.

## After The Demo

```bash
cd /Users/andrei/Developer/tweebyte
./run.sh runtime destroy infra benchmark
```

## Last Confirmed Apples-To-Apples Numbers

Reactive:
- `main_http_reqs.count = 8555506`
- true main `RPS = 47530.59`
- avg `20.79 ms`
- p95 `26.52 ms`

Async:
- `main_http_reqs.count = 4556059`
- true main `RPS = 25311.44`
- avg `39.32 ms`
- p95 `46.64 ms`

## Paper Reference Numbers

Reactive paper:
- `45641 req/s`
- avg `28.87 ms`
- p95 `45.39 ms`

Async paper:
- `26724 req/s`
- avg `49.80 ms`
- p95 `84.02 ms`
