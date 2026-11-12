import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// 맥락 사이드바 안의 선택 단추 줄(홈, 즐겨찾기, 프로젝트, 최근 읽은 이슈)과 바로가기 구역의 모양을 실제 CSS로 시험한다(sidebar 브랜치).
// 보기(`data-shell-view`)가 home이면 맥락 메뉴, shortcuts이면 바로가기가 보인다. 옛 덮는 패널(#mySidenav)은 350px 폭을 전제로 했으므로 232px 사이드바 안에서 폭이 맞는지, 접기 단추가 맨 아래에 남는지를 잰다.
const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const items = Array.from({ length: 12 }, (_, i) => `<li class="user-li" data-location="/a/${i}"><a href="#">owner${i}/아주-긴-프로젝트-이름-${i}</a></li>`).join('');
const MENU = `<div class="col-lg-5 right-menu span-hard-wrap">
  <ul class="nav nav-tabs nm mb-3">
    <li class="nav-item myOrganizationList"><a href="#a" class="nav-link active">즐겨찾기</a></li>
    <li class="nav-item myProjectList"><a href="#b" class="nav-link">프로젝트</a></li>
    <li class="nav-item myRecentIssueList"><a href="#c" class="nav-link">최근 읽은 이슈</a></li>
    <li class="nav-item"><div><i class="yobicon-refresh refresh-button" role="button" tabindex="0"></i></div></li>
  </ul>
  <div class="tab-content tab-box"><div id="usermenu-tab-content-list" class="tab-content"><input class="search-input" type="text" placeholder="검색할 이름"><ul class="user-ul">${items}</ul></div></div>
  <div class="sidebar-bottom">Yona, 사랑으로 만들었습니다</div>
</div>`;
const SWITCH = `<div class="shell-sidebar-switch" role="tablist" aria-label="보기">
  <button type="button" role="tab" data-shell-switch="home" aria-selected="true">홈</button>
  <button type="button" role="tab" data-shell-switch="myOrganizationList" aria-selected="false">즐겨찾기</button>
  <button type="button" role="tab" data-shell-switch="myProjectList" aria-selected="false">프로젝트</button>
  <button type="button" role="tab" data-shell-switch="myRecentIssueList" aria-selected="false">최근 읽은 이슈</button></div>`;
const sidebar = (min = 600, view = 'home') => `<div id="main" class="main"><header class="gnb-outer"><div class="gnb-inner"><ul class="gnb-nav"><li class="gnb-switcher"><button class="shell-switcher-btn" data-shell-switcher>M</button></li></ul></div></header>
<div class="page-wrap-outer"><div class="shell"><nav class="shell-sidebar offcanvas-lg offcanvas-start" id="shellSidebar" data-shell-view="${view}"><div class="offcanvas-body">
  ${SWITCH}
  <div data-shell-pane="home"><div class="shell-sidebar-title d-none d-lg-block">홈</div>
  <ul class="nav nav-pills flex-column shell-sidebar-nav"><li class="nav-item"><a class="nav-link" href="#"><span class="shell-sidebar-label">프로젝트 목록</span></a></li><li class="nav-item"><a class="nav-link active" href="#"><span class="shell-sidebar-label">내 이슈</span></a></li></ul></div>
  <section class="shell-shortcuts" data-shell-pane="shortcuts" data-shell-shortcuts aria-label="바로가기">${MENU}</section>
</div></nav><div class="shell-main" style="min-height:${min}px">본문</div></div></div></div>`;

async function open(width = 1280, html = sidebar(), collapsed = false) {
  const page = await pageWith({ html, stubs: false });
  await page.setViewportSize({ width, height: 900 });
  if (collapsed) await page.evaluate(() => document.documentElement.setAttribute('data-sidebar', 'collapsed'));
  for (const css of ['bootstrap.css', 'usermenu.css', 'yobi.css']) await page.addStyleTag({ path: path.join(CSS, css) });
  await page.addStyleTag({ content: '*, *::before, *::after { transition: none !important; }' });
  return page;
}
const rect = (page, s) => page.evaluate((q) => { const e = document.querySelector(q); if (!e) return null; const r = e.getBoundingClientRect(); return { left: r.left, right: r.right, top: r.top, bottom: r.bottom, width: r.width, height: r.height, sw: e.scrollWidth, cw: e.clientWidth }; }, s);

