// 셸 사이드바 이동(D-136): swap-safe 표지(data-swap-safe)가 있는 화면끼리는 본문(.shell-main)과 사이드바만 바꾸고, 그 밖에는 일반 이동으로 물러난다.
import assert from 'node:assert/strict';
import { buildTs, openTs } from './tsmodule.mjs';

const LIBS = `<script src="/assets/javascripts/lib/htmx.js"></script><script>window.yona = window.yona || {};</script><script src="/assets/javascripts/common/yona.Htmx.js"></script>`;
const NAV = (active) => `<nav class="shell-sidebar" id="shellSidebar" hx-target=".shell-main" hx-select=".shell-main" hx-select-oob="#shellSidebar" hx-swap="outerHTML show:window:top">`
  + ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'].map((k) => `<a class="nav-link${k === active ? ' active' : ''}" href="/${k}">${k.toUpperCase()}</a>`).join('') + `</nav>`;
const MODULE = `<script type="module">import { initNavigation } from 'yona/shell/navigation.js'; initNavigation(); window.__same = true; window.__ready = true;</script>`;
const page = (key, safe) => `<!doctype html><head><title>${key} 화면</title><script type="importmap">{"imports":{"yona/":"/assets/javascripts/esm/"}}</script>${LIBS}</head><body><div class="shell">${NAV(key)}<div class="shell-main">${safe ? '<span data-swap-safe hidden></span>' : ''}${key}본문</div></div>${MODULE}</body>`;
const LEAVE = `<script type="module">import { onPageLeave } from 'yona/core/page.js'; window.__left = window.__left || 0; onPageLeave(() => { window.__left += 1; });</script>`;
const S2 = `<script src="/assets/javascripts/lib/select2/select2.js"></script><script>window.__s2loads = (window.__s2loads || 0) + 1; jQuery.fn.select2 = jQuery.fn.select2 || function () {};</script>`;
const CAL = `<script src="/assets/javascripts/lib/vanilla-calendar-pro/index.js"></script>`;
const MD = `<div class="markdown-wrap"><img src="data:image/gif;base64,R0lGODlhAQABAAAAACw="></div>`;
const WID = `<select data-toggle="select2"></select><input data-toggle="calendar">`;
const PAGES = { '/a': page('a', true), '/b': page('b', true), '/c': page('c', false), '/h': page('h', true).replace('h본문', 'h본문' + WID), '/g': page('g', true).replace('g본문', 'g본문' + MD), '/f': page('f', true).replace('f본문', 'f본문' + CAL), '/e': page('e', true).replace('e본문', 'e본문' + S2), '/d': page('d', true).replace('d본문', 'd본문' + LEAVE) };

async function open(start) {
  buildTs();
  const { page: p, ctx } = await openTs('', { pages: PAGES });
  await p.goto('http://ts.test' + start); await p.waitForFunction(() => window.__ready === true, null, { timeout: 5000 });
  return { p, ctx };
}
const main = (p) => p.textContent('.shell-main');
const active = (p) => p.evaluate(() => document.querySelector('nav.shell-sidebar a.active').textContent);

