// 위키 화면의 두 곳(A-533): 오른쪽 열의 "복사" 단추는 입력칸과 한 줄에 붙은 단추이고, 새 페이지의 파일 첨부는 사이트의 "파일 올리기" 단추이다.
// 컴파일된 실제 CSS가 필요하다(`./gradlew compileSass`).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, ROOT } from './harness.mjs';

const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const CSS_ALL = fs.readFileSync(path.join(CSS, 'bootstrap.css'), 'utf8') + '\n' + fs.readFileSync(path.join(CSS, 'yobi.css'), 'utf8');

async function page(body) {
  const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const p = await ctx.newPage();
  await p.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>${body}</body></html>`);
  return { p, ctx };
}
const rect = (p, q) => p.evaluate((s) => { const r = document.querySelector(s).getBoundingClientRect(); return { top: r.top, bottom: r.bottom, left: r.left, right: r.right, w: r.width, h: r.height }; }, q);

const CLONE = `<div class="wiki-wrap"><div class="page-wrap-outer"><div class="shell-main" style="width:990px"><div style="width:232px"><div class="wiki-sidebar"><div class="wiki-clone mb-4">
  <h4 class="wiki-clone-title">이 위키를 로컬에 복제</h4>
  <div class="input-group input-group-sm"><input type="text" class="wiki-clone-url form-control" readonly value="http://admin@127.0.0.1:19541/admin/demo.wiki.git"><button type="button" class="wiki-clone-copy btn btn-outline-secondary">복사</button></div>
  <div class="wiki-clone-feedback small" role="status"></div></div></div></div></div></div></div>`;

const ATTACH = `<div class="wiki-wrap"><div class="page-wrap-outer"><div class="shell-main" style="width:737px"><form class="wiki-form"><div class="wiki-attach mb-3"><label class="form-label small mb-1 d-block">파일 첨부</label>
  <div class="nbtn medium white fake-file-wrap wiki-attach-button"><i class="yobicon-upload"></i> 파일 올리기<input type="file" class="wiki-attach-input file form-control"></div>
  <div class="form-text">파일은 위키의 uploads 폴더에 저장되고 링크가 본문에 들어갑니다.</div></div></form></div></div></div>`;

export const tests = [
  { name: '로컬 복제의 주소 입력칸과 "복사" 단추는 같은 줄에 붙어 있고 높이가 같다', run: async () => {
    const { p, ctx } = await page(CLONE);
    const inp = await rect(p, '.wiki-clone-url'), btn = await rect(p, '.wiki-clone-copy'), group = await rect(p, '.wiki-clone .input-group');
    assert.ok(Math.abs(inp.top - btn.top) <= 2, `단추가 다음 줄이다: 입력칸 위 ${inp.top.toFixed(0)}, 단추 위 ${btn.top.toFixed(0)}`);
    assert.ok(Math.abs(inp.h - btn.h) <= 2, `높이가 다르다: 입력칸 ${inp.h.toFixed(0)}, 단추 ${btn.h.toFixed(0)}`);
    assert.ok(btn.left >= inp.right - 2 && btn.right <= group.right + 1, `단추(${btn.left.toFixed(0)}~${btn.right.toFixed(0)})가 입력칸(~${inp.right.toFixed(0)}) 오른쪽에 붙어 묶음 안에 있다`);
    assert.ok(inp.w >= 100, `입력칸이 너무 좁다 ${inp.w.toFixed(0)}px`);
    await ctx.close();
  } },
  { name: '복사 결과 글자는 입력 묶음 밖의 별도 줄이라 단추 줄을 밀어내지 않는다', run: async () => {
    const { p, ctx } = await page(CLONE);
    await p.evaluate(() => { document.querySelector('.wiki-clone-feedback').textContent = '복사했습니다'; });
    const group = await rect(p, '.wiki-clone .input-group'), fb = await rect(p, '.wiki-clone-feedback');
    assert.ok(fb.top >= group.bottom - 1, `피드백(${fb.top.toFixed(0)})이 묶음(${group.bottom.toFixed(0)}) 아래 줄이 아니다`);
    await ctx.close();
  } },
  { name: '파일 첨부는 브라우저 기본 입력이 아니라 "파일 올리기" 단추로 보이고 투명한 파일 입력이 단추 위를 덮는다', run: async () => {
    const { p, ctx } = await page(ATTACH);
    const r = await p.evaluate(() => { const w = document.querySelector('.wiki-attach-button'), i = document.querySelector('.wiki-attach-input'); const wr = w.getBoundingClientRect(), ir = i.getBoundingClientRect(), cs = getComputedStyle(i); return { btnW: wr.width, btnH: wr.height, opacity: cs.opacity, overlaps: ir.left < wr.right && ir.right > wr.left && ir.top < wr.bottom + 1 && ir.bottom > wr.top, display: getComputedStyle(w).display }; });
    assert.equal(r.opacity, '0', `파일 입력이 보인다(opacity ${r.opacity}): 영문 기본 글자가 노출된다`);
    assert.ok(r.overlaps, '파일 입력이 단추를 덮지 않아 눌러도 파일 선택이 안 열린다');
    assert.ok(r.btnH >= 26 && r.btnH <= 40, `단추 높이 ${r.btnH.toFixed(0)}px`);
    assert.ok(r.btnW < 300, `단추가 한 줄 전체로 늘어난다: ${r.btnW.toFixed(0)}px`);
    await ctx.close();
  } },
  { name: '"파일 첨부" 글자는 블록이라 올리기 단추 위의 별도 줄이다(인라인이면 단추와 같은 줄에 붙는다)', run: async () => {
    const { p, ctx } = await page(ATTACH);
    assert.equal(await p.evaluate(() => getComputedStyle(document.querySelector('.wiki-attach > label')).display), 'block', '글자가 인라인이라 단추와 같은 줄에 붙는다');
    const lab = await rect(p, '.wiki-attach > label'), btn = await rect(p, '.wiki-attach-button');
    assert.ok(lab.bottom <= btn.top + 1, `글자(${lab.bottom.toFixed(0)})가 단추 위(${btn.top.toFixed(0)})가 아니다`);
    await ctx.close();
  } },
];
