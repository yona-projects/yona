// 루트 글자 크기를 키웠을 때(기본 16px에서 지정 값) 글자와 줄 높이가 함께 커지는지, 가로로 넘치는 곳이 생기는지 확인한다.
// 사용: node scalecheck.mjs [--size 20] [--only <id 일부>] [--role admin]
import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';

const arg = (k, d) => { const i = process.argv.indexOf(k); return i > 0 ? process.argv[i + 1] : d; };
const size = Number(arg('--size', '20')); const only = arg('--only', ''); const role = arg('--role', 'admin');
const fx = await seed({ port: 19191 });
const list = screens(fx).filter((s) => (!only || only.split(',').some((o) => s.id.includes(o))) && (!s.roles || s.roles.includes(role)));
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
try {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'en-US' });
  if (role !== 'anon') { const s = new Session(B.base); await s.login(role === 'admin' ? ADMIN.loginId : role); await ctx.addCookies(s.playwrightCookies()); }
  await ctx.route((u) => u.origin !== B.base, (r) => r.abort());
  for (const sc of list) {
    const page = await ctx.newPage();
    await page.goto(B.base + sc.path, { waitUntil: 'load' }); await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
    const measure = () => page.evaluate(() => {
      const bad = [];
      for (const e of document.querySelectorAll('body *')) {
        const cs = getComputedStyle(e); if (cs.display === 'none' || cs.visibility === 'hidden') continue;
        const r = e.getBoundingClientRect(); if (r.width === 0 || r.height === 0) continue;
        const clipX = e.scrollWidth > e.clientWidth + 1 && ['hidden', 'auto', 'scroll'].includes(cs.overflowX) && e.clientWidth > 0;
        if (clipX) bad.push((e.tagName + '.' + String(e.className).split(' ')[0]).slice(0, 40));
      }
      const t = document.querySelector('p, li, td, a');
      return { pageOverflowX: document.documentElement.scrollWidth - innerWidth, pageHeight: document.documentElement.scrollHeight, clipped: [...new Set(bad)].length, sample: [...new Set(bad)].slice(0, 6), bodyFont: getComputedStyle(document.body).fontSize, bodyLH: getComputedStyle(document.body).lineHeight };
    });
    const a = await measure();
    await page.evaluate((v) => { document.documentElement.style.fontSize = v + 'px'; }, size);
    await page.waitForTimeout(200);
    const b = await measure();
    await page.screenshot({ path: join(ROOT, 'shots', `scale-${sc.id}-${size}.png`) });
    console.log(`${sc.id}: 폰트 ${a.bodyFont}/${a.bodyLH} → ${b.bodyFont}/${b.bodyLH}, 가로 넘침 ${a.pageOverflowX} → ${b.pageOverflowX}, 잘리는 요소 ${a.clipped} → ${b.clipped} ${b.clipped > a.clipped ? JSON.stringify(b.sample) : ''}`);
    await page.close();
  }
  await browser.close();
} finally { await B.stop(); }
