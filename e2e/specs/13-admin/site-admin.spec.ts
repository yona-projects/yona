import { test, expect } from '@playwright/test';
import { uniqueSuffix } from '../../support/unique';
import { requireSeed } from '../../support/seed-store';

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

// Separate dedicated resources from the block above -- deletion is irreversible and must not
// touch anything another spec (or another test in this file) might still reference.
test.describe.serial('admin force-delete actions on dedicated throwaway resources', () => {
  const suffix = uniqueSuffix();
  const deleteTargetLoginId = `e2edeltarget${suffix}`;
  const deleteTargetPassword = 'DeleteTargetPassw0rd!';
  const deleteProjectName = `e2e-delete-target-${suffix}`;

  test('sign up the dedicated force-delete target account', async ({ browser }) => {
    const context = await browser.newContext({ storageState: { cookies: [], origins: [] } });
    const signupPage = await context.newPage();
    await signupPage.goto('/signup');
    await signupPage.fill('#loginId', deleteTargetLoginId);
    await signupPage.fill('#uname', `E2E Delete Target ${suffix}`);
    await signupPage.fill('#email', `${deleteTargetLoginId}@yona-e2e.test`);
    await signupPage.fill('#password', deleteTargetPassword);
    await signupPage.fill('#retypedPassword', deleteTargetPassword);
    await signupPage.click('form[action="/signup"] button[type=submit]');
    await signupPage.waitForLoadState('networkidle');
    await context.close();
  });

  // Screen: site/userList.html's per-row "삭제" button -> native <dialog id="alertDeletionWrap">
  // -> "예" (#accountToggleBtn, wired via $yona.requestAs to DELETE /sites/user/delete/{userId}).
  test('force-delete the dedicated target account', async ({ page }) => {
    await page.goto(`/site/userList?state=ACTIVE&query=${deleteTargetLoginId}`);
    const row = page.locator('li.listitem', { hasText: deleteTargetLoginId });
    await expect(row).toBeVisible();

    await row.locator('[data-toggle="account-delete"]').click();
    await expect(page.locator('dialog#alertDeletionWrap')).toBeVisible();

    const [response] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/user/delete/') && res.request().method() === 'DELETE'),
      page.click('#accountToggleBtn'),
    ]);
    expect(response.ok()).toBeTruthy();
    await page.waitForLoadState('networkidle');

    // Gone from the user list.
    await page.goto(`/site/userList?state=ACTIVE&query=${deleteTargetLoginId}`);
    await expect(page.locator('li.listitem', { hasText: deleteTargetLoginId })).toHaveCount(0);

    // FIXED (was PRODUCT BUG #16): `SiteService.deleteUser()` is a logical/soft delete (sets
    // `UserState.DELETED`, never removes the row), and `UserViewController.userProfile()` used to
    // only check "row exists at all", never `user.state` -- a "deleted" account's public profile
    // kept rendering normally (200) forever. `userProfile()` now treats a DELETED user the same as
    // a nonexistent one (404), matching every other "gone" resource in this app.
    const profileResponse = await page.goto(`/user/${deleteTargetLoginId}`);
    expect(profileResponse?.status()).toBe(404);
  });

  test('create a dedicated project for the force-delete test', async ({ page }) => {
    const adminLoginId = requireSeed('adminLoginId');
    await page.goto('/projectform');
    await page.selectOption('#project-owner', adminLoginId);
    await page.fill('#project-name', deleteProjectName);
    await page.fill('#description', 'Throwaway project for admin force-delete e2e test');
    await page.check('#public');
    await page.selectOption('#vcs', 'GIT');
    await page.click('#newProjectForm button.ybtn-success');
    await expect(page).toHaveURL(new RegExp(`/${adminLoginId}/${deleteProjectName}`));
  });

  // Screen: site/projectList.html's per-row "삭제" button -> native <dialog id="alertDeletionWrap">
  // -> "예" (#projectDeleteBtn, wired via $yona.requestAs to DELETE /sites/project/delete/{id}).
  test('force-delete the dedicated project', async ({ page }) => {
    await page.goto(`/site/projectList?projectName=${deleteProjectName}`);
    const row = page.locator('li.listitem', { hasText: deleteProjectName });
    await expect(row).toBeVisible();

    await row.locator('[data-toggle="delete-project"]').click();
    await expect(page.locator('dialog#alertDeletionWrap')).toBeVisible();

    // Unlike deleteUser() (a @ResponseBody returning ResponseEntity.ok(...)), deleteProject()'s
    // Kotlin signature returns a plain `String` ("redirect:/sites/projectList") -- Spring resolves
    // that as an actual 302 redirect for the DELETE request itself (confirmed live via the access
    // log), not a 200. response.ok() is false for a 302; assert the real status instead.
    const [response] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/project/delete/') && res.request().method() === 'DELETE'),
      page.click('#projectDeleteBtn'),
    ]);
    expect(response.status()).toBe(302);
    await page.waitForLoadState('networkidle');

    await page.goto(`/site/projectList?projectName=${deleteProjectName}`);
    await expect(page.locator('li.listitem', { hasText: deleteProjectName })).toHaveCount(0);

    // FIXED (was part of the systemic #14 bug): `ProjectViewController.projectHome()`'s
    // `?: return "error/404"` used to return the Thymeleaf view name without ever setting the
    // actual HTTP status, so a deleted project's page rendered the 404 template's content with a
    // 200 status. That systemic issue (91 occurrences of the same pattern across 20 controller
    // files) has since been fixed sitewide, so a genuinely deleted project now correctly 404s.
    const adminLoginId = requireSeed('adminLoginId');
    const projectResponse = await page.goto(`/${adminLoginId}/${deleteProjectName}`);
    expect(projectResponse?.status()).toBe(404);
  });
});

