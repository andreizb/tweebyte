import { test, expect } from './coverage-fixture';
import { login, mockFail, trackPageErrors } from './helpers';

/**
 * Second wave of edge coverage: the error-interceptor `extractMessage` body-shape arms that
 * the happy `{ error }` / `{ errors:[...] }` failures do not reach (over-long messages, empty
 * `errors`), the silent 400 arm, and the maximally-sparse tweet detail (TweetCard absent-field
 * fallbacks). All via per-test MSW failure injection — no app logic changes.
 */
test.describe('Interceptor body-shape & status arms', () => {
  let assertNoPageErrors: () => void;
  test.beforeEach(({ page }) => {
    assertNoPageErrors = trackPageErrors(page);
  });
  test.afterEach(() => assertNoPageErrors());

  test('a 403 whose errors[0] is over 200 chars falls back to the default detail text', async ({
    page
  }) => {
    await login(page);
    await page.goto('/profile/00000000-0000-4000-8000-000000000002'); // linus, public
    const header = page.getByRole('main');

    // errors[0] longer than the 200-char guard → extractMessage rejects it and the 403 toast
    // uses its built-in default copy instead (exercises the `errors[0].length < 200` false arm).
    await mockFail(page, 'follow', 403, undefined, { errors: ['x'.repeat(250)] });
    await header.getByRole('button', { name: 'Follow' }).click();

    const toast = page.getByRole('status').filter({ hasText: 'Not allowed' });
    await expect(toast).toBeVisible();
    // The default detail, not the (rejected) 250-char body.
    await expect(toast).toContainText('You can only modify your own content.');
  });

  test('a 403 with an empty errors array falls back to the default detail text', async ({
    page
  }) => {
    await login(page);
    await page.goto('/profile/00000000-0000-4000-8000-000000000002');
    const header = page.getByRole('main');

    // errors: [] → errors[0] is undefined → extractMessage returns undefined → default copy
    // (covers the `typeof errors[0] === 'string'` false arm).
    await mockFail(page, 'follow', 403, undefined, { errors: [] });
    await header.getByRole('button', { name: 'Follow' }).click();

    await expect(page.getByRole('status').filter({ hasText: 'Not allowed' })).toBeVisible();
  });

  test('a 400 on follow surfaces NO global toast (interceptor default no-op arm)', async ({
    page
  }) => {
    await login(page);
    await page.goto('/profile/00000000-0000-4000-8000-000000000002');
    const header = page.getByRole('main');

    // 400 is neither 401/403/404/415/429 nor >=500 nor 0 → the interceptor switch default
    // adds no toast. The follow-button still rolls its optimistic flip back on the non-2xx.
    await mockFail(page, 'follow', 400);
    await header.getByRole('button', { name: 'Follow' }).click();

    // Rolled back to Follow, and the interceptor raised no toast of its own.
    await expect(header.getByRole('button', { name: 'Follow' })).toBeVisible();
    await expect(page.getByRole('status')).toHaveCount(0);
  });

  test('the maximally-sparse tweet detail renders the TweetCard absent-field fallbacks', async ({
    page
  }) => {
    await login(page);
    // SPARSE_TWEET is served by the detail route alone. Opening it drives the TweetCard
    // `'unknown'` author + zeroed-count + null-timestamp fallback arms without an unnamed-link
    // feed row (see the seed comment). It also omits id, so Like/Retweet hit their guard arms.
    await page.goto('/tweet/22000000-0000-4000-8000-000000000001');
    await expect(page.getByRole('heading', { name: 'Post' })).toBeVisible();
    const card = page.locator('article').first();
    await expect(card).toBeVisible();
    // Author falls back to the "unknown" handle and the counts render empty (compact 0 → '').
    await expect(card.getByText('@unknown').first()).toBeVisible();
    const like = card.getByRole('button', { name: 'Like' });
    const retweet = card.getByRole('button', { name: 'Retweet' });
    await expect(like).toBeVisible();
    await like.click();
    await retweet.click();
    await expect(page.getByRole('status')).toHaveCount(0);
  });
});
