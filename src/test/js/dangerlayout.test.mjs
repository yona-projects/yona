// 위험 구역(A-535)의 모양: 세 구역이 같은 구성(제목이 위, 설명이 아래)이고 동의 체크박스가 글자와 같은 높이이다(컴파일된 실제 CSS 필요).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, ROOT } from './harness.mjs';

const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const CSS_ALL = fs.readFileSync(path.join(CSS, 'bootstrap.css'), 'utf8') + '\n' + fs.readFileSync(path.join(CSS, 'yobi.css'), 'utf8');
const consent = (id, text) => `<div class="form-check danger-consent"><input type="checkbox" class="form-check-input" id="${id}" autocomplete="off"><label for="${id}" class="form-check-label label-agreement">${text}</label></div>`;
const HTML = `<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div class="page-wrap-outer"><div class="project-page-wrap shell"><div class="shell-main" style="width:990px;flex:none">
<section id="danger-transfer" class="danger-section"><h3 class="danger-title">프로젝트 이관</h3><div class="cu-desc"><ul><li class="notice"><strong>안내</strong></li></ul>${consent('acceptTransfer', '프로젝트를 이관하는데 동의합니다.')}</div><div class="danger-actions"><a class="ybtn ybtn-danger">이관</a></div></section>
<section id="danger-delete" class="danger-section"><h3 class="danger-title cu-label">프로젝트 삭제</h3><div class="cu-desc"><p><strong class="notice">프로젝트를 삭제하게되면 모든 데이터가 삭제됩니다.</strong></p>${consent('acceptDelete', '프로젝트를 삭제하는데 동의합니다.')}</div><div class="danger-actions"><a class="ybtn ybtn-danger">삭제</a></div></section>
</div></div></div></body></html>`;

async function open() {
  const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
  await page.setContent(HTML); return { page, ctx };
}
const r = (page, q) => page.evaluate((s) => { const b = document.querySelector(s).getBoundingClientRect(); return { top: b.top, bottom: b.bottom, left: b.left, right: b.right, h: b.height }; }, q);

export const tests = [
  { name: '삭제 구역도 제목이 위, 설명이 아래이다(제목 옆으로 설명이 나란히 놓이지 않는다)', run: async () => {
    const { page, ctx } = await open();
    const t = await r(page, '#danger-delete .danger-title'), d = await r(page, '#danger-delete .cu-desc');
    assert.ok(t.bottom <= d.top + 1, `제목(${t.bottom.toFixed(0)})이 설명 위(${d.top.toFixed(0)})가 아니라 옆에 있다`);
    assert.ok(d.left - 1 <= t.left + 4, `설명이 제목 오른쪽으로 밀려 있다: 제목 ${t.left.toFixed(0)}, 설명 ${d.left.toFixed(0)}`);
    await ctx.close();
  } },
  { name: '모달 머리글은 제목이 왼쪽, × 닫기 단추가 오른쪽 끝이고 제목은 지나치게 크지 않다(A-537)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div class="modal show" style="display:block"><div class="modal-dialog"><div class="modal-content"><div class="modal-header"><button type="button" class="btn-close" aria-label="Close"></button><h3>프로젝트를 삭제하시겠습니까?</h3></div><div class="modal-body">본문</div></div></div></div></body></html>`);
    const t = await r(page, '.modal-header h3'), x = await r(page, '.modal-header .btn-close'), h = await r(page, '.modal-header');
    assert.ok(t.left < x.left, `제목(${t.left.toFixed(0)})이 × (${x.left.toFixed(0)})보다 왼쪽이어야 한다`);
    assert.ok(h.right - x.right <= 24, `×가 머리글 오른쪽 끝에서 ${(h.right - x.right).toFixed(0)}px 떨어져 있다`);
    const fs = await page.evaluate(() => parseFloat(getComputedStyle(document.querySelector('.modal-header h3')).fontSize));
    assert.ok(fs <= 22, `제목 글자가 ${fs}px로 크다`);
    await ctx.close();
  } },
];
