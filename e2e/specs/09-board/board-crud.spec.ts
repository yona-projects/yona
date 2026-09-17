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

test('assigning a label on a post persists across reload', async ({ page }) => {
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
  // yona.board.View.js's change handler PUTs to /api/projects/{id}/posts/{postId}/labels --
  // wait for that specific request/response round-trip instead of an arbitrary timeout.
  const putResponse = page.waitForResponse(
    (res) => res.request().method() === 'PUT' && /\/posts\/\d+\/labels$/.test(res.url()),
  );
  await select.evaluate((el: HTMLSelectElement, id: number) => {
    const option = Array.from(el.options).find((o) => o.value === String(id));
    if (option) option.selected = true;
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }, created.id);
  const response = await putResponse;
  expect(response.ok()).toBeTruthy();

  await page.reload();
  // FIXED (was a product bug -- board/view.html rendered the exact same label <select> widget
  // (issue/partial_select_label.html) that issue/view.html uses, and the REST endpoint
  // (BoardController#updatePostLabels, PUT .../posts/{postId}/labels) already worked when hit
  // directly, but yona.board.View.js never wired a "change" handler to call it the way
  // yona.issue.View.js does for issues -- selecting a label on a post used to be a silent
  // no-op). yona.board.View.js now delegates "change" on the [data-toggle=tomselect] label
  // select to that endpoint.
  const persisted = await page.locator('#labelIds').evaluate((el: HTMLSelectElement, id: number) => {
    return Array.from(el.selectedOptions).some((o) => o.value === String(id));
  }, created.id);
  expect(persisted).toBe(true);
});

// FIXED (was a product bug, PRODUCT BUG #1): board/view.html used to attach its OWN click
// listener to the `[data-request-method="delete"]` "예" button (a plain
// `fetch(uri, {method:'DELETE'}).then(...)` that did `window.location = ".../posts"` on
// response.ok) IN ADDITION to yona.Common.js's requestAs() (bundled site-wide as
// /javascripts/yona-common.js, loaded via site/layout::scripts, which board/view.html also
// includes), which auto-binds every `[data-request-method]` element on DOMContentLoaded --
// including this same button. That fired TWO independent DELETE requests per click to the same
// /api/projects/{id}/posts/{postId} URL: the first to arrive deleted the post and returned 200,
// the second hit an already-deleted post and got a non-2xx response (BoardController.
// deletePosting -> postingService.getPosting returns null -> 404), and since `response.ok`
// gated the navigation in board/view.html's own handler, when that handler's own fetch lost the
// race the page just sat on the deleted post's own URL. Fix: board/view.html no longer attaches
// its own listener -- it calls the idempotent `$yona.requestAs(el)` (which returns the same
// cached instance yona-common.js's auto-bind already created, so no second listener is added)
// and hooks only its "load" event to do the /posts redirect. Test strengthened past the
// original timeout-only repro with an explicit request-count assertion so a regression to
// double-binding fails deterministically rather than only "sometimes" (the race was originally
// observed at roughly 50% failure rate).
test('delete a post', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const title = `E2E post to be deleted ${uniqueSuffix()}`;

  await page.goto(`/${owner}/${name}/postform`);
  await page.fill('#title', title);
  await page.locator('textarea[data-editor-mode="content-body"]').fill('This post exists only to be deleted.', { force: true });
  await page.click('#post-form button[type=submit]');
  await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/post/\\d+`));
  const postUrl = page.url();

  let deleteRequestCount = 0;
  page.on('request', (req) => {
    if (req.method() === 'DELETE' && /\/api\/projects\/\d+\/posts\/\d+$/.test(req.url())) {
      deleteRequestCount += 1;
    }
  });

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

  // Scoped to the actual post list rows (board/list.html's `ul.post-list-wrap > li.post-item`)
  // rather than the whole `body` -- the GNB's user-menu "recently viewed" tab
  // (common/usermenu_tab_content_list.html) also renders visited-item titles+bodies concatenated
  // the same way and legitimately keeps history entries for deleted items, which made a
  // body-wide text assertion here flaky/wrong (it was matching stale entries from earlier,
  // unrelated debugging sessions, not this test's own post).
  await expect(page.locator('ul.post-list-wrap li.post-item').filter({ hasText: title })).toHaveCount(0);
  expect(deleteRequestCount).toBe(1);

  // The known-bad symptom this bug produced was worse than a missed redirect: the post's own
  // URL kept rendering as if nothing had happened, because the *successful* DELETE had already
  // run server-side (just the losing, already-404'd second request was the one the handler's
  // navigation logic reacted to). Confirm the post is actually gone server-side too, independent
  // of the UI redirect -- BoardViewController#viewPost renders error/notfound.html (status 200,
  // a context-aware in-project 404 page, not an HTTP 404) for a missing posting.
  const getResponse = await page.request.get(postUrl);
  expect(getResponse.ok()).toBeTruthy();
  const bodyAfterDelete = await getResponse.text();
  expect(bodyAfterDelete).toContain('error-wrap');
});
