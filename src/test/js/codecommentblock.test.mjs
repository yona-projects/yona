// 코드 리뷰 블록 선택(`esm/CodeCommentBlock.js`): 드래그로 고른 범위를 줄/열/면 정보로 바꾸고, 범위를 감싸는 표시(review-block)와 팝 단추 표시/숨김을 고정한다(시험 채우기, D-123).
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `<div id="c"><table class="diff-container" data-file-path="a.txt" data-path-a="pa" data-path-b="pb" data-commit-a="c1" data-commit-b="c2"><tbody>
<tr id="r1" data-line="1" data-type="add" data-side="B"><td class="code"><pre>hello world</pre></td></tr>
<tr id="r2" data-line="2" data-type="remove" data-side="A"><td class="code"><pre>second line</pre></td></tr>
<tr class="comments"><td>코멘트</td></tr>
<tr id="r3" data-line="3" data-type="add" data-side="B"><td class="code"><pre>third</pre></td></tr></tbody></table></div>
<div id="pop" style="display:none;position:absolute;width:20px">pop</div><div id="outside">밖</div>`;

async function open(options = {}) {
  const page = await pageWith({ stubs: false, html: HTML, scripts: [{ content: 'window.Messages = function (k) { return k; };' }, 'common/yona.Common.js'] });
  await page.addScriptTag({ content: classicFromEsm('esm/CodeCommentBlock.js') });
  await page.evaluate((o) => { yona.CodeCommentBlock.init(Object.assign({ welContainer: '#c', welPopButtonOnBlock: '#pop' }, o)); }, options);
  return page;
}
/** 줄 id와 글자 위치로 선택 범위를 만들고 컨테이너에서 mouseup을 일으킨다. 선택을 시작한 줄에서 mousedown도 먼저 보낸다. */
const select = (page, [fromRow, from], [toRow, to], downRow = fromRow) => page.evaluate(([[fr, f], [tr, t], down]) => {
  const node = (id) => document.querySelector(`#${id} pre`).firstChild;
  $(`#${down} pre`).trigger($.Event('mousedown', { button: 0 }));
  const r = document.createRange(); r.setStart(node(fr), f); r.setEnd(node(tr), t);
  const s = getSelection(); s.removeAllRanges(); s.addRange(r); $('#c').trigger('mouseup');
}, [[fromRow, from], [toRow, to], downRow]);
const data = (page) => page.evaluate(() => yona.CodeCommentBlock.getData());

