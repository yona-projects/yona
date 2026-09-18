import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screen: GET/POST .../changeVCS (project/change_vcs.html). project-change-vcs.spec.ts
 * deliberately only loads this screen (real VCS change is hard to undo on the shared seed
 * project) -- this file creates its own dedicated, throwaway GIT project and runs the change to
 * completion via yona.project.ChangeVCS.js's actual flow: check #acceptChangeVCS -> open the
 * native <dialog> -> confirm -> POST, 204 with no Location -> page reload. */

test('create a dedicated GIT project, then actually change its VCS type', async ({ page }) => {
  const adminLoginId = requireSeed('adminLoginId');
  const projectName = `e2e-vcs-change-${uniqueSuffix()}`;

  await page.goto('/projectform');
  await page.selectOption('#project-owner', adminLoginId);
  await page.fill('#project-name', projectName);
  await page.fill('#description', 'Throwaway project for the real VCS-change e2e test');
  await page.check('#public');
  await page.selectOption('#vcs', 'GIT');
  await page.click('#newProjectForm button.ybtn-success');
  await expect(page).toHaveURL(new RegExp(`/${adminLoginId}/${projectName}`));

  await page.goto(`/${adminLoginId}/${projectName}/changeVCS`);
  const nextVcs = await page.locator('h3 span').nth(1).textContent();
  expect(nextVcs).toBeTruthy();

  await page.check('#acceptChangeVCS');
  await page.click('#btnChangeVCS');
  await expect(page.locator('dialog#alertChangeVCS')).toBeVisible();

  const [changeResponse] = await Promise.all([
    page.waitForResponse(
      (res) => res.request().method() === 'POST' && res.url().endsWith(`/${adminLoginId}/${projectName}/changeVCS`)
    ),
    page.click('#btnChangeVCSExec'),
  ]);
  expect(changeResponse.status()).toBe(204);
  // No Location header on success -> the JS module reloads the current page rather than
  // navigating; give that reload a moment to land before asserting on the settings screen.
  await page.waitForLoadState('networkidle');

  // setting.html never prints project.vcs as plain text (only in th:if conditionals) -- re-visit
  // changeVCS instead, whose h3 always renders "${project.vcs} -> ${nextVcs}" directly.
  await page.goto(`/${adminLoginId}/${projectName}/changeVCS`);
  const currentVcsAfterChange = await page.locator('h3 span').nth(0).textContent();
  expect(currentVcsAfterChange!.trim()).toBe(nextVcs!.trim());
});
