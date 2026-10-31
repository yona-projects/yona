// border-box 전환(로드맵 X-38h, D-55)을 돕는 도구: content-box를 전제로 적힌 width/height 선언을 `calc(원래 값 + 여백과 테두리)`로 고친다.
// 현재 빌드(전역 border-box)의 모든 화면을 열어 요소마다 (1) 실제로 적용된 자체 CSS의 width/height/min-/max- 선언이 어느 규칙인지(CDP),
// (2) 그 요소의 안쪽 여백과 테두리 합을 읽고, 소스 맵으로 SCSS 선언 위치를 찾아 값을 고친다.
// 사용: node bbpatch.mjs [--mode pad|diff] [--apply] [--only <id 일부>] [--role admin]
//   pad  (기본): 안쪽 여백과 테두리 합을 더한다. 처음 한 번만 쓴다(이미 고친 선언에 다시 쓰면 두 번 더해진다).
//   diff: 기준 빌드(pre-bs5)와 현재 빌드의 요소 크기 차이만큼만 width/height 선언에 더한다. **권장하지 않는다**: 부모와 형제의 영향까지 선언 값에 넣어 `.row`와 그리드 열에 잘못된 값이 들어갔다(A-299).
// 한계: 화면을 열었을 때 보이는 요소만 본다(닫힌 드롭다운, 모달 등은 보지 못한다). 여백이 요소마다 다른 선언은 값이 갈려 고치지 못하고 충돌로 보고한다.
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { createRequire } from 'node:module';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';

const require = createRequire(join(ROOT, '..', 'build-tools', 'sass', 'package.json'));
const { SourceMapConsumer } = require('source-map-js');
const arg = (n, d) => { const i = process.argv.indexOf(`--${n}`); return i > 0 ? process.argv[i + 1] : d; };
const mode = arg('mode', 'pad'); const apply = process.argv.includes('--apply'); const only = arg('only', ''); const roleOnly = arg('role', '');
const REPO = join(ROOT, '..');
const CSS_DIR = join(REPO, 'build', 'generated', 'sass', 'static', 'assets', 'stylesheets');
const SCSS_DIR = join(REPO, 'app', 'assets', 'stylesheets');
const consumers = Object.fromEntries(['yobi', 'usermenu'].map((n) => [n, new SourceMapConsumer(JSON.parse(readFileSync(join(CSS_DIR, `${n}.css.map`), 'utf8')))]));
const DIMS = { width: 'x', 'min-width': 'x', 'max-width': 'x', height: 'y', 'min-height': 'y', 'max-height': 'y' };
const px = (v) => parseFloat(v) || 0;

const tagAll = () => {
  const out = []; let n = 0;
  const walk = (el, path) => {
    if (['SCRIPT', 'STYLE', 'LINK', 'META', 'NOSCRIPT', 'TITLE', 'HEAD'].includes(el.tagName)) return;
    const cs = getComputedStyle(el); const r = el.getBoundingClientRect();
    if (cs.display !== 'none' && el.getClientRects().length > 0) {
      el.setAttribute('data-bbid', String(n)); out.push({ id: n++, path, tag: el.tagName.toLowerCase(), x: r.x, y: r.y, w: r.width, h: r.height, display: cs.display, bs: cs.boxSizing });
    }
    let i = 0; for (const c of el.children) walk(c, path + '/' + c.tagName.toLowerCase() + (i++));
  };
  walk(document.body, 'body'); return out;
};

