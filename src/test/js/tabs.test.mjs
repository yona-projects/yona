// 탭 기억 `esm/ui/Tabs.js`(F-02, R-139): 고른 탭의 순서를 localStorage(`yobitab-<id>`)에 저장하고 다음에 열 때 그 탭을 보여 준다.
// v1.6은 복원 조건이 `welLink.data("toggle" == "tab")`(항상 거짓)이라 저장만 하고 복원하지 않았다. 고쳐서 복원한다.
// 다만 서버가 탭을 정해 준 경우(`?tab=`, 설정을 저장한 뒤 돌아오는 화면)에는 서버를 따른다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { launch, JS, ROOT, classicFromEsm } from './harness.mjs';

const BOOTSTRAP = path.join(ROOT, 'src/main/resources/static/assets/bootstrap/js/bootstrap.bundle.min.js');
const HTML = `<ul class="nav nav-tabs" id="sso-tabs" role="tablist">
  <li class="nav-item"><button type="button" class="nav-link active" data-bs-toggle="tab" data-bs-target="#p0">일반</button></li>
  <li class="nav-item"><button type="button" class="nav-link" data-bs-toggle="tab" data-bs-target="#p1">LDAP</button></li>
  <li class="nav-item"><a href="#p2" class="nav-link" data-bs-toggle="tab" data-bs-target="#p2">OIDC</a></li>
  <li class="nav-item"><a href="/elsewhere" class="nav-link" id="plain">링크</a></li></ul>
  <div class="tab-content"><div class="tab-pane fade show active" id="p0">0</div><div class="tab-pane fade" id="p1">1</div><div class="tab-pane fade" id="p2">2</div></div>
  <ul class="nav nav-tabs" id="noStore"></ul><ul class="nav nav-tabs"><li><a href="#x" data-bs-toggle="tab">익명</a></li></ul>`;

/** 주소와 저장된 값을 정해 페이지를 연다. 스크립트는 문서가 준비될 때 탭을 복원한다. */
async function open({ query = '', saved = {}, html = HTML } = {}) {
  const b = await launch(); const ctx = await b.newContext(); const page = await ctx.newPage(); page.errors = [];
  page.on('pageerror', (e) => page.errors.push(String(e.message || e)));
  await page.route('http://tabs.test/**', (route) => route.fulfill({ contentType: 'text/html', body: '<!doctype html><body></body>' }));
  await page.goto('http://tabs.test/page.html' + query);
  await page.evaluate((s) => { for (const [k, v] of Object.entries(s)) localStorage.setItem(k, v); }, saved);
  await page.setContent(`<!doctype html><body>${html}</body>`);
  await page.addScriptTag({ path: path.join(JS, 'lib/jquery/jquery-3.7.1.js') });
  await page.addScriptTag({ path: BOOTSTRAP });
  await page.addScriptTag({ path: path.join(JS, 'common/yona.Common.js') });
  await page.addScriptTag({ content: classicFromEsm('esm/ui/Tabs.js') });
  await page.waitForTimeout(200);
  return { page, ctx };
}
const active = (page) => page.evaluate(() => [...document.querySelectorAll('#sso-tabs .nav-link')].findIndex((l) => l.classList.contains('active')));
const shownPane = (page) => page.evaluate(() => [...document.querySelectorAll('.tab-pane')].findIndex((p) => p.classList.contains('active')));

export const tests = [
  { name: '탭 기억: 탭을 누르면 순서를 localStorage에 저장한다', run: async () => {
    const { page, ctx } = await open();
    await page.click('#sso-tabs li:nth-child(2) button'); await page.waitForTimeout(300);
    assert.equal(await page.evaluate(() => localStorage.getItem('yobitab-sso-tabs')), '1'); await ctx.close();
  } },
  { name: '탭 기억: 저장된 순서가 있으면 그 탭이 열린다(button, a 모두, 패널도 함께)', run: async () => {
    const one = await open({ saved: { 'yobitab-sso-tabs': '1' } });
    assert.equal(await active(one.page), 1); assert.equal(await shownPane(one.page), 1); await one.ctx.close();
    const two = await open({ saved: { 'yobitab-sso-tabs': '2' } });
    assert.equal(await active(two.page), 2); assert.equal(await shownPane(two.page), 2); await two.ctx.close();
  } },
  { name: '탭 기억: 저장된 값이 0이어도 복원한다(첫 탭)', run: async () => {
    const { page, ctx } = await open({ saved: { 'yobitab-sso-tabs': '0' } });
    assert.equal(await active(page), 0); assert.equal(await shownPane(page), 0); await ctx.close();
  } },
  { name: '탭 기억: 서버가 탭을 정해 준 주소(?tab=)에서는 저장된 값으로 덮어쓰지 않는다', run: async () => {
    const { page, ctx } = await open({ query: '?tab=general', saved: { 'yobitab-sso-tabs': '2' } });
    assert.equal(await active(page), 0); assert.equal(await shownPane(page), 0); await ctx.close();
  } },
  { name: '탭 기억: 탭이 아닌 링크(data-bs-toggle 없음)나 범위를 벗어난 값, 저장값이 없는 경우는 아무것도 바꾸지 않는다', run: async () => {
    for (const saved of [{ 'yobitab-sso-tabs': '3' }, { 'yobitab-sso-tabs': '9' }, { 'yobitab-sso-tabs': 'abc' }, {}]) {
      const { page, ctx } = await open({ saved }); assert.equal(await active(page), 0, JSON.stringify(saved)); assert.deepEqual(page.errors, []); await ctx.close();
    }
  } },
  { name: '탭 기억: id가 없는 탭 목록은 저장하지 않고 오류도 없다', run: async () => {
    const { page, ctx } = await open(); await page.click('ul.nav-tabs:not([id]) a'); await page.waitForTimeout(200);
    assert.equal(await page.evaluate(() => Object.keys(localStorage).filter((k) => k.startsWith('yobitab-')).length), 0); assert.deepEqual(page.errors, []); await ctx.close();
  } },
  { name: '탭 기억: localStorage를 쓸 수 없어도 오류 없이 넘어간다(차단된 사이트 데이터)', run: async () => {
    const b = await launch(); const ctx = await b.newContext(); const page = await ctx.newPage(); page.errors = []; page.on('pageerror', (e) => page.errors.push(String(e.message || e)));
    await page.route('http://tabs.test/**', (route) => route.fulfill({ contentType: 'text/html', body: '<!doctype html><body></body>' })); await page.goto('http://tabs.test/page.html');
    await page.setContent(`<!doctype html><body>${HTML}</body>`);
    await page.evaluate(() => { Object.defineProperty(window, 'localStorage', { get() { throw new DOMException('denied', 'SecurityError'); } }); });
    for (const f of ['lib/jquery/jquery-3.7.1.js', 'common/yona.Common.js', 'esm/ui/Tabs.js']) await page.addScriptTag(f.startsWith('esm/') ? { content: classicFromEsm(f) } : { path: path.join(JS, f) });
    await page.waitForTimeout(150); assert.deepEqual(page.errors.filter((m) => !/showTab|bootstrap/i.test(m)), []); await ctx.close();
  } },
];
