import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { afterReload, documentReady } from '../../support/ready';
import { uniqueSuffix } from '../../support/unique';

/** Screens: issue/view.html's "댓글 등록하고 이슈 닫기/다시 열기" button (legacy IssueApp.newComment의
 * withStateTransition) and the "이슈 닫기/다시 열기" link (legacy IssueApp.nextState). Uses its OWN issue so the
 * state changes cannot affect the other 06-issue specs that depend on seed.issueNumber. */

let issueNumber: number;
const issueTitle = `E2E comment-state issue ${uniqueSuffix()}`;

async function fillComment(page: import('@playwright/test').Page, value: string) {
  await page.locator('#comment-form textarea[data-editor-mode="comment-body"]').evaluate(
    (el: HTMLTextAreaElement, text: string) => {
      el.value = text;
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    },
    value
  );
}

test.describe.serial('issue comment with state transition', () => {
  test('create a dedicated issue', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/issueform`);
    await page.fill('#title', issueTitle);
    await page.locator('textarea[data-editor-mode="content-body"]').fill('Body for comment-state test.', { force: true });
    await page.click('#button-save');

    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/issue/\\d+`));
    issueNumber = Number(page.url().match(/\/issue\/(\d+)/)?.[1]);
    expect(issueNumber).toBeGreaterThan(0);
  });

  test('"comment and close" posts the comment and closes the issue in one step', async ({ page }) => {
    test.setTimeout(60_000);
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const body = `closing comment ${uniqueSuffix()}`;

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    await documentReady(page);
    await expect(page.locator('.badge-issue-open').first()).toBeVisible();

    await fillComment(page, body);
    const button = page.locator('#comment-and-next-state');
    await expect(button).toBeVisible();
    await afterReload(page, () => button.click());

    await expect(page.locator('body')).toContainText(body);
    await expect(page.locator('.badge-issue-closed').first()).toBeVisible();
  });

  test('"comment and reopen" posts the comment and reopens the closed issue', async ({ page }) => {
    test.setTimeout(60_000);
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const body = `reopening comment ${uniqueSuffix()}`;

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    await documentReady(page);
    await expect(page.locator('.badge-issue-closed').first()).toBeVisible();

    await fillComment(page, body);
    await afterReload(page, () => page.locator('#comment-and-next-state').click());

    await expect(page.locator('body')).toContainText(body);
    await expect(page.locator('.badge-issue-open').first()).toBeVisible();
  });

  test('the plain "close issue" link toggles the state via nextstate', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    await documentReady(page);
    await Promise.all([
      page.waitForURL(new RegExp(`/${owner}/${name}/issue/${issueNumber}$`)),
      page.locator(`a[href$="/issue/${issueNumber}/nextstate"]`).click(),
    ]);
    await expect(page.locator('.badge-issue-closed').first()).toBeVisible();
  });

  test('delete the dedicated issue', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    await page.click('a[href="#deleteConfirm"]');
    await expect(page.locator('dialog#deleteConfirm')).toBeVisible();
    const [deleteResponse] = await Promise.all([
      page.waitForResponse((res) => res.request().method() === 'DELETE' && res.url().includes(`/issues/${issueNumber}`)),
      page.waitForNavigation({ waitUntil: 'load' }),
      page.click('dialog#deleteConfirm button[data-issue-delete]'),
    ]);
    expect(deleteResponse.ok()).toBeTruthy();
  });
});
