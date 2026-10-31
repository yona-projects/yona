// 스크롤 위치별로 고정 대상 요소의 위치와 크기를 기준 빌드와 현재 빌드에서 나란히 비교한다.
// 사용: node scrollcmp.mjs <화면 id> "<선택자>" [--role admin] [--h 500]
import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';

const [id, selector] = process.argv.slice(2);
const arg = (k, d) => { const i = process.argv.indexOf(k); return i > 0 ? process.argv[i + 1] : d; };
const role = arg('--role', 'admin'); const height = Number(arg('--h', '500'));
const fx = await seed({ port: 19191 });
const sc = screens(fx).find((s) => s.id === id);
const A = await startServer({ port: 19291, dataDir: join(ROOT, '.state', 'server-base'), jar: join(ROOT, '.state', 'baseline', 'yona-pre-bs5.jar') });
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
const rows = {};
try {
  const browser = await chromium.launch();
  for (const [name, server] of [['기준', A], ['현재', B]]) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height }, locale: 'en-US' });
    if (role !== 'anon') { const s = new Session(server.base); await s.login(role === 'admin' ? ADMIN.loginId : role); await ctx.addCookies(s.playwrightCookies()); }
    await ctx.route((u) => u.origin !== server.base, (r) => r.abort());
    const page = await ctx.newPage(); await page.goto(server.base + sc.path, { waitUntil: 'load' }); await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
    const max = await page.evaluate(() => document.documentElement.scrollHeight - innerHeight);
    const out = [];
    for (const y of [0, 20, 60, 150, 300, 400, 900, 2000, 5000]) {
      if (y > max && y !== 0) { out.push({ y, skipped: `문서 높이 한계 ${max}` }); break; }
      await page.evaluate((v) => scrollTo(0, v), y); await page.waitForTimeout(150);
      out.push({ y, ...(await page.evaluate((sel) => {
        const e = document.querySelector(sel); if (!e) return { none: true };
        const r = e.getBoundingClientRect(); const cs = getComputedStyle(e);
        return { top: Math.round(r.top), left: Math.round(r.left), w: Math.round(r.width), h: Math.round(r.height), pos: cs.position, cls: e.className.replace(/\s+/g, ' ').slice(0, 60) };
      }, selector)) });
    }
    rows[name] = out;
    await page.screenshot({ path: join(ROOT, 'shots', `scroll-${id}-${name === '기준' ? 'base' : 'new'}.png`) });
    await ctx.close();
  }
  await browser.close();
  for (let i = 0; i < Math.max(rows['기준'].length, rows['현재'].length); i++) {
    const a = rows['기준'][i], b = rows['현재'][i];
    const same = a && b && ['top', 'left', 'w', 'h'].every((k) => a[k] === b[k]);
    console.log(same ? '=' : '≠', JSON.stringify(a), '\n ', JSON.stringify(b));
  }
} finally { await A.stop(); await B.stop(); }
