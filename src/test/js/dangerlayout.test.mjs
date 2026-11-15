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
  { name: '코드 주고받기 목록은 탭 줄 전체에 밑줄이 이어지고 새 코드 보내기 단추는 그 줄 위에 여백을 두고 오른쪽에 놓이며, 제목 입력란은 w-100이면 폼 폭을 채운다(A-568)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body>
<div class="page-wrap-outer"><div class="project-page-wrap shell"><div class="shell-main"><div class="row cb"><div class="col-lg-10 span-hard-wrap" style="width:800px">
<div class="pr-tab-row mb-3" id="row"><ul class="nav nav-tabs nm pullrequeset-tab-menu mb-0" id="tabs"><li class="nav-item"><a class="nav-link active" id="act">열림 1</a></li><li class="nav-item"><a class="nav-link" id="last">닫힘 0</a></li><li><div class="two-column-icon mr10" id="tc"><div class="form-check"><label class="form-check-label"><div class="two-column-icon-border"><input type="checkbox" class="form-check-input"><span class="two-column-mode-text">2단 보기</span></div></label></div></div></li></ul><div class="pr-new-wrap"><a class="ybtn ybtn-success" id="nb">새 코드 보내기</a></div></div></div></div>
<div style="width:700px"><input id="t" type="text" class="text form-control w-100 pr-title" placeholder="제목"><input id="t2" type="text" class="text form-control" placeholder="x"></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); const cs = (s) => getComputedStyle(document.querySelector(s)); return { row: r('#row'), tc: r('#tc'), last: r('#last'), nb: r('#nb'), tabs: r('#tabs'), act: r('#act'), t: r('#t'), t2: r('#t2'), rowBorder: parseFloat(cs('#row').borderBottomWidth), ulBorder: parseFloat(cs('#tabs').borderBottomWidth), actBg: cs('#act').backgroundColor }; });
    assert.ok(m.rowBorder >= 1, `탭 줄 전체의 밑줄(${m.rowBorder}px)이 있어야 단추 아래까지 이어진다`); assert.equal(m.ulBorder, 0, `탭 목록 자체의 밑줄(${m.ulBorder}px)은 없어야 줄이 두 겹이 되지 않는다`);
    assert.ok(m.row.bottom - m.nb.bottom >= 3, `단추 아래와 밑줄 사이 여백 ${(m.row.bottom - m.nb.bottom).toFixed(1)}px (3px 이상)`); assert.ok(m.nb.top >= m.row.top - 1, '단추가 줄 위로 삐져나오면 안 된다');
    assert.ok(Math.abs(m.row.right - m.nb.right) <= 1, `단추 오른쪽(${m.nb.right})이 줄 오른쪽(${m.row.right})에 닿아야 한다`); assert.ok(m.nb.left > m.tabs.left + 150, '단추는 탭 오른쪽에 있다');
    assert.ok(m.tc.left - m.last.right >= 12, `2단 보기 상자와 마지막 탭 사이 ${(m.tc.left - m.last.right).toFixed(1)}px (12px 이상 떨어져야 한다)`); assert.ok(m.row.bottom - m.tc.bottom >= 3, `2단 보기 상자 아래와 밑줄 사이 ${(m.row.bottom - m.tc.bottom).toFixed(1)}px (단추처럼 3px 이상 띄운다)`);
    assert.ok(m.nb.height >= 28 && m.nb.height <= 34, `단추 높이 ${m.nb.height}px`);
    assert.ok(m.t.width >= 600, `제목 입력란 폭 ${m.t.width}px`); assert.ok(m.t2.width < 300, `폭 클래스가 없는 입력란은 v1.6처럼 좁다(${m.t2.width}px)`); assert.ok(m.t.height >= 34, `제목 입력란 높이 ${m.t.height}px`);
    await ctx.close();
  } },
  { name: '비교 화면은 diff 표가 본문 왼쪽 끝에서 시작하고 비교 범위는 이름과 번호가 한 줄에 놓인다(A-570)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="project-page-wrap shell"><div class="shell-main"><div class="code-browse-wrap" id="w">
<p class="commitInfo compare-range" id="cr"><span class="compare-from"><strong class="compare-name">v0.1.0</strong> <code class="compare-id">6122e43</code></span><span class="compare-arrow">→</span><span class="compare-to"><strong class="compare-name">feature/greeting</strong> <code class="compare-id">a2d700e</code></span></p>
<div class="diff-body discommentable" id="db"><div class="diff-partial-outer" id="po"><div class="diff-partial-inner">x</div></div></div></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); const cr = [...document.querySelectorAll('#cr > *')].map((e) => Math.round(e.getBoundingClientRect().top + e.getBoundingClientRect().height / 2)); return { w: r('#w'), po: r('#po'), cr }; });
    assert.ok(Math.abs(m.po.left - m.w.left) <= 1, `diff 표 왼쪽(${m.po.left})이 본문 왼쪽(${m.w.left})과 같아야 한다`);
    assert.ok(Math.max(...m.cr) - Math.min(...m.cr) <= 6, `비교 범위의 요소가 한 줄에 있지 않다: ${m.cr}`);
    await ctx.close();
  } },
  { name: '브랜치의 기본 표시는 단추가 아닌 칩이고, 태그 행은 높이가 같으며, 빈 저장소 안내는 본문 왼쪽 끝에 맞고 안내 문장이 굵지 않다(A-570)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="project-page-wrap shell"><div class="shell-main" style="width:900px">
<div class="bubble-wrap dark-gray repo-wrap"><div class="code-browse-wrap"><table class="table branch-list-wrap"><tbody><tr class="head"><td class="branchName"><a>master</a><span class="headBranch ml10" id="hb">기본 브랜치</span></td></tr></tbody></table>
<table class="table tag-list-wrap"><tbody><tr id="t1"><td class="tagName"><div class="tag-title"><a class="tag-name">v0.1.0</a> <strong class="branch-label tag tag-type annotated ml10">주석 태그</strong></div><div class="tag-meta disabled"><span class="tag-message">첫 릴리스</span> <span class="tagger">Ji-ho Lee, 1일 전</span></div></td><td class="commit"><a class="commitId">6122e43</a></td><td class="actions"><a class="ybtn ybtn-danger ybtn-small">삭제</a></td></tr>
<tr id="t2"><td class="tagName"><div class="tag-title"><a class="tag-name">light</a> <strong class="branch-label tag tag-type lightweight ml10">경량 태그</strong></div></td><td class="commit"><a class="commitId">a2d700e</a></td><td class="actions"><a class="ybtn ybtn-danger ybtn-small">삭제</a></td></tr></tbody></table></div></div>
<div class="row nohead-wrap" id="nh"><div class="col-lg-12"><div class="alert alert-block"><h4 id="nt">저장소가 비어있습니다!</h4></div><h5 id="n5">Yona에 생성된 Git 저장소를 clone 받아서 로컬에 올릴 수 있습니다.</h5><pre><code>git clone x</code></pre></div></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const g = (s) => getComputedStyle(document.querySelector(s)); const r = (s) => document.querySelector(s).getBoundingClientRect(); return { hbBorder: parseFloat(g('#hb').borderTopWidth), hbRadius: parseFloat(g('#hb').borderTopLeftRadius), hbBg: g('#hb').backgroundColor, t1: r('#t1').height, t2: r('#t2').height, nhLeft: r('#nh').left, ntLeft: r('#nt').left, n5w: parseInt(g('#n5').fontWeight, 10) }; });
    assert.equal(m.hbBorder, 0, `기본 브랜치 표시에 테두리(${m.hbBorder}px)가 있으면 단추처럼 보인다`); assert.ok(m.hbRadius >= 8, `칩 모서리 ${m.hbRadius}px`); assert.notEqual(m.hbBg, 'rgba(0, 0, 0, 0)', '칩에 바탕색이 있어야 한다');
    assert.ok(Math.abs(m.t1 - m.t2) <= 2, `태그 행 높이가 다르다: ${m.t1}px, ${m.t2}px`);
    assert.ok(Math.abs(m.ntLeft - m.nhLeft - 12) <= 14 && m.ntLeft - m.nhLeft <= 14, `제목 왼쪽 ${m.ntLeft}이 본문 왼쪽 ${m.nhLeft}에서 ${(m.ntLeft - m.nhLeft).toFixed(0)}px 들어갔다(본문 안쪽 여백만큼만 허용)`); assert.ok(m.n5w <= 500, `안내 문장 글자 굵기 ${m.n5w}`);
    await ctx.close();
  } },
  { name: '변경 파일 트리는 diff 왼쪽 240px 칸이고 diff가 나머지 폭을 채우며, 고른 파일은 구분되고 숨긴 diff는 사라진다(A-571)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="project-page-wrap shell"><div class="shell-main" style="width:990px"><div class="code-browse-wrap">
<div class="diff-tree-layout" id="lay"><aside class="diff-tree" id="tr"><div class="diff-tree-head"><strong class="diff-tree-title">변경된 파일 2개</strong><button type="button" class="diff-tree-all active">전체 보기</button></div>
<ul class="diff-tree-list"><li class="folder active" id="fo"><span class="name" id="fn">src</span><ul class="diff-tree-list"><li class="file active" id="af" data-index="0"><a href="#">Main.kt</a></li></ul></li><li class="file" data-index="1"><a href="#">README.md</a></li></ul></aside>
<div class="diff-body discommentable" id="db"><div class="diff-partial-outer" id="b0">a</div><div class="diff-partial-outer diff-tree-hidden" id="b1" style="display:none">b</div></div></div></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); const cs = (s) => getComputedStyle(document.querySelector(s)); return { tr: r('#tr'), db: r('#db'), lay: r('#lay'), actBg: cs('#af').backgroundColor, hidden: cs('#b1').display, pos: cs('#tr').position, folderCursor: cs('#fn').cursor, folderBg: cs('#fn').backgroundColor }; });
    assert.ok(m.tr.right <= m.db.left + 1, `트리(${m.tr.right})가 diff(${m.db.left}) 왼쪽에 있어야 한다`); assert.ok(m.tr.width >= 200 && m.tr.width <= 260, `트리 폭 ${m.tr.width}px`);
    assert.ok(m.db.right >= m.lay.right - 2, `diff가 나머지 폭을 채워야 한다(${m.db.right} / ${m.lay.right})`); assert.notEqual(m.actBg, 'rgba(0, 0, 0, 0)', '고른 파일이 구분되지 않는다'); assert.equal(m.hidden, 'none'); assert.equal(m.pos, 'sticky', '긴 diff를 읽는 동안 트리가 따라와야 한다');
    assert.equal(m.folderCursor, 'pointer', '폴더도 누를 수 있어 보여야 한다'); assert.notEqual(m.folderBg, 'rgba(0, 0, 0, 0)', '고른 폴더가 구분되지 않는다');
    await ctx.close();
  } },
  { name: '글 수정의 체크박스 세 개(공지, README, 수정 알림)는 한 줄에 모여 오른쪽 끝에 놓인다(A-573)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="project-page-wrap shell"><div class="shell-main" style="width:990px"><form class="nm"><div class="content-wrap frm-wrap">
<div class="right-txt mt10 mb10" id="g"><div class="form-check" id="c1"><label class="form-check-label"><input type="checkbox" class="form-check-input"> 이 글을 공지사항으로 설정합니다</label></div><div class="form-check" id="c2"><label class="form-check-label"><input type="checkbox" class="form-check-input"> 이 글을 프로젝트 README 파일로 만듭니다</label></div><span class="send-notification-check"><div class="form-check" id="c3"><label class="form-check-label"><input type="checkbox" class="form-check-input"> <strong>수정 알림 메일 발송</strong></label></div></span></div>
<div class="actions"><button class="ybtn ybtn-success" id="sv">저장</button><a class="ybtn">취소</a></div></div></form></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); return { g: r('#g'), c1: r('#c1'), c2: r('#c2'), c3: r('#c3') }; });
    const mid = (x) => x.top + x.height / 2;
    assert.ok(Math.max(mid(m.c1), mid(m.c2), mid(m.c3)) - Math.min(mid(m.c1), mid(m.c2), mid(m.c3)) <= 4, `체크박스 셋이 같은 줄에 있지 않다: ${[m.c1, m.c2, m.c3].map((x) => Math.round(mid(x)))}`);
    assert.ok(m.g.right - m.c3.right <= 20, `묶음 오른쪽 끝(${m.g.right})에 마지막 체크박스(${m.c3.right})가 붙어야 한다`);
    await ctx.close();
  } },
  { name: '위키 이력의 제목은 다른 화면의 제목과 같은 크기이고, 위키 diff는 코드 diff와 같은 색이며, 빈 위키는 가운데 정렬 빈 화면 틀에 첫 페이지 단추가 아래로 떨어진다(A-576)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="project-page-wrap shell wiki-wrap"><div class="shell-main"><div class="row"><div class="col-lg-9">
<div class="wiki-page-header wiki-history-header d-flex align-items-center gap-2 mb-3"><h2 class="me-auto mb-0" id="h"><a class="wiki-page-title" id="hl">Home</a> <small id="hs">리비전</small></h2></div>
<div class="wiki-page-header d-flex mb-3"><h2 class="wiki-page-title" id="vt">Home</h2></div>
<div class="wiki-diff" style="width:700px"><div class="diff-line diff-hunk" id="dh">@@ -1,3 +1,4 @@</div><div class="diff-line diff-add" id="da">+추가</div><div class="diff-line diff-del" id="dd">-삭제</div></div>
<div class="wiki-empty text-center py-5" id="we"><div class="error-wrap" id="ew"><i class="ico ico-err1"></i><p id="ep">이 위키에는 아직 페이지가 없습니다.</p></div><a class="wiki-create-first ybtn ybtn-success" id="wb">첫 페이지 만들기</a></div></div></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const g = (s) => getComputedStyle(document.querySelector(s)); const r = (s) => document.querySelector(s).getBoundingClientRect(); return { hl: parseFloat(g('#hl').fontSize), h: parseFloat(g('#h').fontSize), hs: parseFloat(g('#hs').fontSize), vt: parseFloat(g('#vt').fontSize), da: g('#da').backgroundColor, dd: g('#dd').backgroundColor, dh: g('#dh').backgroundColor, ew: r('#ew'), we: r('#we'), ep: r('#ep'), wb: r('#wb'), ta: g('#ew').textAlign }; });
    assert.ok(m.hl >= 17 && m.hl <= 19 && m.h >= 17 && m.h <= 19, `이력 제목 ${m.h}px / ${m.hl}px (18px 안팎)`); assert.ok(m.hs < m.h, `부제 ${m.hs}px`); assert.equal(m.vt, 28, `위키 본문 제목은 그대로(${m.vt}px)`);
    assert.equal(m.da, 'rgb(238, 255, 204)', `추가 줄 색 ${m.da}`); assert.equal(m.dd, 'rgb(255, 221, 221)', `삭제 줄 색 ${m.dd}`); assert.equal(m.dh, 'rgb(248, 248, 255)', `범위 줄 색 ${m.dh}`);
    const center = (x) => x.left + x.width / 2; assert.ok(Math.abs(center(m.ep) - center(m.we)) <= 4, `안내 문구 가운데(${center(m.ep)})가 영역 가운데(${center(m.we)})와 같아야 한다`);
    assert.ok(m.wb.top - m.ep.bottom >= 12 && m.wb.top - m.ep.bottom <= 40, `첫 페이지 단추가 안내 문구에서 ${(m.wb.top - m.ep.bottom).toFixed(0)}px 떨어짐(12~40px, 살짝만)`);
    await ctx.close();
  } },
  { name: '위키 편집의 파일 올리기 상자는 게시글 등록의 파일 올리기 상자와 같은 모양이다(A-576)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    const box = (id) => `<div class="upload-wrap content-footer" id="${id}"><div class="attach-wrap"><div class="btn-wrap"><div class="nbtn medium white fake-file-wrap"><i class="yobicon-upload"></i> 파일 올리기<input type="file" class="file form-control"></div></div><span class="plain">버튼을 클릭해서 선택하세요</span></div></div>`;
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="project-page-wrap shell wiki-wrap"><div class="shell-main" style="width:760px"><div class="board">${box('bd')}</div><div class="wiki-attach mb-3">${box('wk')}</div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const g = (s) => { const e = document.querySelector(s + ' .attach-wrap'); const c = getComputedStyle(e); const r = e.getBoundingClientRect(); return { bg: c.backgroundColor, rad: c.borderTopLeftRadius, h: Math.round(r.height), w: Math.round(r.width), ta: c.textAlign, pad: c.padding }; }; return { bd: g('#bd'), wk: g('#wk') }; });
    assert.deepEqual(m.wk, m.bd, `위키 파일 올리기 상자(${JSON.stringify(m.wk)})가 게시글(${JSON.stringify(m.bd)})과 같아야 한다`);
    await ctx.close();
  } },
  { name: '새 그룹 만들기는 입력란이 폭을 채우고 설명란이 크며, 조직 게시판의 프로젝트 선택과 키워드 검색은 높이가 같고, 조직 감사의 적용 단추는 테마색이다(A-577)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="project-page-wrap"><div class="form-wrap new-project org-create"><form class="frm-wrap"><dl><dt><label>그룹 이름</label></dt><dd><input id="n" class="text form-control w-100"></dd><dt><label>설명</label></dt><dd><textarea id="d" rows="5" class="text form-control w-100"></textarea></dd></dl><div class="actions" id="ac"><button class="ybtn ybtn-success" id="cb">만들기</button></div></form></div></div>
<div class="project-page-wrap shell"><div class="shell-main" style="width:900px"><div class="search-wrap org-list-filter underline"><form class="float-start row align-items-start"><div class="project-selects col-lg-7"><span class="select2 select2-container select2-container--bootstrap-5" id="s2" style="width:100%"><span class="selection"><span class="select2-selection select2-selection--multiple"><ul class="select2-selection__rendered"><li class="select2-search select2-search--inline"><textarea class="select2-search__field" rows="1" style="height:20px;width:100%" placeholder="프로젝트 선택"></textarea></li></ul></span></span></span></div><div class="search-bar col-lg-4" id="sb"><input class="textbox group-board" type="text" placeholder="키워드로 검색"><button type="submit" class="search-btn" id="sbtn"><i class="yobicon-search"></i></button></div></form></div></div></div>
<div class="audit-screen"><button class="btn btn-sm btn-primary" id="ap">적용</button></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); return { n: r('#n'), d: r('#d'), form: r('.org-create .frm-wrap'), s2: r('#s2 .select2-selection'), sb: r('#sb'), sbtn: r('#sbtn'), ap: getComputedStyle(document.getElementById('ap')).backgroundColor }; });
    assert.ok(m.n.width >= 600, `그룹 이름 입력란 폭 ${m.n.width}px`); assert.ok(m.d.height >= 110, `설명란 높이 ${m.d.height}px`); assert.ok(m.d.width >= 600, `설명란 폭 ${m.d.width}px`);
    assert.equal(m.sb.height, 38, `키워드 검색 상자는 프로젝트 선택 상자와 같은 38px이어야 한다(${m.sb.height}px, 실제 선택 상자 높이는 실제 화면에서 확인)`);
    assert.ok(Math.abs((m.sbtn.top + m.sbtn.height / 2) - (m.sb.top + m.sb.height / 2)) <= 1.5, `돋보기 단추 세로 중심(${m.sbtn.top + m.sbtn.height / 2})이 검색 상자 중심(${m.sb.top + m.sb.height / 2})과 맞아야 한다`);
    const [r, g] = m.ap.match(/\d+/g).map(Number); assert.ok(r > 200 && g < 160, `감사 적용 단추 색 ${m.ap}이 테마 주황이 아니다`);
    await ctx.close();
  } },
  { name: '조직 OAuth: 앱 카드, 범위 제목 정렬, 비밀 값 화면의 상자 폭과 단추가 맞는다(A-579)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="project-page-wrap shell"><div class="shell-main" style="width:900px"><div class="two-factor-page oauth-screen">
<p id="desc" style="margin:0">설명</p><ul id="my-apps" class="item-cards list-unstyled"><li class="item-card app-row" id="c1"><div class="item-card-head"><a class="item-card-name">촬영용 앱</a><span class="item-card-type" id="ty">공개</span></div><dl class="item-card-meta"><dt>클라이언트 ID</dt><dd><code>yona_app_x</code></dd></dl><div class="item-card-actions"><form><button class="btn btn-danger btn-sm" id="del">삭제</button></form></div></li><li class="item-card app-row" id="c2"><div class="item-card-head"><a class="item-card-name">둘째</a></div><dl class="item-card-meta"><dt>클라이언트 ID</dt><dd><code>y</code></dd></dl><div class="item-card-actions"><form><button class="btn btn-danger btn-sm">삭제</button></form></div></li></ul>
<div class="tf-actions"><a class="btn btn-success" id="nw">새 앱</a></div>
<div class="mb-4"><span class="form-label d-block tf-scopes-title" id="st">이 앱이 요청할 수 있는 범위</span><div class="form-text mb-2" id="sh">앱은 여기서 고른 범위만</div></div>
<div id="secret"><div class="alert alert-success" id="al">앱을 만들었습니다.</div><h4>검증용 앱</h4><p class="mb-1">클라이언트 ID</p><p><code id="client-id">yona_app_5ohLmfDZSgo059k9jyca</code></p><p class="mb-1">클라이언트 비밀 값</p><p><code id="client-secret" class="tf-secret-value">yona_cs_OemCAvN413uj8JoFkeRR1fnsK2GxCazFFo7UNZfZ</code></p><div class="tf-actions"><a class="btn btn-success" id="done">비밀 값을 저장했습니다</a></div></div>
</div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const g = (s) => getComputedStyle(document.querySelector(s)); const r = (s) => document.querySelector(s).getBoundingClientRect(); return { desc: r('#desc'), c1: r('#c1'), c2: r('#c2'), del: r('#del'), cb: g('#c1').borderTopWidth, cr: g('#c1').borderTopLeftRadius, nw: g('#nw').backgroundColor, done: g('#done').backgroundColor, stL: r('#st').left, shL: r('#sh').left, id: r('#client-id'), sec: r('#client-secret'), al: r('#al'), main: r('.oauth-screen') }; });
    assert.ok(parseFloat(m.cb) >= 1 && parseFloat(m.cr) >= 6, `앱 카드 테두리 ${m.cb}, 모서리 ${m.cr}`);
    assert.ok(Math.abs(m.c1.width - 400) <= 2 && m.c1.height >= 160 && m.c1.height <= 240, `앱 카드는 대략 400x200이어야 한다(${m.c1.width}x${m.c1.height})`);
    assert.ok(m.c1.top - m.desc.bottom >= 12, `설명과 첫 카드 사이 간격이 12px 이상이어야 한다(${m.c1.top - m.desc.bottom}px)`);
    assert.ok(m.c2.top === m.c1.top && m.c2.left > m.c1.left, `카드는 가로로 나란히 놓인다(첫째 ${m.c1.left},${m.c1.top} 둘째 ${m.c2.left},${m.c2.top})`);
    assert.ok(m.del.bottom <= m.c1.bottom && m.c1.bottom - m.del.bottom <= 24, `삭제 단추는 카드 아래쪽에 있다(${m.del.bottom} / ${m.c1.bottom})`);
    for (const [k, v] of [['새 앱', m.nw], ['비밀 값 확인', m.done]]) { const [r, g] = v.match(/\d+/g).map(Number); assert.ok(r > 200 && g < 160, `${k} 단추 색 ${v}이 테마 주황이 아니다`); }
    assert.ok(Math.abs(m.stL - m.shL) <= 1, `범위 제목 왼쪽(${m.stL})이 설명 왼쪽(${m.shL})과 같아야 한다`);
    assert.ok(Math.abs(m.id.width - m.sec.width) <= 2 && m.id.width >= 400, `클라이언트 ID 상자(${m.id.width}px)와 비밀 값 상자(${m.sec.width}px) 폭이 같아야 한다`);
    assert.ok(Math.abs(m.al.width - m.sec.width) <= 2, `안내 상자(${m.al.width}px)도 같은 폭이어야 한다(${m.sec.width}px)`);
    await ctx.close();
  } },
  { name: '기기 인증 코드 입력과 OAuth 승인은 화면 가운데의 큰 카드이다(A-584)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="page-wrap"><form id="f"><div class="device-card" id="dc"><h3 class="device-card-title">기기 인증</h3><p class="device-card-help">터미널에 표시된 코드를 입력하십시오.</p><input id="code" class="text form-control w-100 device-code-input" maxlength="9"><button class="ybtn ybtn-success device-card-submit" id="go">계속</button></div></form><div class="consent-card" id="cc"><h1 class="consent-card-title">앱 승인</h1><ul id="sc" class="list-unstyled"><li>read:user</li></ul><div class="consent-actions"><button class="ybtn ybtn-primary" id="allow">허용</button><button class="ybtn" id="deny">거부</button></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); return { dc: r('#dc'), code: r('#code'), fs: parseFloat(getComputedStyle(document.getElementById('code')).fontSize), ta: getComputedStyle(document.getElementById('code')).textAlign, cc: r('#cc'), allow: r('#allow'), go: r('#go'), sc: r('#sc'), vw: innerWidth }; });
    assert.ok(Math.abs((m.dc.left + m.dc.width / 2) - m.vw / 2) <= 2, `기기 인증 카드는 가운데에 있어야 한다(중심 ${m.dc.left + m.dc.width / 2})`);
    assert.ok(m.dc.width >= 440 && m.dc.width <= 640, `기기 인증 카드 폭 ${m.dc.width}px`); assert.ok(m.fs >= 28 && m.ta === 'center', `코드 입력은 큰 글자(${m.fs}px)와 가운데 정렬(${m.ta})이어야 한다`); assert.ok(m.code.width >= 360, `코드 입력란 폭 ${m.code.width}px`);
    assert.ok(Math.abs((m.cc.left + m.cc.width / 2) - m.vw / 2) <= 2, `승인 카드는 가운데에 있어야 한다`); assert.ok(m.cc.width >= 440 && m.cc.width <= 640, `승인 카드 폭 ${m.cc.width}px`);
    assert.ok(Math.abs(m.go.left - m.code.left) <= 1 && Math.abs(m.go.width - m.code.width) <= 1, `계속 단추(${m.go.left}, ${m.go.width})가 코드 입력란(${m.code.left}, ${m.code.width})과 같은 폭과 왼쪽 끝이어야 한다`);
    assert.ok(Math.abs(m.allow.left - m.sc.left) <= 1 && Math.abs(m.allow.width - m.sc.width) <= 1, `허용 단추(${m.allow.left}, ${m.allow.width})가 권한 목록(${m.sc.left}, ${m.sc.width})과 같은 폭과 왼쪽 끝이어야 한다`);
    await ctx.close();
  } },
  { name: '2단계 인증 방식 카드 두 장은 나란히 놓이고 사용자 설정 단추(초록)는 테마색이다(A-583)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="page-wrap shell user-screen"><div class="shell-main" style="width:900px"><div class="two-factor-page"><div class="tf-method-cards"><div class="tf-method-card" id="m1"><h4>인증 앱</h4><p class="tf-method-help">앱</p><button class="btn btn-success" id="g1">추가</button></div><div class="tf-method-card" id="m2"><h4>보안 키</h4><p class="tf-method-help">키</p><a class="btn btn-success" id="g2">추가</a></div></div></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); const c = (s) => getComputedStyle(document.querySelector(s)).backgroundColor; return { a: r('#m1'), b: r('#m2'), g1: c('#g1'), g2: c('#g2') }; });
    assert.ok(m.a.top === m.b.top && m.b.left > m.a.left, `방식 카드는 가로로 나란히 놓인다(${m.a.left},${m.a.top} / ${m.b.left},${m.b.top})`); assert.ok(Math.abs(m.a.height - m.b.height) <= 1, `두 카드 높이가 같다(${m.a.height}/${m.b.height})`);
    for (const g of [m.g1, m.g2]) { const [r, gg] = g.match(/\d+/g).map(Number); assert.ok(r > 200 && gg < 160, `단추 색 ${g}이 테마 주황이 아니다`); }
    await ctx.close();
  } },
  { name: '빈 SSH 키 목록은 가운데 정렬된 빈 상태 상자이고 단추가 상자 안에 있다(A-586)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="page-wrap shell user-screen"><div class="shell-main" style="width:900px"><div class="key-empty" id="box"><svg class="key-empty-icon" id="ic" viewBox="0 0 24 24" width="40" height="40"></svg><p class="empty" id="msg">등록한 SSH 키가 없습니다.</p><p class="key-empty-help" id="hp">도움말</p><a class="btn btn-success" id="go">새 SSH 키</a></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); const c = (s, k) => getComputedStyle(document.querySelector(s))[k]; return { box: r('#box'), go: r('#go'), msg: r('#msg'), bs: c('#box', 'borderTopStyle'), ta: c('#box', 'textAlign'), bg: c('#go', 'backgroundColor') }; });
    assert.equal(m.bs, 'dashed'); assert.equal(m.ta, 'center'); assert.ok(m.box.width >= 600, `상자 폭 ${m.box.width}px`);
    assert.ok(m.go.top >= m.msg.bottom && m.go.bottom <= m.box.bottom && Math.abs((m.go.left + m.go.width / 2) - (m.box.left + m.box.width / 2)) <= 2, '단추가 상자 안 가운데에 있다');
    const [r, g] = m.bg.match(/\d+/g).map(Number); assert.ok(r > 200 && g < 160, `단추 색 ${m.bg}`); await ctx.close();
  } },
  { name: '보안 키 추가 화면은 카드 안에서 입력란이 폭을 채우고 간격이 넉넉하다(A-586)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="page-wrap shell user-screen"><div class="shell-main" style="width:900px"><div class="two-factor-page"><div class="key-add-card" id="c"><h4 id="h">보안 키 추가</h4><p class="text-body-secondary" id="p">안내</p><label class="form-label" id="l">이름</label><input id="i" class="form-control w-100"><div class="tf-actions" id="a"><button class="btn btn-success" id="g">등록</button></div></div></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); return { c: r('#c'), h: r('#h'), p: r('#p'), l: r('#l'), i: r('#i'), a: r('#a') }; });
    assert.ok(m.i.width >= 360, `입력란 폭 ${m.i.width}px`); assert.ok(m.i.left - m.l.left <= 1, `라벨(${m.l.left})과 입력란(${m.i.left}) 왼쪽 끝이 같다`);
    assert.ok(m.p.top - m.h.bottom >= 4 && m.l.top - m.p.bottom >= 14, `제목-안내 ${m.p.top - m.h.bottom}px, 안내-라벨 ${m.l.top - m.p.bottom}px`); assert.ok(m.i.top - m.l.bottom >= 4 && m.a.top - m.i.bottom >= 16, `입력란 위아래 간격이 넉넉하다`); assert.ok(m.c.width <= 620, `카드 폭 ${m.c.width}px`); await ctx.close();
  } },
  { name: '비밀번호 변경: 입력란은 넓고 비밀번호 재 설정은 눈에 띄는 카드의 테마 윤곽 단추이다(A-586)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="page-wrap shell user-screen"><div class="shell-main" style="width:900px"><form id="frmPassword"><dl><dd><input id="pw" class="form-control" type="password"></dd></dl></form><div class="pw-reset-card" id="rc"><p id="rp">안내</p><a class="ybtn pw-reset-btn" id="rb">비밀번호 재 설정</a></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); const c = (s, k) => getComputedStyle(document.querySelector(s))[k]; return { pw: r('#pw'), rc: r('#rc'), bd: c('#rc', 'borderTopWidth'), bg: c('#rc', 'backgroundColor'), bc: c('#rb', 'borderTopColor'), col: c('#rb', 'color') }; });
    assert.ok(m.pw.width >= 400, `비밀번호 입력란 폭 ${m.pw.width}px`); assert.ok(parseFloat(m.bd) >= 1 && m.bg !== 'rgba(0, 0, 0, 0)', `재 설정 구역은 눈에 띄는 카드이다(테두리 ${m.bd}, 배경 ${m.bg})`);
    for (const k of [m.bc, m.col]) { const [r, g] = k.match(/\d+/g).map(Number); assert.ok(r > 200 && g < 160, `재 설정 단추 색 ${k}이 테마 주황이 아니다`); } await ctx.close();
  } },
  { name: '로그인 창과 2단계 인증 화면은 같은 글자와 입력란과 오류 모양을 쓴다(A-586)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="page-wrap shell user-screen"><div class="shell-main" style="width:900px"><div id="dlg" class="loginDialog"><form class="frm-wrap login-form-wrap"><div class="two-factor-step tf-challenge"><h4 id="dt">2단계 인증</h4><p id="dh">안내</p><dl><dd><input id="dc" class="text form-control" name="code" placeholder="코드"></dd></dl></div><div class="error" id="de"><span class="error-message">코드가 맞지 않습니다.</span></div></form></div><div class="two-factor-page"><div class="alert alert-danger" id="se">코드가 맞지 않습니다.</div><input id="sc" class="form-control w-100 tf-narrow tf-code" name="code"></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const c = (s, k) => getComputedStyle(document.querySelector(s))[k]; return { dta: c('#dt', 'textAlign'), dfs: parseFloat(c('#dt', 'fontSize')), dcf: c('#dc', 'fontFamily'), dcs: parseFloat(c('#dc', 'fontSize')), dcb: c('#dc', 'borderTopWidth'), dcta: c('#dc', 'textAlign'), scf: c('#sc', 'fontFamily'), scs: parseFloat(c('#sc', 'fontSize')), sch: parseFloat(c('#sc', 'height')), phs: parseFloat(getComputedStyle(document.getElementById('dc'), '::placeholder').fontSize), debg: c('#de', 'backgroundColor'), decol: c('#de', 'color'), sebg: c('#se', 'backgroundColor'), secol: c('#se', 'color') }; });
    assert.equal(m.dta, 'center'); assert.ok(m.dfs >= 22, `로그인 창 2단계 제목 ${m.dfs}px`); assert.ok(m.dcs >= 24 && m.dcta === 'center' && parseFloat(m.dcb) >= 1, `로그인 창 코드 입력란: 글자 ${m.dcs}px, 정렬 ${m.dcta}, 테두리 ${m.dcb}`);
    assert.ok(/mono|courier|consolas/i.test(m.dcf) && /mono|courier|consolas/i.test(m.scf), `코드 입력란은 고정폭 글꼴이다(${m.dcf} / ${m.scf})`); assert.equal(m.dcs, m.scs, '설정 화면과 로그인 창의 코드 글자 크기가 같다'); assert.ok(m.phs <= 18, `자리 표시 글자(${m.phs}px)는 입력 글자보다 작아야 한다`); assert.ok(m.sch >= 44, `설정 화면의 코드 입력란도 같은 높이(44px 이상)이다(${m.sch}px)`);
    assert.equal(m.debg, m.sebg, `오류 배경색이 같다(${m.debg} / ${m.sebg})`); assert.equal(m.decol, m.secol, `오류 글자색이 같다(${m.decol} / ${m.secol})`); await ctx.close();
  } },
  { name: 'OAuth 앱 양식의 라벨은 입력란과 같은 왼쪽 끝에서 시작한다(A-588)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="page-wrap shell user-screen"><div class="shell-main" style="width:900px"><div class="two-factor-page oauth-screen"><h4 id="t" class="oauth-form-title">앱 수정</h4><p id="cid" class="mb-3">클라이언트 ID: <code>yona_app_x</code></p><form id="oauth-app-form"><div class="mb-3"><label class="form-label" id="l" for="n">이름</label><input id="n" class="form-control w-100"></div></form></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); return { t: r('#t'), c: r('#cid'), l: r('#l'), n: r('#n') }; });
    assert.ok(m.c.top - m.t.bottom >= 6, `제목과 클라이언트 ID 줄 사이 간격이 6px 이상이어야 한다(${m.c.top - m.t.bottom}px)`);
    assert.ok(Math.abs(m.l.left - m.t.left) <= 1 && Math.abs(m.l.left - m.n.left) <= 1, `라벨(${m.l.left})이 제목(${m.t.left})과 입력란(${m.n.left})과 같은 왼쪽 끝이어야 한다`); await ctx.close();
  } },
  { name: '사이트 관리 화면: 단추는 테마색이고 SSO 구역 제목은 강조되며 구분선 위 간격이 짧고 옵션 상자는 길지 않다(A-594)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="shell"><div class="shell-main site-setting-wrap" style="width:1000px"><div class="two-factor-page"><div class="tab-content sso-form"><div class="tab-pane active show" id="p"><div class="mb-3" id="prev"><label class="form-label">라벨</label><input class="form-control" style="width:300px"></div><h5 class="sso-section" id="sec">구역 제목</h5><div class="mb-3"><label class="form-label">다음</label></div><form class="security-settings"><label class="option" id="opt"><input class="form-check-input" type="checkbox"><span class="option-text"><strong>SSO 전용 모드</strong><small>설명</small></span></label></form><div class="sso-actions"><button class="btn btn-success" id="sv">저장</button><button class="btn btn-outline-secondary" id="ck">연결 확인</button></div><p class="sso-check-help" id="hp">설명</p></div></div></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); const c = (s, k) => getComputedStyle(document.querySelector(s))[k]; return { prev: r('#prev'), sec: r('#sec'), opt: r('#opt'), sv: r('#sv'), ck: r('#ck'), fs: parseFloat(c('#sec', 'fontSize')), fw: parseInt(c('#sec', 'fontWeight')), bt: c('#sec', 'borderTopWidth'), bs: c('#sec', 'boxShadow'), bl: c('#sec', 'borderLeftWidth'), svbg: c('#sv', 'backgroundColor'), ckbc: c('#ck', 'borderTopColor'), ckcol: c('#ck', 'color') }; });
    assert.ok(m.fs >= 16 && m.fw >= 700, `구역 제목은 강조된다(${m.fs}px, ${m.fw})`); assert.ok(parseFloat(m.bt) >= 1 || m.bs !== 'none', '구분선이 있다'); assert.ok(parseFloat(m.bl) >= 3, `구역 제목은 왼쪽 강조선이 있다(${m.bl})`);
    assert.ok(m.sec.top - m.prev.bottom <= 26, `구분선 위 간격이 26px 이하이다(${m.sec.top - m.prev.bottom}px)`); assert.ok(m.opt.width <= 660, `옵션 상자는 660px 이하이다(${m.opt.width}px)`);
    assert.ok(Math.abs(m.sv.top - m.ck.top) <= 2 && m.ck.left > m.sv.left, '저장과 연결 확인은 한 줄이다');
    for (const k of [m.svbg, m.ckbc, m.ckcol]) { const [r, g] = k.match(/\d+/g).map(Number); assert.ok(r > 200 && g < 160, `단추 색 ${k}이 테마 주황이 아니다`); } await ctx.close();
  } },
  { name: '사이트 관리 목록의 로고는 칸을 꽉 채우고 감사 로그 설명 아래에 간격이 있다(A-596)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="shell"><div class="shell-main site-setting-wrap"><div class="project-list-wrap"><div class="listitem" style="background:#f7f7f7"><a class="avatar-wrap list-avatar"><img id="lg" src="data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='1024' height='768'><rect width='1024' height='768' fill='white'/></svg>"></a></div></div><div class="audit-screen" id="au"><form class="audit-filter" id="fl"></form></div><p class="text-body-secondary" id="ds">설명</p><div class="audit-screen2"></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); return { lg: r('#lg'), box: r('.list-avatar'), mb: parseFloat(getComputedStyle(document.querySelector('#ds')).marginBottom) }; });
    assert.ok(Math.abs(m.lg.height - m.box.height) <= 1 && Math.abs(m.lg.width - m.box.width) <= 1, `로고가 칸을 채운다(${m.lg.width}x${m.lg.height} / ${m.box.width}x${m.box.height})`);
    assert.ok(m.mb >= 12, `설명 아래 간격이 12px 이상이다(${m.mb}px)`); await ctx.close();
  } },
  { name: '검색: 검색창은 한 줄이고 프로젝트 결과 강조는 은은하며 결과 없음 안내는 그림 아래에 있다(A-598)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div style="width:900px"><div class="search-box-wrap"><form id="searchInnerForm" class="search-form"><input id="kw" type="text" class="w-100 form-control" value="demo"><button id="sb" type="submit" class="ybtn ybtn-primary">검색</button></form></div><div class="search-result-wrap"><ul class="search-list-wrap"><li class="search-list-item project"><div class="title-wrap"><a id="pn" class="title search-project-name">admin/<strong id="kwd" class="keyword">demo</strong></a></div></li></ul><div id="em" class="empty-result"><p id="et" class="empty-result-text">검색 결과가 없습니다.</p></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); const c = (s, k) => getComputedStyle(document.querySelector(s))[k]; return { kw: r('#kw'), sb: r('#sb'), sbbg: c('#sb', 'backgroundColor'), kbg: c('#kwd', 'backgroundColor'), pnbg: c('#pn', 'backgroundColor'), em: r('#em'), et: r('#et') }; });
    assert.ok(Math.abs(m.kw.top - m.sb.top) <= 4 && m.sb.left >= m.kw.right, `검색창과 단추가 한 줄이다(${JSON.stringify([m.kw.top, m.sb.top])})`);
    assert.ok(m.kw.width > 500, `입력란이 넓다(${m.kw.width}px)`);
    const [r, g] = m.sbbg.match(/\d+/g).map(Number); assert.ok(r > 200 && g < 160, `검색 단추는 테마 주황이다(${m.sbbg})`);
    const [kr, kg, kb] = m.kbg.match(/\d+/g).map(Number); assert.ok(kr > 240 && kg > 220 && kb > 150, `프로젝트 결과의 키워드 강조는 연한 바탕이다(${m.kbg})`);
    assert.ok(m.pnbg === 'rgba(0, 0, 0, 0)', `프로젝트 이름에 보라 바탕이 없다(${m.pnbg})`);
    assert.ok(m.et.top > m.em.top + 100, `안내 글은 그림 아래에 있다(${m.et.top - m.em.top}px)`); await ctx.close();
  } },
  { name: '첫 화면 알림: "Edit" 표시가 칸에 잘리지 않고 아이콘 칸 폭이다(A-602)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="content-container"><div class="main-stream"><ul class="activity-streams notification-wrap"><li class="notification-stream"><div id="ed" class="stream-type updated">Edit</div></li><li class="notification-stream"><div id="ic" class="stream-type closed"><i class="yobicon-closed"></i></div></li></ul></div></div></body></html>`);
    const m = await page.evaluate(() => { const e = document.getElementById('ed'); const i = document.getElementById('ic'); return { sw: e.scrollWidth, cw: e.clientWidth, ew: e.getBoundingClientRect().width, iw: i.getBoundingClientRect().width }; });
    assert.ok(m.sw <= m.cw, `Edit 글자가 칸에 들어간다(scroll ${m.sw} / client ${m.cw})`);
    assert.ok(m.ew >= 30 && m.ew <= 36, `Edit 칸은 아이콘 칸(약 32px)과 같은 폭이다(${m.ew}px)`); await ctx.close();
  } },
  { name: '로그인 전 첫 화면: 부제는 읽을 수 있는 불투명도와 크기이고 제목은 굵다(A-602)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="siteintro"><div class="siteintro-cover"><div class="siteintro-wrap"><h1 id="h" class="site-heading">제목</h1><ul class="site-features"><li id="f">부제</li></ul></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const c = (s, k) => getComputedStyle(document.querySelector(s))[k]; return { fo: parseFloat(c('#f', 'opacity')), ff: parseFloat(c('#f', 'fontSize')), hw: parseInt(c('#h', 'fontWeight')), ho: parseFloat(c('#h', 'opacity')), bg: c('.siteintro', 'backgroundImage') }; });
    assert.ok(m.fo >= 0.9, `부제 불투명도(${m.fo})`); assert.ok(m.ff >= 19, `부제 글자 크기(${m.ff}px)`); assert.ok(m.hw >= 600 && m.ho === 1, `제목은 굵고 불투명하다(${m.hw}, ${m.ho})`); await ctx.close();
  } },
  { name: '환영 화면: 오류 문구가 보이고 입력 폼은 안내 상자와 같은 가운데에 놓인다(A-603)', run: async () => {
    const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage();
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS_ALL}</style></head><body><div class="page-wrap-outer"><div class="container page-wrap"><div class="page"><div class="secret-wrap"><div class="alert secret-box alert-warning" id="box">안내</div><div class="signup-form-wrap frm-wrap"><form class="welcome-form" id="f"><dl><dt><label>이메일</label> <span id="er" class="badge bg-danger">이메일이 잘못되었습니다.</span></dt><dd><input id="in" type="text" class="text password form-control w-100"></dd></dl></form></div></div></div></div></div></body></html>`);
    const m = await page.evaluate(() => { const r = (s) => document.querySelector(s).getBoundingClientRect(); return { er: r('#er'), f: r('#f'), box: r('#box'), inp: r('#in'), fs: parseFloat(getComputedStyle(document.querySelector('#er')).fontSize) }; });
    assert.ok(m.er.width > 60 && m.fs >= 11, `오류 문구가 보인다(폭 ${m.er.width}px, 글자 ${m.fs}px)`);
    const fc = m.f.left + m.f.width / 2, bc = m.box.left + m.box.width / 2; assert.ok(Math.abs(fc - bc) <= 4, `폼이 안내 상자와 같은 가운데이다(${fc} / ${bc})`);
    assert.ok(m.inp.width >= 280, `입력란 폭이 충분하다(${m.inp.width}px)`); await ctx.close();
  } },
];

