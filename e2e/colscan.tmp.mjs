// `.row` 점검: v1.6과 v2에서 같은 화면을 열어 (1) 부모의 overflow에 잘리는 요소, (2) 페이지 내용 영역(좌우 10px 여백 안쪽) 밖으로 나간 요소를 모은다.
// v2에만 있는 것이 이식 중 생긴 결함 후보이다. 사용: node rowscan.tmp.mjs <pages.json> <출력.json>
import fs from 'node:fs';
import { chromium } from 'playwright';

const B = { v16: 'http://127.0.0.1:19200', v2: 'http://127.0.0.1:19300' };
const [listFile, outFile] = process.argv.slice(2);
const PASS = { admin: 'admin1234', tester: 'pass1234' };
const all = JSON.parse(fs.readFileSync(listFile, 'utf8'));
const seen = new Set();
const list = all.filter((x) => {
  if (/format=json|\/-_-api|\.json|\/raw|rawcode|\/download|\/delete|logout|\/svn\/|\.git/.test(x.path)) return false;
  const key = x.path; if (seen.has(key)) return false;
  const ok = x.actor === 'tester' || (x.actor === 'admin' && /\/sites|admin/.test(x.path)) || (x.actor === 'anon' && /^\/(users|$|help|_help)/.test(x.path));
  if (!ok) return false; seen.add(key); return true;
});
console.error('대상', list.length);

const scanFn = () => {
  // 그리드 열(v1.6 `spanN`, v2 `col-*`)을 grid 외의 클래스 이름으로 짝지어 폭과 왼쪽 위치를 잰다
  const out = {};
  for (const e of document.querySelectorAll('[class*="span"], [class*="col-"]')) {
    const cls = (typeof e.className === 'string' ? e.className : '').trim().split(/\s+/);
    if (!cls.some((c) => /^span\d+$|^col-(lg|md|sm|xs)?-?\d+$/.test(c))) continue;
    const others = cls.filter((c) => !/^span\d+$|^col-|^offset|^hide-in|^pull|^float|^d-|^align|^mb-|^mt-|^ms-|^me-|^ps-|^pe-|^p-|^m-/.test(c));
    if (!others.length) continue;
    const cs = getComputedStyle(e); if (cs.display === 'none') continue;
    const r = e.getBoundingClientRect(); if (r.width === 0) continue;
    const key = others.join('.');
    if (!(key in out)) out[key] = [Math.round(r.left), Math.round(r.width)];
  }
  return { cols: out };
};

const b = await chromium.launch();
const ctxs = {};
async function ctxFor(side, actor) {
  const k = `${side}/${actor}`; if (ctxs[k]) return ctxs[k];
  const base = B[side];
  const ctx = await b.newContext({ locale: 'ko-KR', viewport: { width: 1280, height: 900 } });
  if (actor !== 'anon') {
    await ctx.request.get(base + '/users/loginform');
    const x = (await ctx.cookies(base)).find((c) => c.name === 'XSRF-TOKEN')?.value;
    const r = await ctx.request.post(base + '/users/login', { form: { loginIdOrEmail: actor, password: PASS[actor] }, headers: x ? { 'X-XSRF-TOKEN': x } : {}, maxRedirects: 0 });
    if (![302, 303].includes(r.status())) throw new Error(`로그인 실패 ${k} ${r.status()}`);
  }
  return (ctxs[k] = ctx);
}
const result = [];
let n = 0;
for (const { actor, path } of list) {
  const row = { actor, path };
  for (const side of ['v16', 'v2']) {
    try {
      const page = await (await ctxFor(side, actor)).newPage();
      page.on('dialog', (d) => d.dismiss().catch(() => {}));
      const res = await page.goto(B[side] + path, { waitUntil: 'load', timeout: 20000 });
      await page.waitForTimeout(350);
      row[side] = { status: res?.status(), ...(await page.evaluate(scanFn)) };
      await page.close();
    } catch (e) { row[side] = { error: String(e.message).slice(0, 80) }; }
  }
  result.push(row);
  if (++n % 25 === 0) console.error(`${n}/${list.length}`);
}
await b.close();
fs.writeFileSync(outFile, JSON.stringify(result, null, 1));
