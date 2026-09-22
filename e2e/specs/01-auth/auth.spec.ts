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

// yona's application.yml points its SMTP sender at localhost:1025 with no auth -- a `mailpit`
// (https://mailpit.axllent.org/) SMTP-catcher + HTTP API instance is run alongside the suite on
// 127.0.0.1:1025/8025 specifically so real outbound mail (password reset, etc.) can be captured
// and asserted on instead of just checking that a form POST didn't 500.
const MAILPIT_API = 'http://127.0.0.1:8025/api/v1';

async function findMailTo(request: import('@playwright/test').APIRequestContext, toAddress: string, timeoutMs = 5000): Promise<{ ID: string }> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const res = await request.get(`${MAILPIT_API}/messages`);
    const body = await res.json();
    const match = (body.messages ?? []).find((m: any) => (m.To ?? []).some((t: any) => t.Address === toAddress));
    if (match) return match;
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  throw new Error(`mailpit: no message arrived for ${toAddress} within ${timeoutMs}ms (is mailpit running on ${MAILPIT_API}?)`);
}

async function fetchMailText(request: import('@playwright/test').APIRequestContext, id: string): Promise<string> {
  const res = await request.get(`${MAILPIT_API}/message/${id}`);
  const body = await res.json();
  return body.Text ?? body.HTML ?? '';
}

