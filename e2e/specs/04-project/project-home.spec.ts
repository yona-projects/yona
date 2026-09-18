import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screen: GET /{owner}/{projectName} (project/home.html), ProjectViewController.kt:88. Previously
 * only verified via the post-creation redirect landing here (04-project/00-project-create.spec.ts) --
 * this covers the actual widgets on the page itself: the inline description edit (a SEPARATE
 * widget from project-settings.spec.ts's /setting page description field -- different ids,
 * #project-description-input here vs #project-desc there, both independently POST the same
 * overview field), the clone-URL box, and the member/leave-project affordances. */

// FIXED (was: PRODUCT BUG -- fetch(sURLProject, {method:'put', body: JSON.stringify({overview})})
// sent only `overview`, but UpdateProjectRequest.projectScope had no default so Jackson 400'd
// before the controller body ran). Root cause went deeper than projectScope alone: every other
// settable field in UpdateProjectRequest/UpdateProjectParam (isCodeEnabled, isWikiEnabled,
// isUsingReviewerCount, etc.) was applied as an UNCONDITIONAL overwrite in
// ProjectServiceImpl.updateProject() -- so naively giving projectScope a fixed default would have
// fixed the 400 while silently resetting every other project setting (menu toggles, reviewer
// count, code-access-member-only) back to their Kotlin defaults on every description save, since
// this PUT /api/projects/{id} endpoint is used ONLY by this one widget (verified via grep -- no
// other caller). Fixed by making every field except `overview` nullable with a null default in
// both DTOs, and changing updateProject() to only overwrite a field when the param for it is
// non-null (matching the existing `name`/`defaultBranch` "omit to leave unchanged" pattern).
test('description inline-edit widget on the home page toggles and saves independently of /setting', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const overview = `home widget edit ${uniqueSuffix()}`;

  await page.goto(`/${owner}/${name}`);
  await expect(page.locator('#project-description')).toBeVisible();
  await expect(page.locator('.project-description-edit')).toHaveClass(/hidden/);

  await page.click('button[data-toggle="description-edit"]');
  await expect(page.locator('.project-description-edit')).not.toHaveClass(/hidden/);

  await page.fill('#project-description-input', overview);
  const [putResponse] = await Promise.all([
    page.waitForResponse((res) => res.request().method() === 'PUT' && /\/api\/projects\/\d+$/.test(new URL(res.url()).pathname)),
    page.click('#descriptionSaveBtn'),
  ]);
  expect(putResponse.ok()).toBeTruthy();
  const body = await putResponse.json();
  expect(body.overview).toBe(overview);

  // Confirm the save actually persisted server-side (not just an optimistic client-side echo) --
  // a full reload re-fetches project/home.html from scratch.
  await page.reload();
  await expect(page.locator('#project-description')).toHaveText(overview);
});

// FIXED (was PRODUCT BUG, separate from the 400 fixed above, found while verifying that fix --
// confirmed live via a response listener): the success handler also calls yona.Markdown.render()
// to re-render the saved text as HTML in place (yona.project.Home.js:154), which internally does
// `fetch(htVar.sMarkdownRendererUrl, ...)` (yona.Markdown.js's shared _render()). That URL is only
// ever set by yona.Markdown.init() inside the `site/layout :: markdown(project)` fragment
// (site/layout.html) -- and project/home.html never included that fragment, so
// htVar.sMarkdownRendererUrl stayed undefined and fetch(undefined, ...) resolved against the
// current page as a same-origin request for the literal string "undefined" (confirmed live:
// `POST /admin/undefined` -> 405), silently swallowed by the render promise's .catch(). Fixed by
// adding `<th:block th:replace="~{site/layout :: markdown(${project})}"></th:block>` to
// project/home.html (same pattern already used by milestone/create.html etc.).
test('description inline-edit widget updates the DOM immediately without a reload', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const overview = `home widget edit ${uniqueSuffix()}`;

  await page.goto(`/${owner}/${name}`);
  await page.click('button[data-toggle="description-edit"]');
  await page.fill('#project-description-input', overview);
  await Promise.all([
    page.waitForResponse((res) => res.request().method() === 'PUT' && /\/api\/projects\/\d+$/.test(new URL(res.url()).pathname)),
    page.click('#descriptionSaveBtn'),
  ]);
  await page.waitForResponse((res) => res.request().method() === 'POST' && res.url().includes(`/markdown/${owner}/${name}`));
  await expect(page.locator('#project-description')).toHaveText(overview);
});

test('saving the description via the home widget does not reset unrelated project settings', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  // Regression guard for the fix above: this PUT must not silently reset menu toggles etc. back
  // to their Kotlin defaults just because the JS body only ever contains `overview`.
  await page.goto(`/${owner}/${name}/setting`);
  await page.check('#reviewerCountEnable');
  await page.click('#saveSetting #save');
  await page.waitForTimeout(500);

  await page.goto(`/${owner}/${name}`);
  await page.click('button[data-toggle="description-edit"]');
  await page.fill('#project-description-input', `home widget edit ${uniqueSuffix()}`);
  await Promise.all([
    page.waitForResponse((res) => res.request().method() === 'PUT' && /\/api\/projects\/\d+$/.test(new URL(res.url()).pathname)),
    page.click('#descriptionSaveBtn'),
  ]);

  await page.goto(`/${owner}/${name}/setting`);
  await expect(page.locator('#reviewerCountEnable')).toBeChecked();

  // Restore -- 07-pull-request/02-pull-request-workflow.spec.ts's own setup toggles this on too,
  // but leave it as this test found it (on) rather than assuming; other specs re-enable it as
  // needed and none currently assert it starts disabled.
});

test('description edit cancel button discards the change and re-hides the edit form', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}`);
  const before = await page.locator('#project-description').textContent();

  await page.click('button[data-toggle="description-edit"]');
  await page.fill('#project-description-input', 'this edit should be discarded');
  await page.click('button[data-toggle="description-cancel"]');

  await expect(page.locator('.project-description-edit')).toHaveClass(/hidden/);
  await expect(page.locator('#project-description')).toHaveText(before ?? '');
});

test('clone URL box is present with the real clone URL for a code-enabled project', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}`);
  const cloneUrl = page.locator('#cloneURL');
  await expect(cloneUrl).toBeVisible();
  const value = await cloneUrl.inputValue();
  expect(value).toContain(`/${owner}/${name}`);
  await expect(page.locator('#cloneURLBtn')).toBeVisible();
});

test('member-add link on the home sidebar points at the real members screen', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}`);
  const addLink = page.locator('#member-add-link');
  await expect(addLink).toBeVisible();
  await expect(addLink).toHaveAttribute('href', `/${owner}/${name}/members`);
});

test('leave-project button opens a real confirm dialog (not actually confirmed -- would remove the project owner)', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}`);
  // The project owner (admin) is not actually a candidate for "leave" in real usage (yona blocks
  // the owner from leaving their own project) -- just confirm the button and its confirm dialog
  // exist and open, without submitting, to avoid corrupting project ownership for every other spec
  // that depends on seed.projectOwner/projectName.
  const leaveBtn = page.locator('#projectLeaveBtn');
  await expect(leaveBtn).toBeVisible();
  await leaveBtn.click();
  await expect(page.locator('dialog#alertLeave')).toBeVisible();
  // Dismiss without confirming: no explicit close button other than #leaveBtn (confirm) is wired
  // in this dialog per the template, so press Escape.
  await page.keyboard.press('Escape');
});
