// 셸 사이드바 이동(D-136): swap-safe 표지(data-swap-safe)가 있는 화면끼리는 본문(.shell-main)과 사이드바만 바꾸고, 그 밖에는 일반 이동으로 물러난다.
import assert from 'node:assert/strict';
import { buildTs, openTs } from './tsmodule.mjs';

const LIBS = `<script src="/assets/javascripts/lib/htmx.js"></script><script>window.yona = window.yona || {};</script><script src="/assets/javascripts/common/yona.Htmx.js"></script>`;
const NAV = (active) => `<nav class="shell-sidebar" id="shellSidebar" hx-target=".shell-main" hx-select=".shell-main" hx-select-oob="#shellSidebar" hx-swap="outerHTML show:window:top">`
  + ['a', 'b', 'c'].map((k) => `<a class="nav-link${k === active ? ' active' : ''}" href="/${k}">${k.toUpperCase()}</a>`).join('') + `</nav>`;
const MODULE = `<script type="module">import { initNavigation } from 'yona/shell/navigation.js'; initNavigation(); window.__same = true; window.__ready = true;</script>`;
const page = (key, safe) => `<!doctype html><head><title>${key} 화면</title><script type="importmap">{"imports":{"yona/":"/assets/javascripts/esm/"}}</script>${LIBS}</head><body><div class="shell">${NAV(key)}<div class="shell-main">${safe ? '<span data-swap-safe hidden></span>' : ''}${key}본문</div></div>${MODULE}</body>`;
const PAGES = { '/a': page('a', true), '/b': page('b', true), '/c': page('c', false) };

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
];
