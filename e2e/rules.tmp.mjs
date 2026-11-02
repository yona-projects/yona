import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';
const [id, sel, props] = process.argv.slice(2);
const fx = await seed({ port: 19191 });
const sc = screens(fx).find((s) => s.id === id);
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
try {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const s = new Session(B.base); await s.login(ADMIN.loginId); await ctx.addCookies(s.playwrightCookies());
  await ctx.route((u) => u.origin !== B.base, (r) => r.abort());
  const page = await ctx.newPage(); await page.goto(B.base + sc.path, { waitUntil: 'load' });
  console.log((await page.evaluate(([sel, props]) => { const e = document.querySelector(sel); const ps = props.split(','); const out = []; for (const sh of document.styleSheets) { let rules; try { rules = sh.cssRules; } catch (x) { continue; } const walk = (rs) => { for (const r of rs) { if (r.cssRules && !r.selectorText) walk(r.cssRules); else if (r.selectorText && r.style && ps.some((p) => r.style.getPropertyValue(p)) && e.matches(r.selectorText)) out.push((sh.href || '').split('/').pop() + ' | ' + r.selectorText + ' | ' + ps.map((p) => p + ':' + r.style.getPropertyValue(p)).join(' ')); } }; walk(rules); } const r = e.getBoundingClientRect(); return out.join('\n') + '\nrect ' + Math.round(r.width) + 'x' + Math.round(r.height); }, [sel, props])));
  await browser.close();
} finally { await B.stop(); }
