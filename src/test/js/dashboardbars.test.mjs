import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// 프로젝트 홈 대시보드(A-501)의 "담당자별, 마일스톤별 열린 이슈" 막대가 자기 칸 밖으로 튀어나오지 않는지 실제 CSS로 잰다.
// 선생님 지적: 막대가 왼쪽 열의 경계를 넘어 오른쪽 열(라벨별) 위로 겹쳤다. 원인은 `.progress { width: 100px }` 고정 폭이 BS5 열 안쪽 여백(24px) 때문에 74px뿐인 칸보다 넓었기 때문이다.
const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const bar = (pct) => `<div class="progress" title="${pct}%"><div class="progress-bar text-bg-warning" style="width:${pct}%"></div></div>`;
const row = (name, n, pct) => `<div class="row"><div class="col-lg-6"><a class="usf-group"><strong class="name">${name}</strong><span class="loginid"> <strong>@</strong>admin</span></a></div><div class="col-lg-3 num"><strong>${n}</strong></div><div class="col-lg-3 nm">${bar(pct)}</div></div>`;
const HTML = `<div class="page-wrap-outer"><div class="page-wrap shell">
<nav class="shell-sidebar" style="width:232px">사이드바</nav>
<div class="shell-main"><div class="row"><div class="col-lg-9 span-left-pane"><div class="content-container nm"><div class="project-overview-home row">
  <div class="col-lg-6"><h5>담당자별 열린 이슈</h5><div class="overview-assignee">${row('user', 2, 25)}${row('담당자 없음', 6, 75)}</div>
    <h5>마일스톤별 열린 이슈</h5><div class="overview-milestone">${row('v0.1', 4, 50)}</div></div>
  <div class="col-lg-6"><h5>라벨별 열린 이슈</h5><dl class="dl-horizontal overview-label"><dt>우선순위</dt><dd><div class="row"><div class="col-lg-10"><a><span class="issue-label list-label active">높음</span></a></div><div class="col-lg-2 num"><strong>0</strong></div></div><div class="row"><div class="col-lg-10"><a><span class="issue-label list-label active">아주 긴 라벨 이름</span></a></div><div class="col-lg-2 num"><strong>12</strong></div></div></dd></dl><dl class="dl-horizontal overview-label"><dt>종류</dt><dd><div class="row"><div class="col-lg-10"><a><span class="issue-label list-label active">버그</span></a></div><div class="col-lg-2 num"><strong>3</strong></div></div></dd></dl></div>
</div></div></div></div></div></div></div>`;

async function open(width = 1280) {
  const page = await pageWith({ html: HTML, stubs: false });
  await page.setViewportSize({ width, height: 800 });
  for (const css of ['bootstrap.css', 'usermenu.css', 'yobi.css']) await page.addStyleTag({ path: path.join(CSS, css) });
  return page;
}
const geo = (page) => page.evaluate(() => {
  const left = document.querySelector('.project-overview-home > .col-lg-6').getBoundingClientRect();
  const right = document.querySelectorAll('.project-overview-home > .col-lg-6')[1].getBoundingClientRect();
  return {
    leftRight: left.right, rightLeft: right.left,
    bars: [...document.querySelectorAll('.project-overview-home .progress')].map((p) => { const b = p.getBoundingClientRect(); const c = p.parentElement.getBoundingClientRect(); return { l: b.left, r: b.right, w: b.width, colRight: c.right }; }),
  };
});

export const tests = [
  ...[1100, 1280, 1920].map((w) => ({
    name: `막대는 자기 칸과 왼쪽 열 밖으로 나가지 않고 오른쪽 열과 겹치지 않는다(폭 ${w})`,
    async run() {
      const page = await open(w);
      const g = await geo(page);
      for (const b of g.bars) {
        assert.ok(b.r <= b.colRight + 0.5, `막대 오른쪽 ${b.r}가 칸 오른쪽 ${b.colRight}을 넘는다`);
        assert.ok(b.r <= g.leftRight + 0.5, `막대 오른쪽 ${b.r}가 왼쪽 열 오른쪽 ${g.leftRight}을 넘는다`);
        assert.ok(b.r <= g.rightLeft, `막대 오른쪽 ${b.r}가 오른쪽 열 시작 ${g.rightLeft}과 겹친다`);
      }
    },
  })),
  {
    name: '막대는 눈에 보일 만큼 길다(폭 1280에서 70px 이상)이고 v1.6의 최대 100px을 넘지 않는다',
    async run() {
      const page = await open(1280);
      const g = await geo(page);
      for (const b of g.bars) { assert.ok(b.w >= 70, `막대 폭 ${b.w}px`); assert.ok(b.w <= 100.5, `막대 폭 ${b.w}px`); }
    },
  },
  ...[1100, 1280, 1920].map((w) => ({
    name: `라벨 배지와 숫자는 한 줄로 읽힌다: 배지 바로 옆 숫자 열에 모이고 줄마다 숫자 오른쪽이 같은 선에 맞는다(폭 ${w}, A-501)`,
    async run() {
      const page = await open(w);
      const rows = await page.evaluate(() => [...document.querySelectorAll('.overview-label .row')].map((r) => {
        const badge = r.querySelector('.issue-label').getBoundingClientRect();
        const num = r.querySelector('.num').getBoundingClientRect();
        const dd = r.closest('dd').getBoundingClientRect();
        const numText = r.querySelector('.num strong').getBoundingClientRect();
        return { badgeRight: badge.right, numLeft: numText.left, numRight: numText.right, ddRight: dd.right, numCellRight: num.right, h: badge.height };
      }));
      assert.equal(rows.length, 3);
      // 숫자 열을 같은 선에 맞추므로, 같은 분류에서 짧은 배지 뒤에는 가장 긴 배지와의 폭 차이만큼 간격이 더 생긴다. 그 차이를 뺀 간격은 열 사이 여백(14px)에 한 자리 숫자를 오른쪽에 맞춘 폭 차이와 여유를 더해 40px 이하여야 한다.
      const widths = await page.evaluate(() => [...document.querySelectorAll('.overview-label .row .issue-label')].map((e) => e.getBoundingClientRect().width));
      const longest = Math.max(widths[0], widths[1]); // 우선순위 분류의 두 줄
      rows.forEach((r, i) => {
        const gap = r.numLeft - r.badgeRight;
        const extra = i < 2 ? longest - widths[i] : 0;
        assert.ok(gap >= 0 && gap - extra <= 40, `줄 ${i}: 배지와 숫자 사이 ${Math.round(gap)}px, 폭 차이 ${Math.round(extra)}px를 빼도 40px를 넘는다`);
        assert.ok(r.h <= 30, `배지 높이 ${r.h}px: 한 줄이다(글자가 접히지 않는다)`);
      });
      // 같은 분류의 두 줄은 숫자 오른쪽이 같은 선에 있다
            assert.ok(Math.abs(rows[0].numRight - rows[1].numRight) <= 1, `숫자 오른쪽 ${rows[0].numRight} / ${rows[1].numRight}`);
      // 숫자가 칸 오른쪽 끝에 붙어 배지와 멀리 떨어지지 않는다
      assert.ok(rows[0].ddRight - rows[0].numRight > 20, `숫자가 오른쪽 끝(${rows[0].ddRight})에 붙어 있다: ${rows[0].numRight}`);
    },
  })),
];
