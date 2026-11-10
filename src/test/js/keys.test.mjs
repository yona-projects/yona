// 키보드 판별 도우미(`$yona.isEnterKey`, `isEscapeKey`, `isBackspaceKey`, `isComposingKey`)와 `keyCode`, `which`를 걷어낸 호출처를 확인한다(S-05, A-420).
// 한글 입력기로 조합하는 동안의 Enter와 Esc는 입력기의 것이므로 단축키나 제출로 처리하면 안 된다. Safari는 조합 중에도 key를 "Enter"로 보낸다.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pageWith, JS, classicFromEsm } from './harness.mjs';

const ev = (init) => init;
async function open() { return pageWith({ html: '<input id="i"><textarea id="t"></textarea>', scripts: ['common/yona.Common.js'], stubs: false }); }
/** 합성 키 이벤트를 만들어 [fn]에 네이티브 이벤트와 jQuery 이벤트 둘로 넘기고 결과 쌍을 돌려준다 */
const ask = (page, fn, init, type = 'keydown') => page.evaluate(([fn, init, type]) => {
  let jq = null; const el = document.getElementById('i');
  $(el).one(type, (e) => { jq = $yona[fn](e); });
  const native = new KeyboardEvent(type, Object.assign({ bubbles: true, cancelable: true }, init));
  el.dispatchEvent(native);
  return { native: $yona[fn](native), jquery: jq };
}, [fn, init, type]);

export const tests = [
  { name: 'isEnterKey: Enter(숫자 패드 포함)는 참, 다른 키는 거짓이고 네이티브와 jQuery 이벤트가 같다', run: async () => {
    const page = await open();
    for (const [init, want] of [
      [{ key: 'Enter', code: 'Enter', keyCode: 13 }, true], [{ key: 'Enter', code: 'NumpadEnter', keyCode: 13 }, true],
      [{ key: 'a', code: 'KeyA', keyCode: 65 }, false], [{ key: 'Escape', code: 'Escape', keyCode: 27 }, false], [{ key: ' ', code: 'Space', keyCode: 32 }, false],
    ]) assert.deepEqual(await ask(page, 'isEnterKey', init), { native: want, jquery: want }, JSON.stringify(init));
    await page.close();
  } },
  { name: 'isEnterKey: 한글 조합 중(isComposing, keyCode 229, key "Process")은 거짓이다(Safari형 key "Enter"도 포함)', run: async () => {
    const page = await open();
    for (const init of [
      { key: 'Process', code: 'Enter', keyCode: 229 }, { key: 'Enter', code: 'Enter', keyCode: 229 },
      { key: 'Enter', code: 'Enter', keyCode: 13, isComposing: true }, { key: 'Enter', code: 'Enter', keyCode: 229, isComposing: true },
    ]) assert.deepEqual(await ask(page, 'isEnterKey', init), { native: false, jquery: false }, JSON.stringify(init));
    await page.close();
  } },
  { name: 'isEscapeKey와 isBackspaceKey도 같은 규칙이다', run: async () => {
    const page = await open();
    assert.deepEqual(await ask(page, 'isEscapeKey', { key: 'Escape', code: 'Escape', keyCode: 27 }), { native: true, jquery: true });
    assert.deepEqual(await ask(page, 'isEscapeKey', { key: 'Escape', keyCode: 229, isComposing: true }), { native: false, jquery: false });
    assert.deepEqual(await ask(page, 'isEscapeKey', { key: 'Enter', keyCode: 13 }), { native: false, jquery: false });
    assert.deepEqual(await ask(page, 'isBackspaceKey', { key: 'Backspace', code: 'Backspace', keyCode: 8 }, 'keyup'), { native: true, jquery: true });
    assert.deepEqual(await ask(page, 'isBackspaceKey', { key: 'Delete', code: 'Delete', keyCode: 46 }, 'keyup'), { native: false, jquery: false });
    await page.close();
  } },
  { name: 'isComposingKey: 조합 중인 키 이벤트를 알아본다', run: async () => {
    const page = await open();
    assert.deepEqual(await ask(page, 'isComposingKey', { key: 'Process', keyCode: 229 }), { native: true, jquery: true });
    assert.deepEqual(await ask(page, 'isComposingKey', { key: 'a', keyCode: 65, isComposing: true }), { native: true, jquery: true });
    assert.deepEqual(await ask(page, 'isComposingKey', { key: 'a', keyCode: 65 }), { native: false, jquery: false });
    await page.close();
  } },
  { name: '실제 키보드: 입력란에서 Enter와 Esc를 누르면 판별된다', run: async () => {
    const page = await open();
    await page.evaluate(() => { window.__r = []; $('#i').on('keydown', (e) => window.__r.push([e.key, $yona.isEnterKey(e), $yona.isEscapeKey(e)])); });
    await page.focus('#i'); await page.keyboard.press('Enter'); await page.keyboard.press('Escape'); await page.keyboard.press('a');
    assert.deepEqual(await page.evaluate(() => window.__r), [['Enter', true, false], ['Escape', false, true], ['a', false, false]]);
    await page.close();
  } },
  { name: '앱 스크립트와 템플릿에 keyCode와 which가 남지 않는다(조합 중 판별 세 곳만 예외)', run: async () => {
    const roots = [path.join(JS, 'common'), path.join(JS, 'service'), path.join(JS, '..', '..', '..', 'templates')];
    const files = roots.flatMap((r) => fs.readdirSync(r, { recursive: true }).map((f) => path.join(r, String(f)))).filter((f) => /\.(js|html)$/.test(f) && fs.statSync(f).isFile());
    const allowed = new Set(['yona.Common.js', 'yona.Mention.js', 'yona.ShortcutKey.js']); // 조합 중(keyCode 229) 판별만 예외: 도우미, Mention 보호막, 단축키(Common 없이도 도는 파일)
    const offenders = [];
    for (const f of files) {
      if (allowed.has(path.basename(f)) || /yona\.webauthn\.js$/.test(f)) continue;
      fs.readFileSync(f, 'utf8').split('\n').forEach((line, i) => { if (/\b(keyCode|e\.which|evt\.which|Evt\.which|event\.which|oEvent\.which)\b|\.which\s*[=!]==?|\.which\)/.test(line) && !/^\s*(\/\/|\*|\/\*)/.test(line)) offenders.push(`${path.relative(JS, f)}:${i + 1} ${line.trim().slice(0, 90)}`); });
    }
    assert.deepEqual(offenders, []);
  } },
];

