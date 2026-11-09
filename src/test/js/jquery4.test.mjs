// jQuery 4로 올릴 준비(D-103, A-410): 지워지는 API를 표준 JS로 바꾼 곳이 원래와 같게 동작하는지, 플러그인이 지워진 환경에서 실제로 도는지 확인한다.
// 하네스는 기본으로 jQuery 3.7.1에서 지워질 API를 없앤다(`harness.mjs`의 JQUERY4_REMOVALS). 같은 값 비교에는 원래 API가 남은 페이지(`jq4: false`)를 쓴다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, JS, ROOT } from './harness.mjs';

const BS = (f) => ({ path: path.join(ROOT, 'src/main/resources/static/assets/bootstrap/js', f) });

/** 원래 API(jQuery 3.7.1)와 표준 JS 대체식이 같은 값을 돌려주는지 [values] 전체에서 비교한다. 페이지 안에서 `$.xxx`와 [replacement]를 모두 계산한다. */
async function sameResults(page, original, replacement, values, valuesSource) {
  return page.evaluate(({ original, replacement, values, valuesSource }) => {
    if (valuesSource) values = new Function(`return ${valuesSource};`)();
    const orig = new Function('$', 'v', `return ${original};`);
    const repl = new Function('v', `return ${replacement};`);
    const out = [];
    for (const v of values) {
      const call = (f, a) => { try { return { ok: f(a) }; } catch (e) { return { error: true }; } };
      const a = call((x) => orig(window.jQuery, x), v), b = call(repl, v);
      if (JSON.stringify(a) !== JSON.stringify(b)) out.push({ value: String(typeof v === 'function' ? 'function' : v), original: a, replacement: b });
    }
    return out;
  }, { original, replacement, values, valuesSource });
}

const STRINGS = ['', ' ', ' a ', 'a b', '\t\n z \r', ' x ', '﻿y﻿', ' em ', '한글 ', ' 12 ', '0'];

