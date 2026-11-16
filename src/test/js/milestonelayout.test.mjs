// 마일스톤 상세: 이슈 목록이 사이드바 바로 오른쪽 본문 맨 위에서 시작하고 마일스톤 정보는 오른쪽 열에 있다(A-520). 컴파일된 실제 CSS가 필요하다(`./gradlew compileSass`).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, ROOT } from './harness.mjs';

const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const HTML = `<!doctype html><html><head><meta charset="utf-8"><style>${fs.readFileSync(path.join(CSS, 'bootstrap.css'), 'utf8')}</style><style>${fs.readFileSync(path.join(CSS, 'yobi.css'), 'utf8')}</style></head><body>
<div class="page-wrap-outer"><div class="project-page-wrap shell shell-below-banner">
<nav class="shell-sidebar offcanvas-lg offcanvas-start"><div class="offcanvas-body">사이드바</div></nav>
<div class="shell-main" style="width:min(990px, calc(100vw - 24px));flex:none"><div class="milesion-wrap"><div class="row d-flex">
  <div class="col-lg-9"><div class="milestone-head"><h4><a class="title" href="#">v0.1 첫 릴리스</a><small class="ml10"><span class="due-date">기한 <strong>2026-12-31</strong></span> <span class="date">(49일 남음)</span><span class="badge rounded-pill margin-left-5">진행중</span></small></h4><div class="progress"><div class="progress-bar" style="width:0"></div></div></div>
  <div id="issues"><div class="milestone-tabbar"><ul class="nav nav-tabs"><li class="nav-item"><a class="nav-link active" href="#">열림</a></li><li class="nav-item"><a class="nav-link" href="#">닫힘</a></li></ul><div class="search search-bar milestone-search"><input name="filter" class="textbox" type="text" placeholder="검색"><button type="submit" class="search-btn"><i class="yobicon-search"></i></button></div></div><div class="filter-wrap">필터</div><div class="issues">목록</div></div></div>
  <div class="col-lg-3 milestone-info order-first order-lg-last"><a class="milestone-back" href="#">목록</a><div class="milestone-desc"><div class="markdown-wrap"><p>설명</p></div></div><div class="actrow milestone-actions d-grid gap-2"><a class="ybtn" href="#">수정</a><button class="ybtn">마일스톤 종료</button><a class="ybtn ybtn-danger" href="#">삭제</a></div></div>
</div></div></div></div></div></body></html>`;

async function open(width) {
  const b = await launch(); const ctx = await b.newContext({ viewport: { width, height: 800 } }); const page = await ctx.newPage();
  await page.setContent(HTML); return { page, ctx };
}
const rect = (page, sel) => page.evaluate((s) => { const r = document.querySelector(s).getBoundingClientRect(); return { top: r.top, left: r.left, right: r.right, bottom: r.bottom, width: r.width }; }, sel);

