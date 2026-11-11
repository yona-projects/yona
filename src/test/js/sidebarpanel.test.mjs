import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// 사이드바가 본문과 눈으로 구분되는 독립된 패널인지 시험한다(와이어프레임: 다른 바탕색, 오른쪽 경계선, 맥락 이름 머리글, 활성 항목 색 띠).
// 컴파일된 실제 CSS(`./gradlew compileSass`가 만드는 build/generated/sass)를 불러 계산된 스타일과 크기를 잰다.
const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');

const SHELL = `<div class="page-wrap-outer"><div class="project-page-wrap shell">
  <nav class="shell-sidebar offcanvas-lg offcanvas-start" id="shellSidebar" tabindex="-1">
    <div class="offcanvas-header d-lg-none"><h2 class="offcanvas-title h5">demo</h2></div>
    <div class="offcanvas-body">
      <div class="shell-sidebar-title d-none d-lg-block">demo</div>
      <ul class="nav nav-pills flex-column shell-sidebar-nav">
        <li class="nav-item"><a class="nav-link" href="/a" title="홈"><svg class="shell-sidebar-icon" width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M2 8h12" stroke="currentColor"/></svg><span class="shell-sidebar-label">홈</span></a></li>
        <li class="nav-item"><a class="nav-link active" href="/b" aria-current="page" title="이슈"><svg class="shell-sidebar-icon" width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M2 8h12" stroke="currentColor"/></svg><span class="shell-sidebar-label">이슈</span></a></li>
        <li class="nav-item"><a class="nav-link" href="/c" title="위키"><svg class="shell-sidebar-icon" width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M2 8h12" stroke="currentColor"/></svg><span class="shell-sidebar-label">위키</span></a></li>
      </ul>
      <button type="button" class="shell-sidebar-fold d-none d-lg-flex" data-shell-sidebar-toggle aria-expanded="true" aria-controls="shellSidebar"><svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true"><path d="M9 2 4 7l5 5" stroke="currentColor" stroke-width="2" fill="none"/></svg><span class="shell-sidebar-fold-label">접기</span></button>
    </div>
  </nav>
  <div class="shell-main"><p style="height:900px">본문</p></div>
</div></div>`;

async function open(width, html = SHELL, collapsed = false) {
  const page = await pageWith({ html, stubs: false });
  await page.setViewportSize({ width, height: 900 });
  if (collapsed) await page.evaluate(() => document.documentElement.setAttribute('data-sidebar', 'collapsed'));
  for (const css of ['bootstrap.css', 'usermenu.css', 'yobi.css']) await page.addStyleTag({ path: path.join(CSS, css) });
  // Bootstrap 링크의 색 전환(0.15초) 도중에 값을 읽으면 시험이 흔들리므로 전환을 끈다.
  await page.addStyleTag({ content: '*, *::before, *::after { transition: none !important; }' });
  return page;
}

const style = (page, selector, props) => page.evaluate(([s, p]) => {
  const cs = getComputedStyle(document.querySelector(s));
  return Object.fromEntries(p.map((k) => [k, cs[k]]));
}, [selector, props]);

const box = (page, selector) => page.evaluate((s) => { const r = document.querySelector(s).getBoundingClientRect(); return { top: r.top, bottom: r.bottom, left: r.left, right: r.right, height: r.height, width: r.width }; }, selector);

