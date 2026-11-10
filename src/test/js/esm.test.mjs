// ES 모듈 뼈대(D-122): import map의 `yona/` 접두어로 `esm/` 폴더의 모듈을 이름으로 가져오고, 모듈이 옛 전역 이름 공간(`yona.*`)에 등록해 지금의 클래식 스크립트와 함께 쓸 수 있는지 확인한다.
// 모듈은 http 출처가 있어야 하므로 저장소의 파일을 `page.route`로 내려 준다.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, JS, PRELUDE } from './harness.mjs';

const ESM = path.join(JS, 'esm');
const IMPORT_MAP = '{"imports": {"yona/": "/assets/javascripts/esm/"}}';

async function open(body, { legacy = '' } = {}) {
  const browser = await launch(); const ctx = await browser.newContext(); const page = await ctx.newPage(); page.errors = []; page.events = [];
  page.on('pageerror', (e) => page.errors.push(String(e.message || e)));
  await page.route('http://esm.test/**', (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/page') return route.fulfill({ contentType: 'text/html', body: `<!doctype html><head><script type="importmap">${IMPORT_MAP}</script>${body.includes('esm/index.js') ? PRELUDE : ''}<script>window.yona = window.yona || {}; ${legacy}</script></head><body>${body}</body>` });
    const file = path.join(JS, url.pathname.replace('/assets/javascripts/', ''));
    if (url.pathname.startsWith('/assets/javascripts/') && fs.existsSync(file) && fs.statSync(file).isFile()) return route.fulfill({ contentType: 'text/javascript', body: fs.readFileSync(file) });
    return route.fulfill({ status: 404, body: '' });
  });
  await page.goto('http://esm.test/page');
  return { page, ctx };
}

export const tests = [
  { name: 'index.js를 불러오면 yona.esm이 준비되고 yona:esm-ready 이벤트가 한 번 뜬다', run: async () => {
    const { page, ctx } = await open(`<script>window.__ready = 0; document.addEventListener('yona:esm-ready', () => window.__ready++);</script><script type="module" src="/assets/javascripts/esm/index.js"></script>`);
    await page.waitForFunction(() => window.yona && yona.esm && yona.esm.ready === true, null, { timeout: 5000 });
    assert.equal(await page.evaluate(() => window.__ready), 1); assert.equal(await page.evaluate(() => [typeof yona.esm.register, typeof yona.esm.ensureNamespace]).then((r) => r.join()), 'function,function'); assert.deepEqual(page.errors, []); await ctx.close();
  } },
  { name: "import map의 'yona/' 접두어로 모듈을 가져오고 register가 옛 전역 이름 공간에 값을 넣는다(없는 단계는 만든다)", run: async () => {
    const { page, ctx } = await open(`<script type="module">import { register } from 'yona/namespace.js'; register('yona.ui.Sample', 42); register('yona.deep.er.Thing', { ok: true }); window.__done = true;</script>`);
    await page.waitForFunction(() => window.__done === true, null, { timeout: 5000 });
    assert.deepEqual(await page.evaluate(() => [yona.ui.Sample, yona.deep.er.Thing.ok]), [42, true]); assert.deepEqual(page.errors, []); await ctx.close();
  } },
  { name: '이미 있는 전역 이름 공간과 값은 지우지 않는다(클래식 스크립트가 먼저 만든 yona.ui.Existing)', run: async () => {
    const { page, ctx } = await open(`<script type="module">import { register, ensureNamespace } from 'yona/namespace.js'; register('yona.ui.Added', 1); window.__same = ensureNamespace('yona.ui') === yona.ui; window.__done = true;</script>`, { legacy: "yona.ui = { Existing: 'legacy' };" });
    await page.waitForFunction(() => window.__done === true, null, { timeout: 5000 });
    assert.deepEqual(await page.evaluate(() => [yona.ui.Existing, yona.ui.Added, window.__same]), ['legacy', 1, true]); await ctx.close();
  } },
  { name: '모듈은 클래식 스크립트가 쓰던 이름(yona.ui.X)을 그대로 쓰게 등록하고, 같은 이름을 다시 등록하면 새 값으로 바뀐다', run: async () => {
    const { page, ctx } = await open(`<script type="module">import { register } from 'yona/namespace.js'; register('yona.Util', { v: 1 }); register('yona.Util', { v: 2 }); window.__done = true;</script>`);
    await page.waitForFunction(() => window.__done === true, null, { timeout: 5000 }); assert.equal(await page.evaluate(() => yona.Util.v), 2); await ctx.close();
  } },
  { name: '잘못된 이름(빈 글자, 점으로 시작하거나 끝남, 숫자로 시작하는 단계)은 오류를 던진다', run: async () => {
    const { page, ctx } = await open(`<script type="module">import { register } from 'yona/namespace.js'; const out = []; for (const bad of ['', '.a', 'a.', 'a..b', 'a.1b']) { try { register(bad, 1); out.push('ok'); } catch (e) { out.push('error'); } } window.__out = out; </script>`);
    await page.waitForFunction(() => Array.isArray(window.__out), null, { timeout: 5000 }); assert.deepEqual(await page.evaluate(() => window.__out), ['error', 'error', 'error', 'error', 'error']); await ctx.close();
  } },
];
