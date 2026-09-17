import { test as setup, expect } from '@playwright/test';
import * as path from 'path';
import { writeSeed, readSeed } from '../support/seed-store';

/**
 * One-time bootstrap every other spec depends on. A fresh H2 DB has zero users, and
 * BootstrapSetupInterceptor force-redirects every request to /bootstrap-setup until one exists
 * (BootstrapSetupController.kt) -- that first account is always loginId "admin" (hardcoded,
 * readonly in the form) and is granted SITE_ADMIN, which the 13-admin specs need.
 *
 * Re-running against a DB that already has an admin (bootstrap-setup redirects to "/" once
 * userRepository.count() > 0) skips straight to login -- this setup is safe to re-run.
 */
const ADMIN_LOGIN_ID = 'admin';
const ADMIN_PASSWORD = 'AdminPassw0rd!';
const ADMIN_EMAIL = 'admin@yona-e2e.test';
const AUTH_FILE = path.join(__dirname, '..', '.auth', 'admin.json');

setup('bootstrap admin account and authenticate', async ({ page }) => {
  await page.goto('/');

  if (page.url().includes('/bootstrap-setup')) {
    await expect(page.locator('#loginId')).toHaveValue(ADMIN_LOGIN_ID);
    await page.fill('#uname', 'E2E Admin');
    await page.fill('#email', ADMIN_EMAIL);
    await page.fill('#password', ADMIN_PASSWORD);
    await page.fill('#retypedPassword', ADMIN_PASSWORD);
    await page.click('form[action="/bootstrap-setup"] button[type=submit]');
    await expect(page).toHaveURL(/\/users\/loginform/);
  }

  // Password is only known on a truly fresh DB (this run's bootstrap). On a reused DB, seed.json
  // from a prior run still has it -- reuse that instead of a wrong hardcoded guess.
  const existing = readSeed();
  const password = existing.adminPassword ?? ADMIN_PASSWORD;

  await page.goto('/users/loginform');
  await page.fill('#loginIdOrEmailD', ADMIN_LOGIN_ID);
  await page.fill('#password', password);
  // Scoped to the login form specifically -- the page header also renders a search <form> with
  // its own submit button, and an unscoped `button[type=submit]` selector matches that one
  // first (it appears earlier in the DOM), silently submitting a search instead of logging in.
  await page.click('form[action="/users/login"] button[type=submit]');
  // A weak "not on /users/loginform" check passes even when the click above hit the wrong
  // button (it lands on /search, which also isn't /users/loginform) -- assert on a positive
  // signal of an authenticated session instead.
  await expect(page.locator('a[href="/login"]')).toHaveCount(0);

  await page.context().storageState({ path: AUTH_FILE });
  writeSeed({ adminLoginId: ADMIN_LOGIN_ID, adminPassword: password });
});
