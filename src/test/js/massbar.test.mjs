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
  <div class="btn-group check-all"><label for="check-all"><input class="form-check-input" type="checkbox" id="check-all"></label></div>
  <div class="mass-actions"><span class="mass-selected"><b class="mass-count">1</b>개 선택됨</span><button type="button" class="btn btn-outline-secondary" data-mass-state="CLOSED">닫기</button>
  ${BTN('담당자 변경')}${BTN('마일스톤 변경')}${BTN('라벨 추가')}${BTN('라벨 제거')}<a href="#" class="mass-clear" data-mass-clear>선택 해제</a></div>
</form></div></div>
<ul class="issue-list"><li class="post-item" id="first">첫 이슈</li></ul>
</div></div></div></body></html>`;

async function open(selected) {
  const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
  await page.setContent(HTML);
  if (selected) await page.evaluate(() => document.querySelector('.mass-update-wrap').classList.add('has-selection'));
  return { page, ctx };
}
const rect = (page, s) => page.evaluate((q) => { const r = document.querySelector(q).getBoundingClientRect(); return { top: r.top, bottom: r.bottom, left: r.left, right: r.right, h: r.height }; }, s);

export const tests = [
  { name: '선택 전에는 도구줄(.mass-actions)이 보이지 않는다', run: async () => {
    const { page, ctx } = await open(false);
    assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('.mass-actions')).display), 'none');
    await ctx.close();
  } },
  { name: '선택하면 도구줄이 전체 선택 체크박스와 같은 줄에 나타난다', run: async () => {
    const { page, ctx } = await open(true);
    const chk = await rect(page, '#check-all'), count = await rect(page, '.mass-selected');
    assert.ok(Math.abs((chk.top + chk.bottom) / 2 - (count.top + count.bottom) / 2) <= 12, `체크박스 중심 ${((chk.top + chk.bottom) / 2).toFixed(0)}, 개수 표시 중심 ${((count.top + count.bottom) / 2).toFixed(0)}`);
    assert.ok(count.left > chk.right, '개수 표시가 체크박스 오른쪽에 있다');
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
];
