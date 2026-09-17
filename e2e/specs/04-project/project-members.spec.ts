import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screen: GET /{owner}/{projectName}/members (project/members.html).
 *
 * Member add/role-change/delete are all fetch() calls wired in an inline <script> at the bottom
 * of members.html (POST /api/projects/{id}/members?loginId=..., PUT .../members/{userId}/role?roleId=,
 * DELETE .../members/{userId}), each reloading the page on success. Delete asks a native confirm()
 * first -- must register page.once('dialog', d => d.accept()) before the click.
 *
 * seed.secondUserLoginId is added here as a permanent MANAGER-role member (not removed at the end)
 * because 07-pull-request/02-pull-request-workflow.spec.ts reuses that membership to act as a
 * non-contributor reviewer (PullRequestController.checkWritePermission requires project membership).
 * A separate throwaway third user is created just for the add-then-remove test so removal doesn't
 * touch the member the PR workflow spec depends on. */

test('members screen lists the project owner', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/members`);
  await expect(page.locator('body')).toContainText(owner);
});

test('invite the seeded second user and promote them to manager', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const secondUserLoginId = requireSeed('secondUserLoginId');

  await page.goto(`/${owner}/${name}/members`);

  // Idempotent: a re-run of this spec (or a prior full-suite run) may have already added this
  // member -- only invite if they aren't already listed.
  const alreadyMember = await page.locator(`.member-id:text-is("@${secondUserLoginId}")`).count();
  if (alreadyMember === 0) {
    await page.fill('#addNewMember #loginId', secondUserLoginId);
    await page.click('#addNewMember button[type=submit]');
    await page.waitForLoadState('networkidle');
  }
  await expect(page.locator(`.member-id:text-is("@${secondUserLoginId}")`)).toBeVisible();

  // Promote to manager via the per-member role dropdown (role-apply-btn, data-role-id="1"). The
  // dropdown-menu is display:none until the Bootstrap dropdown-toggle button is clicked open, AND
  // the widget's JS copies the currently-active <li><a> into a "d-label" span inside the toggle
  // button itself to show the current selection -- so `a.role-apply-btn[data-role-id="1"]`
  // unscoped matches TWO elements (the real menu item plus that label copy). Scope to
  // `ul.dropdown-menu` to hit only the real, clickable menu item.
  //
  // Normalize to MEMBER first (idempotent across reruns -- a prior partial run may have already
  // left this member as MANAGER), then promote to MANAGER so the actual transition is exercised.
  const memberCard = page.locator('li.member', { has: page.locator(`.member-id:text-is("@${secondUserLoginId}")`) });
  await memberCard.locator('button.dropdown-toggle').click();
  await memberCard.locator('ul.dropdown-menu a.role-apply-btn[data-role-id="2"]').click();
  await page.waitForLoadState('networkidle');

  const normalizedCard = page.locator('li.member', { has: page.locator(`.member-id:text-is("@${secondUserLoginId}")`) });
  await expect(normalizedCard.locator('li[data-value="2"]')).toHaveClass(/active/);

  await normalizedCard.locator('button.dropdown-toggle').click();
  await normalizedCard.locator('ul.dropdown-menu a.role-apply-btn[data-role-id="1"]').click();
  await page.waitForLoadState('networkidle');

  // The active message bundle in this environment renders the role label in English
  // ("Manager"/"Member"), not the Korean placeholder text visible in the .html source -- assert
  // via the dropdown item's "active" class instead of label text.
  const promotedCard = page.locator('li.member', { has: page.locator(`.member-id:text-is("@${secondUserLoginId}")`) });
  await expect(promotedCard.locator('li[data-value="1"]')).toHaveClass(/active/);
});

test('invite a throwaway member and remove them', async ({ page, browser }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const suffix = uniqueSuffix();
  const throwawayLoginId = `e2ememb${suffix}`;

  // Sign up a fresh account in a brand-new, logged-out BROWSER CONTEXT (not just a new page in
  // the shared context, which would clear the admin storageState cookies out from under `page`
  // since a context's cookie jar is shared across all its pages).
  //
  // clearCookies() is required even on a just-created context: observed in this environment that
  // a brand-new browser.newContext() can still carry over the admin `remember-me` cookie (visible
  // via ctx.cookies() right after the first navigation) even though this project's chromium config
  // only applies storageState to the Playwright-managed `page`/`context` fixtures, not to contexts
  // created manually with browser.newContext(). A plain `curl` with no cookie jar never receives
  // this cookie, so the server itself is not unconditionally issuing it -- this looks like a
  // Playwright/Chromium context-isolation quirk in this environment rather than a yona product bug.
  // Without the explicit clear, this signup silently happens as admin instead of anonymously.
  const signupContext = await browser.newContext();
  await signupContext.clearCookies();
  const signupPage = await signupContext.newPage();
  await signupPage.goto('/signup');
  await signupPage.fill('#loginId', throwawayLoginId);
  await signupPage.fill('#uname', `E2E Member ${suffix}`);
  await signupPage.fill('#email', `${throwawayLoginId}@yona-e2e.test`);
  await signupPage.fill('#password', 'ThrowawayPassw0rd!');
  await signupPage.fill('#retypedPassword', 'ThrowawayPassw0rd!');
  await signupPage.click('form[action="/signup"] button[type=submit]');
  await signupContext.close();

  await page.goto(`/${owner}/${name}/members`);
  await page.fill('#addNewMember #loginId', throwawayLoginId);
  await page.click('#addNewMember button[type=submit]');
  await page.waitForLoadState('networkidle');
  await expect(page.locator(`.member-id:text-is("@${throwawayLoginId}")`)).toBeVisible();

  const memberCard = page.locator('li.member', { has: page.locator(`.member-id:text-is("@${throwawayLoginId}")`) });
  page.once('dialog', (d) => d.accept());
  await memberCard.locator('a.delete-member-btn').click();
  await page.waitForLoadState('networkidle');

  await expect(page.locator(`.member-id:text-is("@${throwawayLoginId}")`)).toHaveCount(0);
});
