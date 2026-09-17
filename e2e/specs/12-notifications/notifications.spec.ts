import { test, expect } from '@playwright/test';

/** Screens: GET / (index.html), GET /notifications (index/notifications.html). IndexController.kt. */

test('index page renders for the logged-in admin (notifications list included)', async ({ page }) => {
  const response = await page.goto('/');
  expect(response?.status()).toBeLessThan(400);
  // Logged-in landing shows the account menu instead of Log in/Sign up links.
  await expect(page.locator('a[href="/login"]')).toHaveCount(0);
});

test('index page renders for an anonymous visitor', async ({ page }) => {
  await page.context().clearCookies();
  const response = await page.goto('/');
  expect(response?.status()).toBeLessThan(400);
  await expect(page.locator('a[href="/login"]')).toHaveCount(1);
});

test('notifications screen loads for the logged-in admin', async ({ page }) => {
  const response = await page.goto('/notifications');
  expect(response?.status()).toBeLessThan(400);
  await expect(page).not.toHaveURL(/\/users\/loginform/);
});

test('notifications screen redirects an anonymous visitor to login', async ({ page }) => {
  await page.context().clearCookies();
  await page.goto('/notifications');
  await expect(page).toHaveURL(/\/users\/loginform/);
});
