// 화면에서 JS 식을 평가해 기준 빌드와 현재 빌드의 결과를 나란히 보여 준다. 사용: node evalpage.mjs <화면 id> "<JS 식>" [--role admin]
import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';

const [id, expr] = process.argv.slice(2);
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
    const page = await ctx.newPage(); const errs = []; page.on('pageerror', (e) => errs.push(String(e.message).slice(0, 200))); page.on('console', (m) => { if (m.type() === 'error') errs.push('console: ' + m.text().slice(0, 200)); }); await page.goto(server.base + sc.path, { waitUntil: 'load' }); await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
    if (errs.length) console.log(`[${name}] 페이지 오류`, errs);
    console.log(`[${name}]`, JSON.stringify(await page.evaluate((e) => (0, eval)(e), expr)));
    await ctx.close();
  }
  await browser.close();
} finally { await A.stop(); await B.stop(); }
