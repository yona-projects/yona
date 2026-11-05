// C053 확인: `#cb-` 앵커가 붙은 폴더 주소로 들어갈 때 이전 JS(HEAD)와 현재 JS의 동작을 같은 서버에서 나란히 본다.
import { execFileSync } from 'node:child_process';
import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';

const REL = 'src/main/resources/static/assets/javascripts/service/yobi.code.Browser.js';
const oldJs = execFileSync('git', ['show', `HEAD:${REL}`], { cwd: join(ROOT, '..'), encoding: 'utf8' });
const fx = await seed({ port: 19191 });
const P = `/${fx.project.owner}/${fx.project.name}`;
const S = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
const browser = await chromium.launch();
const results = [];
try {
  for (const [label, useOld] of [['이전 JS(HEAD)', true], ['현재 JS', false]]) {
    for (const [name, url] of [['앵커 없음', `${P}/code/master/src`], ['#cb-src 앵커', `${P}/code/master/src#cb-src`], ['#src 해시', `${P}/code/master/src#src`]]) {
      const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'en-US' });
      const s = new Session(S.base); await s.login(ADMIN.loginId); await ctx.addCookies(s.playwrightCookies());
      if (useOld) await ctx.route('**/yobi.code.Browser.js*', (r) => r.fulfill({ contentType: 'application/javascript', body: oldJs }));
      const page = await ctx.newPage(); const meta = []; const errs = [];
      page.on('request', (q) => { if (q.url().includes('/code/master/!/')) meta.push(new URL(q.url()).pathname.replace(P, '')); });
      page.on('pageerror', (e) => errs.push(String(e.message).slice(0, 120)));
      await page.goto(S.base + url, { waitUntil: 'load' }); await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
      const st = await page.evaluate(() => ({ crumbs: $('#breadcrumbs a').map((i, a) => $(a).text()).get().join(' / '), listVisible: $('div.list-wrap[data-listpath]').toArray().map((e) => $(e).css('display')) }));
      results.push({ 스크립트: label, 주소: name, 목록요청: meta, 이동경로: st.crumbs, 목록display: st.listVisible, 오류: errs });
      if (!useOld && name === '#cb-src 앵커') await page.screenshot({ path: join(ROOT, '..', 'shots', 'c053-anchor-after.png') });
      if (useOld && name === '#cb-src 앵커') await page.screenshot({ path: join(ROOT, '..', 'shots', 'c053-anchor-before.png') });
      await ctx.close();
    }
  }
  for (const r of results) console.log(JSON.stringify(r));
} finally { await browser.close(); await S.stop(); }
