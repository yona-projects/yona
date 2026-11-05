import fs from 'node:fs';
import { join } from 'node:path';
import { chromium } from 'playwright';
const B = { v16: 'http://127.0.0.1:19200', v2: 'http://127.0.0.1:19300' };
const OUT = join(process.cwd(), '..', 'shots', 'v16-vs-v2');
const b = await chromium.launch();
for (const [side, base] of Object.entries(B)) {
  const ctx = await b.newContext({ locale: 'ko-KR', viewport: { width: 1280, height: 700 } });
  await ctx.request.get(base + '/users/loginform');
  const x = (await ctx.cookies(base)).find((c) => c.name === 'XSRF-TOKEN')?.value;
  await ctx.request.post(base + '/users/login', { form: { loginIdOrEmail: 'tester', password: 'pass1234' }, headers: x ? { 'X-XSRF-TOKEN': x } : {}, maxRedirects: 0 });
  const p = await ctx.newPage();
  await p.goto(base + '/tester/proj1', { waitUntil: 'load' });
  await p.waitForTimeout(800);
  await p.click('#sidebar-open-btn').catch((e) => console.log(side, '클릭 실패', e.message.slice(0, 60)));
  await p.waitForTimeout(900);
  const info = await p.evaluate(() => { const s = document.querySelector('#mySidenav'); const r = s.getBoundingClientRect(); const kids = [...s.querySelectorAll('.right-menu, .user-menu-wrap, .nav-tabs, .tab-content')].map((e) => { const q = e.getBoundingClientRect(); return `${e.className.split(' ')[0]} [${Math.round(q.left)},${Math.round(q.right)}]`; }); return { side: [Math.round(r.left), Math.round(r.right), Math.round(r.width)], kids }; });
  console.log(side, JSON.stringify(info));
  await p.screenshot({ path: join(OUT, `sidenav-${side}.png`), clip: { x: 840, y: 0, width: 440, height: 520 } });
  await ctx.close();
}
await b.close();