const fx = await seed({ port: 19191 });
const servers = [];
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
let A = null; A = await startServer({ port: 19291, dataDir: join(ROOT, '.state', 'server-base'), jar: join(ROOT, '.state', 'baseline', 'yona-pre-bs5.jar') });
const decls = new Map(); // key -> {file,line,col,prop,value,deltas:[],selectors:Set}
let elements = 0, skippedOther = 0, inlineSkipped = 0;
try {
  const browser = await chromium.launch();
  const ctxFor = async (server, role) => {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'en-US' });
    if (role !== 'anon') { const s = new Session(server.base); await s.login(role === 'admin' ? ADMIN.loginId : role); await ctx.addCookies(s.playwrightCookies()); }
    await ctx.route((u) => u.origin !== server.base, (r) => r.abort());
    return ctx;
  };
  const ctxB = {}, ctxA = {};
  for (const role of ['anon', 'admin', 'alice', 'bob']) { ctxB[role] = await ctxFor(B, role); if (A) ctxA[role] = await ctxFor(A, role); }
  for (const sc of screens(fx)) {
    if (only && !sc.id.includes(only)) continue;
    for (const role of (roleOnly ? sc.roles : [sc.roles.includes('admin') ? 'admin' : sc.roles[0]])) {
      if (roleOnly && role !== roleOnly) continue;
      const page = await ctxB[role].newPage();
      const client = await ctxB[role].newCDPSession(page);
      const sheets = new Map(); client.on('CSS.styleSheetAdded', (e) => sheets.set(e.header.styleSheetId, e.header.sourceURL || ''));
      await client.send('DOM.enable'); await client.send('CSS.enable');
      try {
        await page.goto(B.base + sc.path, { waitUntil: 'load', timeout: 30000 }); await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
        const els = await page.evaluate(tagAll); const curMap = new Map(els.map((e) => [e.path, e]));
        let baseMap = null;
        if (A) { const pa = await ctxA[role].newPage(); await pa.goto(A.base + sc.path, { waitUntil: 'load', timeout: 30000 }); await pa.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {}); baseMap = new Map((await pa.evaluate(tagAll)).map((e) => [e.path, e])); await pa.close(); }
        const { root } = await client.send('DOM.getDocument', { depth: 0 });
        const { nodeIds } = await client.send('DOM.querySelectorAll', { nodeId: root.nodeId, selector: '[data-bbid]' });
        if (nodeIds.length !== els.length) { console.log(`[${sc.id}.${role}] 요소 수 불일치 ${nodeIds.length} / ${els.length}, 건너뜀`); continue; }
        for (let i = 0; i < els.length; i++) {
          const e = els[i]; if (['img', 'svg', 'canvas', 'video', 'iframe'].includes(e.tag) && mode === 'pad') { /* 교체 요소도 border-box 대상이다 */ }
          if (e.display === 'inline') continue; // 글줄 안의 요소는 width/height가 적용되지 않는다
          let want = null;
          if (mode === 'pad') { const b0 = baseMap.get(e.path); if (!b0 || b0.bs === 'border-box') continue; }
          if (mode === 'diff') {
            const b = baseMap.get(e.path); if (!b) continue;
            // 윗 요소의 크기가 이미 달라졌다면 그 차이가 전해진 것이므로 가장 위에서 달라진 요소만 고친다.
            const pp = e.path.slice(0, e.path.lastIndexOf('/')); const pb = baseMap.get(pp), pc = curMap.get(pp);
            if (pb && pc && (Math.abs(pb.w - pc.w) > 1 || Math.abs(pb.h - pc.h) > 1)) continue;
            want = { x: Math.round(b.w - e.w), y: Math.round(b.h - e.h) }; if (Math.abs(want.x) <= 1 && Math.abs(want.y) <= 1) continue;
          }
          elements++;
          const [{ computedStyle }, matched] = await Promise.all([client.send('CSS.getComputedStyleForNode', { nodeId: nodeIds[i] }), client.send('CSS.getMatchedStylesForNode', { nodeId: nodeIds[i] })]);
          const cs = Object.fromEntries(computedStyle.map((p) => [p.name, p.value]));
          if (cs['box-sizing'] !== 'border-box') continue;
          const padX = px(cs['padding-left']) + px(cs['padding-right']) + px(cs['border-left-width']) + px(cs['border-right-width']);
          const padY = px(cs['padding-top']) + px(cs['padding-bottom']) + px(cs['border-top-width']) + px(cs['border-bottom-width']);
          for (const [prop, axis] of Object.entries(DIMS)) {
            if (mode === 'diff' && prop !== 'width' && prop !== 'height') continue;
            if ((matched.inlineStyle?.cssProperties || []).some((p) => p.name === prop && p.value)) { inlineSkipped++; continue; }
            let win = null;
            for (const m of matched.matchedCSSRules || []) {
              if (m.rule.origin !== 'regular') continue;
              for (const p of m.rule.style.cssProperties) {
                if (p.name !== prop || p.disabled || !p.range) continue;
                if (!win || p.important || !win.p.important) win = { p, rule: m.rule };
              }
            }
            if (!win) continue;
            const val = win.p.value.trim();
            if (/^(auto|none|initial|inherit|unset|revert|fit-content|max-content|min-content)$/i.test(val) || val.startsWith('var(') || /^0(px)?$/.test(val) && mode === 'pad' && false) continue;
            const url = sheets.get(win.rule.styleSheetId) || '';
            const sm = /\/(yobi|usermenu)\.css$/.exec(url); if (!sm) { skippedOther++; continue; }
            const delta = mode === 'pad' ? (axis === 'x' ? padX : padY) : (axis === 'x' ? want.x : want.y);
            if (Math.abs(delta) < 0.01) continue;
            const key = `${sm[1]}:${win.p.range.startLine}:${win.p.range.startColumn}:${prop}`;
            const d = decls.get(key) || { sheet: sm[1], prop, value: val, line: win.p.range.startLine, col: win.p.range.startColumn, deltas: [], selectors: new Set(), screens: new Set() };
            d.deltas.push(Math.round(delta * 100) / 100); d.selectors.add(win.rule.selectorList.text.slice(0, 70)); d.screens.add(sc.id); decls.set(key, d);
          }
        }
        console.log(`[${sc.id}.${role}] 요소 ${els.length}, 누적 선언 ${decls.size}`);
      } catch (err) { console.log(`[${sc.id}.${role}] 오류: ${String(err.message).slice(0, 120)}`); }
      await page.close();
    }
  }
  await browser.close();
} finally { await B.stop(); if (A) await A.stop(); }

