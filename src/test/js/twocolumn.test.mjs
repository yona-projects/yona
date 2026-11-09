// 2단 보기(`service/yona.twoColumnMode.js`): 켜 두면 목록의 제목 링크를 눌러 오른쪽 패널(iframe)에 그 글을 연다. 패널은 v1.6에서 jquery.pageslide 플러그인이 맡았다.
// 같은 시험을 옛 구현(TC_IMPL=old: 옛 스크립트와 플러그인을 임시 폴더에서 불러온다)에서 먼저 통과시키고 새 구현에서도 통과시킨다. 컴파일된 실제 CSS가 필요하다(`./gradlew compileSass`).
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, JS, ROOT } from './harness.mjs';

const OLD = process.env.CLAUDE_JOB_DIR ? path.join(process.env.CLAUDE_JOB_DIR, 'tmp/old-pageslide') : null;
const IMPL = process.env.TC_IMPL === 'old' ? 'old' : 'new';
const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const SCRIPTS = IMPL === 'old'
  ? [path.join(JS, 'lib/jquery/jquery-3.7.1.js'), path.join(OLD, 'jquery.pageslide.js'), path.join(OLD, 'yona.twoColumnMode.js')]
  : [path.join(JS, 'lib/jquery/jquery-3.7.1.js'), path.join(ROOT, 'src/main/resources/static/assets/bootstrap/js/bootstrap.bundle.min.js'), path.join(JS, 'service/yona.twoColumnMode.js')];
const LIST = `<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/__css/bootstrap.css"><link rel="stylesheet" href="/__css/yobi.css"></head><body>
<div class="project-page-wrap" style="width:1000px"><div class="left-menu" id="lm">메뉴</div><div class="user-info-box" id="ui">사용자</div>
<label><input type="checkbox" id="two-column-mode"> 2단 보기</label>
<ul id="list"><li class="post-item" id="p1"><a class="title" id="t1" href="/issue/1">이슈 1</a></li><li class="post-item" id="p2"><a class="title" id="t2" href="/issue/2">이슈 2</a></li></ul><button id="outside">바깥</button><div style="height:2000px"></div></div></body></html>`;

/** [on]이 참이면 2단 보기를 켠 채로 연다. 목록 페이지는 http 주소에서 열어야 pushState가 된다. */
async function open({ on = true } = {}) {
  const b = await launch(); const ctx = await b.newContext({ viewport: { width: 1280, height: 800 } }); const page = await ctx.newPage(); page.errors = []; page.nav = [];
  page.on('pageerror', (e) => page.errors.push(String(e.message || e)));
  await page.route('http://tc.test/**', (route) => {
    const u = new URL(route.request().url());
    if (u.pathname.startsWith('/__css/')) return route.fulfill({ contentType: 'text/css', body: fs.readFileSync(path.join(CSS, u.pathname.slice(7))) });
    if (u.pathname.startsWith('/issue/')) { page.nav.push(u.pathname); return route.fulfill({ contentType: 'text/html', body: `<!doctype html><body>이슈 ${u.pathname.split('/')[2]} 본문</body>` }); }
    return route.fulfill({ contentType: 'text/html', body: LIST });
  });
  await page.goto('http://tc.test/list');
  await page.evaluate((v) => { window.__np = 0; window.NProgress = { done() { window.__np++; }, start() {} }; localStorage.setItem('useTwoColumnMode', v ? 'true' : 'false'); }, on);
  for (const s of SCRIPTS) await page.addScriptTag({ path: s });
  await page.evaluate(() => window._initTwoColumnMode());
  return { page, ctx };
}
const panel = (page) => page.evaluate(() => {
  const el = document.getElementById('pageslide'); if (!el) return { exists: false };
  const cs = getComputedStyle(el), r = el.getBoundingClientRect(), f = el.querySelector('iframe');
  return { exists: true, shown: cs.display !== 'none' && cs.visibility !== 'hidden' && r.width > 0, position: cs.position, left: Math.round(r.left), width: Math.round(r.width), height: Math.round(r.height), top: Math.round(r.top), z: cs.zIndex, shadow: cs.boxShadow !== 'none', bg: cs.backgroundImage.includes('loading-gif-2'), iframe: f ? f.getAttribute('src') : null };
});
const hidden = (page, id) => page.evaluate((i) => getComputedStyle(document.getElementById(i)).display === 'none', id);

