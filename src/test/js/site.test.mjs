// 사이트 관리 화면(프로젝트 목록)의 인라인 스크립트가 서버의 거절과 실패를 사용자에게 보이는지 시험한다.
// v1.6은 이미 지워진 프로젝트(404)에서 아무 반응이 없었다(site-F5).
import assert from 'node:assert/strict';
import { pageWith, templateSnippet } from './harness.mjs';

/** 가짜 fetch, 가짜 새로 고침을 심은 페이지 */
async function open(html) {
  const page = await pageWith({ html, scripts: ['esm/Request.js'] });
  await page.evaluate(() => {
    window.__nav = []; window.__reply = { status: 403 };
    window.fetch = function () { return Promise.resolve(new Response('', { status: window.__reply.status })); };
    yona.Request.navigation = { to: (u) => window.__nav.push(['to', u]), reload: () => window.__nav.push(['reload']) };
  });
  return page;
}

export const tests = [
  {
    name: '프로젝트 삭제가 404나 403이면 확인창을 닫고 화면을 다시 연다. 그 밖의 실패는 알린다 (v1_6은 아무 반응이 없었다, 고쳤다 site-F5)',
    async run() {
      const code = templateSnippet('site/project-list.html', "document.getElementById('projectDeleteBtn')", 'yona.Pagination.update');
      const page = await open('<button id="projectDeleteBtn"></button><div id="alertDeletionWrap"></div>');
      await page.evaluate('window.__hidden = 0; bootstrap.Modal.getOrCreateInstance = function () { return { hide: function () { window.__hidden++; } }; };\nvar deleteUrl = "/sites/project/delete/9";\n' + code);
      const run = async (status) => {
        await page.evaluate((st) => { window.__nav = []; window.__alerts = []; window.__reply = { status: st }; }, status);
        await page.click('#projectDeleteBtn');
        await page.waitForTimeout(150);
        return page.evaluate(() => ({ nav: window.__nav, alerts: window.__alerts.length, hidden: window.__hidden }));
      };
      const notFound = await run(404);
      const serverError = await run(500);
      await page.close();
      assert.deepEqual(notFound.nav, [['reload']]);
      assert.equal(notFound.alerts, 0);
      assert.equal(notFound.hidden, 1, '확인창이 닫히지 않았다');
      assert.deepEqual(serverError.nav, []);
      assert.equal(serverError.alerts, 1, '서버 오류를 알리지 않았다');
    }
  }
];
