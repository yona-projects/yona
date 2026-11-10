// 코드 리뷰 댓글 상자(`esm/CodeCommentBox.js`): 대상 줄 아래나 위에 입력 폼을 옮겨 놓고 블록 정보를 숨은 입력으로 채우며, 닫으면 처음 자리로 되돌리는 동작을 고정한다(시험 채우기, D-123).
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `<table id="diff"><tbody>
<tr id="t1" data-line="1"><td>a</td></tr><tr id="t2" data-line="2"><td>b</td></tr>
<tr class="comment-thread-row"><td><div class="comment-thread-wrap"><div id="th" data-thread-id="5">스레드</div><div class="write-comment-form"></div></div></td></tr>
</tbody></table>
<div id="holder"><div id="review-form" style="display:none"><form><input type="hidden" name="startLine" value="old"><textarea data-editor-mode="code-review-body"></textarea><div class="upload-wrap"></div><button type="button" data-toggle="close">닫기</button></form></div></div>`;
const BLOCK = { bIsReversed: true, nStartLine: 1, sStartType: 'add', sStartSide: 'B', nEndLine: 2, sEndSide: 'B', sFilePath: 'a.txt' };

async function open() {
  const page = await pageWith({ stubs: false, html: HTML, scripts: [{ content: 'window.Messages = function (k) { return k; };' }, 'common/yona.Common.js'] });
  await page.evaluate(() => {
    window.__ev = []; yona.Files = { getUploader: () => null }; yona.Attachments = function () {};
    $(document).on('CodeCommentBox:aftershow CodeCommentBox:afterhide', (e) => window.__ev.push(e.type + '.' + (e.namespace || '')));
    $.event.trigger = ((orig) => function (ev) { if (typeof ev === 'string' && ev.indexOf('CodeCommentBox') === 0) window.__ev.push(ev); return orig.apply(this, arguments); })($.event.trigger);
  });
  await page.addScriptTag({ content: classicFromEsm('esm/CodeCommentBox.js') });
  await page.evaluate(() => { yona.CodeCommentBox.init({}); });
  return page;
}
const withBlock = async (page) => { await page.evaluate((b) => { $('#t1').data('blockInfo', b); }, BLOCK); };
const fields = (page) => page.evaluate(() => { const o = {}; $('#review-form input[type=hidden]').each((i, e) => { o[e.name] = e.value; }); return o; });

export const tests = [
  { name: '상자 열기: 아래쪽이 기본이라 대상 줄 바로 뒤에 comment-form 행을 만들고 폼을 옮겨 보이며 화살표는 위쪽을 향한다', run: async () => {
    const page = await open(); await withBlock(page);
    await page.evaluate(() => yona.CodeCommentBox.show($('#t1')));
    assert.deepEqual(await page.evaluate(() => [$('#t1').next().hasClass('comment-form'), $('#t1').next().find('#review-form').length, $('#review-form').css('display'), $('#review-form').hasClass('arrow-top'), $('#review-form').hasClass('arrow-bottom'), yona.CodeCommentBox.isVisible()]), [true, 1, 'block', true, false, true]);
    assert.equal(await page.evaluate(() => document.activeElement.tagName), 'TEXTAREA'); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '상자 열기: sPlacement가 top이면 대상 줄 앞에 놓고 화살표는 아래쪽을 향한다', run: async () => {
    const page = await open(); await withBlock(page);
    await page.evaluate(() => yona.CodeCommentBox.show($('#t2'), { sPlacement: 'TOP' }));
    assert.deepEqual(await page.evaluate(() => [$('#t2').prev().hasClass('comment-form'), $('#review-form').hasClass('arrow-bottom')]), [true, true]); await page.close();
  } },
  { name: '상자 열기: 블록 정보는 앞 글자를 떼고 소문자로 시작하는 숨은 입력(startLine 등)이 되며 bIsReversed는 뺀다. 이미 있는 입력은 값을 갱신하고 중복하지 않는다', run: async () => {
    const page = await open(); await withBlock(page);
    await page.evaluate(() => yona.CodeCommentBox.show($('#t1')));
    assert.deepEqual(await fields(page), { startLine: '1', startType: 'add', startSide: 'B', endLine: '2', endSide: 'B', filePath: 'a.txt' });
    await page.evaluate(() => { yona.CodeCommentBox.hide(); $('#t1').data('blockInfo', { nStartLine: 9 }); yona.CodeCommentBox.show($('#t1')); });
    assert.equal(await page.locator('#review-form input[name=startLine]').count(), 1); assert.equal(await page.locator('#review-form input[name=startLine]').inputValue(), '9'); await page.close();
  } },
  { name: '상자 열기: 대상이 기존 스레드이면 thread.id를 채우고 그 스레드의 폼 자리로 옮기며 새 행은 만들지 않는다', run: async () => {
    const page = await open();
    await page.evaluate(() => yona.CodeCommentBox.show($('#th')));
    assert.deepEqual(await page.evaluate(() => [$('.comment-thread-wrap .write-comment-form #review-form').length, $('tr.comment-form').length, $('#review-form input[name="thread.id"]').val()]), [1, 0, '5']); await page.close();
  } },
  { name: '상자 닫기: 숨기고 처음 부모로 되돌리며 만든 행을 지운다. 닫기 단추도 같다. 열기/닫기 이벤트가 발생한다', run: async () => {
    const page = await open(); await withBlock(page);
    await page.evaluate(() => { yona.CodeCommentBox.show($('#t1')); $('#review-form [data-toggle=close]').trigger('click'); });
    assert.deepEqual(await page.evaluate(() => [$('#review-form').css('display'), $('#review-form').parent().attr('id'), $('tr.comment-form').length, yona.CodeCommentBox.isVisible()]), ['none', 'holder', 0, false]);
    assert.deepEqual(await page.evaluate(() => window.__ev.filter((e) => /CodeCommentBox:after(show|hide)$/.test(e))), ['CodeCommentBox:aftershow', 'CodeCommentBox:afterhide']); await page.close();
  } },
  { name: '상자 토글: 보이면 닫고 안 보이면 연다', run: async () => {
    const page = await open(); await withBlock(page);
    await page.evaluate(() => yona.CodeCommentBox.toggle($('#t1'))); assert.equal(await page.evaluate(() => yona.CodeCommentBox.isVisible()), true);
    await page.evaluate(() => yona.CodeCommentBox.toggle($('#t1'))); assert.equal(await page.evaluate(() => yona.CodeCommentBox.isVisible()), false); await page.close();
  } },
];