export const tests = [
  { name: '2단 보기: 꺼져 있으면 제목 링크는 그대로 이동하고 패널이 없다', run: async () => {
    const { page, ctx } = await open({ on: false });
    assert.equal(await page.evaluate(() => document.getElementById('two-column-mode').checked), false);
    await Promise.all([page.waitForURL('**/issue/1', { timeout: 5000 }), page.click('#t1')]); assert.equal(page.nav.includes('/issue/1'), true); await ctx.close();
  } },
  { name: '2단 보기: 켜져 있으면 확인 상자가 체크되고 제목 링크는 이동하지 않고 오른쪽 패널에 iframe으로 열린다', run: async () => {
    const { page, ctx } = await open();
    assert.equal(await page.evaluate(() => document.getElementById('two-column-mode').checked), true);
    await page.click('#t1'); await page.waitForTimeout(700);
    assert.equal(page.url(), 'http://tc.test/issue/1', '주소는 pushState로 바뀐다'); assert.deepEqual(page.nav.filter((n) => n === '/issue/1').length, 1, '창 이동이 아니라 iframe 요청 한 번뿐');
    const p = await panel(page); assert.equal(p.shown, true); assert.equal(p.iframe, '/issue/1'); assert.deepEqual([p.position, p.left + p.width, p.width, p.height, p.top], ['fixed', 1280, 640, 800, 0], JSON.stringify(p));
    assert.equal(Number(p.z) >= 1000, true, '다른 요소 위'); assert.deepEqual([p.shadow, p.bg], [true, true], '그림자와 로딩 gif 배경'); assert.equal(await page.evaluate(() => document.getElementById('p1').classList.contains('highlightBg')), true);
    assert.deepEqual([await hidden(page, 'lm'), await hidden(page, 'ui')], [true, true], '열리면 왼쪽 메뉴와 사용자 정보를 숨긴다'); assert.deepEqual(page.errors, []); await ctx.close();
  } },
  { name: '2단 보기: 열자마자(애니메이션 없이) 보이고 iframe에 글이 로드된다', run: async () => {
    const { page, ctx } = await open(); await page.click('#t1'); await page.waitForTimeout(700);
    const frame = page.frames().find((f) => f !== page.mainFrame() && f.url().endsWith('/issue/1')); assert.ok(frame, 'iframe이 읽혔다'); assert.equal(await frame.evaluate(() => document.body.textContent.trim()), '이슈 1 본문'); await ctx.close();
  } },
  { name: '2단 보기: 같은 제목을 다시 누르면 패널이 닫히고 왼쪽 메뉴가 다시 보인다', run: async () => {
    const { page, ctx } = await open(); await page.click('#t1'); await page.waitForTimeout(700); await page.click('#t1', { force: true }); await page.waitForTimeout(500);
    assert.equal((await panel(page)).shown, false); assert.equal(await hidden(page, 'lm'), false, '왼쪽 메뉴 복원'); await ctx.close();
  } },
  { name: '2단 보기: 다른 제목을 누르면 iframe이 바뀌고(옛 iframe은 지워지고 새 주소가 읽힌다) 강조가 옮겨 간다', run: async () => {
    const { page, ctx } = await open(); await page.click('#t1'); await page.waitForTimeout(700);
    await page.evaluate(() => { document.getElementById('t2').click(); }); await page.waitForTimeout(80);
    const during = await panel(page); assert.equal(during.shown, true, '바뀌는 동안에도 패널은 열려 있다'); await page.waitForTimeout(700);
    const after = await panel(page); assert.equal(after.iframe, '/issue/2');
    assert.deepEqual(await page.evaluate(() => [document.getElementById('p1').classList.contains('highlightBg'), document.getElementById('p2').classList.contains('highlightBg')]), [false, true]); assert.equal(page.url(), 'http://tc.test/issue/2'); await ctx.close();
  } },
  { name: '2단 보기: 바깥을 누르거나 Esc를 눌러도 닫히지 않는다(modal)', run: async () => {
    const { page, ctx } = await open(); await page.click('#t1'); await page.waitForTimeout(700);
    await page.click('#outside', { force: true }); await page.keyboard.press('Escape'); await page.waitForTimeout(400); assert.equal((await panel(page)).shown, true); await ctx.close();
  } },
  { name: '2단 보기: 패널이 열려 있어도 바깥 목록 조작과 페이지 스크롤이 된다', run: async () => {
    const { page, ctx } = await open(); await page.click('#t1'); await page.waitForTimeout(700);
    await page.evaluate(() => window.scrollTo(0, 300)); assert.equal(await page.evaluate(() => window.scrollY), 300, '본문 스크롤이 잠기지 않는다');
    await page.evaluate(() => { document.getElementById('t2').click(); }); await page.waitForTimeout(900); assert.equal((await panel(page)).iframe, '/issue/2'); await ctx.close();
  } },
  { name: '2단 보기: 확인 상자를 끄면 패널이 닫히고 제목 링크가 다시 이동하며, 다시 켜면 패널로 열린다(localStorage에 기억)', run: async () => {
    const { page, ctx } = await open(); await page.click('#t1'); await page.waitForTimeout(700);
    await page.evaluate(() => document.getElementById('two-column-mode').click()); await page.waitForTimeout(500);
    assert.equal(await page.evaluate(() => localStorage.getItem('useTwoColumnMode')), 'false'); assert.equal((await panel(page)).shown, false); assert.equal(await hidden(page, 'ui'), false, '사용자 정보 복원');
    await Promise.all([page.waitForURL('**/issue/2', { timeout: 5000 }), page.evaluate(() => document.getElementById('t2').click())]);
    await ctx.close();
    const again = await open(); await again.page.evaluate(() => document.getElementById('two-column-mode').click()); await again.page.waitForTimeout(200);
    assert.equal(await again.page.evaluate(() => localStorage.getItem('useTwoColumnMode')), 'false'); await again.page.evaluate(() => document.getElementById('two-column-mode').click());
    assert.equal(await again.page.evaluate(() => localStorage.getItem('useTwoColumnMode')), 'true'); await again.page.click('#t2'); await again.page.waitForTimeout(700); assert.equal((await again.page.evaluate(() => document.getElementById('pageslide') && getComputedStyle(document.getElementById('pageslide')).display)) !== 'none', true); await again.ctx.close();
  } },
  { name: '2단 보기: 열 때 NProgress.done이 불린다(iframe 로딩 막대 정리)', run: async () => {
    const { page, ctx } = await open(); await page.click('#t1'); await page.waitForTimeout(900); assert.equal(await page.evaluate(() => window.__np) >= 1, true); await ctx.close();
  } },
  { name: '2단 보기: 목록이 갱신되어(htmx) 제목 링크가 새로 생긴 뒤 _initTwoColumnMode를 다시 불러도 새 제목이 패널로 열린다', run: async () => {
    const { page, ctx } = await open();
    await page.evaluate(() => { document.getElementById('list').innerHTML = '<li class="post-item" id="p3"><a class="title" id="t3" href="/issue/3">이슈 3</a></li>'; window._initTwoColumnMode(); });
    await page.click('#t3'); await page.waitForTimeout(800); assert.equal((await panel(page)).iframe, '/issue/3'); assert.equal(page.url(), 'http://tc.test/issue/3'); assert.deepEqual(page.errors, []); await ctx.close();
  } },
];
