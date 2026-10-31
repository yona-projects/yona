// BS5 그리드로 옮긴 뒤 열이 어긋난 곳을 찾는다(D-57). 모든 화면에서 `.row`의 열 폭 합이 12 이하인데 줄이 넘어갔거나,
// 열의 계산된 폭이 BS5 퍼센트 폭과 다른(자체 규칙이 덮어쓴) 곳을 찍는다. 사용: node gridcheck.mjs [--w 1280]
import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';
const wi = process.argv.indexOf('--w'); const width = wi > 0 ? Number(process.argv[wi + 1]) : 1280;
const fx = await seed({ port: 19191 });
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
try {
  const browser = await chromium.launch(); const sessions = {};
  for (const r of ['admin', 'alice', 'bob']) { const s = new Session(B.base); await s.login(r === 'admin' ? ADMIN.loginId : r); sessions[r] = s; }
  const rep = [];
  for (const sc of screens(fx)) {
    const role = sc.roles[0];
    const ctx = await browser.newContext({ viewport: { width, height: 800 } });
    if (role !== 'anon') await ctx.addCookies(sessions[role].playwrightCookies());
    await ctx.route((u) => u.origin !== B.base, (r) => r.abort());
    const page = await ctx.newPage();
    try {
      await page.goto(B.base + sc.path, { waitUntil: 'load', timeout: 30000 });
      await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
      const found = await page.evaluate(() => {
        const out = [];
        const sig = (e) => e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + '.' + [...e.classList].slice(0, 3).join('.');
        document.querySelectorAll('.row').forEach((row) => {
          const cols = [...row.children].filter((c) => [...c.classList].some((k) => /^col-lg-\d+$/.test(k)) && getComputedStyle(c).display !== 'none');
          if (!cols.length) return;
          if (getComputedStyle(row).display !== 'flex') out.push(`row가 flex가 아님(${getComputedStyle(row).display}) ${sig(row)}`);
          const span = cols.reduce((a, c) => a + Number([...c.classList].find((k) => /^col-lg-\d+$/.test(k)).split('-')[2]), 0);
          const r0 = cols[0].getBoundingClientRect(); const wrapped = cols.slice(1).some((c) => c.getBoundingClientRect().top >= r0.bottom - 1 && c.getBoundingClientRect().left <= r0.left + 2);
          if (span <= 12 && wrapped && row.getBoundingClientRect().width > 700) out.push(`줄 넘김 ${sig(row)} 열폭합 ${span} 열 ${cols.map(sig).join(' | ').slice(0, 140)}`);
          cols.forEach((c) => {
            const n = Number([...c.classList].find((k) => /^col-lg-\d+$/.test(k)).split('-')[2]);
            const want = row.getBoundingClientRect().width * n / 12; const got = c.getBoundingClientRect().width;
            if (Math.abs(want - got) > want * 0.03 + 12 && row.getBoundingClientRect().width > 700 && span <= 12) out.push(`폭 다름 ${sig(c)} 기대 ${Math.round(want)} 실제 ${Math.round(got)}`);
          });
        });
        return out;
      });
      if (found.length) rep.push({ id: sc.id, role, found });
    } catch (e) { rep.push({ id: sc.id, role, found: ['오류 ' + String(e.message).slice(0, 80)] }); }
    await ctx.close();
  }
  await browser.close();
  for (const r of rep) { console.log(`## ${r.id} (${r.role})`); r.found.slice(0, 6).forEach((f) => console.log('  ' + f)); }
  console.log(`문제 화면 ${rep.length}개`);
} finally { await B.stop(); }
