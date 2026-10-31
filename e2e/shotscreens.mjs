// 지정한 화면 몇 개를 현재 빌드로 캡처한다(컴포넌트 현대화 전후 비교용). 사용: node shotscreens.mjs <접미사> <화면 id>... [--clip "<선택자>"]
import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';
const args = process.argv.slice(2); const suffix = args.shift();
const ci = args.indexOf('--clip'); const clip = ci >= 0 ? args.splice(ci, 2)[1] : null;
const fx = await seed({ port: 19191 });
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
try {
  const browser = await chromium.launch();
  for (const id of args) {
    const sc = screens(fx).find((s) => s.id === id);
    const ctx = await browser.newContext({ viewport: { width: Number(process.env.W || 1280), height: 800 } });
    if (!process.env.ANON) { const s = new Session(B.base); await s.login(ADMIN.loginId); await ctx.addCookies(s.playwrightCookies()); }
    await ctx.route((u) => u.origin !== B.base, (r) => r.abort());
    const page = await ctx.newPage(); await page.goto(B.base + sc.path, { waitUntil: 'load' });
    await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
    const path = join(ROOT, 'shots', `x38i-${id}-${suffix}.png`);
    if (clip && await page.$(clip)) await (await page.$(clip)).screenshot({ path }); else await page.screenshot({ path });
    console.log(path); await ctx.close();
  }
  await browser.close();
} finally { await B.stop(); }
