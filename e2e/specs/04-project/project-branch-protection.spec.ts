import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screen: GET/POST /projects/{owner}/{projectName}/branch-protections
 * (project/setting_branch_protection.html). */

test('add a branch protection rule with every checkbox toggled on', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const pattern = `release/e2e-${uniqueSuffix()}`;

  await page.goto(`/projects/${owner}/${name}/branch-protections`);
  await page.fill('#formNewBranchProtection #branchPattern', pattern);
  await page.check('#formNewBranchProtection #requirePullRequest');
  await page.fill('#formNewBranchProtection #requireApprovals', '2');
  await page.check('#formNewBranchProtection #requireSignedCommits');
  await page.fill('#formNewBranchProtection #restrictPushTo', 'admin');
  await page.check('#formNewBranchProtection #doNotAllowBypassing');
  await page.check('#formNewBranchProtection #allowForcePush');
  await page.check('#formNewBranchProtection #allowDeletions');
  await page.click('#formNewBranchProtection button[type=submit]');

  await expect(page).toHaveURL(new RegExp(`/projects/${owner}/${name}/branch-protections$`));
  // Each existing rule renders as an inline-edit <input value="..."> (setting_branch_protection.html),
  // not as plain text -- toContainText() checks text nodes, which this input's value never is.
  await expect(page.locator(`input[name="branchPattern"][value="${pattern}"]`)).toHaveCount(1);
});

test('blank branch pattern is rejected (server-side 400)', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/projects/${owner}/${name}/branch-protections`);
  const response = await page.locator('#formNewBranchProtection').evaluate((form: HTMLFormElement) => {
    // The pattern field is `required` client-side; assert the server-side guard directly via a
    // raw fetch to bypass that and confirm BranchProtectionController.validateBranchPattern()
    // itself rejects a blank pattern, not just the browser's own HTML5 validation.
    return fetch(form.action, { method: 'POST', body: new URLSearchParams({ branchPattern: '' }) }).then((r) => r.status);
  });
  expect(response).toBe(400);
});
