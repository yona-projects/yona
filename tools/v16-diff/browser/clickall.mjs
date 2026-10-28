// 화면마다 눈에 보이는 상호작용 요소(링크, 단추, 토글, 체크박스, 선택 상자)를 하나씩 눌러 보고 두 서버의 반응을 견준다.
// 반응: 자바스크립트가 보낸 요청(XHR/fetch)과 이동, 주소, 화면 글자 길이와 지문, 콘솔 오류, 열린 대화상자.
// 사용: node clickall.mjs <페이지목록.json> <출력.json> [행위자,...] [화면당 최대 요소 수]
import { createRequire } from 'node:module';
import fs from 'node:fs';
import crypto from 'node:crypto';
const require = createRequire('/home/jiho/yona-convert/yona/e2e/');
const { chromium } = require('playwright');

const BASES = { 'v1.6': 'http://127.0.0.1:19200', v2: 'http://127.0.0.1:19300' };
const [listFile, outFile, actorsArg, capArg] = process.argv.slice(2);
const list = JSON.parse(fs.readFileSync(listFile, 'utf8'));
const actors = (actorsArg || 'anon,tester').split(',');
const CAP = parseInt(capArg || '40', 10);
const PASS = { admin: 'admin1234' };
const SKIP = /delete|remove|삭제|logout|로그아웃|leave|탈퇴|unwatch|reset ?password|transfer|이전|lock|잠금|destroy|withdraw|merge|병합|cancel|취소/i;
const norm = (s) => s.replace(/\d+\s*(초|분|시간|일|주|개월|년)\s*전/g, '<시각>').replace(/\d+\s*(seconds?|minutes?|hours?|days?|weeks?|months?|years?) ago/g, '<시각>')
  .replace(/\b(a|an) (second|minute|hour|day|week|month|year) ago/g, '<시각>').replace(/\s+/g, ' ').trim();
const hash = (s) => crypto.createHash('sha1').update(s).digest('hex').slice(0, 10);

async function openContext(browser, base, actor) {
  const ctx = await browser.newContext({ locale: 'en-US', viewport: { width: 1280, height: 900 } });
  if (actor !== 'anon') {
    const r = await ctx.request.post(base + '/users/login', { form: { loginIdOrEmail: actor, password: PASS[actor] || 'pass1234' }, maxRedirects: 0 });
    if (![302, 303].includes(r.status())) throw new Error('로그인 실패 ' + actor);
  }
  return ctx;
}

const CANDIDATES = `
(() => {
  const sel = 'a[href], button, [data-toggle], input[type=checkbox], input[type=radio], select, .btn, .ybtn, [role=tab], summary, .dropdown-toggle';
  const out = []; const seenHref = new Set();
  document.querySelectorAll(sel).forEach((el, idx) => {
    if (!(el.offsetWidth || el.offsetHeight || el.getClientRects().length)) return;
    const href = el.getAttribute('href');
    if (el.tagName === 'A') {
      if (!href || href.startsWith('http') || href.startsWith('mailto') || href.startsWith('javascript') || seenHref.has(href)) return;
      seenHref.add(href);
    }
    const desc = [el.tagName.toLowerCase(), el.id ? '#' + el.id : '', (el.className && typeof el.className === 'string') ? '.' + el.className.trim().split(/\\s+/).slice(0, 2).join('.') : '',
      (el.innerText || el.value || '').trim().slice(0, 30), href || '', el.getAttribute('data-request-uri') || '', el.getAttribute('data-toggle') || ''].join('|');
    out.push({ idx, desc });
  });
  return out;
})()`;

