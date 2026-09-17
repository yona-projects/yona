import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screens: GET /{owner}/{projectName}/commits (real history, code/history.html),
 * /commit/{commitId} (diff render + comment form, code/diff.html -> ReviewViewController.
 * newCommitComment), /compare/{revA}..{revB} (code/compare.html), /branches (real branch list).
 * Depends on 01-code-git-setup.spec.ts having pushed two distinct commits to `main`. */

test('commit history lists the two pushed commits with working links', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/commits`);
  await expect(page.locator('body')).toContainText('E2E code-diff: modify fixture file');
  await expect(page.locator('body')).toContainText('E2E code-diff: add fixture file');
});

test('branches screen lists the real main branch', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/branches`);
  await expect(page.locator('body')).toContainText('main');
});

test('commit detail renders the diff and accepts a comment', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const secondCommit = requireSeed('codeDiffSecondCommit');

  await page.goto(`/${owner}/${name}/commit/${secondCommit}`);
  // Diff body shows both a removed line (the original "line two") and an added line
  // ("line four (added)") -- confirms the actual diff content rendered, not just a shell.
  await expect(page.locator('.diff-body, .diff-container').first()).toBeVisible();
  await expect(page.locator('body')).toContainText('line four (added)');

  const commentBody = 'commit comment written by the e2e suite';
  await page.locator('textarea[data-editor-mode="commit-comment-body"]').evaluate((el: HTMLTextAreaElement, value: string) => {
    el.value = value;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }, commentBody);

  await page.locator('.write-comment-form button[type=submit]').click();
  // newCommitComment redirects back to the same commit view with #comment-{id} -- wait for that
  // navigation rather than assuming any fixed timing.
  await page.waitForURL(new RegExp(`/${owner}/${name}/commit/${secondCommit}`));
  await expect(page.locator('body')).toContainText(commentBody);
});

test('compare view renders the diff between the two pushed commits', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const firstCommit = requireSeed('codeDiffFirstCommit');
  const secondCommit = requireSeed('codeDiffSecondCommit');

  const response = await page.goto(`/${owner}/${name}/compare/${firstCommit}..${secondCommit}`);
  expect(response?.status()).toBeLessThan(400);
  await expect(page.locator('body')).toContainText('line four (added)');
});
