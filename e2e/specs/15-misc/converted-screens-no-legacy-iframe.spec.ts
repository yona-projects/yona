import { test, expect, Page } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { twoColumnReady } from '../../support/two-column';
import { uniqueSuffix } from '../../support/unique';

/**
 * P3-75: screens already converted to Turbo Frames must not run any of the legacy iframe/page-slide
 * machinery. yona.issue.List.js still contains two `_initTwoColumnMode()` calls (in `_init` behind
 * `!#issue-list`, and in the pjax reload callback); `yona.twoColumnMode.js` is no longer loaded on
 * these pages, so reaching either call would throw a ReferenceError (the pjax path would even hide
 * it behind a silent full-page fallback). This spec pins that neither is reachable and that the
 * filter links still work as native document navigations.
 */
test.describe.serial('converted screens: no legacy iframe machinery', () => {
  const owner = () => requireSeed('adminLoginId');
  const tag = `noifr${uniqueSuffix()}`;
  const org = `noifr-org-${uniqueSuffix()}`;
  const proj = `noifr-p-${uniqueSuffix()}`;
  const orgProj = `${org}-a`;

  async function newProject(page: Page, ownerName: string, name: string) {
    await page.goto('/projectform');
    await page.selectOption('#project-owner', ownerName);
    await page.fill('#project-name', name);
    await page.fill('#description', 'no legacy iframe');
    await page.check('#public');
    await page.selectOption('#vcs', 'GIT');
    await page.click('#newProjectForm button.ybtn-success');
    await expect(page).toHaveURL(new RegExp(`/${ownerName}/${name}$`));
  }
  async function newIssue(page: Page, base: string, title: string) {
    await page.goto(`${base}/issueform`);
    await page.fill('#title', title);
    await page.locator('textarea[data-editor-mode="content-body"]').evaluate((el: HTMLTextAreaElement) => {
      el.value = 'body'; el.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await page.click('#button-save');
    await expect(page).toHaveURL(/\/issue\/\d+$/);
  }
  async function newPost(page: Page, base: string, title: string) {
    await page.goto(`${base}/postform`);
    await page.fill('#title', title);
    await page.locator('textarea[data-editor-mode="content-body"]').evaluate((el: HTMLTextAreaElement) => {
      el.value = 'body'; el.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await page.click('#post-form button[type=submit]');
    await expect(page).toHaveURL(/\/post\/\d+$/);
  }

  test('seed a personal project and an organization project with an issue and a post each', async ({ page }) => {
    await page.goto('/organizations/new');
    await page.fill('#name', org);
    await page.fill('#descr', 'no legacy iframe');
    await page.click('form[action="/organizations/new"] button.ybtn-success');
    await expect(page).toHaveURL(new RegExp(`/organizations/${org}$`));
    await newProject(page, org, orgProj);
    await newIssue(page, `/${org}/${orgProj}`, `${tag} org issue`);
    await newPost(page, `/${org}/${orgProj}`, `${tag} org post`);
    await newProject(page, owner(), proj);
    await newIssue(page, `/${owner()}/${proj}`, `${tag} my issue`);
    await newPost(page, `/${owner()}/${proj}`, `${tag} my post`);
    // /user/issues shows only issues assigned to me when no filter is given.
    await page.goto(`/${owner()}/${proj}/issues`);
    await page.locator('li.post-item', { hasText: `${tag} my issue` }).locator('input[name="checked-issue"]').check();
    await page.click('#list-assignee button.dropdown-toggle');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'load' }),
      page.locator('#list-assignee ul.dropdown-menu > li').nth(1).locator('a').click(),
    ]);
  });

  const screens = () => [
    { name: 'issue list', url: `/${owner()}/${proj}/issues`, list: '#issue-list', detailFrame: '#issue-detail', filter: true },
    { name: 'board list', url: `/${owner()}/${proj}/posts`, list: '#post-list', detailFrame: '#post-detail', filter: false },
    { name: 'my issues', url: `/user/issues`, list: '#issue-list', detailFrame: '#issue-detail', filter: true },
    { name: 'org issues', url: `/org/${org}/issues`, list: '#issue-list', detailFrame: '#issue-detail', filter: true },
    { name: 'org boards', url: `/org/${org}/boards`, list: '#post-list', detailFrame: '#post-detail', filter: false },
  ];

  for (const idx of [0, 1, 2, 3, 4]) {
    test(`screen ${idx}: no page-slide/iframe, no legacy call, filters stay native navigation`, async ({ page }) => {
      const s = screens()[idx];
      const errors: string[] = [];
      page.on('pageerror', e => errors.push(e.message));
      page.on('console', m => { if (m.type() === 'error') errors.push(`console: ${m.text().slice(0, 160)}`); });

      await page.goto(s.url);
      await twoColumnReady(page);
      await page.evaluate(() => localStorage.setItem('useTwoColumnMode', 'true'));
      await page.reload();
      await twoColumnReady(page);
      await expect(page.locator('#two-column-mode')).toBeChecked();

      // The legacy script is not loaded, so its entry point must not exist and nothing may build a panel.
      expect(await page.evaluate(() => typeof (window as any)._initTwoColumnMode)).toBe('undefined');
      await expect(page.locator('script[src*="yona.twoColumnMode.js"]')).toHaveCount(0);
      await expect(page.locator('yona-page-slide, #pageslide, iframe')).toHaveCount(0);

      // Selecting a row goes through the Turbo detail frame, still without any panel/iframe.
      await page.locator(`${s.list} a.title[data-selection-url]`).first().click();
      await expect(page.locator(`${s.detailFrame} .board-header`)).toBeVisible();
      await expect(page.locator('yona-page-slide, #pageslide, iframe')).toHaveCount(0);

      // The pjax layer of yona.issue.List.js must stay disabled here: a filter click is a full-document
      // navigation (the marker on `window` does not survive), never the silent pjax fallback.
      if (s.filter) {
        await page.evaluate(() => { (window as any).__sameDocument = true; });
        const before = page.url();
        await Promise.all([
          page.waitForNavigation({ waitUntil: 'load' }),
          page.locator('a[pjax-filter]').first().click(),
        ]);
        await twoColumnReady(page);
        expect(await page.evaluate(() => (window as any).__sameDocument ?? false)).toBe(false);
        expect(page.url()).not.toBe(before);
      }
      expect(errors).toEqual([]);
    });
  }
});
