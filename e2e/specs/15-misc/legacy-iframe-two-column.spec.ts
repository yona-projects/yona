import { test, expect, Page } from '@playwright/test';
import { execFileSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/**
 * P3-75 Step 0 characterization: the two-column mode of the PR list and the user profile page is
 * still the legacy iframe/page-slide implementation (yona.twoColumnMode.js + <yona-page-slide>).
 * These specs pin what it does TODAY (real routes, no mocks) so that the Turbo Frames conversion
 * can tell "already like this" from "regressed by the conversion". They are expected to be
 * rewritten/removed together with the conversion of each screen.
 */
const BASE_URL = process.env.YONA_BASE_URL ?? 'http://localhost:8080';

test.describe.serial('legacy iframe two-column: PR list and user profile', () => {
  let owner: string;
  let project: string;
  let base: string;
  const issues: { number: string; title: string }[] = [];
  const pulls: { number: string; title: string }[] = [];

  async function enableTwoColumn(page: Page, url: string) {
    await page.goto(url);
    await page.evaluate(() => localStorage.setItem('useTwoColumnMode', 'true'));
    await page.reload();
    await expect(page.locator('#two-column-mode')).toBeChecked();
  }

  const panelFrame = (page: Page) => page.frameLocator('yona-page-slide iframe');

  test('seed a public git project with two issues and two pull requests', async ({ page }) => {
    owner = requireSeed('adminLoginId');
    const password = requireSeed('adminPassword');
    project = `iframe-${uniqueSuffix()}`;
    base = `/${owner}/${project}`;

    await page.goto('/projectform');
    await page.selectOption('#project-owner', owner);
    await page.fill('#project-name', project);
    await page.fill('#description', 'Legacy iframe two-column characterization');
    await page.check('#public');
    await page.selectOption('#vcs', 'GIT');
    await page.click('#newProjectForm button.ybtn-success');
    await expect(page).toHaveURL(new RegExp(`${base}$`));

    for (const letter of ['A', 'B']) {
      const title = `Iframe issue ${letter}`;
      await page.goto(`${base}/issueform`);
      await page.fill('#title', title);
      await page.locator('textarea[data-editor-mode="content-body"]').evaluate((el: HTMLTextAreaElement) => {
        el.value = 'Issue body';
        el.dispatchEvent(new Event('input', { bubbles: true }));
      });
      await page.click('#button-save');
      await expect(page).toHaveURL(/\/issue\/\d+$/);
      issues.push({ title, number: new URL(page.url()).pathname.split('/').pop()! });
    }

    // Two divergent branches need the real git CLI (same approach as 07-pull-request/00-...).
    const url = new URL(BASE_URL);
    const cloneUrl = `${url.protocol}//${encodeURIComponent(owner)}:${encodeURIComponent(password)}@${url.host}/git/${owner}/${project}`;
    const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'yona-e2e-iframe-'));
    const run = (args: string[]) => execFileSync('git', args, { cwd: workDir, stdio: 'pipe' });
    try {
      run(['clone', cloneUrl, '.']);
      run(['config', 'user.email', 'admin@yona-e2e.test']);
      run(['config', 'user.name', 'E2E Admin']);
      fs.writeFileSync(path.join(workDir, 'README.md'), '# iframe characterization\n');
      run(['add', 'README.md']);
      run(['commit', '-m', 'initial']);
      run(['branch', '-M', 'main']);
      run(['push', 'origin', 'main']);
      for (const letter of ['a', 'b']) {
        run(['checkout', '-q', 'main']);
        run(['checkout', '-q', '-b', `feature/${letter}`]);
        fs.writeFileSync(path.join(workDir, `${letter}.txt`), `feature ${letter}\n`);
        run(['add', `${letter}.txt`]);
        run(['commit', '-m', `feature ${letter}`]);
        run(['push', 'origin', `feature/${letter}`]);
      }
    } finally { fs.rmSync(workDir, { recursive: true, force: true }); }

    for (const letter of ['a', 'b']) {
      const title = `Iframe PR ${letter.toUpperCase()}`;
      await page.goto(`${base}/pull/new?fromBranch=${encodeURIComponent(`feature/${letter}`)}&toBranch=main`);
      await page.fill('#title', title);
      await page.locator('textarea[data-editor-mode="content-body"]').evaluate((el: HTMLTextAreaElement) => {
        el.value = 'PR body';
        el.dispatchEvent(new Event('input', { bubbles: true }));
      });
      await page.click('#button-save');
      await page.waitForURL(new RegExp(`${base}/pulls$`), { timeout: 15_000 });
      const href = await page.locator('a[href*="/pull/"]', { hasText: title }).first().getAttribute('href');
      pulls.push({ title, number: href!.match(/\/pull\/(\d+)/)![1] });
    }
  });

  test('PR list: a row opens the page-slide iframe with the PR page, marks the row and pushes its URL', async ({ page }) => {
    const [a, b] = pulls;
    await enableTwoColumn(page, `${base}/pulls`);
    const link = (n: string) => page.locator(`#list a.title[href$="/pull/${n}"]`).first();

    await link(a.number).click();
    await expect(page.locator('yona-page-slide iframe')).toHaveCount(1);
    await expect(page.locator('yona-page-slide iframe')).toHaveAttribute('src', new RegExp(`${base}/pull/${a.number}$`));
    await expect(panelFrame(page).locator('body')).toContainText(a.title);
    await expect(page).toHaveURL(new RegExp(`${base}/pull/${a.number}$`));
    await expect(link(a.number).locator('xpath=ancestor::li[contains(@class,"post-item")]')).toHaveClass(/highlightBg/);

    await link(b.number).click();
    await expect(page.locator('yona-page-slide iframe')).toHaveAttribute('src', new RegExp(`${base}/pull/${b.number}$`));
    await expect(panelFrame(page).locator('body')).toContainText(b.title);
    await expect(page).toHaveURL(new RegExp(`${base}/pull/${b.number}$`));

    // Clicking the same row again toggles the panel closed.
    await link(b.number).click();
    await expect(page.locator('yona-page-slide')).toBeHidden();
  });

  test('PR list: the PR page inside the iframe keeps its overview controls working', async ({ page }) => {
    const [a] = pulls;
    await enableTwoColumn(page, `${base}/pulls`);
    await page.locator(`#list a.title[href$="/pull/${a.number}"]`).first().click();
    const frame = panelFrame(page);
    await expect(frame.locator('body')).toContainText(a.title);
    await expect(frame.locator('#state')).toBeVisible();
    await expect(frame.locator('#watch-button')).toBeVisible();
    // The page inside the iframe is a full document: it has its own layout and its own scripts.
    expect(await page.locator('yona-page-slide iframe').evaluate((el: HTMLIFrameElement) => !!el.contentWindow)).toBe(true);
  });

  test('PR list: with the preference off a title click is a normal full-page navigation', async ({ page }) => {
    const [a] = pulls;
    await page.goto(`${base}/pulls`);
    await page.evaluate(() => localStorage.setItem('useTwoColumnMode', 'false'));
    await page.reload();
    await expect(page.locator('#two-column-mode')).not.toBeChecked();
    await page.locator(`#list a.title[href$="/pull/${a.number}"]`).first().click();
    await expect(page).toHaveURL(new RegExp(`${base}/pull/${a.number}$`));
    await expect(page.locator('yona-page-slide iframe')).toHaveCount(0);
    await expect(page.locator('.board-header, .pullreq-info, #state').first()).toBeVisible();
  });

  test('PR list: reload of the pushed URL is the standalone PR page, not the two-column list', async ({ page }) => {
    const [a] = pulls;
    await enableTwoColumn(page, `${base}/pulls`);
    await page.locator(`#list a.title[href$="/pull/${a.number}"]`).first().click();
    await expect(page).toHaveURL(new RegExp(`${base}/pull/${a.number}$`));
    await page.reload();
    await expect(page.locator('#list')).toHaveCount(0);
    await expect(page.locator('body')).toContainText(a.title);
  });

  test('user profile: issue and pull request rows open in the iframe panel', async ({ page }) => {
    const [issue] = issues;
    const [pr] = pulls;
    await enableTwoColumn(page, `/user/${owner}`);

    const issueLink = page.locator(`#issues a.title[href$="${base}/issue/${issue.number}"]`).first();
    await expect(issueLink).toBeVisible();
    await issueLink.click();
    await expect(page.locator('yona-page-slide iframe')).toHaveAttribute('src', new RegExp(`${base}/issue/${issue.number}$`));
    await expect(panelFrame(page).locator('body')).toContainText(issue.title);

    await page.locator('a[href="#pullRequests"]').click();
    const prLink = page.locator(`#pullRequests a.title[href$="${base}/pull/${pr.number}"]`).first();
    await expect(prLink).toBeVisible();
    await prLink.click();
    await expect(page.locator('yona-page-slide iframe')).toHaveAttribute('src', new RegExp(`${base}/pull/${pr.number}$`));
    await expect(panelFrame(page).locator('body')).toContainText(pr.title);
  });

  test('user profile: `selected` is the TAB name here and survives alongside two-column mode', async ({ page }) => {
    await enableTwoColumn(page, `/user/${owner}?selected=pullRequests`);
    await expect(page.locator('li.active a[href="#pullRequests"]')).toBeVisible();
    await expect(page.locator('#pullRequests')).toBeVisible();
    await page.locator('a[href="#issues"]').click();
    await expect(page.locator('#issues')).toBeVisible();
  });
});