test.describe('password reset', () => {
  test('lost password form accepts an email submission', async ({ page }) => {
    await page.goto('/lostPassword');
    // Scoped past the page-header's own <form name="gnb-search-form"> (a bare `form` locator
    // is a strict-mode violation with that form also on the page).
    await expect(page.locator('form[action="/lostPassword"]')).toBeVisible();
  });

  test('a real reset email is sent, and following its link lets the user set a new password and log in', async ({ page, request }) => {
    // Dedicated throwaway account -- never touch the shared seed accounts (admin,
    // seed.secondUserLoginId) with a password reset, since other specs depend on their current
    // passwords staying stable.
    const suffix = uniqueSuffix();
    const loginId = `e2epwreset${suffix}`;
    const email = `${loginId}@yona-e2e.test`;
    const originalPassword = 'OriginalPassw0rd!';
    const newPassword = 'ResetPassw0rd!';

    await page.goto('/signup');
    await page.fill('#loginId', loginId);
    await page.fill('#uname', `E2E PwReset ${suffix}`);
    await page.fill('#email', email);
    await page.fill('#password', originalPassword);
    await page.fill('#retypedPassword', originalPassword);
    await page.click('form[action="/signup"] button[type=submit]');
    await expect(page).toHaveURL(/\/login/);

    // PasswordResetController.requestResetPasswordEmail() requires loginId AND emailAddress to
    // both match the same user (see the controller's own comment) -- both fields are required.
    await page.goto('/lostPassword');
    await page.fill('#loginId', loginId);
    await page.fill('#emailAddress', email);
    await page.click('form[action="/lostPassword"] button[type=submit]');
    await expect(page.locator('.alert-success')).toBeVisible();

    const message = await findMailTo(request, email);
    const text = await fetchMailText(request, message.ID);
    // PasswordResetController builds the link as "$serverUrl/user/reset-password?hash=$hashString"
    // in plain text (sendHtmlMail is called with a plain-text body here, not real HTML).
    const linkMatch = text.match(/\/user\/reset-password\?hash=([^\s"<]+)/);
    expect(linkMatch, `reset email body did not contain a reset link:\n${text}`).toBeTruthy();
    const hash = linkMatch![1];

    await page.goto(`/user/reset-password?hash=${hash}`);
    await expect(page.locator('form[action="/user/reset-password"]')).toBeVisible();
    await page.fill('#password', newPassword);
    await page.fill('#retypedPassword', newPassword);
    await page.click('form[action="/user/reset-password"] button[type=submit]');

    // Successful reset redirects to the login form with a flash message.
    await expect(page).toHaveURL(/\/users\/loginform/);

    await page.fill('#loginIdOrEmailD', loginId);
    await page.fill('#password', newPassword);
    await page.click('form[action="/users/login"] button[type=submit]');
    await expect(page.locator('a[href="/login"]')).toHaveCount(0);
  });
});

test.describe('email verification (legacy /verify route)', () => {
  // A bogus code must always 404 regardless of whether email verification is required for new
  // signups -- this holds under both the default (feature off) and the opt-in (feature on, see
  // below) configuration.
  test('visiting the route with a made-up code 404s', async ({ page }) => {
    const response = await page.goto('/verify/does-not-exist/bogus-code');
    expect(response?.status()).toBe(404);
  });

  // UserService.sendVerificationEmail()/verifyUser()/UserVerification were ported from legacy
  // Yobi/yona during the Play->Spring rewrite, but nothing in signup() ever called
  // sendVerificationEmail() or checked the legacy `application.use.email.verification` flag, so
  // no UI path could ever reach GET /verify/{loginId}/{code} or /user/verify. AuthController now
  // has a `yona.signup.require-email-verification` flag (default false, same shape as the
  // existing `yona.signup.require-admin-confirm`) that wires this in: when on, signup() creates
  // the user LOCKED (reusing the same UserState the admin-confirm path already uses --
  // verifyUser() already flips LOCKED back to ACTIVE on success, so the two paths compose for
  // free) and sends the real verification email.
  //
  // This test only exercises real behavior when the server is running with
  // YONA_SIGNUP_REQUIRE_EMAIL_VERIFICATION=true (default is false, so the rest of this suite's
  // "sign up, then log straight in" assumption elsewhere is unaffected during normal runs). It
  // self-skips against a default-config server instead of requiring a separate manual run: the
  // signup form renders #signupVerificationNotice (signup.html) only when the flag is on, giving
  // this test a live, page-sourced signal instead of guessing from an env var the browser can't
  // see. See e2e/README.md for how to start the server with the flag on to actually exercise this.
  test('when required, signup blocks login until the mailed verification link is visited', async ({ page, request }) => {
    await page.goto('/signup');
    if ((await page.locator('#signupVerificationNotice').count()) === 0) {
      test.skip(true, 'server is not running with yona.signup.require-email-verification=true');
    }

    const suffix = uniqueSuffix();
    const loginId = `e2everify${suffix}`;
    const email = `${loginId}@yona-e2e.test`;
    const password = 'VerifyPassw0rd!';

    await page.fill('#loginId', loginId);
    await page.fill('#uname', `E2E Verify ${suffix}`);
    await page.fill('#email', email);
    await page.fill('#password', password);
    await page.fill('#retypedPassword', password);
    await page.click('form[action="/signup"] button[type=submit]');
    // signup() redirects to a distinct query param when email verification is pending, instead
    // of the plain "signupSuccess" used when no gate is configured.
    await expect(page).toHaveURL(/\/users\/loginform\?signupVerificationSent/);

    // The account is LOCKED until verified -- logging in immediately must fail, not succeed.
    await page.fill('#loginIdOrEmailD', loginId);
    await page.fill('#password', password);
    await page.click('form[action="/users/login"] button[type=submit]');
    await expect(page.locator('a[href="/login"]')).toHaveCount(1);

    const message = await findMailTo(request, email);
    const text = await fetchMailText(request, message.ID);
    // UserServiceImpl.sendVerificationEmail() builds "$serverUrl/user/verify?loginId=...&code=...".
    const linkMatch = text.match(/\/user\/verify\?loginId=[^&\s"<]+&code=[^\s"<]+/);
    expect(linkMatch, `verification email body did not contain a verify link:\n${text}`).toBeTruthy();

    await page.goto(linkMatch![0]);
    await expect(page.locator('body')).not.toContainText('404');

    // Now that the account is ACTIVE, login must succeed. /user/verify's success page
    // (user/verified.html) has no login form of its own -- go back to the login form first.
    await page.goto('/users/loginform');
    await page.fill('#loginIdOrEmailD', loginId);
    await page.fill('#password', password);
    await page.click('form[action="/users/login"] button[type=submit]');
    await expect(page.locator('a[href="/login"]')).toHaveCount(0);
  });
});
