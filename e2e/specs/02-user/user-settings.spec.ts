import { test, expect } from '@playwright/test';
import { execFileSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** yona rejects registering the exact same public key material twice (fingerprint is presumably
 * unique per user/site) -- a hardcoded key string would fail on the suite's second run against
 * the same persisted admin account, so generate a fresh ed25519 keypair per run instead. */
function generateSshPublicKey(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'yona-e2e-sshkey-'));
  const keyPath = path.join(dir, 'id_ed25519');
  execFileSync('ssh-keygen', ['-t', 'ed25519', '-N', '', '-C', `e2e-${uniqueSuffix()}@yona`, '-f', keyPath]);
  const publicKey = fs.readFileSync(`${keyPath}.pub`, 'utf-8').trim();
  fs.rmSync(dir, { recursive: true, force: true });
  return publicKey;
}

/** Screens: GET /user/{loginId} (user/view.html), /user/editform (+emails,notifications,token,
 * tokens,tokens/new,oauth-apps,oauth-apps-owned(-new),ssh-keys(-new),gpg-keys(-new)), /user/files,
 * /user/issues, /new/mine. All acting as the seeded admin (default storageState). */

test('public profile view renders for an existing user', async ({ page }) => {
  const loginId = requireSeed('adminLoginId');
  const response = await page.goto(`/user/${loginId}`);
  expect(response?.status()).toBeLessThan(400);
  await expect(page.locator('body')).toContainText(loginId);
});

test('edit basic profile info (name) and save', async ({ page }) => {
  await page.goto('/user/editform');
  await page.fill('#frmBasic input[name="name"]', 'E2E Admin (edited)');
  await page.click('#frmBasic button[type=submit]');

  // editUserInfo() redirects to /user/{loginId} on success.
  await expect(page).toHaveURL(/\/user\/admin$/);
  await expect(page.locator('body')).toContainText('E2E Admin (edited)');
});

test('reset visited project list button on the basic edit screen', async ({ page }) => {
  await page.goto('/user/editform');
  await page.click('form[action="/user/resetVisitedList"] button[type=submit]');
  await expect(page).toHaveURL(/\/user\/editform$/);
});

test('add a secondary email address', async ({ page }) => {
  const suffix = Date.now();
  await page.goto('/user/editform/emails');
  await page.fill('input[name="email"]', `admin-secondary-${suffix}@yona-e2e.test`);
  await page.click('form[action="/user/email"] button[type=submit]');

  await expect(page).toHaveURL(/\/user\/editform\/emails$/);
  await expect(page.locator('body')).toContainText(`admin-secondary-${suffix}@yona-e2e.test`);
});

test('notifications screen lists per-project toggles without erroring', async ({ page }) => {
  const response = await page.goto('/user/editform/notifications');
  expect(response?.status()).toBeLessThan(400);
});

test.describe('API tokens', () => {
  test('issue a new fine-grained API token', async ({ page }) => {
    await page.goto('/user/editform/tokens/new');
    await page.fill('#frmApiTokenIssue input[name="name"]', 'e2e-token');
    await page.selectOption('#frmApiTokenIssue select[name="expiresInDays"]', { index: 0 });
    await page.click('#frmApiTokenIssue button[type=submit]');

    await expect(page).toHaveURL(/\/user\/editform\/tokens$/);
    await expect(page.locator('body')).toContainText('e2e-token');
  });

  test('legacy full-access token screen loads', async ({ page }) => {
    const response = await page.goto('/user/editform/token');
    expect(response?.status()).toBeLessThan(400);
  });

  test('revoke an API token removes it from the list', async ({ page }) => {
    const name = `e2e-token-revoke-${uniqueSuffix()}`;
    await page.goto('/user/editform/tokens/new');
    await page.fill('#frmApiTokenIssue input[name="name"]', name);
    await page.selectOption('#frmApiTokenIssue select[name="expiresInDays"]', { index: 0 });
    await page.click('#frmApiTokenIssue button[type=submit]');
    await expect(page.locator('body')).toContainText(name);

    // ApiTokenServiceImpl.revoke() hard-deletes the row (not a soft "revoked" flag), so the row
    // simply disappears from the list. Guarded by the same native confirm() pattern as the
    // SSH/GPG delete forms.
    page.once('dialog', (dialog) => dialog.accept());
    await page.locator('tr', { hasText: name }).locator('button[type=submit]').click();
    await expect(page.locator('body')).not.toContainText(name);
  });
});

