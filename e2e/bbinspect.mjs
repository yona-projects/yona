// 기준 빌드와 현재 빌드에서 같은 요소(경로)의 상자 속성을 나란히 보여 준다. 사용: node bbinspect.mjs <화면 id> <경로> [--role admin] [--depth 1]
import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';
const [id, path] = process.argv.slice(2);
const arg = (k, d) => { const i = process.argv.indexOf(k); return i > 0 ? process.argv[i + 1] : d; };
const role = arg('--role', 'admin'); const depth = Number(arg('--depth', '1'));
const fx = await seed({ port: 19191 });
const sc = screens(fx).find((s) => s.id === id);
const A = await startServer({ port: 19291, dataDir: join(ROOT, '.state', 'server-base'), jar: join(ROOT, '.state', 'baseline', 'yona-pre-bs5.jar') });
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
const fn = ({ path, depth }) => {
  let el = document.body;
  for (const seg of path.split('/').slice(1)) { const m = /^(.*?)(\d+)$/.exec(seg); el = el.children[Number(m[2])]; if (!el) return ['없음: ' + seg]; }
  const out = [];
  const row = (e, d) => {
    const c = getComputedStyle(e), r = e.getBoundingClientRect();
    out.push('  '.repeat(d) + `${e.tagName.toLowerCase()}.${String(e.className).split(' ').slice(0, 2).join('.')} rect ${Math.round(r.width * 10) / 10}x${Math.round(r.height * 10) / 10} ${c.boxSizing} disp=${c.display} h=${c.height} minh=${c.minHeight} pad=${c.paddingTop}/${c.paddingBottom} bd=${c.borderTopWidth}/${c.borderBottomWidth} mg=${c.marginTop}/${c.marginBottom} lh=${c.lineHeight}`);
    if (d < depth) for (const k of e.children) row(k, d + 1);
  };
  row(el, 0); return out;
};
try {
  const browser = await chromium.launch();
  for (const [name, server] of [['기준', A], ['현재', B]]) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'en-US' });
    if (role !== 'anon') { const s = new Session(server.base); await s.login(role === 'admin' ? ADMIN.loginId : role); await ctx.addCookies(s.playwrightCookies()); }
    await ctx.route((u) => u.origin !== server.base, (r) => r.abort());
    const page = await ctx.newPage(); await page.goto(server.base + sc.path, { waitUntil: 'load' }); await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
    console.log(`[${name}]`); for (const l of await page.evaluate(fn, { path, depth })) console.log(l);
    await ctx.close();
  }
  await browser.close();
} finally { await A.stop(); await B.stop(); }
