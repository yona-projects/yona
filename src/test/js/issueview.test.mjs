// 이슈 보기(`esm/service/issue.View.js`): 관심 등록 토글, 오른쪽 정보창 변경 요청(담당자, 라벨, 마감일), 타임라인 갱신, 댓글과 다음 상태 단추를 고정한다(시험 채우기, D-123).
// 서버 요청, 첨부, 마크다운, 알림은 가짜로 바꿔 호출을 기록한다.
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `
<div class="issue-info"><span class="duedate-status"></span>
  <select data-toggle="select2" name="assigneeId"><option value="">-</option><option value="7">kim</option></select>
  <select data-toggle="select2" name="labelIds" multiple><option value="1">a</option></select>
  <input data-toggle="calendar" id="issueDueDate" name="dueDate" value="">
</div>
<button id="watch-button" type="button">관심</button>
<div id="timeline"><div class="timeline-list"><ul class="comments"><li>c1</li></ul></div><button class="vote" data-request-type="comment-vote" data-request-uri="/vote/1">추천</button></div>
<div id="upload"></div>
<form id="comment-form"><textarea data-editor-mode="comment-body"></textarea></form>
<button id="dynamic-comment-btn" class="hidden" type="button"></button>`;

async function open({ ajax = {}, timelineHtml = null, period = 60000 } = {}) {
  const page = await pageWith({ stubs: false, html: HTML, scripts: [{ content: 'window.Messages = function (k) { return k + (arguments.length > 1 ? "|" + [].slice.call(arguments, 1).join(",") : ""); };' }, 'common/yona.Common.js'] });
  await page.evaluate(({ ajax, timelineHtml }) => {
    window.__ajax = []; window.__posts = []; window.__gets = []; window.__notes = []; window.__shortcuts = [];
    window.moment = (s) => ({ isValid: () => !isNaN(Date.parse(s)) });
    $yona.notify = (m) => window.__notes.push(m);
    yona.Files = { getUploader: () => null }; yona.Attachments = function () {};
    yona.Markdown = { enableMarkdown() {} }; yona.Request = { bind() {} };
    yona.ShortcutKey = { attach: (k, cb) => window.__shortcuts.push([k, cb]) };
    yona.ui = yona.ui || {}; yona.ui.Select2 = { selectionChanges: () => ({ added: [{ id: 5 }, { id: 6 }], removed: { id: 2 } }) };
    yona.http = {
      ajax: (url, o) => { window.__ajax.push([url, o.method, o.data, o.dataType]); const h = { done(cb) { if (ajax.ok) setTimeout(() => cb(ajax.ok), 0); return h; }, fail(cb) { if (ajax.fail) setTimeout(() => cb(ajax.fail), 0); return h; } }; if (ajax.error && o.error) setTimeout(() => o.error(ajax.error), 0); return h; },
      post: (url, cb) => { window.__posts.push(url); setTimeout(cb, 0); return {}; },
      get: (url, cb) => { window.__gets.push(url); const h = { always(f) { setTimeout(() => { if (timelineHtml !== null) cb(timelineHtml); f(); }, 0); return h; } }; return h; },
    };
  }, { ajax, timelineHtml });
  await page.addScriptTag({ content: classicFromEsm('esm/service/issue.View.js') });
  await page.evaluate((period) => { new yona.issue.View({ issueId: 42, nextState: 'closed', timelineUpdatePeriod: period, urls: { massUpdate: '/mass', watch: '/w', unwatch: '/uw', timeline: '/tl', nextState: '/ns' } }); }, period);
  return page;
}
const get = (page, k) => page.evaluate((k) => window[k], k);

