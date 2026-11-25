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

## Configuration and feature gating

Features are gated by **Spring profile, never by env-var defaults.** There are two distinct uses of `${...}` placeholders, and only the first is allowed:

- **Topology / secrets / tuning (env-injected — keep):** *where* to connect and *what* the per-run values are — `DB_HOST`/`DB_PORT`, `*_SERVICE_URL`, `CACHE_HOST`/`CACHE_PORT`, `SERVER_PORT`, `VAULT_URI`/`VAULT_TOKEN`, `KEYCLOAK_BASE_URI`, `OTLP_TRACES_ENDPOINT`, and the `AI_MOCK_*` / `LIVE_LLM_*` / `GATEWAY_RATE_LIMIT_*` knobs. These are deployment wiring and per-experiment parameters, set by compose / `run.sh`.
- **Feature on/off (profile-gated — NOT env):** whether a prod-grade capability (TLS/mTLS, tracing, resilience filters, access logs, Swagger, security auto-config) is active.

The mechanism: the literal **ON** value lives in base `application.properties` (prod/default runtime; also the base beneath functional-equivalence). The benchmark overlay `application-benchmark.properties` flips it literally **OFF**. Unit tests shadow base via `src/test/resources` and default features off unless leaving one off would drop coverage. So never write `feature.enabled=${SOMETHING:true}` to gate a feature — write `feature.enabled=true` in base and `feature.enabled=false` in the benchmark overlay. Benchmark neutrality comes from the overlay, not from an operator remembering to flip an env var. Both stacks carry the identical knob name and value.

## Media model (user-service)

Media lives entirely in **user-service** as a content-addressed store (`media_assets`, owned by the user-service Flyway baseline migration). An asset's id is `UUID.nameUUIDFromBytes(bytes)` and its `checksum` (CRC32, `BIGINT`, `uq_media_assets_checksum` UNIQUE) is the dedup key, so re-storing identical bytes returns the existing row. `MediaController` is mounted at `/media` (reached through the gateway at `/user-service/media`, JWT-protected; k6 benchmarks hit the service directly on 9091, bypassing the gateway). Endpoints:

- `POST /media` — **pure upload**. Multipart `file`; stores the bytes as-is (`source_media_id` NULL, `access_hash` NULL, content-type from the part; accepts anything). Returns `{id}`. Not a benchmark hot path — this is how product flows and seeders create the originals previews derive from.
- `POST /media/{id}/preview` — **CPU image-preview workload**. Takes only `{id}` + the gate password (no content type from the client). Loads original `{id}`'s bytes and dispatches on the **stored original's** content type (the `derivePreview` switch; `resolveAsset` supplies the asset cache → DB), then runs the unchanged CPU pipeline (toRGB → 3× gaussian blur → sobel → resize 256×256 → JPEG 0.8), content-addresses the result, and stores it as a derived asset with `source_media_id={id}` plus a **bcrypt** `access_hash` of the required password. Returns `{id}` (the preview). bcrypt runs ONLY on the genuine content-addressed INSERT (the store factory fires on a cache+DB miss), so re-previewing the same seeded source is a pure-CPU cache hit after warmup — no bcrypt, no DB write. **Per-content-type dispatch:** only `image/*` has a registered pipeline today; any other stored content type hits the default branch → `UnsupportedMediaTypeException` → **415** (add a `case` + handler method to extend to a new media type). 404 if the source is missing; **500** only when an `image/*` source carries undecodable bytes (`ImageIO.read` → null).
- `POST /media/{id}/reveal` — bcrypt-verifies the password against the preview's `access_hash`, then streams the **stored original** (via `source_media_id`) — the pipeline is one-way, so reveal returns the original, not a reversal. 403 on mismatch, 404 if `{id}` is not a gated preview. Not a benchmark path.
- `GET /media/{id}` — **blocking file-download workload**. Streams the resident bytes through a 64 KiB-chunk / 6.25 MB/s `LockSupport.parkNanos` throttle (the one sanctioned artificial-latency knob). 206 with a `Content-Range`, 404 if absent.
- `GET /media/{id}/exists` → `{exists}`; `DELETE /media/cache` flushes the in-JVM cache (204).

Media is reachable from four reference sources — `users.profile_picture_id` (in-DB FK, `ON DELETE SET NULL`), `tweets.media_ids` (tweet-service), `replies`/`retweets.media_ids` (interaction-service), and the transitive `media_assets.source_media_id` edge (a surviving preview pins its original). `StaleMediaCleanupService` unions all four (plus the permanent all-zeros default-avatar sentinel) before deleting; it fails closed and is disabled under `app.cleanup.enabled=false` in the benchmark profile.

## Libraries and client patterns

- **Java 21**, **Spring Boot 3.3.2** (all services and both gateways)
- **JWT**: tokens are issued by a dev-mode **Keycloak** realm. The gateways validate them against the realm JWKS via Auth0 java-jwt (v4.3.0) + jwks-rsa (v0.22.1, cached `JwkProvider` keyed by `kid`) and ownership-gate at the edge in `JwtTokenValidationFilter`. Services trust tokens on the direct east-west path — there is no server-side JWT filter; where a service needs the caller identity it decodes the `user_id` claim from the bearer (unsigned decode, signing-agnostic).
- **MapStruct** (v1.5.5.Final) for entity↔DTO mapping
- **Lombok** for boilerplate reduction (`@Data`, `@Builder`)
- **Redis** for service caches (followed-id feed fallback in tweet-service; following/followed, recommendations, popular-users, popular-hashtags, user-summary in interaction-service). Containers use `redis:6379`; the host-published port is `localhost:63790`. Under `all-docker`, benchmark interaction-service traffic connects direct to `redis:6379`; host applications under `native-local` or `local-app` use `localhost:63790`; the toxiproxy Redis proxy on `26379` stays defined-but-idle for future fault-injection (opt in by exporting `INTERACTION_CACHE_HOST=toxiproxy INTERACTION_CACHE_PORT=26379`).
- Inter-service communication via per-service client classes (`UserClient`, `InteractionClient`, `TweetClient`). Async uses Spring `RestClient`; reactive uses `WebClient`.
