import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// 상단 바 배치 시험이다. 컴파일된 실제 CSS(`./gradlew compileSass`가 만드는 build/generated/sass)를 불러 요소의 위치와 세로 중심을 잰다.
// 맨 왼쪽 햄버거는 사이드바를 접고 펴며(좁은 폭에서는 서랍), 전환 단추(즐겨찾기, 프로젝트, 최근 이슈 패널)는 로고 오른쪽의 별도 아이콘이다(A-487).
// 두 단추가 로고, 검색창과 같은 가운데 선에 놓이는지(A-480, 선생님 지적: 세로 정렬이 다른 항목과 달랐다), 순서가 맞는지,
// 사이드바가 없는 화면에서는 사이드바 햄버거가 숨겨지는지, 폰 폭에서 사용자 메뉴가 같은 줄에 남는지 확인한다.
const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');

const HAMBURGER = '<svg width="18" height="14" viewBox="0 0 18 14" aria-hidden="true"><path d="M0 1h18M0 7h18M0 13h18" stroke="currentColor" stroke-width="2" fill="none"/></svg>';
const SWITCH = '<svg width="18" height="14" viewBox="0 0 18 14" aria-hidden="true"><path d="M1 4h14M12 1l3 3-3 3M17 10H3M6 7l-3 3 3 3" stroke="currentColor" stroke-width="1.6" fill="none"/></svg>';

const bar = (withSidebar) => `<div id="main" class="main"><header class="gnb-outer"><div class="gnb-inner">
  <ul class="gnb-nav">
    <li class="gnb-sidebar-toggle"><button type="button" class="shell-sidebar-btn" data-shell-sidebar-toggle aria-controls="shellSidebar" aria-expanded="true" aria-label="사이드바">${HAMBURGER}</button></li>
    <li><a href="/" class="logo logo-letter">Y</a></li>
    <li class="gnb-switcher"><button type="button" class="shell-switcher-btn" data-shell-switcher aria-label="전환">${SWITCH}</button></li>
    <li class="gnb-projects"><a href="/projects">전체 목록</a></li>
    <li class="divider"></li>
    <li class="gnb-feedback"><a href="https://example.com" target="_blank">개발팀에게 문의하기</a></li>
    <li><form class="gnb-search-form"><div class="search-box"><input type="text" name="keyword"><button type="submit"><i class="yobicon-search"></i></button></div></form></li>
  </ul>
  <ul class="gnb-usermenu" style="width:222px;height:40px"><li class="gnb-usermenu-item"><a href="#" class="user-item-btn">내 이슈</a></li></ul>
</div></header>
${withSidebar ? '<div class="page-wrap-outer"><div class="shell"><nav class="shell-sidebar offcanvas-lg offcanvas-start" id="shellSidebar"><div class="offcanvas-body">메뉴</div></nav><div class="shell-main">본문</div></div></div>' : '<div class="page-wrap-outer">사이드바 없는 화면</div>'}
</div>`;

async function open(width, withSidebar = true) {
  const page = await pageWith({ html: bar(withSidebar), stubs: false });
  await page.setViewportSize({ width, height: 700 });
  for (const css of ['bootstrap.css', 'usermenu.css', 'yobi.css']) await page.addStyleTag({ path: path.join(CSS, css) });
  return page;
}

/** 요소의 세로 중심(문서 좌표)과 가로 위치 */
const center = (page, selector) => page.evaluate((s) => {
  const r = document.querySelector(s).getBoundingClientRect();
  return { y: r.top + r.height / 2, left: r.left, right: r.right, width: r.width };
}, selector);

// 좁은 폭에서 왼쪽 항목과 오른쪽 사용자 메뉴가 한 줄에 들어가는지 본다. 합계가 폭을 넘으면 float된 사용자 메뉴가 아래 줄로 밀려 머리 위에 겹친다.
export const phoneTests = [420, 375, 360].map((width) => ({
  name: `폰 폭 ${width}에서 사용자 메뉴가 상단 바 안(같은 줄)에 남는다`,
  async run() {
    const page = await open(width);
    const menu = await page.evaluate(() => { const r = document.querySelector('.gnb-usermenu').getBoundingClientRect(); return { top: r.top, bottom: r.bottom }; });
    const barBox = await page.evaluate(() => { const r = document.querySelector('.gnb-outer').getBoundingClientRect(); return { top: r.top, bottom: r.bottom }; });
    assert.ok(menu.top < barBox.bottom - 1, `사용자 메뉴 위 ${menu.top}가 상단 바 아래 ${barBox.bottom}보다 아래로 밀렸다`);
    assert.ok(Math.abs(menu.top - barBox.top) <= 1, `사용자 메뉴 위 ${menu.top}, 상단 바 위 ${barBox.top}`);
  },
}));

export const tests = [...[1280, 420].flatMap((width) => [
  {
    name: `두 단추의 세로 중심은 로고, 검색창, 상단 바와 같다(폭 ${width})`,
    async run() {
      const page = await open(width);
      const barBox = await center(page, '.gnb-outer');
      const toggle = await center(page, '.shell-sidebar-btn');
      const sw = await center(page, '.shell-switcher-btn');
      const logo = await center(page, '.gnb-nav .logo');
      const near = (a, b, label) => assert.ok(Math.abs(a - b) <= 1, `${label}: 단추 ${a}, 기준 ${b}`);
      for (const [name, b] of [['사이드바 햄버거', toggle], ['전환', sw]]) {
        near(b.y, logo.y, `${name} 대 로고`);
        near(b.y, barBox.y, `${name} 대 상단 바`);
        // 폰 폭에서는 검색창이 숨겨지므로(display: none) 보일 때만 견준다
        if (await page.isVisible('.gnb-search-form')) near(b.y, (await center(page, '.gnb-search-form')).y, `${name} 대 검색창`);
      }
    },
  },
  {
    name: `순서는 사이드바 햄버거, 로고, 전환 단추이고 서로 겹치지 않는다(폭 ${width})`,
    async run() {
      const page = await open(width);
      const toggle = await center(page, '.shell-sidebar-btn'); const logo = await center(page, '.gnb-nav .logo'); const sw = await center(page, '.shell-switcher-btn');
      assert.ok(toggle.right <= logo.left + 0.5, `햄버거 오른쪽 ${toggle.right}, 로고 왼쪽 ${logo.left}`);
      assert.ok(logo.right <= sw.left + 0.5, `로고 오른쪽 ${logo.right}, 전환 왼쪽 ${sw.left}`);
    },
  },
]),
{
  name: '사이드바가 있는 화면에서는 사이드바 햄버거가 보이고, 없는 화면에서는 숨겨진다',
  async run() {
    const withSidebar = await open(1280, true);
    assert.equal(await withSidebar.isVisible('.shell-sidebar-btn'), true);
    const without = await open(1280, false);
    assert.equal(await without.isVisible('.shell-sidebar-btn'), false, '사이드바가 없으면 누를 일이 없다');
    assert.equal(await without.isVisible('.shell-switcher-btn'), true, '전환 단추는 사이드바와 상관없이 늘 보인다');
  },
},
...phoneTests];
