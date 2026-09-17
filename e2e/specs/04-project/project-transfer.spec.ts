import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: GET /{owner}/{projectName}/transfer (project/transfer.html). Load + fill the
 * destination field only -- never confirm the transfer (owner is the seed for 05/06/07 specs). */

test('transfer screen loads with a destination field and confirm checkbox', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/transfer`);
  await page.fill('#owner', 'someone-else');
  await expect(page.locator('#accept')).toBeVisible();
  await expect(page.locator('#btnTransfer')).toBeVisible();
});
