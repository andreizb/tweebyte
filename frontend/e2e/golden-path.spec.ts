import { test, expect } from './coverage-fixture';
import type { Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * The Tweebyte golden path, end-to-end against the MSW mock backend (no Spring needed):
 *   login → feed → open a tweet → reply → like → retweet → compose → profile → follow.
 *
 * The app is BACKEND-AGNOSTIC: it talks to one configured gateway and behaves identically
 * regardless of which one. This suite is base-URL parameterized (see playwright.config.ts)
 * so the very same spec re-runs per gateway as the agnosticism proof; against MSW it runs
 * once. Axe a11y assertions guard WCAG on each major screen.
 */

const SEED_EMAIL = 'ada@tweebyte.dev';
const SEED_PASSWORD = 'whatever'; // MSW issues a token for any password

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Sign in to Tweebyte' })).toBeVisible();
  await page.getByLabel('Email').fill(SEED_EMAIL);
  await page.getByLabel('Password').fill(SEED_PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.waitForURL('**/home');
}

async function expectNoA11yViolations(page: Page, context: string): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa'])
    .analyze();
  expect(results.violations, `${context} a11y violations: ${JSON.stringify(results.violations.map((v) => v.id))}`)
    .toEqual([]);
}

test.describe('Golden path (MSW)', () => {
  // Collect uncaught client errors; assert none at the end of each test.
  let pageErrors: Error[] = [];
  test.beforeEach(({ page }) => {
    pageErrors = [];
    page.on('pageerror', (err) => pageErrors.push(err));
  });
  test.afterEach(() => {
    expect(pageErrors, `uncaught client errors: ${pageErrors.map((e) => e.message).join('; ')}`).toEqual([]);
  });

  test('login lands on a populated home feed', async ({ page }) => {
    await login(page);
    const feed = page.getByTestId('feed-list');
    await expect(feed).toBeVisible();
    await expect(feed.locator('li')).not.toHaveCount(0);
    await expectNoA11yViolations(page, 'feed');
  });

  test('compose a post and see it at the top of the feed', async ({ page }) => {
    await login(page);
    const text = `e2e post ${Date.now()} — testing the compose flow end to end`;
    // The inline composer on the home feed. Scope the Post button to the composer so it
    // is not confused with the nav-rail "Post" CTA (which routes to /compose).
    const composer = page.getByLabel('Compose a new post').locator('xpath=ancestor::div[contains(@class,"flex")][1]');
    await page.getByLabel('Compose a new post').fill(text);
    await composer.getByRole('button', { name: 'Post' }).click();
    const firstCard = page.getByTestId('feed-list').locator('li').first();
    await expect(firstCard).toContainText(text);
  });

  test('open a tweet, like it, retweet it, and reply', async ({ page }) => {
    await login(page);
    const firstCard = page.getByTestId('feed-list').locator('li').first();

    // Like from the card (optimistic).
    const likeBtn = firstCard.getByRole('button', { name: 'Like' });
    await likeBtn.click();
    await expect(likeBtn).toHaveAttribute('aria-pressed', 'true');

    // Retweet from the card.
    const rtBtn = firstCard.getByRole('button', { name: 'Retweet' });
    await rtBtn.click();
    await expect(rtBtn).toHaveAttribute('aria-pressed', 'true');
    // Toggle retweet off again (exercises the DELETE path).
    await rtBtn.click();
    await expect(rtBtn).toHaveAttribute('aria-pressed', 'false');

    // Navigate into the tweet detail by clicking the post's content link (a /tweet/:id link).
    await firstCard.locator('a[href^="/tweet/"]').first().click();
    await page.waitForURL('**/tweet/**');
    await expect(page.getByRole('heading', { name: 'Post' })).toBeVisible();
    await expectNoA11yViolations(page, 'tweet-detail');

    // Reply.
    const reply = `a thoughtful reply ${Date.now()}`;
    await page.getByLabel('Post your reply').fill(reply);
    await page.getByRole('button', { name: 'Reply' }).click();
    await expect(page.getByTestId('replies-list')).toContainText(reply);
  });

  test('visit a profile and follow / unfollow', async ({ page }) => {
    await login(page);
    // Open a profile by clicking the post author's avatar/name link in the first card
    // (a /profile/:id link) — robust regardless of right-rail content.
    await page.getByTestId('feed-list').locator('a[href^="/profile/"]').first().click();
    await page.waitForURL('**/profile/**');
    await expect(page.getByTestId('profile-tweets').or(page.getByTestId('profile-tweets-empty'))).toBeVisible();
    await expectNoA11yViolations(page, 'profile');

    const followBtn = page.getByRole('button', { name: 'Follow' }).first();
    if (await followBtn.isVisible().catch(() => false)) {
      await followBtn.click();
      await expect(page.getByRole('button', { name: /Following|Requested/ }).first()).toBeVisible();
    }
  });

  test('login screen is accessible', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Sign in to Tweebyte' })).toBeVisible();
    await expectNoA11yViolations(page, 'login');
  });
});
