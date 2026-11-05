// v1.6(공식 1.16.0 릴리스, 19200)과 v2(19300)의 같은 화면을 같은 데이터로 찍어 나란히 놓는다. 선생님 요청으로 한 번 비교한다.
import { join } from 'node:path';
import { chromium } from 'playwright';

const BASES = { v16: 'http://127.0.0.1:19200', v2: 'http://127.0.0.1:19300' };
const OUT = join(process.cwd(), '..', 'shots', 'v16-vs-v2');
const PASS = { tester: 'pass1234' };
const PAGES = [
  ['01-login', 'anon', '/users/loginform', 1280],
  ['02-project-home', 'tester', '/tester/proj1', 1280],
  ['03-issue-list', 'tester', '/tester/proj1/issues?state=open', 1280],
  ['04-issue-view', 'tester', '/tester/proj1/issue/1', 1280],
  ['05-new-issue', 'tester', '/tester/proj1/newIssueForm', 1280],
  ['06-code', 'tester', '/tester/proj1/code', 1280],
  ['07-pull-requests', 'tester', '/tester/proj1/pullRequests', 1280],
  ['08-settings', 'tester', '/tester/proj1/settingform', 1280],
  ['09-user-page', 'tester', '/tester', 1280],
  ['10-project-home-narrow', 'tester', '/tester/proj1', 800],
];
const browser = await chromium.launch();
async function shoot(side, actor, path, width, file) {
  const base = BASES[side];
  const ctx = await browser.newContext({ locale: 'ko-KR', viewport: { width, height: 900 } });
  if (actor !== 'anon') {
    await ctx.request.get(base + '/users/loginform');
    const xsrf = (await ctx.cookies(base)).find((c) => c.name === 'XSRF-TOKEN')?.value;
    const r = await ctx.request.post(base + '/users/login', { form: { loginIdOrEmail: actor, password: PASS[actor] }, headers: xsrf ? { 'X-XSRF-TOKEN': xsrf } : {}, maxRedirects: 0 });
    if (![302, 303].includes(r.status())) throw new Error(`로그인 실패 ${side} ${r.status()}`);
  }
  const page = await ctx.newPage();
  await page.goto(base + path, { waitUntil: 'load' });
  await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});
  await page.screenshot({ path: join(OUT, `${file}-${side}.png`), fullPage: false });
  await ctx.close();
}
try {
  for (const [name, actor, path, width] of PAGES) {
    for (const side of ['v16', 'v2']) await shoot(side, actor, path, width, name);
    // 나란히 놓는다
    const ctx = await browser.newContext({ viewport: { width: width === 800 ? 1660 : 2620, height: 960 } });
    const p = await ctx.newPage();
    const img = (s) => 'file://' + join(OUT, `${name}-${s}.png`);
    const htmlFile = join(OUT, `${name}.html`);
    (await import('node:fs')).writeFileSync(htmlFile, `<body style="margin:0;background:#888;display:flex;gap:20px;padding:10px;font:bold 18px sans-serif"><div><div style="color:#fff;padding:4px">v1.6 (공식 1.16.0)</div><img src="${img('v16')}"></div><div><div style="color:#fff;padding:4px">v2 </div><img src="${img('v2')}"></div></body>`);
    await p.goto('file://' + htmlFile);
    await p.waitForLoadState('load');
    await p.waitForTimeout(300);
    await p.screenshot({ path: join(OUT, `${name}.png`) });
    await ctx.close();
    console.log('찍음', name);
  }
} finally { await browser.close(); }
