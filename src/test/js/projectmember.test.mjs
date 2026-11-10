// 프로젝트 멤버 관리(`esm/service/project.Member.js`): 멤버 추가 자동 완성과 캐시, 추가 요청, 가입 승인, 역할 변경, 삭제 확인과 오류 문구를 고정한다(시험 채우기, D-123).
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `
<form id="addNewMember" action="/members"><input id="loginId" name="loginId" value=""></form>
<button class="enrollAcceptBtn" data-loginId="newbie">수락</button>
<div class="members">
  <button data-action="apply" data-href="/role/1" data-loginId="kim">적용</button>
  <button data-action="apply" data-href="/role/2" data-loginId="park">적용</button>
  <input name="roleof-kim" value="3">
  <button data-action="delete" data-href="/del/1">삭제</button>
</div>`;

async function open() {
  const page = await pageWith({ stubs: false, html: HTML, scripts: [{ content: 'window.Messages = function (k) { return k; };' }, 'common/yona.Common.js'] });
  await page.evaluate(() => {
    window.__send = []; window.__alerts = []; window.__notes = []; window.__ajax = []; window.__ac = [];
    yona.ui = yona.ui || {}; yona.ui.Typeahead = function (sel, o) { window.__ta = o.htData; };
    $yona.sendForm = (o) => window.__send.push(o); $yona.alert = (m) => window.__alerts.push(m); $yona.notify = (m) => window.__notes.push(m);
    $yona.ajaxConfirm = (m, o, _x, b) => window.__ac.push([m, o, b]);
    yona.http = { ajax: (url, o) => { window.__ajax.push([url, o.method, o.dataType, o.data]); const h = { done() { return h; }, fail(cb) { if (window.__fail) setTimeout(() => cb(window.__fail), 0); return h; } }; return h; } };
  });
  await page.addScriptTag({ content: classicFromEsm('esm/service/project.Member.js') });
  await page.evaluate(() => { new yona.project.Member({ sActionURL: '/find', htData: { project: 1 } }); });
  return page;
}
const get = (page, k) => page.evaluate((k) => window[k], k);
const xhr = (range) => `{ getResponseHeader: () => ${JSON.stringify(range)} }`;

export const tests = [
  { name: '시작: 로그인 아이디 입력란에 포커스하고 자동 완성은 한 글자부터 쓴다', run: async () => {
    const page = await open();
    assert.equal(await page.evaluate(() => document.activeElement.id), 'loginId'); assert.equal(await page.evaluate(() => window.__ta.minLength), 1); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '자동 완성: 검색어와 기존 조건으로 GET 요청하고, 응답의 info 목록을 보여 주며, 고르면 loginId를 돌려준다', run: async () => {
    const page = await open();
    const r = await page.evaluate(() => { let shown; window.__ta.source('ki', (list) => { shown = list; }); const o = window.__send[0]; o.fOnLoad([{ info: 'kim (Kim)', loginId: 'kim' }, { info: 'kimura (K)', loginId: 'kimura' }], 'ok', { getResponseHeader: () => null }); return { o: [o.sURL, o.htOptForm, o.htData, o.sDataType], shown: window.__shown = shown, picked: window.__ta.updater('kimura (K)'), render: window.__ta.render('x') }; });
    assert.deepEqual(r.o, ['/find', { method: 'get' }, { project: 1, query: 'ki' }, 'json']); assert.equal(r.picked, 'kimura'); assert.equal(r.render, 'x'); await page.close();
  } },
  { name: '자동 완성: 직전 결과가 전체 범위이고 검색어가 이어지면 서버에 다시 묻지 않고 캐시를 쓴다. 일부 범위면 다시 묻는다', run: async () => {
    const page = await open();
    const r = await page.evaluate(() => { window.__ta.source('k', () => {}); window.__send[0].fOnLoad([{ info: 'kim', loginId: 'kim' }], 'ok', { getResponseHeader: () => 'items 5/5' }); let cached; window.__ta.source('ki', (l) => { cached = l; }); const n1 = window.__send.length;
      window.__ta.source('x', () => {}); window.__send[1].fOnLoad([{ info: 'x1', loginId: 'x1' }], 'ok', { getResponseHeader: () => 'items 3/10' }); window.__ta.source('x1', () => {}); return [n1, cached, window.__send.length]; });
    assert.deepEqual(r, [1, ['kim'], 3]); await page.close();
  } },
  { name: '추가: 제출하면 기본 제출을 막고 loginId를 json으로 POST하며, 실패하면 오류 목록의 마지막 문구를 알림으로 띄운다', run: async () => {
    const page = await open();
    const prevented = await page.evaluate(() => { window.__fail = { responseText: '{"loginId":["첫째","마지막"]}' }; $('#loginId').val('park'); const e = $.Event('submit'); $('#addNewMember').trigger(e); return e.isDefaultPrevented(); }); await page.waitForTimeout(40);
    assert.equal(prevented, true); assert.deepEqual((await get(page, '__ajax'))[0], ['/members', 'post', 'json', { loginId: 'park' }]); assert.deepEqual(await get(page, '__notes'), ['마지막']); await page.close();
  } },
  { name: '가입 수락: 단추를 누르면 data-loginId를 입력란에 넣고 추가 폼을 제출한다', run: async () => {
    const page = await open();
    const r = await page.evaluate(() => { let sent; $('#addNewMember').on('submit', (e) => { sent = $('#loginId').val(); e.preventDefault(); }); $('.enrollAcceptBtn').trigger('click'); return sent; });
    assert.equal(r, 'newbie'); await page.close();
  } },
  { name: '역할 적용: 해당 멤버의 역할 값을 id로 보내고, 역할 입력란이 없으면 아무것도 보내지 않는다', run: async () => {
    const page = await open();
    await page.evaluate(() => { $('[data-loginId=kim][data-action=apply]').trigger('click'); $('[data-loginId=park][data-action=apply]').trigger('click'); });
    const sent = await get(page, '__send'); assert.equal(sent.length, 1); assert.deepEqual([sent[0].sURL, sent[0].htData], ['/role/1', { id: '3' }]); await page.close();
  } },
  { name: '삭제: 확인창에 DELETE 요청 정보와 아니오/예 단추를 넘긴다', run: async () => {
    const page = await open();
    await page.evaluate(() => { $('[data-action=delete]').trigger('click'); });
    const [m, o, b] = (await get(page, '__ac'))[0]; assert.equal(m, 'project.member.deleteConfirm'); assert.deepEqual([o.url, o.method, o.dataType], ['/del/1', 'delete', 'html']); assert.deepEqual(b, { aButtonLabels: ['button.no', 'button.yes'], aButtonStyles: ['ybtn-default', 'ybtn-danger'] }); await page.close();
  } },
  { name: '삭제 오류: 403에 소유자 문구가 있으면 그 문구, 없으면 권한 없음, 404는 프로젝트 없음, 그 밖은 잘못된 요청 문구를 보인다', run: async () => {
    const page = await open(); await page.evaluate(() => { $('[data-action=delete]').trigger('click'); });
    const r = await page.evaluate(() => { const err = window.__ac[0][1].error; [{ status: 403, responseText: 'x project.member.ownerCannotLeave y' }, { status: 403, responseText: '' }, { status: 404, responseText: '' }, { status: 500, responseText: '' }].forEach(err); return window.__alerts; });
    assert.deepEqual(r, ['project.member.ownerCannotLeave', 'error.forbidden', 'project.is.empty', 'error.badrequest']); await page.close();
  } },
];
