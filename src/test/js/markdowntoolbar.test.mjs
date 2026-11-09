// 편집기 서식 단추(`<markdown-toolbar>`, GitHub의 markdown-toolbar-element 2.2.3, D-105, A-416 이후)가 Yona 편집기 마크업에서 동작하는지 확인한다.
// 단추를 누르면 textarea의 선택 범위에 마크다운 문법이 들어가고, 폼 제출 값과 input 이벤트가 따라와야 한다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, JS } from './harness.mjs';

const MODULE = { path: path.join(JS, 'lib/markdown-toolbar/markdown-toolbar-element.js'), type: 'module' };
const HTML = `<form id="f"><markdown-toolbar for="editor-body-x1" id="tb">
<md-header id="h">H</md-header><md-bold id="b">B</md-bold><md-italic id="i">I</md-italic><md-quote id="q">q</md-quote><md-code id="c">c</md-code><md-link id="l">l</md-link>
<md-unordered-list id="ul">u</md-unordered-list><md-ordered-list id="ol">o</md-ordered-list><md-mention id="m">@</md-mention><md-ref id="r">#</md-ref></markdown-toolbar>
<textarea id="editor-body-x1" name="body"></textarea></form>`;

async function open() {
  const page = await pageWith({ html: HTML, scripts: [MODULE], stubs: false });
  await page.waitForFunction(() => customElements.get('markdown-toolbar') && customElements.get('md-bold'));
  await page.evaluate(() => { window.__inputs = 0; document.getElementById('editor-body-x1').addEventListener('input', () => window.__inputs++); });
  return page;
}
/** 텍스트를 채우고 [start, end] 범위를 선택한 뒤 [id] 단추를 눌러 textarea 값과 선택 범위를 돌려준다. */
async function press(page, id, text, start, end) {
  await page.evaluate(([t, s, e]) => { const ta = document.getElementById('editor-body-x1'); ta.value = t; ta.focus(); ta.setSelectionRange(s, e); }, [text, start, end]);
  await page.click('#' + id);
  return page.evaluate(() => { const ta = document.getElementById('editor-body-x1'); return { value: ta.value, start: ta.selectionStart, end: ta.selectionEnd, form: new FormData(document.getElementById('f')).get('body') }; });
}

export const tests = [
  { name: '서식 단추 모듈은 번들 없이 불러와져 markdown-toolbar와 md-* 요소를 정의한다', run: async () => {
    const page = await open();
    const defined = await page.evaluate(() => ['markdown-toolbar', 'md-header', 'md-bold', 'md-italic', 'md-quote', 'md-code', 'md-link', 'md-unordered-list', 'md-ordered-list', 'md-mention', 'md-ref'].filter((n) => !customElements.get(n)));
    assert.deepEqual(defined, []);
    assert.deepEqual(page.errors, []);
    await page.close();
  } },
  { name: '굵게: 선택한 글자를 **로 감싸고 선택은 글자에 남으며 폼 값과 input 이벤트가 따라온다', run: async () => {
    const page = await open();
    const r = await press(page, 'b', 'hello world', 6, 11);
    assert.equal(r.value, 'hello **world**'); assert.deepEqual([r.start, r.end], [8, 13]); assert.equal(r.form, 'hello **world**');
    assert.ok(await page.evaluate(() => window.__inputs) >= 1, 'input 이벤트가 와야 임시 저장 등 기존 구독자가 따라온다');
    await page.close();
  } },
  { name: '기울임, 코드, 인용, 제목, 링크, 목록 문법이 들어간다', run: async () => {
    const page = await open();
    assert.equal((await press(page, 'i', 'abc', 0, 3)).value, '_abc_');
    assert.equal((await press(page, 'c', 'abc', 0, 3)).value, '`abc`');
    assert.equal((await press(page, 'q', 'abc', 0, 3)).value, '> abc');
    assert.equal((await press(page, 'h', 'abc', 0, 3)).value, '### abc');
    assert.equal((await press(page, 'l', 'abc', 0, 3)).value, '[abc](url)');
    assert.equal((await press(page, 'ul', 'abc', 0, 3)).value, '- abc');
    assert.equal((await press(page, 'ol', 'abc', 0, 3)).value, '1. abc');
    await page.close();
  } },
  { name: '여러 줄을 고르고 목록을 누르면 줄마다 문법이 붙는다', run: async () => {
    const page = await open();
    assert.equal((await press(page, 'ul', 'a\nb\nc', 0, 5)).value, '- a\n- b\n- c');
    assert.equal((await press(page, 'ol', 'a\nb', 0, 3)).value, '1. a\n2. b');
    await page.close();
  } },
  { name: '멘션과 참조 단추는 @와 #를 넣어 자동완성이 이어서 열리게 한다', run: async () => {
    const page = await open();
    assert.match((await press(page, 'm', 'hi ', 3, 3)).value, /^hi @/);
    assert.match((await press(page, 'r', 'hi ', 3, 3)).value, /^hi #/);
    await page.close();
  } },
  { name: '한글 선택도 그대로 감싼다', run: async () => {
    const page = await open();
    const r = await press(page, 'b', '안녕 하세요', 3, 6);
    assert.equal(r.value, '안녕 **하세요**');
    await page.close();
  } },
];
