import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: pullrequest/view.html's "changes" tab -- diff-line code review comments
 * (common/reviewForm.html's <yona-review-form> floating popup, triggered by clicking a
 * diff line's .add-comment-btn-cell), and their deletion (ReviewApiController's
 * DELETE /comments/{type}/{id}). Distinct from 01-pull-request-crud.spec.ts's general
 * (non-ranged) PR comment, which uses the plain bottom-of-page comment form instead. */

test.describe.serial('pull request code review line comments', () => {
  test('add a review comment on a specific diff line, then delete it', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const pullRequestNumber = requireSeed('pullRequestNumber');

    await page.goto(`/${owner}/${name}/pull/${pullRequestNumber}/changes`);

    const changesRoot = page.locator('#changes');
    await expect(changesRoot).toHaveAttribute('data-can-review-comment', 'true');

    // Any diff line's first (line-number) cell works as the "add comment" trigger --
    // yona.CodeCommentBlock/CodeCommentBox JS (view.html's inline script) handles selecting
    // the line and opening the floating <yona-review-form> popup.
    const firstLineCell = changesRoot.locator('tr[data-line] .add-comment-btn-cell').first();
    await expect(firstLineCell).toBeVisible();
    await firstLineCell.click();

    // Two elements share id="review-form": the <yona-review-form> custom element (stays put,
    // effectively empty/hidden -- its content is a Vue <Teleport> source) and a plain <div
    // id="review-form" class="review-form arrow-top"> that is the actual Teleport TARGET
    // rendered in place next to the diff line (confirmed via screenshot: toolbar + textarea are
    // visible there, while the custom element itself reports hidden). Target the div.
    const reviewForm = page.locator('div#review-form.review-form');
    await expect(reviewForm).toBeVisible();

    // <yona-review-form> is a Vue 3 custom element rendered as light DOM (per its own
    // authoring comment) -- its markdown editor textarea and submit button should be
    // queryable as plain descendants once shown.
    const textarea = reviewForm.locator('textarea').first();
    await textarea.waitFor({ state: 'attached' });
    const commentBody = `Line comment from the e2e suite ${Date.now()}`;
    await textarea.evaluate((el: HTMLTextAreaElement, value: string) => {
      el.value = value;
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    }, commentBody);

    const submitButton = reviewForm.locator('button[type="submit"]');
    await submitButton.click();

    await page.waitForURL(new RegExp(`/${owner}/${name}/pullRequest/${pullRequestNumber}/changes`));
    await expect(page.locator('body')).toContainText(commentBody);

    // Delete it. ReviewApiController.DELETE /comments/{type}/{id} -- the delete trigger lives
    // inside the rendered comment thread (partial_comment_thread.html); locate it relative to
    // the comment text we just posted.
    // `.comments` also matches an unrelated `<ul class="comments">` container elsewhere on the
    // page -- scope to the specific thread row (`tr.comments.board-comment-wrap`, see
    // partial_diff_comment_on_line.html) that actually contains our text.
    const commentRow = page.locator('tr.comments.board-comment-wrap', { hasText: commentBody });
    await expect(commentRow).toBeVisible();

    // The delete trigger opens a native <dialog id="comment-delete-modal"> (common/
    // commentDeleteModal.html's own inline script calls showModal()/close() directly) --
    // this is NOT a window.confirm(), so Playwright's page.on('dialog') never fires for it.
    // Click the trigger, wait for the modal, then click its confirm button.
    const deleteTrigger = commentRow.locator('[data-toggle="comment-delete"]');
    await deleteTrigger.click();

    await expect(page.locator('#comment-delete-modal')).toBeVisible();
    const [deleteResponse] = await Promise.all([
      page.waitForResponse((res) => res.request().method() === 'DELETE' && res.url().includes('/comments/')),
      page.click('#comment-delete-confirm'),
    ]);
    expect(deleteResponse.ok()).toBeTruthy();

    // The delete handler only removes the <li> from the diff-line thread in place -- the
    // separate "review cards" summary sidebar (id="reviewcards-open") is a static server-render
    // that isn't updated by that DOM surgery, so it still shows the deleted comment's preview
    // text until the next navigation. Reload before asserting, same as other AJAX-less-partial
    // update flows elsewhere in this suite (e.g. board post deletion).
    await page.reload();
    await expect(page.locator('body')).not.toContainText(commentBody);
  });
});
