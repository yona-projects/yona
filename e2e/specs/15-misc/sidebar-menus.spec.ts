import { test, expect, type Page } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

async function openProjectMenus(page: Page) {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  await page.goto(`/${owner}/${name}`);
  await page.locator('[data-sidebar-toggle]').click();
  const left = page.locator('turbo-frame#sidebar');
  await left.locator('.nav-tabs .myProjectList a').click();
  await left.locator('a[href="#sidebar-createdByMe"]').click();
  return { left, right: page.locator('#mySidenav'), owner, name };
}

test.describe('independent sidebar menus', () => {
  test('search, tab transfer, and Escape stay in their own menu', async ({ page }) => {
    const { left, right, owner, name } = await openProjectMenus(page);
    const documents: string[] = [];
    page.on('request', request => {
      if (request.isNavigationRequest() && request.resourceType() === 'document') documents.push(request.url());
    });
    const rowSelector = `.user-li[data-location="/${owner}/${name}/go"]`;
    const leftRow = left.locator(`#sidebar-createdByMe ${rowSelector}`);
    const leftSearch = left.locator('#sidebar-myProjectList .project-search');
    await expect(leftRow).toBeVisible();
    await expect(leftRow.locator('.project-name > a')).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
    await leftSearch.fill('no-matching-sidebar-project');
    await expect(leftRow).toBeHidden();

    await page.locator('#sidebar-open-btn').click();
    await right.locator('.nav-tabs .myProjectList a').click();
    await right.locator('a[href="#createdByMe"]').click();
    const rightRow = right.locator(`#createdByMe ${rowSelector}`);
    const rightSearch = right.locator('#myProjectList .project-search');
    await expect(rightRow).toBeVisible();
    await rightSearch.fill(name);
    await expect(rightRow).toBeVisible();
    await expect(leftSearch).toHaveValue('no-matching-sidebar-project');
    await expect(leftRow).toBeHidden();

    await right.locator('.nav-tabs .myOrganizationList a').click();
    const rightOrgSearch = right.locator('.org-search');
    await expect(rightOrgSearch).toHaveValue(name);
    await expect(rightSearch).toHaveValue('');
    await expect(left.locator('#sidebar-myProjectList')).toHaveClass(/active/);
    await expect(leftSearch).toHaveValue('no-matching-sidebar-project');
    await expect(right.locator(`#myOrganizationList ${rowSelector}`)).toBeVisible();

    await rightOrgSearch.press('Escape');
    await expect(right).toHaveCSS('width', '0px');
    await expect(page.locator('body')).toHaveClass(/left-sidebar-open/);
    await leftSearch.fill('');
    await expect(leftRow).toBeVisible();
    await leftSearch.press('Escape');
    await expect(page.locator('body')).not.toHaveClass(/left-sidebar-open/);
    expect(documents).toEqual([]);
  });

  test('refresh and reopen keep one favorite request and synchronize the breadcrumb', async ({ page }) => {
    const { left, right } = await openProjectMenus(page);
    const breadcrumb = page.locator('.project-breadcrumb > .user-project-list[data-project-id]');
    const projectId = await breadcrumb.getAttribute('data-project-id');
    expect(projectId).toBeTruthy();
    const starSelector = `.star-project[data-project-id="${projectId}"]`;
    const leftStar = left.locator(`#sidebar-createdByMe ${starSelector}`);
    const rightStar = right.locator(`#createdByMe ${starSelector}`);

    for (let refresh = 0; refresh < 2; refresh++) {
      await Promise.all([
        page.evaluate(() => new Promise<void>(resolve => {
          document.getElementById('sidebar')!.addEventListener('turbo:frame-load', () => resolve(), { once: true });
        })),
        left.locator('[data-sidebar-refresh]').click(),
      ]);
      await expect(left.locator('#sidebar-myProjectList')).toHaveClass(/active/);
      await left.locator('a[href="#sidebar-createdByMe"]').click();
      await left.locator('[data-sidebar-close]').click();
      await page.locator('[data-sidebar-toggle]').click();
      await expect(leftStar).toBeVisible();
    }

    const wasStarred = await leftStar.locator('i').evaluate(icon => icon.classList.contains('starred'));
    const togglePath = `/-_-api/v1/favoriteProjects/${projectId}`;
    const posts: string[] = [];
    page.on('request', request => {
      if (new URL(request.url()).pathname === togglePath && request.method() === 'POST') posts.push(request.url());
    });
    let changed = false;
    try {
      const [response] = await Promise.all([
        page.waitForResponse(response => new URL(response.url()).pathname === togglePath && response.request().method() === 'POST'),
        leftStar.click(),
      ]);
      expect(response.ok()).toBeTruthy();
      const result = await response.json();
      changed = result.favored !== wasStarred;
      expect(result.favored).toBe(!wasStarred);
      for (const icon of [leftStar.locator('i'), rightStar.locator('i'), breadcrumb.locator('i')]) {
        if (result.favored) await expect(icon).toHaveClass(/starred/);
        else await expect(icon).not.toHaveClass(/starred/);
      }
      expect(posts).toHaveLength(1);
    } finally {
      if (changed) {
        const [response] = await Promise.all([
          page.waitForResponse(response => new URL(response.url()).pathname === togglePath && response.request().method() === 'POST'),
          breadcrumb.click(),
        ]);
        expect(response.ok()).toBeTruthy();
        expect((await response.json()).favored).toBe(wasStarred);
        if (wasStarred) await expect(leftStar.locator('i')).toHaveClass(/starred/);
        else await expect(leftStar.locator('i')).not.toHaveClass(/starred/);
        expect(posts).toHaveLength(2);
      }
    }
  });

  test('native links retain modifiers and the right menu does not hijack owner links', async ({ page }) => {
    const { left, right, owner, name } = await openProjectMenus(page);
    const originalUrl = page.url();
    const rowSelector = `.user-li[data-location="/${owner}/${name}/go"]`;
    await page.bringToFront();
    const [modifiedPage] = await Promise.all([
      page.context().waitForEvent('page'),
      left.locator(`#sidebar-createdByMe ${rowSelector} .project-name > a`).click({ modifiers: ['ControlOrMeta'] }),
    ]);
    await modifiedPage.waitForURL(url => url.pathname === `/${owner}/${name}` || url.pathname.startsWith(`/${owner}/${name}/`));
    expect(page.url()).toBe(originalUrl);
    await modifiedPage.close();

    await page.locator('#sidebar-open-btn').click();
    await right.locator('.nav-tabs .myProjectList a').click();
    await right.locator('a[href="#createdByMe"]').click();
    const rightRow = right.locator(`#createdByMe ${rowSelector}`);
    const [externalPage] = await Promise.all([
      page.context().waitForEvent('page'),
      rightRow.locator('.project-name > a').click(),
    ]);
    await externalPage.waitForURL(url => url.pathname === `/${owner}/${name}` || url.pathname.startsWith(`/${owner}/${name}/`));
    expect(page.url()).toBe(originalUrl);
    await externalPage.close();

    const [ownerRequest] = await Promise.all([
      page.waitForRequest(request => request.isNavigationRequest() && new URL(request.url()).pathname === `/${owner}`),
      rightRow.locator('.project-owner a').click(),
    ]);
    expect(ownerRequest.frame()).toBe(page.mainFrame());
    await page.waitForLoadState('domcontentloaded');
  });
});
