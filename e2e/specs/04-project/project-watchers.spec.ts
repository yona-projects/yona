import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: GET /{owner}/{projectName}/watchers (project/watchers.html). Watch/unwatch itself is
 * driven from the project header's .watchBtn link (project/header.html), intercepted by
 * yona.project.Global.js's _onClickBtnWatch: `fetch(href, {method:'post'})` then
 * `document.location.reload()` -- so a real click needs a reload wait, not a navigation wait. */

test('watchers screen loads (owner auto-watches their own project on creation)', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  const response = await page.goto(`/${owner}/${name}/watchers`);
  expect(response?.status()).toBeLessThan(400);
});

test('unwatch then watch again round-trips the admin watch state back to its original (watching) value', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}`);
  // Project creator auto-watches their own project, so the header starts in the "watching" state.
  const watcherCountBtn = page.locator('a.watcher-count');
  await expect(watcherCountBtn).toHaveClass(/watch-on/);
  const initialCount = Number((await watcherCountBtn.textContent())?.trim());

  // .watchBtn lives inside a Bootstrap dropdown-menu that's hidden until the .down-arrow toggle
  // (data-toggle="dropdown") opens it -- clicking .watchBtn directly while collapsed times out
  // because Playwright correctly refuses to click a non-visible element.
  await page.locator('.watch-btn button.down-arrow').click();
  await page.locator('a.watchBtn[href*="/unwatch"]').click();
  await page.waitForLoadState('networkidle');
  await expect(page.locator('a.watcher-count')).not.toHaveClass(/watch-on/);
  await page.locator('.watch-btn button.down-arrow').click();
  await expect(page.locator('a.watchBtn[href*="/watch"]').first()).toBeVisible();

  const watchersAfterUnwatch = await page.goto(`/${owner}/${name}/watchers`).then((r) => r!.text());
  const adminLoginId = requireSeed('adminLoginId');
  expect(watchersAfterUnwatch).not.toContain(`@${adminLoginId}`);

  await page.goto(`/${owner}/${name}`);
  await page.locator('.watch-btn button.down-arrow').click();
  await page.locator('a.watchBtn[href*="/watch"]').click();
  await page.waitForLoadState('networkidle');
  const restoredBtn = page.locator('a.watcher-count');
  await expect(restoredBtn).toHaveClass(/watch-on/);
  expect(Number((await restoredBtn.textContent())?.trim())).toBe(initialCount);

  await page.goto(`/${owner}/${name}/watchers`);
  await expect(page.locator('body')).toContainText(`@${adminLoginId}`);
});
