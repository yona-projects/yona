import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

// P3-75: board two-column mode on real Turbo Frames (post-list / post-detail). Real routes only.
test.describe.serial('Turbo board two-column semantics', () => {
  let base: string;
  const posts: { number: string; title: string }[] = [];

  test('create isolated posts through regular forms', async ({ page }) => {
    const owner = requireSeed('adminLoginId');
    const project = `bturbo-${uniqueSuffix()}`;
    base = `/${owner}/${project}`;
    await page.goto('/projectform');
    await page.selectOption('#project-owner', owner);
    await page.fill('#project-name', project);
    await page.fill('#description', 'Board Turbo semantic parity acceptance');
    await page.check('#public');
    await page.selectOption('#vcs', 'GIT');
    await page.click('#newProjectForm button.ybtn-success');
    await expect(page).toHaveURL(new RegExp(`${base}$`));
    for (const letter of ['A', 'B', 'C']) {
      const title = `Turbo post ${letter}`;
      await page.goto(`${base}/postform`);
      await page.fill('#title', title);
      await page.locator('textarea[data-editor-mode="content-body"]').evaluate((el: HTMLTextAreaElement) => {
        el.value = 'Post body with **Markdown**\n\n- [ ] Persistent task';
        el.dispatchEvent(new Event('input', { bubbles: true }));
      });
      await page.click('#post-form button[type=submit]');
      await expect(page).toHaveURL(/\/post\/\d+$/);
      posts.push({ title, number: new URL(page.url()).pathname.split('/').pop()! });
    }
  });

  test('detail-only A/B navigation, browser history, reload and no-JS direct URL', async ({ page, browser }) => {
    const [a, b] = posts;
    await page.goto(`${base}/posts?filter=Turbo&orderBy=createdDate&orderDir=asc`);
    await page.evaluate(() => localStorage.setItem('useTwoColumnMode', 'true'));
    await page.reload();
    await expect(page.locator('#two-column-mode')).toBeChecked();
    const list = await page.locator('#post-list').elementHandle();
    const requests: string[] = [];
    page.on('request', request => {
      if (request.headers()['turbo-frame'] === 'post-detail') requests.push(request.url());
    });
    for (const post of [a, b]) {
      await page.locator(`#post-list a.title[data-detail-url$="/post/${post.number}"]`).last().click();
      await expect(page.locator('#post-detail .board-header')).toContainText(post.title);
      await expect(page.locator('#post-detail')).not.toHaveAttribute('aria-busy', 'true');
      await expect(page).toHaveURL(new RegExp(`selected=${post.number}(?:&|$)`));
      expect(await list!.evaluate(element => element.isConnected)).toBe(true);
      await expect(page.locator('iframe, yona-page-slide')).toHaveCount(0);
      const url = new URL(page.url());
      expect(url.searchParams.get('filter')).toBe('Turbo');
      expect(url.searchParams.get('orderDir')).toBe('asc');
    }
    expect(requests).toHaveLength(2);
    await page.goBack();
    await expect(page.locator('#post-detail .board-header')).toContainText(a.title);
    await page.goForward();
    await expect(page.locator('#post-detail .board-header')).toContainText(b.title);
    const selectedUrl = page.url();
    await page.reload();
    await expect(page.locator('#post-detail .board-header')).toContainText(b.title);
    const fallback = await browser.newContext({ storageState: './.auth/admin.json', javaScriptEnabled: false });
    try {
      const tab = await fallback.newPage();
      const response = await tab.goto(selectedUrl);
      expect(response?.status()).toBe(200);
      await expect(tab.locator('#post-list')).toContainText(a.title);
      await expect(tab.locator('#post-detail')).toContainText(b.title);
    } finally { await fallback.close(); }
    const anonymous = await browser.newContext({ storageState: { cookies: [], origins: [] } });
    try {
      const tab = await anonymous.newPage();
      const errors: string[] = [];
      tab.on('pageerror', error => errors.push(error.message));
      await tab.goto(selectedUrl);
      await expect(tab.locator('#post-detail .board-header')).toContainText(b.title);
      await expect(tab.locator('#post-detail #comment-form')).toHaveCount(0);
      await tab.locator(`#post-list a.title[data-detail-url$="/post/${a.number}"]`).last().click();
      await expect(tab.locator('#post-detail .board-header')).toContainText(a.title);
      expect(errors).toEqual([]);
    } finally { await anonymous.close(); }
  });

  test('canonical draft keys survive A/B/Back and fast switches without cross-post restore', async ({ page }) => {
    const [a, b] = posts;
    await page.goto(`${base}/posts?selected=${a.number}`);
    await page.evaluate(({ key }) => localStorage.setItem(key, 'Existing draft A'), { key: `${base}/post/${a.number}` });
    await page.reload();
    const editor = page.locator('#post-detail #comment-form textarea[data-editor-mode="comment-body"]');
    const visibleEditor = page.locator('#post-detail #comment-form .cm-content');
    await expect(editor).toHaveValue('Existing draft A');
    await expect(visibleEditor).toHaveText('Existing draft A');
    await visibleEditor.fill('Fast unsent draft A');
    await page.locator(`#post-list a.title[data-detail-url$="/post/${b.number}"]`).last().click();
    await expect(page.locator('#post-detail .board-header')).toContainText(b.title);
    await expect(page.locator('#post-detail')).not.toHaveAttribute('aria-busy', 'true');
    await expect(editor).toHaveValue('');
    await visibleEditor.fill('Separate unsent draft B');
    await page.goBack();
    await expect(page.locator('#post-detail .board-header')).toContainText(a.title);
    await expect(editor).toHaveValue('Fast unsent draft A');
    await page.goForward();
    await expect(page.locator('#post-detail .board-header')).toContainText(b.title);
    await expect(editor).toHaveValue('Separate unsent draft B');
    expect(await page.evaluate(key => localStorage.getItem(key), `${base}/posts`)).toBeNull();
    expect(await page.evaluate(key => localStorage.getItem(key), `${base}/post/${a.number}`)).toBe('Fast unsent draft A');
  });

  test('selected detail retains dialogs, watch, uploads, comment submission and tasks after repeated mounts', async ({ page }) => {
    const [a, b] = posts;
    await page.goto(`${base}/posts?selected=${a.number}`);
    for (const post of [b, a, b]) {
      await page.locator(`#post-list a.title[data-detail-url$="/post/${post.number}"]`).last().click();
      await expect(page.locator('#post-detail .board-header')).toContainText(post.title);
      await expect(page.locator('#post-detail')).not.toHaveAttribute('aria-busy', 'true');
    }
    const watch = page.locator('#post-detail #watch-button');
    const watching = await watch.getAttribute('data-watching');
    await watch.click();
    await expect(watch).not.toHaveAttribute('data-watching', watching!);
    await watch.click();
    await expect(watch).toHaveAttribute('data-watching', watching!);
    const upload = page.locator('#post-detail yona-attachments#upload');
    await upload.locator('input[type="file"]').setInputFiles({
      name: 'turbo-board-attachment.txt', mimeType: 'text/plain', buffer: Buffer.from('Frame attachment'),
    });
    const uploaded = upload.locator('.attached-file.complete', { hasText: 'turbo-board-attachment.txt' });
    await expect(uploaded).toBeVisible();
    await uploaded.locator('.btn-delete').click();
    await expect(uploaded).toHaveCount(0);
    await page.locator('#post-detail a[href="#deleteConfirm"]').click();
    await expect(page.locator('#post-detail #deleteConfirm')).toBeVisible();
    await page.locator('#post-detail #deleteConfirm button[data-dismiss="modal"]').last().click();
    await expect(page.locator('#post-detail #deleteConfirm')).toBeHidden();
    const editor = page.locator('#post-detail #comment-form textarea[data-editor-mode="comment-body"]');
    const posted: string[] = [];
    page.on('request', request => { if (request.method() === 'POST' && request.url().endsWith('/comments')) posted.push(request.url()); });
    await page.locator('#post-detail #comment-form button[type="submit"]').click();
    expect(posted).toHaveLength(0);
    const visibleEditor = page.locator('#post-detail #comment-form .cm-content');
    await visibleEditor.fill('Comment submitted from the real Turbo board detail');
    await visibleEditor.press('Control+Shift+Enter');
    await expect(page.locator('#post-detail #comments')).toContainText('Comment submitted from the real Turbo board detail');
    expect(posted).toHaveLength(1);
    await expect(editor).toHaveValue('');
    expect(await page.evaluate(key => localStorage.getItem(key), `${base}/post/${b.number}`)).toBeNull();
    const task = page.locator('#post-detail .markdown-wrap input[type="checkbox"]').first();
    const [taskResponse] = await Promise.all([
      page.waitForResponse(response => response.request().method() === 'PATCH' && response.url().endsWith('/content')),
      task.check(),
    ]);
    expect(taskResponse.ok()).toBe(true);
    await page.reload();
    await expect(page.locator('#post-detail .markdown-wrap input[type="checkbox"]').first()).toBeChecked();
  });

  test('filters, same-post close, preference off and mobile remain usable', async ({ page }) => {
    const [a, b] = posts;
    await page.goto(`${base}/posts?selected=${a.number}`);
    await page.locator(`#post-list a.title[data-detail-url$="/post/${b.number}"]`).last().click();
    await expect(page.locator('#post-detail .board-header')).toContainText(b.title);
    await expect(page.locator('#post-detail')).not.toHaveAttribute('aria-busy', 'true');
    await page.locator('#option_form input[name="filter"]').fill('Turbo post B');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'load' }),
      page.locator('#option_form .search-btn').click(),
    ]);
    await expect(page).toHaveURL(/filter=Turbo/);
    await expect(page.locator('#post-detail .board-header')).toContainText(b.title);
    await page.locator(`#post-list a.title[data-detail-url$="/post/${b.number}"]`).last().click();
    await expect(page.locator('#post-detail')).toBeHidden();
    await expect(page).not.toHaveURL(/[?&]selected=/);
    await page.locator('#two-column-mode').uncheck();
    await page.reload();
    await expect(page.locator('#two-column-mode')).not.toBeChecked();
    await page.locator(`#post-list a.title[data-detail-url$="/post/${b.number}"]`).last().click();
    await expect(page).toHaveURL(new RegExp(`${base}/post/${b.number}$`));
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`${base}/posts?selected=${a.number}`);
    await expect(page.locator('#post-detail .board-header')).toContainText(a.title);
    await page.locator(`#post-list a.title[data-detail-url$="/post/${b.number}"]`).last().click();
    await expect(page).toHaveURL(new RegExp(`${base}/post/${b.number}$`));
  });

  test('watch toggles on the standalone post page and issues one request per click', async ({ page }) => {
    const [, b] = posts;
    const requests: string[] = [];
    page.on('request', request => { if (/\/(un)?watch\?/.test(request.url())) requests.push(request.url()); });
    await page.goto(`${base}/post/${b.number}`);
    const watch = page.locator('#watch-button');
    const watching = await watch.getAttribute('data-watching');
    await watch.click();
    await expect(watch).not.toHaveAttribute('data-watching', watching!);
    await watch.click();
    await expect(watch).toHaveAttribute('data-watching', watching!);
    expect(requests).toHaveLength(2);
    expect(requests[0]).toContain('resource.type=BOARD_POST');
  });
});
