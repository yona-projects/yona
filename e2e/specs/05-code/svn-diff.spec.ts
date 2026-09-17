import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/**
 * Screens: GET /{owner}/{projectName}/commits, /commit/{revision} (code/svnDiff.html --
 * CodeViewController.showCommit() branches here specifically for project.vcs == SUBVERSION, using
 * repository.getPatch(commitId) instead of getDiff()), /compare/{revA}..{revB} (code/compare_svn.html
 * -- CompareViewController.compare() branches the same way, using getPatch(revA, revB)), /branches,
 * /code -- all against the real SVN revisions committed by 01-hg-svn-code-setup.spec.ts.
 *
 * SVN commit ids are plain revision numbers (not hashes). /branches is actually git-only by
 * design (BranchViewController.branches() hard-codes vcsType != "GIT" -> error/400,
 * "error.badrequest.only.available.for.git" -- confirmed against the real hg project too, see
 * hg-diff.spec.ts), not merely "SVN has nothing to list" -- the assertion below checks for that
 * real error body rather than treating a 200 as success-with-branch-data.
 */

test('commit history lists both real svn commits', async ({ page }) => {
  const owner = requireSeed('svnProjectOwner');
  const name = requireSeed('svnProjectName');

  await page.goto(`/${owner}/${name}/commits`);
  await expect(page.locator('body')).toContainText(/E2E: initial svn commit/);
  await expect(page.locator('body')).toContainText(/E2E: second svn commit/);
});

test('commit detail page renders the real svnDiff patch for the second revision', async ({ page }) => {
  const owner = requireSeed('svnProjectOwner');
  const name = requireSeed('svnProjectName');
  const revNew = requireSeed('svnRevNew') as number;

  await page.goto(`/${owner}/${name}/commit/${revNew}`);
  // The second revision changed the note file's content from "v1" to "v2" -- the patch text
  // rendered by code/svnDiff.html must show the new line, proving getPatch() returned the real
  // unified diff (not a blank/error page that happened to return 200).
  await expect(page.locator('body')).toContainText(/v2/);
});

test('compare view shows the patch between the two svn revisions', async ({ page }) => {
  const owner = requireSeed('svnProjectOwner');
  const name = requireSeed('svnProjectName');
  const revOld = requireSeed('svnRevOld') as number;
  const revNew = requireSeed('svnRevNew') as number;

  await page.goto(`/${owner}/${name}/compare/${revOld}..${revNew}`);
  await expect(page.locator('body')).toContainText(/v2/);
});

test('branches screen is git-only and rejects svn projects with a real error message', async ({ page }) => {
  const owner = requireSeed('svnProjectOwner');
  const name = requireSeed('svnProjectName');

  const response = await page.goto(`/${owner}/${name}/branches`);
  expect(response?.status()).toBe(200);
  await expect(page.locator('body')).toContainText('This request is only supported in a git project.');
});

test('code browser shows the file tree once the repo has real commits', async ({ page }) => {
  const owner = requireSeed('svnProjectOwner');
  const name = requireSeed('svnProjectName');

  await page.goto(`/${owner}/${name}/code`);
  await expect(page.locator('body')).toContainText(/e2e-svn-note-/);
});
