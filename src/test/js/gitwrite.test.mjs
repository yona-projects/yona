// 풀 리퀘스트 작성(`esm/service/git.Write.js`): 병합 결과 조회와 표시, 제목과 본문 자동 채움, 제출 검증(변경 없음, 충돌 확인, 필수 입력)을 고정한다(시험 채우기, D-123).
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `
<form class="nm"><input id="title" value=""><textarea data-editor-mode="content-body"></textarea>
  <select id="fp"><option value="1">p1</option></select><select id="tp"><option value="2">p2</option></select>
  <input id="fb" value="feature"><input id="tb" value="main"></form>
<div id="upload"></div><span id="numOfCommits"></span><div id="status"></div><div id="__commits"></div>
<div id="c"><button class="moreBtn">더</button><pre class="commitMsg desc hidden">msg</pre></div>`;

const result = (conflict, commits, title = '제목', body = '본문') => `<div id="mergeResult" data-conflict="${conflict}" data-commits="${commits}" data-pullrequest-title="${title}" data-pullrequest-body="${body}"></div>`;

async function open({ state = 'OPEN', html = null, fail = null, fb = 'feature', tb = 'main' } = {}) {
  const page = await pageWith({ stubs: false, html: HTML, scripts: [{ content: 'window.Messages = function (k) { return k + (arguments.length > 1 ? "|" + [].slice.call(arguments, 1).join(",") : ""); };' }, 'common/yona.Common.js'] });
  await page.evaluate(({ html, fail, fb, tb }) => {
    window.__ajax = []; window.__alerts = []; window.__confirms = []; window.__np = [];
    $('#fb').val(fb); $('#tb').val(tb);
    window.temporarySaveHandler = () => {}; window.NProgress = { start: () => window.__np.push('start'), done: () => window.__np.push('done') };
    yona.Files = { getUploader: () => null, destroyUploader() {} }; yona.Attachments = function () {};
    $yona.alert = (m) => window.__alerts.push(m); $yona.confirm = (m, cb) => window.__confirms.push({ m, cb });
    yona.http = { ajax: (url, o) => { window.__ajax.push([url, o.data]); const h = { done(cb) { if (html !== null) setTimeout(() => cb(html), 0); return h; }, fail(cb) { if (fail) setTimeout(() => cb(fail), 0); return h; }, always(cb) { setTimeout(cb, 5); return h; },
      then(ok, bad) { return new Promise((res, rej) => { if (html !== null) setTimeout(() => res(html), 0); else if (fail) setTimeout(() => rej(fail), 0); }).then(ok, bad); } }; return h; } };
  }, { html, fail, fb, tb });
  await page.addScriptTag({ content: classicFromEsm('esm/service/git.Write.js') });
  await page.evaluate((state) => { window.__w = new yona.git.Write({ mergeResultURL: '/mr', fromProject: $('#fp'), toProject: $('#tp'), fromBranch: $('#fb'), toBranch: $('#tb'), state }); }, state);
  await page.waitForTimeout(60);
  return page;
}
const get = (page, k) => page.evaluate((k) => window[k], k);
const status = (page) => page.evaluate(() => [$('#status').attr('class'), $('#status').text(), $('#numOfCommits').text(), $('#title').val(), $('textarea').val()]);

