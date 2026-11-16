// 병합 상자의 분할 단추: Bootstrap 5 btn-group의 `.btn`과 `.dropdown-toggle-split`이며 요나 주황이다(`.btn-yona`).
// 삼각형 토글이 붙어 한 단추처럼 보이고, 오른쪽 끝에 있어도 열린 목록이 화면 밖으로 잘리지 않는다(컴파일된 실제 CSS 필요).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, ROOT } from './harness.mjs';

const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const BS = fs.readFileSync(path.join(ROOT, 'src/main/resources/static/assets/bootstrap/js/bootstrap.bundle.min.js'), 'utf8');
const HTML = `<!doctype html><html><head><meta charset="utf-8"><style>${fs.readFileSync(path.join(CSS, 'bootstrap.css'), 'utf8')}</style><style>${fs.readFileSync(path.join(CSS, 'yobi.css'), 'utf8')}</style></head><body style="margin:0">
<div style="position:absolute;right:8px;top:20px"><div id="merge-box" style="display:inline-block"><div class="btn-group">
<button type="button" id="a" class="btn btn-yona">코드 병합</button>
<button type="button" id="t" class="btn btn-yona dropdown-toggle dropdown-toggle-split" data-bs-toggle="dropdown" aria-expanded="false"><span class="visually-hidden">병합 방식</span></button>
<ul id="m" class="dropdown-menu dropdown-menu-end" style="min-width:320px"><li><a class="dropdown-item" href="#">병합 커밋</a></li><li><a class="dropdown-item" href="#">스쿼시 병합</a></li></ul></div></div></div></body></html>`;

async function open() {
  const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1000, height: 600 } }); const page = await ctx.newPage();
  await page.setContent(HTML); await page.addScriptTag({ content: BS }); return { page, ctx };
}

export const tests = [
  { name: '병합 단추와 삼각형 토글은 같은 주황이고 가운데 모서리가 이어진 하나의 단추처럼 보인다', run: async () => {
    const { page, ctx } = await open();
    const r = await page.evaluate(() => ['a', 't'].map((id) => { const e = document.getElementById(id), cs = getComputedStyle(e), b = e.getBoundingClientRect(); return { bg: cs.backgroundColor, tl: cs.borderTopLeftRadius, tr: cs.borderTopRightRadius, left: b.left, right: b.right, top: b.top, h: b.height }; }));
    assert.equal(r[0].bg, 'rgb(255, 115, 50)', `병합 단추 색 ${r[0].bg}`); assert.equal(r[1].bg, r[0].bg, '토글 색이 같다');
    assert.equal(r[0].tr, '0px', '왼쪽 단추의 오른쪽 모서리는 각지다'); assert.equal(r[1].tl, '0px', '토글의 왼쪽 모서리는 각지다');
    assert.ok(Math.abs(r[0].right - r[1].left) <= 1, `두 단추 사이 틈 ${r[1].left - r[0].right}px`); assert.equal(r[0].h, r[1].h, '높이가 같다');
    await ctx.close();
  } },
  { name: '삼각형을 누르면 방식 목록이 열리고 오른쪽 끝에서도 화면 안에 들어온다', run: async () => {
    const { page, ctx } = await open();
    await page.click('#t'); await page.waitForTimeout(300);
    const r = await page.evaluate(() => { const m = document.getElementById('m').getBoundingClientRect(); return { shown: document.getElementById('m').classList.contains('show'), left: m.left, right: m.right, w: innerWidth }; });
    assert.equal(r.shown, true); assert.ok(r.left >= 0 && r.right <= r.w, `목록 ${Math.round(r.left)}~${Math.round(r.right)} / 화면 ${r.w}`);
    await ctx.close();
  } },
];
