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
  <div class="col-lg-9"><div class="milestone-head"><h4><a class="title" href="#">v0.1 첫 릴리스</a><small class="ml10"><span class="due-date">기한 <strong>2026-12-31</strong></span> <span class="date">(49일 남음)</span><span class="badge rounded-pill margin-left-5">진행중</span></small></h4><div class="progress"><div class="progress-bar" style="width:0"></div></div><div class="search search-bar milestone-search"><input name="filter" class="textbox" type="text" placeholder="검색"><button type="submit" class="search-btn"><i class="yobicon-search"></i></button></div></div>
  <div id="issues"><ul class="nav nav-tabs mb-3"><li class="nav-item"><a class="nav-link active" href="#">열림</a></li></ul><div class="filter-wrap">필터</div><div class="issues">목록</div></div></div>
  <div class="col-lg-3 milestone-info order-first order-lg-last"><div class="milestone-desc"><div class="markdown-wrap"><p>설명</p></div></div><div class="actrow"><a class="ybtn" href="#">목록</a></div></div>
</div></div></div></div></div></body></html>`;

async function open(width) {
  const b = await launch(); const ctx = await b.newContext({ viewport: { width, height: 800 } }); const page = await ctx.newPage();
  await page.setContent(HTML); return { page, ctx };
}
const rect = (page, sel) => page.evaluate((s) => { const r = document.querySelector(s).getBoundingClientRect(); return { top: r.top, left: r.left, right: r.right, bottom: r.bottom, width: r.width }; }, sel);

export const tests = [
  { name: '넓은 화면: 제목, 진행률, 검색바가 본문 맨 위에 있고 그 아래에 이슈 탭과 필터가 이어지며 설명 열은 오른쪽이다', run: async () => {
    const { page, ctx } = await open(1280);
    const main = await rect(page, '.shell-main'), head = await rect(page, '.milestone-head'), title = await rect(page, '.milestone-head h4'), prog = await rect(page, '.milestone-head .progress'), search = await rect(page, '.milestone-search'), tabs = await rect(page, '#issues .nav-tabs'), filter = await rect(page, '.filter-wrap'), info = await rect(page, '.milestone-info'), list = await rect(page, '#issues');
    assert.ok(head.top - main.top <= 16, `머리가 본문 위에서 ${head.top - main.top}px 아래에서 시작한다`);
    assert.ok(title.top <= prog.top && prog.bottom <= search.top + 1, '제목, 진행률, 검색바가 위에서 아래로 놓인다');
    assert.ok(search.top - prog.bottom >= 8, `진행률과 검색바 사이 ${search.top - prog.bottom}px`);
    assert.ok(prog.top - title.bottom >= 0, `제목과 진행률이 겹친다`);
    assert.ok(tabs.top - search.bottom >= 8, `검색바와 이슈 탭 사이 ${tabs.top - search.bottom}px`);
    assert.ok(tabs.bottom <= filter.top + 1, '이슈 탭 아래에 필터가 있다');
    assert.ok(info.left >= list.right - 1, `설명 열(${info.left})이 목록(${list.right}) 오른쪽에 있다`);
    assert.ok(Math.abs(info.top - main.top) <= 16, `설명 열도 본문 위에서 시작한다: ${info.top - main.top}px`);
    await ctx.close();
  } },
  { name: '좁은 화면: 설명 열이 먼저 오고 제목과 이슈 목록이 그 아래에 이어진다', run: async () => {
    const { page, ctx } = await open(600);
    const info = await rect(page, '.milestone-info'), head = await rect(page, '.milestone-head');
    assert.ok(info.top < head.top, `설명(${info.top})이 머리(${head.top})보다 위에 있어야 한다`);
    await ctx.close();
  } },
];
