// 화면 구석에 잠깐 떴다 사라지는 알림 상자(`esm/ui/Toast.js`)와 드롭다운(`yona.ui.Dropdown.js`)을 시험한다(S-08, A-423).
// Toast 는 jQuery 와 접두사 이벤트(webkitTransitionEnd) 없이 표준 DOM 과 transitionend 를 쓴다. Dropdown 의 휠 막기는 CSS(overscroll-behavior)로 한다.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pageWith, JS, ROOT } from './harness.mjs';

const TPL = '\n<div class="toast" tabindex="-1">\n  <div class="btn-dismiss"><button type="button" class="btn-transparent">&times;</button></div>\n  <div class="center-text"><span class="v"></span><div class="msg"></div></div>\n</div>\n';
const NL2BR = { content: '$yona.nl2br = function (t) { return typeof t === "string" ? t.split("\\n").join("<br>") : t; };' };

async function open({ transition = true } = {}) {
  const page = await pageWith({ html: '<style>.toast{opacity:1;' + (transition ? 'transition:opacity .05s;' : '') + '}</style><div id="box"></div>', scripts: [NL2BR, 'esm/ui/Toast.js'] });
  await page.evaluate((tpl) => { window.toasts = new yona.ui.Toast('#box', { sTplToast: tpl }); }, TPL);
  return page;
}
const count = (page) => page.evaluate(() => document.querySelectorAll('#box .toast').length);

