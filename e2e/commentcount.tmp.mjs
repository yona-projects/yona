// R-41 확인: 파일 보기의 댓글 수가 코드 범위 스레드를 세지 않는 모습을 현재 코드(before)와 고친 코드(after)로 나란히 찍는다.
import { join } from 'node:path';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';

const SC = process.argv[2];
const fx = await seed({ port: 19191 });
const P = `/${fx.project.owner}/${fx.project.name}`;
const dataDir = join(ROOT, '.state', 'server');
const shots = join(ROOT, '..', 'shots');
const browser = await chromium.launch();

async function shot(jar, port, label, make) {
  const S = await startServer({ port, dataDir, jar });
  try {
    const s = new Session(S.base); await s.login(ADMIN.loginId);
    if (make) await make(s);
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 700 }, locale: 'ko-KR' });
    await ctx.addCookies(s.playwrightCookies());
    const page = await ctx.newPage();
    await page.goto(`${S.base}${P}/code/master/src/main.js`, { waitUntil: 'load' });
    const count = await page.locator('.number-of-comments').first().textContent().catch(() => null);
    await page.evaluate(() => { const e = document.querySelector('.number-of-comments'); if (e) e.style.cssText += ';outline:3px solid red;outline-offset:3px'; });
    const box = await page.locator('.number-of-comments').first().boundingBox().catch(() => null);
    const clip = box ? { x: 0, y: Math.max(0, box.y - 120), width: 1280, height: 260 } : undefined;
    await page.screenshot({ path: join(shots, `r41-${label}.png`), clip });
    await ctx.close();
    return (count || '').trim();
  } finally { await S.stop(); }
}

try {
  const make = async (s) => {
    // 파일을 마지막으로 바꾼 커밋을 파일 보기 화면에서 읽는다(시드 기록의 해시가 실제 저장소와 다를 수 있다)
    const html = await (await fetch(`${s.base}${P}/code/master/src/main.js`)).text();
    const commit = html.match(/\/commit\/([0-9a-f]{40})/)[1];
    console.log('파일의 커밋', commit);
    const form = (extra) => new URLSearchParams({ contents: '댓글', ...extra });
    const post = (extra) => fetch(`${s.base}${P}/commit/${commit}/comments`, { method: 'POST', headers: { cookie: [...s.cookies].map(([k, v]) => `${k}=${v}`).join('; '), 'X-XSRF-TOKEN': s.cookies.get('XSRF-TOKEN') }, body: form(extra), redirect: 'manual' }).then((r) => { console.log('댓글 등록', r.status, r.headers.get('location')); return r; });
    // 파일 단위 스레드 1개, 코드 범위 스레드 2개
    await post({});
    for (const n of [1, 3]) await post({ path: 'src/main.js', startLine: String(n), startColumn: '1', startSide: 'B', endLine: String(n + 1), endColumn: '1', endSide: 'B' });
  };
  const before = await shot(join(SC, 'before.jar'), 19295, 'before', make);
  const after = await shot(join(SC, 'after.jar'), 19296, 'after', null);
  console.log(JSON.stringify({ 현재_코드: before, 고친_코드: after }));
} finally { await browser.close(); }
