import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/**
 * Screens: GET /{owner}/{projectName}/commits, /commit/{commitId} (code/diff.html -- shared with
 * git, CodeViewController.showCommit() only branches to code/svnDiff.html when project.vcs is
 * SUBVERSION), /compare/{revA}..{revB} (code/compare.html), /branches, /code, all against the
 * real hg changesets pushed by 01-hg-svn-code-setup.spec.ts.
 */

test('commit history lists both real hg commits', async ({ page }) => {
  const owner = requireSeed('hgProjectOwner');
  const name = requireSeed('hgProjectName');

  await page.goto(`/${owner}/${name}/commits`);
  await expect(page.locator('body')).toContainText(/E2E: initial hg commit/);
  await expect(page.locator('body')).toContainText(/E2E: second hg commit/);
});

test('commit detail page renders the real diff for the second hg commit', async ({ page }) => {
  const owner = requireSeed('hgProjectOwner');
  const name = requireSeed('hgProjectName');
  const commitNew = requireSeed('hgCommitNew') as string;

  await page.goto(`/${owner}/${name}/commit/${commitNew}`);
  // The second commit changed the note file's content from "v1" to "v2" -- the diff view must
  // show the new line, proving getDiff() actually rendered the real changeset (not a blank/error
  // page that happened to return 200).
  await expect(page.locator('body')).toContainText(/v2/);
});

test('compare view shows the diff between the two hg changesets', async ({ page }) => {
  const owner = requireSeed('hgProjectOwner');
  const name = requireSeed('hgProjectName');
  const commitOld = requireSeed('hgCommitOld') as string;
  const commitNew = requireSeed('hgCommitNew') as string;

  await page.goto(`/${owner}/${name}/compare/${commitOld}..${commitNew}`);
  await expect(page.locator('body')).toContainText(/v2/);
});

test('branches screen is git-only and rejects hg projects with a real error message', async ({ page }) => {
  // BranchViewController.branches() hard-codes vcsType != "GIT" -> error/400 with messageKey
  // "error.badrequest.only.available.for.git" -- this is not a gap in this VCS's support, it is
  // the real, intentional legacy behavior (faithfully ported from @IsOnlyGitAvailableAction).
  // Like /migration (see matrix.md's note on that screen), returning a view name doesn't set the
  // HTTP status in this codebase, so the response is 200 even though it's rendering the "only
  // available for git" error body.
  const owner = requireSeed('hgProjectOwner');
  const name = requireSeed('hgProjectName');

  const response = await page.goto(`/${owner}/${name}/branches`);
  expect(response?.status()).toBe(200);
  await expect(page.locator('body')).toContainText('This request is only supported in a git project.');
});

test('code browser shows the file tree once the repo has real commits', async ({ page }) => {
  const owner = requireSeed('hgProjectOwner');
  const name = requireSeed('hgProjectName');

  await page.goto(`/${owner}/${name}/code`);
  await expect(page.locator('body')).toContainText(/e2e-hg-note-/);
});
