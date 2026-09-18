import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screen: GET/DELETE .../delete (project/delete.html). project-delete.spec.ts deliberately only
 * loads this screen (05/06/07 depend on the shared seed project) -- this file creates its own
 * dedicated, throwaway project so the real deletion can run to completion. */

test('create a dedicated project, then delete it for real', async ({ page }) => {
  const adminLoginId = requireSeed('adminLoginId');
  const projectName = `e2e-delete-${uniqueSuffix()}`;

  await page.goto('/projectform');
  await page.selectOption('#project-owner', adminLoginId);
  await page.fill('#project-name', projectName);
  await page.fill('#description', 'Throwaway project for the real-delete e2e test');
  await page.check('#public');
  await page.selectOption('#vcs', 'GIT');
  await page.click('#newProjectForm button.ybtn-success');
  await expect(page).toHaveURL(new RegExp(`/${adminLoginId}/${projectName}`));

  await page.goto(`/${adminLoginId}/${projectName}/deleteform`);
  await page.check('#accept');
  await page.click('#btnDelete');
  await expect(page.locator('dialog#alertDeletion')).toBeVisible();

  const [deleteResponse] = await Promise.all([
    page.waitForResponse(
      (res) => res.request().method() === 'DELETE' && res.url().endsWith(`/${adminLoginId}/${projectName}/delete`)
    ),
    page.click('#btnDeleteExec'),
  ]);
  expect(deleteResponse.ok()).toBeTruthy();
  await page.waitForLoadState('networkidle');

  await page.goto('/projects');
  const listText = await page.locator('body').innerText();
  expect(listText).not.toContain(projectName);

  // ErrorViewStatusInterceptor now maps the "error/404" view name to a real 404 status.
  const viewResponse = await page.goto(`/${adminLoginId}/${projectName}`);
  expect(viewResponse?.status()).toBe(404);
  await expect(page.locator('.error-wrap')).toBeVisible();
});
