import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

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

test('organization-scoped search finds a real project actually owned by that organization', async ({ page }) => {
  // 03-organization always runs before 11-search in full-suite folder execution order (03 < 11
  // alphabetically) -- seed.orgName is guaranteed to exist here. This was previously a
  // `test.skip` guard written during this suite's concurrent multi-fork construction phase, when
  // relative execution order across specs authored in parallel forks wasn't guaranteed; that
  // phase is over, so require the seed instead of silently skipping when it's actually always
  // present now. seed.orgName also names a real org-owned project by now
  // (organization-crud.spec.ts's own rollup-verification test creates one), so search for it by
  // its "e2e-org-" prefix and confirm a real hit, not just a non-500 response.
  const orgName = requireSeed('orgName');

  const response = await page.goto(`/org/${orgName}/search?keyword=e2e-org-&searchType=auto`);
  expect(response?.status()).toBeLessThan(500);
  await expect(page.locator('body')).toContainText(/e2e-org-/);
});
