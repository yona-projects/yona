import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: GET /{owner}/{projectName}/statistics (project/statistics.html). Checked the template
 * directly: it is a literal stub ("Under Construction", no chart library, no model data bound at
 * all) -- there is no real chart/commit/issue data to verify here yet, so "screen loads" is
 * actually the full extent of this screen's current behavior, not an under-tested gap. */

test('statistics screen loads for a PUBLIC project (currently an intentional "Under Construction" stub, not yet real data)', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  const response = await page.goto(`/${owner}/${name}/statistics`);
  expect(response?.status()).toBeLessThan(400);
  await expect(page.locator('body')).toContainText('Under Construction');
});
