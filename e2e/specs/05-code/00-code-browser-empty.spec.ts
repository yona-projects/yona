import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screens: GET /{owner}/{projectName}/code (code/view.html or code/nohead.html for a
 * zero-commit repo -- a freshly UI-created project has no commits yet, exactly the state this
 * spec exercises), /branches, /tags, /commits. `00-` prefix guarantees this runs before
 * 01-code-git-setup.spec.ts pushes real commits into the same seeded project. */

test('code tab of a brand-new (zero-commit) project shows the empty-repo guidance', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/code`);
  // nohead.html's guidance renders instead of a file tree when there are zero commits.
  await expect(page.locator('body')).toContainText(/clone|push|commit/i);
});

test('branches screen loads for a zero-commit project without erroring', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  const response = await page.goto(`/${owner}/${name}/branches`);
  expect(response?.status()).toBeLessThan(500);
});

test('tags screen loads for a zero-commit project without erroring', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  const response = await page.goto(`/${owner}/${name}/tags`);
  expect(response?.status()).toBeLessThan(500);
});

test('commit history screen loads for a zero-commit project without erroring', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  const response = await page.goto(`/${owner}/${name}/commits`);
  expect(response?.status()).toBeLessThan(500);
});