export const tests = [
  { name: 'Toast: push 는 알림을 맨 위에 쌓고 메시지의 HTML 과 줄바꿈을 살려 보여 주며 보이게(opacity 1) 한다', run: async () => {
    const page = await open();
    await page.evaluate(() => { toasts.push('첫째', 0); toasts.push('둘째<br><b>굵게</b>\n셋째째', 0); });
    const r = await page.evaluate(() => Array.from(document.querySelectorAll('#box .toast')).map((t) => [t.querySelector('.msg').innerHTML, t.style.opacity]));
    assert.deepEqual(r, [['둘째<br><b>굵게</b><br>셋째째', '1'], ['첫째', '1']]);
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: 'Toast: 템플릿에 앞뒤 줄바꿈과 공백이 있어도 만들어진다', run: async () => {
    const page = await open(); await page.evaluate(() => toasts.push('x', 0));
    assert.equal(await count(page), 1); assert.ok(await page.evaluate(() => !!document.querySelector('#box .toast .btn-dismiss button'))); await page.close();
  } },
  { name: 'Toast: 누르면 지워진다', run: async () => {
    const page = await open(); await page.evaluate(() => { toasts.push('a', 0); toasts.push('b', 0); });
    await page.click('#box .toast >> nth=0'); assert.equal(await count(page), 1); await page.close();
  } },
  { name: 'Toast: 시간(밀리초)을 주면 그 뒤 투명해지고 전환이 끝나면 지워진다', run: async () => {
    const page = await open(); await page.evaluate(() => toasts.push('곧 사라짐', 80));
    assert.equal(await count(page), 1); await page.waitForTimeout(30); assert.equal(await count(page), 1, '아직 남아 있다');
    await page.waitForFunction(() => document.querySelectorAll('#box .toast').length === 0, null, { timeout: 3000 });
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: 'Toast: 시간이 없거나 0 이면 그대로 남는다. clear 는 모두 지운다', run: async () => {
    const page = await open(); await page.evaluate(() => { toasts.push('a'); toasts.push('b', 0); }); await page.waitForTimeout(250);
    assert.equal(await count(page), 2); await page.evaluate(() => toasts.clear()); assert.equal(await count(page), 0); await page.close();
  } },
  { name: 'Toast: 사라지는 도중에 눌러 먼저 지워져도 오류가 없다', run: async () => {
    const page = await open(); await page.evaluate(() => toasts.push('x', 30)); await page.waitForTimeout(20);
    await page.evaluate(() => document.querySelector('#box .toast').click()); await page.waitForTimeout(300);
    assert.equal(await count(page), 0); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: 'Toast: 실제 $yona.notify 를 거쳐 #tplYobiToast 템플릿으로 만들어진다', run: async () => {
    const page = await pageWith({ html: '<div id="yobiToasts"></div><script type="text/x-yona-template" id="tplYobiToast">' + TPL + '</script>', scripts: ['common/yona.Common.js', 'esm/ui/Toast.js'], stubs: false });
    await page.evaluate(() => { $yona.notify('안녕\n하세요', 0, '제목'); });
    const html = await page.evaluate(() => document.querySelector('#yobiToasts .msg').innerHTML);
    assert.equal(html, '<strong>제목</strong><br>안녕<br>하세요'); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: 'Toast: jQuery 와 접두사 이벤트를 쓰지 않는다', run: async () => {
    const text = fs.readFileSync(path.join(JS, 'esm/ui/Toast.js'), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
    for (const gone of ['webkitTransitionEnd', '$(', '.bind(', '.css(']) assert.ok(!text.includes(gone), `${gone} 가 남아 있다`);
  } },
  // ---- Dropdown
  { name: 'Dropdown: 휠 처리에 비표준 mousewheel 이벤트를 쓰지 않고 CSS overscroll-behavior: contain 이 대신한다', run: async () => {
    const js = fs.readFileSync(path.join(JS, 'esm/ui/Dropdown.js'), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
    assert.ok(!js.includes('mousewheel') && !js.includes('_onScrollList'), 'mousewheel 처리가 남아 있다');
    const scss = fs.readFileSync(path.join(ROOT, 'app/assets/stylesheets/scss/_yobiUI.scss'), 'utf8');
    assert.match(scss, /btn-group\[data-name\][^{]*\.dropdown-menu\s*\{[^}]*overscroll-behavior:\s*contain/);
    // 새 규칙을 끼워 넣다가 앞의 선택자 목록(.btn-group > .btn, .dropdown-menu, .popover 의 글자 크기)을 끊으면 단추 라벨 글자가 커진다
    assert.ok(scss.includes('.btn-group > .btn,\n.btn-group > .dropdown-menu,\n.btn-group > .popover {\n  font-size: 0.75rem;\n}'), '글자 크기 규칙의 선택자 목록이 끊겼다');
  } },
  { name: 'Dropdown: 항목을 고르면 라벨, 숨은 입력, active, 변경 콜백이 처리되고 data-value 없는 항목은 무시한다', run: async () => {
    const html = '<form id="f"><div class="btn-group" id="dd" data-name="state"><button class="d-label">-</button><ul class="dropdown-menu"><li data-value="open" data-selected="true"><a class="dropdown-item"><b>열림</b></a></li><li data-value="closed"><a>닫힘</a></li><li class="divider"><span>구분</span></li></ul></div></form>';
    const page = await pageWith({ html, scripts: ['esm/ui/Dropdown.js'] });
    const r = await page.evaluate(async () => {
      const dd = new yona.ui.Dropdown({ elContainer: '#dd' }); const changes = []; dd.onChange((v) => changes.push(v));
      const out = { 기본: [document.querySelector('.d-label').textContent, dd.getValue(), document.querySelector('input[name=state]').value] };
      $('#dd li[data-value=closed]').trigger('click'); await new Promise((r) => setTimeout(r, 20));
      out.고름 = [document.querySelector('.d-label').textContent, dd.getValue(), document.querySelector('input[name=state]').value, $('#dd li.active').attr('data-value'), changes.slice()];
      $('#dd li.divider').trigger('click'); await new Promise((r) => setTimeout(r, 20)); out.무시 = [dd.getValue(), changes.length];
      out.값으로 = [dd.selectByValue('open'), dd.getValue(), dd.selectByValue('none')];
      out.라벨에dropdownitem없음 = $('.d-label .dropdown-item').length;
      return out;
    });
    assert.deepEqual(r.기본, ['열림', 'open', 'open']); assert.deepEqual(r.고름, ['닫힘', 'closed', 'closed', 'closed', ['closed']]);
    assert.deepEqual(r.무시, ['closed', 1]); assert.deepEqual(r.값으로, [true, 'open', false]); assert.equal(r.라벨에dropdownitem없음, 0);
    assert.deepEqual(page.errors, []); await page.close();
  } },
];
