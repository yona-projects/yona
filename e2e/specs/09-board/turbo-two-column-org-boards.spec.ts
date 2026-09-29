import { test, expect, Page } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { twoColumnReady } from '../../support/two-column';
import { uniqueSuffix } from '../../support/unique';

// P3-75 Step 6: 조직 게시판(/org/{조직}/boards) 2단 보기. 조직 아래 여러 프로젝트의 게시글이 한 목록에 있어
// 선택 상태는 ?detail=post:<owner>/<project>/<번호> 복합 키로 표현한다. 실제 라우트만 사용한다(모킹 없음).
test.describe.serial('Turbo two-column: organization boards', () => {
  const tag = `orgturbo${uniqueSuffix()}`;
  const orgName = `orgturbo-${uniqueSuffix()}`;
  const projects: string[] = [];
  const posts: { project: string; number: string; title: string }[] = [];
  const key = (post: { project: string; number: string }) => `post:${orgName}/${post.project}/${post.number}`;
  const listUrl = () => `/org/${orgName}/boards?filter=${tag}&orderBy=createdDate&orderDir=asc`;
  const link = (page: Page, post: { project: string; number: string }) =>
    page.locator(`#post-list a.title[data-detail-url$="/${orgName}/${post.project}/post/${post.number}"]`).first();

  test('seed an organization with two projects, one post each', async ({ page }) => {
    await page.goto('/organizations/new');
    await page.fill('#name', orgName);
    await page.fill('#descr', 'Organization boards two-column');
    await page.click('form[action="/organizations/new"] button.ybtn-success');
    await expect(page).toHaveURL(new RegExp(`/organizations/${orgName}$`));

    for (const letter of ['a', 'b']) {
      const project = `${orgName}-${letter}`;
      projects.push(project);
      await page.goto('/projectform');
      await page.selectOption('#project-owner', orgName);
      await page.fill('#project-name', project);
      await page.fill('#description', 'Org project');
      await page.check('#public');
      await page.selectOption('#vcs', 'GIT');
      await page.click('#newProjectForm button.ybtn-success');
      await expect(page).toHaveURL(new RegExp(`/${orgName}/${project}$`));

      const title = `${tag} post ${letter.toUpperCase()}`;
      await page.goto(`/${orgName}/${project}/postform`);
      await page.fill('#title', title);
      await page.locator('textarea[data-editor-mode="content-body"]').evaluate((el: HTMLTextAreaElement, text: string) => {
        el.value = text;
        el.dispatchEvent(new Event('input', { bubbles: true }));
      }, `Body ${letter}`);
      await page.click('#post-form button[type=submit]');
      await expect(page).toHaveURL(/\/post\/\d+$/);
      posts.push({ project, title, number: new URL(page.url()).pathname.split('/').pop()! });
    }
  });

  test('cross-project A/B navigation, Back/Forward, reload and no-JS direct URL', async ({ page, browser }) => {
    const [a, b] = posts;
    await page.goto(listUrl());
    await twoColumnReady(page);
    await page.evaluate(() => localStorage.setItem('useTwoColumnMode', 'true'));
    await page.reload();
    await twoColumnReady(page);
    await expect(page.locator('#two-column-mode')).toBeChecked();
    const list = await page.locator('#post-list').elementHandle();
    const frameRequests: string[] = [];
    page.on('request', request => {
      if (request.headers()['turbo-frame'] === 'post-detail') frameRequests.push(request.url());
    });
    for (const post of [a, b]) {
      await link(page, post).click();
      await expect(page.locator('#post-detail .board-header')).toContainText(post.title);
      await expect(page.locator('#post-detail')).not.toHaveAttribute('aria-busy', 'true');
      const url = new URL(page.url());
      expect(url.pathname).toBe(`/org/${orgName}/boards`);
      expect(url.searchParams.get('detail')).toBe(key(post));
      expect(url.searchParams.get('filter')).toBe(tag);
      expect(await list!.evaluate(element => element.isConnected)).toBe(true);
      await expect(page.locator('iframe, yona-page-slide')).toHaveCount(0);
      await expect(link(page, post).locator('xpath=ancestor::li[contains(@class,"post-item")]')).toHaveClass(/highlightBg/);
    }
    // 클릭 1회당 상세 요청 1회(재마운트로 리스너가 중복 등록되면 늘어난다).
    expect(frameRequests).toHaveLength(2);

    await page.goBack();
    await expect(page.locator('#post-detail .board-header')).toContainText(a.title);
    await page.goForward();
    await expect(page.locator('#post-detail .board-header')).toContainText(b.title);

    const selectedUrl = page.url();
    await page.reload();
    await twoColumnReady(page);
    await expect(page.locator('#post-list')).toContainText(a.title);
    await expect(page.locator('#post-detail .board-header')).toContainText(b.title);

    // 같은 행을 다시 누르면 선택이 해제되고 detail 파라미터가 사라진다.
    await link(page, b).click();
    await expect(page.locator('#post-detail')).toBeHidden();
    expect(new URL(page.url()).searchParams.has('detail')).toBe(false);
    expect(new URL(page.url()).searchParams.get('filter')).toBe(tag);

    const fallback = await browser.newContext({ storageState: './.auth/admin.json', javaScriptEnabled: false });
    try {
      const tab = await fallback.newPage();
      const response = await tab.goto(selectedUrl);
      expect(response?.status()).toBe(200);
      await expect(tab.locator('#post-list')).toContainText(a.title);
      await expect(tab.locator('#post-detail')).toContainText(b.title);
    } finally { await fallback.close(); }
  });

  test('search form keeps the selected detail', async ({ page }) => {
    const [a] = posts;
    await page.goto(`${listUrl()}&detail=${encodeURIComponent(key(a))}`);
    await twoColumnReady(page);
    await expect(page.locator('#post-detail .board-header')).toContainText(a.title);
    await expect(page.locator('#option_form input[name="detail"]')).toHaveValue(key(a));
    await page.locator('#option_form input[name="filter"]').fill(`${tag} post`);
    await Promise.all([page.waitForNavigation(), page.locator('#option_form .search-btn').click()]);
    await twoColumnReady(page);
    expect(new URL(page.url()).searchParams.get('detail')).toBe(key(a));
    await expect(page.locator('#post-detail .board-header')).toContainText(a.title);
  });

  test('keys outside the organization, malformed keys and missing posts never render a detail', async ({ page }) => {
    const [a] = posts;
    const bad = ['garbage', `post:${orgName}/${a.project}/999999`, `post:nobody-${tag}/none/1`, `issue:${orgName}/${a.project}/${a.number}`,
      `post:${requireSeed('adminLoginId')}/${a.project}/${a.number}`];
    for (const detail of bad) {
      const response = await page.goto(`${listUrl()}&detail=${encodeURIComponent(detail)}`);
      expect(response?.status()).toBeLessThan(500);
      await expect(page.locator('#post-detail .board-header')).toHaveCount(0);
    }
  });
});
