import { test, expect } from '@playwright/test';
import { uniqueSuffix } from '../../support/unique';

/** Screen: GET/DELETE .../deleteForm & DELETE /organizations/{orgName} (organization/delete.html).
 * organization-crud.spec.ts deliberately never submits this delete (later specs keep referencing
 * seed.orgName) -- this file creates its own dedicated, throwaway organization so the real
 * deletion can run to completion without touching the shared seed. */

test('create a dedicated organization, then delete it for real', async ({ page }) => {
  const orgName = `e2e-org-delete-${uniqueSuffix()}`;

  await page.goto('/organizations/new');
  await page.fill('#name', orgName);
  await page.fill('#descr', 'Throwaway organization for the real-delete e2e test');
  await page.click('form[action="/organizations/new"] button.ybtn-success');
  await expect(page).toHaveURL(new RegExp(`/organizations/${orgName}$`));

  await page.goto(`/organizations/${orgName}/deleteForm`);
  await page.click('#btnDelete');
  await expect(page.locator('dialog#alertDeletion')).toBeVisible();

  const [deleteResponse] = await Promise.all([
    page.waitForResponse((res) => res.request().method() === 'DELETE' && res.url().endsWith(`/organizations/${orgName}`)),
    page.click('#btnDeleteExec'),
  ]);
  expect(deleteResponse.ok()).toBeTruthy();
  await page.waitForURL('/');

  await page.goto('/orgs');
  const listText = await page.locator('body').innerText();
  expect(listText).not.toContain(orgName);

  // ErrorViewStatusInterceptor now maps the "error/404" view name to a real 404 status
  // (previously a bare view-name return left the status at 200 -- fixed system-wide).
  const viewResponse = await page.goto(`/organizations/${orgName}`);
  expect(viewResponse?.status()).toBe(404);
  await expect(page.locator('.error-wrap')).toBeVisible();
});
