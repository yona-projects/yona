import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

// Real application routes only: no response rewriting, intercepted HTML, or mocked detail data.
test.describe.serial('Turbo issue two-column semantics', () => {
  let base: string;
  const issues: { number: string; title: string }[] = [];

  test('create isolated issues through regular CSRF-protected forms', async ({ page }) => {
    const owner = requireSeed('adminLoginId');
    const project = `turbo-${uniqueSuffix()}`;
    base = `/${owner}/${project}`;
    await page.goto('/projectform');
    await page.selectOption('#project-owner', owner);
    await page.fill('#project-name', project);
    await page.fill('#description', 'Turbo semantic parity acceptance');
    await page.check('#public');
    await page.selectOption('#vcs', 'GIT');
    await page.click('#newProjectForm button.ybtn-success');
    await expect(page).toHaveURL(new RegExp(`${base}$`));
    for (const letter of ['A', 'B', 'C']) {
      const title = `Turbo issue ${letter}`;
      await page.goto(`${base}/issueform`);
      await page.fill('#title', title);
      await page.locator('textarea[data-editor-mode="content-body"]').evaluate((el: HTMLTextAreaElement) => {
        el.value = 'Issue body with **Markdown**\n\n- [ ] Persistent task';
        el.dispatchEvent(new Event('input', { bubbles: true }));
      });
      await page.click('#button-save');
      await expect(page).toHaveURL(/\/issue\/\d+$/);
      issues.push({ title, number: new URL(page.url()).pathname.split('/').pop()! });
    }
  });

  test('detail-only A/B navigation, browser history, reload and no-JS direct URL', async ({ page, browser }, testInfo) => {
    const [a, b] = issues;
    await page.goto(`${base}/issues?state=open&filter=Turbo&itemsPerPage=15&orderBy=createdDate&orderDir=asc`);
    await page.evaluate(() => localStorage.setItem('useTwoColumnMode', 'true'));
    await page.reload();
    await expect(page.locator('#two-column-mode')).toBeChecked();
    const list = await page.locator('#issue-list').elementHandle();
    const requests: string[] = [];
    const measurements: { url: string; bytes: number; contentType: string | undefined }[] = [];
    page.on('request', request => {
      if (request.headers()['turbo-frame'] === 'issue-detail') requests.push(request.url());
    });
    for (const issue of [a, b]) {
      const [response] = await Promise.all([
        page.waitForResponse(response => response.request().headers()['turbo-frame'] === 'issue-detail'),
        page.locator(`#issue-list a.title[data-detail-url$="/issue/${issue.number}"]`).last().click(),
      ]);
      measurements.push({ url: response.url(), bytes: (await response.body()).length, contentType: response.headers()['content-type'] });
      await expect(page.locator('#issue-detail .board-header')).toContainText(issue.title);
      await expect(page.locator('#issue-detail')).not.toHaveAttribute('aria-busy', 'true');
      await expect(page).toHaveURL(new RegExp(`selected=${issue.number}(?:&|$)`));
      expect(await list!.evaluate(element => element.isConnected)).toBe(true);
      await expect(page.locator('iframe, yona-page-slide')).toHaveCount(0);
      const url = new URL(page.url());
      expect(url.searchParams.get('filter')).toBe('Turbo');
      expect(url.searchParams.get('orderDir')).toBe('asc');
    }
    expect(requests).toHaveLength(2);
    await page.goBack();
    await expect(page.locator('#issue-detail .board-header')).toContainText(a.title);
    await page.goForward();
    await expect(page.locator('#issue-detail .board-header')).toContainText(b.title);
    const selectedUrl = page.url();
    await page.reload();
    await expect(page.locator('#issue-detail .board-header')).toContainText(b.title);
    await page.goto(selectedUrl);
    await expect(page.locator('#issue-detail .board-header')).toContainText(b.title);
    const fallback = await browser.newContext({ storageState: './.auth/admin.json', javaScriptEnabled: false });
    try {
      const tab = await fallback.newPage();
      const response = await tab.goto(selectedUrl);
      expect(response?.status()).toBe(200);
      await expect(tab.locator('#issue-list')).toContainText(a.title);
      await expect(tab.locator('#issue-detail')).toContainText(b.title);
    } finally { await fallback.close(); }
    const anonymous = await browser.newContext({ storageState: { cookies: [], origins: [] } });
    try {
      const tab = await anonymous.newPage();
      const errors: string[] = [];
      tab.on('pageerror', error => errors.push(error.message));
      await tab.goto(selectedUrl);
      await expect(tab.locator('#issue-detail .board-header')).toContainText(b.title);
      await expect(tab.locator('#issue-detail #comment-form')).toHaveCount(0);
      await tab.locator(`#issue-list a.title[data-detail-url$="/issue/${a.number}"]`).last().click();
      await expect(tab.locator('#issue-detail .board-header')).toContainText(a.title);
      await expect(tab.locator('#issue-detail')).not.toHaveAttribute('aria-busy', 'true');
      expect(errors).toEqual([]);
    } finally { await anonymous.close(); }
    expect(measurements.every(row => row.contentType?.includes('text/html'))).toBe(true);
    console.log('TURBO_SELECTION_MEASUREMENTS', JSON.stringify(measurements));
    await testInfo.attach('selection-network.json', { body: JSON.stringify(measurements, null, 2), contentType: 'application/json' });
    await testInfo.attach('two-column.png', { body: await page.screenshot({ fullPage: true }), contentType: 'image/png' });
  });

  test('canonical legacy draft keys survive A/B/Back and fast switches without cross-issue restore', async ({ page }) => {
    const [a, b] = issues;
    await page.goto(`${base}/issues?selected=${a.number}`);
    await page.evaluate(({ key }) => localStorage.setItem(key, 'Existing 1.x draft A'), { key: `${base}/issue/${a.number}` });
    await page.reload();
    const editor = page.locator('#issue-detail #comment-form textarea[data-editor-mode="comment-body"]');
    const visibleEditor = page.locator('#issue-detail #comment-form .cm-content');
    await expect(editor).toHaveValue('Existing 1.x draft A');
    await expect(visibleEditor).toHaveText('Existing 1.x draft A');
    await visibleEditor.fill('Fast unsent draft A');
    await page.locator(`#issue-list a.title[data-detail-url$="/issue/${b.number}"]`).last().click();
    await expect(page.locator('#issue-detail .board-header')).toContainText(b.title);
    await expect(page.locator('#issue-detail')).not.toHaveAttribute('aria-busy', 'true');
    await expect(editor).toHaveValue('');
    await expect(visibleEditor).toHaveText('');
    await visibleEditor.fill('Separate unsent draft B');
    await page.goBack();
    await expect(page.locator('#issue-detail .board-header')).toContainText(a.title);
    await expect(editor).toHaveValue('Fast unsent draft A');
    await expect(visibleEditor).toHaveText('Fast unsent draft A');
    await page.goForward();
    await expect(page.locator('#issue-detail .board-header')).toContainText(b.title);
    await expect(editor).toHaveValue('Separate unsent draft B');
    await expect(visibleEditor).toHaveText('Separate unsent draft B');
    await page.reload();
    await expect(editor).toHaveValue('Separate unsent draft B');
    expect(await page.evaluate(key => localStorage.getItem(key), `${base}/issues`)).toBeNull();
    await page.locator('#issue-detail .editor-clear-temporary-button button').click();
    await expect(editor).toHaveValue('');
    await page.reload();
    await expect(editor).toHaveValue('');
    await expect(visibleEditor).toHaveText('');
    expect(await page.evaluate(key => localStorage.getItem(key), `${base}/issue/${a.number}`)).toBe('Fast unsent draft A');
  });

  test('selected detail retains dialogs, comment submission and task persistence after repeated mounts', async ({ page }) => {
    const [a, b] = issues;
    await page.goto(`${base}/issues?selected=${a.number}`);
    for (const issue of [b, a, b]) {
      await page.locator(`#issue-list a.title[data-detail-url$="/issue/${issue.number}"]`).last().click();
      await expect(page.locator('#issue-detail .board-header')).toContainText(issue.title);
      await expect(page.locator('#issue-detail')).not.toHaveAttribute('aria-busy', 'true');
    }
    const stalePolls: string[] = [];
    page.on('request', request => {
      if (request.url().endsWith(`/issues/${a.number}/detectChange`)) stalePolls.push(request.url());
    });
    const poll = await page.waitForResponse(response => response.url().endsWith(`/issues/${b.number}/detectChange`));
    expect(poll.ok()).toBe(true);
    expect(stalePolls).toEqual([]);
    await page.locator('#sidebar-open-btn').click();
    await expect(page.locator('#mySidenav')).toBeVisible();
    const favorite = page.locator('#issue-detail .favorite-issue');
    await favorite.click();
    await expect(favorite.locator('i')).toHaveClass(/starred/);
    await expect(page.locator('#mySidenav')).toBeVisible();
    await favorite.click();
    await expect(favorite.locator('i')).not.toHaveClass(/starred/);
    await page.locator('#issue-detail .board-id').click();
    await expect(page.locator('#mySidenav')).toBeHidden();
    const watch = page.locator('#issue-detail #watch-button');
    const watching = await watch.getAttribute('data-watching');
    await watch.click();
    await expect(watch).not.toHaveAttribute('data-watching', watching!);
    await watch.click();
    await expect(watch).toHaveAttribute('data-watching', watching!);
    const upload = page.locator('#issue-detail yona-attachments#upload');
    await upload.locator('input[type="file"]').setInputFiles({
      name: 'turbo-comment-attachment.txt', mimeType: 'text/plain', buffer: Buffer.from('Frame attachment'),
    });
    const uploaded = upload.locator('.attached-file.complete', { hasText: 'turbo-comment-attachment.txt' });
    await expect(uploaded).toBeVisible();
    await uploaded.locator('.btn-delete').click();
    await expect(uploaded).toHaveCount(0);
    await page.locator('#issue-detail a[href="#deleteConfirm"]').click();
    await expect(page.locator('#issue-detail #deleteConfirm')).toBeVisible();
    await page.locator('#issue-detail #deleteConfirm button[data-dismiss="modal"]').last().click();
    const editor = page.locator('#issue-detail #comment-form textarea[data-editor-mode="comment-body"]');
    const posts: string[] = [];
    page.on('request', request => { if (request.method() === 'POST' && request.url().endsWith('/comments')) posts.push(request.url()); });
    await page.locator('#issue-detail #comment-form button[type="submit"]').click();
    const visibleEditor = page.locator('#issue-detail #comment-form .cm-content');
    await visibleEditor.fill('Comment submitted from the real Turbo detail');
    await visibleEditor.press('Control+Shift+Enter');
    await expect(page.locator('#issue-detail #comments')).toContainText('Comment submitted from the real Turbo detail');
    expect(posts).toHaveLength(1);
    const rejected = await page.request.post(`${base}/issues`, { form: { title: 'Missing CSRF must fail' } });
    expect(rejected.status()).toBe(403);
    await expect(editor).toHaveValue('');
    expect(await page.evaluate(key => localStorage.getItem(key), `${base}/issue/${b.number}`)).toBeNull();
    const task = page.locator('#issue-detail .markdown-wrap input[type="checkbox"]').first();
    const [taskResponse] = await Promise.all([
      page.waitForResponse(response => response.request().method() === 'PATCH' && response.url().endsWith('/content')),
      task.check(),
    ]);
    expect(taskResponse.ok()).toBe(true);
    await expect(task).toBeChecked();
    await page.reload();
    await expect(page.locator('#issue-detail .markdown-wrap input[type="checkbox"]').first()).toBeChecked();
  });

  test('filters, pagination, same-issue close, preference off and mobile remain usable', async ({ page }) => {
    const [a, b] = issues;
    await page.goto(`${base}/issues?selected=${a.number}&itemsPerPage=2&orderDir=asc`);
    await page.locator(`#issue-list a.title[data-detail-url$="/issue/${b.number}"]`).last().click();
    await expect(page.locator('#issue-detail .board-header')).toContainText(b.title);
    await expect(page.locator('#issue-detail')).not.toHaveAttribute('aria-busy', 'true');
    await page.locator('#pagination').getByRole('link', { name: 'Next page' }).click();
    await expect(page).toHaveURL(/pageNum=2/);
    await expect(page.locator('#issue-detail .board-header')).toContainText(b.title);
    await page.locator('#search input[name="filter"]').fill('Turbo issue B');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'load' }),
      page.locator('#search [data-submit="submit"]').click(),
    ]);
    await expect(page).toHaveURL(/filter=Turbo/);
    await expect(page.locator('#issue-detail .board-header')).toContainText(b.title);
    await page.locator(`#issue-list a.title[data-detail-url$="/issue/${b.number}"]`).last().click();
    await expect(page.locator('#issue-detail')).toBeHidden();
    await expect(page).not.toHaveURL(/[?&]selected=/);
    await page.locator('#two-column-mode').uncheck();
    await page.reload();
    await expect(page.locator('#two-column-mode')).not.toBeChecked();
    await page.locator(`#issue-list a.title[data-detail-url$="/issue/${b.number}"]`).last().click();
    await expect(page).toHaveURL(new RegExp(`${base}/issue/${b.number}$`));
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`${base}/issues?selected=${a.number}`);
    await expect(page.locator('#issue-detail .board-header')).toContainText(a.title);
    await page.locator(`#issue-list a.title[data-detail-url$="/issue/${b.number}"]`).last().click();
    await expect(page).toHaveURL(new RegExp(`${base}/issue/${b.number}$`));
  });
});
