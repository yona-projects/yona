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

  await page.goto(`/${adminLoginId}/${projectName}/issueform`);
  await page.fill('#title', 'Issue kept across repository reset');
  await page.locator('textarea[data-editor-mode="content-body"]').fill('Keep issue marker', { force: true });
  await page.click('#button-save');
  await expect(page).toHaveURL(new RegExp(`/${adminLoginId}/${projectName}/issue/\\d+`));
  const issueUrl = page.url();

  await page.goto(`/${adminLoginId}/${projectName}/postform`);
  await page.fill('#title', 'Board kept across repository reset');
  await page.locator('textarea[data-editor-mode="content-body"]').fill('Keep board marker', { force: true });
  await page.click('#post-form button[type=submit]');
  await expect(page).toHaveURL(new RegExp(`/${adminLoginId}/${projectName}/post/\\d+`));
  const postUrl = page.url();

  await page.goto(`/${adminLoginId}/${projectName}/changeVCS`);
  const nextVcs = await page.locator('h3 span').nth(1).textContent();
  expect(nextVcs).toBeTruthy();
  const projectId = await page.locator('#confirmVcsProjectName').getAttribute('data-project-id');
  const currentVcs = await page.locator('#confirmVcsProjectName').getAttribute('data-current-vcs');
  // Use the page's fetch wrapper so the real CSRF header is present, just as in the UI.
  const rejectedStatuses = await page.evaluate(async ({ url, projectId, projectName, currentVcs }) => {
    const confirmation = { projectId: Number(projectId), projectName, expectedVcs: currentVcs, accepted: true };
    const bodies = [
      undefined,
      { ...confirmation, projectName: `${projectName}-wrong` },
      { ...confirmation, accepted: false },
      { ...confirmation, expectedVcs: 'SUBVERSION' },
    ];
    const statuses: number[] = [];
    for (const body of bodies) {
      const response = await fetch(url, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      statuses.push(response.status);
    }
    return statuses;
  }, { url: `/${adminLoginId}/${projectName}/changeVCS`, projectId, projectName, currentVcs });
  expect(rejectedStatuses).toEqual([400, 400, 400, 409]);
  await page.reload();
  await expect(page.locator('#confirmVcsProjectName')).toHaveAttribute('data-current-vcs', currentVcs!);

  await page.check('#acceptChangeVCS');
  await page.click('#btnChangeVCS');
  await expect(page.locator('dialog#alertChangeVCS')).toBeVisible();
  await expect(page.locator('#btnChangeVCSExec')).toBeDisabled();
  await page.fill('#confirmVcsProjectName', projectName);

  const [changeResponse] = await Promise.all([
    page.waitForResponse(
      (res) => res.request().method() === 'POST' && res.url().endsWith(`/${adminLoginId}/${projectName}/changeVCS`)
    ),
    page.click('#btnChangeVCSExec'),
  ]);
  expect(changeResponse.status()).toBe(204);
  expect(changeResponse.request().postDataJSON()).toEqual({
    projectId: Number(projectId), projectName, expectedVcs: currentVcs, accepted: true,
  });
  // No Location header on success -> the JS module reloads the current page rather than
  // navigating; give that reload a moment to land before asserting on the settings screen.
  await page.waitForLoadState('networkidle');

  // setting.html never prints project.vcs as plain text (only in th:if conditionals) -- re-visit
  // changeVCS instead, whose h3 always renders "${project.vcs} -> ${nextVcs}" directly.
  await page.goto(`/${adminLoginId}/${projectName}/changeVCS`);
  const currentVcsAfterChange = await page.locator('h3 span').nth(0).textContent();
  expect(currentVcsAfterChange!.trim()).toBe(nextVcs!.trim());
  await expect(page.locator('#confirmVcsProjectName')).toHaveAttribute('data-project-id', projectId!);
  await page.goto(issueUrl);
  await expect(page.locator('body')).toContainText('Keep issue marker');
  await page.goto(postUrl);
  await expect(page.locator('body')).toContainText('Keep board marker');
});
