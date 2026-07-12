import { test, expect } from './coverage-fixture';
import { expectNoA11yViolations, login, mockEmpty, mockFail, trackPageErrors } from './helpers';

/**
 * People search — debounced query (300 ms, >=2 chars), result rendering, the empty-result
 * state, and the error state. Mirrors the backend search / search_edges features.
 */
test.describe('Search', () => {
  let assertNoPageErrors: () => void;
  test.beforeEach(({ page }) => {
    assertNoPageErrors = trackPageErrors(page);
  });
  test.afterEach(() => assertNoPageErrors());

  test('a debounced query returns matching people', async ({ page }) => {
    await login(page);
    await page.goto('/search');
    await expect(page.getByLabel('Search people')).toBeVisible();
    await expect(page.getByTestId('search-idle')).toBeVisible();

    // Count the outbound search calls to prove debouncing (one request, not per-keystroke).
    let searchCalls = 0;
    page.on('request', (r) => {
      if (r.url().includes('/user-service/users/search/')) {
        searchCalls += 1;
      }
    });

    await page.getByLabel('Search people').pressSequentially('grace', { delay: 40 });
    await expect(page.getByTestId('search-results')).toBeVisible();
    await expect(page.getByTestId('search-results').getByText('grace').first()).toBeVisible();
    expect(searchCalls, 'debounce should collapse keystrokes to one request').toBe(1);

    await expectNoA11yViolations(page, 'search-results');
  });

  test('a query with no matches shows the empty-result state', async ({ page }) => {
    await login(page);
    await page.goto('/search');
    await mockEmpty(page, 'search-users');

    await page.getByLabel('Search people').fill('nobodyxyz');
    await expect(page.getByTestId('search-empty')).toBeVisible();
    await expect(page.getByTestId('search-empty')).toContainText('nobodyxyz');
  });

  test('a single character does not trigger a search', async ({ page }) => {
    await login(page);
    await page.goto('/search');
    let searchCalls = 0;
    page.on('request', (r) => {
      if (r.url().includes('/user-service/users/search/')) {
        searchCalls += 1;
      }
    });
    await page.getByLabel('Search people').fill('g');
    // Give the debounce window time to (not) fire.
    await page.waitForTimeout(600);
    expect(searchCalls).toBe(0);
    await expect(page.getByTestId('search-idle')).toBeVisible();
  });

  test('a failed search shows the error state', async ({ page }) => {
    await login(page);
    await page.goto('/search');
    await mockFail(page, 'search-users', 500);

    await page.getByLabel('Search people').fill('grace');
    await expect(page.getByTestId('search-error')).toBeVisible();
  });
});
