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
    const page = await pageWith({ html: HTML, scripts: ['esm/service/organization.Member.js'] });
    const calls = await page.evaluate(() => {
      window.__ajax = [];
      ((window.yona = window.yona || {}).http = (window.yona.http || {})).ajax = function (url) { window.__ajax.push(url); };
      new yona.organization.Member({ sActionURL: '/x', htData: {} });
      $('#del-a').click();   // A를 고른 뒤 취소하고
      $('#del-b').click();   // B를 다시 고른다
      $('#deleteBtn').click(); // 확인을 한 번 누른다
      return window.__ajax;
    });
    await page.close();
    assert.deepEqual(calls, ['/org/member/2/delete'], '확인을 한 번 눌렀는데 DELETE 요청이 ' + JSON.stringify(calls));
  }
},
{
  name: '역할 변경이 실패하면 안내하고 라벨을 원래 역할로 되돌린다 (v1_6은 안내 없이 틀린 라벨이 남았다, 고쳤다 organization-F2)',
  async run() {
    const html = `
      <ul class="members"><li><div class="btn-group" data-name="roleof-u1">
        <button><span class="d-label">관리자</span></button>
        <ul class="dropdown-menu">
          <li data-value="1" class="active" data-selected="true"><a data-action="apply" data-href="/o/member/1/edit" data-loginId="u1" class="dropdown-item">관리자</a></li>
          <li data-value="2"><a data-action="apply" data-href="/o/member/1/edit" data-loginId="u1" class="dropdown-item">구성원</a></li>
        </ul><input type="hidden" name="roleof-u1" value="1"></div></li></ul>
      <div id="alertDeletion"></div>`;
    const page = await pageWith({ html, scripts: ['esm/service/organization.Member.js'] });
    const r = await page.evaluate(() => {
      window.__alerts = [];
      $yona.alert = (m) => window.__alerts.push(m);
      $yona.sendForm = (o) => { o.fOnError({ status: 500, responseText: '' }, 'error', ''); };
      new yona.organization.Member({ sActionURL: '/x', htData: {} });
      // Dropdown이 하는 일(라벨과 값을 먼저 바꾼다)을 흉내 내고 항목을 누른다
      $('.d-label').text('구성원'); $('input[name="roleof-u1"]').val('2');
      $('li[data-value=1]').removeClass('active'); $('li[data-value=2]').addClass('active');
      $('li[data-value=2] a').click();
      return { alerts: window.__alerts.length, label: $('.d-label').text(), val: $('input[name="roleof-u1"]').val(), active: $('li.active').attr('data-value') };
    });
    await page.close();
    assert.equal(r.alerts, 1, '안내가 없다');
    assert.equal(r.label, '관리자'); assert.equal(r.val, '1'); assert.equal(r.active, '1');
  }
}];
