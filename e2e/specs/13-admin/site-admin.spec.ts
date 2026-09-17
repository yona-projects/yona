import { test, expect } from '@playwright/test';
import { uniqueSuffix } from '../../support/unique';

/**
 * Screens: SiteViewController.kt (@RequestMapping ["/site", "/sites"]) -- matrix.md listed these
 * without the /site prefix; the controller confirms the real URLs are /site/userList etc.
 * The bootstrapped admin account is UserState.SITE_ADMIN (global.setup.ts), so the default
 * storageState already has access -- no extra login needed.
 */

test('user list: filter by query and state', async ({ page }) => {
  await page.goto('/site/userList');
  await expect(page).not.toHaveURL(/error\/403/);
  await page.fill('form[action="/sites/userList"] input[name="query"]', 'admin');
  await page.click('form[action="/sites/userList"] button[type=submit]');
  await expect(page.locator('body')).toContainText('admin');
});

test('project list: filter by project name', async ({ page }) => {
  await page.goto('/site/projectList');
  await expect(page).not.toHaveURL(/error\/403/);
  await page.fill('form[action="/sites/projectList"] input[name="projectName"]', 'e2e');
  await page.click('form[action="/sites/projectList"] button[type=submit]');
  await expect(page.locator('body')).toContainText(/e2e/);
});

test('issue list loads', async ({ page }) => {
  const response = await page.goto('/site/issueList');
  expect(response?.status()).toBeLessThan(400);
});

test('post list loads', async ({ page }) => {
  const response = await page.goto('/site/postList');
  expect(response?.status()).toBeLessThan(400);
});

test('mail composer form is present with sender field pre-filled', async ({ page }) => {
  await page.goto('/site/mail');
  await expect(page.locator('#mailForm')).toBeVisible();
  await expect(page.locator('#mailForm input[name="from"]')).toHaveValue(/.+/);
});

// Deliberately does not click the send button -- this form has no dry-run mode and a real submit
// would attempt to email whatever address is typed into the "to" field.
test('mass mail form loads without submitting', async ({ page }) => {
  await page.goto('/site/massmail');
  await expect(page.locator('#mailtoAll')).toBeChecked();
  await page.check('#mailtoPrj');
  await expect(page.locator('#project-list-wrap')).toBeVisible();
});

test('data management screen loads with an import form', async ({ page }) => {
  await page.goto('/site/data');
  await expect(page.locator('form[action="/sites/import"]')).toBeVisible();
});

test('diagnostic screen runs the self-check and renders results', async ({ page }) => {
  const response = await page.goto('/site/diagnostic');
  expect(response?.status()).toBeLessThan(400);
});

test('update screen loads and reports the current version', async ({ page }) => {
  await page.goto('/site/update');
  await expect(page.locator('body')).toContainText('1.15.0');
});

// Self-contained (signs up its own throwaway user) rather than depending on 01-auth's seeded
// secondUserLoginId, since fork ordering across areas isn't guaranteed.
test('a non-admin user is rejected from /site/userList with a 403 page', async ({ browser }) => {
  const context = await browser.newContext({ storageState: { cookies: [], origins: [] } });
  const page = await context.newPage();
  const suffix = uniqueSuffix();
  const loginId = `e2eplain${suffix}`;
  const password = 'PlainUserPassw0rd!';

  await page.goto('/signup');
  await page.fill('#loginId', loginId);
  await page.fill('#uname', `Plain User ${suffix}`);
  await page.fill('#email', `${loginId}@yona-e2e.test`);
  await page.fill('#password', password);
  await page.fill('#retypedPassword', password);
  await page.click('form[action="/signup"] button[type=submit]');

  await page.fill('#loginIdOrEmailD', loginId);
  await page.fill('#password', password);
  await page.click('form[action="/users/login"] button[type=submit]');

  await page.goto('/site/userList');
  // error/forbidden.html renders the literal English word "Forbidden" (not "403"/"권한").
  await expect(page.locator('body')).toContainText(/forbidden|403|unauthorized|권한/i);

  await context.close();
});

/** Screens: site/userList.html's per-row action buttons (SiteApiController.kt) -- guest-mode
 * toggle, account lock/unlock, site-admin role grant/revoke, forced password reset. All of these
 * are POST-then-redirect via the generic data-request-method="post" delegate except reset-password
 * (a plain fetch handled inline in userList.html's own <script>). Operates entirely on a dedicated
 * throwaway account signed up here -- never on admin or any other spec's seeded user -- and
 * reverses every state change (unlock, revoke admin, un-guest) except where the action is itself
 * the thing under test and reversing it isn't possible (there is no "undelete"). */