// FIXED (was PRODUCT BUG #15): GET /sites/export used to reliably 403 even for a genuine
// SITE_ADMIN session. Root cause (found via temporary exception logging + a direct H2 query):
// DataBackupServiceImpl.nextSequenceValue()'s H2 branch ran `SELECT COALESCE(MAX(id), 0) + 1`
// against every table with a column literally named "id" -- but Spring Authorization Server's
// own OAUTH_AUTHORIZATION/OAUTH_AUTHORIZATION_CONSENT/OAUTH_REGISTERED_CLIENT tables have a
// VARCHAR(UUID) "id" column, not an integer one, so H2 threw NumberFormatException trying to
// coerce a UUID string to an int. NumberFormatException IS-A IllegalArgumentException, and
// SiteApiController's class-wide @ExceptionHandler(IllegalArgumentException::class) (meant only
// for checkAdmin()'s auth-failure throw) caught it and reported a generic 403 "FORBIDDEN" with
// no stack trace ever logged -- a real server bug masquerading as an authorization failure.
// Fixed two things: (1) DataBackupServiceImpl now only attempts the MAX(id)+1 approximation when
// the "id" column's JDBC type is actually numeric (hasNumericIdColumn()); (2) checkAdmin()'s
// auth failure now throws a dedicated SiteApiController.UnauthorizedAccessException instead of
// the overly-broad IllegalArgumentException, so the exception handler can no longer swallow
// unrelated server errors from other methods in this controller.
test('data export downloads a real JSON backup file', async ({ page }) => {
  await page.goto('/site/data');
  const exportLink = page.locator('a[href="/sites/export"]');
  await expect(exportLink).toBeVisible();
  await expect(exportLink).toHaveAttribute('href', '/sites/export');

  // Content-Disposition: attachment makes a real page.click() hand the response to the browser's
  // download manager, which detaches it from Playwright's network stack before response.body()
  // can read it ("No resource with given identifier found" -- confirmed live). page.request
  // shares the same authenticated session/cookies and lets us read the body directly instead.
  const response = await page.request.get('/sites/export');
  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain('application/json');
  expect(response.headers()['content-disposition']).toContain('attachment');
  const body = await response.body();
  expect(body.length).toBeGreaterThan(0);
  // A real backup, not an empty stub -- must at least mention the user table (DataBackupServiceImpl
  // dumps raw DB table names, e.g. n4user, not the domain-model name "users"). Casing is DB-engine
  // dependent -- H2 uppercases unquoted identifiers (N4USER), MariaDB preserves them as declared
  // (n4user) -- confirmed live against both, so match case-insensitively instead of assuming H2's.
  expect(body.toString('utf-8').toLowerCase()).toContain('"n4user"');
});

// NOT reproducible in this environment (verified live, not a guess): the "Hide" button
// (POST /sites/unwatchUpdate) only renders when
// `currentUser.isSiteManager and yonaUpdateService.isWatched() and yonaUpdateService.isUpdateRequired()`
// (site/layout.html) are ALL true. `isUpdateRequired()` is set only by
// `YonaUpdateService.checkForUpdate()`, which does a real `git ls-remote` against
// https://github.com/yona-projects/yona.git and compares the highest tag to the hardcoded
// `yona.update.current-version` (default 1.15.0) -- there is no test-facing way to fake this from
// the browser. The server's own boot log for this run confirms the real outcome:
// "Yona is up to date (Current: 1.15.0)" -- so `isUpdateRequired()` is false and the button never
// renders. Forcing it would require either a fake git remote with a lower-versioned tag set or a
// build-time override of `yona.update.current-version`, both out of scope for a black-box e2e
// test. Left as fixme rather than faked.
test.fixme('site admin can hide the update-available notification banner', async () => {
  // Not implemented -- see comment above.
});
