import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';
const [id, sel] = process.argv.slice(2);
const fx = await seed({ port: 19191 });
const sc = screens(fx).find((s) => s.id === id);
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
try {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const s = new Session(B.base); await s.login(ADMIN.loginId); await ctx.addCookies(s.playwrightCookies());
  await ctx.route((u) => u.origin !== B.base, (r) => r.abort());
  const page = await ctx.newPage(); await page.goto(B.base + sc.path, { waitUntil: 'load' });
  console.log(await page.evaluate((sel) => { const e = document.querySelector(sel); const f = (x) => { const r = x.getBoundingClientRect(); return `${x.tagName.toLowerCase()}.${[...x.classList].join('.')} x${Math.round(r.x)} y${Math.round(r.y)} w${Math.round(r.width)} h${Math.round(r.height)} ${getComputedStyle(x).display}`; }; return [f(e), ...[...e.children].map((c) => '  ' + f(c))].join('\n'); }, sel));
  await browser.close();
} finally { await B.stop(); }
