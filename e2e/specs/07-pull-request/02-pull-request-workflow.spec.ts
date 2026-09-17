import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screen: pullrequest/view.html action buttons NOT covered by 01-pull-request-crud.spec.ts --
 * assignee/label editing, reviewer self-registration, review verdicts (approve/request-changes/
 * comment), close/reopen, and the actual merge. Runs against the same seed.pullRequestNumber PR
 * created by 01-pull-request-crud.spec.ts, consuming it as its very last action (merge), since
 * merge is irreversible.
 *
 * Reviewer self-registration (#btn-review/#btn-unreview, the #reviewers avatar strip) is gated by
 * project.isUsingReviewerCount, which defaults to false -- this spec enables it once via the real
 * project/setting.html UI (same AJAX save flow as project-settings.spec.ts) before exercising it.
 *
 * Uses a DEDICATED throwaway reviewer account (signed up and invited as a project member here),
 * not the shared seed.secondUserLoginId -- that account's password is mutated in place by
 * 02-user/user-settings.spec.ts (which may be running concurrently in another worker/fork against
 * the same live server), so relying on it here would be racy. A fresh account owned entirely by
 * this file has no such interference. Reviewer/review/assignee/label actions all require project
 * membership (PullRequestController.checkWritePermission), and SelfReviewException blocks the PR's
 * own contributor (admin) from submitting APPROVE/REQUEST_CHANGES on their own PR, so a
 * non-contributor member is required for the approve step. */

async function signUpAndInviteReviewer(adminPage: import('@playwright/test').Page, browser: import('@playwright/test').Browser, owner: string, name: string) {
  const suffix = uniqueSuffix();
  const loginId = `e2erevwr${suffix}`;
  const password = 'ReviewerPassw0rd!';

  // Fresh, isolated context for the anonymous signup. clearCookies() is required even on a
  // brand-new context: observed in this environment that browser.newContext() can otherwise still
  // carry over the admin `remember-me` cookie (a plain cookie-less curl never receives it, so this
  // looks like a Playwright/Chromium context-isolation quirk here, not a yona bug) -- without the
  // clear, signup silently happens as admin instead of anonymously.
  const signupContext = await browser.newContext();
  await signupContext.clearCookies();
  const signupPage = await signupContext.newPage();
  await signupPage.goto('/signup');
  await signupPage.fill('#loginId', loginId);
  await signupPage.fill('#uname', `E2E Reviewer ${suffix}`);
  await signupPage.fill('#email', `${loginId}@yona-e2e.test`);
  await signupPage.fill('#password', password);
  await signupPage.fill('#retypedPassword', password);
  await signupPage.click('form[action="/signup"] button[type=submit]');
  await signupPage.waitForLoadState('networkidle');
  await signupContext.close();

  await adminPage.goto(`/${owner}/${name}/members`);
  await adminPage.fill('#addNewMember #loginId', loginId);
  await adminPage.click('#addNewMember button[type=submit]');
  await adminPage.waitForLoadState('networkidle');
  await expect(adminPage.locator(`.member-id:text-is("@${loginId}")`)).toBeVisible();

  return { loginId, password };
}

// Set by the first test below, read by the reviewer-actions test further down -- safe because
// test.describe.serial runs every test in this file in one worker, in order.
let reviewerCredentials: { loginId: string; password: string } | undefined;

test.describe.serial('pull request full workflow (assignee/labels/reviews/close/reopen/merge)', () => {
  test('enable reviewer-count, then sign up and invite a dedicated reviewer', async ({ page, browser }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/setting`);
    await page.check('#reviewerCountEnable');
    await page.click('#saveSetting #save');
    await page.waitForTimeout(500);

    await page.goto(`/${owner}/${name}/setting`);
    await expect(page.locator('#reviewerCountEnable')).toBeChecked();

    reviewerCredentials = await signUpAndInviteReviewer(page, browser, owner, name);
  });

  test('assignee can be set and cleared', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const pullRequestNumber = requireSeed('pullRequestNumber');

    await page.goto(`/${owner}/${name}/pull/${pullRequestNumber}`);

    // #pr-assignee-select is TomSelect-decorated (the real <select> is hidden) -- set .value and
    // dispatch 'change' directly rather than page.selectOption(), which requires visibility.
    const assigneeUserId = await page.locator('#pr-assignee-select').evaluate((el: HTMLSelectElement) => {
      const opt = Array.from(el.options).find((o) => o.value !== '' && o.value !== el.value);
      return opt ? opt.value : null;
    });
    expect(assigneeUserId).toBeTruthy();

    await page.locator('#pr-assignee-select').evaluate((el: HTMLSelectElement, val: string) => {
      el.value = val;
      el.dispatchEvent(new Event('change', { bubbles: true }));
    }, assigneeUserId!);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#pr-assignee-select')).toHaveValue(assigneeUserId!);

    await page.locator('#pr-assignee-select').evaluate((el: HTMLSelectElement) => {
      el.value = '';
      el.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#pr-assignee-select')).toHaveValue('');
  });

  test('a label can be added to and removed from the PR', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const pullRequestNumber = requireSeed('pullRequestNumber');

    // A fresh page fixture starts at about:blank, where a relative fetch() URL has nothing to
    // resolve against -- navigate first so window.fetch (patched by site/layout.html to inject
    // the X-XSRF-TOKEN header) has a real page/session to run in.
    await page.goto(`/${owner}/${name}/pull/${pullRequestNumber}`);

    // #labelIds only renders at all when the project has at least one label (issue/
    // partial_select_label.html's th:if) -- create one via the real label REST API first.
    const created = await page.evaluate(
      async ({ owner, name }) => {
        const res = await fetch(`/api/v1/projects/${owner}/${name}/labels`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            name: `e2e-pr-label-${Date.now()}`,
            color: '#ff0000',
            category: 'e2e',
            categoryIsExclusive: false,
          }),
        });
        return { status: res.status, body: await res.text() };
      },
      { owner, name }
    );
    expect(created.status).toBeLessThan(300);
    const labelId = String(JSON.parse(created.body).id);

    await page.goto(`/${owner}/${name}/pull/${pullRequestNumber}`);
    const labelSelect = page.locator('#labelIds');
    await expect(labelSelect).toHaveCount(1);

    // Multi-select is also TomSelect-decorated (class="hide") -- toggle the option's .selected and
    // dispatch 'change' directly, same reasoning as the assignee select above.
    await labelSelect.evaluate((el: HTMLSelectElement, id: string) => {
      const opt = Array.from(el.options).find((o) => o.value === id);
      if (opt) opt.selected = true;
      el.dispatchEvent(new Event('change', { bubbles: true }));
    }, labelId);
    await page.waitForLoadState('networkidle');

    await expect(page.locator(`#labelIds option[value="${labelId}"]`)).toHaveJSProperty('selected', true);

    await page
      .locator('#labelIds')
      .evaluate((el: HTMLSelectElement, id: string) => {
        const opt = Array.from(el.options).find((o) => o.value === id);
        if (opt) opt.selected = false;
        el.dispatchEvent(new Event('change', { bubbles: true }));
      }, labelId);
    await page.waitForLoadState('networkidle');

    await expect(page.locator(`#labelIds option[value="${labelId}"]`)).toHaveJSProperty('selected', false);
  });

  test.describe('reviewer actions as a non-contributor project member', () => {
    test.use({ storageState: { cookies: [], origins: [] } });

    test('the throwaway reviewer self-registers, approves, then un-registers', async ({ page }) => {
      const owner = requireSeed('projectOwner');
      const name = requireSeed('projectName');
      const pullRequestNumber = requireSeed('pullRequestNumber');
      if (!reviewerCredentials) throw new Error('reviewerCredentials was not set by the earlier setup test');

      await page.goto('/users/loginform');
      await page.fill('#loginIdOrEmailD', reviewerCredentials.loginId);
      await page.fill('#password', reviewerCredentials.password);
      await page.click('form[action="/users/login"] button[type=submit]');
      await expect(page.locator('a[href="/login"]')).toHaveCount(0);

      await page.goto(`/${owner}/${name}/pull/${pullRequestNumber}`);

      await page.click('#btn-review');
      await page.waitForLoadState('networkidle');
      await expect(page.locator('#btn-unreview')).toBeVisible();

      await page.fill('#pr-review-body', 'Looks good from the e2e suite.');
      await page.click('#btn-review-approve');
      await page.waitForLoadState('networkidle');
      // Locale-independent: assert via the verdict's CSS class, not its translated label text.
      await expect(page.locator('.review-verdict-approve').first()).toBeVisible();

      await page.click('#btn-unreview');
      await page.waitForLoadState('networkidle');
      await expect(page.locator('#btn-review')).toBeVisible();
    });
  });

  test('admin (the PR contributor) leaves a comment-only review verdict', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const pullRequestNumber = requireSeed('pullRequestNumber');

    await page.goto(`/${owner}/${name}/pull/${pullRequestNumber}`);
    // canApproveOrRequestChanges (approve/request-changes buttons) is false for the PR's own
    // contributor, but the comment verdict has no such restriction.
    await expect(page.locator('#btn-review-approve')).toHaveCount(0);
    await page.fill('#pr-review-body', 'Self-comment from the e2e suite.');
    await page.click('#btn-review-comment');
    await page.waitForLoadState('networkidle');
    // .first(): a re-run of this spec against the same PR leaves a matching comment from the
    // previous run too -- this only needs to confirm at least one landed.
    await expect(page.locator('.review-list-item').filter({ hasText: 'Self-comment from the e2e suite.' }).first()).toBeVisible();
  });

  test('PR can be closed and reopened', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const pullRequestNumber = requireSeed('pullRequestNumber');

    await page.goto(`/${owner}/${name}/pull/${pullRequestNumber}`);
    await page.click('a[data-request-uri*="state=CLOSED"]');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('a[data-request-uri*="state=OPEN"]')).toBeVisible();
    await expect(page.locator('a[data-request-uri*="state=CLOSED"]')).toHaveCount(0);

    await page.click('a[data-request-uri*="state=OPEN"]');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('a[data-request-uri*="state=CLOSED"]')).toBeVisible();
    await expect(page.locator('a[data-request-uri*="state=OPEN"]')).toHaveCount(0);
  });

  // CORRECTED DIAGNOSIS (was previously mis-attributed to a cross-fork git conflict -- that
  // theory does not hold under a clean sequential full-suite run, verified live): the real cause
  // is this file's own "self-registers, approves, then un-registers" reviewer test above, which
  // un-registers the only reviewer at its end while `project.isUsingReviewerCount` (enabled by
  // the very first test in this file) is still on. PullRequestViewController.addCommonPrAttributes()
  // computes `isAcceptable` as OPEN && !conflict && !merging && meetsReviewerCount, where
  // meetsReviewerCount requires `pullRequest.reviewers.size >= project.defaultReviewerCount` --
  // with zero registered reviewers left, that gate fails and #btnAccept never renders (a disabled,
  // id-less button shows instead), even though there is no actual git conflict (confirmed live:
  // the state banner correctly shows `pullRequest.is.safe`, "This pull request can be merged
  // safely"). Since this test's purpose is exercising merge, not reviewer-count enforcement,
  // disable the reviewer-count requirement first so the merge gate reflects only mergeability.
  test('PR can be merged', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const pullRequestNumber = requireSeed('pullRequestNumber');

    // #reviewerCountEnable/#reviewerCountDisable are a radio pair (name="isUsingReviewerCount"),
    // not a checkbox -- uncheck() on a radio throws ("can only be unchecked by selecting another
    // radio in the same group"), so select the disable option directly instead.
    await page.goto(`/${owner}/${name}/setting`);
    await page.check('#reviewerCountDisable');
    await page.click('#saveSetting #save');
    await page.waitForTimeout(500);

    await page.goto(`/${owner}/${name}/pull/${pullRequestNumber}`);
    await expect(page.locator('#btnAccept')).toBeVisible();

    // #btnAccept's click handler calls a native confirm() before firing the merge request.
    page.once('dialog', (d) => d.accept());
    await page.click('#btnAccept');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('#btnAccept')).toHaveCount(0);
    // .badge also matches the unrelated "approved N" review-verdict-summary badge -- scope to the
    // state badge specifically (badge-issue-merged, same naming convention as the closed/open
    // badges elsewhere in this file) to avoid a strict-mode violation.
    await expect(page.locator('.badge.badge-issue-merged')).toContainText(/MERGED|merged/i);
  });
});
