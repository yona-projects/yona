import { test, expect } from '@playwright/test';
import { readSeed, requireSeed } from '../../support/seed-store';

/**
 * Screens: GET /search, GET /{owner}/{projectName}/search, GET /org/{orgName}/search
 * (SearchController.kt -- all three require a non-blank `keyword` or the server responds 400).
 *
 * The header's `<form name="gnb-search-form">` action is server-rendered per page (plain
 * `/search` when the page has no project/org in its model, `/{owner}/{projectName}/search` on
 * project-scoped pages, etc.) -- `name` is the one constant across every page, so that is what
 * locates it, not `action`.
 */

test('header search box submits a global keyword search', async ({ page }) => {
  await page.goto('/');
  const searchForm = page.locator('form[name="gnb-search-form"]');
  await searchForm.locator('input[name="keyword"]').fill('e2e');
  await searchForm.locator('button[type=submit]').click();

  await expect(page).toHaveURL(/\/search\?/);
  await expect(page.locator('body')).not.toContainText('Bad Request');
});

test('blank keyword is rejected with 400 rather than a silent empty page', async ({ page }) => {
  const response = await page.goto('/search?keyword=&searchType=auto');
  expect(response?.status()).toBe(400);
});

test('project-scoped search finds the seeded issue by title', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  // Direct navigation with query params instead of driving the header form's scope dropdown --
  // the dropdown only reassigns the form's `action` via JS (see site/layout.html), the request
  // shape is identical either way.
  const response = await page.goto(`/${owner}/${name}/search?keyword=E2E&searchType=auto`);
  expect(response?.status()).toBeLessThan(500);
  // The 06-issue spec's issue was edited to "E2E seed issue (edited)" -- confirms the search
  // actually indexed/matched real content, not just that the page rendered.
  await expect(page.locator('body')).toContainText(/E2E seed issue/);
});

test('organization-scoped search loads without erroring (skipped if no org seeded yet)', async ({ page }) => {
  const orgName = readSeed().orgName;
  test.skip(!orgName, '03-organization spec has not seeded an organization (parallel authoring, no ordering guarantee against this spec)');

  const response = await page.goto(`/org/${orgName}/search?keyword=e2e&searchType=auto`);
  expect(response?.status()).toBeLessThan(500);
});