export const tests = [
  { name: '표준 JS 대체: 앱 코드의 $.trim(값) → (값 || "").trim() (문자열, null, undefined에서 같다)', run: async () => {
    const page = await pageWith({ jq4: false, stubs: false });
    // 앱이 trim하는 값은 모두 `.val()`이나 문자열 인자라 문자열, null, undefined만 온다.
    assert.deepEqual(await sameResults(page, '$.trim(v)', '(v || "").trim()', [...STRINGS, null, undefined]), []);
    await page.close();
  } },
  { name: '표준 JS 대체: $.trim(숫자와 false)는 같지 않다(그래서 앱은 문자열에만 쓴다)', run: async () => {
    const page = await pageWith({ jq4: false, stubs: false });
    const diff = await sameResults(page, '$.trim(v)', '(v || "").trim()', [0, false, NaN]);
    assert.ok(diff.length > 0, '0, false, NaN은 jQuery가 "0", "false", "NaN"으로 바꾸지만 (v || "")는 빈 글자가 된다 → 이 값에는 String(v == null ? "" : v).trim()을 쓴다');
    assert.deepEqual(await sameResults(page, '$.trim(v)', 'String(v == null ? "" : v).trim()', [...STRINGS, null, undefined, 0, false, NaN, 12, true]), []);
    await page.close();
  } },
  { name: '표준 JS 대체: $.parseJSON(문자열) → JSON.parse(문자열) (jQuery 3.0부터 같은 함수, 오류도 같다)', run: async () => {
    const page = await pageWith({ jq4: false, stubs: false });
    assert.deepEqual(await sameResults(page, '$.parseJSON(v)', 'JSON.parse(v)', ['{"a":1}', '[1,2]', 'null', '"s"', '1', '', '{bad', ' {"a":1} ', '{"한글":"값"}', null, undefined]), []);
    await page.close();
  } },
  { name: '표준 JS 대체: $.isNumeric(값) → (typeof 값 === "number" || typeof 값 === "string") && !isNaN(값 - parseFloat(값))', run: async () => {
    const page = await pageWith({ jq4: false, stubs: false });
    const values = [1, 1.5, -1, 0, '1', ' 12 ', '0x10', '1e3', '', ' ', null, undefined, NaN, Infinity, -Infinity, '-1', '1,2', true, false, [], {}, '12px', '٣'];
    assert.deepEqual(await sameResults(page, '$.isNumeric(v)', '(typeof v === "number" || typeof v === "string") && !isNaN(v - parseFloat(v))', values), []);
    await page.close();
  } },
  { name: '표준 JS 대체: Number.isFinite(Number(값))은 $.isNumeric과 다르다(빈 글자, 공백, true)', run: async () => {
    const page = await pageWith({ jq4: false, stubs: false });
    const diff = await sameResults(page, '$.isNumeric(v)', 'Number.isFinite(Number(v))', ['', ' ', true, false, null, [], '0x10']);
    assert.ok(diff.length >= 4, `간단한 대체식은 값이 달라진다: ${JSON.stringify(diff.map((d) => d.value))}`);
    await page.close();
  } },
  { name: '표준 JS 대체: $.isArray → Array.isArray, $.isFunction → typeof 값 === "function"', run: async () => {
    const page = await pageWith({ jq4: false, stubs: false });
    assert.deepEqual(await sameResults(page, '$.isArray(v)', 'Array.isArray(v)', [[], [1], {}, '', null, undefined, 0, new Array(3)]), []);
    assert.deepEqual(await sameResults(page, '$.isFunction(v)', 'typeof v === "function"', null, '[function () {}, () => 1, class A {}, async () => {}, Math.max, null, undefined, {}, "f", 0]'), []);
    await page.close();
  } },
  { name: '표준 JS 대체: $.expr.filters.visible(요소) → offsetWidth, offsetHeight, getClientRects (jQuery 3.7.1의 정의와 같다)', run: async () => {
    const page = await pageWith({ jq4: false, stubs: false, html: `<div id="a" style="width:10px;height:10px">a</div><div id="b" style="display:none">b</div><div id="c" style="width:0;height:0"></div><div id="d" style="visibility:hidden;width:5px;height:5px">d</div><span id="e">e</span><div id="f" style="position:absolute;width:0;height:0;overflow:hidden"><i id="g"></i></div>` });
    const r = await page.evaluate(() => ['a', 'b', 'c', 'd', 'e', 'f', 'g'].map((id) => {
      const el = document.getElementById(id);
      return [id, jQuery.expr.filters.visible(el), !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length)];
    }));
    assert.deepEqual(r.filter(([, a, b]) => a !== b), [], JSON.stringify(r));
    await page.close();
  } },
  { name: '표준 JS 대체: Date.now()는 $.now()와 같은 종류의 값이다', run: async () => {
    const page = await pageWith({ jq4: false, stubs: false });
    const r = await page.evaluate(() => [Math.abs(jQuery.now() - Date.now()) < 50, typeof Date.now()]);
    assert.deepEqual(r, [true, 'number']);
    await page.close();
  } },
  { name: '하네스: 지워질 API가 실제로 없다(이후 시험이 표준 JS만 쓰는지 가리는 장치)', run: async () => {
    const page = await pageWith({});
    const left = await page.evaluate(() => ['trim', 'parseJSON', 'isNumeric', 'isFunction', 'isArray', 'isWindow', 'camelCase', 'nodeName', 'type', 'now', 'unique'].filter((k) => k in jQuery).concat('filters' in jQuery.expr ? ['expr.filters'] : []));
    assert.deepEqual(left, []);
    await page.close();
  } },
  { name: 'Viewer.js jQuery 래퍼: 지워진 환경에서 메서드 호출(문자열 옵션)이 동작한다(isFunction 없이)', run: async () => {
    const png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
    const page = await pageWith({ stubs: false, scripts: ['lib/viewerjs/viewer.js', 'lib/viewerjs/jquery-viewer.js'], html: `<div id="g"><img src="${png}" alt="x"></div>` });
    const r = await page.evaluate(() => { const g = $('#g'); g.viewer({ inline: false }); const hasViewer = !!g.data('viewer'); g.viewer('destroy'); return { hasViewer, destroyed: !g.data('viewer') }; });
    assert.deepEqual(r, { hasViewer: true, destroyed: true });
    assert.deepEqual(page.errors, []);
    await page.close();
  } },
];
