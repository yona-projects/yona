import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screen: GET/POST /{owner}/{projectName}/setting (project/setting.html). The form has
 * `onsubmit="return false"` -- the real save is an AJAX PUT-ish call to /api/projects/{id}
 * triggered by JS, so there is no page navigation to assert on; instead this reloads the page
 * afterwards and checks the new value actually persisted. */

test('editing the description persists after save', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const overview = `updated by e2e ${uniqueSuffix()}`;

  await page.goto(`/${owner}/${name}/setting`);
  await page.fill('#project-desc', overview);
  await page.click('#saveSetting #save');

  await page.waitForTimeout(500); // let the fire-and-forget AJAX save land
  await page.goto(`/${owner}/${name}/setting`);
  await expect(page.locator('#project-desc')).toHaveValue(overview);
});

test('menu toggles (issue/wiki/board/milestone/review/pullRequest) are present and checked by default', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/setting`);
  for (const id of ['menuSettingIssue', 'menuSettingPullRequest', 'menuSettingReview', 'menuSettingMilestone', 'menuSettingBoard', 'menuSettingWiki']) {
    await expect(page.locator(`#${id}`)).toBeVisible();
  }
});

test('turning a menu toggle off actually persists, and turning it back on restores it', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  // menuSettingReview: no later spec in this suite depends on the review menu being enabled
  // (unlike issue/wiki/board/milestone/pullRequest, which 06-10 seed against), so it is the
  // safest toggle to flip without risking a knock-on failure in another folder.
  await page.goto(`/${owner}/${name}/setting`);
  await expect(page.locator('#menuSettingReview')).toBeChecked();
  await page.locator('#menuSettingReview').uncheck();
  await page.click('#saveSetting #save');
  await page.waitForTimeout(500);

  await page.goto(`/${owner}/${name}/setting`);
  await expect(page.locator('#menuSettingReview')).not.toBeChecked();

  await page.locator('#menuSettingReview').check();
  await page.click('#saveSetting #save');
  await page.waitForTimeout(500);

  await page.goto(`/${owner}/${name}/setting`);
  await expect(page.locator('#menuSettingReview')).toBeChecked();
});
