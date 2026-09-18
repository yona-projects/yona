import { test, expect } from '@playwright/test';
import { computeTotp } from '../../support/totp';
import { uniqueSuffix } from '../../support/unique';

/**
 * Screens: TwoFactorSettingsController (@RequestMapping "/user/editform/security").
 *
 * The screen-entry/failure-path tests below run as the default admin session. The two
 * `test.describe.serial` blocks further down complete real TOTP enrollment and real WebAuthn
 * registration end to end, each against its own dedicated throwaway account (2FA on the shared
 * admin/seed accounts would break every other spec's login) -- computeTotp() is a from-scratch
 * RFC 6238 implementation (see support/totp.ts, verified against the RFC 4226 HOTP test vectors),
 * and WebAuthn uses a CDP virtual authenticator (no real hardware/otplib dependency needed).
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

test.describe.serial('TOTP full enrollment (dedicated account)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('a dedicated account enrolls TOTP with a real, correctly computed code', async ({ page }) => {
    await signUpDedicatedAccount(page, 'totp');

    await page.goto('/user/editform/security/totp/new');
    // The secret sits inside the page's only <code> element (edit_security_totp_new.html);
    // beginTotpEnrollment() always issues a fresh base32 secret, so this is never stale.
    const secret = (await page.locator('code').textContent())?.trim();
    expect(secret).toMatch(/^[A-Z2-7]{16,}$/);

    const code = computeTotp(secret!);
    await page.fill('input[name="code"]', code);
    await page.click('form:has(input[name="code"]) button[type=submit]');

    // verifyAndActivateTotp() redirects to /user/editform/security with either a freshBackupCodes
    // flash (-> backup-codes/show) or a plain totpAdded flash -- either landing page proves the
    // real, from-scratch-computed code was accepted (the whole point of this test, vs. the
    // existing "invalid code" test above which deliberately never reaches this point).
    await expect(page).toHaveURL(/\/user\/editform\/security(\/backup-codes\/show)?$/);
    await expect(page.locator('body')).not.toContainText(/코드가 올바르지 않습니다|invalid/i);

    await page.goto('/user/editform/security');
    await expect(page.locator('body')).not.toContainText('등록된 TOTP가 없습니다');
  });
});

test.describe.serial('WebAuthn full registration (dedicated account, CDP virtual authenticator)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('a dedicated account registers a virtual security key end to end', async ({ page, context }) => {
    await signUpDedicatedAccount(page, 'webauthn');

    // Chrome DevTools Protocol's WebAuthn domain: a virtual authenticator answers
    // navigator.credentials.create()/get() with real (if throwaway) cryptographic credentials --
    // no physical hardware or browser flag needed, and yona's server side sees a genuine WebAuthn
    // JSON ceremony end to end (Spring Security's WebAuthnRelyingPartyOperations does real
    // attestation/signature verification, so this is not a mocked-out shortcut).
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
    await page.fill('#webauthnLabel', 'E2E virtual key');
    await page.click('#webauthnRegisterBtn');

    await expect(page).toHaveURL(/\/user\/editform\/security$/, { timeout: 10_000 });
    await expect(page.locator('body')).not.toContainText('등록된 보안 키가 없습니다');
    await expect(page.locator('body')).toContainText('E2E virtual key');

    await cdp.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId });
  });
});
