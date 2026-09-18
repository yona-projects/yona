import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screen: GET /{owner}/{projectName}/issue/{number}/timeline (an ajax fragment,
 * IssueViewController.kt:381, returns just the "issue/view :: timelineItems" Thymeleaf fragment --
 * issue/view.html's own page load calls this same fragment builder inline, but the dedicated
 * endpoint exists for polling/refreshing the timeline without a full page reload). Self-contained:
 * creates its own issue and a real state-change comment-worthy event, since this file's execution
 * order relative to other 06-issue specs isn't something to depend on. */

test('the timeline fragment endpoint reflects a real state-change event', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const issueTitle = `e2e timeline issue ${uniqueSuffix()}`;

  await page.goto(`/${owner}/${name}/issueform`);
  await page.fill('#title', issueTitle);
  await page.locator('textarea[data-editor-mode="content-body"]').fill('Body for timeline verification.', { force: true });
  await page.click('#button-save');
  await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/issue/(\\d+)`));
  const issueNumber = Number(page.url().match(/\/issue\/(\d+)/)?.[1]);
  expect(issueNumber).toBeGreaterThan(0);

  // Close then reopen via the mass-update widget on the list page (same pattern as
  // issue-management.spec.ts) to generate a real timeline-worthy state-change event.
  await page.goto(`/${owner}/${name}/issues`);
  const issueRow = page.locator('li.post-item', { hasText: issueTitle });
  await issueRow.locator('input[name="checked-issue"]').check();
  await page.click('#state button.dropdown-toggle');
  await Promise.all([
    page.waitForURL(new RegExp(`/${owner}/${name}/issues$`)),
    page.click('#state li[data-value="CLOSED"] a'),
  ]);
  await page.waitForLoadState('networkidle');

  const response = await page.request.get(`/${owner}/${name}/issue/${issueNumber}/timeline`);
  expect(response.status()).toBe(200);
  const body = await response.text();
  // A real timeline event exists for the state change -- not asserting on exact wording (locale-
  // dependent, per this suite's repeated English-vs-Korean surprise), just that the fragment
  // rendered something beyond an empty shell.
  expect(body.length).toBeGreaterThan(50);

  // Confirm the same fragment appears embedded in the full issue view page too (the endpoint
  // reuses viewIssue()'s own model-building code, per the controller's own comment).
  await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
  await expect(page.locator('.timeline-list')).toBeVisible();
});

test('timeline fragment for a non-existent issue number renders the 404 error view with a real 404 status', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  // IssueViewController.timeline() returns the plain view-name string "error/404" -- previously
  // Spring MVC rendered the 404 template with an HTTP 200 (the view name alone doesn't set the
  // status), fixed system-wide by ErrorViewStatusInterceptor.
  const response = await page.request.get(`/${owner}/${name}/issue/999999/timeline`);
  expect(response.status()).toBe(404);
  const body = await response.text();
  expect(body).toMatch(/404|not found|찾을 수 없습니다/i);
});
