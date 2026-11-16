import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// 목록 화면의 검색줄 입력란(A-496). `autofocus`로 열리자마자 포커스가 가 있는데, 브라우저 기본 포커스 링(검은 테두리)이 그대로 보였다(선생님 지적).
// 검은 링은 없애고, 포커스는 다른 입력란처럼 요나 주황 테두리로 알린다.
const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const HTML = `<div class="search-wrap"><div id="search"><form><div class="search-bar"><input name="filter" class="textbox" type="text" placeholder="키워드" autofocus><button type="submit" class="search-btn">검색</button></div></form></div></div>`;

async function open() {
  const page = await pageWith({ html: HTML, stubs: false });
  await page.setViewportSize({ width: 1280, height: 700 });
  for (const css of ['bootstrap.css', 'usermenu.css', 'yobi.css']) await page.addStyleTag({ path: path.join(CSS, css) });
  await page.addStyleTag({ content: '*, *::before, *::after { transition: none !important; }' });
  return page;
}
const css = (page, sel, props) => page.evaluate(([s, p]) => { const cs = getComputedStyle(document.querySelector(s)); return Object.fromEntries(p.map((k) => [k, cs[k]])); }, [sel, props]);

export const tests = [
  {
    name: '검색란에 포커스가 있어도 브라우저 기본의 검은 포커스 링은 없다(A-496)',
    async run() {
      const page = await open();
      await page.focus('.search-bar .textbox');
      const o = await css(page, '.search-bar .textbox', ['outlineStyle', 'outlineWidth']);
      assert.ok(o.outlineStyle === 'none' || o.outlineWidth === '0px', `입력란 윤곽선 ${o.outlineStyle} ${o.outlineWidth}`);
    },
  },
  {
    name: '포커스가 가면 검색줄이 요나 주황 테두리로 바뀌고, 포커스가 없으면 평소 색이다',
    async run() {
      const page = await open();
      await page.focus('.search-bar .textbox');
      const on = await css(page, '.search-bar', ['borderTopColor', 'borderTopWidth']);
      await page.evaluate(() => document.activeElement.blur());
      const off = await css(page, '.search-bar', ['borderTopColor']);
      assert.notEqual(on.borderTopColor, off.borderTopColor, '포커스에 따라 색이 달라진다');
      const [r, g, b] = on.borderTopColor.match(/\d+/g).map(Number);
      assert.ok(r > 200 && g > 80 && g < 160 && b < 100, `주황 계열이다: ${on.borderTopColor}`);
      assert.notEqual(on.borderTopWidth, '0px');
    },
  },
];
