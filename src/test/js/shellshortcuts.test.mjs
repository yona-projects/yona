// 햄버거 패널(즐겨찾기, 프로젝트, 최근 읽은 이슈)을 맥락 사이드바 안으로 옮기는 TS 모듈 `shell/shortcuts`를 실제 Chromium에서 시험한다(sidebar 브랜치).
// 사이드바가 있는 화면에서는 패널의 내용(.right-menu)을 사이드바의 [data-shell-shortcuts] 자리로 옮기고 덮는 패널(#mySidenav)은 치운다.
// 사이드바가 없는 화면에서는 아무것도 하지 않아 덮는 패널이 그대로 쓰인다. 사이드바가 htmx로 교체되면 옮긴 내용을 새 자리에 다시 붙인다.
import assert from 'node:assert/strict';
import { buildTs, openTs } from './tsmodule.mjs';

const OVERLAY = `<div id="mySidenav" class="sidenav"><div class="right-menu"><ul class="nav-tabs"><li class="myOrganizationList"><a href="#myOrganizationList">즐겨찾기</a></li><li class="myProjectList"><a href="#myProjectList">프로젝트</a></li><li class="myRecentIssueList"><a href="#myRecentIssueList">최근</a></li></ul><div id="usermenu-tab-content-list"><input class="search-input"></div></div></div>`;
const BAR = `<button type="button" id="ham" data-shell-switcher>햄버거</button>`;
const SWITCH = `<div class="shell-sidebar-switch" role="tablist"><button type="button" role="tab" data-shell-switch="home" aria-selected="true">홈</button><button type="button" role="tab" data-shell-switch="myOrganizationList" aria-selected="false">즐겨찾기</button><button type="button" role="tab" data-shell-switch="myProjectList" aria-selected="false">프로젝트</button><button type="button" role="tab" data-shell-switch="myRecentIssueList" aria-selected="false">최근 읽은 이슈</button></div>`;
const SIDEBAR = `<nav class="shell-sidebar offcanvas-lg" id="shellSidebar" data-shell-view="home"><div class="offcanvas-body">${SWITCH}<div data-shell-pane="home"><ul class="shell-sidebar-nav"><li>항목</li></ul></div><section data-shell-pane="shortcuts" data-shell-shortcuts aria-label="바로가기"></section><button class="shell-sidebar-fold">접기</button></div></nav>`;
const MOD = `<script type="module">import { initShortcuts } from 'yona/shell/shortcuts.js'; window.__api = initShortcuts(); window.__ready = true;</script>`;

async function open(body, opts = {}) {
  buildTs();
  const { page, ctx } = await openTs(body, opts);
  await page.waitForFunction(() => window.__ready === true, null, { timeout: 5000 });
  return { page, ctx };
}