export const tests = [
  { name: '선택: 한 줄 안의 범위는 줄 번호, 면(추가는 B), 열, 표의 경로와 커밋 정보로 바뀌고 팝 단추가 보인다', run: async () => {
    const page = await open(); await select(page, ['r1', 0], ['r1', 5]);
    assert.deepEqual(await data(page), { bIsReversed: false, nStartLine: 1, sStartType: 'add', sStartSide: 'B', nStartColumn: 0, nEndLine: 1, sEndType: 'add', sEndSide: 'B', nEndColumn: 5, sPathA: 'pa', sPathB: 'pb', sPrevCommitId: 'c1', sCommitId: 'c2', sFilePath: 'a.txt', sPath: 'a.txt' });
    assert.equal(await page.evaluate(() => $('#pop').css('display')), 'block'); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '선택: 여러 줄에 걸치면 끝 줄 정보를 쓰고, 삭제 줄은 면이 A이다. 사이의 댓글 줄은 허용한다', run: async () => {
    const page = await open(); await select(page, ['r1', 6], ['r3', 3]);
    const d = await data(page); assert.deepEqual([d.nStartLine, d.nEndLine, d.nStartColumn, d.nEndColumn, d.sStartSide, d.sEndSide], [1, 3, 6, 3, 'B', 'B']);
    await select(page, ['r2', 1], ['r2', 4]); assert.deepEqual([(await data(page)).sStartSide, (await data(page)).sEndSide, (await data(page)).sStartType], ['A', 'A', 'remove']); await page.close();
  } },
  { name: '선택: 거꾸로 선택하면(끝에서 시작 쪽으로) bIsReversed가 참이다', run: async () => {
    const page = await open(); await select(page, ['r1', 2], ['r3', 3], 'r3');
    // 범위 객체는 항상 앞에서 뒤로이므로, 드래그를 시작한 줄(mousedown)이 범위의 끝쪽 줄이면 거꾸로 선택으로 본다
    assert.equal((await data(page)).bIsReversed, true); await page.close();
  } },
  { name: '선택: 줄 번호가 없는 곳이나 코드가 없는 일반 행을 가로지르는 범위는 무시하고 팝 단추를 보이지 않는다', run: async () => {
    const page = await open();
    await page.evaluate(() => { $('#r2').after('<tr id="rx"><td>코드 아닌 행</td></tr>'); $('#r1').after('<tr id="rx2"><td>x</td></tr>'); });
    await select(page, ['r1', 0], ['r3', 2]); assert.equal(await page.evaluate(() => $('#pop').css('display')), 'none');
    await page.evaluate(() => { const r = document.createRange(); r.selectNodeContents(document.getElementById('outside')); const s = getSelection(); s.removeAllRanges(); s.addRange(r); $('#c').trigger('mouseup'); });
    assert.equal(await page.evaluate(() => $('#pop').css('display')), 'none'); await page.close();
  } },
  { name: '선택: bPopButtonOnBlock이 false면 정보는 얻되 팝 단추는 보이지 않는다', run: async () => {
    const page = await open({ bPopButtonOnBlock: false }); await select(page, ['r1', 0], ['r1', 5]);
    assert.equal((await data(page)).nEndColumn, 5); assert.equal(await page.evaluate(() => $('#pop').css('display')), 'none'); await page.close();
  } },
  { name: '선택: 선택이 풀리면 50ms 안에 팝 단추를 숨긴다', run: async () => {
    const page = await open(); await select(page, ['r1', 0], ['r1', 5]);
    await page.evaluate(() => getSelection().removeAllRanges()); await page.waitForTimeout(200); assert.equal(await page.evaluate(() => $('#pop').css('display')), 'none'); await page.close();
  } },
  { name: '표시: block(범위)은 한 줄이면 그 열 범위를, 여러 줄이면 처음 줄 일부, 중간 줄 전체, 끝 줄 일부를 review-block으로 감싼다', run: async () => {
    const page = await open();
    await page.evaluate(() => yona.CodeCommentBlock.block({ nStartLine: 1, sStartSide: 'B', nStartColumn: 0, nEndLine: 1, sEndSide: 'B', nEndColumn: 5, sPath: 'a.txt' }));
    assert.deepEqual(await page.evaluate(() => $('[data-toggle=comment-block]').map((i, e) => e.textContent).get()), ['hello']);
    await page.evaluate(() => yona.CodeCommentBlock.block({ nStartLine: 1, sStartSide: 'B', nStartColumn: 6, nEndLine: 3, sEndSide: 'B', nEndColumn: 3, sPath: 'a.txt' }));
    assert.deepEqual(await page.evaluate(() => $('span.review-block').map((i, e) => e.textContent).get()), ['world', 'second line', 'thi']); await page.close();
  } },
  { name: '표시: 없는 줄을 가리키면 아무것도 감싸지 않는다. unblock은 감싼 표시를 모두 벗기고 글자는 그대로 두며 팝 단추를 숨긴다', run: async () => {
    const page = await open();
    await page.evaluate(() => yona.CodeCommentBlock.block({ nStartLine: 99, sStartSide: 'B', nStartColumn: 0, nEndLine: 99, sEndSide: 'B', nEndColumn: 1, sPath: 'a.txt' })); assert.equal(await page.locator('.review-block').count(), 0);
    await page.evaluate(() => { $('#pop').show(); yona.CodeCommentBlock.block({ nStartLine: 1, sStartSide: 'B', nStartColumn: 0, nEndLine: 1, sEndSide: 'B', nEndColumn: 5, sPath: 'a.txt' }); yona.CodeCommentBlock.unblock(); });
    assert.deepEqual(await page.evaluate(() => [$('.review-block').length, $('#r1 pre').text(), $('#pop').css('display')]), [0, 'hello world', 'none']); await page.close();
  } },
  { name: '표시: 왼쪽 버튼으로 코드를 누르면 기존 표시를 벗기고, 오른쪽 버튼이면 그대로 둔다', run: async () => {
    const page = await open(); await page.evaluate(() => yona.CodeCommentBlock.block({ nStartLine: 1, sStartSide: 'B', nStartColumn: 0, nEndLine: 1, sEndSide: 'B', nEndColumn: 5, sPath: 'a.txt' }));
    await page.evaluate(() => { $('#r2 pre').trigger($.Event('mousedown', { button: 2 })); }); assert.equal(await page.locator('.review-block').count(), 1);
    await page.evaluate(() => { $('#r2 pre').trigger($.Event('mousedown', { button: 0 })); }); assert.equal(await page.locator('.review-block').count(), 0); await page.close();
  } },
];
