import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';
process.env.YONA_AUDIT_STREAM_WEBHOOK_URL = 'http://127.0.0.1:1/none';
const fx = await seed({ port: 19191 });
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
try {
  await new Promise((r) => setTimeout(r, 45000));            // 처음 설정한 커서가 만들어지길 기다린다
  const s = new Session(B.base); await s.login(ADMIN.loginId); // 로그인 기록이 생겨 밀린 기록이 된다
  await new Promise((r) => setTimeout(r, 25000));            // 보내기에 실패해 오류가 남길 기다린다
  const browser = await chromium.launch();
  for (const id of ['audit-site', 'audit-org', 'audit-user']) {
    const sc = screens(fx).find((x) => x.id === id);
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const t = new Session(B.base); await t.login(id === 'audit-site' ? ADMIN.loginId : (sc.role || ADMIN.loginId)); await ctx.addCookies(t.playwrightCookies());
    await ctx.route((u) => u.origin !== B.base, (r) => r.abort());
    const page = await ctx.newPage(); await page.goto(B.base + sc.path, { waitUntil: 'load' });
    await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
    await page.screenshot({ path: join(ROOT, 'shots', `x9-final-${id}.png`) });
    if (id === 'audit-site') await page.screenshot({ path: join(ROOT, 'shots', 'x9-final-audit-site-full.png'), fullPage: true });
    await ctx.close();
  }
  await browser.close(); console.log('ok');
} finally { await B.stop(); }
