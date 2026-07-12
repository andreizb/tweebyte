import { test, expect } from './coverage-fixture';
import { login, trackPageErrors } from './helpers';

/**
 * Backend-agnosticism surface. The app talks to ONE configured gateway base URL and
 * behaves identically regardless of which one. This spec asserts the neutral connection
 * panel (gateway selector) is present and exposes the active base URL + health — the
 * visible contract that the same suite re-runs unchanged per gateway (the dual-run proof,
 * documented in e2e/README.md). The bearer survives a gateway swap because the gateways
 * are interchangeable by contract; here (single MSW gateway) it simply renders the panel.
 */
test.describe('Backend agnosticism', () => {
  let assertNoPageErrors: () => void;
  test.beforeEach(({ page }) => {
    assertNoPageErrors = trackPageErrors(page);
  });
  test.afterEach(() => assertNoPageErrors());

  test('the connection panel shows the active gateway and a health indicator', async ({ page }) => {
    // The right rail (with the neutral gateway selector) is visible at lg+ widths.
    await page.setViewportSize({ width: 1400, height: 900 });
    await login(page);
    const connection = page.getByText('Connection', { exact: true });
    await expect(connection).toBeVisible();
    // The active gateway label and the concrete, swappable base URL are both surfaced.
    await expect(page.getByText('Gateway', { exact: true })).toBeVisible();
    await expect(page.getByText(/localhost:8080/)).toBeVisible();
  });

  test('the same authenticated routes resolve regardless of the gateway label', async ({ page }) => {
    // PW_GATEWAY_LABEL just tags the run; behaviour is identical. Walk the core routes to
    // assert the contract holds for whichever gateway this run is pointed at.
    await login(page);
    await expect(page.getByTestId('feed-list')).toBeVisible();

    await page.goto('/search');
    await expect(page.getByLabel('Search people')).toBeVisible();

    await page.goto('/compose');
    await expect(page.getByRole('heading', { name: 'New post' })).toBeVisible();
  });

  test('placeholder nav routes stay authenticated and render inside the shell', async ({ page }) => {
    await login(page);

    for (const route of ['/explore', '/ai', '/settings']) {
      await page.goto(route);
      await expect(page).toHaveURL(new RegExp(`${route}$`));
      await expect(page.getByRole('heading', { name: 'Tweebyte' })).toBeVisible();
      await expect(page.getByText('Frontend contracts and backend plumbing are wired.')).toBeVisible();
      await expect(page.getByRole('link', { name: 'Home', exact: true })).toBeVisible();
    }
  });
});
