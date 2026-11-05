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
  const vis = (e) => { const cs = getComputedStyle(e); if (cs.display === 'none' || cs.visibility === 'hidden') return false; const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const sig = (e) => `${e.tagName.toLowerCase()}${e.id ? '#' + e.id : ''}.${(typeof e.className === 'string' ? e.className : '').trim().split(/\s+/).slice(0, 3).join('.')}|${(e.innerText || e.value || '').trim().replace(/\s+/g, ' ').slice(0, 24)}`;
  const clipped = [], beyond = [];
  const items = [...document.querySelectorAll('body *')].filter((e) => !['script', 'style', 'option', 'optgroup', 'path', 'svg', 'br', 'html', 'head'].includes(e.tagName.toLowerCase()) && vis(e));
  for (const e of items) {
    const cs = getComputedStyle(e); if (cs.position === 'fixed') continue;
    if (/select2-offscreen|select2-drop|select2-focusser|tooltip|popover|dropdown-menu|modal/.test(typeof e.className === 'string' ? e.className : '')) continue;
    const leafish = e.children.length === 0 || ['a', 'button', 'input', 'select', 'strong', 'span', 'label', 'td', 'th', 'li'].includes(e.tagName.toLowerCase());
    if (!leafish) continue;
    const r = e.getBoundingClientRect();
    // (1) 잘림: overflow가 보이지 않는 조상 밖으로 나간 요소
    for (let a = e.parentElement; a && a !== document.body; a = a.parentElement) {
      const acs = getComputedStyle(a); if (acs.overflowX === 'visible') continue;
      if (acs.position === 'fixed') break;
      const ar = a.getBoundingClientRect();
      if ((r.right > ar.right + 1.5 || r.left < ar.left - 1.5) && a.scrollWidth <= a.clientWidth + 1) { clipped.push(`${sig(e)} @ ${sig(a)} [${Math.round(r.left)},${Math.round(r.right)}] in [${Math.round(ar.left)},${Math.round(ar.right)}]`); break; }
      if (r.right > ar.right + 1.5 || r.left < ar.left - 1.5) { clipped.push(`${sig(e)} @ ${sig(a)} (스크롤) [${Math.round(r.left)},${Math.round(r.right)}] in [${Math.round(ar.left)},${Math.round(ar.right)}]`); break; }
    }
    // (2) 내용 영역(10~1270) 밖
    if (cs.position !== 'absolute' || true) { if (r.right > 1270.5 || r.left < 9.5) beyond.push(`${sig(e)} [${Math.round(r.left)},${Math.round(r.right)}]`); }
  }
  return { clipped: [...new Set(clipped)].slice(0, 12), beyond: [...new Set(beyond)].slice(0, 12) };
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
