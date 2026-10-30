// 한 요소의 한 속성이 어느 규칙에서 왔는지 기준 빌드와 현재 빌드에서 나란히 보여 준다(CDP). 사용: node explain.mjs <화면 id> "<선택자>" <속성> [--role admin]
import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';

const [id, selector, prop] = process.argv.slice(2);
const ri = process.argv.indexOf('--role'); const role = ri > 0 ? process.argv[ri + 1] : 'admin';
const fx = await seed({ port: 19191 });
const sc = screens(fx).find((s) => s.id === id);
const A = await startServer({ port: 19291, dataDir: join(ROOT, '.state', 'server-base'), jar: join(ROOT, '.state', 'baseline', 'yona-pre-bs5.jar') });
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
try {
  const browser = await chromium.launch();
  for (const [name, server] of [['기준', A], ['현재', B]]) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'en-US' });
    if (role !== 'anon') { const s = new Session(server.base); await s.login(role === 'admin' ? ADMIN.loginId : role); await ctx.addCookies(s.playwrightCookies()); }
    await ctx.route((u) => u.origin !== server.base, (r) => r.abort());
    const page = await ctx.newPage(); await page.goto(server.base + sc.path, { waitUntil: 'load' }); await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
    const cdp = await ctx.newCDPSession(page);
    await cdp.send('DOM.enable'); await cdp.send('CSS.enable');
    const { root } = await cdp.send('DOM.getDocument');
    const { nodeId } = await cdp.send('DOM.querySelector', { nodeId: root.nodeId, selector });
    if (!nodeId) { console.log(`[${name}] 요소 없음`); continue; }
    const m = await cdp.send('CSS.getMatchedStylesForNode', { nodeId });
    const hits = [];
    for (const r of m.matchedCSSRules || []) {
      const decl = r.rule.style.cssProperties.find((p) => p.name === prop.replace(/[A-Z]/g, (c) => '-' + c.toLowerCase()) && !p.disabled);
      if (decl) hits.push(`${r.rule.selectorList.text}  { ${decl.name}: ${decl.value}${decl.important ? ' !important' : ''} }  ← ${(r.rule.styleSheetId && r.rule.origin) || ''} ${(await cdp.send('CSS.getStyleSheetText', { styleSheetId: r.rule.styleSheetId }).then(() => '').catch(() => ''))}`);
    }
    const inline = (m.inlineStyle?.cssProperties || []).find((p) => p.name === prop);
    const cs = await page.evaluate(([sel, p]) => getComputedStyle(document.querySelector(sel))[p], [selector, prop]);
    console.log(`[${name}] 계산값 ${prop}: ${cs}${inline ? `  (인라인 ${inline.value})` : ''}`); hits.forEach((h) => console.log('   ', h.slice(0, 220)));
    await ctx.close();
  }
  await browser.close();
} finally { await A.stop(); await B.stop(); }
