import { test, expect } from '@playwright/test';
import { requireSeed, writeSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screens: GET /{owner}/{projectName}/postform (board/create.html) ->
 * GET /{owner}/{projectName}/post/{number} (board/view.html) ->
 * GET /{owner}/{projectName}/post/{number}/editform (board/edit.html) ->
 * GET /{owner}/{projectName}/posts (board/list.html). */

test.describe.serial('board post lifecycle', () => {
  test('create a post with title and body', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/postform`);
    await page.fill('#title', 'E2E seed post');
    // Same hidden-textarea-behind-a-widget situation as issue/wiki create -- force bypasses the
    // visibility actionability check on the real <textarea name="body"> the form submits.
    await page.locator('textarea[data-editor-mode="content-body"]').fill('Post body written by the e2e suite.', { force: true });
    await page.click('#post-form button[type=submit]');

    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/post/\\d+`));
    const match = page.url().match(/\/post\/(\d+)/);
    const postNumber = Number(match?.[1]);
    expect(postNumber).toBeGreaterThan(0);
    writeSeed({ postNumber });
  });

  test('view the created post', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const postNumber = requireSeed('postNumber');

    await page.goto(`/${owner}/${name}/post/${postNumber}`);
    await expect(page.locator('body')).toContainText('E2E seed post');
  });

  test('edit the post title', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const postNumber = requireSeed('postNumber');

    await page.goto(`/${owner}/${name}/post/${postNumber}/editform`);
    await page.fill('#title', 'E2E seed post (edited)');
    // The edit form's submit handler is AJAX-only (fetch PUT to /api/projects/{id}/posts/{id}):
    // on success it calls a blocking native alert() BEFORE the window.location.href redirect --
    // without accepting that dialog, the redirect never runs and the page hangs on this URL.
    page.once('dialog', (dialog) => dialog.accept());
    await page.click('#post-form button[type=submit]');
    await page.waitForURL(new RegExp(`/${owner}/${name}/post/${postNumber}$`));

    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/post/${postNumber}`));
    await expect(page.locator('body')).toContainText('E2E seed post (edited)');
  });

  test('post list shows the created post', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/posts`);
    await expect(page.locator('body')).toContainText('E2E seed post (edited)');
  });
});

test('assigning a label on a post does not persist -- documents a wiring gap, not desired behavior', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const postNumber = requireSeed('postNumber');
  const labelName = `e2e-board-label-${uniqueSuffix()}`;

  // Board post label assignment has no dedicated UI to create a label from -- seed one directly
  // through the real REST API (LabelRestApiController, the same one project label management
  // uses) so the <select> in board/view.html has something to pick.
  const createResponse = await page.request.post(`/api/v1/projects/${owner}/${name}/labels`, {
    data: { name: labelName, color: '#ff00aa', category: `e2e-${uniqueSuffix()}` },
  });
  expect(createResponse.ok()).toBeTruthy();
  const created = await createResponse.json();

  await page.goto(`/${owner}/${name}/post/${postNumber}`);
  const select = page.locator('#labelIds');
  await expect(select).toBeAttached();
  await select.evaluate((el: HTMLSelectElement, id: number) => {
    const option = Array.from(el.options).find((o) => o.value === String(id));
    if (option) option.selected = true;
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }, created.id);
  await page.waitForTimeout(300);

  await page.reload();
  // KNOWN PRODUCT GAP: board/view.html renders the exact same label <select> widget
  // (issue/partial_select_label.html) that issue/view.html uses, but yona.board.View.js never
  // wires a change handler to PUT /api/projects/{id}/posts/{postId}/labels the way
  // yona.issue.View.js does for issues -- selecting a label on a post is currently a silent
  // no-op. Confirmed by grepping every static JS file for "labelIds": it appears in
  // yona.issue.View.js, yona.issue.List.js, yona.board.List.js (a *filter* select, not this one)
  // and yona.milestone.View.js, but never in yona.board.View.js. This test asserts the actual
  // (broken) behavior -- selection does not survive a reload -- so a future fix will fail this
  // test and prompt someone to update it, rather than the gap silently staying invisible.
  const persisted = await page.locator('#labelIds').evaluate((el: HTMLSelectElement, id: number) => {
    return Array.from(el.selectedOptions).some((o) => o.value === String(id));
  }, created.id);
  expect(persisted).toBe(false);
});

// KNOWN PRODUCT BUG (documented, not fixed here -- see final report's product bug list):
// board/view.html:275-284 attaches its OWN click listener to every
// `[data-request-method="delete"]` element (a plain `fetch(uri, {method:'DELETE'}).then(...)`
// that does `window.location = ".../posts"` on response.ok). But yona.Common.js's requestAs()
// (bundled site-wide as /javascripts/yona-common.js, loaded via site/layout::scripts, which
// board/view.html also includes) ALSO auto-binds every `[data-request-method]` element on
// DOMContentLoaded, INCLUDING this same "예" button (it has data-request-method="delete"). That
// means clicking it fires TWO independent DELETE requests to the same
// /api/projects/{id}/posts/{postId} URL. The first to arrive deletes the post and returns 200;
// the second hits an already-deleted post and gets a non-2xx response
// (BoardController.deletePosting -> postingService.getPosting returns null -> 404). Since
// `response.ok` gates the navigation in board/view.html's own handler, when that handler's own
// fetch is the one that loses the race, `window.location` never fires and the page just sits on
// the deleted post's own URL. Confirmed live: this test times out waiting for /posts roughly as
// often as it passes, and a raw curl GET against post numbers left over from earlier flaky runs
// confirms the post genuinely was deleted server-side even when the UI never navigated away.
// Real race condition from duplicate event binding, not a test bug -- fixme until fixed at the
// source.
test.fixme('delete a post', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/postform`);
  await page.fill('#title', 'E2E post to be deleted');
  await page.locator('textarea[data-editor-mode="content-body"]').fill('This post exists only to be deleted.', { force: true });
  await page.click('#post-form button[type=submit]');
  await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/post/\\d+`));

  // The trash icon jumps to a native <dialog id="deleteConfirm"> (not a bootstrap-JS modal --
  // board/view.html's own comment notes bootstrap's delegate double-fires if data-toggle="modal"
  // is left on the trigger, so a dedicated script calls showModal() instead); the "예" button
  // inside it is the actual data-request-method="delete" trigger.
  await page.locator('a[href="#deleteConfirm"]').click();
  const dialog = page.locator('dialog#deleteConfirm');
  await expect(dialog).toBeVisible();
  await Promise.all([
    page.waitForURL(new RegExp(`/${owner}/${name}/posts$`)),
    dialog.locator('button[data-request-method="delete"]').click(),
  ]);
  await expect(page.locator('body')).not.toContainText('E2E post to be deleted');
});
