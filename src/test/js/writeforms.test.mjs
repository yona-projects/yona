// 글쓰기 화면 3종(`esm/service/issue.Write.js`, `board.Write.js`, `milestone.Write.js`): 제목 Enter 이동, 제출 검증, 임시 저장 정리, 떠날 때 경고, 마일스톤 새로고침을 고정한다(시험 채우기, D-123).
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const ISSUE = `<form id="f"><input id="title"><textarea id="body"></textarea><input id="issueDueDate"></form><div id="upload"></div><div id="options"><a class="icon-refresh">r</a></div><div id="milestoneOption">old</div><script id="tplAttachedFile" type="text/x-tpl">TPL</script>`;
const BOARD = `<form><input id="title"><textarea id="body"></textarea></form><div id="upload"></div>`;
const MILE = `<form id="milestone-form"><input id="title"><textarea data-editor-mode="content-body"></textarea><input id="due"></form><div id="upload"></div><div id="dp"></div>`;

async function open(html, file, ctor, options, { extra = '' } = {}) {
  const page = await pageWith({ stubs: false, html, scripts: [{ content: 'window.Messages = function (k) { return k; };' }, 'common/yona.Common.js'] });
  await page.evaluate((extra) => {
    window.__alerts = []; window.__notes = []; window.__cleared = 0; window.__saved = 0; window.__dd = [];
    window.temporarySaveHandler = () => { window.__saved++; }; window.removeCurrentPageTemprarySavedContent = () => { window.__cleared++; };
    yona.Files = { getUploader: () => null }; yona.Attachments = function () {};
    yona.ui = yona.ui || {}; yona.ui.Dropdown = function (o) { window.__dd.push(o.elContainer); };
    window.moment = (s) => ({ isValid: () => !isNaN(Date.parse(s)) });
    $yona.alert = (m, cb) => { window.__alerts.push(m); if (cb) cb(); }; $yona.showAlert = $yona.alert; $yona.notify = (m) => window.__notes.push(m);
    yona.http = { get: (url, cb) => { window.__got = url; setTimeout(() => cb('<!DOCTYPE html><html><body><div class="wrap"><div id="milestoneOption">new</div></div></body></html>'), 0); return {}; } };
    eval(extra);
  }, extra);
  await page.addScriptTag({ content: classicFromEsm(file) });
  await page.evaluate(([ctor, options]) => { new (ctor.split('.').reduce((o, k) => o[k], window))(options); }, [ctor, options]);
  return page;
}
const get = (page, k) => page.evaluate((k) => window[k], k);
const submit = (page, sel = 'form') => page.evaluate((sel) => { const e = $.Event('submit'); $(sel).on('submit', (ev) => ev.preventDefault()); $(sel).trigger(e); return e.isDefaultPrevented(); }, sel);
// 제출 처리기가 false를 돌려주는지 확인하려면 jQuery가 기본 동작을 막았는지 보면 된다(처리기가 false를 돌려주면 막는다).
const submitResult = (page, sel = 'form') => page.evaluate((sel) => { let native = false; $(sel).one('submit.last', (e) => { native = !e.isDefaultPrevented(); e.preventDefault(); }); $(sel).trigger('submit'); return native; }, sel);

const common = (name, file, ctor, html, opts, titleSel) => [
  { name: `${name}: 시작하면 제목에 포커스하고, 제목에서 Enter를 누르면 본문으로 포커스를 옮기며 임시 저장을 건다`, run: async () => {
    const page = await open(html, file, ctor, opts);
    assert.equal(await page.evaluate(() => document.activeElement.id), 'title'); assert.equal(await get(page, '__saved'), 1);
    const prevented = await page.evaluate(() => { const e = $.Event('keydown', { keyCode: 13, which: 13, key: 'Enter' }); $('#title').trigger(e); return [e.isDefaultPrevented(), document.activeElement.tagName]; });
    assert.deepEqual(prevented, [true, 'TEXTAREA']); assert.deepEqual(page.errors, []); await page.close();
  } },
];