// 같은 선언에 모인 값이 하나로 모이는지 본다(1px 안쪽이면 같은 값으로 본다).
const patches = []; const conflicts = [];
for (const [key, d] of decls) {
  const sorted = [...d.deltas].sort((a, b) => a - b); const med = sorted[Math.floor(sorted.length / 2)];
  const spread = sorted[sorted.length - 1] - sorted[0];
  const pos = consumers[d.sheet].originalPositionFor({ line: d.line + 1, column: d.col });
  const info = { key, prop: d.prop, value: d.value, delta: med, count: d.deltas.length, spread, src: pos.source ? `${pos.source}:${pos.line}:${pos.column}` : null, selectors: [...d.selectors].slice(0, 3), screens: [...d.screens].slice(0, 3) };
  if (!pos.source) { conflicts.push({ ...info, why: '소스 위치 없음' }); continue; }
  if (spread > 1) { conflicts.push({ ...info, why: '값이 여러 개', deltas: [...new Set(sorted)].slice(0, 8) }); continue; }
  patches.push({ ...info, source: pos.source, sline: pos.line, scol: pos.column });
}
writeFileSync(join('/tmp/claude-1000/gen', `bb-report-${mode}.json`), JSON.stringify({ patches, conflicts, stats: { elements, decls: decls.size, skippedOther, inlineSkipped } }, null, 1));
console.log(`선언 ${decls.size}개 중 고칠 것 ${patches.length}, 충돌 ${conflicts.length}, 자체 CSS가 아닌 것 ${skippedOther}, 인라인 ${inlineSkipped}`);
if (apply) {
  const byFile = new Map(); for (const p of patches) { (byFile.get(p.source) || byFile.set(p.source, []).get(p.source)).push(p); }
  let done = 0, failed = 0;
  for (const [file, list] of byFile) {
    const path = join(SCSS_DIR, file); let text = readFileSync(path, 'utf8'); const lines = text.split('\n');
    const lineStart = []; let off = 0; for (const l of lines) { lineStart.push(off); off += l.length + 1; }
    for (const p of list.sort((a, b) => (b.sline - a.sline) || (b.scol - a.scol))) {
      const start = lineStart[p.sline - 1] + p.scol;
      const m = /^([a-z-]+)(\s*:\s*)/.exec(text.slice(start, start + 60));
      if (!m || m[1] !== p.prop) { failed++; console.log('위치 불일치', file, p.sline, p.scol, p.prop, JSON.stringify(text.slice(start, start + 40))); continue; }
      let i = start + m[0].length, depth = 0, end = -1;
      for (; i < text.length; i++) { const c = text[i]; if (c === '(') depth++; else if (c === ')') depth--; else if (depth === 0 && (c === ';' || c === '}' || c === '\n')) { end = i; break; } }
      let value = text.slice(start + m[0].length, end).trim(); let imp = '';
      const im = /\s*!important$/.exec(value); if (im) { imp = ' !important'; value = value.slice(0, im.index); }
      if (/^0$/.test(value)) value = '0px';
      const sign = p.delta < 0 ? '-' : '+'; const amt = Math.abs(p.delta);
      const next = `calc(${value} ${sign} ${amt}px)${imp}`;
      text = text.slice(0, start + m[0].length) + next + text.slice(end); done++;
    }
    writeFileSync(path, text);
  }
  console.log(`적용 ${done}, 실패 ${failed}`);
}
