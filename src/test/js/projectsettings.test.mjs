// 프로젝트 설정 계열(`esm/service/project.Setting.js`, `Global.js`, `ChangeVCS.js`, `Transfer.js`, `Delete.js`, `Fork.js`, `Webhook.js`)의 검증, 메뉴 연동, 위험 작업 확인, 오류 문구를 고정한다(시험 채우기, D-123).
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const MSG = { content: 'window.Messages = function (k) { return k; };' };
async function base(html, file, ctor, options) {
  const page = await pageWith({ stubs: false, html, scripts: [MSG, 'common/yona.Common.js'] });
  await page.evaluate(() => {
    window.__alerts = []; window.__notes = []; window.__ajax = []; window.__hidden = []; window.__req = []; window.__km = [];
    $yona.alert = (m) => window.__alerts.push(m); $yona.showAlert = $yona.alert; $yona.notify = (m) => window.__notes.push(m);
    $yona.isImageFile = (el) => /\.(png|jpg)$/i.test(el.data('fake') || '');
    window.bootstrap = { Modal: { getOrCreateInstance: (el) => ({ hide: () => window.__hidden.push(el.id) }) } };
    yona.ShortcutKey = { setKeymapLink: (m) => window.__km.push(m) };
    yona.Request = { send: (method, url) => { window.__req.push([method, url]); return Promise.resolve({ ok: window.__reqOk }); } };
    yona.http = { ajax: (url, o) => { window.__ajax.push([url, o]); return {}; } };
  });
  await page.addScriptTag({ content: classicFromEsm(file) });
  await page.evaluate(([c, o]) => { new (c.split('.').reduce((a, k) => a[k], window))(o); }, [ctor, options || {}]);
  return page;
}
const get = (page, k) => page.evaluate((k) => window[k], k);
const clickResult = (page, sel) => page.evaluate((sel) => { const e = $.Event('click'); $(sel).trigger(e); return e.isDefaultPrevented(); }, sel);

const SETTING = `<form id="saveSetting"><input id="logoPath" type="file"><input id="project-name" value=""><button id="save" type="button">저장</button>
<input type="checkbox" id="menuSettingCode" checked><input type="checkbox" id="menuSettingPullRequest" checked><input type="checkbox" id="menuSettingReview" checked>
<div id="reviewerCountSettingPanel"></div><div id="defaultBranceSettingPanel"></div><div id="subMenuProjectChangeVCS"></div></form>`;

