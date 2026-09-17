import { test, expect } from '@playwright/test';

/**
 * Screens: TwoFactorSettingsController (@RequestMapping "/user/editform/security").
 *
 * Does NOT complete TOTP enrollment or WebAuthn registration -- both require producing a valid
 * credential (a real TOTP code needs decoding the displayed base32 secret and computing an
 * RFC 6238 HMAC-SHA1 code; WebAuthn needs an actual authenticator/virtual authenticator wired
 * into the browser). Per README.md this is a deliberate scope boundary: screen entry and form
 * presence are verified, the credential-producing step is not. See matrix.md.
 */

test('security settings screen loads with no credentials enrolled yet', async ({ page }) => {
  await page.goto('/user/editform/security');
  await expect(page).not.toHaveURL(/error\/403/);
  await expect(page.locator('a[href="/user/editform/security/webauthn/new"]')).toBeVisible();
  await expect(page.locator('a[href="/user/editform/security/totp/new"]')).toBeVisible();
});

test('TOTP enrollment screen renders a QR code and a raw secret', async ({ page }) => {
  await page.goto('/user/editform/security/totp/new');
  await expect(page).not.toHaveURL(/error\/403/);
  // beginTotpEnrollment() always populates these two model attributes -- their presence is what
  // proves the enrollment step actually generated a fresh secret, without needing to consume it.
  await expect(page.locator('body')).toContainText(/[A-Z2-7]{16,}/); // base32 secret shape
  const qrImage = page.locator('img[src^="data:image"]');
  await expect(qrImage).toBeVisible();
});

test('submitting an invalid TOTP code is rejected and re-shows the enrollment form', async ({ page }) => {
  await page.goto('/user/editform/security/totp/new');
  const actionUrl = await page.locator('form').filter({ has: page.locator('input[name="code"]') }).getAttribute('action');
  expect(actionUrl).toMatch(/\/totp\/\d+\/verify$/);

  await page.fill('input[name="code"]', '000000');
  await page.click('form:has(input[name="code"]) button[type=submit]');

  // A wrong code re-renders the same enrollment screen (with totpError) rather than activating.
  await expect(page).toHaveURL(/\/totp\/new$|\/totp\/\d+\/verify$/);
  await expect(page.locator('body')).toContainText(/[A-Z2-7]{16,}/);
});

test('WebAuthn registration screen loads', async ({ page }) => {
  await page.goto('/user/editform/security/webauthn/new');
  await expect(page).not.toHaveURL(/error\/403/);
});

test('backup codes screen redirects back to security settings when there is nothing fresh to show', async ({ page }) => {
  // showBackupCodes() only renders when a freshBackupCodes flash attribute exists (set right
  // after a TOTP activation or an explicit regenerate) -- visiting it cold redirects away, which
  // is the real, documented behavior (not a test bug).
  await page.goto('/user/editform/security/backup-codes/show');
  await expect(page).toHaveURL(/\/user\/editform\/security$/);
});
