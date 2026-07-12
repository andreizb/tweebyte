import { test, expect } from './coverage-fixture';
import {
  expectNoA11yViolations,
  login,
  ME_ID,
  mockFail,
  SEED_EMAIL,
  SEED_PASSWORD,
  trackPageErrors,
  typeLoginCredentials
} from './helpers';

/**
 * Auth flows — login, register (multipart + avatar pre-upload + validation), and the
 * 401 → /login redirect. Mirrors the backend login / signup / signup_validation features
 * against the MSW mock backend.
 */
test.describe('Auth', () => {
  let assertNoPageErrors: () => void;
  test.beforeEach(({ page }) => {
    assertNoPageErrors = trackPageErrors(page);
  });
  test.afterEach(() => assertNoPageErrors());

  test('login happy path lands on a populated home feed', async ({ page }) => {
    await login(page);
    await expect(page).toHaveURL(/\/home$/);
    await expect(page.getByTestId('feed-list').locator('li')).not.toHaveCount(0);
  });

  test('logging out clears the session and returns to the sign-in screen', async ({ page }) => {
    await login(page);

    await page.getByRole('button', { name: 'Log out' }).click();

    await page.waitForURL('**/login');
    await expect(page.getByRole('heading', { name: 'Sign in to Tweebyte' })).toBeVisible();

    await page.goto('/home');
    await page.waitForURL('**/login**');
    await expect(page).toHaveURL(/returnUrl=%2Fhome/);
  });

  test('login screen is accessible', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Sign in to Tweebyte' })).toBeVisible();
    await expectNoA11yViolations(page, 'login');
  });

  test('login with bad credentials shows an inline error and stays on /login', async ({ page }) => {
    await page.goto('/login');
    // Force the auth endpoint to reject so the inline 401 path renders.
    await page.goto('/login?__fail=login:401');
    await page.getByLabel('Email').fill(SEED_EMAIL);
    await page.getByLabel('Password').fill('wrong-password');
    await page.getByRole('button', { name: 'Sign in' }).click();

    const error = page.getByRole('alert');
    await expect(error).toBeVisible();
    await expect(error).toContainText(/wrong email or password/i);
    await expect(page).toHaveURL(/\/login/);
  });

  test('the sign-in button is disabled until the form is valid', async ({ page }) => {
    await page.goto('/login');
    const submit = page.getByRole('button', { name: 'Sign in' });
    await expect(submit).toBeDisabled();
    await page.getByLabel('Email').fill('not-an-email');
    await expect(submit).toBeDisabled(); // email validator blocks it
    await page.getByLabel('Email').fill(SEED_EMAIL);
    await page.getByLabel('Password').fill(SEED_PASSWORD);
    await expect(submit).toBeEnabled();
  });

  test('register page is accessible and links back to sign in', async ({ page }) => {
    await page.goto('/register');
    await expect(page.getByRole('heading', { name: 'Create your account' })).toBeVisible();
    await expectNoA11yViolations(page, 'register');
    await page.getByRole('link', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/login/);
  });

  test('register with a pre-uploaded avatar posts multipart and lands on home', async ({ page }) => {
    await page.goto('/register');

    // Capture the two outbound calls: avatar pre-upload (/media), then register.
    const mediaReq = page.waitForRequest(
      (r) => r.url().includes('/user-service/media') && r.method() === 'POST'
    );
    const registerReq = page.waitForRequest(
      (r) => r.url().includes('/user-service/auth/register') && r.method() === 'POST'
    );

    // Set an avatar via the hidden file input (the register page pre-uploads it).
    await page.setInputFiles('input[type="file"]', {
      name: 'avatar.png',
      mimeType: 'image/png',
      buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
    });
    await expect(page.getByAltText('Avatar preview')).toBeVisible();

    await page.locator('input[formcontrolname="userName"]').fill('newbie');
    await page.getByLabel('Email').fill('newbie@tweebyte.dev');
    await page.getByLabel('Password').fill('supersecret');
    // birthDate is @NotNull on the backend, so the form requires it before submit.
    await page.locator('input[formcontrolname="birthDate"]').fill('1990-01-01');
    await page.getByRole('button', { name: 'Create account' }).click();

    const media = await mediaReq;
    expect(media.headers()['content-type']).toContain('multipart/form-data');
    const register = await registerReq;
    expect(register.headers()['content-type']).toContain('multipart/form-data');

    await page.waitForURL('**/home');
    await expect(page.getByTestId('feed-list')).toBeVisible();
  });

  test('register without an avatar skips the post-signup media attach', async ({ page }) => {
    await page.goto('/register');

    await page.locator('input[formcontrolname="userName"]').fill('plainnew');
    await page.getByLabel('Email').fill('plainnew@tweebyte.dev');
    await page.getByLabel('Password').fill('supersecret');
    await page.locator('input[formcontrolname="birthDate"]').fill('1990-01-01');
    await page.getByRole('button', { name: 'Create account' }).click();

    await page.waitForURL('**/home');
    await expect(page.getByTestId('feed-list')).toBeVisible();
  });

  test('register keeps the avatar optional when the post-signup media attach fails', async ({
    page
  }) => {
    await page.goto('/register?__fail=media:500');

    await page.setInputFiles('input[type="file"]', {
      name: 'avatar.png',
      mimeType: 'image/png',
      buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
    });
    await page.locator('input[formcontrolname="userName"]').fill('mediafail');
    await page.getByLabel('Email').fill('mediafail@tweebyte.dev');
    await page.getByLabel('Password').fill('supersecret');
    await page.locator('input[formcontrolname="birthDate"]').fill('1990-01-01');
    await page.getByRole('button', { name: 'Create account' }).click();

    await page.waitForURL('**/home');
    await expect(page.getByTestId('feed-list')).toBeVisible();
  });

  test('clearing a selected register avatar removes the preview', async ({ page }) => {
    await page.goto('/register');

    await page.setInputFiles('input[type="file"]', {
      name: 'avatar.png',
      mimeType: 'image/png',
      buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
    });
    await expect(page.getByAltText('Avatar preview')).toBeVisible();

    await page.setInputFiles('input[type="file"]', []);

    await expect(page.getByAltText('Avatar preview')).toHaveCount(0);
  });

  test('register validation blocks submit for each signup_validation rule', async ({ page }) => {
    await page.goto('/register');
    const submit = page.getByRole('button', { name: 'Create account' });
    const userName = page.locator('input[formcontrolname="userName"]');
    const email = page.getByLabel('Email');
    const password = page.getByLabel('Password');
    const birthDate = page.locator('input[formcontrolname="birthDate"]');

    // birthDate is @NotNull on the backend (required in the form); fill it up front so the
    // remaining assertions isolate the username/email/password rules.
    await birthDate.fill('1990-01-01');

    // Blank username (backend: 400). Fill valid email+password, blank name → disabled.
    await email.fill('edge@tweebyte.dev');
    await password.fill('12345678');
    await expect(submit).toBeDisabled();

    // Malformed email (backend: 400).
    await userName.fill('edgepw');
    await email.fill('not-an-email');
    await expect(submit).toBeDisabled();

    // Password one short of the 8-char minimum (backend: 400).
    await email.fill('edge@tweebyte.dev');
    await password.fill('1234567');
    await expect(submit).toBeDisabled();

    // A blank birth date also blocks submit (backend @NotNull).
    await password.fill('12345678');
    await birthDate.fill('');
    await expect(submit).toBeDisabled();

    // Boundary: exactly 8 characters + a birth date is valid (backend: 200).
    await birthDate.fill('1990-01-01');
    await expect(submit).toBeEnabled();
  });

  test('register surfaces a server error without leaving the page', async ({ page }) => {
    await page.goto('/register?__fail=register:500');
    await page.locator('input[formcontrolname="userName"]').fill('willfail');
    await page.getByLabel('Email').fill('willfail@tweebyte.dev');
    await page.getByLabel('Password').fill('supersecret');
    await page.locator('input[formcontrolname="birthDate"]').fill('1990-01-01');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/register/);
  });

  test('register rejects an invalid token without leaving the page', async ({ page }) => {
    await page.goto('/register?__token=bad');
    await page.locator('input[formcontrolname="userName"]').fill('badtoken');
    await page.getByLabel('Email').fill('badtoken@tweebyte.dev');
    await page.getByLabel('Password').fill('supersecret');
    await page.locator('input[formcontrolname="birthDate"]').fill('1990-01-01');
    await page.getByRole('button', { name: 'Create account' }).click();

    const error = page.getByRole('alert');
    await expect(error).toBeVisible();
    await expect(error).toContainText('Registration succeeded but the token was invalid.');
    await expect(page).toHaveURL(/\/register/);
  });

  test('a 401 on a protected call clears the session and redirects to /login', async ({ page }) => {
    await login(page);
    // Make the next profile fetch 401; navigating to a profile triggers the interceptor's
    // clear-session + redirect-to-login behaviour.
    await mockFail(page, 'profile', 401);
    await page.getByTestId('feed-list').locator('a[href^="/profile/"]').first().click();

    await page.waitForURL('**/login**');
    await expect(page).toHaveURL(/\/login/);
    // returnUrl is preserved for post-login bounce-back.
    await expect(page).toHaveURL(/returnUrl/);
  });

  test('a direct protected deep link redirects anonymous users to login with returnUrl', async ({
    page
  }) => {
    await page.goto('/home');

    await page.waitForURL('**/login**');
    await expect(page).toHaveURL(/returnUrl=%2Fhome/);
    await expect(page.getByRole('heading', { name: 'Sign in to Tweebyte' })).toBeVisible();
  });

  test('an expired persisted session is discarded before protected navigation', async ({
    page
  }) => {
    await page.addInitScript((userId) => {
      sessionStorage.setItem(
        'tb.session',
        JSON.stringify({
          token: 'expired-token',
          userId,
          username: 'ada',
          expiresAt: Date.now() - 60_000
        })
      );
    }, ME_ID);

    await page.goto('/home');

    await page.waitForURL('**/login**');
    await expect(page).toHaveURL(/returnUrl=%2Fhome/);
  });

  test('signed-in users visiting login are bounced back to home', async ({ page }) => {
    await login(page);

    await page.goto('/login');

    await page.waitForURL('**/home');
    await expect(page.getByTestId('feed-list')).toBeVisible();
  });

  test('a sub-only token can still establish a session', async ({ page }) => {
    await page.goto('/login?__token=sub-only');
    await typeLoginCredentials(page);
    const submit = page.getByRole('button', { name: 'Sign in' });
    await expect(submit).toBeEnabled();
    await submit.click();

    await page.waitForURL('**/home');
    await expect(page.getByTestId('feed-list')).toBeVisible();
  });

  test('a token without exp is accepted as a non-expiring dev session', async ({ page }) => {
    await page.goto('/login?__token=noexp');
    await typeLoginCredentials(page);
    const submit = page.getByRole('button', { name: 'Sign in' });
    await expect(submit).toBeEnabled();
    await submit.click();

    await page.waitForURL('**/home');
    await expect(page.getByTestId('feed-list')).toBeVisible();
  });

  test('a token with no owner claim is rejected inline', async ({ page }) => {
    await page.goto('/login?__token=nouser');
    await typeLoginCredentials(page);
    const submit = page.getByRole('button', { name: 'Sign in' });
    await expect(submit).toBeEnabled();
    await submit.click();

    const error = page.getByRole('alert');
    await expect(error).toBeVisible();
    await expect(error).toContainText('Received an invalid token');
    await expect(page).toHaveURL(/\/login/);
  });

  test('a malformed token is rejected inline', async ({ page }) => {
    await page.goto('/login?__token=bad');
    await typeLoginCredentials(page);
    const submit = page.getByRole('button', { name: 'Sign in' });
    await expect(submit).toBeEnabled();
    await submit.click();

    const error = page.getByRole('alert');
    await expect(error).toBeVisible();
    await expect(error).toContainText('Received an invalid token');
    await expect(page).toHaveURL(/\/login/);
  });
});
