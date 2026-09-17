import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screen: GET /new/import (project/importing.html). Load + fill only -- an actual git import
 * depends on reaching a real external host, which is network-flaky and out of scope here. */

test('import form accepts a git URL, owner, and project name', async ({ page }) => {
  const owner = requireSeed('adminLoginId');

  await page.goto('/new/import');
  await page.fill('#url', 'https://example.invalid/some/repo.git');
  await page.selectOption('#project-owner', owner);
  await page.fill('#project-name', `e2e-import-${uniqueSuffix()}`);
  await expect(page.locator('#importGit')).toBeVisible();
});

test('blank url is rejected server-side', async ({ page }) => {
  const owner = requireSeed('adminLoginId');

  await page.goto('/new/import');
  await page.selectOption('#project-owner', owner);
  await page.fill('#project-name', `e2e-import-blank-${uniqueSuffix()}`);
  // The template's <button> has no explicit type="submit" attribute (it just relies on the
  // implicit default), so the CSS attribute selector `button[type=submit]` never matches it.
  await page.click('#importGit button.ybtn-success');

  // ImportViewController re-renders project/importing on any validation error instead of
  // redirecting to the new project.
  await expect(page).toHaveURL(/\/new\/import/);
});
