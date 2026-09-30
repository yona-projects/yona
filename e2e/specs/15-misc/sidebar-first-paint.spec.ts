import {test, expect} from '@playwright/test';
import {requireSeed} from '../../support/seed-store';

test('an open sidebar is present with its state before deferred scripts on the next issue page', async ({page}) => {
  const project = requireSeed('projectName');
  const base = `/${requireSeed('projectOwner')}/${project}`;
  const issue = requireSeed('issueNumber');
  await page.goto(`${base}/issues`);
  const sidebar = page.locator('turbo-frame#sidebar');
  await page.locator('[data-sidebar-toggle]').click();
  await expect(sidebar.locator('[data-sidebar-refresh]')).toBeVisible();
  await sidebar.locator('.nav-tabs .myProjectList a').click();
  await sidebar.locator('a[href="#sidebar-createdByMe"]').click();
  await sidebar.locator('#sidebar-myProjectList .project-search').fill(project);

  const {promise: gate, resolve: release} = Promise.withResolvers<void>();
  const {promise: requested, resolve: markRequested} = Promise.withResolvers<void>();
  await page.route('**/javascripts/service/yona.sidebar.Turbo.js', async route => {
    markRequested();
    await gate;
    await route.continue();
  });
  const sidebarRequests: string[] = [];
  page.on('request', request => {
    if (new URL(request.url()).pathname === '/user/sidebar') sidebarRequests.push(request.url());
  });
  try {
    await page.goto(`${base}/issue/${issue}`, {waitUntil: 'commit'});
    await requested;
    await expect(sidebar).toBeVisible();
    await expect(sidebar.locator('[data-sidebar-refresh]')).toBeVisible();
    await expect(sidebar.locator('#sidebar-createdByMe')).toHaveClass(/active/);
    await expect(sidebar.locator('#sidebar-myProjectList .project-search')).toHaveValue(project);
    await expect(sidebar.locator(`#sidebar-createdByMe .project-name > a[href="${base}/go"]`)).toBeVisible();
    expect(sidebarRequests).toEqual([]);
    const before = (await sidebar.boundingBox())!;
    release();
    await page.waitForLoadState('load');
    await expect(page.locator(`#issue-body-${issue} .markdown-wrap`)).toContainText('Body written by the e2e suite.');
    const after = (await sidebar.boundingBox())!;
    expect(after.x).toBe(before.x);
    expect(after.width).toBe(before.width);
    expect(sidebarRequests).toEqual([]);
    await expect(sidebar.locator('#sidebar-myProjectList .project-search')).toHaveValue(project);
  } finally { release(); }
});

test('closing the sidebar makes the next response lazy and does not add URL state', async ({page}) => {
  const base = `/${requireSeed('projectOwner')}/${requireSeed('projectName')}`;
  await page.goto(`${base}/issues`);
  const sidebar = page.locator('turbo-frame#sidebar');
  await page.locator('[data-sidebar-toggle]').click();
  await expect(sidebar.locator('[data-sidebar-refresh]')).toBeVisible();
  await sidebar.locator('[data-sidebar-close]').click();
  const requests: string[] = [];
  page.on('request', request => {
    if (new URL(request.url()).pathname === '/user/sidebar') requests.push(request.url());
  });
  await page.goto(`${base}/issue/${requireSeed('issueNumber')}`);
  await expect(sidebar).toBeHidden();
  await expect(sidebar.locator('#sidebar-usermenu-tab-content-list')).toHaveCount(0);
  expect(requests).toEqual([]);
  await expect(page).toHaveURL(new RegExp(`${base}/issue/${requireSeed('issueNumber')}$`));
});
