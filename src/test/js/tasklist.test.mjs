// 체크리스트(`esm/Tasklist.js`), 댓글 삭제와 수정 토글(`esm/Comment.js`), 하위 작업 쓰기(`esm/Subtask.js`), 하위 탭 활성 표시(`esm/Tabs.js`)를 고정한다(시험 채우기, D-123).
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const MSG = { content: 'window.Messages = function (k) { return k; };' };
async function base(html, file, init) {
  const page = await pageWith({ stubs: false, html, scripts: [MSG, 'common/yona.Common.js'] });
  await page.evaluate(() => {
    window.__ajax = []; window.__alerts = []; window.__notes = []; window.__np = []; window.__req = []; window.__modal = []; window.__pop = 0; window.__auto = 0; window.__saved = [];
    window.NProgress = { start: () => window.__np.push('start'), done: () => window.__np.push('done') };
    $yona.showAlert = (m) => window.__alerts.push(m); $yona.notify = (m) => window.__notes.push([m]);
    window.temporarySaveHandler = (el, flag) => window.__saved.push([el.length, flag]);
    window.autosize = { update: () => { window.__auto++; } };
    window.bootstrap = { Modal: { getOrCreateInstance: (el) => ({ show: () => window.__modal.push(['show', el.id]) }) }, Popover: { getOrCreateInstance: () => { window.__pop++; } } };
    yona.Request = { send: (m, u) => window.__req.push([m, u]) };
    yona.http = { ajax: (o) => { window.__ajax.push(o); const h = { done(cb) { if (window.__ok) setTimeout(() => cb(window.__ok), 0); return h; }, fail(cb) { if (window.__fail) setTimeout(() => cb(window.__fail), 0); return h; } }; return h; } };
  });
  await page.addScriptTag({ content: classicFromEsm(file) });
  if (init) await page.evaluate(init);
  return page;
}
const get = (page, k) => page.evaluate((k) => window[k], k);

const TASKS = (allowed) => `<div class="holder"><div class="edit"><form action="/patch/1"><textarea>- [ ] one\n- [x] two\n- [ ] three</textarea></form></div>
<div id="body1"><div class="tasklist"><span class="task-title"><span class="done-counter"></span></span><div class="bar red" style="width:0"></div></div>
<div class="markdown-wrap" data-allowed-update="${allowed}"><ul><li><input type="checkbox"><span>one</span></li><li><input type="checkbox" checked><span>two</span></li><li><input type="checkbox"><span>three</span></li></ul></div></div></div>`;
const info = (page) => page.evaluate(() => [$('.done-counter').text(), $('.bar').attr('class'), $('.tasklist').hasClass('task-show'), $('textarea').val(), $('input[type=checkbox]').map((i, e) => e.disabled).get()]);

