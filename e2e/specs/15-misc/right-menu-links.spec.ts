import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: the right-hand slide menu (#mySidenav, common/usermenu_tab_content_list.html, wired by
 * yona.Usermenu.js) -- clicking a project must open a real page. The list used to link to
 * /{owner}/{project}/go, which has no mapping in this app (404), so every project click opened a
 * 404 page in a new tab. */
test.describe('right slide menu project links', () => {
  test('clicking a project opens the project home (200), not a 404 page', async ({ page, context }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    await page.goto('/');
    await page.click('#sidebar-open-btn');
    await page.click('a[href="#myProjectList"]');
    await page.click('#mySidenav a[href="#createdByMe"]');

    const item = page.locator(`#mySidenav #createdByMe .user-li[data-location="/${owner}/${name}"]`);
    await expect(item).toBeVisible();

    // The new tab's navigation response can arrive before a listener attached after the click, so collect it up front.
    const navigations: Array<[number, string]> = [];
    context.on('response', (r) => {
      if (r.request().isNavigationRequest()) navigations.push([r.status(), new URL(r.url()).pathname]);
    });

    // A plain click opens the project in a new tab (window.open(..., '_blank')).
    const popupPromise = context.waitForEvent('page');
    await item.click();
    const popup = await popupPromise;
    await popup.waitForLoadState('load');
    expect(navigations.find(([, path]) => path === `/${owner}/${name}`)?.[0]).toBe(200);
    expect(navigations.some(([status]) => status === 404)).toBe(false);
    await expect(popup).toHaveURL(new RegExp(`/${owner}/${name}$`));
    await expect(popup.locator('.project-header-outer')).toBeVisible();
  });
});
