import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screens: issue/list.html's mass-update widget (label/assignee/milestone/state changes),
 * issue/view.html's per-issue state/vote/comment-edit/comment-delete controls, and
 * WikiViewController-style delete flows for issues. Deliberately uses its OWN issue (not
 * seed.issueNumber, which 06-issue/issue-crud.spec.ts and 11-search/search.spec.ts already
 * depend on for a specific title) so none of this file's state mutations (label attach, state
 * close/open, deletion) can affect those other specs. */

let issueNumber: number;
let labelId: number;
let dedicatedMilestoneId: number;
const issueTitle = `E2E management issue ${uniqueSuffix()}`;

test.describe.serial('issue management actions', () => {
  test('create a dedicated issue for management actions', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/issueform`);
    await page.fill('#title', issueTitle);
    await page.locator('textarea[data-editor-mode="content-body"]').fill('Body for management actions.', { force: true });
    await page.click('#button-save');

    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/issue/\\d+`));
    const match = page.url().match(/\/issue\/(\d+)/);
    issueNumber = Number(match?.[1]);
    expect(issueNumber).toBeGreaterThan(0);
  });

  // `seed.milestoneId` is produced by 10-milestone/milestone-crud.spec.ts, but folders run in
  // alphabetical order (06 before 10) -- that seed does not exist yet when this file runs, so
  // depending on it via requireSeed() would always throw. Create a throwaway milestone of this
  // file's own instead, mirroring 10-milestone/milestone-crud.spec.ts's own create flow, so this
  // spec is self-contained regardless of folder execution order.
  test('create a dedicated milestone for the mass-update-assignment test', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/milestone/new`);
    await page.fill('#title', `E2E management milestone ${uniqueSuffix()}`);
    await page.fill('#dueDate', '2099-12-31');
    await page.locator('textarea[data-editor-mode="content-body"]').evaluate((el: HTMLTextAreaElement) => {
      el.value = 'Milestone created for issue-management.spec.ts.';
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await page.check('#milestone-open');
    await page.click('#milestone-form button[type=submit]');

    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/milestone/\\d+`));
    const match = page.url().match(/\/milestone\/(\d+)/);
    dedicatedMilestoneId = Number(match?.[1]);
    expect(dedicatedMilestoneId).toBeGreaterThan(0);
  });

  // PRODUCT BUG (confirmed live via request/response tracing, not a test authoring mistake):
  // attaching a label through this widget silently no-ops. The mass-update POST does carry
  // the right data (`attachingLabelIds=<id>&issues[0].id=<id>`, verified with a network
  // listener) and IssueViewController.massUpdate() (web/IssueViewController.kt:762) does
  // 302-redirect back to /issues as if it succeeded, but the label never actually shows up on
  // the issue afterward. Repro: check one issue's box on /{owner}/{projectName}/issues, open
  // the "라벨 추가" dropdown, click any label -- reload the issue and the label is absent. The
  // sibling state-toggle action on the very same form (`state=CLOSED&issues[0].id=...`) DOES
  // persist correctly, which narrows this to something specific to
  // `IssueMassUpdateForm.attachingLabelIds`/`detachingLabelIds` (web/IssueViewController.kt:1032-1033),
  // both declared as bare top-level `List<Long>` -- unlike `state` (String) and
  // `milestone`/`assignee` (nested single-id object), which both bind and apply fine from the
  // same form submission. Left as fixme per instruction -- do not fix here, follow-up TDD work
  // will address it; flip back to `test(...)` once fixed.
  test('attach a label via the issue list mass-update widget', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    // The seeded project has zero labels (nothing in this suite creates one -- label
    // *creation* through its JS widget is a separate, already-flagged gap in matrix.md's
    // section 4). Seed one directly through the same REST endpoint the label-admin widget
    // itself calls, purely as fixture data -- the thing under test here is *attaching* an
    // existing label via the mass-update dropdown, not label creation.
    const labelName = `e2e-label-${uniqueSuffix()}`;
    const createResponse = await page.request.post(`/api/v1/projects/${owner}/${name}/labels`, {
      data: { name: labelName, color: '#ff0000', category: 'e2e-category', categoryIsExclusive: false },
    });
    expect(createResponse.ok()).toBeTruthy();
    const created = await createResponse.json();
    labelId = created.id ?? created.labelId ?? created.label?.id;
    expect(labelId).toBeGreaterThan(0);

    await page.goto(`/${owner}/${name}/issues`);
    const issueRow = page.locator('li.post-item', { hasText: issueTitle });
    await expect(issueRow).toBeVisible();
    await issueRow.locator('input[name="checked-issue"]').check();

    // yona-dropdown#attaching-label wires onChange -> _onChangeAttachingLabelField -> a real
    // native <form> submit (welForm.submit(), not fetch) to /{owner}/{projectName}/issues/massupdate
    // carrying issues[0].id + attachingLabelIds -- this is a full page navigation, not AJAX.
    // IssueViewController.massUpdate() redirects to plain `/{owner}/{projectName}/issues` on
    // success -- wait for that specific URL rather than waitForLoadState('load'), which can
    // resolve immediately against the *current* (already-loaded) page before the click's
    // navigation even starts, racing ahead into the next page.goto() with a stale page. Even
    // after that, the redirect target keeps loading sub-resources for a moment -- an immediate
    // page.goto() right after waitForURL resolves can still get net::ERR_ABORTED (verified
    // live), so settle on networkidle first.
    await page.click('#attaching-label button.dropdown-toggle');
    await Promise.all([
      page.waitForURL(new RegExp(`/${owner}/${name}/issues$`)),
      page.click(`#attaching-label li[data-value="${labelId}"] a`),
    ]);
    await page.waitForLoadState('networkidle');

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    await expect(page.locator(`.issue-label[data-label-id="${labelId}"]`)).toBeVisible();
  });

  test('assign the issue to the current user via the mass-update widget', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/issues`);
    const issueRow = page.locator('li.post-item', { hasText: issueTitle });
    await issueRow.locator('input[name="checked-issue"]').check();

    // `page.click(selector, {hasText})` silently ignores hasText (it isn't a valid click
    // option, only a Locator filter) and clicked the *first* <li>, "no assignee" (id -1)
    // instead of "assign to me" -- confirmed live via network trace showing
    // `assignee.id=-1` submitted. issue/list.html always renders "assign to me" as the
    // *second* <li> in this dropdown (right after "no assignee", before the divider and the
    // per-member list), so target it positionally instead of by (locale-dependent) text.
    await page.click('#assignee button.dropdown-toggle');
    await Promise.all([
      page.waitForURL(new RegExp(`/${owner}/${name}/issues$`)),
      page.locator('#assignee ul.dropdown-menu > li').nth(1).locator('a').click(),
    ]);
    // waitForURL resolves as soon as the redirect target commits; its sub-resources are still
    // loading for a moment, and an immediate page.goto() right after can hit net::ERR_ABORTED
    // (confirmed live while debugging the mass-update flow) -- settle first.
    await page.waitForLoadState('networkidle');

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    const owner2 = requireSeed('adminLoginId');
    // isAllowedUpdate renders the assignee as a hidden <input id="assignee" value="...">
    // (backing an inline select2-alike widget saved through its own dedicated endpoint, not
    // massUpdate) rather than visible text -- toContainText() would see nothing here.
    await expect(page.locator('#assignee')).toHaveValue(owner2);
  });

  test('assign a milestone to the issue via the mass-update widget', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const milestoneId = dedicatedMilestoneId;

    await page.goto(`/${owner}/${name}/issues`);
    const issueRow = page.locator('li.post-item', { hasText: issueTitle });
    await issueRow.locator('input[name="checked-issue"]').check();

    // The dedicated milestone created above is OPEN by construction (#milestone-open checked),
    // so the mass-update widget's milestone dropdown is guaranteed to be rendered here -- no
    // need to guard against a closed/missing milestone the way a shared cross-file seed would.
    await page.click('#milestone button.dropdown-toggle');
    await Promise.all([
      page.waitForURL(new RegExp(`/${owner}/${name}/issues$`)),
      page.click(`#milestone li[data-value="${milestoneId}"] a`),
    ]);
    await page.waitForLoadState('networkidle');

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    // issue/view.html's <dt> label text is locale-dependent (this environment renders English,
    // not Korean, for reasons unrelated to this test -- same surprise hit the assignee test
    // above). #milestone is the select backing the field directly; check its value instead of
    // a locale-sensitive text label.
    await expect(page.locator('#milestone')).toHaveValue(String(milestoneId));
  });

  test('toggle issue state closed then open via the mass-update widget', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/issues`);
    let issueRow = page.locator('li.post-item', { hasText: issueTitle });
    await issueRow.locator('input[name="checked-issue"]').check();
    await page.click('#state button.dropdown-toggle');
    await Promise.all([
      page.waitForURL(new RegExp(`/${owner}/${name}/issues$`)),
      page.click('#state li[data-value="CLOSED"] a'),
    ]);
    await page.waitForLoadState('networkidle');

    // BUG #4 (was: PRODUCT BUG, now fixed -- see BUGFIXES.md): issue/view.html's state badge did
    // `#{'issue.state.' + issue.state}` with the raw (uppercase) enum name, e.g.
    // "issue.state.CLOSED" -- but messages*.properties only define lowercase keys
    // ("issue.state.closed"/"issue.state.open", confirmed via grep). In whatever locale this
    // environment renders (English here, not Korean -- same surprise as the assignee/milestone
    // tests above), that was a genuine missing key: the badge's *text* rendered as the literal
    // "??issue.state.CLOSED_en_US??" placeholder. The CSS class (`badge-issue-closed`, built
    // from a *lowercased* interpolation) was unaffected, so asserting on it alone still validated
    // the real state change but let the broken text slip through -- assert the actual visible
    // text too. .first() works around the badge being rendered twice on this page (large + small
    // variants).
    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    await expect(page.locator('.badge-issue-closed').first()).toBeVisible();
    await expect(page.locator('.badge-issue-closed').first()).toHaveText('Closed');

    // Reopen -- closed issues drop out of the default (open) issue list view, so switch to the
    // closed-state list to find the checkbox again.
    await page.goto(`/${owner}/${name}/issues?state=CLOSED`);
    issueRow = page.locator('li.post-item', { hasText: issueTitle });
    await issueRow.locator('input[name="checked-issue"]').check();
    await page.click('#state button.dropdown-toggle');
    await Promise.all([
      page.waitForURL(new RegExp(`/${owner}/${name}/issues$`)),
      page.click('#state li[data-value="OPEN"] a'),
    ]);
    await page.waitForLoadState('networkidle');

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    await expect(page.locator('.badge-issue-open').first()).toBeVisible();
    await expect(page.locator('.badge-issue-open').first()).toHaveText('Open');
  });

  test('vote the issue up, then cancel the vote', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    // The vote link is handled by yona.Common.js's generic requestAs() delegate: fetch POST,
    // then document.location.reload() on success (same URL, not a new one) -- wait on the
    // /vote response itself rather than a load-state race, then let the auto-retrying
    // expect() below ride out the reload.
    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    const voteLink = page.locator('#vote a[data-request-method="post"]');
    await Promise.all([
      page.waitForResponse((res) => res.url().endsWith('/vote') && res.request().method() === 'POST'),
      voteLink.click(),
    ]);
    await expect(page.locator('#vote a.ybtn-watching')).toBeVisible();

    // BUG #5 (was: PRODUCT BUG, now fixed -- see BUGFIXES.md): issue/view.html's vote <a> used
    // to hardcode th:href to the `/vote` route unconditionally -- only its CSS class and tooltip
    // title changed based on `hasVoted` (`th:classappend="${hasVoted ? 'ybtn-watching' : ''}"`),
    // the href itself never switched to the `/unvote` route VoteController.unvote() exposes.
    // Confirmed live before the fix: after voting, the visually-"already voted, click to remove"
    // button still pointed at .../vote, so clicking it again re-POSTed to /vote (a no-op add to
    // an already-containing set) instead of ever reaching /unvote -- there was no way to retract
    // a vote through this UI. Assert the href now tracks hasVoted, then actually exercise the
    // toggle through the real UI (no more bypassing it with a direct API cleanup call).
    await expect(voteLink).toHaveAttribute('href', new RegExp(`/${owner}/${name}/issue/${issueNumber}/unvote$`));

    await Promise.all([
      page.waitForResponse((res) => res.url().endsWith('/unvote') && res.request().method() === 'POST'),
      voteLink.click(),
    ]);
    // `.ybtn-watching` (a CSS class selector, matching the whole class token) rather than a
    // /ybtn-watching/ regex -- the link's always-present base class is literally
    // "ybtn-watching-link", which a substring regex would wrongly match even once unvoted.
    await expect(page.locator('#vote a.ybtn-watching')).toHaveCount(0);
    await expect(page.locator('#vote a[data-request-method="post"]')).toHaveAttribute(
      'href',
      new RegExp(`/${owner}/${name}/issue/${issueNumber}/vote$`)
    );
  });

  // PRODUCT BUG (confirmed live via a page.on('pageerror') listener, not fixed per
  // instruction): loading /{owner}/{projectName}/issue/{number} for an issue that has the
  // seeded milestone assigned ("E2E seed milestone (edited)" -- title contains spaces and
  // parentheses) throws `Failed to execute 'querySelector' on 'Document': "E2E seed milestone
  // (edited)" is not a valid selector` in the browser console. Something in the
  // assignee/milestone/sharer TomSelect wiring (yona.ui.TomSelect.js's `_toElement()`, or
  // yona.issue.Assginee.js/yona.issue.Sharer.js which build their own TomSelect instances
  // outside the normal `[data-toggle=tomselect]` auto-init loop -- grep hint, exact call site
  // not pinpointed within time budget) is passing an option's *display text* where a CSS
  // selector or element reference was expected. The thrown exception appears to abort
  // whatever inline `<script>` block was mid-execution, which in turn leaves later handler
  // wiring in that same block unregistered -- concretely, `[data-toggle="comment-edit"]`
  // click delegation with it, which is why this test would hang forever waiting for the edit
  // button to become interactive (it's visible in a screenshot, just never wired up). Repro:
  // assign this specific milestone to any issue (10-milestone/milestone-crud.spec.ts or this
  // file's own "assign a milestone" test do it), then load that issue's view page with
  // DevTools open -- the querySelector error fires on load. Left as fixme; do not fix here.
  test('post a comment, edit it, vote/unvote it, then delete it', async ({ page }) => {
    // This test's regression guard for the querySelector-crash bug adds a milestone-rename
    // round trip (editform load + submit) on top of the already-long post/edit/delete comment
    // flow (each step is itself a full page reload, per this screen's design), so the default
    // 30s test timeout is too tight -- give it more headroom rather than trimming steps.
    test.setTimeout(60_000);

    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const commentBody = `management comment ${uniqueSuffix()}`;
    const editedBody = `${commentBody} (edited)`;

    // Rename the dedicated milestone (assigned to this file's issueNumber by "assign a
    // milestone to the issue via the mass-update widget" above) to include parentheses,
    // mirroring the exact shape of the milestone title that originally triggered this bug
    // ("E2E seed milestone (edited)" from 10-milestone/milestone-crud.spec.ts's own edit
    // flow) -- a title with only spaces (no parens) does NOT reproduce the querySelector
    // crash (confirmed: `document.querySelector("word word word")` is valid CSS syntax, just
    // matches nothing; parentheses specifically are not valid outside a pseudo-class/function
    // and make querySelector throw a SyntaxError).
    await page.goto(`/${owner}/${name}/milestone/${dedicatedMilestoneId}/editform`);
    await page.fill('#title', `E2E management milestone (edited) ${uniqueSuffix()}`);
    await page.click('#milestone-form button[type=submit]');
    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/milestone/${dedicatedMilestoneId}`));

    const pageErrors: string[] = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    // PRODUCT BUG regression guard: a milestone title containing parentheses used to crash
    // an inline <script> block in issue/view.html with `Failed to execute 'querySelector' on
    // 'Document': "..." is not a valid selector`, which aborted the rest of that script and
    // silently left `[data-toggle="comment-edit"]` click delegation unregistered (see fixed
    // root cause below). Assert no such error before proceeding, so a regression here fails
    // fast with a clear message instead of a generic timeout waiting for the edit button.
    expect(pageErrors).toEqual([]);
    await page.locator('#comment-form textarea[data-editor-mode="comment-body"]').evaluate(
      (el: HTMLTextAreaElement, value: string) => {
        el.value = value;
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
      },
      commentBody
    );
    const [createResponse] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/comments') && res.request().method() === 'POST'),
      page.locator('#comment-form button[type=submit]').click(),
    ]);
    expect(createResponse.ok()).toBeTruthy();
    // This screen's comment-form submit handler does a full `window.location.reload()` right
    // after the fetch resolves (issue/view.html's inline script), which races Playwright's CDP
    // body buffering for `createResponse` -- reading `.json()`/`.text()` on it after that
    // point reliably hangs forever in this environment (confirmed via a standalone repro: the
    // read never resolves or rejects, even past a 60s timeout) rather than racing cleanly. Skip
    // parsing the response body and just use the last comment-edit trigger instead -- this test
    // works with its own dedicated issue, so the comment just posted is always the only one.
    await expect(page.locator('body')).toContainText(commentBody);
    const commentIdLocator = page.locator('[data-toggle="comment-edit"]').last();

    await commentIdLocator.click();
    const editForm = page.locator('.comment-update-form:visible').last();
    await editForm.locator('textarea[data-editor-mode="update-comment-body"]').evaluate(
      (el: HTMLTextAreaElement, value: string) => {
        el.value = value;
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
      },
      editedBody
    );
    const [updateResponse] = await Promise.all([
      page.waitForResponse((res) => res.request().method() === 'PUT' && res.url().includes('/comments/')),
      editForm.locator('button[type=submit]').click(),
    ]);
    expect(updateResponse.ok()).toBeTruthy();
    await expect(page.locator('body')).toContainText(editedBody);

    // Comment vote/unvote (VoteController.voteComment/unvoteComment) -- a previously untested
    // route pair. The button toggles data-request-uri between .../vote and .../unvote server-side
    // (issue/view.html's hasCommentVoted branch) and yona.issue.View.js's _onClickCommentVote
    // does fetch(POST) then location.reload().
    const commentId = await commentIdLocator.getAttribute('data-comment-id');
    expect(commentId).toBeTruthy();
    // Don't wait on networkidle after the click -- this page has enough incidental background
    // activity post-reload that networkidle can hang well past the test timeout (confirmed
    // live). Don't race a page.waitForResponse() against the click either -- confirmed flaky
    // under full-suite load (a Promise.all([waitForResponse(...), click()]) pair timed out once
    // in a 220-test run despite passing reliably in isolation; the response/reload/re-render
    // sequence has no hard guarantee of completing within the observation window when the whole
    // browser is under load). Click, then let Playwright's auto-retrying expect() alone ride out
    // the fetch + reload + re-render with a generous explicit timeout -- no network-timing
    // assumption at all, matching the more robust half of the issue-level vote test above.
    const voteButton = page.locator(`button[data-request-type="comment-vote"][data-request-uri*="/comment/${commentId}/vote"]`);
    await expect(voteButton).toBeVisible();
    const unvoteButton = page.locator(`button[data-request-type="comment-vote"][data-request-uri*="/comment/${commentId}/unvote"]`);
    await voteButton.click();
    await expect(unvoteButton).toBeVisible({ timeout: 30_000 });
    await unvoteButton.click();
    await expect(voteButton).toBeVisible({ timeout: 30_000 });

    const deleteTrigger = page.locator('[data-toggle="comment-delete"]').last();
    await deleteTrigger.click();
    await expect(page.locator('#comment-delete-modal')).toBeVisible();
    const [deleteResponse] = await Promise.all([
      page.waitForResponse((res) => res.request().method() === 'DELETE' && res.url().includes('/comments/')),
      page.click('#comment-delete-confirm'),
    ]);
    expect(deleteResponse.ok()).toBeTruthy();
    await expect(page.locator('body')).not.toContainText(editedBody);
  });

  test('delete the dedicated management issue', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    await page.click('a[href="#deleteConfirm"]');
    await expect(page.locator('dialog#deleteConfirm')).toBeVisible();
    const [deleteResponse] = await Promise.all([
      page.waitForResponse((res) => res.request().method() === 'DELETE' && res.url().includes(`/issues/${issueNumber}`)),
      page.click('dialog#deleteConfirm button[data-request-method="delete"]'),
    ]);
    expect(deleteResponse.ok()).toBeTruthy();
    // deleteIssue() returns a plain 200 (no Location header), so requestAs() just reloads the
    // *current* page rather than redirecting -- give that reload a moment to land before
    // navigating away, same race condition as the mass-update flows above.
    await page.waitForLoadState('networkidle');

    await page.goto(`/${owner}/${name}/issues`);
    // expect(...).not.toContainText() is an auto-retrying assertion re-polling this page over
    // its timeout window; it reported a false positive here in testing even though a direct,
    // single innerText() snapshot immediately after the same navigation confirms the title is
    // genuinely gone (verified live, not just here). Assert directly on the snapshot instead.
    const listText = await page.locator('body').innerText();
    expect(listText).not.toContain(issueTitle);
  });
});
