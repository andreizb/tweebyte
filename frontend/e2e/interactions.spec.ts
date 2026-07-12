import { test, expect } from './coverage-fixture';
import { composeFreshPost, login, mockFail, mockNull, trackPageErrors } from './helpers';

/**
 * Interactions on a TweetCard — like, retweet (incl. toggle-off / DELETE path), and reply.
 * Each is optimistic: the UI flips instantly and rolls back on error. Mirrors the backend
 * likes / retweets / replies interaction features.
 */
test.describe('Interactions', () => {
  let assertNoPageErrors: () => void;
  test.beforeEach(({ page }) => {
    assertNoPageErrors = trackPageErrors(page);
  });
  test.afterEach(() => assertNoPageErrors());

  test('like flips aria-pressed and ticks the count, then toggles back off', async ({ page }) => {
    await login(page);
    // Compose a fresh post (0 counts) so the +1/-1 count delta is exact and not masked by
    // the compact-number formatting that collapses, e.g., 1820 and 1821 both to "1.8K".
    const text = `fresh post for like counting ${Date.now()}`;
    await page.getByLabel('Compose a new post').fill(text);
    await page
      .getByLabel('Compose a new post')
      .locator('xpath=ancestor::div[contains(@class,"flex")][1]')
      .getByRole('button', { name: 'Post' })
      .click();

    const card = page.getByTestId('feed-list').locator('li').first();
    await expect(card).toContainText(text);
    const like = card.getByRole('button', { name: 'Like' });
    const count = () => like.locator('span.tabular-nums');

    // The compact-number pipe renders 0 as an empty string (X-style hide-zero).
    await expect(count()).toHaveText('');
    await like.click();
    await expect(like).toHaveAttribute('aria-pressed', 'true');
    await expect(count()).toHaveText('1'); // optimistic +1

    await like.click();
    await expect(like).toHaveAttribute('aria-pressed', 'false');
    await expect(count()).toHaveText('');
  });

  test('a failed like rolls the optimistic flip back and toasts', async ({ page }) => {
    await login(page);
    const card = page.getByTestId('feed-list').locator('li').first();
    const like = card.getByRole('button', { name: 'Like' });

    await mockFail(page, 'like', 500);
    await like.click();
    // Rolls back to not-pressed after the request fails.
    await expect(like).toHaveAttribute('aria-pressed', 'false');
    await expect(page.getByRole('status').filter({ hasText: /could not update like/i })).toBeVisible();
  });

  test('retweet flips on, then the toggle-off exercises the DELETE path', async ({ page }) => {
    await login(page);
    const card = page.getByTestId('feed-list').locator('li').first();
    const rt = card.getByRole('button', { name: 'Retweet' });

    await rt.click();
    await expect(rt).toHaveAttribute('aria-pressed', 'true');
    await rt.click();
    await expect(rt).toHaveAttribute('aria-pressed', 'false');
  });

  test('a retweet create response without an id still leaves the button on', async ({ page }) => {
    await login(page);
    const card = await composeFreshPost(page, 'retweet missing-id create probe');
    const rt = card.getByRole('button', { name: 'Retweet' });

    await mockNull(page, 'retweet');
    await rt.click();

    await expect(rt).toHaveAttribute('aria-pressed', 'true');
  });

  test('a server-seeded retweet without an id can be toggled off locally', async ({ page }) => {
    await page.goto('/login?__null=viewer-retweets');
    await page.getByLabel('Email').fill('ada@tweebyte.dev');
    await page.getByLabel('Password').fill('whatever');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await page.waitForURL('**/home');

    const card = page.getByTestId('feed-list').locator('li').first();
    const rt = card.getByRole('button', { name: 'Retweet' });
    await expect(rt).toHaveAttribute('aria-pressed', 'true');

    await rt.click();

    await expect(rt).toHaveAttribute('aria-pressed', 'false');
  });

  test('a failed retweet removal restores the known retweet id state', async ({ page }) => {
    await login(page);
    const card = await composeFreshPost(page, 'retweet delete rollback probe');
    const rt = card.getByRole('button', { name: 'Retweet' });

    await rt.click();
    await expect(rt).toHaveAttribute('aria-pressed', 'true');

    await mockFail(page, 'retweet', 500);
    await rt.click();

    await expect(rt).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByRole('status').filter({ hasText: /could not remove repost/i })).toBeVisible();
  });

  test('a failed retweet rolls back and toasts', async ({ page }) => {
    await login(page);
    const card = page.getByTestId('feed-list').locator('li').first();
    const rt = card.getByRole('button', { name: 'Retweet' });

    await mockFail(page, 'retweet', 500);
    await rt.click();
    await expect(rt).toHaveAttribute('aria-pressed', 'false');
    await expect(page.getByRole('status').filter({ hasText: /could not repost/i })).toBeVisible();
  });

  test('reply from the detail thread inserts optimistically and bumps the reply count', async ({
    page
  }) => {
    await login(page);
    // Compose a fresh post (0 replies) so the reply-count delta is exact and deterministic.
    const text = `fresh post for reply counting ${Date.now()}`;
    await page.getByLabel('Compose a new post').fill(text);
    await page
      .getByLabel('Compose a new post')
      .locator('xpath=ancestor::div[contains(@class,"flex")][1]')
      .getByRole('button', { name: 'Post' })
      .click();

    const card = page.getByTestId('feed-list').locator('li').first();
    await expect(card).toContainText(text);
    await card.locator('a[href^="/tweet/"]').first().click();
    await page.waitForURL('**/tweet/**');
    // Fresh tweet → empty thread to start.
    await expect(page.getByTestId('replies-empty')).toBeVisible();

    const reply = `a thoughtful e2e reply ${Date.now()}`;
    await page.getByLabel('Post your reply').fill(reply);
    await page.getByRole('button', { name: 'Reply' }).click();

    await expect(page.getByTestId('replies-list')).toContainText(reply);
    await expect(page.getByRole('status').filter({ hasText: 'Reply posted' })).toBeVisible();
    // The focal card's reply count bumped from 0 to 1.
    await expect(
      page.locator('article').first().getByRole('link', { name: 'Reply' }).locator('span.tabular-nums')
    ).toHaveText('1');
  });

  test('a failed reply does not insert and surfaces an error toast', async ({ page }) => {
    await login(page);
    const card = page.getByTestId('feed-list').locator('li').first();
    await card.locator('a[href^="/tweet/"]').first().click();
    await page.waitForURL('**/tweet/**');

    await mockFail(page, 'reply', 500);
    const reply = `this reply will fail ${Date.now()}`;
    await page.getByLabel('Post your reply').fill(reply);
    await page.getByRole('button', { name: 'Reply' }).click();

    await expect(page.getByRole('status').filter({ hasText: /could not post reply/i })).toBeVisible();
    await expect(page.getByText(reply)).toHaveCount(0);
  });
});
