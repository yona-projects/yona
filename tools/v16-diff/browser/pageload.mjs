// 실제 브라우저(Playwright)로 같은 화면을 두 서버에서 열어, 자바스크립트가 만든 요청(XHR/fetch), 콘솔 오류, 최종 화면 글자를 견준다.
// 사용: node pageload.mjs <페이지목록.json> <출력.json> [행위자,...]
// 페이지목록: [{"actor":"anon","path":"/..."}...]. 로그인은 API로 한 번 해서 쿠키만 컨텍스트에 넣는다.
import { createRequire } from 'node:module';
import fs from 'node:fs';
const require = createRequire('/home/jiho/yona-convert/yona/e2e/');
const { chromium } = require('playwright');

const BASES = { 'v1.6': 'http://127.0.0.1:19200', v2: 'http://127.0.0.1:19300' };
const [listFile, outFile, actorsArg] = process.argv.slice(2);
const list = JSON.parse(fs.readFileSync(listFile, 'utf8'));
const actors = (actorsArg || 'anon,tester,admin').split(',');
const PASS = { admin: 'admin1234' };

const norm = (s) => s
  .replace(/\d+\s*(초|분|시간|일|주|개월|년)\s*전/g, '<시각>').replace(/\d+\s*(seconds?|minutes?|hours?|days?|weeks?|months?|years?) ago/g, '<시각>')
  .replace(/\b(a|an) (second|minute|hour|day|week|month|year) ago/g, '<시각>').replace(/\s+/g, ' ').trim();

async function openContext(browser, base, actor) {
  const ctx = await browser.newContext({ locale: 'en-US', viewport: { width: 1280, height: 900 } });
  if (actor !== 'anon') {
    const r = await ctx.request.post(base + '/users/login', { form: { loginIdOrEmail: actor, password: PASS[actor] || 'pass1234' }, maxRedirects: 0 });
    if (![302, 303].includes(r.status())) throw new Error(`로그인 실패 ${actor} ${r.status()}`);
  }
  return ctx;
}

async function visit(ctx, base, path) {
  const page = await ctx.newPage();
  const reqs = [], errs = [];
  page.on('response', (res) => {
    const rq = res.request();
    const t = rq.resourceType();
    if (t === 'xhr' || t === 'fetch') reqs.push(`${rq.method()} ${new URL(res.url()).pathname}${new URL(res.url()).search} ${res.status()}`);
    if (t === 'document' && rq.isNavigationRequest() && res.status() >= 300 && res.status() < 400) reqs.push(`NAV ${res.status()} ${new URL(res.url()).pathname}`);
  });
  page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().replace(base, '<서버>').replace(/:\d+:\d+/g, '')); });
  page.on('pageerror', (e) => errs.push('PAGEERROR ' + String(e.message).slice(0, 200)));
  page.on('dialog', (d) => d.dismiss().catch(() => {}));
  let status = null, title = null, text = null, finalUrl = null;
  try {
    const resp = await page.goto(base + path, { waitUntil: 'load', timeout: 30000 });
    status = resp ? resp.status() : null;
    await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});
    await page.waitForTimeout(300);
    title = await page.title();
    text = norm(await page.evaluate(() => document.body ? document.body.innerText : ''));
    finalUrl = page.url().replace(base, '<서버>');
  } catch (e) { errs.push('GOTO ' + String(e.message).slice(0, 120)); }
  await page.close();
  return { status, title, finalUrl, reqs: reqs.sort(), errs: errs.sort(), text };
}

const browser = await chromium.launch();
const out = {};
for (const [name, base] of Object.entries(BASES)) {
  out[name] = {};
  for (const actor of actors) {
    const ctx = await openContext(browser, base, actor);
    for (const item of list.filter((i) => i.actor === actor)) {
      out[name][`${actor} ${item.path}`] = await visit(ctx, base, item.path);
    }
    await ctx.close();
  }
}
await browser.close();
fs.writeFileSync(outFile, JSON.stringify(out));
let same = 0, diff = 0;
const lines = [];
for (const key of Object.keys(out['v1.6'])) {
  const a = out['v1.6'][key], b = out.v2[key];
  const problems = [];
  for (const f of ['status', 'title', 'finalUrl']) if (a[f] !== b[f]) problems.push(`${f}: ${a[f]} | ${b[f]}`);
  if (JSON.stringify(a.reqs) !== JSON.stringify(b.reqs)) problems.push(`요청: v1.6=${JSON.stringify(a.reqs)} v2=${JSON.stringify(b.reqs)}`);
  if (JSON.stringify(a.errs) !== JSON.stringify(b.errs)) problems.push(`콘솔: v1.6=${JSON.stringify(a.errs)} v2=${JSON.stringify(b.errs)}`);
  if (a.text !== b.text) {
    let i = 0; while (i < a.text.length && a.text[i] === b.text[i]) i++;
    problems.push(`글자 첫 차이 @${i}: v1.6=…${JSON.stringify(a.text.slice(Math.max(0, i - 30), i + 60))} v2=…${JSON.stringify(b.text.slice(Math.max(0, i - 30), i + 60))}`);
  }
  if (problems.length) { diff++; lines.push(`## ${key}\n  ${problems.join('\n  ')}`); } else same++;
}
console.log(`브라우저 화면 대조: 같음 ${same}, 다름 ${diff} (전체 ${same + diff})`);
fs.writeFileSync(outFile.replace(/\.json$/, '.md'), lines.join('\n') + '\n');