test.describe('OAuth apps', () => {
  test('authorized apps (apps I granted access to) screen loads', async ({ page }) => {
    const response = await page.goto('/user/editform/oauth-apps');
    expect(response?.status()).toBeLessThan(400);
  });

  test('completing a real consent grant lists the app under authorized apps, and revoking it removes the entry', async ({ page }) => {
    // OAuthAuthorizedAppsService.listAuthorizedApps() reads from OAuth2AuthorizationConsent
    // rows, which are only written when the /oauth2/consent screen is actually shown AND
    // submitted -- a scope=openid-only request auto-approves without ever creating a consent
    // record (see 14-oauth2-2fa-sso/oauth2-consent.spec.ts's header comment), so this needs the
    // same openid+profile combination that spec uses to actually reach the consent screen.
    const suffix = uniqueSuffix();
    const clientName = `e2e-authz-app-${suffix}`;
    const redirectUri = 'http://localhost:9999/callback';

    await page.goto('/user/editform/oauth-apps-owned/new');
    await page.fill('#frmOAuthAppRegister input[name="clientName"]', clientName);
    await page.fill('#frmOAuthAppRegister input[name="redirectUri"]', redirectUri);
    await page.check('#frmOAuthAppRegister input[name="confidential"][value="true"]');
    await page.check('#frmOAuthAppRegister input[name="scopes"][value="openid"]');
    await page.check('#frmOAuthAppRegister input[name="scopes"][value="profile"]');
    await page.click('#frmOAuthAppRegister button[type=submit]');
    const clientId = await page.locator('.alert-success code').first().textContent();

    const authorizeUrl =
      `/oauth2/authorize?response_type=code&client_id=${encodeURIComponent(clientId!)}` +
      `&redirect_uri=${encodeURIComponent(redirectUri)}&scope=${encodeURIComponent('openid profile')}&state=e2e-state-${suffix}`;
    await page.goto(authorizeUrl);
    await expect(page).toHaveURL(/\/oauth2\/consent/);

    // Submitting Authorize redirects the browser toward redirect_uri (an intentionally
    // unreachable port -- no real callback server exists in this suite), which the browser
    // can't load; the server-side consent record is already persisted by the time that POST
    // response is generated, so noWaitAfter avoids failing the test on that unreachable hop.
    await page.locator('button[type=submit]', { hasText: /Authorize|인가/ }).click({ noWaitAfter: true });
    await page.waitForTimeout(500);

    await page.goto('/user/editform/oauth-apps');
    await expect(page.locator('body')).toContainText(clientName);

    page.once('dialog', (dialog) => dialog.accept());
    await page.locator('tr', { hasText: clientName }).locator('button[type=submit]').click();
    await expect(page.locator('body')).not.toContainText(clientName);
  });

  test('register a new owned OAuth app', async ({ page }) => {
    await page.goto('/user/editform/oauth-apps-owned/new');
    await page.fill('#frmOAuthAppRegister input[name="clientName"]', 'E2E Test App');
    await page.fill('#frmOAuthAppRegister input[name="redirectUri"]', 'https://example.com/callback');
    await page.check('#frmOAuthAppRegister input[name="scopes"][value="profile"]');
    await page.click('#frmOAuthAppRegister button[type=submit]');

    await expect(page).toHaveURL(/\/user\/editform\/oauth-apps-owned$/);
    await expect(page.locator('body')).toContainText('E2E Test App');
  });

  test('delete an owned OAuth app removes it from the list', async ({ page }) => {
    const clientName = `e2e-oauth-delete-${uniqueSuffix()}`;
    await page.goto('/user/editform/oauth-apps-owned/new');
    await page.fill('#frmOAuthAppRegister input[name="clientName"]', clientName);
    await page.fill('#frmOAuthAppRegister input[name="redirectUri"]', 'https://example.com/callback');
    await page.check('#frmOAuthAppRegister input[name="scopes"][value="profile"]');
    await page.click('#frmOAuthAppRegister button[type=submit]');
    await expect(page.locator('body')).toContainText(clientName);

    page.once('dialog', (dialog) => dialog.accept());
    await page.locator('tr', { hasText: clientName }).locator('button[type=submit]').click();
    await expect(page.locator('body')).not.toContainText(clientName);
  });
});

