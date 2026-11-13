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

const MODAL_HTML = (disabled) => `<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div id="alertDeletion" class="modal show" style="display:block"><div class="modal-dialog"><div class="modal-content"><div class="modal-header"><h3>제목</h3></div><div class="modal-body"><p>설명</p><p class="mb-0"><label for="deleteConfirmName" class="form-label">입력 안내</label><input type="text" id="deleteConfirmName" class="form-control w-100"></p></div><div class="modal-footer"><button id="btnDeleteExec" type="button" class="ybtn ybtn-danger"${disabled ? ' disabled' : ''}>프로젝트를 삭제합니다.</button></div></div></div></div></body></html>`;

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
  { name: '확인 창의 실행 단추는 입력란 바로 아래(여백 14px 이하)에 전체 너비로 붙고 푸터 구분선이 없다(A-540)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(MODAL_HTML(false));
    const i = await r(page, '#deleteConfirmName'), x = await r(page, '#btnDeleteExec'), f = await r(page, '.modal-footer');
    assert.ok(x.top - i.bottom <= 14, `입력란과 단추 사이가 ${(x.top - i.bottom).toFixed(0)}px 벌어져 있다`);
    assert.ok(x.right - x.left >= (f.right - f.left) - 40, `단추 너비 ${(x.right - x.left).toFixed(0)}px, 푸터 ${(f.right - f.left).toFixed(0)}px`);
    const bt = await page.evaluate(() => parseFloat(getComputedStyle(document.querySelector('.modal-footer')).borderTopWidth));
    assert.equal(bt, 0, '푸터 위 구분선이 있다');
    await ctx.close();
  } },
  { name: '비활성 단추도 글자가 읽힌다(글자와 배경의 대비가 3 이상, A-540)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(MODAL_HTML(true));
    const ratio = await page.evaluate(() => {
      const e = document.getElementById('btnDeleteExec'), cs = getComputedStyle(e), op = parseFloat(cs.opacity);
      const rgb = (c) => c.match(/[\d.]+/g).slice(0, 3).map(Number);
      const mix = (c, o) => c.map((v) => v * o + 255 * (1 - o));
      const lum = (c) => { const [r, g, b2] = c.map((v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }); return 0.2126 * r + 0.7152 * g + 0.0722 * b2; };
      const fg = mix(rgb(cs.color), op), bg = mix(rgb(cs.backgroundColor), op);
      const [l1, l2] = [lum(fg), lum(bg)].sort((a, b2) => b2 - a); return (l1 + 0.05) / (l2 + 0.05);
    });
    assert.ok(ratio >= 3, `비활성 단추의 글자 대비가 ${ratio.toFixed(2)}이다`);
    await ctx.close();
  } },
  { name: '알림 창(yobiDialog)은 겉껍질이 화면 전체를 덮고 창이 가운데에 놓인다(A-546)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div id="yobiDialog" class="modal yobiDialog show" style="display:block" tabindex="-1"><div class="modal-dialog"><div class="modal-content"><div class="btn-dismiss"><button type="button" class="btn-transparent">×</button></div><div class="message"><div class="center-text"><p class="msg">알림 문구입니다.</p><p class="desc"></p></div><div class="center-txt buttons"><button type="button" class="ybtn ybtn-info">확인</button></div></div></div></div></div></body></html>`);
    const shell = await r(page, '#yobiDialog'), box = await r(page, '#yobiDialog .modal-content');
    assert.ok(Math.round(shell.right - shell.left) >= 1280, `겉껍질 너비가 ${(shell.right - shell.left).toFixed(0)}px라 화면 전체를 덮지 못한다`);
    const center = (box.left + box.right) / 2;
    assert.ok(Math.abs(center - 640) <= 2, `창 가운데가 ${center.toFixed(0)}px에 있다(640이어야 한다)`);
    assert.ok(box.right - box.left <= 540, `창 너비가 ${(box.right - box.left).toFixed(0)}px이다`);
    await ctx.close();
  } },
  { name: '새 배포 키 폼은 폭이 760px 이하이고 쓰기 허용 체크 상자는 글자와 같은 높이의 16px이다(A-549)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div class="page-wrap-outer"><div class="project-page-wrap shell"><div class="shell-main" style="width:990px;flex:none"><form id="deploy-key-create" class="deploy-key-form"><div class="mb-3"><input class="form-control w-100" type="text"></div><div class="form-check deploy-key-writable"><input id="w" type="checkbox" class="form-check-input"><label for="w" class="form-check-label">쓰기 허용</label><div class="form-text">도움말</div></div></form></div></div></div></body></html>`);
    const f = await r(page, '#deploy-key-create');
    assert.ok(f.right - f.left <= 760, `폼 너비가 ${(f.right - f.left).toFixed(0)}px이다`);
    const c = await r(page, '#w'), l = await r(page, 'label[for=w]');
    assert.ok(c.h >= 15 && c.h <= 18, `체크 상자 크기 ${c.h}px`);
    assert.ok(Math.abs((c.top + c.bottom) / 2 - (l.top + l.bottom) / 2) <= 4, `체크 상자 중심 ${((c.top + c.bottom) / 2).toFixed(0)}, 글자 중심 ${((l.top + l.bottom) / 2).toFixed(0)}`);
    await ctx.close();
  } },
];
