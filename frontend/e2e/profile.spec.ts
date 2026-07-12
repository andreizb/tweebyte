import { test, expect } from './coverage-fixture';
import {
  expectNoA11yViolations,
  login,
  ME_ID,
  mockFail,
  trackPageErrors
} from './helpers';

/**
 * Profile — viewing, editing (multipart PUT + avatar pre-upload), follow / unfollow, and
 * the follow-REQUEST flow for a private account. Mirrors the backend profile / follows
 * features. Seed: `margaret` (id …004) is the private account; `ada` (ME_ID) is the viewer.
 */
const PRIVATE_USER_ID = '00000000-0000-4000-8000-000000000004'; // margaret, is_private: true

test.describe('Profile', () => {
  let assertNoPageErrors: () => void;
  test.beforeEach(({ page }) => {
    assertNoPageErrors = trackPageErrors(page);
  });
  test.afterEach(() => assertNoPageErrors());

  test('viewing another profile shows header, counts and tweets', async ({ page }) => {
    await login(page);
    await page.getByTestId('feed-list').locator('a[href^="/profile/"]').first().click();
    await page.waitForURL('**/profile/**');

    await expect(
      page.getByTestId('profile-tweets').or(page.getByTestId('profile-tweets-empty'))
    ).toBeVisible();
    // Scope the follow-count labels to the profile header (the right rail also renders
    // "Following" inside its who-to-follow buttons, which would make a bare text match
    // ambiguous under strict mode).
    const header = page.getByRole('main');
    await expect(header.getByText('Following').first()).toBeVisible();
    await expect(header.getByText('Followers').first()).toBeVisible();
    await expectNoA11yViolations(page, 'profile');
  });

  test('the profile Back button returns to the previous timeline', async ({ page }) => {
    await login(page);
    await page.getByTestId('feed-list').locator('a[href^="/profile/"]').first().click();
    await page.waitForURL('**/profile/**');

    await page.getByRole('button', { name: 'Back' }).click();

    await page.waitForURL('**/home');
    await expect(page.getByTestId('feed-list')).toBeVisible();
  });

  test('a null profile tweet page renders the empty-tweets state', async ({ page }) => {
    await login(page);
    await page.goto('/profile/00000000-0000-4000-8000-000000000002?__null=profile-tweets');

    await expect(page.getByTestId('profile-tweets-empty')).toBeVisible();
    await expect(page.getByText("@linus hasn't posted yet.")).toBeVisible();
  });

  test('profile interaction rows are optional', async ({ page }) => {
    await login(page);
    await page.goto(
      '/profile/00000000-0000-4000-8000-000000000002?__empty=profile-interactions'
    );

    await expect(page.getByTestId('profile-tweets')).toBeVisible();
    await expect(page.getByRole('main').getByText('Followers').first()).toBeVisible();
  });

  test('follow then unfollow toggles the button on a public account', async ({ page }) => {
    await login(page);
    await page.goto(`/profile/00000000-0000-4000-8000-000000000002`); // linus, public
    // Scope to the profile header (the right-rail "Who to follow" also has Follow buttons).
    const header = page.getByRole('main');

    const follow = header.getByRole('button', { name: 'Follow' });
    await expect(follow).toBeVisible();
    await follow.click();
    // Public account → "Following".
    const following = header.getByRole('button', { name: /Following/ });
    await expect(following).toBeVisible();

    await following.click();
    await expect(header.getByRole('button', { name: 'Follow' })).toBeVisible();
  });

  test('a failed follow rolls the button back to Follow and toasts', async ({ page }) => {
    await login(page);
    await page.goto(`/profile/00000000-0000-4000-8000-000000000002`);
    const header = page.getByRole('main');

    await mockFail(page, 'follow', 500);
    await header.getByRole('button', { name: 'Follow' }).click();

    await expect(header.getByRole('button', { name: 'Follow' })).toBeVisible();
    await expect(page.getByRole('status').filter({ hasText: /could not follow/i })).toBeVisible();
  });

  test('following a PRIVATE account shows Requested (follow-request flow)', async ({ page }) => {
    await login(page);
    await page.goto(`/profile/${PRIVATE_USER_ID}`);
    const header = page.getByRole('main');
    // The private badge is shown on the profile.
    await expect(header.getByText('Private').first()).toBeVisible();

    await header.getByRole('button', { name: 'Follow' }).click();
    // Private account → optimistic "Requested" (request, not an immediate follow).
    await expect(header.getByRole('button', { name: 'Requested' })).toBeVisible();

    // Cancelling the request returns to Follow.
    await header.getByRole('button', { name: 'Requested' }).click();
    await expect(header.getByRole('button', { name: 'Follow' })).toBeVisible();
  });

  test('own profile: edit profile via the multipart dialog updates the header', async ({
    page
  }) => {
    await login(page);
    await page.goto(`/profile/${ME_ID}`); // ada — own profile
    await expect(page.getByRole('button', { name: 'Edit profile' })).toBeVisible();
    // No Follow button in the profile header on your own profile (the rail is separate).
    await expect(page.getByRole('main').getByRole('button', { name: 'Follow' })).toHaveCount(0);

    await page.getByRole('button', { name: 'Edit profile' }).click();
    const dialog = page.getByRole('dialog', { name: 'Edit profile' });
    await expect(dialog).toBeVisible();
    await expectNoA11yViolations(page, 'edit-profile-dialog');

    // Pre-upload an avatar (multipart /media) then save (multipart PUT /users/{id}).
    const mediaReq = page.waitForRequest(
      (r) => r.url().includes('/user-service/media') && r.method() === 'POST'
    );
    const putReq = page.waitForRequest(
      (r) => r.url().includes(`/user-service/users/${ME_ID}`) && r.method() === 'PUT'
    );

    await dialog.locator('input[type="file"]').setInputFiles({
      name: 'new-avatar.png',
      mimeType: 'image/png',
      buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
    });
    const newBio = `Updated via e2e at ${Date.now()}`;
    await dialog.getByLabel('Bio').fill(newBio);
    await dialog.getByRole('button', { name: 'Save' }).click();

    const media = await mediaReq;
    expect(media.headers()['content-type']).toContain('multipart/form-data');
    const put = await putReq;
    expect(put.headers()['content-type']).toContain('multipart/form-data');

    // Dialog closes, success toast, and the updated bio is reflected in the header.
    await expect(dialog).toBeHidden();
    await expect(page.getByRole('status').filter({ hasText: 'Profile updated' })).toBeVisible();
    await expect(page.getByText(newBio)).toBeVisible();
  });

  test('edit profile keeps the dialog open and toasts when the save fails', async ({ page }) => {
    await login(page);
    await page.goto(`/profile/${ME_ID}`);
    await page.getByRole('button', { name: 'Edit profile' }).click();
    const dialog = page.getByRole('dialog', { name: 'Edit profile' });

    await mockFail(page, 'profile-update', 500);
    await dialog.getByLabel('Bio').fill('this save will fail');
    await dialog.getByRole('button', { name: 'Save' }).click();

    await expect(page.getByRole('status').filter({ hasText: /could not save profile/i })).toBeVisible();
    await expect(dialog).toBeVisible(); // stays open so the edit isn't lost
  });

  test('edit profile clears a newly selected avatar preview', async ({ page }) => {
    await login(page);
    await page.goto(`/profile/${ME_ID}`);
    await page.getByRole('button', { name: 'Edit profile' }).click();
    const dialog = page.getByRole('dialog', { name: 'Edit profile' });

    await dialog.locator('input[type="file"]').setInputFiles({
      name: 'new-avatar.png',
      mimeType: 'image/png',
      buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
    });
    await expect(dialog.getByAltText('New avatar preview')).toBeVisible();

    await dialog.locator('input[type="file"]').setInputFiles([]);

    await expect(dialog.getByAltText('New avatar preview')).toHaveCount(0);
  });

  test('edit profile can be dismissed by cancel, close, and backdrop controls', async ({
    page
  }) => {
    await login(page);
    await page.goto(`/profile/${ME_ID}`);
    const open = page.getByRole('button', { name: 'Edit profile' });
    const dialog = page.getByRole('dialog', { name: 'Edit profile' });

    await open.click();
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: 'Cancel' }).click();
    await expect(dialog).toBeHidden();

    await open.click();
    await expect(dialog).toBeVisible();
    await page.getByRole('button', { name: 'Close', exact: true }).click();
    await expect(dialog).toBeHidden();

    await open.click();
    await expect(dialog).toBeVisible();
    await page.mouse.click(10, 10);
    await expect(dialog).toBeHidden();
  });

  test('a minimal own profile opens the edit dialog with an empty bio and closes on Escape', async ({
    page
  }) => {
    await login(page, 'newcomer@tweebyte.dev');
    await page.goto('/profile/00000000-0000-4000-8000-000000000006');
    await page.getByRole('button', { name: 'Edit profile' }).click();
    const dialog = page.getByRole('dialog', { name: 'Edit profile' });

    await expect(dialog.getByLabel('Bio')).toHaveValue('');
    await dialog.getByLabel('Bio').focus();
    await page.keyboard.press('Escape');

    await expect(dialog).toBeHidden();
  });

  test('a profile that does not exist shows the not-found state', async ({ page }) => {
    await login(page);
    await page.goto('/profile/99999999-0000-4000-8000-000000000000');
    await expect(page.getByTestId('profile-notfound')).toBeVisible();
  });
});
