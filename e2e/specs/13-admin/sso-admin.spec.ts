import { test, expect } from '@playwright/test';

/**
 * Screen: SsoAdminController (@RequestMapping ["/site/sso", "/sites/sso"]) -- OIDC and SAML2
 * settings forms. Saves real (but non-functional, no actual IdP) settings values to prove the
 * forms round-trip -- does not attempt an actual SSO login (out of scope, see README.md).
 */

test('OIDC settings form saves and re-renders the saved values', async ({ page }) => {
  await page.goto('/site/sso');
  await expect(page).not.toHaveURL(/error\/403/);

  await page.check('#oidcSsoForm input[name="enabled"]');
  await page.fill('#oidcSsoForm input[name="registrationId"]', 'e2e-oidc');
  await page.fill('#oidcSsoForm input[name="issuerUri"]', 'https://idp.example.com');
  await page.fill('#oidcSsoForm input[name="clientId"]', 'e2e-client-id');
  await page.fill('#oidcSsoForm input[name="clientSecret"]', 'e2e-client-secret');
  await page.click('#oidcSsoForm button[type=submit]');

  await expect(page).toHaveURL(/\/site\/sso$/);
  await expect(page.locator('#oidcSsoForm input[name="issuerUri"]')).toHaveValue('https://idp.example.com');
});

test('SAML2 settings form saves and re-renders the saved values', async ({ page }) => {
  await page.goto('/site/sso');

  await page.check('#saml2SsoForm input[name="enabled"]');
  await page.fill('#saml2SsoForm input[name="registrationId"]', 'e2e-saml2');
  await page.fill('#saml2SsoForm input[name="idpSsoUrl"]', 'https://idp.example.com/sso');
  await page.fill('#saml2SsoForm input[name="idpEntityId"]', 'https://idp.example.com/entity');
  await page.fill('#saml2SsoForm textarea[name="idpCertificate"]', '-----BEGIN CERTIFICATE-----\ndummy\n-----END CERTIFICATE-----');
  await page.click('#saml2SsoForm button[type=submit]');

  await expect(page).toHaveURL(/\/site\/sso$/);
  await expect(page.locator('#saml2SsoForm input[name="idpSsoUrl"]')).toHaveValue('https://idp.example.com/sso');

  // Cleanup: disable both again so this run doesn't leave SSO "enabled" (pointing at a fake IdP)
  // for whatever runs next against this DB.
  await page.uncheck('#saml2SsoForm input[name="enabled"]');
  await page.click('#saml2SsoForm button[type=submit]');
  await page.uncheck('#oidcSsoForm input[name="enabled"]');
  await page.click('#oidcSsoForm button[type=submit]');
});
