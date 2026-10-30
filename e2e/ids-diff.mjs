// 두 빌드에서 같은 화면의 id가 있는 요소와 주요 클래스 요소의 위치를 대조한다(문서 순서). 사용: node ids-diff.mjs <화면 id> [--role admin]
import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';
const id = process.argv[2]; const ri = process.argv.indexOf('--role'); const role = ri > 0 ? process.argv[ri + 1] : 'admin';
const fx = await seed({ port: 19191 }); const sc = screens(fx).find((s) => s.id === id);
const A = await startServer({ port: 19291, dataDir: join(ROOT, '.state', 'server-base'), jar: join(ROOT, '.state', 'baseline', 'yona-pre-bs5.jar') });
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
const grab = (page) => page.evaluate(() => [...document.querySelectorAll('[id], [class]')].filter((e) => e.getClientRects().length && (e.id || /project|menu|header|title|toolbar|editor|nav|wrap|tab/.test(e.className.toString()))).slice(0, 400).map((e) => ({ k: e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + '.' + e.className.toString().trim().split(/\s+/).slice(0, 2).join('.'), y: Math.round(e.getBoundingClientRect().y + scrollY), h: Math.round(e.getBoundingClientRect().height), x: Math.round(e.getBoundingClientRect().x), w: Math.round(e.getBoundingClientRect().width) })));
try {
  const browser = await chromium.launch(); const res = [];
  for (const server of [A, B]) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'en-US' });
    if (role !== 'anon') { const s = new Session(server.base); await s.login(role === 'admin' ? ADMIN.loginId : role); await ctx.addCookies(s.playwrightCookies()); }
    await ctx.route((u) => u.origin !== server.base, (r) => r.abort());
    const page = await ctx.newPage(); await page.goto(server.base + sc.path, { waitUntil: 'load' }); await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
    res.push(await grab(page)); await ctx.close();
  }
  const vis = (e) => e.x < 1280; const mb = new Map(); res[1].forEach((e) => { if (!mb.has(e.k)) mb.set(e.k, e); });
  let n = 0;
  for (const a of res[0]) { const b = mb.get(a.k); if (!b || !vis(a)) continue; if (Math.abs(a.y - b.y) > 1 || Math.abs(a.h - b.h) > 1 || Math.abs(a.x - b.x) > 1 || Math.abs(a.w - b.w) > 1) { console.log(`${a.k.slice(0, 60).padEnd(60)} y ${a.y}→${b.y}  h ${a.h}→${b.h}  x ${a.x}→${b.x}  w ${a.w}→${b.w}`); if (++n > 25) break; } }
  await browser.close();
} finally { await A.stop(); await B.stop(); }
