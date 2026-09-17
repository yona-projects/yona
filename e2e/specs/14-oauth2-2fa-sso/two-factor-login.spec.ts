import { test, expect } from '@playwright/test';

/**
 * Screen: TwoFactorLoginController (@RequestMapping "/users/login/2fa"). This screen only
 * renders when SecurityContext holds a Pre2faAuthenticationToken (set by Spring Security right
 * after a correct password, when the account has 2FA enabled) -- a normal authenticated session
 * (like the default admin storageState, which has no 2FA enrolled) is NOT that token, so
 * `pendingToken()` returns null and the controller redirects to /users/loginform. Actually
 * enrolling 2FA on an account and driving a real login through it needs a computed TOTP code
 * (out of scope -- see two-factor-settings.spec.ts's header), so this documents the reachable
 * part: what happens when the pending-2FA precondition is missing.
 */

test('visiting the 2FA step directly without a pending 2FA login redirects to the login form', async ({ page }) => {
  await page.goto('/users/login/2fa');
  await expect(page).toHaveURL(/\/users\/loginform$/);
});

test('same redirect happens when a totp method is explicitly requested', async ({ page }) => {
  await page.goto('/users/login/2fa?method=totp');
  await expect(page).toHaveURL(/\/users\/loginform$/);
});