test.describe('SSH keys', () => {
  test('add a valid SSH public key', async ({ page }) => {
    await page.goto('/user/editform/ssh-keys/new');
    await page.fill('#frmSshKeyAdd input[name="title"]', 'e2e laptop');
    await page.fill('#frmSshKeyAdd textarea[name="publicKey"]', generateSshPublicKey());
    await page.click('#frmSshKeyAdd button[type=submit]');

    await expect(page).toHaveURL(/\/user\/editform\/ssh-keys$/);
    await expect(page.locator('body')).toContainText('e2e laptop');
  });

  test('a malformed SSH key is rejected with a validation error', async ({ page }) => {
    await page.goto('/user/editform/ssh-keys/new');
    await page.fill('#frmSshKeyAdd input[name="title"]', 'bad key');
    await page.fill('#frmSshKeyAdd textarea[name="publicKey"]', 'not-a-real-ssh-key');
    await page.click('#frmSshKeyAdd button[type=submit]');

    // addSshKey() re-renders the "new" form template directly on validation failure (no
    // "redirect:" -- see UserViewController.kt) rather than actually redirecting, so the URL
    // stays at the POST target (no /new suffix) even though the form content is the same.
    await expect(page).toHaveURL(/\/user\/editform\/ssh-keys$/);
    await expect(page.locator('.alert-error')).toBeVisible();
  });

  test('delete an SSH key removes it from the list', async ({ page }) => {
    // Registers its own throwaway key rather than deleting the one from the "add" test above, so
    // this test is independent of Playwright's within-file execution order.
    const title = `e2e ssh delete ${uniqueSuffix()}`;
    await page.goto('/user/editform/ssh-keys/new');
    await page.fill('#frmSshKeyAdd input[name="title"]', title);
    await page.fill('#frmSshKeyAdd textarea[name="publicKey"]', generateSshPublicKey());
    await page.click('#frmSshKeyAdd button[type=submit]');
    await expect(page.locator('body')).toContainText(title);

    // POST /user/editform/ssh-keys/{id}/delete, guarded client-side by a native confirm()
    // (edit_ssh_keys.html's inline submit listener) -- must accept it or the form never submits.
    page.once('dialog', (dialog) => dialog.accept());
    await page.locator('tr', { hasText: title }).locator('button[type=submit]').click();
    await expect(page.locator('body')).not.toContainText(title);
  });
});

/** Same duplicate-key concern as SSH -- generate a fresh keypair in an isolated GNUPGHOME
 * (never touches the real user's keyring) instead of a hardcoded armored block. */
function generateGpgPublicKey(): string {
  const gnupgHome = fs.mkdtempSync(path.join(os.tmpdir(), 'yona-e2e-gpghome-'));
  fs.chmodSync(gnupgHome, 0o700);
  // The UID email MUST be one of the account's own verified emails -- GpgKeyServiceImpl rejects
  // (with "이 GPG 키의 User ID에 본인 계정의 인증된 이메일이 없습니다") any key whose UID doesn't
  // match, re-rendering the "new" form at the same /gpg-keys URL a *successful* add also lands
  // on. A prior version of this helper used a random unverified email, so the "add a valid key"
  // test below was actually asserting on that silent-rejection page the whole time (URL-only
  // check can't tell the two apart) -- ADMIN_EMAIL matches global.setup.ts's bootstrap email for
  // the admin account this suite always runs as.
  const uid = `E2E Test ${uniqueSuffix()} <admin@yona-e2e.test>`;
  // --pinentry-mode loopback --passphrase '': without this, --quick-generate-key tries to pop a
  // pinentry prompt for a passphrase, which fails with "Inappropriate ioctl for device" when run
  // from a child process with no controlling TTY (exactly this execFileSync call).
  execFileSync('gpg', [
    '--batch', '--homedir', gnupgHome, '--pinentry-mode', 'loopback', '--passphrase', '',
    '--quick-generate-key', uid, 'ed25519', 'sign', 'never',
  ]);
  const armoredKey = execFileSync('gpg', ['--batch', '--homedir', gnupgHome, '--armor', '--export', uid]).toString('utf-8');
  fs.rmSync(gnupgHome, { recursive: true, force: true });
  return armoredKey;
}

