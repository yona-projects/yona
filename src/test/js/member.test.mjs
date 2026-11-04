import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const HTML = `
  <div class="members">
    <a data-action="delete" id="del-a" data-href="/org/member/1/delete" href="#">A</a>
    <a data-action="delete" id="del-b" data-href="/org/member/2/delete" href="#">B</a>
  </div>
  <div id="alertDeletion"></div>
  <button id="deleteBtn">확인</button>
  <input id="loginId">`;

export const tests = [{
  name: '구성원 삭제 확인 핸들러는 클릭마다 쌓이지 않아 마지막에 고른 구성원만 지운다 (C024, C174, C113-3)',
  async run() {
    const page = await pageWith({ html: HTML, scripts: ['service/yobi.organization.Member.js'] });
    const calls = await page.evaluate(() => {
      window.__ajax = [];
      $.ajax = function (url) { window.__ajax.push(url); };
      new yobi.organization.Member({ sActionURL: '/x', htData: {} });
      $('#del-a').click();   // A를 고른 뒤 취소하고
      $('#del-b').click();   // B를 다시 고른다
      $('#deleteBtn').click(); // 확인을 한 번 누른다
      return window.__ajax;
    });
    await page.close();
    assert.deepEqual(calls, ['/org/member/2/delete'], '확인을 한 번 눌렀는데 DELETE 요청이 ' + JSON.stringify(calls));
  }
}];