export const tests = [
  ...common('이슈 작성', 'esm/service/issue.Write.js', 'yona.issue.Write', ISSUE, {}),
  ...common('게시글 작성', 'esm/service/board.Write.js', 'yona.board.Write', BOARD, {}),
  ...common('마일스톤 작성', 'esm/service/milestone.Write.js', 'yona.milestone.Write', MILE, { elDatePicker: '#dp', elDueDate: '#due' }),

  { name: '이슈 작성: 제목이 비면 알림창을 띄우고 제목에 포커스한 채 제출을 막는다. 임시 저장은 지우지 않는다', run: async () => {
    const page = await open(ISSUE, 'esm/service/issue.Write.js', 'yona.issue.Write', {});
    await page.evaluate(() => { $('#body').trigger('focus'); document.getElementById('body').blur(); });
    assert.equal(await submitResult(page), false); assert.deepEqual(await get(page, '__alerts'), ['issue.error.emptyTitle']); assert.equal(await get(page, '__cleared'), 0); assert.equal(await page.evaluate(() => document.activeElement.id), 'title'); await page.close();
  } },
  { name: '이슈 작성: 마감일 형식이 틀리면 알림만 띄우고 막으며, 제목이 있고 마감일이 올바르면 임시 저장을 지우고 통과시킨다', run: async () => {
    const page = await open(ISSUE, 'esm/service/issue.Write.js', 'yona.issue.Write', {});
    await page.evaluate(() => { $('#title').val('제목'); $('#issueDueDate').val('zzz'); });
    assert.equal(await submitResult(page), false); assert.deepEqual(await get(page, '__notes'), ['issue.error.invalid.duedate']);
    await page.evaluate(() => { $('#issueDueDate').val('2030-01-01'); });
    assert.equal(await submitResult(page), true); assert.equal(await get(page, '__cleared'), 1); await page.close();
  } },
  { name: '이슈 작성: 본문에 포커스한 뒤 내용이 있으면 떠날 때 경고 문구를 돌려주고, 제출하면 그 경고를 뗀다', run: async () => {
    const page = await open(ISSUE, 'esm/service/issue.Write.js', 'yona.issue.Write', {});
    const r = await page.evaluate(() => { $('#body').trigger('focus'); $('#body').val('내용'); const e = $.Event('beforeunload'); $(window).trigger(e); const warned = e.result; $('#title').val('t'); $('form').on('submit', (ev) => ev.preventDefault()).trigger('submit'); const e2 = $.Event('beforeunload'); $(window).trigger(e2); return [warned, e2.result]; });
    assert.deepEqual(r, ['issue.error.beforeunload', undefined]); await page.close();
  } },
  { name: '이슈 작성: 마일스톤 새로고침 단추를 누르면 폼 주소를 읽어 #milestoneOption 안쪽을 바꾸고 드롭다운을 다시 만든다', run: async () => {
    const page = await open(ISSUE, 'esm/service/issue.Write.js', 'yona.issue.Write', { sIssueFormURL: '/issueform' });
    await page.evaluate(() => { $('.icon-refresh').trigger('click'); }); await page.waitForTimeout(60);
    assert.equal(await get(page, '__got'), '/issueform'); assert.equal(await page.evaluate(() => $('#milestoneOption').html()), 'new'); assert.deepEqual(await get(page, '__dd'), ['#milestoneId']); await page.close();
  } },

  { name: '게시글 작성: 제목이 비면 알림창을 띄우고 제출을 막으며, 있으면 임시 저장을 지우고 통과시킨다', run: async () => {
    const page = await open(BOARD, 'esm/service/board.Write.js', 'yona.board.Write', {});
    assert.equal(await submitResult(page), false); assert.deepEqual(await get(page, '__alerts'), ['post.error.emptyTitle']); assert.equal(await get(page, '__cleared'), 0);
    await page.evaluate(() => { $('#title').val('제목'); }); assert.equal(await submitResult(page), true); assert.equal(await get(page, '__cleared'), 1); await page.close();
  } },
  { name: '게시글 작성: 본문에 포커스한 뒤 내용이 있으면 떠날 때 경고 문구를 돌려준다', run: async () => {
    const page = await open(BOARD, 'esm/service/board.Write.js', 'yona.board.Write', {});
    const r = await page.evaluate(() => { $('#body').trigger('focus'); $('#body').val('내용'); const e = $.Event('beforeunload'); $(window).trigger(e); return e.result; });
    assert.equal(r, 'post.error.beforeunload'); await page.close();
  } },

  { name: '마일스톤 작성: 제목, 내용이 비면 각각 알림을 띄우고 막는다. 마감일은 YYYY-MM-DD 모양이 아니면 막는다', run: async () => {
    const page = await open(MILE, 'esm/service/milestone.Write.js', 'yona.milestone.Write', { elDatePicker: '#dp', elDueDate: '#due' });
    assert.equal(await submitResult(page, '#milestone-form'), false);
    await page.evaluate(() => { $('#title').val('제목'); }); assert.equal(await submitResult(page, '#milestone-form'), false);
    await page.evaluate(() => { $('textarea').val('내용'); $('#due').val('2030/01/01'); }); assert.equal(await submitResult(page, '#milestone-form'), false);
    assert.deepEqual(await get(page, '__alerts'), ['milestone.error.title', 'milestone.error.content', 'milestone.error.duedateFormat']); await page.close();
  } },
  { name: '마일스톤 작성: 모두 올바르면 통과하고(제출 때마다 임시 저장 정리는 검증 전에 호출된다)', run: async () => {
    const page = await open(MILE, 'esm/service/milestone.Write.js', 'yona.milestone.Write', { elDatePicker: '#dp', elDueDate: '#due' });
    await page.evaluate(() => { $('#title').val('제목'); $('textarea').val('내용'); $('#due').val('2030-01-01'); });
    assert.equal(await submitResult(page, '#milestone-form'), true); assert.equal(await get(page, '__cleared'), 1); await page.close();
  } },
];
