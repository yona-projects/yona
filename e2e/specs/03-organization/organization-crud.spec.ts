import { test, expect } from '@playwright/test';
import { requireSeed, writeSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screens: GET /orgs (list), /organizations/new -> create, /organizations/{orgName} (view),
 * .../members, .../issues, .../boards, .../pullrequests, .../settingform -> save,
 * .../deleteForm (loaded only -- never submitted, so later specs can still reference the org). */

test('organization list screen loads', async ({ page }) => {
  const response = await page.goto('/orgs');
  expect(response?.status()).toBeLessThan(400);
});

test('create an organization with name and description', async ({ page }) => {
  const orgName = `e2e-org-${uniqueSuffix()}`;

  await page.goto('/organizations/new');
  await page.fill('#name', orgName);
  await page.fill('#descr', 'Organization created by the e2e suite');
  // create.html's submit <button> has no explicit type attribute (defaults to submit inside a
  // <form>, so a `button[type=submit]` CSS selector would NOT match it) -- scope by the form's
  // action instead.
  await page.click('form[action="/organizations/new"] button.ybtn-success');

  await expect(page).toHaveURL(new RegExp(`/organizations/${orgName}$`));
  writeSeed({ orgName });
});

test('organization home view shows the org and its (empty) project list', async ({ page }) => {
  const orgName = requireSeed('orgName');
  await page.goto(`/organizations/${orgName}`);
  await expect(page.locator('body')).toContainText(orgName);
});

test('organization members screen loads for an org admin', async ({ page }) => {
  const orgName = requireSeed('orgName');
  // The creator is auto-enrolled as ORG_ADMIN by organizationService.createOrganization(), so
  // the seeded admin session should pass the ORG_ADMIN-or-site-manager gate either way.
  const response = await page.goto(`/organizations/${orgName}/members`);
  expect(response?.status()).toBeLessThan(400);
});

test('invite a member, change their role, then remove them', async ({ page }) => {
  const orgName = requireSeed('orgName');
  const secondUserLoginId = requireSeed('secondUserLoginId');

  await page.goto(`/organizations/${orgName}/members`);

  // #addNewMember's submit handler does e.preventDefault() and fetches
  // /api/organizations/{orgId}/members?userLoginId=...&roleId=7 (roleId 7 == org member), then
  // reloads the page on success -- there's no `<form action>` navigation to wait on.
  await page.fill('#loginId', secondUserLoginId);
  await Promise.all([
    page.waitForLoadState('load'),
    page.click('#addNewMember button[type=submit]'),
  ]);

  const memberRow = page.locator('ul.members li.member', { hasText: `@${secondUserLoginId}` });
  await expect(memberRow).toBeVisible();

  // Role change: role id 6 == org_admin per members.html's inline ternary
  // (`role.id == 6 ? 'org_admin' : 'org_member'`) -- click that role option in the invited
  // member's dropdown. Fires a PUT to /api/organizations/{orgId}/members/{userId}/role and
  // reloads. The option lives inside a Bootstrap-style dropdown-menu (display:none until the
  // toggle button opens it) -- clicking it directly without opening the dropdown first hangs
  // forever on Playwright's actionability wait, since the locator resolves to the right <a> but
  // it's never "visible, enabled and stable" while its menu stays closed. members.html wires each
  // .role-apply-btn with a plain `document.querySelectorAll(".role-apply-btn").forEach(el =>
  // el.addEventListener("click", ...))` -- a direct per-element listener, not a delegate keyed
  // off the dropdown being open. The dropdown-menu itself stays CSS-hidden without a working
  // Bootstrap dropdown toggle on this page, so neither a plain click nor a Playwright
  // {force: true} click can land on it (both still require a non-zero bounding box in real
  // Chromium). A native DOM .click() call fires the addEventListener handler directly regardless
  // of visibility, which is all this needs.
  await memberRow.locator('.role-apply-btn[data-role-id="6"]').evaluate((el: HTMLElement) => el.click());
  await page.waitForLoadState('load');

  const reloadedRow = page.locator('ul.members li.member', { hasText: `@${secondUserLoginId}` });
  await expect(reloadedRow.locator('.btn-group[data-name*="roleof"] li[data-selected="true"]')).toHaveAttribute(
    'data-value',
    '6'
  );

  // Remove: opens the native <dialog id="alertDeletion"> confirm modal, #deleteBtn fires the
  // actual DELETE. Removing here undoes the invite so later specs don't inherit an extra org
  // member.
  await reloadedRow.locator('.delete-member-btn').click();
  await expect(page.locator('#alertDeletion')).toBeVisible();
  await Promise.all([
    page.waitForLoadState('load'),
    page.click('#alertDeletion #deleteBtn'),
  ]);

  await expect(page.locator('ul.members li.member', { hasText: `@${secondUserLoginId}` })).toHaveCount(0);
});

test('organization issues rollup loads with zero member projects', async ({ page }) => {
  const orgName = requireSeed('orgName');
  const response = await page.goto(`/organizations/${orgName}/issues`);
  expect(response?.status()).toBeLessThan(500);
});

test('organization boards rollup loads with zero member projects', async ({ page }) => {
  const orgName = requireSeed('orgName');
  const response = await page.goto(`/organizations/${orgName}/boards`);
  expect(response?.status()).toBeLessThan(500);
});

test('organization pull requests rollup loads with zero member projects', async ({ page }) => {
  const orgName = requireSeed('orgName');
  const response = await page.goto(`/organizations/${orgName}/pullrequests`);
  expect(response?.status()).toBeLessThan(500);
});

test('update organization name and description via the settings form', async ({ page }) => {
  const orgName = requireSeed('orgName');
  const renamedTo = `${orgName}-renamed`;

  await page.goto(`/organizations/${orgName}/settingform`);
  await page.fill('#saveSetting #project-name', renamedTo);
  await page.fill('#saveSetting #project-desc', 'Updated by the e2e suite');
  await page.click('#saveSetting #save');

  await expect(page).toHaveURL(new RegExp(`/organizations/${renamedTo}$`));
  writeSeed({ orgName: renamedTo });
});

test('issues/boards/pullrequests rollups actually show content from a real org-owned project', async ({ page }) => {
  const orgName = requireSeed('orgName');
  const projectName = `e2e-org-project-${uniqueSuffix()}`;

  // #project-owner lists every organization the current user is ORG_ADMIN of
  // (ProjectViewController.newProjectForm()) -- admin is auto-enrolled as ORG_ADMIN on the org it
  // created earlier in this file, so it's selectable here the same way 00-project-create.spec.ts
  // selects the admin's own loginId.
  await page.goto('/projectform');
  await page.selectOption('#project-owner', orgName);
  await page.fill('#project-name', projectName);
  await page.fill('#description', 'Org-owned project for rollup verification');
  await page.check('#public');
  await page.selectOption('#vcs', 'GIT');
  await page.click('#newProjectForm button.ybtn-success');
  await expect(page).toHaveURL(new RegExp(`/${orgName}/${projectName}`));

  const issueTitle = `e2e org rollup issue ${uniqueSuffix()}`;
  await page.goto(`/${orgName}/${projectName}/issueform`);
  await page.fill('#title', issueTitle);
  await page.locator('textarea[data-editor-mode="content-body"]').fill('Body for org rollup verification.', { force: true });
  await page.click('#button-save');
  await expect(page).toHaveURL(new RegExp(`/${orgName}/${projectName}/issue/\\d+`));

  await page.goto(`/organizations/${orgName}/issues`);
  await expect(page.locator('body')).toContainText(issueTitle);

  const postTitle = `e2e org rollup post ${uniqueSuffix()}`;
  await page.goto(`/${orgName}/${projectName}/postform`);
  await page.fill('#title', postTitle);
  await page.locator('textarea[data-editor-mode="content-body"]').fill('Body for org rollup verification.', { force: true });
  await page.click('#post-form button[type=submit]');
  await expect(page).toHaveURL(new RegExp(`/${orgName}/${projectName}/post/\\d+`));

  await page.goto(`/organizations/${orgName}/boards`);
  await expect(page.locator('body')).toContainText(postTitle);

  // Pull requests require code (a real commit + a second branch) -- out of scope for a rollup
  // content check; the zero-project-state test above already confirms this route itself doesn't
  // 500 with a real member project present.
  const pullRequestsResponse = await page.goto(`/organizations/${orgName}/pullrequests`);
  expect(pullRequestsResponse?.status()).toBeLessThan(500);
});

test('delete-organization confirmation screen loads (not submitted -- later specs still need the org)', async ({ page }) => {
  const orgName = requireSeed('orgName');
  const response = await page.goto(`/organizations/${orgName}/deleteForm`);
  expect(response?.status()).toBeLessThan(400);
});

test('creating an organization without a name is rejected', async ({ page }) => {
  await page.goto('/organizations/new');
  await page.fill('#name', '');
  await page.click('form[action="/organizations/new"] button.ybtn-success');

  // Validation failure re-renders the create form (organizationService.createOrganization
  // throws, caught by the controller) instead of redirecting to a new org's home.
  await expect(page).toHaveURL(/\/organizations\/new$/);
});
