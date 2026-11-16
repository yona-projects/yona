// 멤버 화면의 멤버 줄은 본문 폭 전체가 아니라 읽기 좋은 폭(최대 760px)까지만 쓴다(A-535). 컴파일된 실제 CSS가 필요하다(`./gradlew compileSass`).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, ROOT } from './harness.mjs';

const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const CSS_ALL = fs.readFileSync(path.join(CSS, 'bootstrap.css'), 'utf8') + '\n' + fs.readFileSync(path.join(CSS, 'yobi.css'), 'utf8');
const HTML = `<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div class="page-wrap-outer"><div class="project-page-wrap shell"><div class="shell-main" style="width:990px;flex:none">
<div class="inner-bubble"><form class="nm d-flex gap-2"><input type="text" class="text uname form-control" placeholder="새로운 멤버의 아이디를 입력하세요"><button type="submit" class="ybtn ybtn-success">추가</button></form></div>
<ul class="members project row"><li class="member col-12 span-hard-wrap"><a class="avatar-wrap mlarge float-start mr10"><img width="64" height="64" src="data:image/gif;base64,R0lGODlhAQABAAAAACw="></a><div class="member-name">user</div><div class="member-id">@admin</div><div class="member-setting"><span class="badge owner bg-secondary">프로젝트 소유자</span></div></li></ul>
</div></div></div></body></html>`;

export const tests = [
  { name: '멤버 줄은 본문(990px)을 다 채우지 않고 최대 760px까지만 쓰며 역할 표시는 줄의 오른쪽 끝에 있다', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 700 } }); const page = await ctx.newPage();
    await page.setContent(HTML);
    const r = await page.evaluate(() => { const li = document.querySelector('li.member').getBoundingClientRect(), s = document.querySelector('.member-setting').getBoundingClientRect(); return { w: li.width, left: li.left, right: li.right, sRight: s.right }; });
    assert.ok(r.w <= 761, `멤버 줄 폭 ${r.w}px가 760px를 넘는다`);
    assert.ok(r.w >= 500, `멤버 줄이 너무 좁다 ${r.w}px`);
    assert.ok(r.right - r.sRight <= 6, `역할 표시가 줄 오른쪽 끝에 있지 않다: 줄 ${r.right.toFixed(0)}, 표시 ${r.sRight.toFixed(0)}`);
    await ctx.close();
  } },
];