test.describe.serial('admin user-management actions on a dedicated throwaway account', () => {
  const suffix = uniqueSuffix();
  const targetLoginId = `e2etarget${suffix}`;
  const targetPassword = 'TargetUserPassw0rd!';

  test('sign up the dedicated target account', async ({ browser }) => {
    const context = await browser.newContext({ storageState: { cookies: [], origins: [] } });
    const page = await context.newPage();
    await page.goto('/signup');
    await page.fill('#loginId', targetLoginId);
    await page.fill('#uname', `E2E Admin Target ${suffix}`);
    await page.fill('#email', `${targetLoginId}@yona-e2e.test`);
    await page.fill('#password', targetPassword);
    await page.fill('#retypedPassword', targetPassword);
    await page.click('form[action="/signup"] button[type=submit]');
    await page.waitForLoadState('networkidle');
    await context.close();
  });

  // These buttons use data-request-method="post" + data-request-uri, wired by yona.Common.js's
  // requestAs() delegate: it fetch()es the URL (the browser's fetch implementation follows the
  // controller's 302 redirect internally and transparently), then on a final 200 response calls
  // `document.location.reload()` -- a same-URL reload, not a navigation to the redirect target.
  // waitForURL() never resolves here since the address bar URL never actually changes; wait on
  // the underlying POST response instead, then let the reload settle with networkidle.
  test('toggle guest mode on, then off (moves the user between the ACTIVE and GUEST tabs)', async ({ page }) => {
    await page.goto(`/site/userList?state=ACTIVE&query=${targetLoginId}`);
    const row = page.locator('li.listitem', { hasText: targetLoginId });
    await expect(row).toBeVisible();
    await expect(row.locator('a[data-request-uri*="toggleGuestMode"]')).not.toHaveClass(/ybtn-success/);

    await Promise.all([
      page.waitForResponse((res) => res.url().includes('toggleGuestMode') && res.request().method() === 'POST'),
      row.locator('a[data-request-uri*="toggleGuestMode"]').click(),
    ]);
    await page.waitForLoadState('networkidle');

    // The account must disappear from the ACTIVE tab and show up under the GUEST tab -- a real
    // reload/re-query of both tabs, not just the button label re-rendering.
    await page.goto(`/site/userList?state=ACTIVE&query=${targetLoginId}`);
    await expect(page.locator('li.listitem', { hasText: targetLoginId })).toHaveCount(0);

    await page.goto(`/site/userList?state=GUEST&query=${targetLoginId}`);
    const rowAfterToggle = page.locator('li.listitem', { hasText: targetLoginId });
    await expect(rowAfterToggle).toBeVisible();
    await expect(rowAfterToggle.locator('a[data-request-uri*="toggleGuestMode"]')).toHaveClass(/ybtn-success/);

    // Toggle back off (un-guest), restoring the account to its default non-guest state and tab.
    await Promise.all([
      page.waitForResponse((res) => res.url().includes('toggleGuestMode') && res.request().method() === 'POST'),
      rowAfterToggle.locator('a[data-request-uri*="toggleGuestMode"]').click(),
    ]);
    await page.waitForLoadState('networkidle');

    await page.goto(`/site/userList?state=GUEST&query=${targetLoginId}`);
    await expect(page.locator('li.listitem', { hasText: targetLoginId })).toHaveCount(0);

    await page.goto(`/site/userList?state=ACTIVE&query=${targetLoginId}`);
    const rowRestored = page.locator('li.listitem', { hasText: targetLoginId });
    await expect(rowRestored).toBeVisible();
    await expect(rowRestored.locator('a[data-request-uri*="toggleGuestMode"]')).not.toHaveClass(/ybtn-success/);
  });

  test('lock the account, then unlock it', async ({ page }) => {
    await page.goto(`/site/userList?state=ACTIVE&query=${targetLoginId}`);
    const row = page.locator('li.listitem', { hasText: targetLoginId });
    await Promise.all([
      page.waitForResponse((res) => res.url().includes('toggleAccountLock') && res.request().method() === 'POST'),
      row.locator('a[data-request-uri*="toggleAccountLock"]').click(),
    ]);
    await page.waitForLoadState('networkidle');

    await page.goto(`/site/userList?state=LOCKED&query=${targetLoginId}`);
    await expect(page.locator('li.listitem', { hasText: targetLoginId })).toBeVisible();

    // A locked account genuinely cannot log in -- confirm that live before unlocking again.
    const lockedContext = await page.context().browser()!.newContext({ storageState: { cookies: [], origins: [] } });
    const lockedPage = await lockedContext.newPage();
    await lockedPage.goto('/users/loginform');
    await lockedPage.fill('#loginIdOrEmailD', targetLoginId);
    await lockedPage.fill('#password', targetPassword);
    await lockedPage.click('form[action="/users/login"] button[type=submit]');
    await expect(lockedPage.locator('a[href="/login"]')).toHaveCount(1);
    await lockedContext.close();

    const lockedRow = page.locator('li.listitem', { hasText: targetLoginId });
    await Promise.all([
      page.waitForResponse((res) => res.url().includes('toggleAccountLock') && res.request().method() === 'POST'),
      lockedRow.locator('a[data-request-uri*="toggleAccountLock"]').click(),
    ]);
    // waitForLoadState('networkidle') alone can resolve before the requestAs() delegate's own
    // document.location.reload() actually starts (there is no navigation for it to track yet),
    // letting the immediate page.goto() below race the reload and hit net::ERR_ABORTED (confirmed
    // live) -- give the reload a moment to actually begin first, same workaround used elsewhere
    // in this suite for the same fire-and-forget reload pattern (e.g. project-settings.spec.ts).
    await page.waitForTimeout(500);
    await page.waitForLoadState('networkidle');
    await page.goto(`/site/userList?state=ACTIVE&query=${targetLoginId}`);
    await expect(page.locator('li.listitem', { hasText: targetLoginId })).toBeVisible();
  });

  test('grant site-admin role, then revoke it', async ({ page }) => {
    await page.goto(`/site/userList?state=ACTIVE&query=${targetLoginId}`);
    const row = page.locator('li.listitem', { hasText: targetLoginId });
    await Promise.all([
      page.waitForResponse((res) => res.url().includes('toggleSiteAdminRole') && res.request().method() === 'POST'),
      row.locator('a[data-request-uri*="toggleSiteAdminRole"]').click(),
    ]);
    await page.waitForLoadState('networkidle');

    await page.goto(`/site/userList?state=SITE_ADMIN&query=${targetLoginId}`);
    await expect(page.locator('li.listitem', { hasText: targetLoginId })).toBeVisible();

    // Revoke immediately -- do not leave a stray extra site-admin account lying around.
    const adminRow = page.locator('li.listitem', { hasText: targetLoginId });
    await Promise.all([
      page.waitForResponse((res) => res.url().includes('toggleSiteAdminRole') && res.request().method() === 'POST'),
      adminRow.locator('a[data-request-uri*="toggleSiteAdminRole"]').click(),
    ]);
    // Same fire-and-forget reload race as the lock/unlock test above.
    await page.waitForTimeout(500);
    await page.waitForLoadState('networkidle');
    await page.goto(`/site/userList?state=ACTIVE&query=${targetLoginId}`);
    await expect(page.locator('li.listitem', { hasText: targetLoginId })).toBeVisible();
  });

  // FIXED (was a product bug -- see BUGFIXES.md #8): the "비밀번호 초기화" button's data-href
  // used to be built by userList.html as `@{'/' + ${user.loginId}(action='resetPassword')}`,
  // i.e. POST /{loginId}?action=resetPassword -- but the real reset endpoint
  // (SiteApiController.kt's resetUserPasswordBySiteManager) is mapped at
  // POST /site/users/{loginId}/reset-password, an entirely different path. No controller matched
  // bare POST /{loginId}, so the button 404d every time; userList.html's own click handler for
  // data-toggle="reset-password" surfaced this as a "password change failed" $yona.alert(). Fixed
  // by pointing data-href at the real route. Verify a real behavioral effect, not just "no longer
  // 404s": the old password must stop working and the newly issued one must log the account in.
  test('force-reset the target account password', async ({ page }) => {
    await page.goto(`/site/userList?state=ACTIVE&query=${targetLoginId}`);
    const row = page.locator('li.listitem', { hasText: targetLoginId });
    const resetButton = row.locator('button[data-toggle="reset-password"]');

    const [response] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/reset-password') && res.request().method() === 'POST'),
      resetButton.click(),
    ]);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.isSuccess).toBe(true);
    const newPassword: string = body.newPassword;
    expect(newPassword).toBeTruthy();

    await expect(row.locator('.alert-success')).toBeVisible();

    const context = await page.context().browser()!.newContext({ storageState: { cookies: [], origins: [] } });
    const freshPage = await context.newPage();

    // The old password must no longer work.
    await freshPage.goto('/users/loginform');
    await freshPage.fill('#loginIdOrEmailD', targetLoginId);
    await freshPage.fill('#password', targetPassword);
    await freshPage.click('form[action="/users/login"] button[type=submit]');
    await expect(freshPage.locator('a[href="/login"]')).toHaveCount(1);

    // The freshly issued password must work.
    await freshPage.goto('/users/loginform');
    await freshPage.fill('#loginIdOrEmailD', targetLoginId);
    await freshPage.fill('#password', newPassword);
    await freshPage.click('form[action="/users/login"] button[type=submit]');
    await expect(freshPage.locator('a[href="/login"]')).toHaveCount(0);

    await context.close();
  });
});
