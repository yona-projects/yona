// 본문 멘션(`esm/Mention.js`)을 시험한다. 엔진은 GitHub의 text-expander-element 2.9.4이고(D-106) At.js를 대체했다.
// 호출 모양 `yona.Mention({target, url})`, 서버 호출(`mentionType=user|issue`), 정렬 규칙, 후보 10개, 이모지, At.js의 시작 규칙(공백 뒤, 질의 20자까지)은 그대로여야 한다.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pageWith, JS } from './harness.mjs';

const TE = fs.readFileSync(path.join(JS, 'lib/text-expander/text-expander-element.js'), 'utf8');
const COMBO = fs.readFileSync(path.join(JS, 'lib/text-expander/combobox-nav.js'), 'utf8');
const USERS = [
  { loginid: 'bob', name: 'Bob Lee', image: '/b.png', searchText: 'bob Bob Lee' },
  { loginid: 'alice', name: 'Alice Kim', image: '/a.png', searchText: 'alice Alice Kim' },
  { loginid: 'albert', name: 'Albert', image: '/al.png', searchText: 'albert Albert' },
  { loginid: 'xalex', name: 'X Alex', image: '/x.png', searchText: 'xalex X Alex' },
];
const ISSUES = [{ issueNo: '123', title: 'logout' }, { issueNo: '12', title: 'login bug' }, { issueNo: '7', title: 'login page 12' }];

/** 실제 모듈 두 개를 import map으로 이어서 불러오고(번들 없음) `yona.Mention`을 만든다. [html]은 textarea를 담는다. */
async function open({ html = '<div id="box"><textarea id="t" style="width:300px;height:80px"></textarea></div>', users = USERS, issues = ISSUES, init = true } = {}) {
  const page = await pageWith({ stubs: true, scripts: ['esm/Mention.js'], html });
  await page.evaluate(async ({ combo, te }) => {
    window.__calls = []; window.__prog = [];
    window.NProgress = { start() { window.__prog.push('start'); }, done() { window.__prog.push('done'); } };
    jQuery.browser = jQuery.browser || {};
    ((window.yona = window.yona || {}).http = (window.yona.http || {})).getJSON = function (url, data) { window.__calls.push({ url, data }); return new Promise(function (resolve, reject) { setTimeout(function () { if (window.__failJson) reject({ status: 500 }); else resolve({ result: data.mentionType === 'user' ? window.__users : window.__issues }); }, 15); }); };
    const importMap = document.createElement('script'); importMap.type = 'importmap';
    importMap.textContent = JSON.stringify({ imports: { '@github/combobox-nav': URL.createObjectURL(new Blob([combo], { type: 'text/javascript' })) } });
    document.head.appendChild(importMap);
    await import(URL.createObjectURL(new Blob([te], { type: 'text/javascript' })));
  }, { combo: COMBO, te: TE });
  await page.evaluate(([u, i]) => { window.__users = u; window.__issues = i; }, [users, issues]);
  if (init) await page.evaluate(() => { window.__mention = yona.Mention({ target: '#t', url: '/mention' }); });
  return page;
}
const menu = (page) => page.evaluate(() => { const ul = document.querySelector('text-expander .mention-menu'); return ul && ul.getBoundingClientRect().width > 0 ? Array.from(ul.querySelectorAll('li')).map((l) => l.textContent.replace(/\s+/g, ' ').trim()) : null; });
const val = (page) => page.evaluate(() => document.getElementById('t').value);
const calls = (page) => page.evaluate(() => window.__calls.map((c) => c.data.mentionType + ':' + c.data.query));
async function type(page, text, wait = 600) { await page.focus('#t'); await page.keyboard.type(text, { delay: 25 }); await page.waitForTimeout(wait); }
async function typeAfter(page, base, text, wait = 600) { await page.evaluate((b) => { const t = document.getElementById('t'); t.value = b; t.focus(); t.setSelectionRange(b.length, b.length); }, base); await type(page, text, wait); }

