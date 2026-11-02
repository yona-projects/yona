import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
try {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const s = new Session(B.base); await s.login(ADMIN.loginId); await ctx.addCookies(s.playwrightCookies());
  const page = await ctx.newPage(); await page.goto(B.base + '/sites/audit', { waitUntil: 'load' });
  const rows = await page.evaluate(() => [...document.querySelectorAll('.audit-filter input, .audit-filter select, .audit-filter button, .audit-filter a, .audit-export-csv, .audit-export-jsonl, .audit-export-json, .audit-verify')].map((e) => {
    const r = e.getBoundingClientRect(); const c = getComputedStyle(e);
    return `${e.tagName}.${e.className.toString().slice(0, 40)} h=${r.height.toFixed(1)} w=${r.width.toFixed(0)} lh=${c.lineHeight} pad=${c.paddingTop}/${c.paddingBottom} fs=${c.fontSize} box=${c.boxSizing}`;
  }));
  console.log(rows.join('\n'));
  await browser.close();
} finally { await B.stop(); }
