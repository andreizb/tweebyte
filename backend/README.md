# Backend

The backend implements one microservice API twice:

- `async/` uses Spring Web, JPA/Hibernate, blocking clients, and bounded executors.
- `reactive/` uses Spring WebFlux, R2DBC, Reactor, and non-blocking clients.

Each stack contains `gateway-service`, `user-service`, `tweet-service`, and `interaction-service`. Ports, data ownership, inter-service flow, security, profiles, and cross-stack invariants belong in [`../ARCHITECTURE.md`](../ARCHITECTURE.md).

## Module layout

Service modules follow the same package structure under `ro.tweebyte.<service>`:

```text
controller -> service -> repository
```

Supporting packages contain entities, DTOs, MapStruct mappers, configuration, exceptions, inter-service clients, and utilities. Database schemas are owned by service-local Flyway migrations under `src/main/resources/db/migration/`; application code validates or consumes those schemas rather than creating an alternative schema path.

## Build and test

Run Maven from an individual module because the repository has no root reactor POM:

```bash
cd backend/async/user-service
./mvnw verify

cd ../../reactive/tweet-service
./mvnw test

# One test class or method.
./mvnw test -Dtest=TweetControllerTests
./mvnw test -Dtest=TweetControllerTests#testName
```

Use JDK 21. From the repository root, the unit coverage gate across all eight modules is:

```bash
./testing/functional-equivalence/coverage-gate.sh unit
```

## Configuration

Base `application.properties` is the production/default configuration. `application-benchmark.properties` is the benchmark overlay; there is intentionally no `application-prod.properties`. Topology, secrets, and per-run tuning may come from environment variables, while feature enablement is profile-owned.

Start services through the repository dispatcher so URLs, databases, caches, Vault, TLS, and profiles are wired consistently:

```bash
./run.sh runtime up async prod
./run.sh runtime up reactive benchmark
./run.sh local up async benchmark all
```

See [`../deployment/README.md`](../deployment/README.md) for runtime commands, [`../testing/README.md`](../testing/README.md) for test surfaces, [`../SECURITY.md`](../SECURITY.md) for the security boundary, and [`../AGENTS.md`](../AGENTS.md) for coding-agent guardrails.