export const tests = [
  { name: 'swap-safe 화면에서 swap-safe 화면으로 가면 본문과 사이드바만 바뀌고 문서는 다시 불러오지 않는다', run: async () => {
    const { p, ctx } = await open('/a');
    await p.click('nav.shell-sidebar a[href="/b"]');
    await p.waitForFunction(() => document.querySelector('.shell-main').textContent.includes('b본문') && document.querySelector('nav.shell-sidebar a.active').textContent === 'B', null, { timeout: 5000 });
    assert.equal(await active(p), 'B'); assert.equal(new URL(p.url()).pathname, '/b');
    assert.equal(await p.title(), 'b 화면'); assert.equal(await p.evaluate(() => window.__same), true, '문서를 다시 불러오면 안 된다');
    await ctx.close();
  } },
  { name: '교체한 사이드바로도 계속 같은 방식으로 이동한다(속성을 다시 켠다)', run: async () => {
    const { p, ctx } = await open('/a');
    await p.click('nav.shell-sidebar a[href="/b"]'); await p.waitForFunction(() => document.querySelector('.shell-main').textContent.includes('b본문'));
    await p.click('nav.shell-sidebar a[href="/a"]'); await p.waitForFunction(() => document.querySelector('.shell-main').textContent.includes('a본문') && document.querySelector('nav.shell-sidebar a.active').textContent === 'A');
    assert.equal(await active(p), 'A'); assert.equal(await p.evaluate(() => window.__same), true); await ctx.close();
  } },
  { name: '응답이 swap-safe가 아니면 일반 이동으로 물러난다', run: async () => {
    const { p, ctx } = await open('/a');
    await p.click('nav.shell-sidebar a[href="/c"]');
    await p.waitForFunction(() => window.__ready === true && location.pathname === '/c', null, { timeout: 5000 });
    assert.equal(await main(p), 'c본문'); assert.equal(await p.evaluate(() => window.__same), true, '새 문서가 처음부터 그려졌다');
    await ctx.close();
  } },
  { name: '지금 화면이 swap-safe가 아니면 사이드바 이동을 가로채지 않는다(일반 이동)', run: async () => {
    const { p, ctx } = await open('/c');
    await p.evaluate(() => { window.__marker = 'old'; });
    await p.click('nav.shell-sidebar a[href="/a"]'); await p.waitForFunction(() => location.pathname === '/a' && window.__ready === true);
    assert.equal(await p.evaluate(() => window.__marker), undefined, '문서가 새로 열려야 한다'); await ctx.close();
  } },
  { name: 'htmx가 없는 화면에서도 오류 없이 지나간다', run: async () => {
    buildTs();
    const { page: p, ctx } = await openTs('<div class="shell-main"><span data-swap-safe hidden></span></div><script type="module">import { initNavigation } from "yona/shell/navigation.js"; initNavigation(); window.__ok = true;</script>');
    await p.waitForFunction(() => window.__ok === true, null, { timeout: 5000 }); assert.deepEqual(p.errors, []); await ctx.close();
  } },
  { name: '본문을 바꾸기 직전에 화면이 등록한 정리 함수를 한 번만 부른다', run: async () => {
    const { p, ctx } = await open('/a');
    await p.click('nav.shell-sidebar a[href="/d"]'); await p.waitForFunction(() => document.querySelector('.shell-main').textContent.includes('d본문') && document.querySelector('nav.shell-sidebar a.active').textContent === 'D', null, { timeout: 5000 });
    assert.equal(await p.evaluate(() => window.__left || 0), 0, 'd 화면에 들어올 때는 부르지 않는다(등록만 한다)');
    await p.click('nav.shell-sidebar a[href="/a"]'); await p.waitForFunction(() => document.querySelector('.shell-main').textContent.includes('a본문') && document.querySelector('nav.shell-sidebar a.active').textContent === 'A', null, { timeout: 5000 });
    assert.equal(await p.evaluate(() => window.__left), 1, '나갈 때 한 번');
    await p.click('nav.shell-sidebar a[href="/b"]'); await p.waitForFunction(() => document.querySelector('nav.shell-sidebar a.active').textContent === 'B', null, { timeout: 5000 });
    assert.equal(await p.evaluate(() => window.__left), 1, '이미 비운 함수를 다시 부르지 않는다');
    await ctx.close();
  } },
  { name: '일반 이동으로 물러날 때는 정리 함수를 부르지 않는다(문서가 새로 열리므로)', run: async () => {
    const { p, ctx } = await open('/d');
    await p.click('nav.shell-sidebar a[href="/c"]'); await p.waitForFunction(() => location.pathname === '/c' && window.__ready === true, null, { timeout: 5000 });
    assert.equal(await p.evaluate(() => window.__left || 0), 0); await ctx.close();
  } },
  { name: '이미 불러온 select2 라이브러리 스크립트는 본문 교체 때 다시 싣지 않는다', run: async () => {
    const { p, ctx } = await open('/a');
    await p.evaluate(() => { window.jQuery = window.jQuery || function () {}; window.jQuery.fn = window.jQuery.fn || {}; window.jQuery.fn.select2 = function () {}; });
    await p.click('nav.shell-sidebar a[href="/e"]'); await p.waitForFunction(() => document.querySelector('nav.shell-sidebar a.active').textContent === 'E', null, { timeout: 5000 });
    await p.waitForTimeout(300);
    assert.equal(await p.evaluate(() => document.querySelectorAll('script[src*="lib/select2/select2.js"]').length), 0, '라이브러리 태그가 다시 들어오면 안 된다');
    await ctx.close();
  } },
  { name: '이미 불러온 달력 라이브러리 스크립트도 본문 교체 때 다시 싣지 않는다', run: async () => {
    const { p, ctx } = await open('/a');
    await p.evaluate(() => { window.VanillaCalendarPro = { Calendar: function () {} }; });
    await p.click('nav.shell-sidebar a[href="/f"]'); await p.waitForFunction(() => document.querySelector('nav.shell-sidebar a.active').textContent === 'F', null, { timeout: 5000 });
    await p.waitForTimeout(300);
    assert.equal(await p.evaluate(() => document.querySelectorAll('script[src*="vanilla-calendar-pro/index.js"]').length), 0);
    await ctx.close();
  } },
  { name: '본문 교체로 들어온 마크다운 영역에도 이미지 보기를 한 번만 붙인다', run: async () => {
    const { p, ctx } = await open('/a');
    await p.evaluate(() => {
      window.__viewer = 0;
      window.jQuery = window.jQuery || function (el) { return { viewer() { window.__viewer += 1; }, each() { return this; } }; };
      window.jQuery.fn = window.jQuery.fn || {};
      window.jQuery.fn.viewer = function () { window.__viewer += 1; };
      const original = window.jQuery;
      window.jQuery = Object.assign(function (el) { return { viewer() { window.__viewer += 1; } }; }, original, { fn: original.fn });
    });
    await p.click('nav.shell-sidebar a[href="/g"]'); await p.waitForFunction(() => document.querySelector('nav.shell-sidebar a.active').textContent === 'G', null, { timeout: 5000 });
    await p.waitForTimeout(300);
    assert.equal(await p.evaluate(() => window.__viewer), 1);
    await p.click('nav.shell-sidebar a[href="/b"]'); await p.waitForFunction(() => document.querySelector('nav.shell-sidebar a.active').textContent === 'B', null, { timeout: 5000 });
    assert.equal(await p.evaluate(() => window.__viewer), 1, '다른 화면으로 가도 다시 붙이지 않는다');
    await ctx.close();
  } },
  { name: '본문 교체로 들어온 select2와 달력 입력란을 초기화한다(문서가 준비될 때 한 번만 도는 자동 초기화를 대신한다)', run: async () => {
    const { p, ctx } = await open('/a');
    await p.evaluate(() => {
      window.__w = { select2: 0, calendar: 0 };
      window.yona = window.yona || {}; window.yona.ui = { Select2() { window.__w.select2 += 1; }, Calendar() { window.__w.calendar += 1; } };
    });
    await p.click('nav.shell-sidebar a[href="/h"]'); await p.waitForFunction(() => document.querySelector('nav.shell-sidebar a.active').textContent === 'H', null, { timeout: 5000 });
    await p.waitForTimeout(300);
    assert.deepEqual(await p.evaluate(() => window.__w), { select2: 1, calendar: 1 });
    await p.click('nav.shell-sidebar a[href="/b"]'); await p.waitForFunction(() => document.querySelector('nav.shell-sidebar a.active').textContent === 'B', null, { timeout: 5000 });
    assert.deepEqual(await p.evaluate(() => window.__w), { select2: 1, calendar: 1 }, '위젯이 없는 화면으로 가도 다시 부르지 않는다');
    await ctx.close();
  } },
];
