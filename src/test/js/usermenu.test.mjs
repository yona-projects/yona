// 사용자 메뉴(`esm/Usermenu.js`): 왼쪽 즐겨찾기 사이드바 열고 닫기, 프로젝트, 조직, 이슈 즐겨찾기 토글, 검색 필터, 목록 요청과 오류 처리를 고정한다(시험 채우기, D-123).
// 서버 요청은 `yona.http`를 가짜로 바꿔 받은 주소를 기록하고 [responses]의 응답을 `done`, `fail`로 돌려준다.
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `<div id="main"><div class="main-stream col-lg-12"></div></div>
<nav class="gnb-usermenu-dropdown"><button id="sidebar-open-btn" type="button">열기</button></nav>
<div id="mySidenav" style="width:0"><div id="usermenu-tab-content-list"></div>
  <input class="search-input" type="text">
  <ul class="user-ul"><li class="user-li" data-location="/a/one">Alpha proj</li><li class="user-li" data-location="/a/two">Beta proj</li></ul>
  <ul class="org-ul"><li class="org-li"><span class="sub-project-counter"></span><span class="all-orgs">조직</span><ul class="project-ul"><li class="user-li">x</li><li class="user-li">y</li></ul>Gamma org</li></ul>
  <div class="project-list"><span class="star-project" data-project-id="7"><i></i></span><span class="star-project" data-project-id="8"><i></i></span></div>
  <div class="org-list"><span class="star-org" data-organization-id="3"><i></i></span></div>
  <span class="favorite-issue" data-issue-id="5"><i></i></span>
</div>`;

async function open({ html = HTML, responses = {}, width = 1000 } = {}) {
  const page = await pageWith({ stubs: false, html, scripts: [{ content: 'window.Messages = function (k) { return k; };' }, 'common/yona.Common.js'] });
  await page.setViewportSize({ width, height: 700 });
  await page.evaluate((resp) => {
    window.UsermenuUrl = '/um'; window.UsermenuToggleFavoriteProjectUrl = '/fp/'; window.UsermenuToggleFoveriteOrganizationUrl = '/fo/'; window.UsermenuGetFoveriteProjectsUrl = '/fl'; window.UsermenuToggleFavoriteIssueUrl = '/fi/';
    window.__calls = []; window.__alerts = []; window.__notes = [];
    const fake = (kind) => (url) => { window.__calls.push(kind + ' ' + url); const r = resp[url]; const h = { done(cb) { if (r && r.ok) setTimeout(() => cb(r.data), 0); return h; }, fail(cb) { if (r && !r.ok) setTimeout(() => cb({ responseText: JSON.stringify(r.data) }), 0); return h; },
      // 실제 yona.http처럼 await할 수 있게 한다. 응답을 정하지 않은 주소는 끝내 완료되지 않는다(예전에 done/fail이 불리지 않던 것과 같다)
      then(ok, bad) { return new Promise((res, rej) => { if (r && r.ok) setTimeout(() => res(r.data), 0); else if (r) setTimeout(() => rej({ responseText: JSON.stringify(r.data) }), 0); }).then(ok, bad); } }; return h; };
    window.yona = window.yona || {}; yona.http = { get: fake('GET'), post: fake('POST') };
    $yona.alert = (m) => window.__alerts.push(m); $yona.notify = (m) => window.__notes.push(m);
  }, responses);
  await page.addScriptTag({ content: classicFromEsm('esm/Usermenu.js') });
  await page.waitForTimeout(150);
  return page;
}
const calls = (page) => page.evaluate(() => window.__calls.slice());
const width = (page) => page.evaluate(() => document.getElementById('mySidenav').style.width);