export const tests = [
  { name: '넓은 화면: 제목과 진행률이 본문 맨 위, 그 아래 탭 줄(검색바는 같은 줄 오른쪽 끝), 필터, 목록 순이고 설명 열은 오른쪽이다', run: async () => {
    const { page, ctx } = await open(1280);
    const main = await rect(page, '.shell-main'), head = await rect(page, '.milestone-head'), title = await rect(page, '.milestone-head h4'), prog = await rect(page, '.milestone-head .progress'), tabs = await rect(page, '#issues .nav-tabs'), search = await rect(page, '.milestone-search'), list = await rect(page, '#issues'), filter = await rect(page, '.filter-wrap'), info = await rect(page, '.milestone-info');
    assert.ok(head.top - main.top <= 16, `머리가 본문 위에서 ${head.top - main.top}px 아래에서 시작한다`);
    assert.ok(title.top <= prog.top && prog.bottom <= tabs.top + 1, '제목, 진행률, 탭이 위에서 아래로 놓인다');
    assert.ok(tabs.top - prog.bottom >= 8, `진행률과 탭 사이 ${tabs.top - prog.bottom}px`);
    assert.ok(Math.abs((search.top + search.bottom) / 2 - (tabs.top + tabs.bottom) / 2) <= 24, `검색바가 탭 줄에 있다: 탭 중심 ${((tabs.top + tabs.bottom) / 2).toFixed(0)}, 검색 중심 ${((search.top + search.bottom) / 2).toFixed(0)}`);
    assert.ok(search.left >= tabs.right, '검색바는 탭의 오른쪽에 있다'); assert.ok(list.right - search.right <= 1, `검색바가 본문 오른쪽 끝에 닿는다: ${list.right} / ${search.right}`);
    assert.ok(search.width <= 260 && search.width >= 160, `검색바 폭 ${search.width}px(약 220px)`);
    assert.ok(Math.max(tabs.bottom, search.bottom) <= filter.top + 1, '탭 줄 아래에 필터가 있다');
    assert.ok(info.left >= list.right - 1, `설명 열(${info.left})이 목록(${list.right}) 오른쪽에 있다`);
    assert.ok(Math.abs(info.top - main.top) <= 16, `설명 열도 본문 위에서 시작한다: ${info.top - main.top}px`);
    await ctx.close();
  } },
  { name: '탭 줄의 밑줄이 검색바 아래까지 본문 폭 전체에 이어진다', run: async () => {
    const { page, ctx } = await open(1280);
    const r = await page.evaluate(() => { const bar = document.querySelector('.milestone-tabbar'), cs = getComputedStyle(bar), b = bar.getBoundingClientRect(), l = document.getElementById('issues').getBoundingClientRect(); return { bw: cs.borderBottomWidth, left: b.left, right: b.right, listLeft: l.left, listRight: l.right }; });
    assert.equal(r.bw, '1px', `탭 줄 밑줄 ${r.bw}`); assert.ok(Math.abs(r.right - r.listRight) <= 1 && Math.abs(r.left - r.listLeft) <= 1, '밑줄이 본문 폭 전체가 아니다');
    await ctx.close();
  } },
  { name: '좁은 화면: 설명 열이 먼저 오고 제목과 이슈 목록이 그 아래에 이어진다', run: async () => {
    const { page, ctx } = await open(600);
    const info = await rect(page, '.milestone-info'), head = await rect(page, '.milestone-head');
    assert.ok(info.top < head.top, `설명(${info.top})이 머리(${head.top})보다 위에 있어야 한다`);
    await ctx.close();
  } },
  { name: '좁은 화면에서 검색바가 탭 줄에 들어가지 않으면 아래 줄로 내려가고 화면 밖으로 나가지 않는다', run: async () => {
    const { page, ctx } = await open(360);
    const search = await rect(page, '.milestone-search');
    assert.ok(search.right <= 360 + 1 && search.left >= 0, `검색바 ${search.left.toFixed(0)}~${search.right.toFixed(0)}가 화면(360) 밖이다`);
    await ctx.close();
  } },
  { name: '검색 입력칸이 검색 상자 안에 온전히 들어가 오른쪽 테두리가 잘리지 않고 돋보기 단추와 겹치지 않는다', run: async () => {
    const { page, ctx } = await open(1280);
    const box = await rect(page, '.milestone-search'), inp = await rect(page, '.milestone-search input'), btn = await rect(page, '.milestone-search button');
    assert.ok(inp.right <= box.right + 0.5, `입력칸 오른쪽 ${inp.right.toFixed(1)}가 상자 오른쪽 ${box.right.toFixed(1)}을 넘는다(잘림)`);
    assert.ok(inp.left >= box.left - 0.5, '입력칸이 상자 왼쪽 밖이다');
    assert.ok(btn.left >= inp.left && btn.right <= box.right + 0.5, '돋보기 단추가 상자 안에 있다');
    await ctx.close();
  } },
  { name: '동작 단추는 열 폭을 채우는 세로 묶음이라 한 줄에 둘이 놓이지 않고 혼자 남는 단추가 없다', run: async () => {
    const { page, ctx } = await open(1280);
    const r = await page.evaluate(() => { const col = document.querySelector('.milestone-actions').getBoundingClientRect(); return { colW: col.width, btns: [...document.querySelectorAll('.milestone-actions .ybtn')].map((e) => { const b = e.getBoundingClientRect(); return { top: Math.round(b.top), w: Math.round(b.width), l: Math.round(b.left) }; }) }; });
    assert.equal(r.btns.length, 3);
    for (const b of r.btns) assert.ok(Math.abs(b.w - r.colW) <= 2, `단추 폭 ${b.w}이 묶음 폭 ${Math.round(r.colW)}과 다르다`);
    assert.ok(r.btns[0].top < r.btns[1].top && r.btns[1].top < r.btns[2].top, `한 줄에 둘 이상이다: ${r.btns.map((b) => b.top)}`);
    await ctx.close();
  } },
  { name: '"목록" 이동 링크는 설명 상자보다 위에 있는 글자 링크이다', run: async () => {
    const { page, ctx } = await open(1280);
    const back = await rect(page, '.milestone-back'), desc = await rect(page, '.milestone-desc');
    assert.ok(back.bottom <= desc.top + 1, `목록 링크(${back.bottom.toFixed(0)})가 설명(${desc.top.toFixed(0)})보다 위가 아니다`);
    assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('.milestone-back')).backgroundColor), 'rgba(0, 0, 0, 0)', '단추 바탕이 있다');
    await ctx.close();
  } },
];
