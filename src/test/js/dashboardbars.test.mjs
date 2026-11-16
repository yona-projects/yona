import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// 프로젝트 홈 대시보드(A-501)가 v1.6(Bootstrap 2 row-fluid)과 같은 칸 비율로 그려지는지 실제 CSS로 잰다.
// v1.6 실행 화면(공식 v1.16.0)에 같은 템플릿 줄을 끼워 실측한 값: 막대 `.progress`는 100px 고정, 칸은 이름 48.936%, 개수와 막대 각 23.404%, 칸 사이 2.1277%, 칸 안쪽 여백 없음.
// 같은 열 폭(370px)에서 v1.6은 막대 칸이 86px이고 막대(100px)가 열 경계 밖으로 5px 나오지만 오른쪽 열(16px 떨어짐)과는 겹치지 않았다.
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
  ...[1280, 1920].map((w) => ({
    name: `담당자별 줄의 칸 비율과 막대 폭이 v1.6과 같다: 이름 48.9%, 개수와 막대 칸 23.4%, 막대 100px 고정(폭 ${w})`,
    async run() {
      const page = await open(w);
      const m = await page.evaluate(() => {
        const row = document.querySelector('.overview-assignee .row');
        const parent = row.parentElement.getBoundingClientRect();
        const cols = [...row.children].map((c) => { const b = c.getBoundingClientRect(); return { l: b.left, w: b.width }; });
        const bar = row.querySelector('.progress').getBoundingClientRect();
        return { rowW: row.getBoundingClientRect().width, parentW: parent.width, cols, barW: bar.width, barL: bar.left, cellL: cols[2].l };
      });
      assert.ok(Math.abs(m.rowW - m.parentW) <= 1, `줄 폭 ${m.rowW}이 열 폭 ${m.parentW}과 같다(칸 안쪽 여백 없음, 줄이 열 밖으로 나가지 않는다)`);
      const ratio = (c) => c.w / m.rowW;
      assert.ok(Math.abs(ratio(m.cols[0]) - 0.48936) <= 0.004, `이름 칸 비율 ${ratio(m.cols[0])}`);
      assert.ok(Math.abs(ratio(m.cols[1]) - 0.23404) <= 0.004, `개수 칸 비율 ${ratio(m.cols[1])}`);
      assert.ok(Math.abs(ratio(m.cols[2]) - 0.23404) <= 0.004, `막대 칸 비율 ${ratio(m.cols[2])}`);
      assert.ok(Math.abs(m.barW - 100) <= 0.5, `막대 폭 ${m.barW}px (v1.6은 100px 고정)`);
      assert.ok(Math.abs(m.barL - m.cellL) <= 1, `막대는 칸 왼쪽에서 시작한다 ${m.barL} / ${m.cellL}`);
    },
  })),
  {
    name: '막대는 v1.6처럼 오른쪽 열(라벨별)과 겹치지 않는다(폭 1280)',
    async run() {
      const page = await open(1280);
      const g = await geo(page);
      for (const b of g.bars) assert.ok(b.r <= g.rightLeft, `막대 오른쪽 ${b.r}가 오른쪽 열 시작 ${g.rightLeft}과 겹친다`);
    },
  },
  ...[1100, 1280, 1920].map((w) => ({
    name: `라벨 개수는 v1.6처럼 칸 오른쪽 끝에 있고(배지 칸 10/12, 개수 칸 2/12), 배지는 줄바꿈하지 않는다(폭 ${w})`,
    async run() {
      const page = await open(w);
      const rows = await page.evaluate(() => [...document.querySelectorAll('.overview-label .row')].map((r) => {
        const dd = r.closest('dd').getBoundingClientRect();
        const num = r.querySelector('.num strong').getBoundingClientRect();
        const badge = r.querySelector('.issue-label').getBoundingClientRect();
        return { ddRight: dd.right, ddLeft: dd.left, numRight: num.right, badgeLeft: badge.left, badgeH: badge.height };
      }));
      for (const [i, r] of rows.entries()) {
        assert.ok(r.numRight <= r.ddRight + 0.5 && r.numRight >= r.ddRight - 20, `줄 ${i}: 개수 오른쪽 ${Math.round(r.numRight)}이 칸 오른쪽 끝 ${Math.round(r.ddRight)}에서 20px 안에 있다`);
        assert.ok(Math.abs(r.badgeLeft - r.ddLeft) <= 1, `줄 ${i}: 배지는 칸 왼쪽에서 시작한다`);
        assert.ok(r.badgeH <= 30, `줄 ${i}: 배지 높이 ${r.badgeH}px: 한 줄이다`);
      }
    },
  })),
  ...[1100, 1280, 1920].map((w) => ({
    name: `분류 이름(우선순위, 종류)은 첫 배지와 같은 줄에서 오른쪽으로 정렬되어 배지 바로 앞에 붙는다(폭 ${w}, v1.6의 dl-horizontal)`,
    async run() {
      const page = await open(w);
      const rows = await page.evaluate(() => [...document.querySelectorAll('.overview-label')].map((dl) => {
        const dt = dl.querySelector('dt'); const badge = dl.querySelector('.issue-label').getBoundingClientRect();
        const range = document.createRange(); range.selectNodeContents(dt); const text = range.getBoundingClientRect();
        const box = dt.getBoundingClientRect();
        return { textRight: text.right, textCy: text.top + text.height / 2, badgeLeft: badge.left, badgeCy: badge.top + badge.height / 2, dtLeft: box.left, dlLeft: dl.getBoundingClientRect().left, align: getComputedStyle(dt).textAlign, float: getComputedStyle(dt).float };
      }));
      for (const [i, r] of rows.entries()) {
        assert.ok(Math.abs(r.textCy - r.badgeCy) <= 12, `분류 ${i}: 이름(세로 ${Math.round(r.textCy)})이 첫 배지(세로 ${Math.round(r.badgeCy)})와 같은 줄이 아니다`);
        assert.ok(r.badgeLeft - r.textRight >= 0 && r.badgeLeft - r.textRight <= 30, `분류 ${i}: 이름 오른쪽과 배지 왼쪽 사이 ${Math.round(r.badgeLeft - r.textRight)}px (30px 이하여야 이어져 읽힌다)`);
      }
    },
  })),
];