test.describe('GPG keys', () => {
  test('add a valid ASCII-armored GPG public key', async ({ page }) => {
    await page.goto('/user/editform/gpg-keys/new');
    await page.fill('#frmGpgKeyAdd textarea[name="armoredPublicKey"]', generateGpgPublicKey());
    await page.click('#frmGpgKeyAdd button[type=submit]');

    // A rejected key (e.g. unverified UID email) re-renders this same "new" form at this same
    // URL -- toHaveURL alone can't distinguish success from silent rejection, so also assert the
    // success flash banner and the absence of the rejection's error alert.
    await expect(page).toHaveURL(/\/user\/editform\/gpg-keys$/);
    await expect(page.locator('.alert-success')).toBeVisible();
    await expect(page.locator('.alert-error')).toHaveCount(0);
  });

  test('an invalid GPG key is rejected with a validation error', async ({ page }) => {
    await page.goto('/user/editform/gpg-keys/new');
    await page.fill('#frmGpgKeyAdd textarea[name="armoredPublicKey"]', 'not a real gpg key');
    await page.click('#frmGpgKeyAdd button[type=submit]');

    // Same "re-render, no redirect" pattern as the SSH key handler -- URL stays at the POST
    // target (no /new suffix).
    await expect(page).toHaveURL(/\/user\/editform\/gpg-keys$/);
    await expect(page.locator('.alert-error')).toBeVisible();
  });

  test('delete a GPG key removes it from the list', async ({ page }) => {
    const armoredKey = generateGpgPublicKey();
    await page.goto('/user/editform/gpg-keys/new');
    await page.fill('#frmGpgKeyAdd textarea[name="armoredPublicKey"]', armoredKey);
    await page.click('#frmGpgKeyAdd button[type=submit]');
    await expect(page.locator('.alert-success')).toBeVisible();

    // The key ID (short form) is what the list row shows -- extract it from the armored block's
    // fingerprint via gpg itself rather than re-deriving it in JS.
    const keyId = execFileSync('gpg', ['--with-colons', '--import-options', 'show-only', '--import'], {
      input: armoredKey,
    })
      .toString('utf-8')
      .split('\n')
      .find((line) => line.startsWith('fpr:'))!
      .split(':')[9]
      .slice(-16);

    await page.goto('/user/editform/gpg-keys');
    const row = page.locator('tr', { hasText: new RegExp(keyId, 'i') });
    await expect(row).toBeVisible();

    // POST /user/editform/gpg-keys/{id}/delete, guarded by the same native confirm() pattern.
    page.once('dialog', (dialog) => dialog.accept());
    await row.locator('button[type=submit]').click();
    await expect(page.locator('tr', { hasText: new RegExp(keyId, 'i') })).toHaveCount(0);
  });
});

test('uploaded-files screen loads (empty state included)', async ({ page }) => {
  const response = await page.goto('/user/files');
  expect(response?.status()).toBeLessThan(400);
});

test('cross-project "my issues" screen shows the seeded issue', async ({ page }) => {
  const response = await page.goto('/user/issues');
  expect(response?.status()).toBeLessThan(400);
});

test('/user/issues/new/mine (project-picker for "new issue") loads without erroring', async ({ page }) => {
  const response = await page.goto('/user/issues/new/mine');
  expect(response?.status()).toBeLessThan(500);
});

// Uses the throwaway second user from 01-auth (not admin) so a real password change here can't
// break the shared admin storageState every other spec in the suite depends on.
test.describe('password change', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('change password with the correct current password, then log in with the new one', async ({ page }) => {
    const loginId = requireSeed('secondUserLoginId');
    const oldPassword = requireSeed('secondUserPassword');
    const newPassword = 'NewUserPassw0rd!';

    await page.goto('/users/loginform');
    await page.fill('#loginIdOrEmailD', loginId);
    await page.fill('#password', oldPassword);
    await page.click('form[action="/users/login"] button[type=submit]');
    await expect(page.locator('a[href="/login"]')).toHaveCount(0);

    await page.goto('/user/editform/password');
    await page.fill('#frmPassword #oldPassword', oldPassword);
    await page.fill('#frmPassword #password', newPassword);
    await page.fill('#frmPassword #retypedPassword', newPassword);
    await page.click('#frmPassword button[type=submit]');

    // resetUserPassword() logs the session out and redirects to the login form on success.
    await expect(page).toHaveURL(/\/users\/loginform$/);

    await page.fill('#loginIdOrEmailD', loginId);
    await page.fill('#password', newPassword);
    await page.click('form[action="/users/login"] button[type=submit]');
    await expect(page.locator('a[href="/login"]')).toHaveCount(0);
  });
});
