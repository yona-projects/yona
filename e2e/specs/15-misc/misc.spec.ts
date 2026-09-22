import { test, expect } from '@playwright/test';

/** Screens: GET /_help (HelpController.kt), GET /oauth2/consent (OAuthConsentController.kt),
 * GET /migration (MigrationViewController.kt). */

test('help page loads while logged in', async ({ page }) => {
  const response = await page.goto('/_help');
  expect(response?.status()).toBe(200);
});

test('help page loads while logged out (no auth required)', async ({ page }) => {
  await page.context().clearCookies();
  const response = await page.goto('/_help');
  expect(response?.status()).toBe(200);
});

// client_id/scope/state are required @RequestParam with no default -- Spring itself rejects a
// bare request with 400 before the controller body runs.
test('consent screen without client_id/scope/state responds 400, not a crash', async ({ page }) => {
  const response = await page.goto('/oauth2/consent');
  expect(response?.status()).toBe(400);
});

// With no external migration source configured (default h2 profile),
// migrationService.isAllowMigration() is false, and the controller renders "error/403" -- as a
// normal 200 response, not an actual HTTP 403 (the view name doesn't set the status code).
test('migration screen renders the disabled-migration view when no migration source is configured', async ({ page }) => {
  const response = await page.goto('/migration');
  expect(response?.status()).toBe(200);
  await expect(page.locator('body')).toContainText(/not authorized|권한|허용/i);
});