export const tests = [
  {
    name: '선택 단추 줄은 사이드바 맨 위에서 네 단추가 한 줄로 다 보이고 홈이 선택된 모양이다',
    async run() {
      const page = await open();
      const info = await page.evaluate(() => {
        const sb = document.querySelector('.shell-sidebar').getBoundingClientRect(); const row = document.querySelector('.shell-sidebar-switch');
        const first = document.querySelector('.shell-sidebar .offcanvas-body').firstElementChild === row;
        return { first, sbLeft: sb.left, sbRight: sb.right, rowH: row.getBoundingClientRect().height, btns: [...row.querySelectorAll('button')].map((b) => { const r = b.getBoundingClientRect(); return { top: Math.round(r.top), left: r.left, right: r.right, h: r.height }; }) };
      });
      assert.equal(info.first, true, '단추 줄이 본문의 첫 요소이다');
      assert.equal(new Set(info.btns.map((b) => b.top)).size, 1, `단추가 여러 줄로 접혔다: ${JSON.stringify(info.btns.map((b) => b.top))}`);
      for (const [i, b] of info.btns.entries()) assert.ok(b.left >= info.sbLeft - 1 && b.right <= info.sbRight + 1, `단추 ${i}(${Math.round(b.left)}~${Math.round(b.right)})가 사이드바 밖으로 잘린다`);
      assert.ok(info.rowH <= 36, `줄 높이 ${info.rowH}px: 작은 단추 줄이다`);
      assert.ok(info.btns.every((b) => b.h >= 22), '단추 높이가 눌러지는 크기이다(22px 이상)');
      const sel = await page.evaluate(() => getComputedStyle(document.querySelector('[data-shell-switch=home]')).fontWeight);
      assert.ok(Number(sel) >= 600, `선택된 단추는 굵다(${sel})`);
    },
  },
  {
    name: '홈 보기: 맥락 메뉴가 보이고 바로가기 구역은 숨는다. 바로가기 보기: 반대이다',
    async run() {
      let page = await open(1280, sidebar(600, 'home'));
      assert.equal(await page.isVisible('[data-shell-pane=home] .shell-sidebar-nav'), true);
      assert.equal(await page.isVisible('.shell-shortcuts'), false);
      page = await open(1280, sidebar(600, 'shortcuts'));
      assert.equal(await page.isVisible('[data-shell-pane=home]'), false);
      assert.equal(await page.isVisible('.shell-shortcuts'), true);
    },
  },
  {
    name: '바로가기 보기에서는 구역이 사이드바 남는 높이를 모두 쓰고 사이드바 바닥까지 이어진다(접기 단추가 없다)',
    async run() {
      const page = await open(1280, sidebar(700, 'shortcuts'));
      const sb = await rect(page, '.shell-sidebar'); const host = await rect(page, '.shell-shortcuts'); const row = await rect(page, '.shell-sidebar-switch');
      assert.ok(host.top >= row.bottom - 1, `구역(${Math.round(host.top)})이 단추 줄(${Math.round(row.bottom)}) 아래에서 시작한다`);
      assert.ok(sb.bottom - host.bottom <= 14, `구역 아래 ${Math.round(host.bottom)}가 사이드바 아래 ${Math.round(sb.bottom)}까지 이어진다`);
      assert.ok(host.height >= 450, `구역 높이 ${Math.round(host.height)}px: 남는 높이를 쓴다`);
    },
  },
  {
    name: '바로가기 내용은 사이드바 폭 안에 들어가고 가로로 넘치지 않으며 긴 목록은 구역 안에서 스크롤된다',
    async run() {
      const page = await open(1280, sidebar(500, 'shortcuts'));
      const sb = await rect(page, '.shell-sidebar'); const menu = await rect(page, '.shell-shortcuts .right-menu'); const body = await rect(page, '.shell-sidebar .offcanvas-body');
      assert.ok(menu.left >= sb.left - 0.5 && menu.right <= sb.right + 0.5, `내용 ${Math.round(menu.left)}~${Math.round(menu.right)}가 사이드바 안에 있다`);
      assert.ok(body.sw <= body.cw + 1, `사이드바 본문이 가로로 넘친다 ${body.sw}/${body.cw}`);
      assert.ok(menu.width >= 180, `내용 폭 ${menu.width}px`);
      const ov = await page.evaluate(() => getComputedStyle(document.querySelector('.shell-shortcuts')).overflowY);
      assert.ok(['auto', 'scroll'].includes(ov), `구역 overflow-y ${ov}`);
    },
  },
  {
    name: '안쪽 탭 글자는 선택 단추 줄이 대신하므로 숨기고, 새로고침 아이콘은 남는다',
    async run() {
      const page = await open(1280, sidebar(600, 'shortcuts'));
      const r = await page.evaluate(() => ({ tabs: [...document.querySelectorAll('.shell-shortcuts .nav-tabs > li > a')].map((a) => a.getBoundingClientRect().height), refresh: document.querySelector('.shell-shortcuts .refresh-button').getBoundingClientRect().width }));
      assert.ok(r.tabs.every((h) => h === 0), `안쪽 탭이 보인다: ${r.tabs}`);
      assert.ok(r.refresh > 0, '새로고침 아이콘은 보인다');
    },
  },
  {
    name: '하단 문구는 구역 안에 흐름대로 놓이고 화면에 새어 나가지 않는다',
    async run() {
      const page = await open(1280, sidebar(600, 'shortcuts'));
      const pos = await page.evaluate(() => getComputedStyle(document.querySelector('.shell-shortcuts .sidebar-bottom')).position);
      const sb = await rect(page, '.shell-sidebar'); const f = await rect(page, '.shell-shortcuts .sidebar-bottom');
      assert.notEqual(pos, 'fixed'); assert.notEqual(pos, 'absolute');
      assert.ok(f.right <= sb.right + 0.5 && f.left >= sb.left - 0.5, `하단 문구가 사이드바 안에 있다`);
    },
  },
  {
    name: '접힌 띠(44px)에서는 선택 단추 줄과 바로가기 구역이 숨고, 보기와 상관없이 맥락 메뉴 아이콘만 남는다',
    async run() {
      const page = await open(1280, sidebar(600, 'shortcuts'), true);
      assert.equal(await page.isVisible('.shell-sidebar-switch'), false);
      assert.equal(await page.isVisible('.shell-shortcuts'), false);
      assert.equal(await page.isVisible('[data-shell-pane=home] .shell-sidebar-nav'), true, '바로가기 보기였어도 띠에서는 맥락 메뉴가 보인다');
    },
  },
  {
    name: '사이드바가 있는 화면에서도 햄버거는 모든 폭에서 보인다(넓은 폭에서는 사이드바를 접고 펴고, 좁은 폭에서는 서랍을 연다)',
    async run() {
      for (const w of [1280, 700]) {
        const page = await open(w);
        assert.equal(await page.isVisible('.shell-switcher-btn'), true, `폭 ${w}`);
      }
    },
  },
  {
    name: '사이드바가 없는 화면에서는 햄버거가 넓은 폭에서도 보인다(덮는 패널을 연다)',
    async run() {
      const page = await open(1280, `<div id="main" class="main"><header class="gnb-outer"><div class="gnb-inner"><ul class="gnb-nav"><li class="gnb-switcher"><button class="shell-switcher-btn" data-shell-switcher>M</button></li></ul></div></header><div class="page-wrap-outer">본문</div></div>`);
      assert.equal(await page.isVisible('.shell-switcher-btn'), true);
    },
  },
];