// ---- 댓글 폼의 Esc(`esm/CommentForm.js`): 제출이 진행 중일 때 Esc 로 브라우저가 제출을 취소하면 진행 표시를 걷는다 ----
async function commentForm() {
  const page = await pageWith({ html: '<form id="comment-form"><textarea id="ta"></textarea></form>', stubs: false });
  // 임시 저장은 localStorage 를 쓰는데 시험 문서에서는 막혀 있으므로 이 시험과 무관한 두 함수만 비워 둔다
  await page.evaluate(() => { window.temporarySaveHandler = function () {}; window.removeCurrentPageTemprarySavedContent = function () {}; window.__done = 0; window.NProgress = { start() {}, done() { window.__done++; } }; });
  await page.addScriptTag({ path: path.join(JS, 'common/yona.Common.js') });
  await page.addScriptTag({ content: classicFromEsm('esm/CommentForm.js') });
  return page;
}
const pressKey = (page, init) => page.evaluate((init) => window.dispatchEvent(new KeyboardEvent('keydown', Object.assign({ bubbles: true, cancelable: true }, init))), init);

tests.push(
  { name: '댓글 폼: 제출이 진행 중일 때 Esc 를 누르면 진행 표시를 걷고 진행 중 표시를 끈다', run: async () => {
    const page = await commentForm();
    await page.evaluate(() => { $('#comment-form').data('onsubmit', true); });
    await pressKey(page, { key: 'Escape', code: 'Escape', keyCode: 27 });
    assert.deepEqual(await page.evaluate(() => [window.__done, $('#comment-form').data('onsubmit')]), [1, false]);
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '댓글 폼: 제출 중이 아니거나 Esc 가 아니거나 한글 조합 중이면 아무 일도 없다', run: async () => {
    const page = await commentForm();
    await pressKey(page, { key: 'Escape', code: 'Escape', keyCode: 27 });           // 제출 중이 아님
    await page.evaluate(() => { $('#comment-form').data('onsubmit', true); });
    await pressKey(page, { key: 'Enter', code: 'Enter', keyCode: 13 });             // Esc 가 아님
    await pressKey(page, { key: 'Escape', code: 'Escape', keyCode: 229, isComposing: true }); // 조합 중
    assert.deepEqual(await page.evaluate(() => [window.__done, $('#comment-form').data('onsubmit')]), [0, true]);
    await page.close();
  } },
);