export const tests = [
  {
    name: '사이드바는 본문과 다른 바탕색을 가진 패널이다',
    async run() {
      const page = await open(1280);
      const side = await style(page, '.shell-sidebar', ['backgroundColor']);
      const main = await style(page, '.shell-main', ['backgroundColor']);
      assert.notEqual(side.backgroundColor, 'rgba(0, 0, 0, 0)', '바탕이 투명하면 본문과 구분되지 않는다');
      assert.notEqual(side.backgroundColor, main.backgroundColor, `사이드바 ${side.backgroundColor}, 본문 ${main.backgroundColor}`);
      assert.notEqual(side.backgroundColor, 'rgb(255, 255, 255)', '흰색이면 흰 본문과 구분되지 않는다');
    },
  },
  {
    name: '사이드바 오른쪽에 경계선이 있다',
    async run() {
      const page = await open(1280);
      const side = await style(page, '.shell-sidebar', ['borderRightWidth', 'borderRightStyle']);
      assert.equal(side.borderRightStyle, 'solid');
      assert.ok(parseFloat(side.borderRightWidth) >= 1, `경계선 두께 ${side.borderRightWidth}`);
    },
  },
  {
    name: '사이드바 패널은 본문 높이까지 이어진다(항목 몇 개만큼만 끊기지 않는다)',
    async run() {
      const page = await open(1280);
      const side = await box(page, '.shell-sidebar');
      const main = await box(page, '.shell-main');
      assert.ok(side.height >= main.height - 1, `사이드바 높이 ${side.height}, 본문 높이 ${main.height}`);
    },
  },
  {
    name: '넓은 화면에서 사이드바 머리에 맥락 이름(프로젝트 이름)이 보인다',
    async run() {
      const page = await open(1280);
      assert.equal(await page.isVisible('.shell-sidebar-title'), true);
      assert.equal((await page.textContent('.shell-sidebar-title')).trim(), 'demo');
    },
  },
  {
    name: '활성 항목은 바탕색과 굵은 글자로 다른 항목과 구분된다',
    async run() {
      const page = await open(1280);
      const active = await style(page, '.shell-sidebar .nav-link.active', ['backgroundColor', 'fontWeight']);
      const plain = await style(page, '.shell-sidebar .nav-link:not(.active)', ['backgroundColor']);
      assert.notEqual(active.backgroundColor, 'rgba(0, 0, 0, 0)', '활성 항목 바탕이 투명하다');
      assert.notEqual(active.backgroundColor, plain.backgroundColor);
      assert.ok(Number(active.fontWeight) >= 600, `글자 굵기 ${active.fontWeight}`);
    },
  },
  {
    name: '본문이 길면 사이드바 패널도 본문 끝까지 이어진다',
    async run() {
      const page = await open(1280);
      const side = await box(page, '.shell-sidebar');
      const main = await box(page, '.shell-main');
      assert.ok(side.bottom >= main.bottom - 1, `사이드바 아래 ${side.bottom}, 본문 아래 ${main.bottom}`);
    },
  },
  {
    name: '접기 단추는 짧은 화면에서 사이드바 패널 맨 아래에 있다(A-488)',
    async run() {
      const page = await open(1280);
      const side = await box(page, '.shell-sidebar'); const fold = await box(page, '.shell-sidebar-fold');
      assert.ok(await page.isVisible('.shell-sidebar-fold'), '넓은 화면에서 보인다');
      assert.ok(side.bottom - fold.bottom <= 24, `패널 아래 ${side.bottom}, 단추 아래 ${fold.bottom}`);
      const items = await box(page, '.shell-sidebar-nav');
      assert.ok(fold.top >= items.bottom, '목록 아래에 있다');
    },
  },
  {
    name: '본문이 길어도 접기 단추는 화면 아래에 붙어 따라온다(스크롤하지 않아도 보인다)',
    async run() {
      const page = await open(1280, SHELL.replace('height:900px', 'height:2400px'));
      const fold = await box(page, '.shell-sidebar-fold');
      assert.ok(fold.bottom <= 900 + 0.5, `단추 아래 ${fold.bottom}가 화면 높이 900 안에 있어야 한다`);
      assert.ok(fold.top >= 0, '화면 안에 있다');
    },
  },
  {
    name: '접으면 좁은 띠(60px 이하)가 되고 머리글과 글자는 숨겨지며 펴기 단추는 띠 맨 아래에 남는다',
    async run() {
      const page = await open(1280, SHELL, true);
      const side = await box(page, '.shell-sidebar'); const main = await box(page, '.shell-main');
      assert.ok(side.width <= 60 && side.width >= 36, `띠 폭 ${side.width}`);
      assert.equal(await page.isVisible('.shell-sidebar-nav'), true, '항목은 아이콘만 남아 목록이 보인다(A-489)');
      assert.equal(await page.isVisible('.shell-sidebar-title'), false);
      assert.equal(await page.isVisible('.shell-sidebar-fold'), true, '펴기 단추가 보인다');
      assert.equal(await page.isVisible('.shell-sidebar-fold-label'), false, '띠에서는 글자를 숨긴다');
      const fold = await box(page, '.shell-sidebar-fold');
      assert.ok(side.bottom - fold.bottom <= 24 && fold.right <= side.right + 0.5, '띠 맨 아래에 있다');
      assert.ok(main.left < 120, `본문이 넓어진다(본문 왼쪽 ${main.left})`);
    },
  },
  {
    name: '좁은 폭(서랍)에서는 하단 접기 단추를 숨긴다',
    async run() {
      const page = await open(700);
      assert.equal(await page.isVisible('.shell-sidebar-fold'), false);
    },
  },
  {
    name: '열린 사이드바의 항목은 아이콘이 이름 왼쪽의 같은 줄에 있다(A-489)',
    async run() {
      const page = await open(1280);
      const icon = await box(page, '.shell-sidebar .nav-link .shell-sidebar-icon'); const label = await box(page, '.shell-sidebar .nav-link .shell-sidebar-label');
      assert.ok(icon.width >= 14 && icon.width <= 20, `아이콘 폭 ${icon.width}`);
      assert.ok(icon.right <= label.left + 0.5, `아이콘 오른쪽 ${icon.right}, 이름 왼쪽 ${label.left}`);
      assert.ok(Math.abs((icon.top + icon.height / 2) - (label.top + label.height / 2)) <= 3, '아이콘과 이름이 같은 줄이다');
    },
  },
  {
    name: '접힌 띠에서는 항목이 아이콘만 남고 띠의 가운데에 놓이며 이름은 이름표로만 알린다',
    async run() {
      const page = await open(1280, SHELL, true);
      const side = await box(page, '.shell-sidebar');
      assert.equal(await page.isVisible('.shell-sidebar-nav'), true, '아이콘 목록은 띠에서도 보인다');
      assert.equal(await page.isVisible('.shell-sidebar .nav-link .shell-sidebar-label'), false, '이름은 숨긴다');
      const icons = await page.evaluate(() => [...document.querySelectorAll('.shell-sidebar .nav-link .shell-sidebar-icon')].map((e) => { const r = e.getBoundingClientRect(); return { cx: r.left + r.width / 2, w: r.width, visible: r.width > 0 }; }));
      assert.equal(icons.length, 3);
      for (const i of icons) { assert.ok(i.visible && i.w >= 14, '아이콘이 보인다'); assert.ok(Math.abs(i.cx - (side.left + side.width / 2)) <= 8, `아이콘 가운데 ${i.cx}, 띠 가운데 ${side.left + side.width / 2}`); }
      assert.ok((await page.getAttribute('.shell-sidebar .nav-link', 'title')).length > 0, '이름표(title)가 있다');
      const active = await style(page, '.shell-sidebar .nav-link.active', ['backgroundColor']);
      assert.notEqual(active.backgroundColor, 'rgba(0, 0, 0, 0)', '활성 항목은 띠에서도 바탕으로 구분된다');
    },
  },
];
