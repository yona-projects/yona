// 게시글 목록, 게시글 보기, 리뷰 목록, 리뷰 댓글 수정(`esm/service/board.List.js`, `board.View.js`, `review.List.js`, `review.CommentEdit.js`)을 고정한다(시험 채우기, D-123).
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const MSG = { content: 'window.Messages = function (k) { return k + (arguments.length > 1 ? "|" + [].slice.call(arguments, 1).join(",") : ""); };' };
async function base(html, file, init) {
  const page = await pageWith({ stubs: false, html, scripts: [MSG, 'common/yona.Common.js'] });
  await page.evaluate(() => {
    window.__alerts = []; window.__notes = []; window.__send = []; window.__ajax = []; window.__pg = [];
    window._initTwoColumnMode = () => { window.__two = true; };
    yona.Pagination = { update: (el, n) => window.__pg.push([el.attr('id'), n]) };
    yona.Files = { getUploader: () => null }; yona.Attachments = function (o) { window.__att = (window.__att || 0) + 1; };
    $yona.notify = (m) => window.__notes.push(m); $yona.sendForm = (o) => window.__send.push(o);
    yona.http = { ajax: (url, o) => { window.__ajax.push([url, o.method, o.dataType, o.data]); const h = { done(cb) { if (window.__ok) setTimeout(() => cb(window.__ok), 0); return h; }, fail(cb) { if (window.__fail) setTimeout(() => cb(window.__fail), 0); return h; } }; return h; } };
  });
  await page.addScriptTag({ content: classicFromEsm(file) });
  if (init) await page.evaluate(init);
  return page;
}
const get = (page, k) => page.evaluate((k) => window[k], k);

const BOARD_LIST = `<form id="option_form"><input name="orderBy"><input name="orderDir"><input name="pageNum"><input name="filter"><select data-search="labelIds" multiple><option value="3">a</option><option value="4">b</option></select></form>
<div id="pagination"><a pageNum="2">2</a></div>
<div class="post-list-wrap"><div class="post-item" id="p1"><a data-label-id="4" data-category-id="1" href="#">L</a></div></div><span class="title-prefix">[공지]</span>`;
const REVIEW_LIST = `<form id="sf"><input name="state"><input name="authorId"><input name="participantId"><input name="orderBy"><input name="orderDir"></form><div id="pagination"></div>
<div class="issue-list-wrap"><a data-toggle="filter" data-type="state" data-value="closed" href="#">s</a><a data-toggle="filter" data-type="authorId" data-value="7" href="#">a</a><a data-toggle="filter" data-type="participantId" data-value="9" href="#">p</a><a data-toggle="order" data-field="created" data-value="asc" href="#">o</a></div>`;
const BOARD_VIEW = `<button id="watch-button" data-watching="false" type="button">관심</button><div id="upload"></div><textarea data-editor-mode="comment-body"></textarea><div class="attachments"></div><div class="attachments"></div>`;
const REVIEW_EDIT = `<ul><li class="comment"><div class="meta-info"><span class="ago">1분 전</span></div><div class="comment-body">원문</div><textarea class="comment-raw">raw</textarea><button class="comment-edit" data-url="/rc/1">수정</button></li></ul>`;

