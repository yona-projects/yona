// 항상 보이는 달력(마일스톤, API 토큰)은 담는 열 폭에 맞고 열 밖으로 넘치지 않는다.
// 사이드바를 펼치면 오른쪽 열이 좁아지고(232px) 접으면 넓어진다. 달력은 v1.6의 Pikaday 폭(258px)을 넘지 않는 한 열을 채운다. 컴파일된 실제 CSS가 필요하다(`./gradlew compileSass`).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, JS, ROOT } from './harness.mjs';

const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const LIB = path.join(JS, 'lib/vanilla-calendar-pro');

async function inColumn(colWidth) {
  const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1400, height: 800 } }); const page = await ctx.newPage();
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0"><div id="col" style="width:${colWidth}px;margin-left:100px"><div id="cal" class="date-picker"></div></div></body></html>`);
  await page.addStyleTag({ content: fs.readFileSync(path.join(CSS, 'bootstrap.css'), 'utf8') });
  await page.addStyleTag({ content: fs.readFileSync(path.join(LIB, 'index.css'), 'utf8') });
  await page.addStyleTag({ content: fs.readFileSync(path.join(CSS, 'yobi.css'), 'utf8') });
  await page.addScriptTag({ content: fs.readFileSync(path.join(LIB, 'index.js'), 'utf8') });
  await page.evaluate(() => { const vc = new VanillaCalendarPro.Calendar('#cal', { selectionDatesMode: 'single' }); vc.init(); });
  await page.waitForSelector('[data-vc="calendar"]');
  const r = await page.evaluate(() => {
    const cal = document.querySelector('[data-vc="calendar"]'), col = document.getElementById('col').getBoundingClientRect(), c = cal.getBoundingClientRect();
    const cells = [...cal.querySelectorAll('[data-vc-date-btn]')].map((e) => e.getBoundingClientRect().right);
    const arrows = [...cal.querySelectorAll('[data-vc-arrow]')].map((e) => e.getBoundingClientRect().right);
    return { colRight: col.right, calWidth: c.width, calRight: c.right, maxInner: Math.max(...cells, ...arrows), scrollOverflow: cal.scrollWidth > cal.clientWidth };
  });
  await ctx.close(); return r;
}

export const tests = [
  { name: '열이 232px(사이드바를 편 상태)이면 달력은 열 안에 모두 들어가고 안쪽 칸도 넘치지 않는다', run: async () => {
    const r = await inColumn(232);
    assert.ok(r.calRight <= r.colRight + 0.5, `달력 오른쪽 ${r.calRight}, 열 오른쪽 ${r.colRight}`);
    assert.ok(r.maxInner <= r.colRight + 0.5, `안쪽 칸이 열 밖으로 나간다: ${r.maxInner} > ${r.colRight}`);
    assert.equal(r.scrollOverflow, false, '달력 안쪽이 넘친다');
  } },
  { name: '열이 넓어도(사이드바를 접은 상태) 달력은 v1.6 폭 258px를 넘지 않는다', run: async () => {
    const r = await inColumn(420);
    assert.ok(r.calWidth <= 258.5, `달력 폭 ${r.calWidth}`); assert.ok(r.calWidth >= 230, `달력이 너무 좁다 ${r.calWidth}`);
    assert.ok(r.maxInner <= r.calRight + 0.5, '안쪽 칸이 달력 밖으로 나간다');
  } },
];
