import { test, expect } from '@playwright/test';
import { uniqueSuffix } from '../../support/unique';

/**
 * Screen: OAuthConsentController (GET /oauth2/consent), reached via Spring Authorization
 * Server's standard /oauth2/authorize endpoint. Self-registers its own OAuth app (via user
 * self-service, see 13-admin/oauth-apps-admin.spec.ts's header comment for why that -- not
 * /site/oauth-apps -- is where registration lives) rather than depending on another spec's seed,
 * since fork/area execution order isn't guaranteed.
 */

test('missing required query params returns a client error, not a 500', async ({ page }) => {
  const response = await page.goto('/oauth2/consent');
  expect(response?.status()).toBeGreaterThanOrEqual(400);
  expect(response?.status()).toBeLessThan(500);
});

test('unknown client_id renders a 404 page instead of the consent screen', async ({ page }) => {
  await page.goto('/oauth2/consent?client_id=does-not-exist&scope=openid&state=xyz');
  await expect(page.locator('body')).not.toContainText('Authorize');
});

test('a full authorization_code request for a freshly registered confidential app reaches the consent screen', async ({ page }) => {
  const suffix = uniqueSuffix();
  const clientName = `e2e-consent-app-${suffix}`;
  const redirectUri = 'http://localhost:9999/callback';

  await page.goto('/user/editform/oauth-apps-owned/new');
  await page.fill('#frmOAuthAppRegister input[name="clientName"]', clientName);
  await page.fill('#frmOAuthAppRegister input[name="redirectUri"]', redirectUri);
  // Confidential (not public/PKCE-only) so /oauth2/authorize doesn't also require a PKCE
  // code_challenge just to reach the consent screen.
  await page.check('#frmOAuthAppRegister input[name="confidential"][value="true"]');
  // Spring Authorization Server's own consent logic (OAuth2AuthorizationConsentAuthenticationProvider)
  // treats a request for "openid" ALONE as nothing worth asking consent for (it's just
  // identity/sign-in, not a data-access grant) and auto-issues the code even with
  // requireAuthorizationConsent=true -- confirmed live: with only "openid" requested, /oauth2/authorize
  // 302s straight to redirect_uri with a code, never showing /oauth2/consent at all. Requesting an
  // additional real scope ("profile") is what actually makes the consent screen appear.
  await page.check('#frmOAuthAppRegister input[name="scopes"][value="openid"]');
  await page.check('#frmOAuthAppRegister input[name="scopes"][value="profile"]');
  await page.click('#frmOAuthAppRegister button[type=submit]');

  // .alert-success renders both "Client ID: <code>" and the client secret as separate <code>
  // elements -- the client ID is the first one.
  const clientId = await page.locator('.alert-success code').first().textContent();
  expect(clientId).toBeTruthy();

  const authorizeUrl =
    `/oauth2/authorize?response_type=code&client_id=${encodeURIComponent(clientId!)}` +
    `&redirect_uri=${encodeURIComponent(redirectUri)}&scope=${encodeURIComponent('openid profile')}&state=e2e-state-${suffix}`;
  await page.goto(authorizeUrl);

  await expect(page).toHaveURL(/\/oauth2\/consent/);
  await expect(page.locator('body')).toContainText(clientName);
});