export const tests = [
  { name: '게시글 목록: 쪽 번호를 누르면 pageNum을 넣고 폼을 제출하며, 쪽 표시를 갱신하고 2단 보기를 시작한다', run: async () => {
    const page = await base(BOARD_LIST, 'esm/service/board.List.js', () => { new yona.board.List({ nTotalPages: 5 }); });
    assert.deepEqual(await get(page, '__pg'), [['pagination', 5]]); assert.equal(await get(page, '__two'), true);
    const r = await page.evaluate(() => { let n; $('#option_form').on('submit', (e) => { n = $('[name=pageNum]').val(); e.preventDefault(); }); const e = $.Event('click'); $('#pagination a').trigger(e); return [n, e.isDefaultPrevented()]; });
    assert.deepEqual(r, ['2', true]); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '게시글 목록: 제목 머리말을 누르면 filter 입력에 그 글자를 넣고 폼을 제출한다', run: async () => {
    const page = await base(BOARD_LIST, 'esm/service/board.List.js', () => { new yona.board.List({ nTotalPages: 1 }); });
    const r = await page.evaluate(() => { let n; $('#option_form').on('submit', (e) => { n = $('[name=filter]').val(); e.preventDefault(); }); $('.title-prefix').trigger('click'); return n; });
    assert.equal(r, '[공지]'); await page.close();
  } },
  { name: '게시글 목록: 목록의 라벨을 누르면 다중 선택 라벨 필터에 그 라벨을 더하고 change를 일으킨다(링크 이동은 막는다)', run: async () => {
    const page = await base(BOARD_LIST, 'esm/service/board.List.js', () => { new yona.board.List({ nTotalPages: 1 }); });
    const r = await page.evaluate(() => { let changed = 0; $('[data-search=labelIds]').val(['3']).on('change', () => changed++); const e = $.Event('click'); $('a[data-label-id]').trigger(e); return [$('[data-search=labelIds]').val(), changed, e.isDefaultPrevented()]; });
    assert.deepEqual(r, [['3', '4'], 1, true]); await page.close();
  } },
  { name: '게시글 목록: 마우스를 올리면 배경이 #fafafa, 내리면 #fff가 된다', run: async () => {
    const page = await base(BOARD_LIST, 'esm/service/board.List.js', () => { new yona.board.List({ nTotalPages: 1 }); });
    const r = await page.evaluate(() => { const el = $('#p1'); el.trigger('mouseenter'); const a = el.css('background-color'); el.trigger('mouseleave'); return [a, el.css('background-color')]; });
    assert.deepEqual(r, ['rgb(250, 250, 250)', 'rgb(255, 255, 255)']); await page.close();
  } },

  { name: '게시글 보기: 관심 단추는 처음 watch 주소로 sendForm하고 성공하면 data-watching과 글자를 바꾼다. 다시 누르면 unwatch 주소다', run: async () => {
    const page = await base(BOARD_VIEW, 'esm/service/board.View.js', () => { new yona.board.View({ sWatchUrl: '/w', sUnwatchUrl: '/uw' }); });
    await page.evaluate(() => { $('#watch-button').trigger('click'); window.__send[0].fOnLoad(); });
    assert.equal((await get(page, '__send'))[0].sURL, '/w'); assert.equal(await page.locator('#watch-button').getAttribute('data-watching'), 'true'); assert.equal(await page.locator('#watch-button').innerText(), 'post.unwatch');
    await page.evaluate(() => { $('#watch-button').trigger('click'); window.__send[1].fOnLoad(); });
    assert.equal((await get(page, '__send'))[1].sURL, '/uw'); assert.equal(await page.locator('#watch-button').innerText(), 'post.watch'); assert.deepEqual(await get(page, '__notes'), ['post.watch.start', 'post.unwatch.start']); await page.close();
  } },
  { name: '게시글 보기: 첨부 목록마다 내려받기 처리를 붙이되 이미 붙은 곳은 건너뛴다', run: async () => {
    const page = await base(BOARD_VIEW.replace('<div class="attachments"></div><div', '<div class="attachments" id="a1"></div><div'), 'esm/service/board.View.js', () => { $('#a1').data('isYobiAttachment', true); new yona.board.View({}); });
    assert.equal(await get(page, '__att'), 1); await page.close();
  } },

  { name: '리뷰 목록: 필터 링크는 state, authorId, participantId 입력을 알맞게 채우고 폼을 제출한다(작성자 선택 시 참여자는 비운다)', run: async () => {
    const page = await base(REVIEW_LIST, 'esm/service/review.List.js', () => { new yona.review.List({ welSearchForm: $('#sf'), nTotalPages: 3 }); });
    assert.deepEqual(await get(page, '__pg'), [['pagination', 3]]);
    const r = await page.evaluate(() => { const out = []; $('#sf').on('submit', (e) => { e.preventDefault(); out.push($('#sf input').map((i, x) => x.name + '=' + x.value).get().join('&')); }); $('[data-type=state]').trigger('click'); $('[data-type=authorId]').trigger('click'); $('[data-type=participantId]').trigger('click'); return out; });
    assert.deepEqual(r, ['state=closed&authorId=&participantId=&orderBy=&orderDir=', 'state=closed&authorId=7&participantId=&orderBy=&orderDir=', 'state=closed&authorId=&participantId=9&orderBy=&orderDir=']); await page.close();
  } },
  { name: '리뷰 목록: 정렬 링크는 orderBy, orderDir을 채우고 폼을 제출한다. 쪽 수가 없으면 1쪽이다', run: async () => {
    const page = await base(REVIEW_LIST, 'esm/service/review.List.js', () => { new yona.review.List({ welSearchForm: $('#sf') }); });
    const r = await page.evaluate(() => { let o; $('#sf').on('submit', (e) => { e.preventDefault(); o = [$('[name=orderBy]').val(), $('[name=orderDir]').val()]; }); $('[data-toggle=order]').trigger('click'); return o; });
    assert.deepEqual(r, ['created', 'asc']); assert.deepEqual(await get(page, '__pg'), [['pagination', 1]]); await page.close();
  } },

  { name: '리뷰 댓글 수정: 수정 단추를 누르면 원문 textarea가 든 편집 폼이 열리고 본문은 숨겨진다. 취소하면 닫힌다. 이미 열려 있으면 또 열지 않는다', run: async () => {
    const page = await base(REVIEW_EDIT, 'esm/service/review.CommentEdit.js');
    await page.evaluate(() => { $('.comment-edit').trigger('click'); $('.comment-edit').trigger('click'); });
    assert.equal(await page.locator('.comment-edit-form').count(), 1); assert.equal(await page.locator('.comment-edit-text').inputValue(), 'raw'); assert.equal(await page.locator('.comment-body').isVisible(), false);
    assert.deepEqual(await page.evaluate(() => [$('.comment-edit-cancel').text(), $('.comment-edit-save').text()]), ['button.cancel', 'button.save']);
    await page.evaluate(() => { $('.comment-edit-cancel').trigger('click'); }); assert.equal(await page.locator('.comment-edit-form').count(), 0); assert.equal(await page.locator('.comment-body').isVisible(), true); await page.close();
  } },
  { name: '리뷰 댓글 수정: 빈 내용은 알림만 띄우고, 저장하면 POST로 contents를 보내 본문을 응답 HTML로 바꾸고 "(수정됨)"을 한 번만 붙인다', run: async () => {
    const page = await base(REVIEW_EDIT, 'esm/service/review.CommentEdit.js');
    await page.evaluate(() => { $('.comment-edit').trigger('click'); $('.comment-edit-text').val('  '); $('.comment-edit-save').trigger('click'); });
    assert.deepEqual(await get(page, '__notes'), ['post.comment.empty']); assert.deepEqual(await get(page, '__ajax'), []);
    await page.evaluate(() => { window.__ok = { bodyHtml: '<b>새</b>' }; $('.comment-edit-text').val('새 내용'); $('.comment-edit-save').trigger('click'); }); await page.waitForTimeout(60);
    assert.deepEqual((await get(page, '__ajax'))[0], ['/rc/1', 'POST', 'json', { contents: '새 내용' }]);
    assert.equal(await page.evaluate(() => [$('.comment-body').html(), $('textarea.comment-raw').val(), $('.comment-edited').length, $('.comment-edited').text(), $('.comment-edit-form').length].join('|')), '<b>새</b>|새 내용|1|(reviewComment.edited)|0');
    await page.evaluate(() => { $('.comment-edit').trigger('click'); $('.comment-edit-save').trigger('click'); }); await page.waitForTimeout(60);
    assert.equal(await page.locator('.comment-edited').count(), 1); await page.close();
  } },
  { name: '리뷰 댓글 수정: 저장 요청이 실패하면 상태와 함께 실패 알림을 띄우고 폼은 그대로 둔다', run: async () => {
    const page = await base(REVIEW_EDIT, 'esm/service/review.CommentEdit.js');
    await page.evaluate(() => { window.__fail = { status: 500, statusText: 'Err' }; $('.comment-edit').trigger('click'); $('.comment-edit-save').trigger('click'); }); await page.waitForTimeout(60);
    assert.deepEqual(await get(page, '__notes'), ['error.failedTo|common.comment.edit,500,Err'].map((s) => s)); assert.equal(await page.locator('.comment-edit-form').count(), 1); await page.close();
  } },
];
