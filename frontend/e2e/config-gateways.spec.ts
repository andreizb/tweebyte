import { test, expect } from './coverage-fixture';
import { Page } from '@playwright/test';
import { login, trackPageErrors } from './helpers';

/**
 * Runtime-config + multi-gateway selector coverage. The app fetches /assets/config.json at
 * bootstrap via a bare fetch (NOT through MSW), so Playwright's page.route can serve an
 * alternate config per test — exercising the config-loader normalize arms (malformed input)
 * and the GatewayRegistry / GatewaySelector multi-gateway paths (radiogroup, active-switch,
 * persistence) that a single-gateway config never reaches.
 *
 * Every served config keeps `useMock:true` so MSW still starts and the app is self-contained;
 * the gateway baseUrls all point at the mock origin, which MSW intercepts regardless.
 */

interface ConfigResponse {
  status?: number;
  contentType?: string;
  body?: string;
}

/** Serve a config.json for the next navigation (must be set before page.goto). */
async function serveConfig(page: Page, config: unknown, response: ConfigResponse = {}): Promise<void> {
  await page.route('**/assets/config.json', (route) =>
    route.fulfill({
      status: response.status ?? 200,
      contentType: response.contentType ?? 'application/json',
      body: response.body ?? JSON.stringify(config)
    })
  );
}

const TWO_GATEWAYS = {
  activeGatewayId: 'alpha',
  useMock: true,
  healthPollMs: 0, // single health check (no polling) — exercises the no-poll arm
  showLatency: true,
  gateways: [
    { id: 'alpha', label: 'Alpha', baseUrl: 'http://localhost:8080' },
    { id: 'beta', label: 'Beta', baseUrl: 'http://localhost:8080' }
  ]
};

test.describe('Runtime config & multi-gateway selector', () => {
  let assertNoPageErrors: () => void;
  test.beforeEach(({ page }) => {
    assertNoPageErrors = trackPageErrors(page);
  });
  test.afterEach(() => assertNoPageErrors());

  test('two configured gateways render a radiogroup and switching the active one toasts', async ({
    page
  }) => {
    await serveConfig(page, TWO_GATEWAYS);
    await page.setViewportSize({ width: 1400, height: 900 });
    await login(page);

    const group = page.getByRole('radiogroup', { name: 'Active gateway' });
    await expect(group).toBeVisible();
    const alpha = group.getByRole('radio', { name: /Alpha/ });
    const beta = group.getByRole('radio', { name: /Beta/ });
    await expect(alpha).toHaveAttribute('aria-checked', 'true');
    await expect(beta).toHaveAttribute('aria-checked', 'false');

    // Switch → the registry sets the new active id, persists it, and an info toast fires.
    await beta.click();
    await expect(beta).toHaveAttribute('aria-checked', 'true');
    await expect(page.getByRole('status').filter({ hasText: 'Connected to Beta' })).toBeVisible();

    // Clicking the already-active gateway is a no-op (early return; no second toast).
    await beta.click();
    await expect(beta).toHaveAttribute('aria-checked', 'true');
  });

  test('the active gateway selection survives a reload (sessionStorage persistence)', async ({
    page
  }) => {
    await serveConfig(page, TWO_GATEWAYS);
    await page.setViewportSize({ width: 1400, height: 900 });
    await login(page);

    const group = page.getByRole('radiogroup', { name: 'Active gateway' });
    await group.getByRole('radio', { name: /Beta/ }).click();
    await expect(group.getByRole('radio', { name: /Beta/ })).toHaveAttribute('aria-checked', 'true');

    // Reload: the persisted active id (Beta) is read back from sessionStorage.
    await page.reload();
    const reloaded = page.getByRole('radiogroup', { name: 'Active gateway' });
    await expect(reloaded.getByRole('radio', { name: /Beta/ })).toHaveAttribute(
      'aria-checked',
      'true'
    );
  });

  test('a malformed config (gateways not an array, bad scalars) still boots via defaults', async ({
    page
  }) => {
    // gateways is not an array → normalizeGateways falls back to the default gateway. The bad
    // scalar types (healthPollMs/showLatency/useMock/activeGatewayId) all fall back too. Keep
    // useMock truthy-as-boolean false here would disable MSW, so force it true.
    await serveConfig(page, {
      activeGatewayId: 12345,
      useMock: true,
      healthPollMs: 'soon',
      showLatency: 'yes',
      gateways: 'not-an-array'
    });
    await page.goto('/login');
    // App still boots to a usable login screen despite the malformed config.
    await expect(page.getByRole('heading', { name: 'Sign in to Tweebyte' })).toBeVisible();
  });

  test('partial gateway entries get defaulted ids/labels and still work', async ({ page }) => {
    // One entry missing id+label (gets gateway-1 / "Gateway 1"), one with an empty-string id
    // (also defaulted), one same-origin empty baseUrl (valid). activeGatewayId references a
    // non-existent id → falls back to the first gateway.
    await serveConfig(page, {
      activeGatewayId: 'nope',
      useMock: true,
      healthPollMs: 5000,
      showLatency: false,
      gateways: [
        { baseUrl: 'http://localhost:8080' },
        { id: '', label: '', baseUrl: 'http://localhost:8080' },
        { id: 'same', label: 'Same Origin', baseUrl: '' }
      ]
    });
    await page.setViewportSize({ width: 1400, height: 900 });
    await login(page);

    // Three gateways → radiogroup with three radios; the first is active (bad activeGatewayId).
    const group = page.getByRole('radiogroup', { name: 'Active gateway' });
    await expect(group.getByRole('radio')).toHaveCount(3);
    await expect(group.getByRole('radio').first()).toHaveAttribute('aria-checked', 'true');
    // showLatency:false → the round-trip badge is hidden.
    await expect(page.getByTitle('Last request round-trip')).toHaveCount(0);
  });

  test('an empty gateways array falls back to the single default gateway', async ({ page }) => {
    await serveConfig(page, { useMock: true, gateways: [] });
    await login(page);
    // Single (default) gateway → no radiogroup, just the single-gateway connection row.
    await expect(page.getByRole('radiogroup', { name: 'Active gateway' })).toHaveCount(0);
    await expect(page.getByText('Connection', { exact: true })).toBeVisible();
  });

  test('a non-OK config response falls back to defaults and still boots', async ({ page }) => {
    await serveConfig(page, {}, { status: 500, contentType: 'text/plain', body: 'nope' });

    await page.goto('/login');

    await expect(page.getByRole('heading', { name: 'Sign in to Tweebyte' })).toBeVisible();
  });

  test('malformed config JSON falls back to defaults and still boots', async ({ page }) => {
    await serveConfig(page, {}, { body: '{ bad json' });

    await page.goto('/login');

    await expect(page.getByRole('heading', { name: 'Sign in to Tweebyte' })).toBeVisible();
  });

  test('a null config uses the default mock gateway', async ({ page }) => {
    await serveConfig(page, null);

    await login(page);

    await expect(page.getByTestId('feed-list')).toBeVisible();
    await expect(page.getByText('Connection', { exact: true })).toBeVisible();
  });
});
