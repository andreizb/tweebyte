import { test, expect } from './coverage-fixture';
import { expectNoA11yViolations, login, trackPageErrors, typeLoginCredentials } from './helpers';

/**
 * Tweet detail / thread — opening a post, rendering the focal card + replies, the
 * not-found state, and the empty-thread state. Mirrors the backend tweet_crud /
 * replies features.
 */
test.describe('Tweet detail', () => {
  let assertNoPageErrors: () => void;
  test.beforeEach(({ page }) => {
    assertNoPageErrors = trackPageErrors(page);
  });
  test.afterEach(() => assertNoPageErrors());

  test('opening a tweet renders the focal card, the reply composer and the thread', async ({
    page
  }) => {
    await login(page);
    await page.getByTestId('feed-list').locator('a[href^="/tweet/"]').first().click();
    await page.waitForURL('**/tweet/**');

    await expect(page.getByRole('heading', { name: 'Post' })).toBeVisible();
    // Focal tweet rendered as a (non-clickable) card.
    await expect(page.locator('article').first()).toBeVisible();
    // Reply composer present.
    await expect(page.getByLabel('Post your reply')).toBeVisible();
    // Seeded replies render in the thread.
    await expect(page.getByTestId('replies-list')).toBeVisible();

    await expectNoA11yViolations(page, 'tweet-detail');
  });

  test('the detail Back button returns to the previous timeline', async ({ page }) => {
    await login(page);
    await page.getByTestId('feed-list').locator('a[href^="/tweet/"]').first().click();
    await page.waitForURL('**/tweet/**');

    await page.getByRole('button', { name: 'Back' }).click();

    await page.waitForURL('**/home');
    await expect(page.getByTestId('feed-list')).toBeVisible();
  });

  test('a not-found tweet shows the deleted/not-found state', async ({ page }) => {
    await login(page);
    // Navigate straight to a tweet id the mock does not know.
    await page.goto('/tweet/99999999-0000-4000-8000-000000000000');
    await expect(page.getByTestId('detail-notfound')).toBeVisible();
    await expect(page.getByText('Post not found')).toBeVisible();
  });

  test('a tweet whose replies failed to load still renders the focal post', async ({ page }) => {
    await login(page);
    const href = await page
      .getByTestId('feed-list')
      .locator('a[href^="/tweet/"]')
      .first()
      .getAttribute('href');
    // Fail the replies read for this navigation via the URL hook (a hard goto resets any
    // page.evaluate override); the focal tweet still loads independently of its thread.
    await page.goto(`${href}?__fail=reply:500`);

    await expect(page.getByRole('heading', { name: 'Post' })).toBeVisible();
    await expect(page.locator('article').first()).toBeVisible();
    // With no replies loaded, the empty-thread copy is shown.
    await expect(page.getByTestId('replies-empty')).toBeVisible();
  });

  test('a null replies page is treated as an empty thread', async ({ page }) => {
    await login(page);
    const href = await page
      .getByTestId('feed-list')
      .locator('a[href^="/tweet/"]')
      .first()
      .getAttribute('href');

    await page.goto(`${href}?__null=reply`);

    await expect(page.getByRole('heading', { name: 'Post' })).toBeVisible();
    await expect(page.locator('article').first()).toBeVisible();
    await expect(page.getByTestId('replies-empty')).toBeVisible();
  });

  test('the reply button is disabled until at least one character is typed', async ({ page }) => {
    await login(page);
    await page.getByTestId('feed-list').locator('a[href^="/tweet/"]').first().click();
    await page.waitForURL('**/tweet/**');

    const replyBtn = page.getByRole('button', { name: 'Reply' });
    await expect(replyBtn).toBeDisabled();
    await page.getByLabel('Post your reply').fill('hi');
    await expect(replyBtn).toBeEnabled();
  });

  test('a session without preferred_username replies with the fallback handle', async ({
    page
  }) => {
    await page.goto('/login?__token=no-name');
    await typeLoginCredentials(page);
    const submit = page.getByRole('button', { name: 'Sign in' });
    await expect(submit).toBeEnabled();
    await submit.click();
    await page.waitForURL('**/home');

    await page.getByTestId('feed-list').locator('a[href^="/tweet/"]').first().click();
    await page.waitForURL('**/tweet/**');
    await expect(page.getByLabel('Post your reply')).toBeVisible();
    await expect(page.getByRole('img', { name: 'you avatar' })).toBeVisible();

    const reply = `reply without display name ${Date.now()}`;
    await page.getByLabel('Post your reply').fill(reply);
    await page.getByRole('button', { name: 'Reply' }).click();

    await expect(page.getByTestId('replies-list')).toContainText(reply);
  });

  test('a detail tweet with omitted counters renders with zero-count defaults', async ({
    page
  }) => {
    await login(page);
    await page.goto('/tweet/22000000-0000-4000-8000-000000000002');

    const card = page.locator('article').first();
    await expect(card).toContainText('omitted counters');
    await expect(card.getByRole('button', { name: 'Like' })).toHaveAttribute('aria-pressed', 'false');
    await expect(card.getByRole('button', { name: 'Retweet' })).toHaveAttribute('aria-pressed', 'false');
    await expect(page.getByTestId('replies-empty')).toBeVisible();
  });
});
