// 임시 저장(`esm/service/temporarySaveHandler.js`)의 입력란별 타이머와 키, 모두 취소(D-124, 동작 차이 5번)를 고정한다.
// 예전에는 타이머가 페이지에 하나라서 5초 안에 다른 입력란에 치면 앞 입력란의 초안 저장이 사라졌다.
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `<div class="editor-notice-label"></div>
<textarea id="a" data-editor-mode="comment-body"></textarea>
<div id="comment-editform-11"><textarea id="e11" data-editor-mode="update-comment-body"></textarea></div>
<div id="comment-editform-12"><textarea id="e12" data-editor-mode="update-comment-body"></textarea></div>`;

async function open() {
  const page = await pageWith({ stubs: false, html: HTML, scripts: [{ content: 'window.Messages = function (k) { return k; };' }, 'common/yona.Common.js'] });
  await page.clock.install();
  await page.evaluate(() => { const m = new Map(); Object.defineProperty(window, 'localStorage', { value: { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)), removeItem: (k) => m.delete(k), key: (i) => [...m.keys()][i], get length() { return m.size; } } }); window.__ls = m; });
  // 모듈이 등록하는 setInterval을 기록해 둔다(1시간 취소 시험용)
  await page.evaluate(() => { window.__intervals = []; const real = window.setInterval; window.setInterval = function (fn, ms) { window.__intervals.push([fn, ms]); return real.apply(window, arguments); }; });
  await page.addScriptTag({ content: classicFromEsm('esm/service/temporarySaveHandler.js') });
  await page.evaluate(() => { temporarySaveHandler($('#a')); temporarySaveHandler($('#e11'), false); temporarySaveHandler($('#e12'), false); });
  return page;
}
const type = (page, id, text) => page.evaluate(([id, text]) => { $('#' + id).val(text).trigger('keyup'); }, [id, text]);
const stored = (page) => page.evaluate(() => Object.fromEntries(window.__ls));
const BASE = '/-last-comment-update-draft';

export const tests = [
  { name: '초안: 5초 안에 다른 입력란에 쳐도 앞 입력란의 초안이 사라지지 않고 각자 저장된다', run: async () => {
    const page = await open(); const path = await page.evaluate(() => location.pathname);
    await type(page, 'a', '새 댓글'); await page.clock.runFor(2000); await type(page, 'e11', '수정 11'); await page.clock.runFor(6000);
    const s = await stored(page);
    assert.equal(s[path], '새 댓글'); assert.equal(s[path + '-last-comment-update-draft-11'], '수정 11'); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '초안: 수정 댓글마다 다른 키에 저장하므로 서로 덮어쓰지 않는다', run: async () => {
    const page = await open(); const path = await page.evaluate(() => location.pathname);
    await type(page, 'e11', '열한째'); await type(page, 'e12', '열둘째'); await page.clock.runFor(6000);
    const s = await stored(page); assert.equal(s[path + '-last-comment-update-draft-11'], '열한째'); assert.equal(s[path + '-last-comment-update-draft-12'], '열둘째'); await page.close();
  } },
  { name: '초안: 같은 입력란에서 이어 치면 마지막 입력 5초 뒤에 한 번만 저장한다', run: async () => {
    const page = await open(); const path = await page.evaluate(() => location.pathname);
    await type(page, 'a', '가'); await page.clock.runFor(3000); await type(page, 'a', '가나'); await page.clock.runFor(3000);
    assert.equal((await stored(page))[path], undefined); await page.clock.runFor(3000); assert.equal((await stored(page))[path], '가나'); await page.close();
  } },
  { name: '초안: 입력란을 비우면 저장된 초안을 지우고 예약된 저장도 취소한다', run: async () => {
    const page = await open(); const path = await page.evaluate(() => location.pathname);
    await type(page, 'a', '가'); await page.clock.runFor(6000); assert.equal((await stored(page))[path], '가');
    await type(page, 'a', '가나'); await type(page, 'a', ''); await page.clock.runFor(6000); assert.equal((await stored(page))[path], undefined); await page.close();
  } },
  { name: '초안: cancelAllDraftTimers는 모든 입력란의 예약된 저장을 취소한다(댓글 제출 뒤 초안이 되살아나지 않게)', run: async () => {
    const page = await open(); const path = await page.evaluate(() => location.pathname);
    await type(page, 'a', '쓰는 중'); await type(page, 'e11', '수정 중'); await page.evaluate(() => cancelAllDraftTimers()); await page.clock.runFor(6000);
    assert.deepEqual(await stored(page), {}); await page.close();
  } },
  { name: '초안: 시스템이 1시간마다 예약된 초안 타이머를 취소한다', run: async () => {
    const page = await open();
    const info = await page.evaluate(() => window.__intervals.filter(([, ms]) => ms === 3600 * 1000).length); assert.equal(info, 1, '1시간 간격 등록이 하나여야 한다');
    await type(page, 'a', '쓰는 중'); await page.evaluate(() => { window.__intervals.find(([, ms]) => ms === 3600 * 1000)[0](); }); await page.clock.runFor(6000);
    assert.deepEqual(await stored(page), {}); await page.close();
  } },
  { name: '초안 삭제: removeCurrentPage...는 본문 키와 수정 댓글 키 전부를 지운다', run: async () => {
    const page = await open(); const path = await page.evaluate(() => location.pathname);
    await page.evaluate(([p]) => { __ls.set(p, 'x'); __ls.set(p + '-last-comment-update-draft', 'y'); __ls.set(p + '-last-comment-update-draft-11', 'z'); __ls.set('/other', 'keep'); removeCurrentPageTemprarySavedContent(); }, [path]);
    assert.deepEqual(await stored(page), { '/other': 'keep' }); await page.close();
  } },
  { name: '댓글 제출: CommentForm이 제출할 때 모든 입력란의 예약된 초안 저장을 취소한다', run: async () => {
    const page = await pageWith({ stubs: false, html: HTML.replace('<textarea id="a"', '<form id="comment-form"><textarea id="a"').replace('</textarea>\n<div id="comment-editform-11">', '</textarea></form>\n<div id="comment-editform-11">'), scripts: [{ content: 'window.Messages = function (k) { return k; };' }, 'common/yona.Common.js'] });
    await page.clock.install();
    await page.evaluate(() => { const m = new Map(); Object.defineProperty(window, 'localStorage', { value: { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)), removeItem: (k) => m.delete(k), key: (i) => [...m.keys()][i], get length() { return m.size; } } }); window.__ls = m; window.NProgress = { start() {}, done() {} }; $yona.notify = function () {}; });
    await page.addScriptTag({ content: classicFromEsm('esm/service/temporarySaveHandler.js') });
    await page.addScriptTag({ content: classicFromEsm('esm/CommentForm.js') });
    await page.evaluate(() => { temporarySaveHandler($('#e11'), false); $('#e11').val('수정 중').trigger('keyup'); $('#a').val('').closest('form').trigger('submit'); });
    await page.clock.runFor(6000); assert.deepEqual(await stored(page), {}, '제출 뒤에 초안이 저장되었다'); await page.close();
  } },
];
