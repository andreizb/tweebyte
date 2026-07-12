# Tweebyte frontend e2e

Playwright end-to-end suite that drives the real Angular app against the in-browser **MSW
mock backend** (`assets/config.json` → `useMock: true`). No Spring stack is required.

## Running

```bash
# System Google Chrome (the reliable path — no extra browser downloads):
PW_CHANNEL=chrome npx playwright test

# Just one area:
PW_CHANNEL=chrome npx playwright test e2e/feed.spec.ts

# With the advisory e2e coverage report (see below):
PW_CHANNEL=chrome npm run e2e:coverage
```

The dev server is started automatically by `playwright.config.ts` (`webServer`).

## Layout

| File | Area (mirrors the backend cucumber breadth) |
| --- | --- |
| `golden-path.spec.ts` | the original end-to-end happy path |
| `auth.spec.ts` | login, register (multipart + avatar), signup validation, 401 → /login |
| `feed.spec.ts` | timeline render, infinite-scroll pagination, empty + error states |
| `compose.spec.ts` | optimistic insert, ≥10-char guard, error rollback, /compose route |
| `interactions.spec.ts` | like / retweet / reply optimistic flip + count, rollback, toggle-off |
| `tweet-detail.spec.ts` | thread render, not-found, empty-thread, reply guard |
| `profile.spec.ts` | view, edit (multipart), follow / unfollow, private follow-request |
| `search.spec.ts` | debounced people search, empty + error states |
| `error-states.spec.ts` | 429 toast, 5xx retry + toast, network-error toast, dismiss |
| `agnosticism.spec.ts` | the neutral gateway/connection panel (dual-run surface) |
| `helpers.ts` | login, Axe a11y assert, MSW scenario driver, page-error guard |

`helpers.ts` keeps everything gateway-relative; `expectNoA11yViolations` guards WCAG 2.0
A/AA (zero violations) on every key screen.

## Driving error / empty states (the MSW scenario hook)

MSW runs as a Service Worker, so Playwright's `page.route` can't intercept the calls it
fulfils. Instead `src/mocks/scenario.ts` exposes `window.__tbMock`, and `helpers.ts` wraps
it as `mockFail` / `mockEmpty` / `mockSlow` / `mockReset`. For a failure that must be
active on the **first** request after a navigation (before any `page.evaluate` runs), use
the URL params the mock hydrates on boot:

```
/login?__fail=feed:500:1     # next 1 feed call fails, then recovers
/login?__empty=feed          # the feed read returns an empty page
```

All of this is test-only plumbing and is tree-shaken out of non-mock production builds
along with the rest of `src/mocks`.

## Dual-run (backend-agnosticism proof)

The app is backend-agnostic: it talks to one configured gateway and behaves identically
regardless of which one. The **same** suite is therefore meant to run once per gateway as
the agnosticism proof — no test changes, only configuration:

```bash
# Gateway A
PW_GATEWAY_LABEL=alpha PW_CHANNEL=chrome npx playwright test

# Gateway B (point assets/config.json's active gateway baseUrl at B, or override per env),
# then re-run with a different label:
PW_GATEWAY_LABEL=beta  PW_CHANNEL=chrome npx playwright test
```

Against the single MSW gateway the suite runs once. To run against live gateways, set
`assets/config.json` `useMock: false` and point the active gateway `baseUrl` at each
interchangeable backend in turn (e.g. the async and reactive stacks behind the same
contract); `PW_GATEWAY_LABEL` tags each run in the report. The base URL / project tagging
lives in `playwright.config.ts` (`PW_BASE_URL`, `PW_PORT`, `PW_GATEWAY_LABEL`).

## Advisory e2e coverage

`npm run e2e:coverage` runs the suite while collecting V8 coverage via Playwright's
`page.coverage` and maps it back to source through sourcemaps with
[`monocart-coverage-reports`](https://github.com/cenfun/monocart-coverage-reports) — **no
build instrumentation**. The HTML + summary land in `coverage-e2e/`.

This number is **advisory only**. E2E is flow-biased and will sit below the unit
suite; Vitest's ≥90% coverage gate remains the load-bearing frontend coverage proof.
The e2e number just shows what the browser flows actually exercise.
