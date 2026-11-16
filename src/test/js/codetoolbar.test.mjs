// 코드 파일 화면의 도구 줄(새 파일, 커밋 이력, 압축 파일로 내려받기)은 단추 사이에 간격이 있다(컴파일된 실제 CSS 필요: `./gradlew compileSass`).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, ROOT } from './harness.mjs';

const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const HTML = `<!doctype html><html><head><meta charset="utf-8"><style>${fs.readFileSync(path.join(CSS, 'bootstrap.css'), 'utf8')}</style><style>${fs.readFileSync(path.join(CSS, 'yobi.css'), 'utf8')}</style></head><body>
<div class="page-wrap-outer"><div class="code-browse-wrap" style="width:900px"><div class="code-browse-header">
<select id="branches" class="float-start form-select"><option>master</option></select>
<div class="float-end"><a class="ybtn" href="#">압축 파일로 내려받기</a></div>
<div class="float-end"><a id="commit-history-link" class="ybtn" href="#">커밋 이력</a></div>
<div class="float-end"><a id="new-file-link" class="ybtn" href="#">새 파일</a></div>
</div></div></div></body></html>`;

export const tests = [
  { name: '도구 줄의 단추 사이 간격은 모두 같고 6px 이상이다(새 파일 링크의 자체 여백 때문에 달라지지 않는다)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1000, height: 600 } }); const page = await ctx.newPage();
    await page.setContent(HTML);
    const r = await page.evaluate(() => [...document.querySelectorAll('.code-browse-header .float-end a')].map((a) => { const x = a.getBoundingClientRect(); return { left: x.left, right: x.right }; }));
    assert.equal(r.length, 3);
    const gaps = [r[0].left - r[1].right, r[1].left - r[2].right];
    for (const g of gaps) assert.ok(g >= 6, `간격 ${g}px`);
    assert.ok(Math.abs(gaps[0] - gaps[1]) <= 1, `간격이 서로 다르다: ${gaps.map((g) => g.toFixed(1)).join(', ')}px`);
    await ctx.close();
  } },
];
