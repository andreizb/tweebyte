import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for the Tweebyte golden-path e2e.
 *
 * The app runs fully on the MSW mock backend (assets/config.json `useMock:true`), so the
 * suite needs no live Spring stack. It is served by `ng serve` on the port below.
 *
 * BASE-URL PARAMETERIZED FOR DUAL-RUN: the app is backend-agnostic — the SAME suite is
 * meant to run once per configured gateway as the agnosticism proof. `PW_PROJECT_NAME`
 * just tags the run; the app's behaviour is identical regardless of which gateway URL is
 * active (against MSW it runs once). To run twice in CI, invoke Playwright per gateway
 * (e.g. point assets/config.json at gateway A, run; point at B, run) — no test changes.
 */
const PORT = Number(process.env['PW_PORT'] ?? 4271);
const HOST_URL = process.env['PW_BASE_URL'] ?? `http://localhost:${PORT}`;
const GATEWAY = process.env['PW_GATEWAY_LABEL'] ?? 'mock';
// Use the bundled Playwright Chromium by default; set PW_CHANNEL=chrome to drive a
// system-installed Google Chrome instead (handy where the Chromium download is blocked).
const CHANNEL = process.env['PW_CHANNEL'] || undefined;
// Advisory e2e coverage (monocart V8). Off by default; `npm run e2e:coverage` sets it.
// The setup/teardown clean + generate the report and are themselves no-ops when unset.
const COVERAGE = process.env['E2E_COVERAGE'] === '1';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  workers: 1,
  // Advisory coverage lifecycle (no-op unless E2E_COVERAGE=1).
  globalSetup: COVERAGE ? require.resolve('./e2e/coverage-setup.ts') : undefined,
  globalTeardown: COVERAGE ? require.resolve('./e2e/coverage-teardown.ts') : undefined,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: HOST_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure'
  },
  projects: [
    {
      // One project per gateway. Re-run with a different PW_GATEWAY_LABEL/config to prove
      // identical behaviour across interchangeable backends (the agnosticism guarantee).
      // Default uses the bundled Chromium; PW_CHANNEL=chrome drives system Google Chrome.
      name: `chromium-${GATEWAY}`,
      use: { ...devices['Desktop Chrome'], channel: CHANNEL }
    }
  ],
  webServer: {
    command: `npm run start -- --port ${PORT} --configuration development`,
    url: HOST_URL,
    reuseExistingServer: !process.env['CI'],
    timeout: 180_000,
    stdout: 'pipe',
    stderr: 'pipe'
  }
});
