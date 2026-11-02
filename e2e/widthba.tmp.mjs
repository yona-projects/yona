import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';
const fx = await seed({ port: 19191 });
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
try {
  const browser = await chromium.launch();
  for (const [id, role, sel, cls] of [['settings-password', ADMIN.loginId, 'form', 'w-25'], ['project-fork-new', 'alice', 'form', 'w-50']]) {
    const sc = screens(fx).find((s) => s.id === id);
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const s = new Session(B.base); await s.login(role); await ctx.addCookies(s.playwrightCookies());
    await ctx.route((u) => u.origin !== B.base, (r) => r.abort());
    const page = await ctx.newPage(); await page.goto(B.base + sc.path, { waitUntil: 'load' });
    await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
    const form = (await page.$$(sel)).filter(Boolean);
    const target = await page.$(`form:has(.${cls})`);
    await page.evaluate((c) => document.querySelectorAll('.' + c).forEach((e) => e.classList.remove(c)), cls);
    await target.screenshot({ path: `/tmp/claude-1000/wb-${id}-before.png` });
    await page.evaluate(([c]) => {}, [cls]);
    await page.reload({ waitUntil: 'load' }); await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
    await (await page.$(`form:has(.${cls})`)).screenshot({ path: `/tmp/claude-1000/wb-${id}-after.png` });
    await ctx.close();
  }
  await browser.close();
} finally { await B.stop(); }