export const tests = [
  { name: '사이드바가 있으면 패널 내용(.right-menu)을 사이드바 자리로 옮기고 덮는 패널(#mySidenav)은 치운다. 안쪽 요소와 id는 그대로이다', run: async () => {
    const { page, ctx } = await open(BAR + OVERLAY + SIDEBAR + MOD);
    const r = await page.evaluate(() => ({
      overlay: !!document.getElementById('mySidenav'),
      inHost: !!document.querySelector('[data-shell-shortcuts] > .right-menu'),
      list: !!document.querySelector('[data-shell-shortcuts] #usermenu-tab-content-list'),
      tab: !!document.querySelector('[data-shell-shortcuts] .myProjectList'),
    }));
    assert.deepEqual(r, { overlay: false, inHost: true, list: true, tab: true });
    assert.deepEqual(page.errors, []); await ctx.close();
  } },
  { name: '사이드바가 없으면 아무것도 옮기지 않아 덮는 패널이 그대로 있다', run: async () => {
    const { page, ctx } = await open(BAR + OVERLAY + MOD);
    assert.equal(await page.evaluate(() => !!document.querySelector('#mySidenav > .right-menu')), true);
    assert.deepEqual(page.errors, []); await ctx.close();
  } },
  { name: '옮긴 내용은 같은 요소(핸들러가 달린 그대로)이다', run: async () => {
    const { page, ctx } = await open(BAR + OVERLAY + SIDEBAR + `<script>window.__marker = document.querySelector('.right-menu'); window.__marker.addEventListener('click', () => { window.__clicks = (window.__clicks || 0) + 1; });</script>` + MOD);
    assert.equal(await page.evaluate(() => document.querySelector('[data-shell-shortcuts] > .right-menu') === window.__marker), true);
    await page.evaluate(() => document.querySelector('.right-menu').dispatchEvent(new MouseEvent('click', { bubbles: true })));
    assert.equal(await page.evaluate(() => window.__clicks), 1); await ctx.close();
  } },
  { name: '사이드바가 htmx로 교체되어 자리가 새로 그려지면 옮긴 내용을 새 자리에 다시 붙인다', run: async () => {
    const { page, ctx } = await open(BAR + OVERLAY + SIDEBAR + MOD);
    await page.evaluate((html) => {
      window.__before = document.querySelector('.right-menu');
      const old = document.getElementById('shellSidebar'); const t = document.createElement('div'); t.innerHTML = html; old.replaceWith(t.firstElementChild);
      document.body.dispatchEvent(new CustomEvent('htmx:afterSettle', { bubbles: true }));
    }, SIDEBAR);
    const r = await page.evaluate(() => ({ same: document.querySelector('[data-shell-shortcuts] > .right-menu') === window.__before, count: document.querySelectorAll('.right-menu').length }));
    assert.deepEqual(r, { same: true, count: 1 });
    assert.deepEqual(page.errors, []); await ctx.close();
  } },
  { name: '좁은 폭(992 미만)에서 햄버거를 누르면 사이드바 서랍을 열고 옛 핸들러로는 넘기지 않는다', run: async () => {
    const { page, ctx } = await open(BAR + OVERLAY + SIDEBAR + `<script>window.bootstrap = { Offcanvas: { getOrCreateInstance: (el) => ({ toggle: () => { window.__toggled = (window.__toggled || 0) + 1; window.__el = el.id; } }) } }; document.getElementById('ham').addEventListener('click', () => { window.__old = (window.__old || 0) + 1; });</script>` + MOD);
    await page.setViewportSize({ width: 700, height: 800 });
    await page.click('#ham');
    assert.deepEqual(await page.evaluate(() => [window.__toggled, window.__el, window.__old || 0]), [1, 'shellSidebar', 0]);
    assert.deepEqual(page.errors, []); await ctx.close();
  } },
  { name: '넓은 폭에서는 햄버거를 가로채지 않는다(사이드바가 이미 보이므로 단추는 숨겨진다)', run: async () => {
    const { page, ctx } = await open(BAR + OVERLAY + SIDEBAR + `<script>window.bootstrap = { Offcanvas: { getOrCreateInstance: () => ({ toggle: () => { window.__toggled = 1; } }) } }; document.getElementById('ham').addEventListener('click', () => { window.__old = (window.__old || 0) + 1; });</script>` + MOD);
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.click('#ham');
    assert.deepEqual(await page.evaluate(() => [window.__toggled || 0, window.__old || 0]), [0, 1]); await ctx.close();
  } },
  { name: '선택 단추를 누르면 보기가 바뀐다: 홈은 맥락 메뉴, 나머지는 바로가기이고 눌린 단추만 선택 상태이다', run: async () => {
    const { page, ctx } = await open(BAR + OVERLAY + SIDEBAR + MOD);
    const st = () => page.evaluate(() => ({ view: document.getElementById('shellSidebar').getAttribute('data-shell-view'), selected: [...document.querySelectorAll('[data-shell-switch]')].map((b) => b.getAttribute('aria-selected')).join() }));
    assert.deepEqual(await st(), { view: 'home', selected: 'true,false,false,false' });
    await page.click('[data-shell-switch=myProjectList]');
    assert.deepEqual(await st(), { view: 'shortcuts', selected: 'false,false,true,false' });
    await page.click('[data-shell-switch=home]');
    assert.deepEqual(await st(), { view: 'home', selected: 'true,false,false,false' });
    assert.deepEqual(page.errors, []); await ctx.close();
  } },
  { name: '바로가기 단추를 누르면 안쪽의 같은 탭 링크를 눌러 준다(탭 전환과 Usermenu.js의 기억이 그대로 동작한다)', run: async () => {
    const { page, ctx } = await open(BAR + OVERLAY + SIDEBAR + `<script>window.__tabs = []; document.querySelectorAll('.right-menu .nav-tabs a').forEach((a) => a.addEventListener('click', (e) => { e.preventDefault(); window.__tabs.push(a.getAttribute('href')); }));</script>` + MOD);
    await page.click('[data-shell-switch=myRecentIssueList]'); await page.click('[data-shell-switch=myOrganizationList]');
    assert.deepEqual(await page.evaluate(() => window.__tabs), ['#myRecentIssueList', '#myOrganizationList']);
    await page.click('[data-shell-switch=home]');
    assert.deepEqual(await page.evaluate(() => window.__tabs), ['#myRecentIssueList', '#myOrganizationList'], '홈은 탭을 누르지 않는다'); await ctx.close();
  } },
  { name: '고른 보기는 기억되어 다음 화면과 사이드바 교체 뒤에도 이어진다', run: async () => {
    const { page, ctx } = await open(BAR + OVERLAY + SIDEBAR + MOD);
    await page.click('[data-shell-switch=myProjectList]');
    assert.equal(await page.evaluate(() => localStorage.getItem('shellSidebarView')), 'shortcuts');
    // htmx가 사이드바를 새로 그리면(보기는 서버가 홈으로 그린다) 기억한 보기를 다시 적용한다
    await page.evaluate((html) => { const old = document.getElementById('shellSidebar'); const t = document.createElement('div'); t.innerHTML = html; old.replaceWith(t.firstElementChild); document.body.dispatchEvent(new CustomEvent('htmx:afterSettle', { bubbles: true })); }, SIDEBAR);
    const r = await page.evaluate(() => ({ view: document.getElementById('shellSidebar').getAttribute('data-shell-view'), selected: [...document.querySelectorAll('[data-shell-switch]')].map((b) => b.getAttribute('aria-selected')).join() }));
    assert.deepEqual(r, { view: 'shortcuts', selected: 'false,false,true,false' });
    await page.click('[data-shell-switch=home]');
    assert.equal(await page.evaluate(() => localStorage.getItem('shellSidebarView')), 'home'); await ctx.close();
  } },
  { name: '기억한 보기와 탭이 있으면 처음부터 그 단추가 선택되어 있다', run: async () => {
    const { page, ctx } = await open(BAR + OVERLAY + SIDEBAR + MOD, { head: '<script>localStorage.setItem("shellSidebarView","shortcuts"); localStorage.setItem("sidebarActiveMenu","myRecentIssueList");</script>' });
    const r = await page.evaluate(() => ({ view: document.getElementById('shellSidebar').getAttribute('data-shell-view'), selected: [...document.querySelectorAll('[data-shell-switch]')].map((b) => b.getAttribute('aria-selected')).join() }));
    assert.deepEqual(r, { view: 'shortcuts', selected: 'false,false,false,true' }); await ctx.close();
  } },
];
