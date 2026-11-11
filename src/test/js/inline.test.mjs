import assert from 'node:assert/strict';
import { pageWith, templateSnippet } from './harness.mjs';

/** 번역 버튼 핸들러 조각을 실행하고, 버튼을 눌렀을 때 버튼이 비활성화되는지 본다. */
async function translateDisables(template, from, to, buttonHtml, selector) {
  const snippet = templateSnippet(template, from, to);
  const page = await pageWith({ html: buttonHtml + '<div class="markdown-wrap"></div><div id="comment-body-9"><div class="markdown-wrap"></div></div>' });
  const disabled = await page.evaluate(async ([code, sel]) => {
    // jQuery는 성공 콜백의 `this`를 요청 설정 객체로 준다. 버튼이 아니다
    ((window.yona = window.yona || {}).http = (window.yona.http || {})).ajax = function () { return $.Deferred().resolveWith({ url: '/translation' }, [{ translated: '번역' }]).promise(); };
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
    assert.equal(await translateDisables('board/view.html', "$('#translate').one(", 'yona.ui.Elevator.init', '<button id="translate"></button>', '#translate'), true);
  } },
  { name: '댓글 번역 단추는 번역을 받은 뒤 비활성화된다 (C064, 게시판)', async run() {
    assert.equal(await translateDisables('board/view.html', "$('.comment-translate').one(", 'yona.ui.Elevator.init', '<button class="comment-translate" data-comment-id="9"></button>', '.comment-translate'), true);
  } },
  { name: '글 번역 단추는 번역을 받은 뒤 비활성화된다 (C064, 이슈)', async run() {
    assert.equal(await translateDisables('issue/view.html', "$('#translate').one(", "$('.comment-translate').one(", '<button id="translate"></button>', '#translate'), true);
  } },
  { name: '댓글 번역 단추는 번역을 받은 뒤 비활성화된다 (C064, 이슈)', async run() {
    assert.equal(await translateDisables('issue/view.html', "$('.comment-translate').one(", '// 이메일 복사 단추', '<button class="comment-translate" data-comment-id="9"></button>', '.comment-translate'), true);
  } },
  { name: '게시판 라벨을 고르면 jQuery 3에 없는 .error 때문에 오류가 나지 않는다 (R-30)', async run() {
    const snippet = templateSnippet('board/view.html', "$('.board-labels select').on('change'", '// detect comment');
    const page = await pageWith({ html: '<div class="board-labels"><select><option>a</option></select></div>' });
    const out = await page.evaluate(async (code) => {
      ((window.yona = window.yona || {}).http = (window.yona.http || {})).ajax = function () { return $.Deferred().resolve({}).promise(); };
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
  { name: '홈의 안내(버튼 영역)는 처음에 접혀 있고, 펼친 상태를 기억해 다음에도 펼친다 (A-493)', async run() {
    const snippet = templateSnippet('index/notifications.html', '$("#toggleIntro").click', "$('.notification-wrap').on('click','[data-toggle=\"learnmore\"]'");
    const html = '<div class="site-guide-outer hide">안내</div><div class="guide-toggle"><button id="toggleIntro"></button></div>';
    const run = async (stored, click) => {
      const page = await pageWith({ html });
      const out = await page.evaluate(([code, st, doClick]) => {
        const m = new Map(st === null ? [] : [['yobi-intro', st]]);
        Object.defineProperty(window, 'localStorage', { configurable: true, value: { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)) } });
        new Function(code)();
        if (doClick) document.getElementById('toggleIntro').dispatchEvent(new MouseEvent('click', { bubbles: true }));
        return { hidden: document.querySelector('.site-guide-outer').classList.contains('hide'), stored: m.get('yobi-intro') };
      }, [snippet, stored, click]);
      await page.close();
      return out;
    };
    assert.equal((await run(null, false)).hidden, true, '기억한 값이 없으면 접힌 채로 시작한다');
    assert.equal((await run('false', false)).hidden, true, '접어 둔 상태는 접힌 채로');
    assert.equal((await run('true', false)).hidden, false, '펼쳐 둔 상태는 펼친 채로');
    const opened = await run(null, true);
    assert.equal(opened.hidden, false, '단추를 누르면 펼쳐진다');
    assert.equal(opened.stored, 'true', '펼침을 기억한다');
  } },
];
