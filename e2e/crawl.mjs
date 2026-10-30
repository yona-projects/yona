// 전 화면 순회 캡처(로드맵 X-38a). screens.mjs의 모든 화면을 역할별로 열어 캡처하고 콘솔 오류, 깨진 자원, 상태 코드를 기록한다.
// 사용: node crawl.mjs --label <이름> [--only <id 일부>] [--base http://localhost:19191]
// 서버가 없으면 띄운다(고정 데이터가 없으면 먼저 심는다). 결과: shots/<이름>/<id>.<역할>.png, report.json, index.html(모음 이미지).
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
import { screens } from './screens.mjs';
import { interact } from './lib/interact.mjs';

const arg = (name, fallback) => { const i = process.argv.indexOf(`--${name}`); return i > 0 ? process.argv[i + 1] : fallback; };
const label = arg('label', 'current');
const only = arg('only', '');
const withInteract = process.argv.includes('--interact');
const PORT = Number(process.env.E2E_PORT || 19191);
const out = join(ROOT, 'shots', label);
mkdirSync(out, { recursive: true });

const fx = await seed({ port: PORT });
const server = await startServer({ port: PORT });
const base = server.base;
const results = [];
try {
  const sessions = {};
  for (const role of ['admin', 'alice', 'bob']) { const s = new Session(base); await s.login(role === 'admin' ? ADMIN.loginId : role); sessions[role] = s; }
  const browser = await chromium.launch();
  const contexts = {};
  for (const role of ['anon', 'admin', 'alice', 'bob']) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'en-US' });
    if (role !== 'anon') await ctx.addCookies(sessions[role].playwrightCookies());
    // 외부 주소(분석 스크립트 등)는 막는다: 네트워크에 따라 결과가 달라지지 않게 한다
    await ctx.route((url) => url.origin !== base, (route) => route.abort());
    contexts[role] = ctx;
  }
  for (const sc of screens(fx)) {
    if (only && !sc.id.includes(only)) continue;
    for (const role of sc.roles) {
      const page = await contexts[role].newPage();
      const problems = { console: [], pageerrors: [], failed: [], http: [] };
      // 막아 둔 외부 주소의 실패는 세지 않는다(같은 출처에서 난 오류만 기록한다)
      page.on('console', (m) => { if (m.type() === 'error' && (!m.location().url || m.location().url.startsWith(base)) && !(sc.expect !== 200 && m.text().includes(`status of ${sc.expect}`))) problems.console.push(m.text().slice(0, 300)); });
      page.on('pageerror', (e) => problems.pageerrors.push(String(e.message).slice(0, 300)));
      page.on('requestfailed', (r) => { if (r.url().startsWith(base) && r.failure()?.errorText !== 'net::ERR_ABORTED') problems.failed.push(`${r.method()} ${r.url().replace(base, '')} ${r.failure()?.errorText}`); });
      page.on('response', (r) => { if (r.url().startsWith(base) && r.status() >= 400 && r.request().resourceType() !== 'document') problems.http.push(`${r.status()} ${r.url().replace(base, '')}`); });
      let status = null, error = null, finalUrl = null, inter = null;
      try {
        const res = await page.goto(base + sc.path, { waitUntil: 'load', timeout: 30000 });
        status = res?.status() ?? null;
        await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});
        finalUrl = page.url().replace(base, '');
        await page.screenshot({ path: join(out, `${sc.id}.${role}.png`), fullPage: true });
        if (withInteract && status === sc.expect) inter = await interact(page, base, sc.path);
      } catch (e) { error = String(e.message).slice(0, 300); }
      // 알려진 오류(v1.6에서 온 것 등)는 목록의 knownErrors에 적은 글이 들어 있으면 기록만 하고 실패로 세지 않는다
      const known = (m) => (sc.knownErrors || []).some((k) => m.includes(k));
      problems.known = [...problems.console, ...problems.pageerrors].filter(known);
      problems.console = problems.console.filter((m) => !known(m)); problems.pageerrors = problems.pageerrors.filter((m) => !known(m));
      const interactBad = inter ? inter.failed.length + inter.errors.filter((m) => !known(m)).length : 0;
      const ok = !error && status === sc.expect && !problems.console.length && !problems.pageerrors.length && !problems.failed.length && !problems.http.length && !interactBad;
      results.push({ id: sc.id, role, path: sc.path, finalUrl, expect: sc.expect, status, error, ok, note: sc.note || '', interact: inter, ...problems });
      await page.close();
    }
  }
  await browser.close();
} finally { await server.stop(); }

// --only로 일부만 다시 돌린 경우에는 같은 이름의 기존 결과에 합쳐 넣는다(바뀐 화면만 교체한다)
let merged = results;
if (only && existsSync(join(out, 'report.json'))) {
  const previous = JSON.parse(readFileSync(join(out, 'report.json'), 'utf8')).results;
  const fresh = new Map(results.map((r) => [`${r.id}.${r.role}`, r]));
  merged = previous.map((r) => fresh.get(`${r.id}.${r.role}`) ?? r);
  for (const r of results) if (!previous.some((p) => p.id === r.id && p.role === r.role)) merged.push(r);
}
results.splice(0, results.length, ...merged);
writeFileSync(join(out, 'report.json'), JSON.stringify({ label, generatedAt: new Date().toISOString(), results }, null, 2));
const esc = (s) => String(s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
const cards = results.map((r) => `<div class="c ${r.ok ? 'ok' : 'bad'}"><a href="${r.id}.${r.role}.png"><img loading="lazy" src="${r.id}.${r.role}.png"></a><div><b>${esc(r.id)}</b> <i>${r.role}</i> ${r.status ?? ''}${r.status !== r.expect ? ` (기대 ${r.expect})` : ''}${r.interact ? ` · 트리거 ${r.interact.opened}/${r.interact.attempted}` : ''}<br><small>${esc(r.path)}</small>${[...r.console, ...r.pageerrors, ...r.failed, ...r.http, ...(r.interact ? r.interact.failed.map((f) => `[${f.type}] ${f.text}: ${f.reason}`) : []), ...(r.error ? [r.error] : [])].map((p) => `<br><span class="p">${esc(p)}</span>`).join('')}</div></div>`).join('');
writeFileSync(join(out, 'index.html'), `<!doctype html><meta charset="utf-8"><title>${label}</title><style>body{font:13px sans-serif;margin:8px}.c{display:inline-block;vertical-align:top;width:300px;margin:4px;border:2px solid #4a4;padding:4px}.c.bad{border-color:#c33}.c img{width:100%;max-height:260px;object-fit:cover;object-position:top}.p{color:#c33;font-size:11px}</style><h3>${label}: ${results.filter((r) => r.ok).length}/${results.length} 통과</h3>${cards}`);
const bad = results.filter((r) => !r.ok);
console.log(`${label}: ${results.length - bad.length}/${results.length} 통과`);
for (const r of bad) console.log(`- ${r.id}.${r.role} 상태 ${r.status}(기대 ${r.expect}) ${[...r.console, ...r.pageerrors, ...r.failed, ...r.http, ...(r.interact ? r.interact.failed.map((f) => `[${f.type}] ${f.text}: ${f.reason}`) : []), ...(r.error ? [r.error] : [])].slice(0, 2).join(' | ')}`);
