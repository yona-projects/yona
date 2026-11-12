// 마일스톤 상세: 이슈 목록이 사이드바 바로 오른쪽 본문 맨 위에서 시작하고 마일스톤 정보는 오른쪽 열에 있다(A-520). 컴파일된 실제 CSS가 필요하다(`./gradlew compileSass`).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, ROOT } from './harness.mjs';

const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const HTML = `<!doctype html><html><head><meta charset="utf-8"><style>${fs.readFileSync(path.join(CSS, 'bootstrap.css'), 'utf8')}</style><style>${fs.readFileSync(path.join(CSS, 'yobi.css'), 'utf8')}</style></head><body>
<div class="page-wrap-outer"><div class="project-page-wrap shell shell-below-banner">
<nav class="shell-sidebar offcanvas-lg offcanvas-start"><div class="offcanvas-body">사이드바</div></nav>
<div class="shell-main" style="width:990px;flex:none"><div class="milesion-wrap"><div class="row d-flex">
  <div class="col-lg-9"><div id="issues"><ul class="nav nav-tabs mb-3"><li class="nav-item"><a class="nav-link active" href="#">열림</a></li></ul><div class="issues">목록</div></div></div>
  <div class="col-lg-3 milestone-info order-first order-lg-last"><h4><a class="title" href="#">v0.1 첫 릴리스</a><small class="ml10"><span class="due-date">기한 <strong>2026-12-31</strong></span> <span class="date">(49일 남음)</span><span class="badge rounded-pill margin-left-5">진행중</span></small></h4><div class="progress"><div class="progress-bar" style="width:0"></div></div><div class="milestone-desc"><div class="markdown-wrap"><p>설명</p></div></div><div class="actrow"><a class="ybtn" href="#">목록</a></div></div>
</div></div></div></div></div></body></html>`;

async function open(width) {
  const b = await launch(); const ctx = await b.newContext({ viewport: { width, height: 800 } }); const page = await ctx.newPage();
  await page.setContent(HTML); return { page, ctx };
}
const rect = (page, sel) => page.evaluate((s) => { const r = document.querySelector(s).getBoundingClientRect(); return { top: r.top, left: r.left, right: r.right, bottom: r.bottom, width: r.width }; }, sel);

export const tests = [
  { name: '넓은 화면: 이슈 탭 줄이 본문 맨 위(사이드바 첫 줄 높이)에서 시작하고 마일스톤 정보는 목록 오른쪽에 있다', run: async () => {
    const { page, ctx } = await open(1280);
    const main = await rect(page, '.shell-main'), tabs = await rect(page, '#issues .nav-tabs'), info = await rect(page, '.milestone-info'), list = await rect(page, '#issues');
    assert.ok(tabs.top - main.top <= 16, `탭 줄이 본문 위에서 ${tabs.top - main.top}px 아래에서 시작한다`);
    assert.ok(info.left >= list.right - 1, `정보 열(${info.left})이 목록(${list.right}) 오른쪽에 있어야 한다`);
    assert.ok(Math.abs(info.top - main.top) <= 16, `정보 열도 본문 위에서 시작한다: ${info.top - main.top}px`);
    await ctx.close();
  } },
  { name: '좁은 화면: 마일스톤 정보가 먼저 오고 이슈 목록이 그 아래에 이어진다', run: async () => {
    const { page, ctx } = await open(600);
    const info = await rect(page, '.milestone-info'), list = await rect(page, '#issues');
    assert.ok(info.top < list.top, `정보(${info.top})가 목록(${list.top})보다 위에 있어야 한다`);
    await ctx.close();
  } },
  { name: '오른쪽 열에서 기한과 상태 배지는 제목 아래 별도 줄이고 기한 날짜는 줄바꿈하지 않는다', run: async () => {
    const { page, ctx } = await open(1280);
    const r = await page.evaluate(() => { const small = document.querySelector('.milestone-info h4 small'), due = document.querySelector('.milestone-info .due-date'); return { smallDisplay: getComputedStyle(small).display, ws: getComputedStyle(due).whiteSpace, smallTop: Math.round(small.getBoundingClientRect().top), titleBottom: Math.round(document.querySelector('.milestone-info h4 .title').getBoundingClientRect().bottom) }; });
    assert.equal(r.smallDisplay, 'block', `부가 정보 display ${r.smallDisplay}`);
    assert.equal(r.ws, 'nowrap', `기한 칸 white-space ${r.ws}`);
    assert.ok(r.smallTop >= r.titleBottom - 1, `부가 정보(${r.smallTop})가 제목(${r.titleBottom}) 아래 줄이어야 한다`);
    await ctx.close();
  } },
];
