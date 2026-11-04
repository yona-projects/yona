import assert from 'node:assert/strict';
import { pageWith, templateSnippet } from './harness.mjs';

/** 번역 버튼 핸들러 조각을 실행하고, 버튼을 눌렀을 때 버튼이 비활성화되는지 본다. */
async function translateDisables(template, from, to, buttonHtml, selector) {
  const snippet = templateSnippet(template, from, to);
  const page = await pageWith({ html: buttonHtml + '<div class="markdown-wrap"></div><div id="comment-body-9"><div class="markdown-wrap"></div></div>' });
  const disabled = await page.evaluate(async ([code, sel]) => {
    // jQuery는 성공 콜백의 `this`를 요청 설정 객체로 준다. 버튼이 아니다
    $.ajax = function () { return $.Deferred().resolveWith({ url: '/translation' }, [{ translated: '번역' }]).promise(); };
    new Function(code)();
    $(sel).trigger('click');
    await new Promise((r) => setTimeout(r, 10));
    return $(sel).attr('disabled') === 'disabled';
  }, [snippet, selector]);
  await page.close();
  return disabled;
}

export const tests = [
  { name: '글 번역 단추는 번역을 받은 뒤 비활성화된다 (C064, 게시판)', async run() {
    assert.equal(await translateDisables('board/view.html', "$('#translate').one(", '$.elevator', '<button id="translate"></button>', '#translate'), true);
  } },
  { name: '댓글 번역 단추는 번역을 받은 뒤 비활성화된다 (C064, 게시판)', async run() {
    assert.equal(await translateDisables('board/view.html', "$('.comment-translate').one(", '$.elevator', '<button class="comment-translate" data-comment-id="9"></button>', '.comment-translate'), true);
  } },
  { name: '글 번역 단추는 번역을 받은 뒤 비활성화된다 (C064, 이슈)', async run() {
    assert.equal(await translateDisables('issue/view.html', "$('#translate').one(", "$('.comment-translate').one(", '<button id="translate"></button>', '#translate'), true);
  } },
  { name: '댓글 번역 단추는 번역을 받은 뒤 비활성화된다 (C064, 이슈)', async run() {
    assert.equal(await translateDisables('issue/view.html', "$('.comment-translate').one(", 'if (ClipboardJS.isSupported())', '<button class="comment-translate" data-comment-id="9"></button>', '.comment-translate'), true);
  } },
  { name: '게시판 라벨을 고르면 jQuery 3에 없는 .error 때문에 오류가 나지 않는다 (R-30)', async run() {
    const snippet = templateSnippet('board/view.html', "$('.board-labels select').on('change'", '// detect comment');
    const page = await pageWith({ html: '<div class="board-labels"><select><option>a</option></select></div>' });
    const out = await page.evaluate(async (code) => {
      $.ajax = function () { return $.Deferred().resolve({}).promise(); };
      new Function(code)();
      var e = $.Event('change'); e.val = [1];
      try { $('.board-labels select').trigger(e); } catch (err) { return err.message; }
      return 'ok';
    }, snippet);
    await page.close();
    assert.equal(out, 'ok', out);
  } },
  { name: '알림의 링크나 이미지를 눌러도 더 보기가 접히거나 펴지지 않는다 (C077)', async run() {
    const snippet = templateSnippet('index/notifications.html', "$('.notification-wrap').on('click','[data-toggle=\"learnmore\"]'", 'function checkOverflow');
    const html = '<div class="notification-wrap"><div id="n" data-toggle="learnmore" data-target="m1"><span class="text">본문</span><a href="#x">링크</a><div class="more">...</div></div></div><div id="m1"><div class="message"></div></div>';
    const page = await pageWith({ html });
    const out = await page.evaluate((code) => {
      new Function(code)();
      var shown = function () { return $('.more').css('display') !== 'none'; };
      $('#n a')[0].dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      var afterLink = shown();
      $('#n .text')[0].dispatchEvent(new MouseEvent('click', { bubbles: true }));
      return { afterLink: afterLink, afterText: shown() };
    }, snippet);
    await page.close();
    assert.equal(out.afterLink, true, '링크를 눌렀는데 더 보기가 토글되었다');
    assert.equal(out.afterText, false, '본문을 누르면 토글되어야 한다');
  } },
];
