# Frontend

Angular 17 client for Tweebyte. It talks to one gateway-relative API contract, so the same bundle works with either backend stack.

System-level frontend decisions are documented in [`../ARCHITECTURE.md`](../ARCHITECTURE.md#frontend). This file owns frontend layout, runtime configuration, and development commands.

## Structure

| Path | Purpose |
|---|---|
| `src/app/core/` | API clients, authentication, runtime configuration, interceptors, and shared state |
| `src/app/features/` | Auth, feed, compose, profile, search, and tweet-detail screens |
| `src/app/layout/` | Application shell, navigation, gateway status, and right rail |
| `src/app/shared/` | Reusable UI components and pipes |
| `src/mocks/` | MSW handlers and deterministic browser-test fixtures |
| `e2e/` | Playwright browser tests; see [`e2e/README.md`](e2e/README.md) |
| `deploy/` | Nginx configuration used by the frontend container |

The app uses standalone Angular components, RxJS for request/stream orchestration, Signals and `@ngrx/signals` for view state, TailwindCSS for styling, Vitest for unit tests, MSW for the in-browser mock API, and Playwright for end-to-end tests.

## Runtime configuration

`src/assets/config.json` is loaded at startup. It selects the active gateway, health-poll interval, latency display, and whether the browser should use MSW:

```json
{
  "activeGatewayId": "gateway",
  "useMock": true,
  "healthPollMs": 10000,
  "showLatency": true,
  "gateways": [
    { "id": "gateway", "label": "Gateway", "baseUrl": "http://localhost:8080" }
  ]
}
```

API services use relative paths; the base-URL interceptor applies the selected gateway. Switching from async to reactive therefore changes configuration, not application code.

Authentication is brokered by user-service. The client stores the returned bearer token in session storage, reads the `user_id` and expiry claims for UI state, attaches the token through an interceptor, and returns to login on `401`. The gateway and services remain authoritative; client-side guards are only UX controls. See [`../SECURITY.md`](../SECURITY.md) for the runtime security boundary.

AI responses are SSE over authenticated `POST` requests. `AiStreamService` consumes `fetch()` response streams with `ReadableStream` and supports cancellation with `AbortController`; browser `EventSource` is not used because it cannot issue the required POST body.

## Development

```bash
cd frontend
npm ci

# Development server using the configured gateway.
npm start

# Development server with the MSW configuration.
npm run start:mock

# Static checks and unit coverage.
npm run lint
npm test

# Production bundle.
npm run build

# Browser tests.
PW_CHANNEL=chrome npm run e2e
```

The full application can also run through the deployment overlay:

```bash
WITH_FRONTEND=1 ./run.sh runtime up async prod
```

Generated `node_modules/`, Angular build output, unit coverage, Playwright reports, and browser coverage are ignored and should not be committed.