export const tests = [
  { name: '열기: 상태가 OPEN이면 바로 병합 결과를 조회하고(요청 값은 공백을 뺀 제목, 프로젝트, 브랜치), 안전하면 초록 표시와 제목, 본문을 채운다', run: async () => {
    const page = await open({ html: result(false, 3) });
    assert.deepEqual(await get(page, '__ajax'), [['/mr', { title: '', fromProjectId: '1', toProjectId: '2', fromBranch: 'feature', toBranch: 'main' }]]);
    assert.deepEqual(await status(page), ['alert-success', 'pullRequest.is.safe', '3', '제목', '본문']); assert.deepEqual(await get(page, '__np'), ['start', 'done']); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '열기: 상태가 OPEN이 아니면 조회하지 않는다. 두 브랜치가 모두 비어도 조회하지 않는다', run: async () => {
    let page = await open({ state: 'MERGED', html: result(false, 1) }); assert.deepEqual(await get(page, '__ajax'), []); await page.close();
    page = await open({ html: result(false, 1), fb: '', tb: '' }); assert.deepEqual(await get(page, '__ajax'), []); await page.close();
  } },
  { name: '결과: 충돌이 있으면 빨간 표시와 "안전하지 않음", 커밋이 없으면 파란 표시와 "변경 없음"이다', run: async () => {
    let page = await open({ html: result(true, 2) }); assert.deepEqual((await status(page)).slice(0, 3), ['alert-danger', 'pullRequest.is.not.safe', '2']); await page.close();
    page = await open({ html: result(false, 0) }); assert.deepEqual((await status(page)).slice(0, 3), ['alert-info', 'pullRequest.diff.noChanges', '']); await page.close();
  } },
  { name: '결과: 사용자가 제목을 이미 쳤으면 조회 결과로 제목과 본문을 덮어쓰지 않는다', run: async () => {
    const page = await open({ state: 'MERGED', html: result(false, 1, 'T', 'B') });
    await page.evaluate(() => { $('#title').val('내 제목').trigger('keyup'); });
    await page.evaluate(() => { $('#fb').trigger('change'); }); await page.waitForTimeout(80);
    assert.equal(await page.evaluate(() => $('#title').val()), '내 제목'); await page.close();
  } },
  { name: '결과: 조회가 실패하면 빨간 표시와 상태 문구를 보이고 제목과 본문을 비운다', run: async () => {
    const page = await open({ fail: { status: 500, statusText: 'Err' } });
    await page.evaluate(() => { $('#title').val('x'); $('textarea').val('y'); $('#fb').trigger('change'); }); await page.waitForTimeout(80);
    assert.deepEqual(await status(page), ['alert-danger', 'pullRequest.error.newPullRequestForm|500,Err', '', '', '']); await page.close();
  } },
  { name: '제출: 변경이 없으면 알림창을 띄우고 제출을 막는다', run: async () => {
    const page = await open({ html: result(false, 0) });
    const prevented = await page.evaluate(() => { const e = $.Event('submit'); $('form.nm').trigger(e); return e.isDefaultPrevented(); });
    assert.equal(prevented, true); assert.deepEqual(await get(page, '__alerts'), ['pullRequest.diff.noChanges']); await page.close();
  } },
  { name: '제출: 충돌이 있으면 확인창을 띄우고, 두 번째 단추를 누르면 다시 제출한다', run: async () => {
    const page = await open({ html: result(true, 2) });
    const prevented = await page.evaluate(() => { const e = $.Event('submit'); $('form.nm').trigger(e); return e.isDefaultPrevented(); });
    assert.equal(prevented, true); assert.equal((await get(page, '__confirms'))[0].m, 'pullRequest.ignore.conflict');
    const resubmitted = await page.evaluate(() => { let n = 0; $('form.nm').on('submit', (e) => { n++; e.preventDefault(); }); window.__confirms[0].cb({ nButtonIndex: 0 }); const a = n; window.__confirms[0].cb({ nButtonIndex: 1 }); return [a, n]; });
    assert.deepEqual(resubmitted, [0, 1]); await page.close();
  } },
  { name: '제출: 제목이 비어 있으면 필수 입력 알림창을 띄우고, 모두 있으면 제출을 막지 않는다', run: async () => {
    const page = await open({ html: result(false, 2, '', '') });
    let prevented = await page.evaluate(() => { const e = $.Event('submit'); $('form.nm').trigger(e); return e.isDefaultPrevented(); });
    assert.equal(prevented, true); assert.deepEqual(await get(page, '__alerts'), ['pullRequest.title.required']);
    prevented = await page.evaluate(() => { $('#title').val('제목'); const e = $.Event('submit'); $('form.nm').on('submit', (ev) => ev.preventDefault()).trigger(e); return e.isDefaultPrevented(); });
    assert.equal((await get(page, '__alerts')).length, 1); await page.close();
  } },
  { name: '커밋 메시지: "더" 단추를 누르면 바로 뒤 메시지의 hidden이 토글된다', run: async () => {
    const page = await open({ html: result(false, 1) });
    await page.evaluate(() => { $('button.moreBtn').trigger('click'); }); assert.equal(await page.evaluate(() => $('pre.commitMsg').hasClass('hidden')), false);
    await page.evaluate(() => { $('button.moreBtn').trigger('click'); }); assert.equal(await page.evaluate(() => $('pre.commitMsg').hasClass('hidden')), true); await page.close();
  } },
];
