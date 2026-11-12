import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// 맥락 사이드바 안에 들어온 바로가기(즐겨찾기, 프로젝트, 최근 읽은 이슈)의 모양을 실제 CSS로 시험한다(sidebar 브랜치).
// 옛 덮는 패널(#mySidenav)은 350px 폭을 전제로 했으므로, 232px 사이드바 안에서 폭이 맞는지, 탭이 한 줄인지, 접기 단추가 맨 아래에 남는지를 잰다.
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
const sidebar = (extra = '') => `<div id="main" class="main"><header class="gnb-outer"><div class="gnb-inner"><ul class="gnb-nav"><li class="gnb-switcher"><button class="shell-switcher-btn" data-shell-switcher>M</button></li></ul></div></header>
<div class="page-wrap-outer"><div class="shell"><nav class="shell-sidebar offcanvas-lg offcanvas-start" id="shellSidebar"><div class="offcanvas-body">
  <div class="shell-sidebar-title d-none d-lg-block">홈</div>
  <ul class="nav nav-pills flex-column shell-sidebar-nav"><li class="nav-item"><a class="nav-link" href="#"><span class="shell-sidebar-label">프로젝트 목록</span></a></li><li class="nav-item"><a class="nav-link active" href="#"><span class="shell-sidebar-label">내 이슈</span></a></li></ul>
  <section class="shell-shortcuts" data-shell-shortcuts aria-label="바로가기">${MENU}</section>
  <button type="button" class="shell-sidebar-fold d-none d-lg-flex"><span class="shell-sidebar-fold-label">접기</span></button>
</div></nav><div class="shell-main" style="min-height:${extra || 600}px">본문</div></div></div></div>`;

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
    name: '바로가기 내용은 사이드바 폭 안에 들어가고 가로로 넘치지 않는다',
    async run() {
      const page = await open();
      const sb = await rect(page, '.shell-sidebar'); const menu = await rect(page, '.shell-shortcuts .right-menu'); const body = await rect(page, '.shell-sidebar .offcanvas-body');
      assert.ok(menu.left >= sb.left - 0.5 && menu.right <= sb.right + 0.5, `내용 ${Math.round(menu.left)}~${Math.round(menu.right)}가 사이드바 ${Math.round(sb.left)}~${Math.round(sb.right)} 안에 있다`);
      assert.ok(body.sw <= body.cw + 1, `사이드바 본문이 가로로 넘친다 ${body.sw}/${body.cw}`);
      assert.ok(menu.width >= 180, `내용 폭 ${menu.width}px: 너무 좁게 눌리지 않는다`);
    },
  },
  {
    name: '탭 세 개와 새로고침 아이콘이 한 줄에 놓인다',
    async run() {
      const page = await open();
      const info = await page.evaluate(() => {
        const host = document.querySelector('.shell-shortcuts').getBoundingClientRect();
        return { hostRight: host.right, hostLeft: host.left, items: [...document.querySelectorAll('.shell-shortcuts .nav-tabs > li')].map((l) => { const r = l.getBoundingClientRect(); return { top: Math.round(r.top), left: Math.round(r.left), right: Math.round(r.right) }; }) };
      });
      assert.equal(new Set(info.items.map((i) => i.top)).size, 1, `탭 줄이 여러 줄로 접혔다: ${JSON.stringify(info.items)}`);
      // 한 줄이어도 구역 밖으로 밀려 잘리면 보이지 않는다(overflow 숨김). 모든 탭이 구역 안에 보여야 한다.
      for (const [i, it] of info.items.entries()) assert.ok(it.left >= info.hostLeft - 1 && it.right <= info.hostRight + 1, `탭 ${i}(${it.left}~${it.right})가 구역(${Math.round(info.hostLeft)}~${Math.round(info.hostRight)}) 밖으로 잘린다`);
    },
  },
  {
    name: '긴 목록은 구역 안에서 스크롤되고 접기 단추는 사이드바 맨 아래에 남는다',
    async run() {
      const page = await open(1280, sidebar(500));
      const sb = await rect(page, '.shell-sidebar'); const fold = await rect(page, '.shell-sidebar-fold'); const host = await rect(page, '.shell-shortcuts');
      const ov = await page.evaluate(() => getComputedStyle(document.querySelector('.shell-shortcuts')).overflowY);
      assert.ok(['auto', 'scroll'].includes(ov), `구역 overflow-y ${ov}`);
      assert.ok(Math.abs(fold.bottom - sb.bottom) <= 1, `접기 단추 아래 ${Math.round(fold.bottom)}, 사이드바 아래 ${Math.round(sb.bottom)}`);
      assert.ok(host.bottom <= fold.top + 1, `구역(${Math.round(host.bottom)})이 접기 단추(${Math.round(fold.top)})를 가리지 않는다`);
    },
  },
  {
    name: '하단 문구는 구역 안(사이드바 안)에 흐름대로 놓이고 화면에 새어 나가지 않는다',
    async run() {
      const page = await open();
      const pos = await page.evaluate(() => getComputedStyle(document.querySelector('.shell-shortcuts .sidebar-bottom')).position);
      const sb = await rect(page, '.shell-sidebar'); const f = await rect(page, '.shell-shortcuts .sidebar-bottom');
      assert.notEqual(pos, 'fixed'); assert.notEqual(pos, 'absolute');
      assert.ok(f.right <= sb.right + 0.5 && f.left >= sb.left - 0.5, `하단 문구가 사이드바 안에 있다 ${Math.round(f.left)}~${Math.round(f.right)}`);
    },
  },
  {
    name: '접힌 띠(44px)에서는 바로가기 구역이 숨고 접기 단추만 남는다',
    async run() {
      const page = await open(1280, sidebar(), true);
      assert.equal(await page.isVisible('.shell-shortcuts'), false);
      assert.equal(await page.isVisible('.shell-sidebar-fold'), true);
    },
  },
  {
    name: '사이드바가 있는 화면에서 햄버거는 넓은 폭(992 이상)에서 숨고 좁은 폭에서는 보인다',
    async run() {
      let page = await open(1280);
      assert.equal(await page.isVisible('.shell-switcher-btn'), false, '넓은 폭: 사이드바가 이미 바로가기를 담고 있다');
      page = await open(700);
      assert.equal(await page.isVisible('.shell-switcher-btn'), true, '좁은 폭: 햄버거가 서랍을 연다');
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
