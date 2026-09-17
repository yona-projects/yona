import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: GET /{owner}/{projectName}/changeVCS (project/change_vcs.html). Load-only -- actually
 * submitting would change the seeded GIT project's VCS type, breaking every 05-code/06-issue/
 * 07-pull-request spec that assumes it stays GIT. */

test('change VCS screen loads and shows the next VCS in the cycle', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  const response = await page.goto(`/${owner}/${name}/changeVCS`);
  expect(response?.status()).toBeLessThan(400);
  // No <form> here -- it's a checkbox + confirm-dialog pattern (#acceptChangeVCS / #btnChangeVCS),
  // not a plain form submission.
  await expect(page.locator('#acceptChangeVCS')).toBeVisible();
  await expect(page.locator('#btnChangeVCS')).toBeVisible();
});
