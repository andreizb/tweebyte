import { test, expect } from './coverage-fixture';
import { login, mockFail, trackPageErrors } from './helpers';

/**
 * Compose — optimistic insert at the top of the feed, the >=10-char client-side guard
 * (mirrors the backend tweet_validation minimum), and rollback when the post fails.
 */
test.describe('Compose', () => {
  let assertNoPageErrors: () => void;
  test.beforeEach(({ page }) => {
    assertNoPageErrors = trackPageErrors(page);
  });
  test.afterEach(() => assertNoPageErrors());

  function composer(page: import('@playwright/test').Page) {
    return page
      .getByLabel('Compose a new post')
      .locator('xpath=ancestor::div[contains(@class,"flex")][1]');
  }

  test('a composed post appears at the top of the feed (optimistic insert)', async ({ page }) => {
    await login(page);
    const text = `e2e compose ${Date.now()} — the optimistic insert path`;
    await page.getByLabel('Compose a new post').fill(text);
    await composer(page).getByRole('button', { name: 'Post' }).click();

    const firstCard = page.getByTestId('feed-list').locator('li').first();
    await expect(firstCard).toContainText(text);
    // A success toast confirms the post.
    await expect(page.getByRole('status').filter({ hasText: 'Posted' })).toBeVisible();
  });

  test('the Post button stays disabled below the 10-character minimum', async ({ page }) => {
    await login(page);
    const postBtn = composer(page).getByRole('button', { name: 'Post' });
    const box = page.getByLabel('Compose a new post');

    await expect(postBtn).toBeDisabled(); // empty
    await box.fill('too short'); // 9 chars
    await expect(postBtn).toBeDisabled();
    // The "N more to post" hint is shown while under the minimum.
    await expect(page.getByText(/more to post/)).toBeVisible();

    await box.fill('now this is long enough'); // >= 10
    await expect(postBtn).toBeEnabled();
  });

  test('a compose error rolls back: the post does not stay, an error toast shows', async ({
    page
  }) => {
    await login(page);
    const feed = page.getByTestId('feed-list');
    const before = await feed.locator('li').count();

    await mockFail(page, 'compose', 500);
    const text = `this compose will fail ${Date.now()}`;
    await page.getByLabel('Compose a new post').fill(text);
    await composer(page).getByRole('button', { name: 'Post' }).click();

    // Error toast surfaces; the optimistic insert never happened (post only emits on success).
    await expect(page.getByRole('status').filter({ hasText: 'Could not post' })).toBeVisible();
    await expect(feed.locator('li').first()).not.toContainText(text);
    await expect(feed.locator('li')).toHaveCount(before);
  });

  test('the standalone /compose route posts and returns to home', async ({ page }) => {
    await login(page);
    await page.goto('/compose');
    await expect(page.getByRole('heading', { name: 'New post' })).toBeVisible();

    const text = `posted from the dedicated compose route ${Date.now()}`;
    await page.getByLabel('Compose a new post').fill(text);
    // Scope to the composer's Post button (the nav rail also has a "Post" CTA).
    await page.getByRole('main').getByRole('button', { name: 'Post' }).click();

    await page.waitForURL('**/home');
    await expect(page.getByTestId('feed-list').locator('li').first()).toContainText(text);
  });
});