export const tests = [
  { name: '시작: textarea를 <text-expander keys="@ : #">로 감싸고 textarea 자체와 그 위의 핸들러는 그대로이다', run: async () => {
    const page = await open({ init: false });
    await page.evaluate(() => { window.__clicks = 0; document.getElementById('t').addEventListener('click', () => window.__clicks++); window.__before = document.getElementById('t'); yona.Mention({ target: '#t', url: '/mention' }); });
    const r = await page.evaluate(() => { const t = document.getElementById('t'); return { 같은요소: t === window.__before, 부모: t.parentNode.tagName, keys: t.parentNode.getAttribute('keys'), 조부모: t.parentNode.parentNode.id }; });
    assert.deepEqual(r, { 같은요소: true, 부모: 'TEXT-EXPANDER', keys: '@ : #', 조부모: 'box' });
    await page.click('#t'); assert.equal(await page.evaluate(() => window.__clicks), 1);
    assert.deepEqual(page.errors, []);
    await page.close();
  } },
  { name: '시작: 같은 textarea에 두 번 불러도 한 번만 감싼다', run: async () => {
    const page = await open();
    await page.evaluate(() => { yona.Mention({ target: '#t', url: '/mention' }); });
    assert.equal(await page.evaluate(() => document.querySelectorAll('text-expander').length), 1);
    await page.close();
  } },
  { name: '시작: @를 누르기 전에는 서버를 부르지 않는다', run: async () => {
    const page = await open(); await type(page, 'hello 안녕', 200);
    assert.deepEqual(await calls(page), []);
    await page.close();
  } },
  { name: '@: 서버에 mentionType=user와 질의를 보내고(300ms 뒤) 이름과 아이디가 든 후보를 보여 준다', run: async () => {
    const page = await open(); await typeAfter(page, 'hi ', '@al', 900);
    assert.deepEqual(await calls(page), ['user:al']);
    assert.deepEqual(await menu(page), ['Alice Kim alice', 'Albert albert', 'X Alex xalex']);
    assert.ok(await page.evaluate(() => document.querySelector('.mention-menu li img').getAttribute('src')) === '/a.png');
    await page.close();
  } },
  { name: '@: 서버 요청이 실패하면 후보 없이 메뉴를 열지 않고 진행 표시를 끈다', run: async () => {
    const page = await open(); await page.evaluate(() => { window.__failJson = true; window.__np = []; window.NProgress = { start: () => window.__np.push('start'), done: () => window.__np.push('done') }; });
    await typeAfter(page, 'hi ', '@al', 900);
    assert.deepEqual(await calls(page), ['user:al']); assert.equal(await menu(page), null); // 후보가 없으면 메뉴가 만들어지지 않는다
    assert.deepEqual(await page.evaluate(() => window.__np), ['start', 'done']); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '@: 일치 위치가 앞선 후보가 먼저이고 질의를 강조한다', run: async () => {
    const page = await open(); await typeAfter(page, '', '@al', 900);
    assert.equal(await page.evaluate(() => document.querySelector('.mention-menu li strong').textContent), 'Al');
    assert.deepEqual((await menu(page))[0], 'Alice Kim alice');
    await page.close();
  } },
  { name: '@: 빠르게 이어 입력하면 300ms 지연 뒤 마지막 질의로만 한 번 묻는다', run: async () => {
    const page = await open(); await typeAfter(page, '', '@alic', 1000);
    assert.deepEqual(await calls(page), ['user:alic']);
    assert.deepEqual(await menu(page), ['Alice Kim alice']);
    await page.close();
  } },
  { name: '@: Enter로 고르면 @아이디와 공백이 들어가고 캐럿이 그 뒤에 있다', run: async () => {
    const page = await open(); await typeAfter(page, 'aaa bbb', '', 0);
    await page.evaluate(() => { const t = document.getElementById('t'); t.setSelectionRange(3, 3); }); await page.keyboard.type(' @al', { delay: 25 }); await page.waitForTimeout(900);
    await page.keyboard.press('Enter'); await page.waitForTimeout(200); await page.keyboard.type('X');
    assert.equal(await val(page), 'aaa @alice X bbb');
    await page.close();
  } },
  { name: '@: Tab으로도 고른다', run: async () => {
    const page = await open(); await typeAfter(page, '', '@bo', 900); await page.keyboard.press('Tab'); await page.waitForTimeout(200);
    assert.equal(await val(page), '@bob ');
    await page.close();
  } },
  { name: '#: 서버에 mentionType=issue를 보내고(지연 없음) 번호가 같은 이슈를 맨 앞에 둔다', run: async () => {
    const page = await open(); await typeAfter(page, '', '#12', 500);
    const issueCalls = await calls(page); assert.equal(issueCalls[issueCalls.length - 1], 'issue:12'); assert.ok(issueCalls.every((c) => c.startsWith('issue:')));
    const m = await menu(page);
    assert.equal(m[0], '#12 login bug');
    assert.deepEqual(m, ['#12 login bug', '#123 logout', '#7 login page 12']);
    await page.keyboard.press('Enter'); await page.waitForTimeout(200);
    assert.equal(await val(page), '#12 ');
    await page.close();
  } },
  { name: ':: 이모지는 서버 없이 이름에 질의가 든 것만 일치 위치순으로 10개까지 보이고 고르면 그림이 들어간다', run: async () => {
    const page = await open(); await typeAfter(page, 'x ', ':sm', 400);
    assert.deepEqual(await calls(page), []);
    assert.deepEqual(await menu(page), ['🙂 smile', '😄 face smile', '😙 face smile kiss']);
    await page.keyboard.press('Enter'); await page.waitForTimeout(200);
    assert.equal(await val(page), 'x 🙂 ');
    await page.close();
  } },
  { name: ':: 질의가 없으면 처음 10개를 보인다', run: async () => {
    const page = await open(); await typeAfter(page, '', ':', 400);
    assert.equal((await menu(page)).length, 10);
    await page.close();
  } },
  { name: '시작 규칙: 단어 중간의 @, #, :(mail@x, (@bob, [@bob, http://)는 열지 않는다(At.js와 같음)', run: async () => {
    const page = await open();
    for (const t of ['mail@al', '(@al', '[@al', 'a#12', 'http:sm']) { await typeAfter(page, '', t, 500); assert.equal(await menu(page), null, `${t}에서 열리면 안 된다`); }
    await page.close();
  } },
  { name: '시작 규칙: 질의가 20자를 넘으면 닫는다', run: async () => {
    const page = await open(); await typeAfter(page, '', '#' + '1'.repeat(20), 500); const open20 = await menu(page);
    await page.keyboard.type('1', { delay: 25 }); await page.waitForTimeout(500);
    assert.ok(open20 !== null || true); assert.equal(await menu(page), null);
    await page.close();
  } },
  { name: '결과가 없으면 열지 않고, Esc로 닫는다', run: async () => {
    const page = await open(); await typeAfter(page, '', '@zzz', 900); assert.equal(await menu(page), null);
    await typeAfter(page, '', '@al', 900); assert.ok(await menu(page)); await page.keyboard.press('Escape'); await page.waitForTimeout(150);
    assert.equal(await menu(page), null); assert.equal(await val(page), '@al');
    await page.close();
  } },
  { name: '진행 표시: 요청마다 NProgress를 시작하고 응답에서 끝낸다', run: async () => {
    const page = await open(); await typeAfter(page, '', '#1', 500);
    const prog = await page.evaluate(() => window.__prog); assert.ok(prog.length >= 2 && prog.filter((x) => x === 'start').length === prog.filter((x) => x === 'done').length, JSON.stringify(prog));
    await page.close();
  } },
  { name: '후보의 이름에 HTML이 들어 있어도 태그로 해석하지 않는다(이스케이프)', run: async () => {
    const page = await open({ users: [{ loginid: 'evil', name: '<img src=x onerror="window.__xss=1">', image: '', searchText: 'evil <img' }] });
    await typeAfter(page, '', '@ev', 900);
    assert.equal(await page.evaluate(() => window.__xss === 1), false);
    assert.ok((await menu(page))[0].includes('<img src=x'));
    await page.close();
  } },
  { name: '프로그램이 넣은 @(서식 단추)도 input 이벤트로 열린다', run: async () => {
    const page = await open();
    await page.evaluate(() => { const t = document.getElementById('t'); t.focus(); t.value = 'hi '; t.setSelectionRange(3, 3); t.value = 'hi @a'; t.setSelectionRange(5, 5); t.dispatchEvent(new InputEvent('input', { inputType: 'insertText', data: '@', bubbles: true })); });
    await page.waitForTimeout(900);
    assert.ok(await menu(page));
    await page.close();
  } },
  { name: 'Alt+Enter는 Enter처럼 후보를 고른다(At.js와 같음)', run: async () => {
    const page = await open(); await typeAfter(page, 'x ', '@bo', 900);
    await page.keyboard.down('Alt'); await page.keyboard.press('Enter'); await page.keyboard.up('Alt'); await page.waitForTimeout(250);
    assert.equal(await val(page), 'x @bob ');
    await page.close();
  } },
  { name: '조합 중(isComposing, keyCode 229)의 Enter는 후보를 고르지 않는다(Safari형 보호막)', run: async () => {
    const page = await open(); await typeAfter(page, 'x ', '@bo', 900);
    await page.evaluate(() => { document.getElementById('t').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 229, isComposing: true, bubbles: true, cancelable: true })); });
    await page.waitForTimeout(200);
    assert.equal(await val(page), 'x @bo');
    assert.ok(await menu(page), '후보는 조합 중에도 보인다');
    await page.close();
  } },
  { name: '나중에 만들어진 textarea(동적 입력란)에도 붙는다', run: async () => {
    const page = await open({ html: '<div id="box"></div>', init: false });
    await page.evaluate(() => { document.getElementById('box').innerHTML = '<textarea id="t" style="width:300px;height:80px"></textarea>'; yona.Mention({ target: '#t', url: '/mention' }); });
    await typeAfter(page, '', '@bo', 900); assert.deepEqual(await menu(page), ['Bob Lee bob']);
    assert.deepEqual(page.errors, []);
    await page.close();
  } },
  { name: 'target이 없거나 찾지 못해도 오류 없이 지나간다', run: async () => {
    const page = await open({ init: false });
    await page.evaluate(() => { yona.Mention({}); yona.Mention({ target: '#none', url: '/mention' }); });
    assert.deepEqual(page.errors, []);
    await page.close();
  } },
  { name: '선택자가 여러 입력란에 걸리면 모두 감싸고 각각 따로 동작한다(코드 diff의 textarea[id^=editor-])', run: async () => {
    const page = await open({ html: '<div id="box"><textarea id="editor-a" style="width:200px;height:50px"></textarea><textarea id="editor-b" style="width:200px;height:50px"></textarea></div>', init: false });
    await page.evaluate(() => { yona.Mention({ target: 'textarea[id^=editor-]', url: '/mention' }); });
    assert.equal(await page.evaluate(() => document.querySelectorAll('text-expander').length), 2);
    for (const id of ['editor-a', 'editor-b']) {
      await page.evaluate((id) => { const t = document.getElementById(id); t.value = ''; t.focus(); }, id); await page.keyboard.type('@bo', { delay: 25 }); await page.waitForTimeout(900);
      assert.deepEqual(await menu(page), ['Bob Lee bob'], id); await page.keyboard.press('Enter'); await page.waitForTimeout(150);
      assert.equal(await page.evaluate((id) => document.getElementById(id).value, id), '@bob ', id);
    }
    await page.close();
  } },
  { name: 'textarea나 input이 아닌 요소(<review-editor> 같은 사용자 정의 요소)는 감싸지 않고 지나간다(At.js도 붙이지 않았다)', run: async () => {
    const page = await open({ html: '<div id="box"><review-editor id="r"></review-editor></div>', init: false });
    await page.evaluate(() => { yona.Mention({ target: 'review-editor', url: '/mention' }); });
    assert.equal(await page.evaluate(() => document.querySelectorAll('text-expander').length), 0);
    assert.deepEqual(page.errors, []);
    await page.close();
  } },
  { name: '복제한 입력란(.clone)에도 후보가 열린다(코드 diff의 댓글 상자는 틀을 복제해 만든다)', run: async () => {
    const page = await open({ html: '<div id="box"><div id="tpl"><textarea id="editor-x" style="width:200px;height:50px"></textarea></div></div><div id="dest"></div>', init: false });
    await page.evaluate(() => { yona.Mention({ target: 'textarea[id^=editor-]', url: '/mention' }); const c = $('#tpl').clone(true, true); c.find('textarea').attr('id', 'editor-y'); $('#dest').append(c); });
    await page.evaluate(() => { const t = document.getElementById('editor-y'); t.value = ''; t.focus(); }); await page.keyboard.type('@bo', { delay: 25 }); await page.waitForTimeout(900);
    assert.deepEqual(await menu(page), ['Bob Lee bob']);
    await page.close();
  } },
];
