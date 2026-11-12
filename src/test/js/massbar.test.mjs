// 일괄 변경 도구줄(A-523)의 배치: 선택하면 체크박스와 같은 줄에 나타나고, 아래 이슈 목록과 겹치지 않는다(컴파일된 실제 CSS 필요).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, ROOT } from './harness.mjs';

const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const BTN = (t, extra = '') => `<div class="btn-group"><button class="btn btn-outline-secondary dropdown-toggle" ${extra}><span class="d-label">${t}</span></button></div>`;
const HTML = `<!doctype html><html><head><meta charset="utf-8"><style>${fs.readFileSync(path.join(CSS, 'bootstrap.css'), 'utf8')}</style><style>${fs.readFileSync(path.join(CSS, 'yobi.css'), 'utf8')}</style></head><body>
<div class="page-wrap-outer"><div class="project-page-wrap shell"><div class="shell-main" style="width:740px;flex:none">
<div class="filter-wrap mass-update-sticky"><div class="mass-update-wrap"><form id="mass-update-form" class="mass-update-form float-start">
  <div class="mass-actions"><span class="mass-selected"><b class="mass-count">1</b>개 선택됨</span><a href="#" class="mass-select-all-link" data-mass-select-all>모두 선택</a><button type="button" class="btn btn-outline-secondary" data-mass-state="CLOSED">닫기</button>
  ${BTN('담당자 변경')}${BTN('마일스톤 변경')}${BTN('라벨 추가')}${BTN('라벨 제거')}<a href="#" class="mass-clear" data-mass-clear>선택 해제</a></div>
</form></div><div class="filters float-end"><a class="filter">기한순</a><a class="filter">변경순</a></div></div>
<ul class="issue-list"><li class="post-item" id="first">첫 이슈</li></ul>
</div></div></div></body></html>`;

async function open(selected, withSort = true) {
  const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
  await page.setContent(HTML);
  if (!withSort) await page.evaluate(() => document.querySelector('.filters').remove());
  if (selected) await page.evaluate(() => document.querySelector('.mass-update-wrap').classList.add('has-selection'));
  return { page, ctx };
}
const rect = (page, s) => page.evaluate((q) => { const r = document.querySelector(q).getBoundingClientRect(); return { top: r.top, bottom: r.bottom, left: r.left, right: r.right, h: r.height }; }, s);

export const tests = [
  { name: '선택 전에는 도구줄이 없고(전체 선택 체크박스도 없다) 필터 줄에는 정렬만 오른쪽 끝에 남는다', run: async () => {
    const { page, ctx } = await open(false);
    assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('.mass-update-wrap')).display), 'none', '선택 전에 도구줄 상자가 보인다');
    assert.equal(await page.evaluate(() => document.querySelectorAll('#check-all').length), 0);
    const bar = await rect(page, '.filter-wrap'), sort = await rect(page, '.filters');
    assert.ok(bar.right - sort.right <= 1, `정렬이 줄의 오른쪽 끝에 있다: ${bar.right} / ${sort.right}`);
    assert.ok(bar.h <= 40, `필터 줄 높이 ${bar.h}px`);
    await ctx.close();
  } },
  { name: '선택하면 도구줄이 줄의 왼쪽 끝에서 시작해 한 줄에 들어가고 정렬 필터는 숨는다', run: async () => {
    const { page, ctx } = await open(true);
    assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('.filters')).display), 'none', '선택 중에도 정렬이 보인다');
    const bar = await rect(page, '.filter-wrap'), count = await rect(page, '.mass-selected'), last = await rect(page, '.mass-clear');
    assert.ok(count.left - bar.left <= 4, `개수 표시가 줄 왼쪽 끝에서 ${count.left - bar.left}px 떨어져 있다`);
    assert.ok(Math.abs((count.top + count.bottom) / 2 - (last.top + last.bottom) / 2) <= 14, `"선택 해제"가 다음 줄이다: ${((count.top + count.bottom) / 2).toFixed(0)} / ${((last.top + last.bottom) / 2).toFixed(0)}`);
    await ctx.close();
  } },
  { name: '도구줄이 아래 이슈 목록과 겹치지 않는다', run: async () => {
    const { page, ctx } = await open(true);
    const bar = await rect(page, '.filter-wrap'), actions = await rect(page, '.mass-actions'), first = await rect(page, '#first');
    assert.ok(actions.bottom <= first.top + 0.5, `도구줄 아래 ${actions.bottom.toFixed(0)}가 첫 이슈 위 ${first.top.toFixed(0)}보다 아래이다(겹침)`);
    assert.ok(bar.bottom <= first.top + 0.5, `필터 줄 아래 ${bar.bottom.toFixed(0)}, 첫 이슈 위 ${first.top.toFixed(0)}`);
    await ctx.close();
  } },
  { name: '"닫기" 단추의 높이와 글자 크기가 옆의 드롭다운 단추와 같다', run: async () => {
    const { page, ctx } = await open(true);
    const r = await page.evaluate(() => { const a = document.querySelector('[data-mass-state]'), d = document.querySelector('.mass-actions .dropdown-toggle'); const f = (e) => ({ h: Math.round(e.getBoundingClientRect().height), fs: getComputedStyle(e).fontSize }); return { state: f(a), drop: f(d) }; });
    assert.deepEqual(r.state, r.drop, `상태 단추 ${JSON.stringify(r.state)}, 드롭다운 단추 ${JSON.stringify(r.drop)}`);
    await ctx.close();
  } },
  { name: '"모두 선택" 링크는 개수 표시 바로 옆에 있다', run: async () => {
    const { page, ctx } = await open(true);
    const count = await rect(page, '.mass-selected'), all = await rect(page, '.mass-select-all-link');
    assert.ok(all.left >= count.right - 1 && all.left - count.right <= 24, `개수(${count.right.toFixed(0)})와 모두 선택(${all.left.toFixed(0)}) 사이 ${(all.left - count.right).toFixed(0)}px`);
    await ctx.close();
  } },
  { name: '정렬이 없는 화면(마일스톤)은 선택 전에 필터 줄이 자리를 차지하지 않고 이슈 목록이 바로 이어진다', run: async () => {
    const { page, ctx } = await open(false, false);
    assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('.filter-wrap')).display), 'none', '내용 없는 필터 줄이 보인다');
    await ctx.close();
  } },
  { name: '정렬이 없는 화면도 이슈를 고르면 도구줄이 보인다', run: async () => {
    const { page, ctx } = await open(true, false);
    const bar = await rect(page, '.filter-wrap'), count = await rect(page, '.mass-selected');
    assert.ok(bar.h >= 20 && count.h > 0, `필터 줄 높이 ${bar.h}px, 개수 표시 높이 ${count.h}px`);
    await ctx.close();
  } },
];
