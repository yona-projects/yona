// 게시판 도구줄(A-531): 라벨 선택은 옆의 검색 칸과 같은 높이와 글자 크기이고, "단축키 안내" 모달은 화면 가운데에 온전히 뜬다.
// 실제 select2 라이브러리와 컴파일된 실제 CSS(`./gradlew compileSass`)로 잰다.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, JS, ROOT } from './harness.mjs';

const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const read = (p) => fs.readFileSync(p, 'utf8');
const BASE_CSS = [read(path.join(CSS, 'bootstrap.css')), read(path.join(JS, 'lib/select2/select2.css')), read(path.join(JS, 'lib/select2/select2-bootstrap-5-theme.css')), read(path.join(CSS, 'yobi.css'))].join('\n');

async function page(width, body) {
  const b = await launch(); const ctx = await b.newContext({ viewport: { width, height: 800 } }); const p = await ctx.newPage();
  await p.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${BASE_CSS}</style></head><body>${body}</body></html>`);
  return { p, ctx };
}

export const tests = [
  { name: '게시판 도구줄의 라벨 선택은 옆의 검색 칸과 높이와 글자 크기가 같다(검색 32px, 13px)', run: async () => {
    const { p, ctx } = await page(1280, `<div class="page-wrap-outer"><div class="shell-main" style="width:990px"><div class="d-flex align-items-start gap-2">
      <div class="search search-bar" style="width:205px"><input class="textbox" type="text" placeholder="검색"><button class="search-btn"></button></div>
      <div class="board-labels" style="width:206px"><dl><dd><select id="labelIds" multiple="multiple" class="hide form-select"><optgroup label="종류"><option value="1">버그</option></optgroup></select></dd></dl></div></div></div></div>`);
    await p.addScriptTag({ content: read(path.join(JS, 'lib/jquery/jquery-3.7.1.js')) });
    await p.addScriptTag({ content: read(path.join(JS, 'lib/select2/select2.js')) });
    await p.evaluate(() => { $('#labelIds').select2({ theme: 'bootstrap-5', width: '100%', placeholder: '라벨 선택', allowClear: true }); $('.select2-container').addClass('issue-labels bordered'); });
    const r = await p.evaluate(() => { const g = (e) => { const b = e.getBoundingClientRect(); return { h: Math.round(b.height), fs: parseFloat(getComputedStyle(e).fontSize) }; }; const field = document.querySelector('.select2-search__field'); return { search: g(document.querySelector('.search-bar')), sel: g(document.querySelector('.select2-selection--multiple')), field: g(field), containerH: Math.round(document.querySelector('.select2-container').getBoundingClientRect().height) }; });
    assert.ok(Math.abs(r.sel.h - r.search.h) <= 2, `라벨 선택 높이 ${r.sel.h}px, 검색 칸 ${r.search.h}px`);
    assert.ok(r.sel.h <= r.containerH + 1, `선택 영역(${r.sel.h}px)이 컨테이너(${r.containerH}px) 밖으로 넘친다`);
    assert.ok(r.field.fs <= 14, `라벨 입력 글자 ${r.field.fs}px(13px 안팎이어야 한다)`);
    await ctx.close();
  } },
  { name: '"단축키 안내" 모달은 화면 가운데에 온전히 뜨고 화면 밖으로 나가지 않는다(넓은 화면)', run: async () => {
    const { p, ctx } = await page(1280, `<div id="helpKeys" class="modal fade keymap-help show" style="display:block" tabindex="-1"><div class="modal-dialog"><div class="modal-content"><div class="row"><div class="col-lg-3"><h5>프로젝트</h5></div><div class="col-lg-9"><h5>이슈 목록</h5></div></div></div></div></div>`);
    const r = await p.evaluate(() => { const d = document.querySelector('#helpKeys .modal-dialog').getBoundingClientRect(); return { left: d.left, right: d.right, w: d.width, vw: innerWidth }; });
    assert.ok(r.left >= 0 && r.right <= r.vw, `모달 ${r.left.toFixed(0)}~${r.right.toFixed(0)}이 화면(${r.vw}) 밖이다`);
    assert.ok(Math.abs((r.left + r.right) / 2 - r.vw / 2) <= 4, `가운데가 아니다: 중심 ${((r.left + r.right) / 2).toFixed(0)}, 화면 중심 ${r.vw / 2}`);
    await ctx.close();
  } },
  { name: '"단축키 안내" 모달은 폰 폭에서도 화면 밖으로 나가지 않는다', run: async () => {
    const { p, ctx } = await page(400, `<div id="helpKeys" class="modal fade keymap-help show" style="display:block" tabindex="-1"><div class="modal-dialog"><div class="modal-content"><div class="row"><div class="col-lg-3"><h5>프로젝트</h5></div></div></div></div></div>`);
    const r = await p.evaluate(() => { const d = document.querySelector('#helpKeys .modal-dialog').getBoundingClientRect(); return { left: d.left, right: d.right, vw: innerWidth }; });
    assert.ok(r.left >= 0 && r.right <= r.vw + 0.5, `모달 ${r.left.toFixed(0)}~${r.right.toFixed(0)}이 폰 화면(${r.vw}) 밖이다`);
    await ctx.close();
  } },
];