async function observe(ctx, base, path, pick) {
  const page = await ctx.newPage();
  const reqs = [], errs = [];
  page.on('response', (res) => {
    const rq = res.request(); const t = rq.resourceType();
    if (t === 'xhr' || t === 'fetch') reqs.push(`${rq.method()} ${new URL(res.url()).pathname}${new URL(res.url()).search} ${res.status()}`);
    if (t === 'document' && rq.isNavigationRequest()) reqs.push(`NAV ${res.status()} ${new URL(res.url()).pathname}`);
  });
  page.on('console', (m) => { if (m.type() === 'error') errs.push(m.text().replace(base, '<서버>').replace(/:\d+:\d+/g, '').slice(0, 160)); });
  page.on('pageerror', (e) => errs.push('PAGEERROR ' + String(e.message).slice(0, 160)));
  const dialogs = [];
  page.on('dialog', async (d) => { dialogs.push(d.type() + ':' + d.message().slice(0, 60)); await d.dismiss().catch(() => {}); });
  let result = null; let cands = [];
  try {
    await page.goto(base + path, { waitUntil: 'load', timeout: 30000 });
    await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
    cands = await page.evaluate(CANDIDATES);
    if (pick !== null && cands[pick]) {
      reqs.length = 0; errs.length = 0;
      const handle = (await page.$$('a[href], button, [data-toggle], input[type=checkbox], input[type=radio], select, .btn, .ybtn, [role=tab], summary, .dropdown-toggle'))[cands[pick].idx];
      const before = norm(await page.evaluate(() => document.body.innerText));
      try {
        const tag = await handle.evaluate((e) => e.tagName);
        if (tag === 'SELECT') { const vals = await handle.evaluate((e) => [...e.options].map((o) => o.value)); if (vals.length > 1) await handle.selectOption(vals[1], { timeout: 2000 }); }
        else await handle.click({ timeout: 2500, noWaitAfter: false });
      } catch (e) { errs.push('CLICK ' + String(e.message).split('\n')[0].slice(0, 80)); }
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
      await page.waitForTimeout(250);
      const after = norm(await page.evaluate(() => document.body ? document.body.innerText : ''));
      const modal = await page.evaluate(() => [...document.querySelectorAll('.modal, .yobiDialog, .toast')].filter((m) => m.offsetWidth).map((m) => (m.id || m.className).toString().slice(0, 40)));
      result = { url: page.url().replace(base, '<서버>'), reqs: reqs.sort(), errs: errs.sort(), dialogs, modal, same: before === after, len: after.length, sig: hash(after) };
    }
  } catch (e) { result = { error: String(e.message).split('\n')[0].slice(0, 100) }; }
  await page.close();
  return { cands, result };
}

const browser = await chromium.launch();
const out = {};
for (const [name, base] of Object.entries(BASES)) {
  out[name] = {};
  for (const actor of actors) {
    const ctx = await openContext(browser, base, actor);
    for (const item of list.filter((i) => i.actor === actor)) {
      const first = await observe(ctx, base, item.path, null);
      const entries = [];
      const todo = first.cands.filter((c) => !SKIP.test(c.desc)).slice(0, CAP);
      for (const c of todo) {
        const k = first.cands.indexOf(c);
        const r = await observe(ctx, base, item.path, k);
        entries.push({ desc: c.desc, ...r.result });
      }
      out[name][`${actor} ${item.path}`] = { count: first.cands.length, entries };
    }
    await ctx.close();
  }
}
await browser.close();
fs.writeFileSync(outFile, JSON.stringify(out));
let same = 0, diff = 0; const lines = [];
for (const key of Object.keys(out['v1.6'])) {
  const a = out['v1.6'][key], b = out.v2[key];
  if (a.count !== b.count) { diff++; lines.push(`## ${key}\n  요소 수: v1.6=${a.count} v2=${b.count}`); continue; }
  const probs = [];
  a.entries.forEach((e, i) => {
    const f = b.entries[i];
    if (!f) { probs.push(`  ${e.desc}: v2 항목 없음`); return; }
    const d = [];
    for (const k of ['url', 'reqs', 'errs', 'dialogs', 'modal', 'same', 'len', 'error']) if (JSON.stringify(e[k]) !== JSON.stringify(f[k])) d.push(`${k}: ${JSON.stringify(e[k])} | ${JSON.stringify(f[k])}`);
    if (d.length) probs.push(`  [${e.desc}] ${d.join(' / ')}`);
  });
  if (probs.length) { diff++; lines.push(`## ${key}\n${probs.join('\n')}`); } else same++;
}
console.log(`누르기 대조: 같은 화면 ${same}, 다른 화면 ${diff} (전체 ${same + diff})`);
fs.writeFileSync(outFile.replace(/\.json$/, '.md'), lines.join('\n') + '\n');
