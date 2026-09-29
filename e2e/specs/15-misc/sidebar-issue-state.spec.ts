import {test, expect} from '@playwright/test';
import {requireSeed} from '../../support/seed-store';

test('sidebar project subtab and search survive ordinary navigation into an existing issue body', async ({page}) => {
  const owner = requireSeed('projectOwner');
  const project = requireSeed('projectName');
  const issue = requireSeed('issueNumber');
  const base = `/${owner}/${project}`;
  await page.goto(`${base}/issues`);
  await page.locator('[data-sidebar-toggle]').click();
  const sidebar = page.locator('turbo-frame#sidebar');
  await expect(sidebar.locator('[data-sidebar-refresh]')).toBeVisible();
  await sidebar.locator('.nav-tabs .myProjectList a').click();
  await sidebar.locator('a[href="#sidebar-createdByMe"]').click();
  await sidebar.locator('#sidebar-myProjectList .project-search').fill(project);

  await page.goto(`${base}/issue/${issue}`);
  await expect(page.locator(`#issue-body-${issue} .markdown-wrap`)).toContainText('Body written by the e2e suite.');
  await expect(sidebar).toBeVisible();
  await expect(sidebar.locator('.nav-tabs .myProjectList')).toHaveClass(/active/);
  await expect(sidebar.locator('#sidebar-createdByMe')).toHaveClass(/active/);
  await expect(sidebar.locator('#sidebar-myProjectList .project-search')).toHaveValue(project);

  await Promise.all([
    page.waitForResponse(response => response.request().headers()['turbo-frame'] === 'sidebar'),
    sidebar.locator('[data-sidebar-refresh]').click(),
  ]);
  await expect(sidebar).not.toHaveAttribute('aria-busy', 'true');
  await expect(sidebar.locator('#sidebar-createdByMe')).toHaveClass(/active/);
  await expect(sidebar.locator('#sidebar-myProjectList .project-search')).toHaveValue(project);
  await page.goBack();
  await expect(page).toHaveURL(new RegExp(`${base}/issues$`));
  await expect(sidebar).toBeVisible();
  await expect(sidebar.locator('#sidebar-createdByMe')).toHaveClass(/active/);
  await expect(sidebar.locator('#sidebar-myProjectList .project-search')).toHaveValue(project);
});

test('issue detail frames and comment drafts remain independent of sidebar refresh', async ({page}) => {
  const owner = requireSeed('projectOwner');
  const project = requireSeed('projectName');
  const issue = requireSeed('issueNumber');
  const base = `/${owner}/${project}`;
  await page.setViewportSize({width: 1440, height: 900});
  await page.goto(`${base}/issues`);
  await page.locator('#two-column-mode').check();
  await page.locator('[data-sidebar-toggle]').click();
  const sidebar = page.locator('turbo-frame#sidebar');
  await expect(sidebar.locator('[data-sidebar-refresh]')).toBeVisible();
  await sidebar.locator('.nav-tabs .myProjectList a').click();
  await sidebar.locator('a[href="#sidebar-createdByMe"]').click();
  await sidebar.locator('#sidebar-myProjectList .project-search').fill(project);
  await sidebar.evaluate(element => Reflect.set(window, '__sidebarBeforeIssue', element));
  await sidebar.locator('[data-sidebar-close]').click();

  await page.locator(`#issue-list a.title[data-detail-url="${base}/issue/${issue}"]:visible`).first().click();
  const detail = page.locator('#issue-detail');
  await expect(detail.locator(`#issue-body-${issue} .markdown-wrap`)).toContainText('Body written by the e2e suite.');
  const comment = detail.locator('#comment-form .cm-content');
  await comment.pressSequentially('Unsent comment while checking independent sidebar state');
  await expect(detail.locator('#comment-form yona-markdown-editor')).toHaveJSProperty('value', 'Unsent comment while checking independent sidebar state');
  await page.locator('[data-sidebar-toggle]').click();
  expect(await sidebar.evaluate(element => element === Reflect.get(window, '__sidebarBeforeIssue'))).toBe(true);
  await expect(sidebar.locator('#sidebar-createdByMe')).toHaveClass(/active/);
  await expect(sidebar.locator('#sidebar-myProjectList .project-search')).toHaveValue(project);

  await Promise.all([
    page.waitForResponse(response => response.request().headers()['turbo-frame'] === 'sidebar'),
    sidebar.locator('[data-sidebar-refresh]').click(),
  ]);
  await expect(sidebar).not.toHaveAttribute('aria-busy', 'true');
  await expect(comment).toHaveText('Unsent comment while checking independent sidebar state');
  await expect(detail.locator(`#issue-body-${issue} .markdown-wrap`)).toContainText('Body written by the e2e suite.');
  await expect(page).toHaveURL(new RegExp(`${base}/issues\\?selected=${issue}$`));
});
