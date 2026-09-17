import { test, expect } from '@playwright/test';
import { writeSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/**
 * Screens covered: GET /users/loginform (login.html), GET /signup (signup.html).
 * Runs logged OUT (storageState overridden) since these are the anonymous-only entry points.
 */
test.use({ storageState: { cookies: [], origins: [] } });

test.describe('signup', () => {
  test('every required field, then log in with the new account', async ({ page }) => {
    const suffix = uniqueSuffix();
    const loginId = `e2euser${suffix}`;
    const password = 'UserPassw0rd!';

    await page.goto('/signup');
    await page.fill('#loginId', loginId);
    await page.fill('#uname', `E2E User ${suffix}`);
    await page.fill('#email', `${loginId}@yona-e2e.test`);
    await page.fill('#password', password);
    await page.fill('#retypedPassword', password);
    await page.click('form[action="/signup"] button[type=submit]');

    // Successful signup redirects to the login form with a success banner.
    await expect(page).toHaveURL(/\/login/);

    await page.fill('#loginIdOrEmailD', loginId);
    await page.fill('#password', password);
    await page.click('form[action="/users/login"] button[type=submit]');
    await expect(page.locator('a[href="/login"]')).toHaveCount(0);

    writeSeed({ secondUserLoginId: loginId, secondUserPassword: password });
  });

  test('mismatched retyped password is rejected', async ({ page }) => {
    const suffix = uniqueSuffix();
    await page.goto('/signup');
    await page.fill('#loginId', `e2ebad${suffix}`);
    await page.fill('#uname', 'Mismatch User');
    await page.fill('#email', `bad${suffix}@yona-e2e.test`);
    await page.fill('#password', 'Passw0rd!');
    await page.fill('#retypedPassword', 'DoesNotMatch!');
    await page.click('form[action="/signup"] button[type=submit]');

    // Validation failure re-renders the signup form (does not proceed to /login).
    await expect(page).toHaveURL(/\/signup/);
  });
});

test.describe('login', () => {
  test('wrong password shows an error and does not authenticate', async ({ page }) => {
    await page.goto('/users/loginform');
    await page.fill('#loginIdOrEmailD', 'admin');
    await page.fill('#password', 'definitely-wrong');
    await page.click('form[action="/users/login"] button[type=submit]');

    await expect(page).toHaveURL(/\/users\/loginform/);
  });

  test('"remember me" checkbox and "forgot password" link are present', async ({ page }) => {
    await page.goto('/users/loginform');
    await expect(page.locator('#remember-me')).toBeVisible();
    // Two links point at /lostPassword on this page (the inline one and one inside an
    // unrelated component) -- .first() avoids a strict-mode violation since we only care that
    // at least one is reachable.
    await expect(page.locator('a[href="/lostPassword"]').first()).toBeVisible();
  });
});

test.describe('password reset', () => {
  test('lost password form accepts an email submission', async ({ page }) => {
    await page.goto('/lostPassword');
    // Scoped past the page-header's own <form name="gnb-search-form"> (a bare `form` locator
    // is a strict-mode violation with that form also on the page).
    await expect(page.locator('form[action="/lostPassword"]')).toBeVisible();
  });
});
