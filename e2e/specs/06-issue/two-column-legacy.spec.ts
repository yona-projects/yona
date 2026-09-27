import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

// Retained in specs for reproducible baseline evidence, but never run against Turbo by default.
// YONA_LEGACY_TWO_COLUMN=1 YONA_BASE_URL=http://localhost:18080 npx playwright test two-column-legacy.spec.ts
// Uses only the setup admin, not the shared project/issues, and never writes seed.json.
test('characterize legacy two-column navigation', async ({ page }, testInfo) => {
  test.skip(process.env.YONA_LEGACY_TWO_COLUMN !== '1', 'Opt-in legacy iframe characterization only');
  test.setTimeout(120_000);
  const owner = requireSeed('adminLoginId');
  const project = `e2e-two-column-${uniqueSuffix()}`;
  const listPath = `/${owner}/${project}/issues`;
  const observations: unknown[] = [];
  const errors: string[] = [];
  const selectionRequests: { url: string; resourceType: string }[] = [];
  let recordingSelection = false;
  page.on('request', request => {
    if (recordingSelection) selectionRequests.push({ url: request.url(), resourceType: request.resourceType() });
  });
  page.on('pageerror', error => errors.push(error.message));

  const observe = async (step: string) => expect(async () => {
    observations.push({
      step,
      ...await page.evaluate(() => ({
        url: location.href,
        historyLength: history.length,
        historyState: history.state,
        preference: localStorage.getItem('useTwoColumnMode'),
        checked: document.querySelector<HTMLInputElement>('#two-column-mode')?.checked ?? null,
        listPresent: !!document.querySelector('div[pjax-container]'),
        highlighted: Array.from(document.querySelectorAll('.post-item.highlightBg'))
          .map(row => row.textContent?.trim()),
      })),
      iframeVisible: await page.locator('#pageslide iframe, yona-page-slide iframe').isVisible(),
      frames: await Promise.all(page.frames().map(async frame => ({
        url: frame.url(),
        title: await frame.title(),
        text: (await frame.locator('body').innerText()).slice(0, 3000),
      }))),
    });
  }).toPass({ timeout: 5000 });

  try {
    // Use the same browser setup flows as project-create and issue-crud; no API contract guesses.
    await page.goto('/projectform');
    await page.selectOption('#project-owner', owner);
    await page.fill('#project-name', project);
    await page.fill('#description', 'Isolated legacy two-column characterization');
    await page.check('#public');
    await page.selectOption('#vcs', 'GIT');
    await page.click('#newProjectForm button.ybtn-success');
    await expect(page).toHaveURL(new RegExp(`/${owner}/${project}`));

    const issues: { title: string; path: string }[] = [];
    for (const label of ['A', 'B']) {
      const title = `Legacy issue ${label} ${project}`;
      await page.goto(`/${owner}/${project}/issueform`);
      await page.fill('#title', title);
      await page.locator('textarea[data-editor-mode="content-body"]').fill(`Legacy detail ${label}`, { force: true });
      await page.click('#button-save');
      await expect(page).toHaveURL(new RegExp(`/${owner}/${project}/issue/\\d+$`));
      issues.push({ title, path: new URL(page.url()).pathname });
    }
    const [a, b] = issues;
    await page.evaluate(() => localStorage.setItem('useTwoColumnMode', 'false'));
    await page.goto(listPath);
    await expect(page.locator('#two-column-mode')).not.toBeChecked();
    await observe('preference-off-list');

    await page.locator('#two-column-mode').check();
    await expect(page.locator('#two-column-mode')).toBeChecked();
    await observe('preference-on-list');

    recordingSelection = true;
    for (const issue of [a, b]) {
      await page.locator('.post-item a.title').filter({ hasText: issue.title }).click();
      await expect(page).toHaveURL(new URL(issue.path, page.url()).href);
      await expect(page.frameLocator('#pageslide iframe, yona-page-slide iframe').locator('body'))
        .toContainText(issue.title);
      await expect(page.locator('div[pjax-container]')).toBeVisible();
      await observe(issue === a ? 'iframe-A' : 'iframe-B');
    }
    recordingSelection = false;

    // The detail document also writes parent history; record browser results rather than
    // assuming the standalone twoColumnMode.js push/replace branch owns the final state.
    await page.goBack();
    await observe('back-after-B');
    await page.goForward();
    await observe('forward-after-B');
    await page.reload();
    await observe('reload-after-forward');
    await expect(page.locator('body')).toContainText(b.title);
    await expect(page.locator('#two-column-mode')).toHaveCount(0);
    await expect(page.locator('#pageslide iframe, yona-page-slide iframe')).toHaveCount(0);

    await page.goto(listPath);
    await expect(page.locator('#two-column-mode')).toBeChecked();
    await observe('preference-on-persists-after-reload');
    await page.locator('#two-column-mode').uncheck();
    await page.reload();
    await expect(page.locator('#two-column-mode')).not.toBeChecked();
    await observe('preference-off-persists-after-reload');
    await page.locator('.post-item a.title').filter({ hasText: a.title }).click();
    await expect(page).toHaveURL(new URL(a.path, page.url()).href);
    await expect(page.locator('body')).toContainText(a.title);
    await expect(page.locator('#two-column-mode')).toHaveCount(0);
    await expect(page.locator('#pageslide iframe, yona-page-slide iframe')).toHaveCount(0);
    await observe('preference-off-full-page-A');
  } finally {
    const evidence = JSON.stringify({ project, observations, selectionRequests, errors }, null, 2);
    console.log(`LEGACY_TWO_COLUMN_OBSERVATIONS\n${evidence}`);
    await testInfo.attach('legacy-two-column-observations.json', {
      body: evidence,
      contentType: 'application/json',
    });
  }
});
