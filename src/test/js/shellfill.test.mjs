// 사이드바 패널을 화면 아래까지 잇는 모듈(`shell/fill`)을 실제 Chromium에서 시험한다(A-482).
// 본문이 짧으면 `.shell`의 최소 높이를 계산해 패널이 푸터 바로 위, 즉 화면 아래까지 이어지게 한다. 컴파일된 실제 CSS를 함께 불러 잰다.
// CSS만으로 #main을 세로 flex로 만들면 `position: absolute`인 프로젝트 상단 바가 맨 위로 올라가 관리자 띠와 겹쳤으므로(A-482), 그 회귀도 시험한다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { ROOT } from './harness.mjs';
import { buildTs, openTs } from './tsmodule.mjs';

const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');

const body = (long) => `<div id="main" class="main">
  <div class="admin-logged-in-affix">관리자로 로그인 하였습니다</div>
  <header class="gnb-outer project-header"><div class="gnb-inner"><ul class="gnb-nav"><li><a class="logo logo-letter" href="/">Y</a></li></ul></div></header>
  <div class="project-header-outer" style="height:120px"></div>
  <div class="page-wrap-outer"><div class="project-page-wrap shell">
    <nav class="shell-sidebar offcanvas-lg offcanvas-start"><div class="offcanvas-body"><div class="shell-sidebar-title d-none d-lg-block">demo</div>
      <ul class="nav nav-pills flex-column"><li class="nav-item"><a class="nav-link active" href="/a">홈</a></li></ul></div></nav>
    <div class="shell-main"><p style="height:${long ? 1400 : 40}px">본문</p></div>
  </div></div>
  <footer class="page-footer-outer"><div>footer</div></footer></div>
  <script type="module">import { initFill } from 'yona/shell/fill.js'; window.__fill = initFill(); window.__ready = true;</script>`;

async function open(long = false, size = { width: 1280, height: 800 }) {
  buildTs();
  const { page, ctx } = await openTs(body(long));
  await page.setViewportSize(size);
  for (const css of ['bootstrap.css', 'usermenu.css', 'yobi.css']) await page.addStyleTag({ path: path.join(CSS, css) });
  await page.addStyleTag({ content: '*, *::before, *::after { transition: none !important; }' });
  await page.waitForFunction(() => window.__ready === true, null, { timeout: 5000 });
  await page.evaluate(() => window.__fill.fit());
  return { page, ctx };
}
const rect = (page, selector) => page.evaluate((s) => { const r = document.querySelector(s).getBoundingClientRect(); return { top: r.top + scrollY, bottom: r.bottom + scrollY, height: r.height }; }, selector);

export const tests = [
  { name: '본문이 짧으면 사이드바 패널이 푸터 바로 위까지 이어지고 푸터는 화면 아래에 놓인다', run: async () => {
    const { page, ctx } = await open(false);
    const side = await rect(page, '.shell-sidebar'); const footer = await rect(page, '.page-footer-outer');
    assert.ok(Math.abs(side.bottom - footer.top) <= 1, `사이드바 아래 ${side.bottom}, 푸터 위 ${footer.top}`);
    assert.ok(Math.abs(footer.bottom - 800) <= 1, `푸터 아래 ${footer.bottom}, 화면 800`);
    assert.deepEqual(page.errors, []); await ctx.close();
  } },
  { name: '본문이 길면 높이를 건드리지 않는다(스크롤이 늘지 않는다)', run: async () => {
    const { page, ctx } = await open(true);
    const natural = await page.evaluate(() => { const s = document.querySelector('.shell'); const h = s.getBoundingClientRect().height; return { h, min: s.style.minHeight }; });
    const main = await rect(page, '.shell-main'); const side = await rect(page, '.shell-sidebar');
    assert.ok(side.bottom >= main.bottom - 1, '패널이 본문 끝까지 이어진다');
    assert.ok(natural.h <= main.height + 1, `shell 높이 ${natural.h}가 본문 ${main.height}보다 커지면 안 된다`); await ctx.close();
  } },
  { name: '창 높이가 바뀌면 다시 맞춘다', run: async () => {
    const { page, ctx } = await open(false);
    await page.setViewportSize({ width: 1280, height: 1000 }); await page.waitForTimeout(200);
    const footer = await rect(page, '.page-footer-outer');
    assert.ok(Math.abs(footer.bottom - 1000) <= 1, `푸터 아래 ${footer.bottom}, 화면 1000`); await ctx.close();
  } },
  { name: '좁은 폭(992 미만, 서랍)에서는 최소 높이를 주지 않는다', run: async () => {
    const { page, ctx } = await open(false, { width: 700, height: 800 });
    assert.equal(await page.evaluate(() => document.querySelector('.shell').style.minHeight), ''); await ctx.close();
  } },
  { name: '프로젝트 상단 바(position: absolute)는 관리자 띠 아래에 그대로 있다(flex로 바꾸면 맨 위로 올라가 겹쳤다)', run: async () => {
    const { page, ctx } = await open(false);
    const bar = await rect(page, '.gnb-outer'); const banner = await rect(page, '.admin-logged-in-affix');
    assert.ok(bar.top >= banner.bottom - 1, `상단 바 위 ${bar.top}, 관리자 띠 아래 ${banner.bottom}`); await ctx.close();
  } },
];
