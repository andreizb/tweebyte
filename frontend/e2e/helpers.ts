import { expect, Locator, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * Shared e2e helpers for the Tweebyte suite (MSW mock backend).
 *
 * Everything here is gateway-relative — the suite is base-URL parameterized (see
 * playwright.config.ts) so it re-runs unchanged per configured gateway as the
 * backend-agnosticism proof. See e2e/README.md for the dual-run recipe.
 */

export const SEED_EMAIL = 'ada@tweebyte.dev';
export const SEED_PASSWORD = 'whatever'; // MSW issues a token for any password.
export const ME_ID = '00000000-0000-4000-8000-000000000001';

/** Type credentials through the login form and wait until Angular accepts the controls. */
export async function typeLoginCredentials(
  page: Page,
  email = SEED_EMAIL,
  password = SEED_PASSWORD
): Promise<void> {
  const emailInput = page.locator('input[formcontrolname="email"]');
  const passwordInput = page.locator('input[formcontrolname="password"]');
  await emailInput.click();
  await emailInput.fill('');
  await emailInput.pressSequentially(email);
  await passwordInput.click();
  await passwordInput.fill('');
  await passwordInput.pressSequentially(password);
  await expect(emailInput).toHaveValue(email);
  await expect(passwordInput).toHaveValue(password);
}

/** Log in as the seed user and land on /home. */
export async function login(page: Page, email = SEED_EMAIL): Promise<void> {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Sign in to Tweebyte' })).toBeVisible();
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(SEED_PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.waitForURL('**/home');
  await expect(page.getByTestId('feed-list')).toBeVisible();
}

/**
 * Compose a fresh post from the home feed's inline composer and return the locator for the
 * resulting top card. A fresh post has zero interaction counts and is NOT in the seeded
 * viewer-state (not pre-liked / pre-retweeted), so optimistic ON→rollback transitions are
 * deterministic. Must be called while on /home with the composer visible.
 */
export async function composeFreshPost(page: Page, note = 'fresh post'): Promise<Locator> {
  const text = `${note} ${Date.now()} — deterministic e2e card`;
  const box = page.getByLabel('Compose a new post');
  await box.fill(text);
  await box
    .locator('xpath=ancestor::div[contains(@class,"flex")][1]')
    .getByRole('button', { name: 'Post' })
    .click();
  const card = page.getByTestId('feed-list').locator('li').first();
  await expect(card).toContainText(text);
  return card;
}

/** Assert WCAG 2.0 A/AA cleanliness on the current screen. */
export async function expectNoA11yViolations(page: Page, context: string): Promise<void> {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
  expect(
    results.violations,
    `${context} a11y violations: ${JSON.stringify(results.violations.map((v) => v.id))}`
  ).toEqual([]);
}

/**
 * The scenario keys exposed by `window.__tbMock` (see src/mocks/scenario.ts). These let
 * specs drive error/empty/latency states that the MSW Service Worker would otherwise mask
 * from Playwright's network layer.
 */
export type ScenarioKey =
  | 'feed'
  | 'compose'
  | 'reply'
  | 'trends'
  | 'recommendations'
  | 'like'
  | 'retweet'
  | 'follow'
  | 'followed-ids'
  | 'health'
  | 'search-users'
  | 'search-tweets'
  | 'summaries'
  | 'viewer-likes'
  | 'viewer-retweets'
  | 'profile'
  | 'profile-tweets'
  | 'profile-interactions'
  | 'profile-update'
  | 'login'
  | 'register'
  | 'media';

type FailStatus = 0 | 400 | 401 | 403 | 404 | 415 | 429 | 500 | 503;

/** Optional response-body overrides for a forced failure (mirrors scenario.FailKind). */
interface FailBody {
  /** Emit `{ error: <this> }`. */
  error?: string;
  /** Emit the GlobalExceptionHandler shape `{ errors: [...] }` instead. */
  errors?: string[];
  /** Emit a JSON string response body. */
  text?: string;
}

/**
 * Wait until the MSW scenario hook is installed on `window`. It is set during the app's
 * APP_INITIALIZER (after the Service Worker starts), which can land a moment after the
 * navigation's `load` event — so any driver that runs right after `page.goto` must wait
 * for it, or the override would be a silent no-op.
 */
async function waitForHook(page: Page): Promise<void> {
  await page.waitForFunction(
    () => typeof (window as unknown as { __tbMock?: unknown }).__tbMock !== 'undefined',
    undefined,
    { timeout: 10_000 }
  );
}

/**
 * Force a group of MSW endpoints to fail. Optionally fail only the next N calls (`times`)
 * and/or override the response body (`body.error` for `{ error }`, `body.errors` for the
 * GlobalExceptionHandler `{ errors: [...] }` shape).
 */
export async function mockFail(
  page: Page,
  key: ScenarioKey,
  status: FailStatus,
  times?: number,
  body?: FailBody
): Promise<void> {
  await waitForHook(page);
  await page.evaluate(
    ([k, s, t, b]) => {
      const hook = (window as unknown as { __tbMock?: Record<string, unknown> }).__tbMock as
        | {
            fail: (
              key: string,
              kind: { status: number; error?: string; errors?: string[]; text?: string },
              times?: number
            ) => void;
          }
        | undefined;
      const override = b as { error?: string; errors?: string[]; text?: string } | undefined;
      hook?.fail(
        k as string,
        { status: s as number, error: override?.error, errors: override?.errors, text: override?.text },
        t as number | undefined
      );
    },
    [key, status, times, body] as const
  );
}

/** Force a group of MSW reads to return an empty collection. */
export async function mockEmpty(page: Page, key: ScenarioKey): Promise<void> {
  await waitForHook(page);
  await page.evaluate((k) => {
    const hook = (window as unknown as { __tbMock?: Record<string, unknown> }).__tbMock as
      | { empty: (key: string) => void }
      | undefined;
    hook?.empty(k);
  }, key);
}

/** Force a group of MSW reads to return literal JSON null. */
export async function mockNull(page: Page, key: ScenarioKey): Promise<void> {
  await waitForHook(page);
  await page.evaluate((k) => {
    const hook = (window as unknown as { __tbMock?: Record<string, unknown> }).__tbMock as
      | { nullBody: (key: string) => void }
      | undefined;
    hook?.nullBody(k);
  }, key);
}

/** Add artificial latency (ms) to a group of MSW endpoints. */
export async function mockSlow(page: Page, key: ScenarioKey, ms: number): Promise<void> {
  await waitForHook(page);
  await page.evaluate(
    ([k, m]) => {
      const hook = (window as unknown as { __tbMock?: Record<string, unknown> }).__tbMock as
        | { slow: (key: string, ms: number) => void }
        | undefined;
      hook?.slow(k as string, m as number);
    },
    [key, ms] as const
  );
}

/** Clear one or all MSW scenario overrides. */
export async function mockReset(page: Page, key?: ScenarioKey): Promise<void> {
  await waitForHook(page);
  await page.evaluate((k) => {
    const hook = (window as unknown as { __tbMock?: Record<string, unknown> }).__tbMock as
      | { reset: (key?: string) => void }
      | undefined;
    hook?.reset(k);
  }, key);
}

/**
 * Install a fresh page-error collector. Returns an assertion to run at test end so any
 * uncaught client error fails the test (mirrors golden-path.spec.ts).
 */
export function trackPageErrors(page: Page): () => void {
  const errors: Error[] = [];
  page.on('pageerror', (err) => errors.push(err));
  return () =>
    expect(errors, `uncaught client errors: ${errors.map((e) => e.message).join('; ')}`).toEqual(
      []
    );
}
