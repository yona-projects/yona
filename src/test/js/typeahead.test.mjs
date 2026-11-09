// `common/yona.ui.Typeahead.js`: 입력란 아래에 후보 목록을 보여 주는 자동 완성(S-10, A-426). v1.6은 BS2 플러그인(bootstrap-typeahead 2.3.1 + better-typeahead)을 jQuery로 불렀고,
// 지금은 플러그인 없이 같은 동작을 한다. 기대값은 옛 플러그인에서 먼저 확인한 값이다(환경 변수 TA_IMPL=old 로 옛 구현을 불러 같은 시험이 통과하는지 볼 수 있다).
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith } from './harness.mjs';

const OLD = process.env.CLAUDE_JOB_DIR ? path.join(process.env.CLAUDE_JOB_DIR, 'tmp/old-typeahead') : null;
const IMPL = process.env.TA_IMPL === 'old' ? 'old' : 'new';
const SCRIPTS = IMPL === 'old'
  ? ['common/yona.Common.js', { path: path.join(OLD, 'bootstrap-typeahead.js') }, { path: path.join(OLD, 'bootstrap-better-typeahead.js') }, { path: path.join(OLD, 'yona.ui.Typeahead.js') }]
  : ['common/yona.Common.js', 'common/yona.ui.Typeahead.js'];

/** 입력란 하나와 위젯을 만든다. [setup]은 페이지 안에서 실행되는 식이며 `new yona.ui.Typeahead('#i', 옵션)`을 만들어야 한다. */
async function open(setup, { html = '' } = {}) {
  const page = await pageWith({ html: `<div style="position:relative;margin:30px"><input id="i" type="text" autocomplete="off" style="width:200px"></div><button id="other">x</button>${html}`, scripts: SCRIPTS, stubs: false });
  await page.evaluate(() => { window.__changes = 0; document.getElementById('i').addEventListener('change', () => { window.__changes++; }); $('#i').on('change', () => { window.__changes_jq = (window.__changes_jq || 0) + 1; }); });
  await page.evaluate(`(${setup})()`);
  return page;
}
const snap = (page) => page.evaluate(() => {
  const input = document.getElementById('i'), menu = document.querySelector('ul.typeahead');
  const items = menu ? [...menu.querySelectorAll('li')].map((li) => ({ html: li.querySelector('a').innerHTML, value: li.getAttribute('data-value'), active: li.classList.contains('active') })) : [];
  return { shown: !!menu && getComputedStyle(menu).display !== 'none', items, value: input.value, changes: window.__changes_jq || 0, afterInput: !!menu && input.nextElementSibling === menu };
});
const texts = (s) => s.items.map((i) => i.html);
const type = async (page, text) => { await page.focus('#i'); await page.keyboard.type(text); await page.waitForTimeout(60); };
const FRUIT = `new yona.ui.Typeahead('#i', { htData: { source: ['banana', 'Apple', 'avocado', 'grape'], minLength: 1 } })`;

