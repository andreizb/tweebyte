# Tweebyte

Tweebyte is a Twitter-like microblogging system implemented twice behind the same HTTP API: a blocking Spring Web/JPA stack and a non-blocking WebFlux/R2DBC stack. The repository exists to keep those implementations functionally equivalent and compare them under identical workloads.

## Repository map

| Area | Purpose | Entry point |
|---|---|---|
| `backend/` | Async and reactive Java microservices | [`backend/README.md`](backend/README.md) |
| `frontend/` | Angular client, mock backend, and browser tests | [`frontend/README.md`](frontend/README.md) |
| `deployment/` | Docker, local-process, and native-local runtime wiring | [`deployment/README.md`](deployment/README.md) |
| `testing/` | Unit coverage, functional equivalence, and benchmarks | [`testing/README.md`](testing/README.md) |

The system design lives in [`ARCHITECTURE.md`](ARCHITECTURE.md). Recorded verification and benchmark numbers live only in [`testing/RESULTS.md`](testing/RESULTS.md).

## Quick start

Prerequisites for the full stack are JDK 21, Maven 3.9+, and Docker Desktop. k6 and JMeter are needed only for their respective benchmark workloads.

```bash
# Start either complete stack with the normal runtime configuration.
./run.sh runtime up async prod
./run.sh runtime up reactive prod

# Include the Angular frontend.
WITH_FRONTEND=1 ./run.sh runtime up async prod

# Stop without deleting volumes.
./run.sh runtime down async prod

# Run one service's tests.
cd backend/async/tweet-service && mvn test
```

Run `./run.sh --help` for the current command tree. Deployment profiles and local topologies are documented in [`deployment/README.md`](deployment/README.md).

## Documentation responsibilities

| Document | Owns |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | System components, data flow, security design, profiles, and invariants |
| [`SECURITY.md`](SECURITY.md) | Supported line, security boundary, and private vulnerability reporting |
| [`AGENTS.md`](AGENTS.md) | Coding-agent rules, implementation guardrails, benchmark operating knowledge, and pinned versions |
| [`backend/README.md`](backend/README.md) | Backend layout and service-level development commands |
| [`frontend/README.md`](frontend/README.md) | Frontend structure, runtime configuration, and development commands |
| [`deployment/README.md`](deployment/README.md) | Runtime commands, compose overlays, profiles, ports, and topologies |
| [`testing/README.md`](testing/README.md) | Test surfaces and output locations |
| [`testing/performance/README.md`](testing/performance/README.md) | Benchmark engines, profiles, methodology rules, and result cleanup |
| [`testing/RESULTS.md`](testing/RESULTS.md) | Current test status and complete retained measurement tables |

AI coding agents must read [`AGENTS.md`](AGENTS.md) before changing the repository. It is the only agent-specific instruction file.

## License

[MIT](LICENSE).
