import { test, expect } from '@playwright/test';
import { uniqueSuffix } from '../../support/unique';

/**
 * Screen: OAuthAppsAdminController (@RequestMapping ["/site/oauth-apps", "/sites/oauth-apps"]).
 *
 * This controller only handles a read-only site-wide audit list plus a forced-delete action --
 * app *registration* is deliberately handled by user self-service instead
 * (UserViewController's /user/editform/oauth-apps-owned, see that controller's own comment).
 * There is no registration form here to test; registering an app (needed for 14-oauth2-2fa-sso's
 * consent flow) happens on the user self-service screen instead, exercised there.
 */

test('oauth apps audit list loads for a site admin', async ({ page }) => {
  const response = await page.goto('/site/oauth-apps');
  expect(response?.status()).toBeLessThan(400);
  await expect(page).not.toHaveURL(/error\/403/);
});

test('a self-registered app appears in the admin audit list with its owner', async ({ page }) => {
  const suffix = uniqueSuffix();
  const clientName = `e2e-audit-app-${suffix}`;

  // Registration itself is user self-service (see file header) -- the admin (default
  // storageState) can register one for itself too, which is enough to prove the audit list
  // reflects self-service registrations.
  await page.goto('/user/editform/oauth-apps-owned/new');
  await page.fill('#frmOAuthAppRegister input[name="clientName"]', clientName);
  await page.fill('#frmOAuthAppRegister input[name="redirectUri"]', 'http://localhost:9999/callback');
  await page.check('#frmOAuthAppRegister input[name="confidential"][value="true"]');
  await page.click('#frmOAuthAppRegister button[type=submit]');
  await expect(page).toHaveURL(/\/user\/editform\/oauth-apps-owned$/);

  await page.goto('/site/oauth-apps');
  await expect(page.locator('body')).toContainText(clientName);
  await expect(page.locator('body')).toContainText('admin');
});

test('a site admin can force-delete any app from the audit list', async ({ page }) => {
  const suffix = uniqueSuffix();
  const clientName = `e2e-audit-delete-${suffix}`;

  await page.goto('/user/editform/oauth-apps-owned/new');
  await page.fill('#frmOAuthAppRegister input[name="clientName"]', clientName);
  await page.fill('#frmOAuthAppRegister input[name="redirectUri"]', 'http://localhost:9999/callback');
  await page.check('#frmOAuthAppRegister input[name="confidential"][value="true"]');
  await page.click('#frmOAuthAppRegister button[type=submit]');
  await expect(page).toHaveURL(/\/user\/editform\/oauth-apps-owned$/);

  await page.goto('/site/oauth-apps');
  await expect(page.locator('body')).toContainText(clientName);

  // POST /sites/oauth-apps/{id}/delete, guarded by a native confirm() (inline onsubmit).
  page.once('dialog', (dialog) => dialog.accept());
  await page.locator('tr', { hasText: clientName }).locator('button[type=submit]').click();
  await expect(page.locator('body')).not.toContainText(clientName);
});
