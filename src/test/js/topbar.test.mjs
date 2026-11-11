import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// 상단 바 배치 시험이다. 컴파일된 실제 CSS(`./gradlew compileSass`가 만드는 build/generated/sass)를 불러 요소의 위치와 세로 중심을 잰다.
// 맨 왼쪽 햄버거는 v1.6의 사이드바(즐겨찾기, 프로젝트, 최근 이슈 패널)를 여는 단추이다(A-489, v1.6 `.pin`). 맥락 사이드바를 접고 펴는 일은 사이드바 맨 아래 단추가 한다.
// 햄버거가 로고, 검색창과 같은 가운데 선에 놓이는지(A-480, 선생님 지적: 세로 정렬이 다른 항목과 달랐다), 로고보다 왼쪽인지,
// 사이드바가 없는 화면에서도 보이는지, 폰 폭에서 사용자 메뉴가 같은 줄에 남는지 확인한다.
const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');

const HAMBURGER = '<svg width="18" height="14" viewBox="0 0 18 14" aria-hidden="true"><path d="M0 1h18M0 7h18M0 13h18" stroke="currentColor" stroke-width="2" fill="none"/></svg>';

const bar = (withSidebar) => `<div id="main" class="main"><header class="gnb-outer"><div class="gnb-inner">
  <ul class="gnb-nav">
    <li class="gnb-switcher"><button type="button" class="shell-switcher-btn" data-shell-switcher aria-label="사이드바">${HAMBURGER}</button></li>
    <li><a href="/" class="logo logo-letter">Y</a></li>
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
    name: `햄버거의 세로 중심은 로고, 검색창, 상단 바와 같다(폭 ${width})`,
    async run() {
      const page = await open(width);
      const barBox = await center(page, '.gnb-outer');
      const b = await center(page, '.shell-switcher-btn');
      const logo = await center(page, '.gnb-nav .logo');
      const near = (x, y, label) => assert.ok(Math.abs(x - y) <= 1, `${label}: 햄버거 ${x}, 기준 ${y}`);
      near(b.y, logo.y, '로고');
      near(b.y, barBox.y, '상단 바');
      // 폰 폭에서는 검색창이 숨겨지므로(display: none) 보일 때만 견준다
      if (await page.isVisible('.gnb-search-form')) near(b.y, (await center(page, '.gnb-search-form')).y, '검색창');
    },
  },
  {
    name: `햄버거는 로고보다 왼쪽에 있고 겹치지 않는다(폭 ${width})`,
    async run() {
      const page = await open(width);
      const b = await center(page, '.shell-switcher-btn'); const logo = await center(page, '.gnb-nav .logo');
      assert.ok(b.right <= logo.left + 0.5, `햄버거 오른쪽 ${b.right}, 로고 왼쪽 ${logo.left}`);
    },
  },
]),
{
  name: '햄버거는 사이드바가 있든 없든 모든 폭에서 보인다(v1.6 사이드바는 어느 화면에서나 열린다)',
  async run() {
    for (const [width, withSidebar] of [[1280, true], [1280, false], [700, true], [700, false]]) {
      const page = await open(width, withSidebar);
      assert.equal(await page.isVisible('.shell-switcher-btn'), true, `폭 ${width}, 사이드바 ${withSidebar}`);
    }
  },
},
...phoneTests];
