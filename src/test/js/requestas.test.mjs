// 눌러서 서버에 요청하고 화면을 갱신하는 `common/yona.Request.js`를 시험한다(S-07, A-422). Yobi 시절의 jQuery 플러그인 `jquery.requestAs.js`를 대체했다.
// 선언형(`data-request-method`, `data-request-uri`)은 문서가 준비될 때 요소마다 클릭과 Enter 처리기를 단다. 직접 부를 때는 `yona.Request.send(방식, 주소)`를 쓴다.
// 서버 계약은 그대로다: 성공이 204이고 `Location`이 있으면 그곳으로 이동하고, 아니면 화면을 새로 고친다. 실패는 사용자에게 알리지 않는다.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pageWith, JS, ROOT } from './harness.mjs';

/** 가짜 fetch 와 가짜 이동을 심은 페이지. [reply]는 다음 응답({status, location, text}) 또는 'network' */
async function open(html = '') {
  const page = await pageWith({ html, scripts: ['common/yona.Common.js', 'common/yona.Request.js'], stubs: false });
  await page.evaluate(() => {
    window.__fetch = []; window.__nav = []; window.__reply = { status: 200 };
    window.fetch = function (url, opts) {
      window.__fetch.push({ url: String(url), method: opts.method, headers: opts.headers });
      if (window.__reply === 'network') return Promise.reject(new TypeError('network'));
      const r = window.__reply; const headers = r.location ? { Location: r.location } : {};
      return Promise.resolve(new Response(r.status === 204 ? null : (r.text || ''), { status: r.status, headers }));
    };
    yona.Request.navigation = { to: (u) => window.__nav.push(['to', u]), reload: () => window.__nav.push(['reload']) };
    yona.Request.bind(document);
  });
  return page;
}
const state = (page) => page.evaluate(() => ({ fetch: window.__fetch, nav: window.__nav }));
const settle = (page) => page.waitForTimeout(120);
const reply = (page, r) => page.evaluate((r) => { window.__reply = r; }, r);