export const tests = [
  { name: '관심 등록: 처음 누르면 watch 주소로, 다시 누르면 unwatch 주소로 POST하고 단추 글자와 상태가 바뀐다', run: async () => {
    const page = await open();
    await page.evaluate(() => { $('#watch-button').trigger('click'); }); await page.waitForTimeout(40);
    assert.deepEqual(await get(page, '__posts'), ['/w']); assert.equal(await page.locator('#watch-button').innerText(), 'issue.unwatch'); assert.equal(await page.evaluate(() => $('#watch-button').hasClass('ybtn-watching')), true);
    await page.evaluate(() => { $('#watch-button').trigger('click'); }); await page.waitForTimeout(40);
    assert.deepEqual(await get(page, '__posts'), ['/w', '/uw']); assert.equal(await page.locator('#watch-button').innerText(), 'issue.watch');
    assert.deepEqual(await get(page, '__notes'), ['issue.watch.start', 'issue.unwatch.start']); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '정보창: 담당자 선택을 바꾸면 이슈 번호와 함께 mass update 주소로 json POST하고 성공 알림을 띄운다', run: async () => {
    const page = await open({ ajax: { ok: {} } });
    await page.evaluate(() => { $('[name=assigneeId]').val('7').trigger('change'); }); await page.waitForTimeout(50);
    assert.deepEqual(await get(page, '__ajax'), [['/mass', 'post', { 'issues[0].id': 42, assigneeId: '7' }, 'json']]); assert.deepEqual(await get(page, '__notes'), ['issue.update.assigneeId']);
    assert.deepEqual(await get(page, '__gets'), ['/tl']); await page.close();
  } },
  { name: '정보창: 라벨 선택을 바꾸면 늘고 준 라벨 번호를 attaching, detaching으로 보낸다(배열은 번호 모음, 객체 하나는 한 개 배열)', run: async () => {
    const page = await open({ ajax: { ok: {} } });
    await page.evaluate(() => { $('[name=labelIds]').trigger('change'); }); await page.waitForTimeout(50);
    assert.deepEqual((await get(page, '__ajax'))[0][2], { 'issues[0].id': 42, attachingLabelIds: [5, 6], detachingLabelIds: [2] }); await page.close();
  } },
  { name: '정보창: 요청이 실패하면 상태 코드와 함께 실패 알림을 띄운다', run: async () => {
    const page = await open({ ajax: { fail: { status: 500, statusText: 'Err' } } });
    await page.evaluate(() => { $('[name=assigneeId]').val('7').trigger('change'); }); await page.waitForTimeout(50);
    assert.deepEqual(await get(page, '__notes'), ['error.failedTo|issue.update.assigneeId,500,Err']); await page.close();
  } },
  { name: '마감일: 올바른 날짜면 isDueDateChanged와 함께 보내고 상태 글자를 "(메시지)"로 바꾸며 기한이 지났으면 overdue를 붙인다', run: async () => {
    const page = await open({ ajax: { ok: { dueDateMsg: '3일 지남', isOverDue: true } } });
    await page.evaluate(() => { $('#issueDueDate').val('2020-01-02').trigger('change'); }); await page.waitForTimeout(50);
    assert.deepEqual((await get(page, '__ajax'))[0][2], { 'issues[0].id': 42, dueDate: '2020-01-02', isDueDateChanged: true });
    assert.equal(await page.locator('.duedate-status').innerText(), '(3일 지남)'); assert.equal(await page.evaluate(() => $('.duedate-status').hasClass('overdue')), true); await page.close();
  } },
  { name: '마감일: 날짜가 올바르지 않으면 알림만 띄우고 요청하지 않는다', run: async () => {
    const page = await open({ ajax: { ok: {} } });
    await page.evaluate(() => { $('#issueDueDate').val('not-a-date').trigger('change'); }); await page.waitForTimeout(40);
    assert.deepEqual(await get(page, '__notes'), ['issue.error.invalid.duedate']); assert.deepEqual(await get(page, '__ajax'), []); await page.close();
  } },
  { name: '타임라인: 새 HTML이 오면 0.5초 뒤에 목록을 바꾸고, 같은 HTML이면 바꾸지 않는다', run: async () => {
    const page = await open({ ajax: { ok: {} }, timelineHtml: '<ul class="comments"><li>c1</li><li>c2</li></ul>' });
    await page.evaluate(() => { $('[name=assigneeId]').val('7').trigger('change'); }); await page.waitForTimeout(200);
    assert.equal(await page.locator('.timeline-list li').count(), 1);
    await page.waitForTimeout(600); assert.equal(await page.locator('.timeline-list li').count(), 2); await page.close();
  } },
  { name: '댓글 추천: 요청 주소로 POST하고 실패하면 서버 문구를 알림으로 보인다', run: async () => {
    const page = await open({ ajax: { error: { responseText: 'vote.error' } } });
    await page.evaluate(() => { $('.vote').trigger('click'); }); await page.waitForTimeout(50);
    assert.deepEqual((await get(page, '__ajax'))[0].slice(0, 2), ['/vote/1', 'post']); assert.deepEqual(await get(page, '__notes'), ['vote.error']); await page.close();
  } },
  { name: '다음 상태 단추: 글자를 쓰면 "댓글과 다음 상태", 비우면 "다음 상태" 글자가 되고, 글이 있을 때 누르면 withStateTransition=true로 제출한다', run: async () => {
    const page = await open();
    assert.equal(await page.locator('#dynamic-comment-btn').innerText(), 'button.nextState.closed'); assert.equal(await page.evaluate(() => $('#dynamic-comment-btn').hasClass('hidden')), false);
    await page.evaluate(() => { $('textarea').val('내용').trigger('keyup'); }); assert.equal(await page.locator('#dynamic-comment-btn').innerText(), 'button.commentAndNextState.closed');
    await page.evaluate(() => { $('textarea').val('').trigger('keyup'); }); assert.equal(await page.locator('#dynamic-comment-btn').innerText(), 'button.nextState.closed');
    const sub = await page.evaluate(() => { window.__sub = null; $('#comment-form').on('submit', (e) => { e.preventDefault(); window.__sub = $('#comment-form [name=withStateTransition]').val(); }); $('textarea').val('내용'); $('#dynamic-comment-btn').trigger('click'); return window.__sub; });
    assert.equal(sub, 'true'); await page.close();
  } },
  { name: '단축키: CTRL+SHIFT+ENTER를 댓글 입력란에서 누르면 다음 상태 단추를 누른다', run: async () => {
    const page = await open();
    const r = await page.evaluate(() => { let n = 0; $('textarea').val('내용'); $('#comment-form').on('submit', (e) => { e.preventDefault(); }); $('#dynamic-comment-btn').on('click', () => n++); const [key, cb] = window.__shortcuts[0]; cb({ welTarget: $('textarea') }); cb({ welTarget: $('#upload') }); return [key, n]; });
    assert.deepEqual(r, ['CTRL+SHIFT+ENTER', 1]); await page.close();
  } },
];
