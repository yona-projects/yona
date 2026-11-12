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
  { name: '도구 줄의 단추들은 서로 6px 이상 떨어져 있다', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1000, height: 600 } }); const page = await ctx.newPage();
    await page.setContent(HTML);
    const r = await page.evaluate(() => [...document.querySelectorAll('.code-browse-header .float-end a')].map((a) => { const x = a.getBoundingClientRect(); return { left: x.left, right: x.right }; }));
    assert.equal(r.length, 3);
    for (let i = 0; i < 2; i++) assert.ok(r[i].left - r[i + 1].right >= 6, `단추 ${i}와 ${i + 1} 사이 ${r[i].left - r[i + 1].right}px`);
    await ctx.close();
  } },
];
