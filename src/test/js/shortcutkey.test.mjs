// 전역 단축키(`common/yona.ShortcutKey.js`)가 Shadow DOM 안의 입력란(`<title-input>`)에서 친 글자를 단축키로 받아들이지 않는지 확인한다(D-102, A-408).
// 프로젝트 메뉴에는 H, B, C, I, M, P처럼 글자 하나로 화면을 옮기는 단축키가 있다. 키보드 이벤트의 대상(`event.target`)은 Shadow DOM 경계에서 호스트로 바뀌므로
// 태그 이름만 보면 입력란을 알아보지 못한다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, JS } from './harness.mjs';

const BUNDLE = path.join(JS, 'lib/title-input/title-input.min.js');
const HTML = '<title-input id="title" name="title"></title-input><input id="plain"><textarea id="ta"></textarea><div id="free" tabindex="0">x</div>';

async function setup() {
  const page = await pageWith({ html: HTML, scripts: ['common/yona.ShortcutKey.js'] });
  await page.addScriptTag({ path: BUNDLE, type: 'module' });
  await page.waitForFunction(() => customElements.get('title-input'));
  await page.evaluate(() => {
    window.__hits = [];
    yona.ShortcutKey.setKeymapLink({ B: '#board' });
    yona.ShortcutKey.attach('G', (info) => window.__hits.push({ tag: info.sTagName, form: info.bFormInput, target: info.welTarget[0].tagName.toLowerCase() }));
  });
  return page;
}
const hash = (page) => page.evaluate(() => location.hash);

export const tests = [
  { name: '입력란이 아닌 곳에서 B를 누르면 단축키가 동작한다(대조군)', run: async () => {
    const page = await setup();
    await page.locator('#free').focus(); await page.keyboard.press('b');
    assert.equal(await hash(page), '#board');
    await page.close();
  } },
  { name: '일반 입력란과 textarea에서 친 글자는 단축키가 아니다', run: async () => {
    const page = await setup();
    await page.locator('#plain').focus(); await page.keyboard.type('bug');
    await page.locator('#ta').focus(); await page.keyboard.type('bug');
    assert.equal(await hash(page), '');
    await page.close();
  } },
  { name: '<title-input>에서 친 글자도 단축키가 아니다(대상이 호스트로 보여도 안쪽 입력란을 본다)', run: async () => {
    const page = await setup();
    await page.locator('title-input input').focus(); await page.keyboard.type('bug');
    assert.equal(await hash(page), '', '제목에 "bug"를 치다가 게시판으로 이동하면 안 된다');
    assert.equal(await page.evaluate(() => document.getElementById('title').value), 'bug');
    await page.close();
  } },
  { name: '핸들러에 알려 주는 대상 정보는 안쪽 입력란이다(sTagName INPUT, bFormInput true)', run: async () => {
    const page = await setup();
    await page.locator('title-input input').focus(); await page.keyboard.press('g');
    assert.deepEqual(await page.evaluate(() => window.__hits), [{ tag: 'INPUT', form: true, target: 'input' }]);
    await page.locator('#free').focus(); await page.keyboard.press('g');
    assert.deepEqual(await page.evaluate(() => window.__hits.at(-1)), { tag: 'DIV', form: false, target: 'div' });
    await page.close();
  } },
];

// ---- S-05(A-420): keyCode 표 대신 KeyboardEvent.key 로 단축키를 알아본다 ----
/** 미국 배열 키보드가 보내는 [key, code, keyCode]를 만든다. 옛 표(keyCode → 이름)의 모든 항목을 시험하는 데 쓴다 */
const US = (() => {
  const t = [];
  for (const c of 'ABCDEFGHIJKLMNOPQRSTUVWXYZ') t.push([c, c.toLowerCase(), 'Key' + c, c.charCodeAt(0)]);
  for (const d of '0123456789') t.push([d, d, 'Digit' + d, d.charCodeAt(0)]);
  for (const [name, key, code, kc] of [['ENTER', 'Enter', 'Enter', 13], ['UP', 'ArrowUp', 'ArrowUp', 38], ['DOWN', 'ArrowDown', 'ArrowDown', 40], ['LEFT', 'ArrowLeft', 'ArrowLeft', 37], ['RIGHT', 'ArrowRight', 'ArrowRight', 39],
    ['ESC', 'Escape', 'Escape', 27], ['SPACE', ' ', 'Space', 32], ['BACKSPACE', 'Backspace', 'Backspace', 8], ['TAB', 'Tab', 'Tab', 9], ['DELETE', 'Delete', 'Delete', 46], ['PAGEUP', 'PageUp', 'PageUp', 33], ['PAGEDOWN', 'PageDown', 'PageDown', 34],
    ['HOME', 'Home', 'Home', 36], ['END', 'End', 'End', 35], ['[', '[', 'BracketLeft', 219], [']', ']', 'BracketRight', 221], [';', ';', 'Semicolon', 186], ["'", "'", 'Quote', 222], [',', ',', 'Comma', 188], ['.', '.', 'Period', 190],
    ['/', '/', 'Slash', 191], ['-', '-', 'Minus', 189], ['=', '=', 'Equal', 187], ['\\', '\\', 'Backslash', 220], ['`', '`', 'Backquote', 192]]) t.push([name, key, code, kc]);
  for (let n = 1; n <= 12; n++) t.push(['F' + n, 'F' + n, 'F' + n, 111 + n]);
  return t;
})();
async function plain() {
  const page = await pageWith({ html: '<div id="free" tabindex="0">x</div><input id="plain">', scripts: ['common/yona.ShortcutKey.js'] });
  await page.evaluate(() => { window.__hits = []; });
  return page;
}
const press = (page, init) => page.evaluate((init) => document.getElementById('free').dispatchEvent(new KeyboardEvent('keydown', Object.assign({ bubbles: true, cancelable: true }, init))), init);
const hits = (page) => page.evaluate(() => window.__hits);
const attach = (page, name) => page.evaluate((name) => yona.ShortcutKey.attach(name, () => window.__hits.push(name)), name);

