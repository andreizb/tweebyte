import { test, expect } from './coverage-fixture';
import { composeFreshPost, login, mockFail, trackPageErrors } from './helpers';

/**
 * Cross-cutting error handling via the HTTP error interceptor:
 *   429 → "Slow down" rate-limit toast
 *   5xx (idempotent) → retried with backoff, then a "Something went wrong" toast
 *   network error (status 0) → "Network error" toast
 * Mirrors the gateway / interaction_edges error expectations.
 *
 * These act on a FRESHLY composed post (not pre-liked / not pre-retweeted by the seeded
 * viewer-state), so the optimistic ON→rollback transition is deterministic.
 */
test.describe('Error & rate-limit states', () => {
  let assertNoPageErrors: () => void;
  test.beforeEach(({ page }) => {
    assertNoPageErrors = trackPageErrors(page);
  });
  test.afterEach(() => assertNoPageErrors());

  test('a 429 surfaces the rate-limit toast', async ({ page }) => {
    await login(page);
    const card = await composeFreshPost(page, 'rate-limit probe');
    const like = card.getByRole('button', { name: 'Like' });
    await expect(like).toHaveAttribute('aria-pressed', 'false');

    await mockFail(page, 'like', 429);
    await like.click();

    await expect(page.getByRole('status').filter({ hasText: 'Slow down' })).toBeVisible();
    // The optimistic like also rolled back on the non-2xx response.
    await expect(like).toHaveAttribute('aria-pressed', 'false');
  });

  test('a 429 with no message uses the default rate-limit detail', async ({ page }) => {
    await login(page);
    const card = await composeFreshPost(page, 'rate-limit fallback probe');
    const like = card.getByRole('button', { name: 'Like' });

    await mockFail(page, 'like', 429, undefined, { errors: [] });
    await like.click();

    const toast = page.getByRole('status').filter({ hasText: 'Slow down' });
    await expect(toast).toBeVisible();
    await expect(toast).toContainText('Rate limit hit');
  });

  test('a 5xx on an idempotent call is retried with backoff, then surfaces the server-error toast', async ({
    page
  }) => {
    await login(page);
    // Like a fresh post so it is ON; toggling OFF issues a DELETE (idempotent → retried).
    const card = await composeFreshPost(page, 'retry probe');
    const like = card.getByRole('button', { name: 'Like' });
    await like.click();
    await expect(like).toHaveAttribute('aria-pressed', 'true');

    // Count the DELETE attempts: the interceptor retries 5xx twice (3 attempts total).
    let attempts = 0;
    page.on('request', (r) => {
      if (r.url().includes('/interaction-service/likes/') && r.method() === 'DELETE') {
        attempts += 1;
      }
    });

    await mockFail(page, 'like', 503);
    await like.click(); // toggle off → DELETE

    await expect(page.getByRole('status').filter({ hasText: /could not update like/i })).toBeVisible({
      timeout: 15_000
    });
    expect(attempts, 'interceptor should retry 5xx DELETE (1 + 2 retries)').toBeGreaterThanOrEqual(3);
    // Rolled back to liked after the DELETE ultimately failed.
    await expect(like).toHaveAttribute('aria-pressed', 'true');
  });

  test('a 5xx on a non-idempotent POST surfaces immediately without retrying', async ({ page }) => {
    await login(page);
    const card = await composeFreshPost(page, 'no-retry probe');
    const retweet = card.getByRole('button', { name: 'Retweet' });

    // A retweet create is a POST — NOT retried (a retry could duplicate the write).
    let attempts = 0;
    page.on('request', (r) => {
      if (r.url().includes('/interaction-service/retweets/') && r.method() === 'POST') {
        attempts += 1;
      }
    });

    await mockFail(page, 'retweet', 503);
    await retweet.click();

    await expect(page.getByRole('status').filter({ hasText: /could not repost/i })).toBeVisible();
    expect(attempts, 'a POST must not be retried by the interceptor').toBe(1);
    await expect(retweet).toHaveAttribute('aria-pressed', 'false');
  });

  test('a 5xx with no message uses the default server-error detail', async ({ page }) => {
    await login(page);
    const card = await composeFreshPost(page, 'server fallback probe');
    const retweet = card.getByRole('button', { name: 'Retweet' });

    await mockFail(page, 'retweet', 503, undefined, { errors: [] });
    await retweet.click();

    const toast = page.getByRole('status').filter({ hasText: 'Something went wrong' });
    await expect(toast).toBeVisible();
    await expect(toast).toContainText('The server had a problem.');
  });

  test('a string error body is surfaced as the detail message', async ({ page }) => {
    await login(page);
    await page.goto('/profile/00000000-0000-4000-8000-000000000002');
    const header = page.getByRole('main');

    await mockFail(page, 'follow', 403, undefined, { text: 'plain forbidden' });
    await header.getByRole('button', { name: 'Follow' }).click();

    const toast = page.getByRole('status').filter({ hasText: 'Not allowed' });
    await expect(toast).toBeVisible();
    await expect(toast).toContainText('plain forbidden');
  });

  test('an empty string error body falls back to the default detail message', async ({ page }) => {
    await login(page);
    await page.goto('/profile/00000000-0000-4000-8000-000000000002');
    const header = page.getByRole('main');

    await mockFail(page, 'follow', 403, undefined, { text: '' });
    await header.getByRole('button', { name: 'Follow' }).click();

    const toast = page.getByRole('status').filter({ hasText: 'Not allowed' });
    await expect(toast).toBeVisible();
    await expect(toast).toContainText('You can only modify your own content.');
  });

  test('an overlong string error body falls back to the default detail message', async ({
    page
  }) => {
    await login(page);
    await page.goto('/profile/00000000-0000-4000-8000-000000000002');
    const header = page.getByRole('main');

    await mockFail(page, 'follow', 403, undefined, { text: 'x'.repeat(250) });
    await header.getByRole('button', { name: 'Follow' }).click();

    const toast = page.getByRole('status').filter({ hasText: 'Not allowed' });
    await expect(toast).toBeVisible();
    await expect(toast).toContainText('You can only modify your own content.');
  });

  test('a network error surfaces the network toast', async ({ page }) => {
    await login(page);
    const card = page.getByTestId('feed-list').locator('li').first();

    await mockFail(page, 'like', 0); // status 0 → network error
    await card.getByRole('button', { name: 'Like' }).click();

    await expect(page.getByRole('status').filter({ hasText: 'Network error' })).toBeVisible({
      timeout: 15_000
    });
  });

  test('toasts can be dismissed', async ({ page }) => {
    await login(page);
    const card = page.getByTestId('feed-list').locator('li').first();
    await mockFail(page, 'like', 429);
    await card.getByRole('button', { name: 'Like' }).click();

    const toast = page.getByRole('status').filter({ hasText: 'Slow down' });
    await expect(toast).toBeVisible();
    await toast.getByRole('button', { name: 'Dismiss notification' }).click();
    await expect(toast).toBeHidden();
  });
});
