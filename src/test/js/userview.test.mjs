// 사용자 보기 화면(`esm/service/user.View.js`)의 탭 기억을 시험한다.
import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

export const tests = [
  {
    name: '탭을 바꾸면 바뀐 탭을 기억해 daysAgo Enter 이동에도 쓴다 (v1_6은 처음 탭으로 돌아갔다, 고쳤다 user-F4)',
    async run() {
      const html = `<input id="daysAgoBtn" value="5">
        <a id="t1" href="#issues" data-bs-toggle="tab">이슈</a><a id="t2" href="#projects" data-bs-toggle="tab">프로젝트</a>
        <a id="sub" href="#closedIssues" data-bs-toggle="tab">닫힘</a>`;
      const page = await pageWith({ html, scripts: ['common/yona.Common.js', 'esm/service/user.View.js'] });
      const seen = await page.evaluate(() => {
        const urls = [];
        const orig = history.replaceState.bind(history);
        history.replaceState = (st, t, url) => { urls.push(String(url)); return orig(st, t, url); };
        window.Messages = (k) => k; window._initTwoColumnMode = () => {};
        new yona.user.View({ sTabSelected: 'issues' });
        const shown = (id) => document.getElementById(id).dispatchEvent(new CustomEvent('shown.bs.tab', { bubbles: true }));
        shown('t2');                                  // 프로젝트 탭으로 바꾼다
        shown('sub');                                 // 하위 탭은 무시한다
        return urls;
      });
      await page.close();
      assert.deepEqual(seen, ['?daysAgo=5&selected=projects'], '바뀐 탭이 기억되지 않았거나 하위 탭이 섞였다');
    }
  }
];
