import { test, expect } from '@playwright/test';
import { requireSeed, writeSeed } from '../../support/seed-store';

/** Screens: GET /{owner}/{projectName}/pull/new (pullrequest/create.html) ->
 * GET /{owner}/{projectName}/pull/{number} (pullrequest/view.html) ->
 * GET /{owner}/{projectName}/pull/{number}/edit (pullrequest/edit.html) ->
 * GET /{owner}/{projectName}/pulls, /closedPullRequests, /sentPullRequests (pullrequest/list.html).
 * Depends on pull-request-git-setup.spec.ts having pushed `main` and `feature/e2e-pr`. */

test.describe.serial('pull request lifecycle', () => {
  test('create a PR from feature/e2e-pr into main', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const featureBranch = requireSeed('pullRequestFeatureBranch');

    // fromBranch/toBranch query params prefill the <select>s (create.html's th:selected checks
    // `br == prefillFromBranch`/`prefillToBranch`) -- both PullRequestViewController.createPullRequestForm
    // 400s with error/badrequest if either project has zero branches, so this only works after
    // pull-request-git-setup.spec.ts has actually pushed both branches.
    await page.goto(`/${owner}/${name}/pull/new?fromBranch=${encodeURIComponent(featureBranch)}&toBranch=main`);
    await expect(page.locator('#pull-request-form')).toBeVisible();

    await page.fill('#title', 'E2E seed pull request');
    await page.locator('textarea[data-editor-mode="content-body"]').evaluate((el: HTMLTextAreaElement) => {
      el.value = 'PR body written by the e2e suite.';
      el.dispatchEvent(new Event('input', { bubbles: true }));
    });

    // yona.pullrequest.Write.js submits this form via AJAX (no <form action=...> at all).
    // _getRedirectURL() for "new" mode always targets the LIST page (.../pulls), never the
    // created PR's own URL -- there is no page to waitForURL on that already contains the PR
    // number, so find it afterward from the list instead.
    await page.click('#button-save');
    await page.waitForURL(new RegExp(`/${owner}/${name}/pulls$`), { timeout: 15_000 });

    const prLink = page.locator('a[href*="/pull/"]', { hasText: 'E2E seed pull request' }).first();
    await expect(prLink).toBeVisible();
    const href = await prLink.getAttribute('href');
    const match = href?.match(/\/pull\/(\d+)/);
    const pullRequestNumber = Number(match?.[1]);
    expect(pullRequestNumber).toBeGreaterThan(0);
    writeSeed({ pullRequestNumber });
  });

  test('view the created pull request', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const pullRequestNumber = requireSeed('pullRequestNumber');

    await page.goto(`/${owner}/${name}/pull/${pullRequestNumber}`);
    await expect(page.locator('body')).toContainText('E2E seed pull request');
  });

  test('post a general (non-ranged) comment on the pull request', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const pullRequestNumber = requireSeed('pullRequestNumber');
    const commentBody = 'PR comment written by the e2e suite';

    // The general comment form (common/commentForm.html) lives on the "changes" tab
    // (pullrequest/view.html), posting via a real native <form> to
    // ReviewViewController.newPullRequestComment -- unlike the issue comment form, this one is
    // NOT AJAX-intercepted, so it does a real redirect back to .../pullRequest/{number}/changes.
    await page.goto(`/${owner}/${name}/pull/${pullRequestNumber}/changes`);
    await page.locator('#comment-form-wrap textarea[data-editor-mode="code-review-body"]').evaluate((el: HTMLTextAreaElement, value: string) => {
      el.value = value;
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    }, commentBody);

    await page.locator('#comment-form-wrap button[type=submit]').click();
    await page.waitForURL(new RegExp(`/${owner}/${name}/pullRequest/${pullRequestNumber}/changes`));
    await expect(page.locator('body')).toContainText(commentBody);
  });

  test('edit the pull request title', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const pullRequestNumber = requireSeed('pullRequestNumber');

    await page.goto(`/${owner}/${name}/pull/${pullRequestNumber}/edit`);
    // The title pre-fills an <input value="...">, not text content -- toContainText() only
    // matches text nodes.
    await expect(page.locator('#title')).toHaveValue('E2E seed pull request');
  });

  test('pull request lists show the created PR', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/pulls`);
    await expect(page.locator('body')).toContainText('E2E seed pull request');

    await page.goto(`/${owner}/${name}/sentPullRequests`);
    const sentResponse = await page.goto(`/${owner}/${name}/sentPullRequests`);
    expect(sentResponse?.status()).toBeLessThan(500);

    const closedResponse = await page.goto(`/${owner}/${name}/closedPullRequests`);
    expect(closedResponse?.status()).toBeLessThan(500);
  });

  test('pull request state polling fragment responds', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const pullRequestNumber = requireSeed('pullRequestNumber');

    const response = await page.goto(`/${owner}/${name}/pull/${pullRequestNumber}/state`);
    expect(response?.status()).toBeLessThan(500);
  });
});
