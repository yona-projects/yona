import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: GET /{owner}/{projectName}/reviews (reviewthread/list.html), ReviewThreadController.kt:52.
 * matrix.md previously mis-classified this as REST-only (confused with CommentThreadController's
 * REST resolve/open endpoints) and marked it `todo` -- it is a real server-rendered screen with its
 * own search/filter sidebar (all/participant/author tabs), an OPEN/CLOSED state tab bar, a text
 * filter form, and an XLS export link, all driving real query params back through the same GET
 * route. This project has no CommentThread (diff-anchored review thread) records seeded anywhere
 * else in the suite -- PR general/commit comments are a different domain object -- so this
 * exercises the real empty-state + filter/tab wiring rather than list-item rendering. */

test('reviews screen loads with the default (all reviews) filter', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  const response = await page.goto(`/${owner}/${name}/reviews`);
  expect(response?.status()).toBe(200);
  // .search-wrap li.active matches unrelated sidebar nav items elsewhere on the page -- scope to
  // this widget's own list, and check locale-independently (this environment renders English, not
  // Korean, for reasons unrelated to this test -- same surprise noted throughout this suite).
  await expect(page.locator('.search-wrap ul.lst-stacked li.active a[data-toggle="filter"]:not([data-type])')).toBeVisible();
  await expect(page.locator('#search')).toBeVisible();
});

test('the participant/author sidebar filters actually change the query string and stay on the page', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/reviews`);

  // data-toggle="filter" links are wired by inline JS to build a query string from data-type/
  // data-value and navigate -- click the "참여한 리뷰" (participant) tab and confirm the resulting
  // URL actually carries participantId, then confirm the tab now renders as active server-side
  // (proving the round trip actually changed server-rendered state, not just client-side class
  // toggling).
  const participantLink = page.locator('.search-wrap li a[data-type="participantId"]');
  const participantValue = await participantLink.getAttribute('data-value');
  expect(participantValue).toBeTruthy();

  await Promise.all([page.waitForURL(new RegExp(`participantId=${participantValue}`)), participantLink.click()]);
  await expect(page.locator(`.search-wrap li a[data-type="participantId"]`).locator('..')).toHaveClass(/active/);

  // Author tab
  const authorLink = page.locator('.search-wrap li a[data-type="authorId"]');
  const authorValue = await authorLink.getAttribute('data-value');
  await Promise.all([page.waitForURL(new RegExp(`authorId=${authorValue}`)), authorLink.click()]);
  await expect(page.locator(`.search-wrap li a[data-type="authorId"]`).locator('..')).toHaveClass(/active/);

  // Back to "all reviews" (the plain filter link with no data-type). The JS clears
  // authorId/participantId to empty strings rather than stripping the query string entirely, so
  // wait on that instead of an exact bare-path match.
  await Promise.all([
    page.waitForURL(/authorId=&participantId=/),
    page.locator('.search-wrap li a[data-toggle="filter"]:not([data-type])').click(),
  ]);
});

test('OPEN/CLOSED state tabs filter via real query params', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/reviews`);

  // Scope directly to the OPEN/CLOSED tab anchors' own parent <li> -- a bare `ul.nav-tabs
  // li.active` also matches an unrelated header/sidebar element on this page (e.g. the "Favorite"
  // panel item), causing a strict-mode violation.
  const openTab = page.locator('ul.nav-tabs a[data-type="state"][data-value="OPEN"]');
  await Promise.all([page.waitForURL(/state=OPEN/), openTab.click()]);
  await expect(page.locator('ul.nav-tabs a[data-type="state"][data-value="OPEN"]').locator('..')).toHaveClass(/active/);

  const closedTab = page.locator('ul.nav-tabs a[data-type="state"][data-value="CLOSED"]');
  await Promise.all([page.waitForURL(/state=CLOSED/), closedTab.click()]);
  const response = await page.goto(`/${owner}/${name}/reviews?state=CLOSED`);
  expect(response?.status()).toBe(200);
});

test('the text filter form actually submits a filter query param', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/reviews`);
  await page.fill('#search input[name="filter"]', 'no-such-review-keyword');
  await Promise.all([page.waitForURL(/filter=no-such-review-keyword/), page.click('#search button.search-btn')]);
  // Empty-state list (no CommentThread seeded anywhere matches this keyword) -- just confirm the
  // page still renders 200 with the submitted filter reflected back into the input.
  await expect(page.locator('#search input[name="filter"]')).toHaveValue('no-such-review-keyword');
});

test('XLS export link points at the real download route with current filters carried over', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/reviews?state=OPEN`);
  const exportLink = page.locator(`a[href*="/reviews"][href*="format=xls"]`);
  await expect(exportLink).toHaveCount(1);
  const href = await exportLink.getAttribute('href');
  expect(href).toContain('state=OPEN');

  const response = await page.request.get(href!);
  expect(response.status()).toBe(200);
  expect(response.headers()['content-disposition']).toContain('attachment');
});
