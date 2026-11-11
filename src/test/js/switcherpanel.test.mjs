import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// 상단 햄버거가 여는 v1.6 사이드바 패널(#mySidenav, A-490)을 실제 CSS로 시험한다.
// 선생님 지적: v1.6의 사이드바는 화면 왼쪽에 있었다. 본문을 밀어내지 않고 본문 위에 덮여야 한다.
const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');

const HTML = `<div id="main" class="main">
<p class="band" style="height:28px;margin:0">알림 띠</p>
<header class="gnb-outer"><div class="gnb-inner"><ul class="gnb-nav"><li class="gnb-switcher"><button class="shell-switcher-btn" data-shell-switcher>M</button></li></ul>
  <div id="mySidenav" class="sidenav row align-items-start"><div class="col-lg-5 right-menu"><div id="usermenu-tab-content-list">목록</div></div></div>
</div></header>
<div class="page-wrap-outer"><div class="shell"><nav class="shell-sidebar">사이드바</nav><div class="shell-main" style="height:900px">본문</div></div></div></div>`;

async function open(width) {
  const page = await pageWith({ html: HTML, stubs: false });
  await page.setViewportSize({ width, height: 800 });
  for (const css of ['bootstrap.css', 'usermenu.css', 'yobi.css']) await page.addStyleTag({ path: path.join(CSS, css) });
  await page.addStyleTag({ content: '*, *::before, *::after { transition: none !important; }' });
  return page;
}
const rect = (page, s) => page.evaluate((q) => { const r = document.querySelector(q).getBoundingClientRect(); return { left: r.left, right: r.right, top: r.top, bottom: r.bottom, width: r.width }; }, s);
const openPanel = (page) => page.evaluate(() => { const e = document.getElementById('mySidenav'); e.style.width = '360px'; });

export const tests = [
  {
    name: '패널은 화면 왼쪽 가장자리에 붙어 열린다(v1.6 사이드바 위치)',
    async run() {
      const page = await open(1280);
      await openPanel(page);
      const p = await rect(page, '#mySidenav');
      assert.ok(Math.abs(p.left) <= 1, `패널 왼쪽 ${p.left}`);
      assert.ok(Math.abs(p.width - 360) <= 2, `패널 폭 ${p.width}`);
    },
  },
  {
    name: '패널을 열어도 본문과 맥락 사이드바의 위치와 크기가 변하지 않는다(덮어 쓰기)',
    async run() {
      const page = await open(1280);
      const before = [await rect(page, '.shell-main'), await rect(page, '.shell-sidebar')];
      await openPanel(page);
      const after = [await rect(page, '.shell-main'), await rect(page, '.shell-sidebar')];
      assert.deepEqual(after, before);
      const z = await page.evaluate(() => getComputedStyle(document.getElementById('mySidenav')).zIndex);
      assert.ok(Number(z) >= 100, `본문 위에 쌓인다 z-index ${z}`);
    },
  },
  {
    name: '패널은 상단 바 아래에서 시작해 화면 아래까지 이어진다',
    async run() {
      const page = await open(1280);
      await openPanel(page);
      const h = await rect(page, '.gnb-outer'); const p = await rect(page, '#mySidenav');
      assert.ok(Math.abs(p.top - h.bottom) <= 1, `패널 위 ${p.top}, 상단 바 아래 ${h.bottom}`);
      assert.ok(p.bottom >= 799, `패널 아래 ${p.bottom}`);
    },
  },
];