tests.push(
  { name: '옛 keyCode 표의 모든 항목(글자, 숫자, 이동 키, 기호, F1~F12)이 key로도 같은 이름의 단축키를 부른다', run: async () => {
    const page = await plain(); const missing = [];
    for (const [name, key, code, keyCode] of US) {
      await page.evaluate(() => { window.__hits = []; }); await attach(page, name);
      await press(page, { key, code, keyCode });
      if ((await hits(page)).join() !== name) missing.push(name);
    }
    assert.deepEqual(missing, []); await page.close();
  } },
  { name: '조합 키(CTRL, SHIFT, ALT)와 함께 눌러도 이름이 같다: CTRL+ENTER, CTRL+A, SHIFT+ENTER, ALT+N', run: async () => {
    const page = await plain();
    for (const [name, init] of [['CTRL+ENTER', { key: 'Enter', code: 'Enter', keyCode: 13, ctrlKey: true }], ['CTRL+A', { key: 'a', code: 'KeyA', keyCode: 65, ctrlKey: true }],
      ['SHIFT+ENTER', { key: 'Enter', code: 'Enter', keyCode: 13, shiftKey: true }], ['ALT+N', { key: 'n', code: 'KeyN', keyCode: 78, altKey: true }], ['CTRL+B', { key: 'b', code: 'KeyB', keyCode: 66, metaKey: true }]]) {
      await page.evaluate(() => { window.__hits = []; }); await attach(page, name); await press(page, init);
      assert.equal((await hits(page)).join(), name, name);
    }
    await page.close();
  } },
  { name: 'Shift를 누른 글자와 CapsLock으로 대문자가 된 글자: SHIFT+G, G', run: async () => {
    const page = await plain(); await attach(page, 'SHIFT+G'); await attach(page, 'G');
    await press(page, { key: 'G', code: 'KeyG', keyCode: 71, shiftKey: true }); await press(page, { key: 'G', code: 'KeyG', keyCode: 71 }); await press(page, { key: 'g', code: 'KeyG', keyCode: 71 });
    assert.deepEqual(await hits(page), ['SHIFT+G', 'G', 'G']); await page.close();
  } },
  { name: 'Shift를 누른 숫자와 기호는 물리 키 이름이다: SHIFT+1(key는 !), SHIFT+/(key는 ?)', run: async () => {
    const page = await plain(); await attach(page, 'SHIFT+1'); await attach(page, 'SHIFT+/');
    await press(page, { key: '!', code: 'Digit1', keyCode: 49, shiftKey: true }); await press(page, { key: '?', code: 'Slash', keyCode: 191, shiftKey: true });
    assert.deepEqual(await hits(page), ['SHIFT+1', 'SHIFT+/']); await page.close();
  } },
  { name: '숫자 패드의 숫자와 연산 키는 옛 표에 없었으므로 단축키가 아니다', run: async () => {
    const page = await plain(); await attach(page, '1'); await attach(page, '=');
    await press(page, { key: '1', code: 'Numpad1', keyCode: 97 }); await press(page, { key: '+', code: 'NumpadAdd', keyCode: 107 });
    assert.deepEqual(await hits(page), []); await page.close();
  } },
  { name: '라틴이 아닌 배열(러시아어)에서도 물리 글자 키로 단축키가 동작한다(옛 keyCode와 같다)', run: async () => {
    const page = await plain(); await attach(page, 'G');
    await press(page, { key: 'п', code: 'KeyG', keyCode: 71 });
    assert.deepEqual(await hits(page), ['G']); await page.close();
  } },
  { name: '한글 입력기: 조합 중이거나 key가 Process인 키는 단축키가 아니다', run: async () => {
    const page = await plain(); await attach(page, 'G'); await attach(page, 'ENTER');
    await press(page, { key: 'Process', code: 'KeyG', keyCode: 229 }); await press(page, { key: 'ㅎ', code: 'KeyG', keyCode: 229 });
    await press(page, { key: 'g', code: 'KeyG', keyCode: 71, isComposing: true }); await press(page, { key: 'Enter', code: 'Enter', keyCode: 229, isComposing: true });
    assert.deepEqual(await hits(page), []); await page.close();
  } },
  { name: '표에 없는 키(Shift 단독, 미디어 키, Unidentified)는 무시하고 오류도 없다', run: async () => {
    const page = await plain(); await attach(page, 'G');
    for (const key of ['Shift', 'Control', 'Alt', 'Meta', 'CapsLock', 'AudioVolumeUp', 'Unidentified', 'Dead']) await press(page, { key, code: key, keyCode: 0 });
    assert.deepEqual(await hits(page), []); assert.deepEqual(page.errors, []); await page.close();
  } },
);
