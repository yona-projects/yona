// jQuery 정적 유틸(`$.each`, `$.extend`, `$.proxy`, `$.inArray`, `$.map`, `$.isEmptyObject`, `$.parseHTML`)을 표준 JS로 바꾼 것을 확인한다(S-13, A-427).
// 1) 원래 API와 표준 대체식이 같은 값을 내는지 값으로 비교한다. 다른 경우(undefined, 평탄화, 스크립트)는 다르다는 것을 시험으로 남기고, 앱 호출처가 그 경우를 만나지 않음을 아래에서 확인한다.
// 2) 앱 소스(common, service)에 정적 유틸 호출이 남아 있지 않다.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pageWith, JS } from './harness.mjs';

/** 페이지 안에서 [original]($ 인자)과 [replacement]를 [values]의 각 값에 적용해 JSON으로 같은지 본다. 다른 값의 목록을 돌려준다. */
async function diff(page, original, replacement, values) {
  return page.evaluate(({ original, replacement, values }) => {
    const vals = new Function(`return ${values};`)(); const o = new Function('$', 'v', `return ${original};`); const r = new Function('v', `return ${replacement};`); const out = [];
    vals.forEach((v, i) => { const a = JSON.stringify(o(window.jQuery, v)), b = JSON.stringify(r(v)); if (a !== b) out.push({ i, original: a, replacement: b }); });
    return out;
  }, { original, replacement, values });
}
const open = (html = '') => pageWith({ jq4: false, stubs: false, html });

// `service/`는 화면 모듈이 `esm/service/`로 옮겨 가 비어 있어 지웠다(shell-F14). 없는 디렉터리는 건너뛴다
const sourceFiles = () => ['common', 'service'].filter((d) => fs.existsSync(path.join(JS, d))).flatMap((d) => fs.readdirSync(path.join(JS, d)).filter((f) => f.endsWith('.js')).map((f) => path.join(JS, d, f)));

