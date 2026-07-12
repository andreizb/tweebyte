import { test, expect } from './coverage-fixture';
import {
  expectNoA11yViolations,
  login,
  mockFail,
  mockReset,
  trackPageErrors,
  typeLoginCredentials
} from './helpers';

/**
 * Home timeline — render of TweetCard fields, infinite-scroll pagination, the empty-feed
 * state, and the load-error → retry path. Mirrors the backend feed_and_aggregates feature.
 */
test.describe('Feed', () => {
  let assertNoPageErrors: () => void;
  test.beforeEach(({ page }) => {
    assertNoPageErrors = trackPageErrors(page);
  });
  test.afterEach(() => assertNoPageErrors());

  test('home timeline renders TweetCard fields (author, handle, content, action bar)', async ({
    page
  }) => {
    await login(page);
    const firstCard = page.getByTestId('feed-list').locator('li').first();
    await expect(firstCard).toBeVisible();

    // Author display name + @handle.
    await expect(firstCard.locator('a[href^="/profile/"]').first()).toBeVisible();
    await expect(firstCard.getByText(/^@/).first()).toBeVisible();
    // Content links to the tweet detail.
    await expect(firstCard.locator('a[href^="/tweet/"]').first()).toBeVisible();
    // The three interactive action-bar controls + a relative timestamp.
    await expect(firstCard.getByRole('button', { name: 'Like' })).toBeVisible();
    await expect(firstCard.getByRole('button', { name: 'Retweet' })).toBeVisible();
    await expect(firstCard.getByRole('link', { name: 'Reply' })).toBeVisible();

    await expectNoA11yViolations(page, 'feed');
  });

  test('feed shows seeded content from across the cohort', async ({ page }) => {
    await login(page);
    const feed = page.getByTestId('feed-list');
    // A couple of the seeded posts, to prove the list is materialized (not a skeleton).
    await expect(feed).toContainText('Backpressure');
    await expect(feed.locator('li').count()).resolves.toBeGreaterThan(1);
  });

  test('scrolling to the bottom pages in more and then reports "all caught up"', async ({
    page
  }) => {
    await login(page);
    const feed = page.getByTestId('feed-list');
    // The seed cohort spans two pages (27 rows, page size 20), so the first load fills exactly
    // one page and is NOT yet exhausted. Scrolling the last row into view trips the sentinel,
    // which fires feed.store.loadMore → page 2 appends the rest and exhausts the cohort.
    const firstPage = await feed.locator('li').count();
    expect(firstPage).toBe(20);

    await feed.locator('li').last().scrollIntoViewIfNeeded();
    // loadMore appended page 2: more rows now, and the "all caught up" footer renders.
    await expect.poll(() => feed.locator('li').count()).toBeGreaterThan(firstPage);
    await expect(page.getByText("You're all caught up.")).toBeVisible({ timeout: 10_000 });
  });

  test('a failed page-2 load is non-fatal: keeps page 1, no error state, retries on next scroll', async ({
    page
  }) => {
    await login(page);
    const feed = page.getByTestId('feed-list');
    const firstPage = await feed.locator('li').count();
    expect(firstPage).toBe(20);

    // Page 0 already loaded successfully; now make the NEXT feed read (page 1) fail. loadMore's
    // failure arm keeps what we have, stops the spinner, and does NOT mark exhausted/error.
    await mockFail(page, 'feed', 403);
    await feed.locator('li').last().scrollIntoViewIfNeeded();
    // The page-2 spinner clears and the list is unchanged — no global error state.
    await expect(page.getByTestId('feed-loading-more')).toHaveCount(0);
    await expect(feed).toBeVisible();
    expect(await feed.locator('li').count()).toBe(firstPage);

    // Clear the failure and scroll again — the sentinel can fire once more and page 2 loads.
    await mockReset(page, 'feed');
    await feed.locator('li').last().scrollIntoViewIfNeeded();
    await expect.poll(() => feed.locator('li').count()).toBeGreaterThan(firstPage);
    await expect(page.getByText("You're all caught up.")).toBeVisible({ timeout: 10_000 });
  });

  test('empty feed shows the welcome empty-state', async ({ page }) => {
    // Force the feed read to return an empty page for the very first load.
    await page.goto('/login?__empty=feed');
    await typeLoginCredentials(page);
    const submit = page.getByRole('button', { name: 'Sign in' });
    await expect(submit).toBeEnabled();
    await submit.click();
    await page.waitForURL('**/home');

    await expect(page.getByTestId('feed-empty')).toBeVisible();
    await expect(page.getByText('Welcome to Tweebyte')).toBeVisible();
    await expect(page.getByTestId('feed-list')).toHaveCount(0);
    await expectNoA11yViolations(page, 'feed-empty');
  });

  test('a null feed response is treated as an empty timeline', async ({ page }) => {
    await page.goto('/login?__null=feed');
    await typeLoginCredentials(page);
    const submit = page.getByRole('button', { name: 'Sign in' });
    await expect(submit).toBeEnabled();
    await submit.click();
    await page.waitForURL('**/home');

    await expect(page.getByTestId('feed-empty')).toBeVisible();
    await expect(page.getByText('Welcome to Tweebyte')).toBeVisible();
  });

  test('a failed feed load shows the error state and recovers on Try again', async ({ page }) => {
    await login(page);
    await page.goto('/search');
    // Fail the next feed LOAD's 3 attempts (the error interceptor retries 5xx twice), so
    // the error state surfaces. The counter is then exhausted, so Try-again succeeds.
    await mockFail(page, 'feed', 500, 3);
    await page.locator('a[href="/home"]').first().click();
    await page.waitForURL('**/home');

    const retry = page.getByRole('button', { name: 'Try again' });
    await expect(retry).toBeVisible({ timeout: 15_000 });
    await retry.click();

    await expect(page.getByTestId('feed-list')).toBeVisible();
    await expect(page.getByTestId('feed-list').locator('li')).not.toHaveCount(0);
  });
});
