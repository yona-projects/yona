import { test, expect } from '@playwright/test';
import { computeTotp } from '../../support/totp';
import { uniqueSuffix } from '../../support/unique';

/**
 * Screen: TwoFactorLoginController (@RequestMapping "/users/login/2fa"). This screen only
 * renders when SecurityContext holds a Pre2faAuthenticationToken (set by Spring Security right
 * after a correct password, when the account has 2FA enabled) -- a normal authenticated session
 * (like the default admin storageState, which has no 2FA enrolled) is NOT that token, so
 * `pendingToken()` returns null and the controller redirects to /users/loginform.
 *
 * The first two tests below document that reachable-without-2FA-enabled part. The two
 * `test.describe.serial` blocks further down drive a REAL end-to-end 2FA login (TOTP and
 * WebAuthn) against a dedicated throwaway account each enrolls for itself -- see
 * two-factor-settings.spec.ts's header for why computeTotp()/the CDP virtual authenticator make
 * this possible without otplib or real hardware.
 */

test('visiting the 2FA step directly without a pending 2FA login redirects to the login form', async ({ page }) => {
  await page.goto('/users/login/2fa');
  await expect(page).toHaveURL(/\/users\/loginform$/);
});

test('same redirect happens when a totp method is explicitly requested', async ({ page }) => {
  await page.goto('/users/login/2fa?method=totp');
  await expect(page).toHaveURL(/\/users\/loginform$/);
});

async function signUpDedicatedAccount(page: import('@playwright/test').Page, label: string) {
  const suffix = uniqueSuffix();
  const loginId = `e2e${label}${suffix}`;
  const password = '2faPassw0rd!';

  await page.goto('/signup');
  await page.fill('#loginId', loginId);
  await page.fill('#uname', `E2E ${label} ${suffix}`);
  await page.fill('#email', `${loginId}@yona-e2e.test`);
  await page.fill('#password', password);
  await page.fill('#retypedPassword', password);
  await page.click('form[action="/signup"] button[type=submit]');
  await expect(page).toHaveURL(/\/login/);

  await page.fill('#loginIdOrEmailD', loginId);
  await page.fill('#password', password);
  await page.click('form[action="/users/login"] button[type=submit]');
  await expect(page.locator('a[href="/login"]')).toHaveCount(0);

  return { loginId, password };
}

async function loginWithPassword(page: import('@playwright/test').Page, loginId: string, password: string) {
  await page.goto('/users/loginform');
  await page.fill('#loginIdOrEmailD', loginId);
  await page.fill('#password', password);
  await page.click('form[action="/users/login"] button[type=submit]');
}

test.describe.serial('real end-to-end TOTP login (dedicated account)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('a dedicated account enrolls TOTP, logs out, then logs back in through the real 2FA step', async ({ page, context }) => {
    const account = await signUpDedicatedAccount(page, 'totplogin');

    await page.goto('/user/editform/security/totp/new');
    const secret = (await page.locator('code').textContent())?.trim();
    expect(secret).toMatch(/^[A-Z2-7]{16,}$/);
    await page.fill('input[name="code"]', computeTotp(secret!));
    await page.click('form:has(input[name="code"]) button[type=submit]');
    await expect(page).toHaveURL(/\/user\/editform\/security(\/backup-codes\/show)?$/);

    // Log out client-side (clearing cookies) rather than depending on the fetch-based logout
    // link's own CSRF wiring -- this test's concern is the 2FA login step, not logout itself.
    await context.clearCookies();

    await loginWithPassword(page, account.loginId, account.password);
    // A correct password on a 2FA-enabled account yields a Pre2faAuthenticationToken and
    // redirects here instead of completing the login -- this is the real precondition the two
    // tests above documented as unreachable without it.
    await expect(page).toHaveURL(/\/users\/login\/2fa/);
    await expect(page.locator('#totpStep')).toBeVisible();

    await page.fill('#totpStep input[name="code"]', computeTotp(secret!));
    await page.click('#totpStep button[type=submit]');

    // finalizeLogin() replaces the SecurityContext with the real, fully-authenticated token --
    // same positive-signal assertion used across this suite's other login tests.
    await expect(page.locator('a[href="/login"]')).toHaveCount(0);
    await expect(page).not.toHaveURL(/\/users\/login\/2fa/);
  });
});

test.describe.serial('real end-to-end WebAuthn login (dedicated account, CDP virtual authenticator)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('a dedicated account registers a virtual key, logs out, then logs back in through the real 2FA step', async ({ page, context }) => {
    const account = await signUpDedicatedAccount(page, 'webauthnlogin');

    const cdp = await context.newCDPSession(page);
    await cdp.send('WebAuthn.enable');
    const { authenticatorId } = await cdp.send('WebAuthn.addVirtualAuthenticator', {
      options: {
        protocol: 'ctap2',
        transport: 'internal',
        hasResidentKey: true,
        hasUserVerification: true,
        isUserVerified: true,
        automaticPresenceSimulation: true,
      },
    });

    await page.goto('/user/editform/security/webauthn/new');
    await page.fill('#webauthnLabel', 'E2E login virtual key');
    await page.click('#webauthnRegisterBtn');
    await expect(page).toHaveURL(/\/user\/editform\/security$/, { timeout: 10_000 });

    await context.clearCookies();
    await loginWithPassword(page, account.loginId, account.password);
    await expect(page).toHaveURL(/\/users\/login\/2fa/);

    // login_2fa.html's inline script calls startWebauthn() automatically on load for the
    // webauthn step -- with automaticPresenceSimulation on, the same virtual authenticator
    // answers navigator.credentials.get() without any click needed here.
    await expect(page.locator('#webauthnStep')).toBeVisible();
    await expect(page.locator('a[href="/login"]')).toHaveCount(0, { timeout: 10_000 });
    await expect(page).not.toHaveURL(/\/users\/login\/2fa/);

    await cdp.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId });
  });
});