export const tests = [
  { name: '체크리스트: 처음에 "(완료/전체)" 글자와 진행 막대 색을 채우고 tasklist를 보인다. 전부 끝나면 초록이다', run: async () => {
    const page = await base(TASKS(true), 'esm/Tasklist.js');
    let [counter, bar, show] = await info(page); assert.deepEqual([counter, bar, show], ['(1/3)', 'bar red', true]);
    await page.evaluate(() => { window.__ok = {}; }); await page.click('input[type=checkbox] >> nth=0'); await page.click('input[type=checkbox] >> nth=2'); await page.waitForTimeout(80);
    [counter, bar] = await info(page); assert.deepEqual([counter, bar], ['(3/3)', 'bar green']); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '체크리스트: data-allowed-update가 true가 아니면 체크 상자를 모두 비활성화한다', run: async () => {
    let page = await base(TASKS(false), 'esm/Tasklist.js'); assert.deepEqual((await info(page))[4], [true, true, true]); await page.close();
    page = await base(TASKS(true), 'esm/Tasklist.js'); assert.deepEqual((await info(page))[4], [false, false, false]); await page.close();
  } },
  { name: '체크리스트: 상자를 누르면 원문 textarea의 해당 줄이 [x]로 바뀌고 PATCH(JSON: content, original)로 보낸 뒤 개수를 갱신한다', run: async () => {
    const page = await base(TASKS(true), 'esm/Tasklist.js');
    await page.evaluate(() => { window.__ok = {}; }); await page.click('input[type=checkbox] >> nth=0'); await page.waitForTimeout(60);
    const o = (await get(page, '__ajax'))[0]; assert.deepEqual([o.method, o.url, o.contentType], ['PATCH', '/patch/1', 'application/json']);
    assert.deepEqual(JSON.parse(o.data), { content: '- [x] one\n- [x] two\n- [ ] three', original: '- [ ] one\n- [x] two\n- [ ] three' });
    assert.deepEqual(await get(page, '__np'), ['done']); // beforeSend는 http가 부르지 않는 가짜라 start가 없다
    assert.equal((await info(page))[0], '(2/3)'); await page.close();
  } },
  { name: '체크리스트: 체크를 해제하면 [ ]로 되돌리고, 실패하면 서버 메시지와 "Refresh the page!" 안내를 알림으로 띄운다', run: async () => {
    const page = await base(TASKS(true), 'esm/Tasklist.js');
    await page.evaluate(() => { window.__fail = { statusText: 'Conflict', responseText: '{"message":"changed"}' }; }); await page.click('input[type=checkbox] >> nth=1'); await page.waitForTimeout(60);
    assert.equal(JSON.parse((await get(page, '__ajax'))[0].data).content, '- [ ] one\n- [ ] two\n- [ ] three');
    assert.deepEqual(await get(page, '__alerts'), ['[Conflict] changed\n\nRefresh the page!']); assert.deepEqual(await get(page, '__np'), ['done']); await page.close();
  } },

  { name: '댓글: 삭제 단추는 요청 주소를 기억하고 모달을 연다. 확인 단추는 그 주소로 delete 요청을 보낸다', run: async () => {
    const page = await base(`<div id="comments"><button data-toggle="comment-delete" data-request-uri="/c/1">삭제</button></div><div id="comment-delete-modal"></div><button id="comment-delete-confirm"></button>`, 'esm/Comment.js', () => { yona.Comment.init({}); });
    await page.evaluate(() => { $('[data-toggle=comment-delete]').trigger('click'); const e = $.Event('click'); $('#comment-delete-confirm').trigger(e); window.__prev = e.isDefaultPrevented(); });
    assert.deepEqual(await get(page, '__modal'), [['show', 'comment-delete-modal']]); assert.deepEqual(await get(page, '__req'), [['delete', '/c/1']]); assert.equal(await get(page, '__prev'), true); await page.close();
  } },
  { name: '댓글: 수정 단추와 취소 단추는 수정 폼과 본문을 서로 바꿔 보이고, 새 댓글 칸을 숨기며 autosize를 갱신한다. 수정 폼마다 임시 저장을 건다', run: async () => {
    const page = await base(`<div id="comments"><button data-toggle="comment-edit" data-comment-id="5">수정</button><div id="comment-editform-5" style="display:none"><textarea></textarea><button class="ybtn-cancel" data-comment-id="5">취소</button></div><div id="comment-body-5">본문</div></div><div class="add-a-comment">c</div><div data-bs-toggle="popover"></div>`, 'esm/Comment.js', () => { yona.Comment.init({}); });
    const vis = () => page.evaluate(() => [$('#comment-editform-5').css('display'), $('#comment-body-5').css('display'), $('.add-a-comment').css('display')]);
    assert.deepEqual(await get(page, '__saved'), [[1, false]]);
    await page.evaluate(() => { $('[data-toggle=comment-edit]').trigger('click'); }); assert.deepEqual(await vis(), ['block', 'none', 'none']);
    await page.evaluate(() => { $('.ybtn-cancel').trigger('click'); }); assert.deepEqual((await vis()).slice(0, 2), ['none', 'block']); assert.equal(await get(page, '__auto'), 2); assert.equal(await get(page, '__pop'), 2); await page.close();
  } },

  { name: '하위 작업: 메시지를 누르면 하위 작업 영역이 토글되고 option-on이 붙고 떨어지며, 안의 select의 disabled가 뒤집힌다', run: async () => {
    const page = await base(`<a class="subtask-message">sub</a><div class="subtask-wrap" style="display:none"><select disabled></select></div><select id="targetProjectId"><option value="1">p1</option><option value="2">p2</option></select><select id="parentId"><option value="0">none</option><option value="9">i</option></select>`, 'esm/Subtask.js');
    await page.evaluate(() => { $('.subtask-message').trigger('click'); });
    assert.deepEqual(await page.evaluate(() => [$('.subtask-wrap').css('display'), $('.subtask-message').hasClass('option-on'), $('.subtask-wrap select')[0].disabled]), ['block', true, false]);
    await page.evaluate(() => { $('.subtask-message').trigger('click'); });
    assert.deepEqual(await page.evaluate(() => [$('.subtask-wrap').css('display'), $('.subtask-message').hasClass('option-on'), $('.subtask-wrap select')[0].disabled]), ['none', false, true]); await page.close();
  } },
  { name: '하위 작업: 처음과 다른 프로젝트를 고르면 부모 이슈를 첫 값으로 되돌리고 비활성화하며 이동 알림을 띄운다. 처음 프로젝트로 돌아오면 다시 활성화한다', run: async () => {
    const page = await base(`<select id="targetProjectId"><option value="1">p1</option><option value="2">p2</option></select><select id="parentId"><option value="0">none</option><option value="9">i</option></select>`, 'esm/Subtask.js');
    await page.evaluate(() => { $('#parentId').val('9'); $('#targetProjectId').val('2').trigger('change'); });
    assert.deepEqual(await page.evaluate(() => [$('#parentId').val(), $('#parentId').prop('disabled')]), ['0', true]); assert.deepEqual(await get(page, '__notes'), [["Issue will be moved or written to 'p2'"]]);
    await page.evaluate(() => { $('#targetProjectId').val('1').trigger('change'); }); assert.equal(await page.evaluate(() => $('#parentId').prop('disabled')), false); await page.close();
  } },

  { name: '탭: 하위 탭이 보여지면 그 탭의 li에 active를 붙이고 형제의 active는 뗀다', run: async () => {
    const page = await base(`<ul class="nav-subtab"><li class="active"><a id="a" data-bs-toggle="tab" href="#x">a</a></li><li><a id="b" data-bs-toggle="tab" href="#y">b</a></li></ul>`, 'esm/Tabs.js');
    await page.evaluate(() => { $('#b').trigger('shown.bs.tab'); });
    assert.deepEqual(await page.evaluate(() => $('li').map((i, e) => e.classList.contains('active')).get()), [false, true]); await page.close();
  } },
];
