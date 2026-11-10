// Tab 들여쓰기(`esm/KeyControl.js`)의 `_tab`, `_untab`을 시험한다(S-03, D-105 이후 다시 열림).
// 결과 글자와 선택 범위는 v1.6 구현(값을 통째로 바꿈)과 같아야 하고, 되돌리기(Ctrl+Z)가 한 번에 이전으로 돌아가야 한다.
import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

/** v1.6 구현 그대로(기준). 값을 통째로 바꾸는 방식이다. 이름만 `_refTab`, `_refUntab`으로 바꿔 둔다 */
const REFERENCE = "(function () {\n\n    var tabCharacter = '    '; // Use `\\t` or multiple space character\n\n    var select = function (start, end, target) {\n        target.focus();\n        target.setSelectionRange(start, end);\n    };\n\n    window._refTab = function (target) {\n\n        var start = target.selectionStart,\n            end = target.selectionEnd,\n            value = target.value,\n            selections = value.substring(start, end).split('\\n');\n\n        for (var i = 0, len = selections.length; i < len; ++i) {\n            selections[i] = tabCharacter + selections[i];\n        }\n\n        target.value = value.substring(0, start) + selections.join('\\n') + value.substring(end);\n\n        // re-select text after tabbing\n        var selectEnd = (end + (tabCharacter.length * selections.length));\n        if (start === end) {\n            select(selectEnd, selectEnd, target);\n        } else {\n            select(start, selectEnd, target);\n        }\n        target.focus();\n    };\n\n    window._refUntab = function (target) {\n        var start = target.selectionStart,\n            end = target.selectionEnd,\n            value = target.value,\n            pattern = new RegExp(tabCharacter),\n            edits = 0;\n\n        if (start === end) { // single line\n\n            while (start > 0) {\n                if (value.charAt(start - 1) === '\\n' || value.charAt(start - 1) === '\\r') {\n                    break;\n                }\n                start--;\n            }\n\n            var portion = value.substring(start, end),\n                matches = portion.match(pattern);\n\n            if (matches) {\n                target.value = value.substring(0, start) + portion.replace(pattern, \"\") + value.substring(end);\n                end--;\n            }\n\n            // set caret position after tabbing\n            var selectEnd = end <= start ? end : end - tabCharacter.length + 1;\n            select(selectEnd, selectEnd, target);\n\n        } else { // multiline\n\n            var selections = value.substring(start, end).split('\\n');\n\n            for (var i = 0, len = selections.length; i < len; ++i) {\n                if (selections[i].match(pattern)) {\n                    edits++;\n                    selections[i] = selections[i].replace(pattern, \"\");\n                }\n            }\n\n            target.value = value.substring(0, start) + selections.join('\\n') + value.substring(end);\n\n            // re-select text after tabbing\n            select(start, (edits > 0 ? end - (tabCharacter.length * edits) : end), target);\n        }\n\n        target.focus()\n    };\n})();";

async function open() {
  const page = await pageWith({ html: '<textarea id="t" style="width:300px;height:100px"></textarea>', scripts: ['esm/KeyControl.js'], stubs: false });
  await page.addScriptTag({ content: REFERENCE });
  await page.evaluate(() => { window.__inputs = 0; document.getElementById('t').addEventListener('input', () => window.__inputs++); });
  return page;
}
/** [fn] ('_tab', '_untab', '_refTab', '_refUntab')을 실행하고 값과 선택 범위를 돌려준다 */
const run = (page, fn, value, start, end) => page.evaluate(([fn, value, start, end]) => {
  const t = document.getElementById('t'); t.value = value; t.focus(); t.setSelectionRange(start, end); window.__inputs = 0;
  window[fn](t);
  return { value: t.value, start: t.selectionStart, end: t.selectionEnd, inputs: window.__inputs };
}, [fn, value, start, end]);

const CASES = [
  ['abc', 3, 3], ['abc', 0, 0], ['abc', 1, 2], ['abc', 0, 3], ['a\nb\nc', 0, 5], ['a\nb\nc', 2, 3], ['한글 줄\n둘째 줄', 0, 8], ['한글 줄\n둘째 줄', 4, 4],
  ['    abc', 7, 7], ['    abc', 5, 5], ['    abc', 0, 7], ['    a\n    b\n    c', 0, 17], ['    a\nb\n    c', 0, 13], ['  ab', 4, 4], ['x    y', 6, 6],
  ['', 0, 0], ['첫 줄\n    둘째 줄', 7, 7], ['a\n\nb', 0, 4], ['    a\n    b', 6, 6],
];

