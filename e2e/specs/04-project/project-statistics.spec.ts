import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: GET /{owner}/{projectName}/statistics (project/statistics.html). */

test('statistics screen loads for a PUBLIC project', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  const response = await page.goto(`/${owner}/${name}/statistics`);
  expect(response?.status()).toBeLessThan(400);
});
