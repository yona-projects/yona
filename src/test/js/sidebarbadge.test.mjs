// 사이드바 항목 오른쪽의 개수 배지는 항목 글자보다 눈에 띄지 않는 작은 알약이어야 한다(컴파일된 실제 CSS 필요: `./gradlew compileSass`).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, ROOT } from './harness.mjs';

const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const HTML = `<!doctype html><html><head><meta charset="utf-8"><style>${fs.readFileSync(path.join(CSS, 'bootstrap.css'), 'utf8')}</style><style>${fs.readFileSync(path.join(CSS, 'yobi.css'), 'utf8')}</style></head><body>
<nav class="shell-sidebar offcanvas-lg" style="width:256px"><div class="offcanvas-body"><ul class="nav flex-column">
<li class="nav-item"><a class="nav-link" id="a" href="#"><span class="shell-sidebar-label">이슈</span><span class="badge text-bg-secondary rounded-pill">8</span></a></li>
<li class="nav-item"><a class="nav-link" id="b" href="#"><span class="shell-sidebar-label">코드 주고받기</span><span class="badge text-bg-secondary rounded-pill">128</span></a></li>
</ul></div></nav></body></html>`;

export const tests = [
  { name: '사이드바 개수 배지는 높이 20px 이하, 글자 12px 이하의 작은 알약이고 항목 글자보다 크지 않다', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(HTML);
    const r = await page.evaluate(() => [...document.querySelectorAll('.nav-link')].map((a) => {
      const bd = a.querySelector('.badge'), l = a.querySelector('.shell-sidebar-label');
      return { h: bd.getBoundingClientRect().height, w: bd.getBoundingClientRect().width, fs: parseFloat(getComputedStyle(bd).fontSize), lfs: parseFloat(getComputedStyle(l).fontSize) };
    }));
    for (const x of r) { assert.ok(x.h <= 20, `배지 높이 ${x.h}px`); assert.ok(x.fs <= 12, `배지 글자 ${x.fs}px`); assert.ok(x.fs < x.lfs, `배지 글자(${x.fs})가 항목 글자(${x.lfs})보다 작아야 한다`); }
    await ctx.close();
  } },
];