export const tests = [
  { name: '표준 JS 대체: $.extend(대상, 원본)은 undefined가 없는 값에서 Object.assign과 같다', run: async () => {
    const page = await open();
    assert.deepEqual(await diff(page, '$.extend({}, v[0], v[1])', 'Object.assign({}, v[0], v[1])', '[[{a:1,b:{c:2}},{b:{d:3},e:null}],[{}, {x:0,y:"",z:false}],[{id:1,text:"t"},{text:"u",id:null}],[{a:1},{a:NaN}]]'), []);
    assert.deepEqual(await diff(page, '$.extend(v[0], v[1])', 'Object.assign(v[0], v[1])', '[[{a:1},{b:2}],[{a:1},null],[{a:1},undefined]]'), [], '원본이 null, undefined여도 같다');
    await page.close();
  } },
  { name: '표준 JS 대체: $.extend는 원본의 undefined 값을 건너뛰지만 Object.assign은 복사한다(다른 경우, 호출처 확인은 아래)', run: async () => {
    const page = await open();
    const d = await diff(page, '$.extend({a:1,b:2}, v)', 'Object.assign({a:1,b:2}, v)', '[{a:undefined,b:3}]');
    assert.equal(d.length, 1, '다른 경우가 실제로 있다'); assert.equal(d[0].original, '{"a":1,"b":3}'); assert.equal(d[0].replacement, '{"b":3}', 'a가 undefined로 덮인다(JSON에서는 사라짐)');
    await page.close();
  } },
  { name: '표준 JS 대체: $.each(배열)과 배열.forEach는 같은 값을 같은 순서로 돌고 인자 순서만 (i, v)와 (v, i)로 다르다', run: async () => {
    const page = await open();
    assert.deepEqual(await diff(page, 'var o=[]; $.each(v, function(i, x){ o.push([i, x]); }); return o;'.replace(/^var o=\[\];/, '(function(){var o=[];').replace(/return o;$/, 'return o;})()'), '(function(){var o=[]; v.forEach(function(x, i){ o.push([i, x]); }); return o;})()', '[[],[1],["a","b","c"],[0,null,undefined,false]]'), []);
    await page.close();
  } },
  { name: '표준 JS 대체: $.each(객체)는 Object.entries와 같은 키 순서와 값이다(select2 선택 변화 비교)', run: async () => {
    const page = await open();
    assert.deepEqual(await diff(page, '(function(){var o=[]; $.each(v, function(k, x){ o.push([k, x]); }); return o;})()', '(function(){var o=[]; Object.entries(v).forEach(function(e){ o.push([e[0], e[1]]); }); return o;})()', '[{},{a:1,b:2},{"10":"x","2":"y","k":"z"},{id1:{id:"id1"},id2:{id:"id2"}}]'), []);
    await page.close();
  } },
  { name: '표준 JS 대체: $.each(유사 배열: options 모음, jQuery 객체, 배열)은 Array.from(...).forEach와 같다', run: async () => {
    const page = await open('<select id="s"><option value="a" selected>A</option><option value="b">B</option></select><p class="t">1</p><p class="t">2</p>');
    const r = await page.evaluate(() => {
      const a = [], b = []; $.each($('#s')[0].options, (i, o) => a.push(o.value + o.selected)); Array.from($('#s')[0].options).forEach((o) => b.push(o.value + o.selected));
      const c = [], d = []; $.each($('.t'), (_, el) => c.push(el.textContent)); Array.from($('.t')).forEach((el) => d.push(el.textContent));
      return [JSON.stringify(a) === JSON.stringify(b), JSON.stringify(c) === JSON.stringify(d), a.length, c.length];
    });
    assert.deepEqual(r, [true, true, 2, 2]); await page.close();
  } },
  { name: '표준 JS 대체: $.map은 값마다 객체나 숫자를 돌려줄 때 Array.map과 같고, null이나 배열을 돌려주면 다르다(호출처는 객체와 parseInt만 돌려준다)', run: async () => {
    const page = await open();
    assert.deepEqual(await diff(page, '$.map(v, function(x){ return {id: x}; })', 'v.map(function(x){ return {id: x}; })', '[[],[1,2],["a"]]'), []);
    assert.deepEqual(await diff(page, '$.map(v, function(s){ return parseInt(s, 10); })', 'v.map(function(s){ return parseInt(s, 10); })', '[["@@ -1,2 +3,4 @@","1","2","3","4"],["x","","7"]]'), []);
    assert.equal((await diff(page, '$.map(v, function(x){ return x > 1 ? null : [x, x]; })', 'v.map(function(x){ return x > 1 ? null : [x, x]; })', '[[1,2]]')).length, 1, 'null과 배열이면 다르다');
    // 정규식 match 배열: 일치하지 않은 묶음(undefined)에도 콜백이 불린다
    assert.deepEqual(await diff(page, '$.map("@@ -1 +3 @@".match(/@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@/), function(s){ return parseInt(s, 10); })', '"@@ -1 +3 @@".match(/@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@/).map(function(s){ return parseInt(s, 10); })', '[0]'), []);
    await page.close();
  } },
  { name: '표준 JS 대체: $.inArray(값, 배열) !== -1은 배열.includes(값)과 같다', run: async () => {
    const page = await open();
    assert.deepEqual(await diff(page, '$.inArray(v[0], v[1]) !== -1', 'v[1].includes(v[0])', '[["md",["md","markdown"]],["txt",["md"]],["",[]],["1",[1]],[1,[1]]]'), []); await page.close();
  } },
  { name: '표준 JS 대체: !$.isEmptyObject(객체)는 Object.keys(객체).length > 0과 같다(오류 모음은 항상 객체)', run: async () => {
    const page = await open();
    assert.deepEqual(await diff(page, '!$.isEmptyObject(v)', 'Object.keys(v).length > 0', '[{}, {name:["x"]}, {url:[]}]'), []); await page.close();
  } },
  { name: '표준 JS 대체: $.proxy(fn, 대상, 고정 인자)는 fn.bind(대상, 고정 인자)와 같은 대상과 인자 순서로 불린다', run: async () => {
    const page = await open();
    const r = await page.evaluate(() => { function f(a, b) { return [this.id, a, b]; } const ctx = { id: 'c' }; return [JSON.stringify($.proxy(f, ctx, 1)(2)) === JSON.stringify(f.bind(ctx, 1)(2)), JSON.stringify(f.bind(ctx, 'ns')({ type: 'change' }).slice(0, 2))]; });
    assert.deepEqual(r, [true, '["c","ns"]']); await page.close();
  } },
  { name: '표준 JS 대체: $.parseHTML(조각)은 <template>으로 읽고 script를 지운 것과 같은 노드를 준다(<tr> 조각과 글자도 같다)', run: async () => {
    const page = await open();
    const r = await page.evaluate(() => {
      const viaTemplate = (html) => { const t = document.createElement('template'); t.innerHTML = html; t.content.querySelectorAll('script').forEach((s) => s.remove()); return Array.from(t.content.childNodes); };
      const ser = (nodes) => nodes.map((n) => n.nodeType === 1 ? n.outerHTML : '#' + n.nodeType + n.textContent).join('|');
      return ['<div class="a">x</div><span>y</span>', '<tr><td>1</td></tr>', ' <p>t</p> ', '글자만', '', '<p>a</p><script>alert(1)</script><p>b</p>', '<li class="l"><a href="#">k</a></li>'].map((h) => [ser($.parseHTML(h)), ser(viaTemplate(h))]).filter(([a, b]) => a !== b);
    });
    assert.deepEqual(r, []); await page.close();
  } },
  { name: '표준 JS 대체: 문서 전체(html, head, body)를 받아도 DOMParser로 읽은 #id의 안쪽이 $.parseHTML로 찾은 것과 같다', run: async () => {
    const page = await open();
    const r = await page.evaluate(() => { const html = '<!doctype html><html><head><title>t</title><script>alert(1)</script></head><body><div id="review-area"><b>안</b><script>alert(2)</script></div></body></html>';
      const a = $('<div>').append($.parseHTML(html)).find('#review-area').html(); const b = new DOMParser().parseFromString(html, 'text/html').querySelector('#review-area'); b.querySelectorAll('script').forEach((s) => s.remove()); return [a, b.innerHTML]; });
    assert.equal(r[0], r[1]); await page.close();
  } },
  { name: '앱 소스(common, service)에는 jQuery 정적 유틸(each, extend, proxy, inArray, map, isEmptyObject, parseHTML) 호출이 없다', run: async () => {
    const rx = /(?:\$|jQuery)\.(each|extend|proxy|inArray|map|isEmptyObject|parseHTML)\(/;
    const hits = sourceFiles().flatMap((f) => fs.readFileSync(f, 'utf8').split('\n').map((line, i) => (rx.test(line) && !/^\s*(\/\/|\*)/.test(line) ? `${path.basename(f)}:${i + 1} ${line.trim().slice(0, 70)}` : null)).filter(Boolean));
    assert.deepEqual(hits, []);
  } },
  { name: '원래 메일 숨기기(OriginalMessage.hide): 구분선과 그 뒤를 숨기고 "..." 단추로 펼친다', run: async () => {
    const page = await pageWith({ stubs: true, html: '<div id="m" data-via-email><p id="a">안녕</p><div id="line">---- Original Message ----</div><p id="q1">인용1</p><p id="q2">인용2</p></div><div id="n" data-via-email><p id="plain">그냥 글</p></div>', scripts: ['esm/OriginalMessage.js'] });
    await page.evaluate(() => yona.OriginalMessage.hide($('[data-via-email]')));
    const vis = () => page.evaluate(() => ['a', 'line', 'q1', 'q2', 'plain'].map((id) => getComputedStyle(document.getElementById(id)).display !== 'none'));
    assert.deepEqual(await vis(), [true, false, false, false, true]);
    assert.equal(await page.evaluate(() => document.querySelectorAll('#m button').length + document.querySelectorAll('#n button').length), 1, '구분선이 있는 메일에만 단추');
    await page.click('#m button'); assert.deepEqual(await vis(), [true, true, true, true, true]); await page.click('#m button'); assert.deepEqual(await vis(), [true, false, false, false, true]);
    assert.deepEqual(page.errors, []); await page.close();
  } },
];
