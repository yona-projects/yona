import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

// P3-75 Step 4: 내 이슈(/user/issues) 2단 보기. 여러 프로젝트에 걸친 목록이라 선택 상태는
// ?detail=issue:<owner>/<project>/<번호> 복합 키로 표현한다. 실제 라우트만 사용한다(모킹 없음).
test.describe.serial('Turbo two-column: my issues', () => {
  const tag = `myturbo${uniqueSuffix()}`;
  let owner: string;
  let project: string;
  const issues: { number: string; title: string }[] = [];
  const key = (number: string) => `issue:${owner}/${project}/${number}`;
  const listUrl = () => `/user/issues?filter=${tag}&orderBy=createdDate&orderDir=asc`;
  const link = (page: import('@playwright/test').Page, number: string) =>
    page.locator(`#issue-list a.title[data-detail-url$="/issue/${number}"]`).first();

  test('seed two issues assigned to me in one project', async ({ page }) => {
    owner = requireSeed('adminLoginId');
    project = `my-${uniqueSuffix()}`;
    await page.goto('/projectform');
    await page.selectOption('#project-owner', owner);
    await page.fill('#project-name', project);
    await page.fill('#description', 'My issues two-column');
    await page.check('#public');
    await page.selectOption('#vcs', 'GIT');
    await page.click('#newProjectForm button.ybtn-success');
    await expect(page).toHaveURL(new RegExp(`/${owner}/${project}$`));
    for (const letter of ['A', 'B']) {
      const title = `${tag} issue ${letter}`;
      await page.goto(`/${owner}/${project}/issueform`);
      await page.fill('#title', title);
      await page.locator('textarea[data-editor-mode="content-body"]').evaluate((el: HTMLTextAreaElement, text: string) => {
        el.value = text;
        el.dispatchEvent(new Event('input', { bubbles: true }));
      }, `Body ${letter}`);
      await page.click('#button-save');
      await expect(page).toHaveURL(/\/issue\/\d+$/);
      issues.push({ title, number: new URL(page.url()).pathname.split('/').pop()! });
    }
    // /user/issues는 필터가 없으면 "나에게 할당된" 이슈만 보여주므로 두 이슈를 모두 나에게 할당한다.
    await page.goto(`/${owner}/${project}/issues`);
    for (const issue of issues) await page.locator('li.post-item', { hasText: issue.title }).locator('input[name="checked-issue"]').check();
    await page.click('#list-assignee button.dropdown-toggle');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'load' }),
      page.locator('#list-assignee ul.dropdown-menu > li').nth(1).locator('a').click(),
    ]);
  });

  test('detail-only A/B navigation, Back/Forward, reload and no-JS direct URL', async ({ page, browser }) => {
    const [a, b] = issues;
    await page.goto(listUrl());
    await page.evaluate(() => localStorage.setItem('useTwoColumnMode', 'true'));
    await page.reload();
    await expect(page.locator('#two-column-mode')).toBeChecked();
    const list = await page.locator('#issue-list').elementHandle();
    const frameRequests: string[] = [];
    page.on('request', request => {
      if (request.headers()['turbo-frame'] === 'issue-detail') frameRequests.push(request.url());
    });
    for (const issue of [a, b]) {
      await link(page, issue.number).click();
      await expect(page.locator('#issue-detail .board-header')).toContainText(issue.title);
      await expect(page.locator('#issue-detail')).not.toHaveAttribute('aria-busy', 'true');
      const url = new URL(page.url());
      expect(url.pathname).toBe('/user/issues');
      expect(url.searchParams.get('detail')).toBe(key(issue.number));
      expect(url.searchParams.get('filter')).toBe(tag);
      expect(await list!.evaluate(element => element.isConnected)).toBe(true);
      await expect(page.locator('iframe, yona-page-slide')).toHaveCount(0);
      await expect(link(page, issue.number).locator('xpath=ancestor::li[contains(@class,"post-item")]')).toHaveClass(/highlightBg/);
    }
    // 클릭 1회당 상세 요청 1회(재마운트로 리스너가 중복 등록되면 늘어난다).
    expect(frameRequests).toHaveLength(2);

    await page.goBack();
    await expect(page.locator('#issue-detail .board-header')).toContainText(a.title);
    await page.goForward();
    await expect(page.locator('#issue-detail .board-header')).toContainText(b.title);

    const selectedUrl = page.url();
    await page.reload();
    await expect(page.locator('#issue-list')).toContainText(a.title);
    await expect(page.locator('#issue-detail .board-header')).toContainText(b.title);

    // 같은 행을 다시 누르면 선택이 해제되고 detail 파라미터가 사라진다.
    await link(page, b.number).click();
    await expect(page.locator('#issue-detail')).toBeHidden();
    expect(new URL(page.url()).searchParams.has('detail')).toBe(false);
    expect(new URL(page.url()).searchParams.get('filter')).toBe(tag);

    const fallback = await browser.newContext({ storageState: './.auth/admin.json', javaScriptEnabled: false });
    try {
      const tab = await fallback.newPage();
      const response = await tab.goto(selectedUrl);
      expect(response?.status()).toBe(200);
      await expect(tab.locator('#issue-list')).toContainText(a.title);
      await expect(tab.locator('#issue-detail')).toContainText(b.title);
    } finally { await fallback.close(); }
  });

  test('search form and pagination keep the selected detail', async ({ page }) => {
    const [a] = issues;
    await page.goto(`${listUrl()}&detail=${encodeURIComponent(key(a.number))}`);
    await expect(page.locator('#issue-detail .board-header')).toContainText(a.title);
    await expect(page.locator('#search input[name="detail"]')).toHaveValue(key(a.number));
    await page.locator('#search input[name="filter"]').fill(`${tag} issue`);
    await Promise.all([page.waitForNavigation(), page.locator('#search .search-btn').click()]);
    expect(new URL(page.url()).searchParams.get('detail')).toBe(key(a.number));
    await expect(page.locator('#issue-detail .board-header')).toContainText(a.title);
  });

  test('invalid or unreadable keys never render a detail', async ({ page }) => {
    const [a] = issues;
    for (const bad of ['garbage', `issue:${owner}/${project}/999999`, `issue:nobody-${tag}/none/1`, `pull:${owner}/${project}/${a.number}`]) {
      const response = await page.goto(`${listUrl()}&detail=${encodeURIComponent(bad)}`);
      expect(response?.status()).toBeLessThan(500);
      await expect(page.locator('#issue-detail .board-header')).toHaveCount(0);
    }
  });
});