export const tests = [
  { name: '결과(값과 선택 범위)가 v1.6 구현과 모든 경우에서 같다: _tab', run: async () => {
    const page = await open(); const diffs = [];
    for (const [v, s, e] of CASES) { const a = await run(page, '_tab', v, s, e), b = await run(page, '_refTab', v, s, e); if (a.value !== b.value || a.start !== b.start || a.end !== b.end) diffs.push({ v, s, e, a, b }); }
    assert.deepEqual(diffs, []); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '결과(값과 선택 범위)가 v1.6 구현과 모든 경우에서 같다: _untab', run: async () => {
    const page = await open(); const diffs = [];
    for (const [v, s, e] of CASES) { const a = await run(page, '_untab', v, s, e), b = await run(page, '_refUntab', v, s, e); if (a.value !== b.value || a.start !== b.start || a.end !== b.end) diffs.push({ v, s, e, a, b }); }
    assert.deepEqual(diffs, []); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: 'input 이벤트가 동작마다 한 번 온다(임시 저장 등 구독자가 알 수 있게)', run: async () => {
    const page = await open();
    const a = await run(page, '_tab', 'abc', 0, 3); assert.equal(a.inputs, 1);
    const b = await run(page, '_untab', '    abc', 4, 4); assert.equal(b.inputs, 1);
    await page.close();
  } },
  { name: '되돌리기: Tab 뒤 Ctrl+Z 한 번으로 이전 글자로 돌아간다', run: async () => {
    const page = await open();
    await page.evaluate(() => { const t = document.getElementById('t'); t.focus(); });
    await page.keyboard.type('abc'); await page.keyboard.press('Enter'); await page.keyboard.type('def');
    await page.evaluate(() => window._tab(document.getElementById('t')));
    assert.equal(await page.evaluate(() => document.getElementById('t').value), 'abc\ndef    ');
    await page.keyboard.press('Control+z');
    assert.equal(await page.evaluate(() => document.getElementById('t').value), 'abc\ndef');
    await page.close();
  } },
  { name: '되돌리기: 여러 줄 들여쓰기도 한 번에 돌아간다', run: async () => {
    const page = await open();
    await page.evaluate(() => { const t = document.getElementById('t'); t.focus(); });
    await page.keyboard.type('a'); await page.keyboard.press('Enter'); await page.keyboard.type('b');
    await page.evaluate(() => { const t = document.getElementById('t'); t.setSelectionRange(0, t.value.length); window._tab(t); });
    assert.equal(await page.evaluate(() => document.getElementById('t').value), '    a\n    b');
    await page.keyboard.press('Control+z');
    assert.equal(await page.evaluate(() => document.getElementById('t').value), 'a\nb');
    await page.close();
  } },
  { name: '되돌리기: 내어쓰기(Shift+Tab) 뒤에도 Ctrl+Z로 돌아간다', run: async () => {
    const page = await open();
    await page.evaluate(() => { const t = document.getElementById('t'); t.focus(); });
    await page.keyboard.type('    abc');
    await page.evaluate(() => window._untab(document.getElementById('t')));
    assert.equal(await page.evaluate(() => document.getElementById('t').value), 'abc');
    await page.keyboard.press('Control+z');
    assert.equal(await page.evaluate(() => document.getElementById('t').value), '    abc');
    await page.close();
  } },
  { name: 'execCommand를 쓸 수 없거나 실패해도 같은 결과가 나온다(예전 방식으로 물러남)', run: async () => {
    const page = await open();
    for (const mode of ['false', 'throw']) {
      await page.evaluate((mode) => { window.__orig = window.__orig || document.execCommand.bind(document); document.execCommand = mode === 'throw' ? () => { throw new Error('no'); } : () => false; }, mode);
      const diffs = [];
      for (const [v, s, e] of CASES) {
        const a = await run(page, '_tab', v, s, e), b = await run(page, '_refTab', v, s, e); if (a.value !== b.value || a.start !== b.start || a.end !== b.end) diffs.push({ mode, v, s, e, a, b });
        const c = await run(page, '_untab', v, s, e), d = await run(page, '_refUntab', v, s, e); if (c.value !== d.value || c.start !== d.start || c.end !== d.end) diffs.push({ mode, fn: 'untab', v, s, e, c, d });
      }
      assert.deepEqual(diffs, [], mode);
    }
    await page.close();
  } },
];
