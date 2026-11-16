import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// "내 파일" 표(A-499). 선생님 지적: 머리글이 크다, 파일 이름이 다 보이지 않는다, 내려받기 열은 아이콘뿐인데 제목이 더 길다, 날짜가 열 안에 충분히 보여야 한다.
// 실제 CSS로 머리글 크기, 파일 이름 전체 표시, 내려받기 열 폭, 날짜 한 줄을 잰다.
const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const NAME = '16858-2026-11-9-21-41-screenshot.png'; // 38자, 예전 열 폭에서는 ".p / ng"로 끊겼다
const row = (name, date) => `<div class="attachment-file-detail row">
  <div class="file-preview col-lg-1"></div>
  <div class="col-lg-4 file-name"><a href="#"><i class="icon text-icon"></i>${name}</a></div>
  <div class="col-lg-1 file-size">182 B</div>
  <div class="col-lg-1 file-download"><a href="#"><button type="button" class="ybtn"><i class="yobicon-cloud-download"></i></button></a></div>
  <div class="col-lg-2 file-date">${date}</div>
  <div class="col-lg-3 file-location"><a href="#">/admin/hglfs/commit/235e479ceedade5094b2bbf1428bf55c9fa81dcc</a></div>
</div>`;
const HTML = `<div class="page-wrap-outer"><div class="page-wrap shell"><div class="shell-main"><div class="attachment-files">
<div class="attachment-files-header row">
  <div class="col-lg-1 header-preview">Preview</div>
  <div class="col-lg-4 header-file-name">Filename</div>
  <div class="col-lg-1 header-size">Size</div>
  <div class="col-lg-1 header-download" title="Download"><i class="yobicon-cloud-download" aria-hidden="true"></i><span class="visually-hidden">Download</span></div>
  <div class="col-lg-2 file-date">Date</div>
  <div class="col-lg-3 header-location">Location</div>
</div>
${row(NAME, '2026-11-10 12:00')}
${row('a.txt', '2026-11-09 21:45')}
</div></div></div></div>`;

async function open(width = 1280) {
  const page = await pageWith({ html: HTML, stubs: false });
  await page.setViewportSize({ width, height: 700 });
  for (const css of ['bootstrap.css', 'usermenu.css', 'yobi.css']) await page.addStyleTag({ path: path.join(CSS, css) });
  await page.addStyleTag({ content: '*, *::before, *::after { transition: none !important; }' });
  return page;
}
const cells = (page) => page.evaluate(() => { const r = document.querySelector('.attachment-file-detail'); const o = { row: Math.round(r.getBoundingClientRect().width) }; for (const c of r.children) { const b = c.getBoundingClientRect(); o[c.className.replace(/col-lg-\d+/, '').trim().split(' ')[0]] = Math.round(b.width) + 'x' + Math.round(b.height); } return JSON.stringify(o); });
const m = (page, sel) => page.evaluate((s) => { const e = document.querySelector(s); const r = e.getBoundingClientRect(); const cs = getComputedStyle(e); return { w: r.width, h: r.height, sw: e.scrollWidth, cw: e.clientWidth, fs: parseFloat(cs.fontSize) }; }, sel);

export const tests = [
  {
    name: '머리글은 작은 글자와 얇은 줄이다(글자 13px 이하, 높이 34px 이하)',
    async run() {
      const page = await open();
      const h = await m(page, '.attachment-files-header');
      const t = await m(page, '.attachment-files-header .header-file-name');
      assert.ok(t.fs <= 13, `머리글 글자 ${t.fs}px`);
      assert.ok(h.h <= 34, `머리글 높이 ${h.h}px`);
    },
  },
  {
    name: '파일 이름은 끊기거나 잘리지 않고 한 줄에 다 보인다(38자)',
    async run() {
      const page = await open();
      const n = await m(page, '.attachment-file-detail .file-name');
      const a = await m(page, '.attachment-file-detail .file-name a');
      assert.ok(n.sw <= n.cw + 1, `이름 칸 안에 들어간다 ${n.sw}/${n.cw}`);
      assert.ok(a.h <= 32, `한 줄이다(높이 ${a.h}px) 칸: ${await cells(page)}`);
    },
  },
  {
    name: '내려받기 열은 아이콘만 들어갈 폭이고, 머리글 아이콘이 그 안에 들어간다',
    async run() {
      const page = await open();
      const d = await m(page, '.attachment-files-header .header-download');
      const btn = await m(page, '.attachment-file-detail .file-download .ybtn');
      assert.ok(d.w <= 64, `내려받기 열 폭 ${d.w}px`);
      assert.ok(btn.w <= d.w, `단추(${btn.w})가 열(${d.w}) 안에 들어간다`);
      assert.ok(d.sw <= d.cw + 1, `머리글이 열 밖으로 넘치지 않는다 ${d.sw}/${d.cw}`);
      assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('.header-download .visually-hidden')).position), 'absolute', '글자는 화면에서 숨겨진다');
    },
  },
  {
    name: '날짜(2026-11-10 12:00)는 열 안에 한 줄로 다 보인다',
    async run() {
      const page = await open();
      const d = await m(page, '.attachment-file-detail .file-date');
      assert.ok(d.sw <= d.cw + 1, `날짜가 열 안에 들어간다 ${d.sw}/${d.cw}`);
      assert.ok(d.h <= 32, `한 줄이다(높이 ${d.h}px)`);
    },
  },
  {
    name: '넓은 폭(1920)과 보통 폭(1100)에서도 이름과 날짜가 한 줄이다',
    async run() {
      for (const w of [1100, 1920]) {
        const page = await open(w);
        const a = await m(page, '.attachment-file-detail .file-name a');
        const d = await m(page, '.attachment-file-detail .file-date');
        assert.ok(a.h <= 32, `폭 ${w}: 이름 높이 ${a.h}`);
        assert.ok(d.h <= 32, `폭 ${w}: 날짜 높이 ${d.h}`);
      }
    },
  },
];