export const tests = [
  { name: '선언형: 누르면 data-request-method 방식과 data-request-uri 주소로 요청하고 X-Requested-With 를 단다', run: async () => {
    const page = await open('<button id="b" data-request-method="delete" data-request-uri="/issue/3/delete">삭제</button>');
    await page.click('#b'); await settle(page);
    const s = await state(page);
    assert.equal(s.fetch.length, 1); assert.equal(s.fetch[0].method, 'DELETE'); assert.equal(s.fetch[0].url, '/issue/3/delete');
    assert.equal(s.fetch[0].headers['X-Requested-With'], 'XMLHttpRequest');
    await page.close();
  } },
  { name: '선언형: data-request-uri 가 없으면 href 를 쓴다(링크 요소)', run: async () => {
    const page = await open('<a id="a" href="/vote" data-request-method="post">투표</a>');
    await page.click('#a'); await settle(page);
    assert.deepEqual((await state(page)).fetch.map((f) => [f.method, f.url]), [['POST', '/vote']]);
    assert.equal(await page.evaluate(() => typeof window.__fetch), 'object', '링크가 열려 페이지가 바뀌면 안 된다');
    await page.close();
  } },
  { name: '성공 기본 동작: 204 와 Location 이면 그곳으로 이동한다', run: async () => {
    const page = await open('<button id="b" data-request-method="post" data-request-uri="/x">x</button>');
    await reply(page, { status: 204, location: '/issue/9' }); await page.click('#b'); await settle(page);
    assert.deepEqual((await state(page)).nav, [['to', '/issue/9']]); await page.close();
  } },
  { name: '성공 기본 동작: 그 밖의 성공(200, Location 없는 204)은 화면을 새로 고친다', run: async () => {
    const page = await open('<button id="b" data-request-method="put" data-request-uri="/x">x</button>');
    await page.click('#b'); await settle(page); await reply(page, { status: 204 }); await page.click('#b'); await settle(page);
    assert.deepEqual((await state(page)).nav, [['reload'], ['reload']]); await page.close();
  } },
  { name: '실패(403, 500, 네트워크 오류)는 이동도 새로 고침도 하지 않고 request:error 로 알린다', run: async () => {
    const page = await open('<button id="b" data-request-method="delete" data-request-uri="/x">x</button>');
    await page.evaluate(() => { window.__err = []; document.getElementById('b').addEventListener('request:error', (e) => window.__err.push(e.detail.status)); });
    await reply(page, { status: 403 }); await page.click('#b'); await settle(page);
    await reply(page, { status: 500 }); await page.click('#b'); await settle(page);
    await reply(page, 'network'); await page.click('#b'); await settle(page);
    assert.deepEqual(await page.evaluate(() => window.__err), [403, 500, 0]); assert.deepEqual((await state(page)).nav, []);
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: 'request:before 에서 preventDefault 하면 요청을 보내지 않는다(수락 단추 비활성화 같은 사전 처리도 여기서)', run: async () => {
    const page = await open('<button id="b" data-request-method="post" data-request-uri="/x">x</button>');
    await page.evaluate(() => { window.__cancel = true; document.getElementById('b').addEventListener('request:before', (e) => { window.__before = (window.__before || 0) + 1; if (window.__cancel) e.preventDefault(); }); });
    await page.click('#b'); await settle(page); assert.equal((await state(page)).fetch.length, 0);
    await page.evaluate(() => { window.__cancel = false; }); await page.click('#b'); await settle(page);
    assert.equal((await state(page)).fetch.length, 1); assert.equal(await page.evaluate(() => window.__before), 2); await page.close();
  } },
  { name: 'request:load 에서 preventDefault 하면 이동도 새로 고침도 하지 않는다. 상태와 응답을 담아 보낸다', run: async () => {
    const page = await open('<button id="b" data-request-method="post" data-request-uri="/x">x</button>');
    await page.evaluate(() => { document.getElementById('b').addEventListener('request:load', (e) => { window.__load = [e.detail.status, e.detail.response instanceof Response]; e.preventDefault(); }); });
    await reply(page, { status: 204, location: '/y' }); await page.click('#b'); await settle(page);
    assert.deepEqual(await page.evaluate(() => window.__load), [204, true]); assert.deepEqual((await state(page)).nav, []); await page.close();
  } },
  { name: '누르면 기본 동작과 조상의 클릭 처리기로의 전파를 막는다(링크가 열리지 않는다)', run: async () => {
    const page = await open('<div id="p"><a id="a" href="#moved" data-request-method="post" data-request-uri="/x">x</a></div>');
    await page.evaluate(() => { window.__parent = 0; document.getElementById('p').addEventListener('click', () => window.__parent++); });
    await page.click('#a'); await settle(page);
    assert.equal(await page.evaluate(() => window.__parent), 0); assert.equal(await page.evaluate(() => location.hash), ''); await page.close();
  } },
  { name: 'Enter 키로도 요청한다. 다른 키와 한글 조합 중 Enter 는 아니다', run: async () => {
    const page = await open('<button id="b" data-request-method="post" data-request-uri="/x">x</button>');
    await page.evaluate(() => { const b = document.getElementById('b'); const k = (init) => b.dispatchEvent(new KeyboardEvent('keydown', Object.assign({ bubbles: true, cancelable: true }, init)));
      k({ key: 'a', keyCode: 65 }); k({ key: 'Enter', keyCode: 229, isComposing: true }); k({ key: 'Process', keyCode: 229 }); });
    await settle(page); assert.equal((await state(page)).fetch.length, 0);
    await page.evaluate(() => document.getElementById('b').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, bubbles: true, cancelable: true })));
    await settle(page); assert.equal((await state(page)).fetch.length, 1); await page.close();
  } },
  { name: 'GET 링크(<a>)에는 아무것도 달지 않는다: 보통 링크이다(C003)', run: async () => {
    const page = await open('<a id="a" href="#plain" data-request-method="get">링크</a>');
    await page.click('#a'); await settle(page);
    assert.equal((await state(page)).fetch.length, 0); assert.equal(await page.evaluate(() => location.hash), '#plain');
    assert.equal(await page.evaluate(() => document.getElementById('a').style.cursor), ''); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '요청하는 요소에는 손가락 모양 커서를 준다', run: async () => {
    const page = await open('<button id="b" data-request-method="post" data-request-uri="/x">x</button>');
    assert.equal(await page.evaluate(() => document.getElementById('b').style.cursor), 'pointer'); await page.close();
  } },
  { name: 'bind 는 같은 요소에 두 번 걸어도 요청이 한 번만 나간다. 나중에 들어온 요소는 bind(루트)로 건다', run: async () => {
    const page = await open('<div id="root"></div>');
    await page.evaluate(() => { yona.Request.bind(document); yona.Request.bind(document);
      const root = document.getElementById('root'); root.innerHTML = '<button id="late" data-request-method="delete" data-request-uri="/late">x</button>'; });
    await page.click('#late'); await settle(page); assert.equal((await state(page)).fetch.length, 0, '아직 안 걸었다');
    await page.evaluate(() => yona.Request.bind(document.getElementById('root'))); await page.evaluate(() => yona.Request.bind(document.getElementById('root')));
    await page.click('#late'); await settle(page); assert.equal((await state(page)).fetch.length, 1); await page.close();
  } },
  { name: '방식과 주소는 누르는 때 읽는다(확인창 단추가 항목마다 주소를 바꿔 쓴다)', run: async () => {
    const page = await open('<button id="b" data-request-method="delete" data-request-uri="/first">x</button>');
    await page.evaluate(() => { document.getElementById('b').dataset.requestUri = '/second'; });
    await page.click('#b'); await settle(page); assert.equal((await state(page)).fetch[0].url, '/second'); await page.close();
  } },
  { name: '직접 부르기: send(방식, 주소)는 요청하고 {ok, status}로 끝나며 기본 동작도 한다. 실패는 예외가 아니라 결과다', run: async () => {
    const page = await open('');
    const r = await page.evaluate(async () => { window.__reply = { status: 204, location: '/done' }; const a = await yona.Request.send('delete', '/p/1'); window.__reply = { status: 403 }; const b = await yona.Request.send('delete', '/p/1'); window.__reply = 'network'; const c = await yona.Request.send('post', '/p/2'); return [a, b, c]; });
    assert.deepEqual(r, [{ ok: true, status: 204 }, { ok: false, status: 403 }, { ok: false, status: 0 }]);
    const s = await state(page); assert.deepEqual(s.fetch.map((f) => f.method + ' ' + f.url), ['DELETE /p/1', 'DELETE /p/1', 'POST /p/2']); assert.deepEqual(s.nav, [['to', '/done']]);
    assert.equal(s.fetch[0].headers['X-Requested-With'], 'XMLHttpRequest'); await page.close();
  } },
  { name: '직접 부르기에서 요소를 함께 넘기면 그 요소에 이벤트를 보낸다', run: async () => {
    const page = await open('<button id="b">x</button>');
    const r = await page.evaluate(async () => { const b = document.getElementById('b'); window.__e = []; ['request:before', 'request:load', 'request:error'].forEach((t) => b.addEventListener(t, () => window.__e.push(t)));
      window.__reply = { status: 200 }; await yona.Request.send('post', '/x', b); window.__reply = { status: 500 }; await yona.Request.send('post', '/x', b); return window.__e; });
    assert.deepEqual(r, ['request:before', 'request:load', 'request:before', 'request:error']); await page.close();
  } },
  { name: '옛 플러그인은 없다: 파일, $.fn.requestAs, .requestAs( 호출, 번들 입력', run: async () => {
    assert.ok(!fs.existsSync(path.join(JS, 'lib/jquery/jquery.requestAs.js')), '플러그인 파일이 남아 있다');
    const roots = [path.join(JS, 'common'), path.join(JS, 'service'), path.join(JS, '..', '..', '..', 'templates')];
    const files = roots.flatMap((r) => fs.readdirSync(r, { recursive: true }).map((f) => path.join(r, String(f)))).filter((f) => /\.(js|html)$/.test(f) && fs.statSync(f).isFile());
    const left = files.filter((f) => /\.requestAs\(|data\(["']requestAs["']\)/.test(fs.readFileSync(f, 'utf8'))).map((f) => path.relative(JS, f));
    assert.deepEqual(left, []);
    const bundles = fs.readFileSync(path.join(ROOT, 'build-tools/js-bundle/bundles.mjs'), 'utf8');
    assert.ok(bundles.includes('common/yona.Request.js') && !bundles.includes('requestAs'), '번들 입력이 바뀌어야 한다');
  } },
  { name: '수락 단추의 닿지 않던 훅이 없다: 병합은 병합 상자의 일반 폼 제출이라 요청 이벤트를 쓰지 않는다(A-422)', run: async () => {
    for (const f of ['esm/service/code.Diff.js', 'esm/service/git.View.js']) {
      const text = fs.readFileSync(path.join(JS, f), 'utf8');
      assert.ok(!text.includes('request:before'), `${f}: request:before 훅이 남아 있다`);
      assert.ok(!/welBtnAccept\[0\]\.addEventListener/.test(text), `${f}: 수락 단추에 건 처리기가 남아 있다`);
    }
    assert.ok(!fs.readFileSync(path.join(JS, 'esm/service/code.Diff.js'), 'utf8').includes('welBtnAccept'), 'code.Diff 에서는 수락 단추 변수도 필요 없다');
  } },
];
