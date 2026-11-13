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
  { name: '규칙 묶음 폼은 레이블이 입력란 위에 쌓이고 폭이 760px 이하이며 체크 상자가 16px이고 카드 상자로 감싸지 않는다(A-550, A-552)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div class="page-wrap-outer"><div class="project-page-wrap shell"><div class="shell-main" style="width:990px;flex:none"><form id="rulesetForm" class="ruleset-form">
<section class="ruleset-section" id="rulesetBasics"><h4 class="ruleset-section-title">이름</h4><label for="n" class="form-label">규칙 묶음 이름</label><input id="n" type="text" class="form-control"></section>
<section class="ruleset-section" id="rulesetRules"><h4 class="ruleset-section-title">규칙</h4><label class="rule-option"><input id="c" type="checkbox" class="form-check-input"><span class="rule-option-text"><span class="rule-option-title">삭제 제한</span><span class="rule-option-desc">대상 브랜치를 지우지 못하게 합니다.</span></span></label></section>
</form></div></div></div></body></html>`);
    const f = await r(page, '#rulesetForm'); assert.ok(f.right - f.left <= 760, `폼 너비 ${(f.right - f.left).toFixed(0)}px`);
    // 구역 제목과 내용 사이 간격은 눈에 보이는 거리가 14px 이하이다(A-554)
    const gaps = await page.evaluate(() => ['#rulesetBasics', '#rulesetRules'].map((id) => { const sec = document.querySelector(id), ti = sec.querySelector('.ruleset-section-title'); const content = sec.querySelector('label.form-label, .rule-option-title'); return { id, titleH: ti.getBoundingClientRect().height, gap: content.getBoundingClientRect().top - ti.getBoundingClientRect().bottom }; }));
    gaps.forEach((g) => { assert.ok(g.titleH <= 28, `${g.id} 제목 박스 높이가 ${g.titleH}px이다`); assert.ok(g.gap <= 14, `${g.id} 제목과 내용 사이가 ${g.gap.toFixed(0)}px이다`); });
    // 텍스트 박스는 읽기 편한 크기와 모양이고 포커스가 보인다(A-552)
    const ib = await page.evaluate(() => { const e = document.getElementById('n'); const c = getComputedStyle(e); const before = c.boxShadow; e.focus(); const after = getComputedStyle(e).boxShadow; return { h: e.getBoundingClientRect().height, font: parseFloat(c.fontSize), radius: parseFloat(c.borderTopLeftRadius), focusChanged: before !== after && after !== 'none' }; });
    assert.ok(ib.h >= 34, `텍스트 박스 높이 ${ib.h}px`); assert.ok(ib.font >= 14, `텍스트 박스 글자 ${ib.font}px`); assert.ok(ib.radius >= 4, `텍스트 박스 모서리 ${ib.radius}px`); assert.ok(ib.focusChanged, '포커스 표시가 없다');
    const l = await r(page, 'label[for=n]'), i = await r(page, '#n'); assert.ok(i.right <= f.right + 0.5, `입력란 오른쪽(${i.right.toFixed(0)})이 폼 오른쪽(${f.right.toFixed(0)})보다 튀어나왔다`); assert.ok(l.bottom <= i.top + 2, `레이블(${l.bottom.toFixed(0)})이 입력란(${i.top.toFixed(0)}) 위에 있지 않다`);
    const c = await r(page, '#c'), t = await r(page, '.rule-option-title'); assert.ok(c.h >= 15 && c.h <= 18, `체크 상자 ${c.h}px`);
    // 단일 폼이라 카드 상자로 감싸지 않는다: 구역과 규칙 줄에 테두리 상자와 배경색이 없다(D-143, A-552)
    const box = await page.evaluate(() => ['#rulesetBasics', '#rulesetRules', '.rule-option'].map((s) => { const c = getComputedStyle(document.querySelector(s)); return { s, side: parseFloat(c.borderLeftWidth) + parseFloat(c.borderRightWidth), bg: c.backgroundColor, radius: parseFloat(c.borderTopLeftRadius) }; }));
    box.forEach((b) => assert.ok(b.side === 0 && b.bg === 'rgba(0, 0, 0, 0)' && b.radius === 0, `${b.s}가 카드 상자 모양이다: 좌우 테두리 ${b.side}, 배경 ${b.bg}, 모서리 ${b.radius}`));
    assert.ok(Math.abs((c.top + c.bottom) / 2 - (t.top + t.bottom) / 2) <= 6, `체크 상자 중심 ${((c.top + c.bottom) / 2).toFixed(0)}, 제목 중심 ${((t.top + t.bottom) / 2).toFixed(0)}`);
    await ctx.close();
  } },
  { name: '이슈 라벨 목록은 분류 소제목 줄이 배경으로 구분되고 라벨 칩은 둥글며 작업 단추는 오른쪽 끝이다(A-556)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div class="page-wrap-outer"><div class="project-page-wrap shell label-editor-wrap"><div class="shell-main" style="width:990px;flex:none"><div id="labelsList" class="issue-label-list-wrap">
<div class="category-wrap label-group" data-category-name="종류"><div class="label-group-head" id="gh"><span class="category-name">종류</span><span class="label-group-meta">라벨 2개</span><button class="ybtn ybtn-mini">분류 수정</button></div>
<table class="table nm"><tr data-label-id="1"><td><span class="issue-label active" id="chip" style="background-color:#d9534f">버그</span></td><td class="actions" id="act"><button class="ybtn ybtn-small" id="ab">수정</button></td></tr></table></div>
</div></div></div></div></body></html>`);
    const bg = await page.evaluate(() => getComputedStyle(document.getElementById('gh')).backgroundColor); assert.notEqual(bg, 'rgba(0, 0, 0, 0)', '소제목 줄에 배경이 없다');
    const chip = await page.evaluate(() => { const c = document.getElementById('chip'); const r = c.getBoundingClientRect(); return { h: r.height, w: r.width, radius: parseFloat(getComputedStyle(c).borderTopLeftRadius), display: getComputedStyle(c).display }; });
    assert.ok(chip.h <= 28, `라벨 칩 높이 ${chip.h}px`); assert.ok(chip.radius >= 8, `라벨 칩 모서리 ${chip.radius}px`); assert.ok(chip.w < 300, `라벨 칩이 줄 전체로 늘어났다: ${chip.w}px`);
    const tbl = await r(page, '#labelsList table'), ab = await r(page, '#ab'); assert.ok(tbl.right - ab.right <= 16, `작업 단추가 오른쪽 끝에서 ${(tbl.right - ab.right).toFixed(0)}px 떨어져 있다`);
    await ctx.close();
  } },
  { name: '새 라벨 폼의 색상 선택기는 "색 직접 선택" 글자가 붙은 상자 안에 있고 높이가 이름 입력란과 같으며 옛 안쪽 그림자가 없다(A-557, A-559)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div class="page-wrap-outer"><div class="project-page-wrap shell label-editor-wrap"><div class="shell-main" style="width:990px;flex:none"><form id="frmNewLabel" class="new-label-wrap"><input id="nm" type="text" name="name" class="input-label form-control"><div class="label-preset-colors"><label class="label-color-pick" id="pick"><span class="label-color-caption" id="cap">색 직접 선택</span><input id="col" type="color" name="color" value="#2196f3" class="input-label-color form-control form-control-color"></label></div></form></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const c = document.getElementById('col'), n = document.getElementById('nm'), k = document.getElementById('pick'); const ks = getComputedStyle(k); return { ch: c.getBoundingClientRect().height, cw: c.getBoundingClientRect().width, kh: k.getBoundingClientRect().height, nh: n.getBoundingClientRect().height, border: parseFloat(ks.borderTopWidth), shadow: getComputedStyle(c).boxShadow, capFont: parseFloat(getComputedStyle(document.getElementById('cap')).fontSize) }; });
    assert.ok(m.border >= 1, '선택기 상자에 테두리가 없어 선택기로 알아보기 어렵다'); assert.ok(Math.abs(m.kh - m.nh) <= 2, `상자 높이 ${m.kh}px, 이름 입력란 ${m.nh}px`); assert.ok(m.cw >= 20 && m.cw <= 40 && m.ch >= 20 && m.ch <= 40, `선택기 크기 ${m.cw}x${m.ch}px`);
    assert.equal(m.shadow, 'none', `옛 안쪽 그림자가 남았다: ${m.shadow}`); assert.ok(m.capFont >= 12, `안내 글자 ${m.capFont}px`);
    const cap = await r(page, '#cap'), col = await r(page, '#col'); assert.ok(cap.right <= col.left + 1 && Math.abs((cap.top + cap.bottom) / 2 - (col.top + col.bottom) / 2) <= 6, '글자와 선택기가 같은 줄에 있지 않다');
    await ctx.close();
  } },
  { name: '라벨 수정 모달의 입력란은 모달 폭에 맞고 견본이 모달 밖으로 넘치지 않으며 이름 옆에 색 칩이 있다(A-558)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    const sw = Array.from({ length: 17 }, (_, i) => `<button type="button" class="issue-label btn-preset-color" style="background-color:hsl(${i * 20},60%,50%)"></button>`).join('');
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div id="editLabel" class="modal yobiDialog show" style="display:block" tabindex="-1"><div class="modal-dialog"><div class="modal-content"><div class="message edit-label-form"><div class="center-txt">
<select class="form-select" name="category.id"><option>종류</option></select>
<div class="edit-label-name-row"><input id="nm" type="text" name="name" class="text input-label-name form-control" value="버그"><span class="edit-label-chip issue-label active" id="chip" style="background-color:#d73a4a">버그</span></div>
<div class="label-preset-colors edit" id="sw">${sw}<label class="label-color-pick"><span class="label-color-caption">색 직접 선택</span><input id="col" type="color" name="color" value="#d73a4a" class="input-label-color form-control form-control-color"></label></div>
</div></div></div></div></div></body></html>`);
    const box = await r(page, '#editLabel .modal-content'), nm = await r(page, '#nm'), sel = await r(page, '#editLabel select'), sw2 = await r(page, '#sw'), col = await r(page, '#col'), chip = await r(page, '#chip');
    assert.ok(nm.right <= box.right + 0.5 && nm.left >= box.left, `이름 입력란이 모달 밖이다: ${nm.left.toFixed(0)}~${nm.right.toFixed(0)} / 모달 ${box.left.toFixed(0)}~${box.right.toFixed(0)}`);
    assert.ok(sw2.right <= box.right + 0.5, `견본 줄이 모달 밖으로 나갔다: ${sw2.right.toFixed(0)} > ${box.right.toFixed(0)}`);
    assert.ok(Math.abs(nm.left - sel.left) <= 2, `이름 입력란 왼쪽(${nm.left.toFixed(0)})이 분류 선택 왼쪽(${sel.left.toFixed(0)})과 어긋난다`);
    const row = await r(page, '#editLabel .edit-label-name-row');
    assert.ok(Math.abs((row.right - row.left) - (sel.right - sel.left)) <= 2, `이름 줄 ${(row.right - row.left).toFixed(0)}px, 분류 선택 ${(sel.right - sel.left).toFixed(0)}px(폭이 서로 맞아야 한다)`);
    assert.ok(Math.abs(sw2.left - sel.left) <= 2, `견본 줄 왼쪽(${sw2.left.toFixed(0)})이 분류 선택 왼쪽(${sel.left.toFixed(0)})과 어긋난다`);
    assert.ok(col.right - col.left <= 64, `색상 선택기 너비 ${(col.right - col.left).toFixed(0)}px`); assert.ok(chip.h >= 14 && chip.h <= 30, `색 칩 높이 ${chip.h}px`);
    const bg = await page.evaluate(() => getComputedStyle(document.getElementById('nm')).backgroundColor); assert.ok(bg === 'rgb(255, 255, 255)' || bg === 'rgba(0, 0, 0, 0)', `이름 입력란이 칠해졌다: ${bg}`);
    await ctx.close();
  } },
  { name: '프로젝트 설정 폼은 왼쪽 제목 칸과 오른쪽 내용 칸이고 가로 선택, 4열 체크 목록, 병합 표가 한 줄에 놓이며 입력란은 읽기 편하다(A-563)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    const feat = ['코드', '이슈', '코드 주고받기', '리뷰', '마일스톤', '게시판', '위키'].map((n, i) => `<label class="setting-check"><input id="f${i}" type="checkbox" class="form-check-input"> ${n}</label>`).join('');
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div class="page-wrap-outer"><div class="project-page-wrap shell"><div class="shell-main" style="width:990px;flex:none"><form id="saveSetting" class="setting-form">
<section class="setting-section" id="settingAccess"><div class="setting-section-head"><h4>공개와 접근</h4></div><div class="setting-section-body">
 <div class="setting-field"><label class="setting-label">공개 설정</label><div class="setting-seg" id="seg"><label class="on"><input type="radio" name="p" checked>공개</label><label><input type="radio" name="p">비공개</label></div></div>
 <div class="setting-field"><label class="setting-label" for="nm">프로젝트 이름</label><input id="nm" type="text" class="form-control w-100"></div></div></section>
<section class="setting-section" id="settingFeatures"><div class="setting-section-head"><h4>기능</h4></div><div class="setting-section-body"><div class="setting-features" id="grid">${feat}</div></div></section>
<section class="setting-section" id="settingCode"><div class="setting-section-head"><h4>코드와 병합</h4></div><div class="setting-section-body"><table class="setting-merge" id="mt"><thead><tr><th>방식</th><th>허용</th><th>기본</th></tr></thead><tbody><tr><td>병합 커밋</td><td><input type="checkbox"></td><td><input type="radio"></td></tr></tbody></table></div></section>
</form></div></div></div></body></html>`);
    const head = await r(page, '#settingAccess .setting-section-head'), body = await r(page, '#settingAccess .setting-section-body'), form = await r(page, '#saveSetting');
    assert.ok(head.right <= body.left + 1, `제목 칸(${head.right.toFixed(0)})이 내용 칸(${body.left.toFixed(0)})보다 왼쪽이어야 한다`); assert.ok(head.right - head.left <= 200, `제목 칸 너비 ${(head.right - head.left).toFixed(0)}px`); assert.ok(form.right - form.left <= 840, `폼 너비 ${(form.right - form.left).toFixed(0)}px`);
    const tops = await page.evaluate(() => [...document.querySelectorAll('#grid .setting-check')].map((e) => Math.round(e.getBoundingClientRect().top)));
    assert.equal(tops.filter((t) => t === tops[0]).length, 4, `기능 체크 목록의 첫 줄에 ${tops.filter((t) => t === tops[0]).length}개가 있다(4개여야 한다): ${tops}`);
    const seg = await page.evaluate(() => { const l = [...document.querySelectorAll('#seg label')].map((e) => e.getBoundingClientRect()); return { sameRow: Math.abs(l[0].top - l[1].top) < 2, adjacent: Math.abs(l[0].right - l[1].left) <= 2, border: parseFloat(getComputedStyle(document.getElementById('seg')).borderTopWidth), onBg: getComputedStyle(document.querySelector('#seg label.on')).backgroundColor }; });
    assert.ok(seg.sameRow && seg.adjacent, '가로 선택의 항목이 한 줄에 붙어 있지 않다'); assert.ok(seg.border >= 1, '가로 선택에 테두리가 없다'); assert.notEqual(seg.onBg, 'rgba(0, 0, 0, 0)', '고른 항목이 구분되지 않는다');
    const ib = await page.evaluate(() => { const e = document.getElementById('nm'), c = getComputedStyle(e); return { h: e.getBoundingClientRect().height, font: parseFloat(c.fontSize), radius: parseFloat(c.borderTopLeftRadius), pad: c.padding, lh: c.lineHeight, bs: c.boxSizing }; });
    assert.ok(ib.h >= 34 && ib.font >= 14 && ib.radius >= 4, `입력란 ${ib.h}px, 글자 ${ib.font}px, 모서리 ${ib.radius}px`);
    const th = await page.evaluate(() => [...document.querySelectorAll('#mt thead th')].map((e) => Math.round(e.getBoundingClientRect().top)));
    assert.equal(new Set(th).size, 1, `병합 표 머리 칸이 한 줄에 있지 않다: ${th}`);
    await ctx.close();
  } },
  { name: '프로젝트 만들기와 가져오기 폼의 글 입력란은 w-100이면 폼 폭을 채우고, 대시보드의 0개 라벨 개수는 흐리다(A-566)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div class="page-wrap-outer"><div style="width:700px"><form id="newProjectForm"><input id="a" type="text" class="text form-control w-100" placeholder="영문, 한글, 숫자 및 일부 기호(_-.)만 사용할 수 있습니다"><input id="b" type="text" class="text form-control" placeholder="x"></form>
<div class="project-overview-home"><div class="overview-label"><div class="num is-zero" id="z"><strong>0</strong></div><div class="num" id="n"><strong>3</strong></div></div></div></div></div></body></html>`);
    const w = await page.evaluate(() => ({ a: document.getElementById('a').getBoundingClientRect().width, b: document.getElementById('b').getBoundingClientRect().width, h: document.getElementById('a').getBoundingClientRect().height, fs: parseFloat(getComputedStyle(document.getElementById('a')).fontSize) }));
    assert.ok(w.a >= 600, `w-100 입력란 폭 ${w.a}px (폼 폭 700px를 채워야 한다)`); assert.ok(w.b < 300, `폭 클래스가 없는 입력란은 v1.6처럼 좁다(${w.b}px)`); assert.ok(w.h >= 36, `입력란 높이 ${w.h}px`);
    const c = await page.evaluate(() => ({ z: getComputedStyle(document.querySelector('#z strong')).color, n: getComputedStyle(document.querySelector('#n strong')).color }));
    assert.notEqual(c.z, c.n, `0개 개수(${c.z})가 다른 개수(${c.n})와 구분되지 않는다`);
    await ctx.close();
  } },
  { name: '이슈 옵션 단추는 처음부터 활성처럼 테마색이고, 라벨 입력 칸은 모노스페이스가 아닌 본문 글꼴이다(A-567)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div class="page-wrap-outer"><div class="subtask-message" id="o">이슈 옵션</div><div class="select2 select2-container select2-container--bootstrap-5 issue-labels bordered"><span class="select2-selection select2-selection--multiple"><ul class="select2-selection__rendered"><li class="select2-search select2-search--inline"><textarea class="select2-search__field" placeholder="라벨 선택"></textarea></li></ul></span></div></div></body></html>`);
    const c = await page.evaluate(() => { const o = getComputedStyle(document.getElementById('o')), f = getComputedStyle(document.querySelector('.select2-search__field')); return { bc: o.borderTopColor, col: o.color, ff: f.fontFamily }; });
    const rgb = (s) => s.match(/\d+/g).map(Number);
    const [r, g] = rgb(c.col); assert.ok(r > 200 && g < 170, `이슈 옵션 글자색 ${c.col}이 테마색(주황)이 아니다`);
    assert.equal(c.bc, c.col, `테두리(${c.bc})와 글자(${c.col})가 같은 색이어야 활성처럼 보인다`);
    assert.ok(!/monospace|consolas|menlo/i.test(c.ff), `라벨 입력 칸 글꼴 ${c.ff}`);
    await ctx.close();
  } },
  { name: '하위 이슈 블록은 부모 줄이 한 줄이고 진행 막대가 충분히 길며, 하위 이슈가 줄마다 한 줄로 정돈된다(A-567)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    const child = (n, t, cls, sel) => `<div class="issue-item child-issue${sel ? ' selected-child' : ''}"><span class="state-label ${cls}"></span><a class="twoColumeModeTarget"><span class="item-name"><span class="subtask-number">#${n}</span><span>${t}</span><span>- user</span></span></a><span class="font12 no-border-at-child"></span><a class="badge issue-label list-label active bg-secondary" style="background:#d33">버그</a><span class="child-issue-date">13초 전</span></div>`;
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="shell"><div class="shell-main" style="width:760px"><div class="subtasks">
<div class="child-issues"><div class="issue-item parent-issue"><a id="pt">#2 [긴급] 로그인 오류에 대한 아주 긴 제목이 들어가도 한 줄을 유지해야 한다</a><div class="upload-progress red-outline" id="pg"><div class="bar red" style="width: 50%;"></div></div><span id="cnt">1/2 </span><span class="parent-issue-state open" id="st">열림</span></div>
<hr class="parent-issue-delimeter"/><div class="child-issues" id="list">${child(10, '하위 이슈 확인용', 'open', true)}${child(11, '두 번째 하위 이슈의 제목도 길어질 수 있다', 'closed', false)}</div></div></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); const pt = r('#pt'), pg = r('#pg'), cnt = r('#cnt'), st = r('#st'); const rows = [...document.querySelectorAll('#list .child-issue')].map((e) => { const x = e.getBoundingClientRect(); const kids = [...e.children].map((k) => k.getBoundingClientRect()); return { h: x.height, tops: kids.map((k) => Math.round(k.top + k.height / 2)) }; }); const hr = getComputedStyle(document.querySelector('hr.parent-issue-delimeter')).display; const outer = getComputedStyle(document.querySelector('.subtasks > .child-issues')); const sel = getComputedStyle(document.querySelector('.selected-child')).backgroundColor; return { pt, pg, cnt, st, rows, hr, outerBorder: outer.borderTopWidth, outerRadius: outer.borderTopLeftRadius, sel }; });
    const mid = (x) => x.top + x.height / 2;
    assert.ok(m.pg.width >= 100, `진행 막대 폭 ${m.pg.width}px (100px 이상이어야 한다)`); assert.ok(m.pg.height >= 6 && m.pg.height <= 12, `진행 막대 높이 ${m.pg.height}px`);
    assert.ok(Math.max(mid(m.pt), mid(m.pg), mid(m.cnt), mid(m.st)) - Math.min(mid(m.pt), mid(m.pg), mid(m.cnt), mid(m.st)) <= 4, '부모 줄의 제목, 막대, 개수, 상태가 같은 높이에 한 줄로 놓여야 한다');
    assert.ok(m.pt.right <= m.pg.left, `긴 제목(${m.pt.right.toFixed(0)})이 진행 막대(${m.pg.left.toFixed(0)})를 밀어내지 않아야 한다`);
    assert.equal(m.hr, 'none', '점선 구분선은 없앴다'); assert.ok(parseFloat(m.outerBorder) >= 1 && parseFloat(m.outerRadius) >= 6, `바깥 상자 테두리 ${m.outerBorder}, 모서리 ${m.outerRadius}`);
    m.rows.forEach((row, i) => { assert.ok(row.h <= 44, `하위 이슈 ${i} 줄 높이 ${row.h}px (한 줄이어야 한다)`); assert.ok(Math.max(...row.tops) - Math.min(...row.tops) <= 6, `하위 이슈 ${i}의 요소가 같은 줄에 있지 않다: ${row.tops}`); });
    assert.notEqual(m.sel, 'rgba(0, 0, 0, 0)', '선택된 하위 이슈가 구분되지 않는다');
    await ctx.close();
  } },
];
