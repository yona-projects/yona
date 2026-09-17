import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: GET /{owner}/{projectName}/deleteform (project/delete.html). Load-only -- never
 * confirm the delete (this project is the seed for 05/06/07 specs). */

test('delete screen loads with a confirm checkbox and delete button', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/deleteform`);
  await expect(page.locator('#accept')).toBeVisible();
  await expect(page.locator('#btnDelete')).toBeVisible();
});