export const tests = [
  { name: '프로젝트 설정: 이름은 영숫자, 하이픈, 밑줄, 점, 한글만 허용하고 ".", "..", ".git"은 예약어로 막는다', run: async () => {
    const page = await base(SETTING, 'esm/service/project.Setting.js', 'yona.project.Setting');
    const r = await page.evaluate(() => ['my-proj_1.x', '한글', 'a b', '', '.', '..', '.git', 'a/b'].map((v) => { $('#project-name').val(v); const e = $.Event('click'); return [v, $('#save').triggerHandler(e)]; }));
    assert.deepEqual(r.map(([v, ok]) => [v, ok]), [['my-proj_1.x', true], ['한글', true], ['a b', false], ['', false], ['.', false], ['..', false], ['.git', false], ['a/b', false]]);
    assert.deepEqual(await get(page, '__alerts'), ['project.name.alert', 'project.name.alert', 'project.name.reserved.alert', 'project.name.reserved.alert', 'project.name.reserved.alert', 'project.name.alert']); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '프로젝트 설정: 로고가 이미지가 아니면 알림 후 값을 비우고, 이미지면 폼을 제출한다', run: async () => {
    const page = await base(SETTING, 'esm/service/project.Setting.js', 'yona.project.Setting');
    const r = await page.evaluate(() => { let sub = 0; $('#saveSetting').on('submit', (e) => { sub++; e.preventDefault(); }); $('#logoPath').data('fake', 'a.txt').trigger('change'); const bad = sub; $('#logoPath').data('fake', 'a.png').trigger('change'); return [bad, sub]; });
    assert.deepEqual(r, [0, 1]); assert.deepEqual(await get(page, '__alerts'), ['project.logo.alert']); await page.close();
  } },
  { name: '프로젝트 설정: 코드 메뉴를 끄면 풀 리퀘스트와 리뷰가 꺼지고 관련 패널이 숨겨진다. 풀 리퀘스트와 리뷰를 켜면 코드가 켜진다', run: async () => {
    const page = await base(SETTING, 'esm/service/project.Setting.js', 'yona.project.Setting');
    const state = () => page.evaluate(() => ['menuSettingCode', 'menuSettingPullRequest', 'menuSettingReview'].map((id) => document.getElementById(id).checked).concat([$('#reviewerCountSettingPanel').css('display')]));
    await page.click('#menuSettingCode');
    assert.deepEqual(await state(), [false, false, false, 'none']);
    await page.click('#menuSettingPullRequest');
    assert.deepEqual(await state(), [true, true, false, 'block']);
    await page.click('#menuSettingPullRequest'); assert.equal((await state())[3], 'none');
    await page.click('#menuSettingCode'); await page.click('#menuSettingReview');
    assert.deepEqual((await state()).slice(0, 3), [true, false, true]); await page.close();
  } },

  { name: '프로젝트 전역: 관심 단추는 href로 POST하고 기본 동작을 막는다. 실패하면 "Server Error"를 알린다', run: async () => {
    const page = await base(`<a class="watchBtn" href="/watch/1">w</a><a id="enrollBtn" href="/enroll/1">e</a>`, 'esm/service/project.Global.js', 'yona.project.Global');
    assert.equal(await clickResult(page, '.watchBtn'), true);
    const [url, o] = (await get(page, '__ajax'))[0]; assert.deepEqual([url, o.method], ['/watch/1', 'post']);
    await page.evaluate(() => { window.__ajax[0][1].error(); }); assert.deepEqual(await get(page, '__notes'), ['Server Error']); await page.close();
  } },
  { name: '프로젝트 전역: 가입 신청 실패 문구는 네트워크(readyState 0), 403, 4xx, 5xx, 그 밖으로 나뉜다', run: async () => {
    const page = await base(`<a id="enrollBtn" href="/enroll/1">e</a>`, 'esm/service/project.Global.js', 'yona.project.Global');
    await clickResult(page, '#enrollBtn');
    await page.evaluate(() => { const err = window.__ajax[0][1].error; [{ readyState: 0, status: 0 }, { readyState: 4, status: 403 }, { readyState: 4, status: 404 }, { readyState: 4, status: 503 }, { readyState: 4, status: 302 }].forEach(err); });
    assert.deepEqual(await get(page, '__notes'), ['user.enroll.failed.network', 'error.forbidden', 'user.enroll.failed.client', 'user.enroll.failed.server', 'user.enroll.failed']); await page.close();
  } },
  { name: '프로젝트 전역: 단축키 지도(htKeyMap)가 객체일 때만 setKeymapLink를 부른다', run: async () => {
    let page = await base('', 'esm/service/project.Global.js', 'yona.project.Global', { htKeyMap: { a: 1 } }); assert.deepEqual(await get(page, '__km'), [{ a: 1 }]); await page.close();
    page = await base('', 'esm/service/project.Global.js', 'yona.project.Global', {}); assert.deepEqual(await get(page, '__km'), []); await page.close();
  } },

  { name: 'VCS 변경: 동의 체크가 없으면 확인 창 대신 알림을 띄워 막는다. 실행하면 대상 VCS를 data로 POST한다', run: async () => {
    const page = await base(`<input type="checkbox" id="acceptChangeVCS"><button id="btnChangeVCS"></button><button id="btnChangeVCSExec"></button><select id="targetVcs"><option value="GIT">g</option></select><span class="next-vcs"></span><span class="vcs-description"></span><span class="vcs-requestion"></span><div id="alertChangeVCS"></div>`, 'esm/service/project.ChangeVCS.js', 'yona.project.ChangeVCS', { sTransferURL: '/vcs', oTexts: { GIT: { description: '설명', requestion: '요청' } } });
    assert.equal(await clickResult(page, '#btnChangeVCS'), true); // 처리기가 false를 돌려주면 기본 동작이 막힌다
    assert.deepEqual(await get(page, '__alerts'), ['project.changeVCS.alert']);
    await page.evaluate(() => { $('#btnChangeVCSExec').trigger('click'); const [url, o] = window.__ajax[0]; window.__a = [url, o.method, o.data]; });
    assert.deepEqual(await get(page, '__a'), ['/vcs', 'post', { targetVcs: 'GIT' }]);
    await page.evaluate(() => { $('#targetVcs').trigger('change'); }); assert.deepEqual(await page.evaluate(() => [$('.next-vcs').text(), $('.vcs-description').text(), $('.vcs-requestion').text()]), ['GIT', '설명', '요청']);
    await page.evaluate(() => { window.__ajax[0][1].error(); }); assert.deepEqual(await get(page, '__hidden'), ['alertChangeVCS']); assert.deepEqual((await get(page, '__alerts')).slice(-1), ['project.changeVCS.error']); await page.close();
  } },
  { name: '프로젝트 이전: 동의 체크가 없으면 알림을 띄워 막고, 실행은 owner를 쿼리로 PUT하며 한 번만 보낸다. 실패하면 모달을 닫고 알린다', run: async () => {
    const page = await base(`<input type="checkbox" id="accept"><button id="btnTransfer"></button><button id="btnTransferExec"></button><input id="owner" value="newowner"><div id="alertTransfer"></div>`, 'esm/service/project.Transfer.js', 'yona.project.Transfer', { sTransferURL: '/transfer' });
    assert.equal(await clickResult(page, '#btnTransfer'), true); // 처리기가 false를 돌려주면 기본 동작이 막힌다
    assert.deepEqual(await get(page, '__alerts'), ['project.transfer.alert']);
    await page.evaluate(() => { $('#btnTransferExec').trigger('click').trigger('click'); });
    const ajax = await get(page, '__ajax'); assert.equal(ajax.length, 1); assert.deepEqual([ajax[0][0], ajax[0][1].method], ['/transfer?owner=newowner', 'put']);
    await page.evaluate(() => { window.__ajax[0][1].error(); }); assert.deepEqual(await get(page, '__hidden'), ['alertTransfer']); assert.deepEqual((await get(page, '__alerts')).slice(-1), ['project.transfer.error']); await page.close();
  } },
  { name: '프로젝트 삭제: 동의 체크가 없으면 알림으로 막고, 실행은 DELETE 요청을 보내며 실패(ok=false)면 모달을 닫고 알린다', run: async () => {
    const page = await base(`<input type="checkbox" id="accept"><button id="btnDelete"></button><button id="btnDeleteExec"></button><div id="alertDeletion"></div>`, 'esm/service/project.Delete.js', 'yona.project.Delete', { sDeleteURL: '/delete' });
    assert.equal(await clickResult(page, '#btnDelete'), true); // 처리기가 false를 돌려주면 기본 동작이 막힌다
    assert.deepEqual(await get(page, '__alerts'), ['project.delete.alert']);
    await page.evaluate(() => { window.__reqOk = false; $('#btnDeleteExec').trigger('click'); }); await page.waitForTimeout(40);
    assert.deepEqual(await get(page, '__req'), [['delete', '/delete']]); assert.deepEqual(await get(page, '__hidden'), ['alertDeletion']); assert.deepEqual((await get(page, '__alerts')).slice(-1), ['project.delete.error']); await page.close();
  } },
  { name: '프로젝트 포크: 소유자 선택을 바꾸면 선택한 항목의 data-url로 이동한다', run: async () => {
    const page = await base(`<select id="project-owner"><option data-url="#a">a</option><option data-url="#b">b</option></select>`, 'esm/service/project.Fork.js', 'yona.project.Fork');
    await page.evaluate(() => { $('#project-owner').val('b').trigger('change'); }); assert.equal(await page.evaluate(() => location.hash), '#b'); await page.close();
  } },
  { name: '웹훅: payload URL이 비면 알림을 띄워 제출을 막고, 있으면 통과시킨다', run: async () => {
    const page = await base(`<form id="wh"><input name="payloadUrl" value=""></form>`, 'esm/service/project.Webhook.js', 'yona.project.Webhook', { form: '#wh' });
    const r = await page.evaluate(() => { const a = $('#wh').triggerHandler('submit'); $('[name=payloadUrl]').val('http://x'); const b = $('#wh').triggerHandler('submit'); return [a, b]; });
    assert.deepEqual(r, [false, true]); assert.deepEqual(await get(page, '__alerts'), ['project.webhook.payloadUrl.empty']); await page.close();
  } },
];
