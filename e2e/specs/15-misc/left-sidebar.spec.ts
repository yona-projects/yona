import { test, expect, type Page } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: the left sidebar (<yona-sidebar>, a Shadow DOM web component). It replaces the iframe-hosted
 * layout_framed sidebar: every page now includes it directly, its open state lives in localStorage
 * (`yonaLeftSidebarOpen`), and its items are real <a href> links. The right-hand slide menu
 * (#sidebar-open-btn) is a separate feature and is not touched here (see favorites.spec.ts). */
const OPEN_KEY = 'yonaLeftSidebarOpen';
const LEGACY_KEY = 'shallWeOpenLeftNavigation';

const sidebarOpen = (page: Page) => page.evaluate(() => document.documentElement.classList.contains('left-sidebar-open'));
const stored = (page: Page, key: string) => page.evaluate((k) => localStorage.getItem(k), key);

async function reset(page: Page, url: string) {
  await page.goto(url);
  await page.evaluate(([a, b]) => { localStorage.removeItem(a); localStorage.removeItem(b); sessionStorage.clear(); }, [OPEN_KEY, LEGACY_KEY]);
  await page.reload();
}

test.describe('left sidebar (<yona-sidebar>)', () => {
  test('starts closed, opens from the GNB pin, persists the state and reserves its width', async ({ page }) => {
    await reset(page, '/');
    const sidebar = page.locator('yona-sidebar');
    await expect(sidebar).toBeHidden();
    await expect(page.locator('.pin')).toHaveAttribute('aria-expanded', 'false');

    await page.locator('.pin').click();
    await expect(sidebar).toBeVisible();
    await expect(page.locator('.pin')).toHaveAttribute('aria-expanded', 'true');
    expect(await sidebarOpen(page)).toBe(true);
    expect(await stored(page, OPEN_KEY)).toBe('true');
    // The body is pushed right by the sidebar width instead of being covered by it.
    // The body padding is transitioning (slide), so poll instead of reading the first value.
    await expect.poll(() => page.evaluate(() => parseInt(getComputedStyle(document.body).paddingLeft, 10))).toBe(270);

    await page.locator('.pin').click();
    await expect(sidebar).toBeHidden();
    expect(await stored(page, OPEN_KEY)).toBe('false');
    await expect.poll(() => page.evaluate(() => parseInt(getComputedStyle(document.body).paddingLeft, 10))).toBe(0);
  });

  for (const [label, path] of [['a plain page', () => '/'], ['a project page', () => `/${requireSeed('projectOwner')}/${requireSeed('projectName')}/issues`]] as const) {
    test(`the sidebar slides and the pin follows its edge on ${label}`, async ({ page }) => {
      await reset(page, path());
      const pinX = () => page.evaluate(() => Math.round(document.querySelector('.pin')!.getBoundingClientRect().x));
      const sidebarX = () => page.evaluate(() => Math.round(document.querySelector('yona-sidebar')!.getBoundingClientRect().x));
      await page.waitForTimeout(400); // let the post-load "ready" frames pass so transitions are on

      expect(await pinX()).toBe(-6);
      await page.locator('.pin').click();
      // Mid-slide: the sidebar is between fully hidden (-270) and fully open (0), and the pin has left its closed spot.
      await page.waitForTimeout(60);
      const midSidebar = await sidebarX();
      expect(midSidebar).toBeGreaterThan(-270);
      expect(midSidebar).toBeLessThan(0);
      // Flush against the sidebar's right edge: the pin must not overlap the sidebar (width 270).
      await expect.poll(pinX).toBe(270);
      await expect.poll(sidebarX).toBe(0);

      await page.locator('.pin').click();
      await expect.poll(pinX).toBe(-6);
      await expect(page.locator('yona-sidebar')).toBeHidden();
    });
  }

  test('a stored open state is applied at once on load, without replaying the slide', async ({ page }) => {
    await reset(page, '/');
    await page.locator('.pin').click();
    await expect.poll(() => page.evaluate(() => Math.round(document.querySelector('yona-sidebar')!.getBoundingClientRect().x))).toBe(0);
    // Reload while open: right after commit the sidebar and the pin must already be in their open positions.
    await page.reload({ waitUntil: 'commit' });
    await page.waitForSelector('.pin', { state: 'attached' });
    const first = await page.evaluate(() => ({
      sidebar: Math.round(document.querySelector('yona-sidebar')!.getBoundingClientRect().x),
      pin: Math.round(document.querySelector('.pin')!.getBoundingClientRect().x),
    }));
    expect(first).toEqual({ sidebar: 0, pin: 270 });
  });

  test('an open sidebar stays open across navigation and reload, with no iframe anywhere', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    await reset(page, '/');
    await page.locator('.pin').click();
    await expect(page.locator('yona-sidebar')).toBeVisible();

    await page.goto(`/${owner}/${name}/issues`);
    // The open state is applied before first paint by the head script, so the layout is already reserved.
    expect(await sidebarOpen(page)).toBe(true);
    await expect(page.locator('yona-sidebar')).toBeVisible();
    await expect(page.locator('yona-sidebar .sidebar')).toBeVisible();
    expect(page.frames().length).toBe(1);
    await expect(page.locator('iframe#mainFrameId')).toHaveCount(0);

    await page.reload();
    await expect(page.locator('yona-sidebar .sidebar')).toBeVisible();
  });

  test('project items are real links that navigate the page, and the URL is the real page URL', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    await reset(page, '/');
    await page.locator('.pin').click();
    await page.locator('yona-sidebar [data-tab="myProjectList"]').click();
    await page.locator('yona-sidebar [data-subtab="createdByMe"]').click();

    const link = page.locator(`yona-sidebar a.project-list[href="/${owner}/${name}"]`);
    await expect(link).toBeVisible();
    expect(await link.getAttribute('target')).toBeNull();
    // 404 페이지로 가는 링크를 URL만 보고 통과시키지 않도록 이동한 응답의 상태까지 확인한다.
    const [response] = await Promise.all([page.waitForNavigation(), link.click()]);
    expect(response?.status()).toBe(200);
    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}$`));
    await expect(page.locator('.project-header-outer')).toBeVisible();
    expect(page.url()).not.toContain('/user/sidebar');
    await expect(page.locator('yona-sidebar .sidebar')).toBeVisible();
  });

  test('the active tab and the search text are restored after navigation', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    await reset(page, '/');
    await page.locator('.pin').click();
    await page.locator('yona-sidebar [data-tab="myProjectList"]').click();
    await page.locator('yona-sidebar [data-subtab="createdByMe"]').click();
    await page.locator('yona-sidebar .project-search').fill(String(name).slice(0, 4));

    await page.goto(`/${owner}/${name}/issues`);
    await expect(page.locator('yona-sidebar [data-tab="myProjectList"]')).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('yona-sidebar [data-subtab="createdByMe"]')).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('yona-sidebar .project-search')).toHaveValue(String(name).slice(0, 4));
  });

  test('navigating shows the cached list at once and does not ask the server again within 15 seconds', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    let apiCalls = 0;
    await page.route('**/-_-api/v1/usermenu', async (route) => {
      apiCalls += 1;
      await route.continue();
    });

    await reset(page, '/');
    await page.locator('.pin').click();
    await page.locator('yona-sidebar [data-tab="myProjectList"]').click();
    await page.locator('yona-sidebar [data-subtab="createdByMe"]').click();
    await expect(page.locator('yona-sidebar #myProjectList a.project-list').first()).toBeVisible();
    expect(apiCalls).toBe(1);

    // Slow the API down a lot: the list must still be there immediately because it comes from the cache.
    await page.unroute('**/-_-api/v1/usermenu');
    await page.route('**/-_-api/v1/usermenu', async (route) => {
      apiCalls += 1;
      await new Promise((resolve) => setTimeout(resolve, 3000));
      await route.continue();
    });
    await page.goto(`/${owner}/${name}/issues`);
    await expect(page.locator('yona-sidebar #myProjectList a.project-list').first()).toBeVisible({ timeout: 1500 });
    await expect(page.locator('yona-sidebar .status')).toHaveCount(0);

    // Two more navigations inside the 15 second window never reach the server.
    await page.goto(`/${owner}/${name}`);
    await expect(page.locator('yona-sidebar #myProjectList a.project-list').first()).toBeVisible({ timeout: 1500 });
    expect(apiCalls).toBe(1);
  });

  test('the legacy shallWeOpenLeftNavigation flag is migrated once', async ({ page }) => {
    await reset(page, '/');
    await page.evaluate((k) => localStorage.setItem(k, 'true'), LEGACY_KEY);
    await page.reload();
    await expect(page.locator('yona-sidebar .sidebar')).toBeVisible();
    expect(await stored(page, OPEN_KEY)).toBe('true');
    expect(await stored(page, LEGACY_KEY)).toBeNull();
  });

  test('old /user/sidebar links redirect to the original page and never leave the site', async ({ page }) => {
    await page.goto('/user/sidebar?path=/user/issues');
    await expect(page).toHaveURL(/\/user\/issues$/);

    await page.goto('/user/sidebar?path=//evil.example.com/x');
    await expect(page).toHaveURL(/localhost:\d+\/$/);
  });

  test('the slide iframe of the issue two-column view does not get its own sidebar', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    await reset(page, '/');
    await page.locator('.pin').click();
    await page.goto(`/${owner}/${name}/issues`);

    const inFrame = await page.evaluate(async (url) => {
      const frame = document.createElement('iframe');
      frame.src = url;
      document.body.appendChild(frame);
      await new Promise((resolve) => frame.addEventListener('load', resolve, { once: true }));
      const doc = frame.contentDocument!;
      return { sidebar: doc.querySelector('yona-sidebar'), flagged: doc.documentElement.classList.contains('in-frame') };
    }, `/${owner}/${name}/issues`);
    expect(inFrame.sidebar).toBeNull();
    expect(inFrame.flagged).toBe(true);
  });
});
