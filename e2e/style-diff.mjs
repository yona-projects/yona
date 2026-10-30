// 두 빌드의 같은 화면에서 요소의 계산된 스타일과 위치를 대조해 "어떤 규칙이 얼마나 많은 요소를 바꿨는지"를 모은다(로드맵 X-38, D-53).
// 캡처 비교가 "어디가 다른가"를 보여 준다면 이 도구는 "왜 다른가"의 후보를 준다: (클래스, 속성, 이전 값 → 현재 값)별 요소 수.
// 사용: node style-diff.mjs [--base <jar>] [--only <id 일부>] [--role admin]
// 기준 빌드는 .state/baseline/yona-pre-bs5.jar(pre-bs5 태그에서 만든 jar)와 .state/server-base 데이터를, 현재 빌드는 build/libs의 jar와 .state/server를 쓴다.
import { existsSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';

const arg = (n, d) => { const i = process.argv.indexOf(`--${n}`); return i > 0 ? process.argv[i + 1] : d; };
const only = arg('only', ''); const roleOnly = arg('role', '');
const baseJar = arg('base', join(ROOT, '.state', 'baseline', 'yona-pre-bs5.jar'));
const PROPS = ['display', 'position', 'float', 'boxSizing', 'width', 'height', 'marginTop', 'marginRight', 'marginBottom', 'marginLeft', 'paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft',
  'fontSize', 'fontWeight', 'lineHeight', 'fontFamily', 'color', 'backgroundColor', 'borderTopWidth', 'borderTopColor', 'textDecorationLine', 'verticalAlign', 'textAlign'];

const fx = await seed({ port: 19191 });
const A = await startServer({ port: 19291, dataDir: join(ROOT, '.state', 'server-base'), jar: baseJar });
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
const counts = new Map(); const perScreen = [];
try {
  const browser = await chromium.launch();
  async function ctxFor(server, role) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'en-US' });
    if (role !== 'anon') { const s = new Session(server.base); await s.login(role === 'admin' ? ADMIN.loginId : role); await ctx.addCookies(s.playwrightCookies()); }
    await ctx.route((u) => u.origin !== server.base, (r) => r.abort());
    return ctx;
  }
  const ctxA = {}, ctxB = {};
  for (const role of ['anon', 'admin', 'alice', 'bob']) { ctxA[role] = await ctxFor(A, role); ctxB[role] = await ctxFor(B, role); }
  const collect = (page) => page.evaluate((PROPS) => {
    const out = [];
    const walk = (el, path) => {
      if (['SCRIPT', 'STYLE', 'LINK', 'META', 'NOSCRIPT', 'TITLE', 'HEAD'].includes(el.tagName)) return;
      const cs = getComputedStyle(el); const r = el.getBoundingClientRect();
      if (cs.display !== 'none' && el.getClientRects().length > 0) {
        const cls = (el.getAttribute('class') || '').split(/\s+/).filter(Boolean).filter((c) => !/^(col-md-|row$|modal|ui-|tooltip|popover|fade|in$)/.test(c)).slice(0, 3).join('.');
        const style = {}; for (const p of PROPS) style[p] = cs[p];
        out.push({ path, label: el.tagName.toLowerCase() + (cls ? '.' + cls : ''), x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), style });
      }
      let i = 0; for (const c of el.children) walk(c, path + '/' + c.tagName.toLowerCase() + (i++));
    };
    walk(document.body, 'body');
    return out;
  }, PROPS);
  for (const sc of screens(fx)) {
    if (only && !sc.id.includes(only)) continue;
    for (const role of sc.roles) {
      if (roleOnly && role !== roleOnly) continue;
      const pa = await ctxA[role].newPage(), pb = await ctxB[role].newPage();
      try {
        await Promise.all([pa.goto(A.base + sc.path, { waitUntil: 'load', timeout: 30000 }), pb.goto(B.base + sc.path, { waitUntil: 'load', timeout: 30000 })]);
        await Promise.all([pa.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {}), pb.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {})]);
        const [ea, eb] = await Promise.all([collect(pa), collect(pb)]);
        const mb = new Map(eb.map((e) => [e.path, e])); let matched = 0, moved = 0;
        for (const a of ea) {
          const b = mb.get(a.path); if (!b || a.label !== b.label) continue; matched++;
          if (Math.abs(a.x - b.x) > 1 || Math.abs(a.y - b.y) > 1 || Math.abs(a.w - b.w) > 1 || Math.abs(a.h - b.h) > 1) moved++;
          const norm = (p, v) => (p === 'textAlign' && v === 'start' ? 'left' : v);
          for (const p of PROPS) if (norm(p, a.style[p]) !== norm(p, b.style[p])) { const k = `${a.label} | ${p} | ${a.style[p]} → ${b.style[p]}`; const v = counts.get(k) || { n: 0, screens: new Set() }; v.n++; v.screens.add(sc.id); counts.set(k, v); }
        }
        perScreen.push({ id: sc.id, role, total: ea.length, matched, moved });
        if (process.argv.includes('--moved')) { const first = ea.filter((a) => { const b = mb.get(a.path); return b && a.label === b.label && Math.abs(a.y - b.y) > 1; }).slice(0, 12); const hs = ea.filter((a) => { const b = mb.get(a.path); return b && a.label === b.label && Math.abs(a.h - b.h) > 1; }).slice(0, 22); console.log(`[${sc.id}.${role}] 높이가 바뀐 요소(앞쪽 22개):`); for (const a of hs) { const b = mb.get(a.path); console.log(`   ${a.path.slice(0, 70)} ${a.label} h ${a.h}→${b.h}`); } console.log(`[${sc.id}.${role}] 처음 위치가 바뀐 요소:`); for (const a of first) { const b = mb.get(a.path); console.log(`   ${a.path.slice(0, 70)} ${a.label} y ${a.y}→${b.y} h ${a.h}→${b.h}`); } }
      } catch (e) { perScreen.push({ id: sc.id, role, error: String(e.message).slice(0, 120) }); }
      await pa.close(); await pb.close();
    }
  }
  await browser.close();
} finally { await A.stop(); await B.stop(); }
const top = [...counts.entries()].sort((x, y) => y[1].n - x[1].n);
writeFileSync(join(ROOT, 'shots', 'style-diff.json'), JSON.stringify({ perScreen, top: top.slice(0, 400).map(([k, v]) => ({ k, n: v.n, screens: v.screens.size, ids: [...v.screens].slice(0, 8) })) }, null, 2));
console.log('화면', perScreen.length, '위치나 크기가 바뀐 요소 합계', perScreen.reduce((s, r) => s + (r.moved || 0), 0), '/', perScreen.reduce((s, r) => s + (r.matched || 0), 0));
for (const [k, v] of top.slice(0, 60)) console.log(String(v.n).padStart(5), `${v.screens.size}화면`.padStart(6), k.slice(0, 150));
