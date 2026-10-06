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
    expect(await page.evaluate(() => parseInt(getComputedStyle(document.body).paddingLeft, 10))).toBe(270);

    await page.locator('.pin').click();
    await expect(sidebar).toBeHidden();
    expect(await stored(page, OPEN_KEY)).toBe('false');
    expect(await page.evaluate(() => parseInt(getComputedStyle(document.body).paddingLeft, 10))).toBe(0);
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