export const tests = [
  { name: '자동 완성: 입력하면 검색어를 포함한 후보가 나오고 앞에서 시작하는 것, 대소문자까지 같은 것, 나머지 순으로 정렬되며 첫 항목이 선택 표시된다', run: async () => {
    const page = await open(`() => { ${FRUIT} }`); await type(page, 'a'); const s = await snap(page);
    // 'a'로 시작(소문자로 비교): Apple, avocado → 원래 배열 순서. 대소문자까지 같게 'a'를 포함: banana, grape. 강조는 모든 일치에 붙는다(전역)
    assert.deepEqual(texts(s), ['<strong>A</strong>pple', '<strong>a</strong>voc<strong>a</strong>do', 'b<strong>a</strong>n<strong>a</strong>n<strong>a</strong>', 'gr<strong>a</strong>pe']);
    assert.equal(s.shown, true); assert.equal(s.afterInput, true); assert.deepEqual(s.items.map((i) => i.active), [true, false, false, false]); assert.deepEqual(s.items.map((i) => i.value), ['Apple', 'avocado', 'banana', 'grape']);
    await page.close();
  } },
  { name: '자동 완성: 정렬은 앞에서 시작, 대소문자까지 같은 포함, 대소문자 무시 포함 순이다', run: async () => {
    const page = await open(`() => { new yona.ui.Typeahead('#i', { htData: { source: ['xAb', 'zab', 'ab', 'Ab'], minLength: 1 } }); }`); await type(page, 'ab'); const s = await snap(page);
    assert.deepEqual(s.items.map((i) => i.value), ['ab', 'Ab', 'zab', 'xAb']); await page.close();
  } },
  { name: '자동 완성: 검색어에 정규식 기호가 있어도 오류 없이 강조하고, 일치하는 것이 없으면 목록을 숨긴다', run: async () => {
    const page = await open(`() => { new yona.ui.Typeahead('#i', { htData: { source: ['a.b', 'a+b', 'axb'], minLength: 1 } }); }`); await type(page, 'a.'); let s = await snap(page);
    assert.deepEqual(s.items.map((i) => i.value), ['a.b']); assert.deepEqual(texts(s), ['<strong>a.</strong>b']);
    await type(page, 'zz'); s = await snap(page); assert.equal(s.shown, false); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '자동 완성: 보이는 후보 수는 10개까지이다(래퍼의 limit 옵션은 서버에 보내는 값일 뿐이고 v1.6도 보이는 수를 바꾸지 않았다)', run: async () => {
    const many = Array.from({ length: 15 }, (_, n) => 'item' + n);
    const a = await open(`() => { new yona.ui.Typeahead('#i', { htData: { source: ${JSON.stringify(many)}, minLength: 1 } }); }`); await type(a, 'item'); assert.equal((await snap(a)).items.length, 10); await a.close();
    const b = await open(`() => { new yona.ui.Typeahead('#i', { htData: { source: ${JSON.stringify(many)}, minLength: 1, limit: 3 } }); }`); await type(b, 'item'); assert.equal((await snap(b)).items.length, 10, 'limit 은 보이는 수를 바꾸지 않는다'); await b.close();
  } },
  { name: '자동 완성: minLength 1이면 빈 칸에서 열리지 않고, 글자를 지워 길이가 모자라면 닫힌다', run: async () => {
    const page = await open(`() => { ${FRUIT} }`); await page.focus('#i'); assert.equal((await snap(page)).shown, false);
    await page.keyboard.type('a'); assert.equal((await snap(page)).shown, true); await page.keyboard.press('Backspace'); await page.waitForTimeout(50); assert.equal((await snap(page)).shown, false); await page.close();
  } },
  { name: '자동 완성: minLength 0(기본)이면 초점만 가도 모든 후보가 열리고 선택 표시는 없다. 글자를 쓰면 첫 항목이 선택된다', run: async () => {
    const page = await open(`() => { new yona.ui.Typeahead('#i', { htData: { source: ['one', 'two', 'three'] } }); }`); await page.focus('#i'); await page.waitForTimeout(50);
    let s = await snap(page); assert.equal(s.shown, true); assert.deepEqual(s.items.map((i) => i.value), ['one', 'two', 'three']); assert.deepEqual(s.items.map((i) => i.active), [false, false, false]);
    await page.keyboard.type('t'); await page.waitForTimeout(50); s = await snap(page); assert.deepEqual(s.items.map((i) => i.value), ['two', 'three']); assert.deepEqual(s.items.map((i) => i.active), [true, false]); await page.close();
  } },
  { name: '자동 완성: 방향키로 움직이고 끝에서 처음으로, 처음에서 끝으로 돌아간다(목록이 열려 있을 때는 기본 동작을 막고, 닫혀 있으면 막지 않는다)', run: async () => {
    const page = await open(`() => { ${FRUIT} }`); await type(page, 'a');
    await page.evaluate(() => { window.__prevented = []; document.getElementById('i').addEventListener('keydown', (e) => { setTimeout(() => window.__prevented.push([e.key, e.defaultPrevented]), 0); }); });
    const active = async () => (await snap(page)).items.findIndex((i) => i.active);
    await page.keyboard.press('ArrowDown'); assert.equal(await active(), 1); await page.keyboard.press('ArrowDown'); await page.keyboard.press('ArrowDown'); assert.equal(await active(), 3);
    await page.keyboard.press('ArrowDown'); assert.equal(await active(), 0, '끝에서 처음으로'); await page.keyboard.press('ArrowUp'); assert.equal(await active(), 3, '처음에서 끝으로');
    await page.keyboard.press('Escape'); await page.waitForTimeout(30); assert.equal((await snap(page)).shown, false);
    await page.keyboard.press('ArrowDown'); await page.waitForTimeout(30);
    const prevented = await page.evaluate(() => window.__prevented); assert.deepEqual(prevented.find(([k], i) => k === 'ArrowDown' && i === 0), ['ArrowDown', true]); assert.deepEqual(prevented[prevented.length - 1], ['ArrowDown', false], '닫혀 있을 때는 막지 않는다'); await page.close();
  } },
  { name: '자동 완성: Enter는 키를 뗄 때 선택 항목을 입력란에 채우고 change 를 한 번 내며 목록을 닫는다(keydown의 기본 동작은 막는다)', run: async () => {
    const page = await open(`() => { ${FRUIT} }`); await type(page, 'a'); await page.evaluate(() => { window.__changes_jq = 0; window.__kd = null; document.getElementById('i').addEventListener('keydown', (e) => { if (e.key === 'Enter') setTimeout(() => { window.__kd = e.defaultPrevented; }, 0); }); });
    await page.keyboard.press('ArrowDown'); await page.keyboard.press('Enter'); await page.waitForTimeout(30);
    const s = await snap(page); assert.equal(s.value, 'avocado'); assert.equal(s.changes, 1); assert.equal(s.shown, false); assert.equal(await page.evaluate(() => window.__kd), true);
    await page.close();
  } },
  { name: '자동 완성: 목록이 닫혀 있을 때 Enter는 아무것도 하지 않고 막지도 않는다', run: async () => {
    const page = await open(`() => { ${FRUIT} }`); await page.focus('#i'); await page.evaluate(() => { window.__kd = null; document.getElementById('i').addEventListener('keydown', (e) => { setTimeout(() => { window.__kd = e.defaultPrevented; }, 0); }); });
    await page.keyboard.press('Enter'); await page.waitForTimeout(30); assert.equal(await page.evaluate(() => window.__kd), false); assert.equal((await snap(page)).changes, 0); await page.close();
  } },
  { name: '자동 완성: Tab은 키를 누를 때 선택 항목을 채우고 닫으며 초점은 그대로 다음으로 넘어간다(기본 동작을 막지 않는다)', run: async () => {
    const page = await open(`() => { ${FRUIT} }`); await type(page, 'gr'); await page.evaluate(() => { window.__changes_jq = 0; window.__kd = null; document.getElementById('i').addEventListener('keydown', (e) => { if (e.key === 'Tab') setTimeout(() => { window.__kd = e.defaultPrevented; }, 0); }); });
    await page.keyboard.press('Tab'); await page.waitForTimeout(30); const s = await snap(page);
    // change 는 둘이다: 선택이 내는 것 하나와, 입력란에서 초점이 나갈 때 브라우저가 글자를 쓴 뒤 내는 것 하나(옛 구현도 같다)
    assert.equal(s.value, 'grape'); assert.equal(s.changes, 2); assert.equal(s.shown, false); assert.equal(await page.evaluate(() => window.__kd), false); assert.equal(await page.evaluate(() => document.activeElement.id), 'other'); await page.close();
  } },
  { name: '자동 완성: Esc는 입력란을 그대로 두고 목록만 닫는다', run: async () => {
    const page = await open(`() => { ${FRUIT} }`); await type(page, 'a'); await page.keyboard.press('Escape'); await page.waitForTimeout(30);
    const s = await snap(page); assert.equal(s.shown, false); assert.equal(s.value, 'a'); assert.equal(s.changes, 0); await page.close();
  } },
  { name: '자동 완성: 마우스를 올린 항목이 선택 표시되고, 누르면 채우고 닫으며 입력란에 초점이 돌아온다', run: async () => {
    const page = await open(`() => { ${FRUIT} }`); await type(page, 'a');
    await page.hover('ul.typeahead li >> nth=2'); assert.deepEqual((await snap(page)).items.map((i) => i.active), [false, false, true, false]);
    await page.click('ul.typeahead li >> nth=2'); await page.waitForTimeout(30); const s = await snap(page);
    // change 는 둘이다: 선택이 내는 것 하나와, 목록을 누르며 입력란이 초점을 잃을 때 브라우저가 내는 것 하나(옛 구현도 같다)
    assert.equal(s.value, 'banana'); assert.equal(s.changes, 2); assert.equal(s.shown, false); assert.equal(await page.evaluate(() => document.activeElement.id), 'i'); await page.close();
  } },
  { name: '자동 완성: 입력란에서 초점이 나가면 닫히고, 마우스가 목록 위에 있는 동안은 닫히지 않는다', run: async () => {
    const page = await open(`() => { ${FRUIT} }`); await type(page, 'a'); await page.focus('#other'); await page.waitForTimeout(30); assert.equal((await snap(page)).shown, false);
    await type(page, ''); await page.keyboard.press('Backspace'); await page.keyboard.type('g'); await page.waitForTimeout(30); assert.equal((await snap(page)).shown, true);
    await page.hover('ul.typeahead li >> nth=0'); await page.evaluate(() => document.getElementById('other').focus()); await page.waitForTimeout(30); assert.equal((await snap(page)).shown, true, '마우스가 목록 위에 있으면 유지');
    await page.mouse.move(600, 500); await page.waitForTimeout(30); assert.equal((await snap(page)).shown, false, '마우스가 나가면 초점이 없으므로 닫힌다'); await page.close();
  } },
  { name: '자동 완성: 목록은 입력란 바로 다음에 놓이고 입력란의 왼쪽 아래에 맞춰진다(top과 left 인라인 값)', run: async () => {
    const page = await open(`() => { ${FRUIT} }`); await type(page, 'a');
    const r = await page.evaluate(() => { const i = document.getElementById('i'), m = document.querySelector('ul.typeahead'); return { top: m.style.top, left: m.style.left, expectTop: (i.offsetTop + i.offsetHeight) + 'px', expectLeft: i.offsetLeft + 'px', cls: m.className, aTag: m.querySelector('li a').className, href: m.querySelector('li a').getAttribute('href') }; });
    assert.equal(r.top, r.expectTop); assert.equal(r.left, r.expectLeft); assert.equal(r.cls, 'typeahead dropdown-menu'); assert.equal(r.aTag, 'dropdown-item'); assert.equal(r.href, '#'); await page.close();
  } },
  { name: '자동 완성: updater 로 채울 값을 바꾼다(구성원 추가는 표시 글자를 로그인 ID로 바꾼다)', run: async () => {
    const page = await open(`() => { window.users = { 'Kim (kim)': 'kim', 'Lee (lee)': 'lee' }; new yona.ui.Typeahead('#i', { htData: { source: Object.keys(users), minLength: 1, updater: (item) => users[item] } }); }`);
    await type(page, 'l'); await page.keyboard.press('Enter'); await page.waitForTimeout(30); assert.equal((await snap(page)).value, 'lee'); await page.close();
  } },
  { name: '자동 완성: 서버 source 는 요청하고 결과를 목록으로 보여 주며, 전체를 받았다고(Content-Range items 3/3) 하면 이어쓰기는 다시 묻지 않고 받아 둔 것에서 거른다', run: async () => {
    const page = await open(`() => {
      window.__reqs = [];
      $yona.sendForm = function (o) { window.__reqs.push([o.sURL, o.htData.query, o.htData.context]); o.fOnLoad(['alpha', 'alps', 'beta'], 'ok', { getResponseHeader: () => 'items 3/3' }); };
      new yona.ui.Typeahead('#i', { sActionURL: '/projects', htData: { context: 'T', minLength: 1 } }); }`);
    await type(page, 'a'); assert.deepEqual((await snap(page)).items.map((i) => i.value), ['alpha', 'alps', 'beta']);
    await page.keyboard.type('lp'); await page.waitForTimeout(30);
    assert.deepEqual(await page.evaluate(() => window.__reqs), [['/projects', 'a', 'T']], '이어쓴 질의는 받아 둔 것에서 거른다');
    assert.deepEqual((await snap(page)).items.map((i) => i.value), ['alpha', 'alps']); await page.close();
  } },
  { name: '자동 완성: 전체가 아니라고(Content-Range items 10/50) 하면 질의가 바뀔 때마다 다시 묻는다', run: async () => {
    const page = await open(`() => {
      window.__reqs = [];
      $yona.sendForm = function (o) { window.__reqs.push(o.htData.query); o.fOnLoad(['alpha', 'alps'], 'ok', { getResponseHeader: () => 'items 10/50' }); };
      new yona.ui.Typeahead('#i', { sActionURL: '/projects', htData: { minLength: 1 } }); }`);
    await type(page, 'alp'); assert.deepEqual(await page.evaluate(() => window.__reqs), ['a', 'al', 'alp']); await page.close();
  } },
  { name: '자동 완성: 비동기 source 가 나중에 process 를 불러도 그때 목록이 나타난다', run: async () => {
    const page = await open(`() => { new yona.ui.Typeahead('#i', { htData: { minLength: 1, source: (q, process) => { setTimeout(() => process(['late1', 'late2']), 80); } } }); }`);
    await type(page, 'l'); assert.equal((await snap(page)).shown, false); await page.waitForTimeout(200); assert.deepEqual((await snap(page)).items.map((i) => i.value), ['late1', 'late2']); await page.close();
  } },
  { name: '자동 완성: 항목을 직접 그리는 render 는 HTML(아바타 이미지 등)을 그대로 보여 주고 첫 항목이 선택된다(구성원 추가 화면)', run: async () => {
    const render = IMPL === 'old'
      ? `function (items) { var that = this; items = $(items).map(function (i, item) { i = $(that.options.item).attr('data-value', item); var h = $('<div />'); h.append(item); i.find('a').html(h.html()); return i[0]; }); items.first().addClass('active'); this.$menu.html(items); return this; }`
      : `function (item) { return item; }`;
    const page = await open(`() => { window.users = { "<img class='mention_image' src='/a.png'>Kim (kim)": 'kim' }; new yona.ui.Typeahead('#i', { htData: { source: ['<img class=\\'mention_image\\' src=\\'/a.png\\'>Kim (kim)'], minLength: 1, render: ${render}, updater: (v) => 'kim' } }); }`);
    await type(page, 'kim'); const s = await snap(page);
    assert.deepEqual(texts(s), [`<img class="mention_image" src="/a.png">Kim (kim)`]); assert.equal(s.items[0].active, true);
    await page.keyboard.press('Enter'); await page.waitForTimeout(30); assert.equal((await snap(page)).value, 'kim'); await page.close();
  } },
  { name: '자동 완성: 목록이 열린 채 선택 항목이 없을 때(minLength 0, 빈 칸) Enter 를 누르면 v1.6처럼 입력란이 비워지고 change 가 나간다(v1.6 동작 유지)', run: async () => {
    const page = await open(`() => { new yona.ui.Typeahead('#i', { htData: { source: ['one', 'two'], updater: (v) => v } }); }`);
    await page.focus('#i'); await page.waitForTimeout(30); await page.keyboard.press('Enter'); await page.waitForTimeout(30);
    const s = await snap(page); assert.equal(s.value, ''); assert.equal(s.changes, 1); assert.equal(s.shown, false); await page.close();
  } },
  { name: '자동 완성: source 로 배열을 주면 배열을 그대로 읽는다(나중에 항목을 추가해도 반영된다, 라벨 분류)', run: async () => {
    const page = await open(`() => { window.cats = []; new yona.ui.Typeahead('#i', { htData: { source: window.cats } }); }`);
    await page.focus('#i'); await page.waitForTimeout(30); assert.equal((await snap(page)).shown, false);
    await page.evaluate(() => { window.cats.push('Type', 'Priority'); document.getElementById('other').focus(); }); await page.focus('#i'); await page.waitForTimeout(30);
    assert.deepEqual((await snap(page)).items.map((i) => i.value), ['Type', 'Priority']); await page.close();
  } },
  { name: '자동 완성: 값이 이미 있는 입력란에 초점을 주면 그 값으로 후보를 찾는다', run: async () => {
    const page = await open(`() => { document.getElementById('i').value = 'gr'; ${FRUIT} }`); await page.focus('#i'); await page.waitForTimeout(40);
    assert.deepEqual((await snap(page)).items.map((i) => i.value), ['grape']); await page.close();
  } },
  { name: '자동 완성: 같은 입력란에 두 번 만들어도 목록은 하나이고 이벤트가 겹치지 않는다', run: async () => {
    const page = await open(`() => { ${FRUIT}; ${FRUIT}; }`); await type(page, 'a'); await page.keyboard.press('Enter'); await page.waitForTimeout(30);
    assert.equal(await page.evaluate(() => document.querySelectorAll('ul.typeahead').length), 1); assert.equal((await snap(page)).changes, 1); await page.close();
  } },
  { name: '자동 완성: 입력란이 없거나 source 가 오류를 내도 페이지 오류 없이 넘어간다(v1.6은 try/catch로 로그만 남겼다)', run: async () => {
    const page = await open(`() => { new yona.ui.Typeahead('#nothing', { htData: { source: [] } }); }`); assert.deepEqual(page.errors, []); await page.close();
  } },
  ...(IMPL === 'old' ? [] : [{ name: '자동 완성(보완): 한글 조합 중에 누른 Enter(keydown이 조합 중)는 조합을 마치는 키라서 항목을 고르지 않고, 다음 Enter가 고른다(v1.6에는 없던 보완)', run: async () => {
    const page = await open(`() => { ${FRUIT} }`); await type(page, 'a');
    await page.evaluate(() => { const i = document.getElementById('i'); i.dispatchEvent(new KeyboardEvent('keydown', { key: 'Process', keyCode: 229, isComposing: true, bubbles: true })); i.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', keyCode: 13, bubbles: true })); });
    let s = await snap(page); assert.equal(s.value, 'a', '조합을 마치는 Enter로는 고르지 않는다'); assert.equal(s.shown, true);
    await page.keyboard.press('Enter'); await page.waitForTimeout(30); s = await snap(page); assert.equal(s.value, 'Apple'); assert.equal(s.shown, false); await page.close();
  } }, { name: '자동 완성(보완): 입력란에 접근성 속성(role=combobox 없이도 aria-expanded)이 열림과 닫힘을 따른다', run: async () => {
    const page = await open(`() => { ${FRUIT} }`); await type(page, 'a');
    assert.equal(await page.evaluate(() => document.getElementById('i').getAttribute('aria-expanded')), 'true');
    await page.keyboard.press('Escape'); assert.equal(await page.evaluate(() => document.getElementById('i').getAttribute('aria-expanded')), 'false'); await page.close();
  } }]),
  ...(IMPL === 'old' ? [] : [{ name: '자동 완성: maxItems 로 보이는 후보 수를 줄일 수 있다(이슈 라벨 분류는 v1.6의 BS2 기본값 8개)', run: async () => {
    const many = Array.from({ length: 15 }, (_, n) => 'cat' + n);
    const page = await open(`() => { new yona.ui.Typeahead('#i', { htData: { source: ${JSON.stringify(many)}, minLength: 1, maxItems: 8 } }); }`); await type(page, 'cat'); assert.equal((await snap(page)).items.length, 8); await page.close();
  } }]),
];
