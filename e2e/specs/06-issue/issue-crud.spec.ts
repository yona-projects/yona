import { test, expect } from '@playwright/test';
import { requireSeed, writeSeed } from '../../support/seed-store';

/** Screens: GET /{owner}/{projectName}/issueform (issue/create.html) ->
 * GET /{owner}/{projectName}/issue/{number} (issue/view.html) ->
 * GET /{owner}/{projectName}/issue/{number}/editform (issue/edit.html) ->
 * GET /{owner}/{projectName}/issues (issue/list.html). Depends on 04-project's seeded project. */

test.describe.serial('issue lifecycle', () => {
  test('create an issue with title and body', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/issueform`);
    await page.fill('#title', 'E2E seed issue');
    // The markdown editor widget visually overlays this <textarea name="body"> (it's the real
    // field the form submits), so Playwright's actionability check ("is it visible") never
    // passes on it -- force bypasses that and sets the value the form actually reads.
    await page.locator('textarea[data-editor-mode="content-body"]').fill('Body written by the e2e suite.', { force: true });
    await page.click('#button-save');

    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/issue/\\d+`));
    const match = page.url().match(/\/issue\/(\d+)/);
    const issueNumber = Number(match?.[1]);
    expect(issueNumber).toBeGreaterThan(0);
    writeSeed({ issueNumber });
  });

  test('view the created issue', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const issueNumber = requireSeed('issueNumber');

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    await expect(page.locator('body')).toContainText('E2E seed issue');
  });

  test('post a comment on the issue', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const issueNumber = requireSeed('issueNumber');
    const commentBody = 'issue comment written by the e2e suite';

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    // #comment-form's th:action is dead code (the template's own comment above the submit
    // handler says so): the real submission is a fetch() AJAX POST to commentApiBase
    // (/api/projects/{id}/issues/{number}/comments) wired by JS on the form's submit event,
    // which reloads the page on success.
    await page.locator('#comment-form textarea[data-editor-mode="comment-body"]').evaluate((el: HTMLTextAreaElement, value: string) => {
      el.value = value;
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    }, commentBody);

    const [response] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/comments') && res.request().method() === 'POST'),
      page.locator('#comment-form button[type=submit]').click(),
    ]);
    expect(response.ok()).toBeTruthy();
    await expect(page.locator('body')).toContainText(commentBody);
  });

  test('edit the issue title', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const issueNumber = requireSeed('issueNumber');

    await page.goto(`/${owner}/${name}/issue/${issueNumber}/editform`);
    await page.fill('#title', 'E2E seed issue (edited)');
    await page.click('#button-save');

    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/issue/${issueNumber}`));
    await expect(page.locator('body')).toContainText('E2E seed issue (edited)');
  });

  test('issue list shows the created issue', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/issues`);
    await expect(page.locator('body')).toContainText('E2E seed issue (edited)');
  });
});

// PRODUCT GAP, not a test bug: #title has no `required` attribute and IssueApiController has no
// server-side blank-title rejection either -- submitting with an empty title actually creates an
// issue with an empty title. This test documents that real behavior rather than the (wrong)
// assumption that it would be rejected -- see matrix.md's 06-issue row.
test('blank issue title is currently accepted (documents a validation gap, not desired behavior)', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/issueform`);
  await page.click('#button-save');
  await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/issue/\\d+$`));
});