export const tests = [
  { name: '사용자 메뉴: 드롭다운이 있으면 처음에 목록(UsermenuUrl)을 읽어 #usermenu-tab-content-list에 넣는다', run: async () => {
    const page = await open({ responses: { '/um': { ok: true, data: '<b class="loaded">목록</b>' } } });
    assert.equal((await calls(page))[0], 'GET /um'); assert.equal(await page.locator('#usermenu-tab-content-list .loaded').count(), 1); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '사용자 메뉴: 드롭다운이 없으면 목록을 읽지 않고 검색 필터 같은 처리기는 그대로 달린다', run: async () => {
    const page = await open({ html: HTML.replace('class="gnb-usermenu-dropdown"', 'class="x"') });
    assert.deepEqual((await calls(page)).filter((c) => c === 'GET /um'), []);
    await page.fill('.search-input', 'beta'); await page.dispatchEvent('.search-input', 'keyup', { key: 'a' }); await page.waitForTimeout(100);
    assert.deepEqual(await page.evaluate(() => [...document.querySelectorAll('.user-ul > .user-li')].map((e) => getComputedStyle(e).display !== 'none')), [false, true]); await page.close();
  } },
  { name: '사이드바: 열기 단추를 누르면 360px로 열리고 .main-stream은 col-lg-8이 되며 즐겨찾기 목록을 읽는다. 다시 누르면 닫힌다', run: async () => {
    const page = await open({ responses: { '/um': { ok: true, data: '' }, '/fl': { ok: true, data: { projectIds: [7] } } } });
    await page.click('#sidebar-open-btn'); await page.waitForTimeout(100);
    assert.equal(await width(page), '360px'); assert.equal(await page.evaluate(() => document.querySelector('.main-stream').className), 'main-stream col-lg-8'); assert.ok((await calls(page)).includes('GET /fl'));
    assert.deepEqual(await page.evaluate(() => [...document.querySelectorAll('.star-project i')].map((i) => i.classList.contains('starred'))), [true, false]);
    await page.click('#sidebar-open-btn'); await page.waitForTimeout(100);
    assert.equal(await width(page), '0px'); assert.equal(await page.evaluate(() => document.querySelector('.main-stream').className), 'main-stream col-lg-12'); await page.close();
  } },
  { name: '사이드바: 작은 화면(720px 이하)에서는 100vw로 열린다', run: async () => {
    const page = await open({ width: 600, responses: { '/um': { ok: true, data: '' }, '/fl': { ok: true, data: { projectIds: [] } } } });
    await page.click('#sidebar-open-btn'); await page.waitForTimeout(100); assert.equal(await width(page), '100vw'); await page.close();
  } },
  { name: '사이드바: 본문(#main)을 누르면 닫히고, 사이드바 안을 누르면 닫히지 않는다', run: async () => {
    const page = await open({ responses: { '/um': { ok: true, data: '' }, '/fl': { ok: true, data: { projectIds: [] } } } });
    await page.click('#sidebar-open-btn'); await page.waitForTimeout(100);
    await page.evaluate(() => document.querySelector('.user-ul').dispatchEvent(new MouseEvent('click', { bubbles: true }))); await page.waitForTimeout(50); assert.equal(await width(page), '360px');
    await page.evaluate(() => document.querySelector('.main-stream').dispatchEvent(new MouseEvent('click', { bubbles: true }))); await page.waitForTimeout(50); assert.equal(await width(page), '0px'); await page.close();
  } },
  { name: '사이드바: 검색 입력에서 Esc를 누르면 닫힌다', run: async () => {
    const page = await open({ responses: { '/um': { ok: true, data: '' }, '/fl': { ok: true, data: { projectIds: [] } } } });
    await page.click('#sidebar-open-btn'); await page.waitForTimeout(100); await page.focus('.search-input'); await page.keyboard.press('Escape'); await page.waitForTimeout(100); assert.equal(await width(page), '0px'); await page.close();
  } },
  { name: '검색 필터: 입력한 글자가 든 프로젝트와 조직만 보이고, 비운 뒤 Backspace면 모두 보인다', run: async () => {
    const page = await open({ responses: { '/um': { ok: true, data: '' } } });
    const shown = () => page.evaluate(() => [...document.querySelectorAll('.user-ul > .user-li, .org-li')].map((e) => getComputedStyle(e).display !== 'none'));
    await page.fill('.search-input', 'gam'); await page.dispatchEvent('.search-input', 'keyup', { key: 'm' }); await page.waitForTimeout(80); assert.deepEqual(await shown(), [false, false, true]);
    await page.fill('.search-input', ''); await page.dispatchEvent('.search-input', 'keyup', { key: 'Backspace' }); await page.waitForTimeout(80); assert.deepEqual(await shown(), [true, true, true]); await page.close();
  } },
  { name: '프로젝트 즐겨찾기: 별을 누르면 번호를 이어 붙인 주소로 POST하고 favored가 참이면 starred를 달고 거짓이면 뗀다', run: async () => {
    const page = await open({ responses: { '/fp/7': { ok: true, data: { favored: true } }, '/fp/8': { ok: true, data: { favored: false } } } });
    await page.evaluate(() => document.querySelectorAll('.star-project i')[1].classList.add('starred'));
    await page.dispatchEvent('.star-project[data-project-id="7"]', 'click'); await page.dispatchEvent('.star-project[data-project-id="8"]', 'click'); await page.waitForTimeout(100);
    assert.deepEqual((await calls(page)).filter((c) => c.startsWith('POST')), ['POST /fp/7', 'POST /fp/8']);
    assert.deepEqual(await page.evaluate(() => [...document.querySelectorAll('.star-project i')].map((i) => i.classList.contains('starred'))), [true, false]); await page.close();
  } },
  { name: '프로젝트 즐겨찾기: 요청이 실패하면 서버가 준 reason으로 알림창을 띄운다', run: async () => {
    const page = await open({ responses: { '/fp/7': { ok: false, data: { reason: '권한 없음' } } } });
    await page.dispatchEvent('.star-project[data-project-id="7"]', 'click'); await page.waitForTimeout(100); assert.deepEqual(await page.evaluate(() => window.__alerts), ['Update failed: 권한 없음']); await page.close();
  } },
  { name: '조직과 이슈 즐겨찾기: 각자 주소로 POST하고, 이슈는 서버 메시지를 알림으로 보인다', run: async () => {
    const page = await open({ responses: { '/fo/3': { ok: true, data: { favored: true } }, '/fi/5': { ok: true, data: { favored: true, message: 'issue.favorite.on' } } } });
    await page.dispatchEvent('.star-org', 'click'); await page.dispatchEvent('.favorite-issue', 'click'); await page.waitForTimeout(100);
    assert.deepEqual((await calls(page)).filter((c) => c.startsWith('POST')), ['POST /fo/3', 'POST /fi/5']);
    assert.deepEqual(await page.evaluate(() => [document.querySelector('.star-org i').classList.contains('starred'), document.querySelector('.favorite-issue i').classList.contains('starred'), window.__notes]), [true, true, ['issue.favorite.on']]); await page.close();
  } },
  { name: '프로젝트 항목: 누르면 새 창(또는 mainFrame)으로 data-location을 열고 selected를 옮긴다', run: async () => {
    const page = await open({ responses: {} });
    await page.evaluate(() => { window.__opened = []; window.open = (u, t) => window.__opened.push([u, t]); });
    await page.dispatchEvent('.user-ul > .user-li:nth-child(1)', 'click'); await page.dispatchEvent('.user-ul > .user-li:nth-child(2)', 'click'); await page.waitForTimeout(60);
    assert.deepEqual(await page.evaluate(() => window.__opened), [['/a/one', '_blank'], ['/a/two', '_blank']]); assert.deepEqual(await page.evaluate(() => [...document.querySelectorAll('.user-ul > .user-li')].map((e) => e.classList.contains('selected'))), [false, true]); await page.close();
  } },
  { name: 'v1.6 결함 수정: 목록을 읽어 와도 처리기는 한 번만 붙어서 별을 한 번 누르면 요청도 한 번만 간다', run: async () => {
    const page = await open({ responses: { '/um': { ok: true, data: '' }, '/fp/7': { ok: true, data: { favored: true } } } });
    await page.dispatchEvent('.star-project[data-project-id="7"]', 'click'); await page.waitForTimeout(100);
    assert.deepEqual((await calls(page)).filter((c) => c.startsWith('POST')), ['POST /fp/7']); await page.close();
  } },
  { name: '조직 목록: 하위 프로젝트 개수를 .sub-project-counter에 쓴다', run: async () => {
    const page = await open({ responses: { '/um': { ok: true, data: '' } } }); assert.equal(await page.locator('.sub-project-counter').textContent(), '2'); await page.close();
  } },
];
