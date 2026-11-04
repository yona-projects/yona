import assert from 'node:assert/strict';
import path from 'node:path';
import { launch, JS } from './harness.mjs';

/**
 * 쿠키를 쓸 수 있도록 가짜 주소(http://yona.test)의 페이지를 만들고, 쿠키 `XSRF-TOKEN`을 심은 뒤 yona.Csrf.js를 불러온다.
 */
async function page(html = '') {
  const b = await launch();
  const p = await b.newPage();
  p.errors = [];
  await p.route('http://yona.test/**', (route) => route.fulfill({ contentType: 'text/html', body: `<!doctype html><html><body>${html}</body></html>` }));
  await p.goto('http://yona.test/start');
  await p.evaluate(() => { document.cookie = 'XSRF-TOKEN=tok123; path=/'; });
  await p.addScriptTag({ path: path.join(JS, 'common/yona.Csrf.js') });
  return p;
}

export const tests = [
  { name: '상태를 바꾸는 XHR 요청에는 토큰 헤더를 싣고 읽기 요청에는 싣지 않는다 (R-02)', async run() {
    const p = await page();
    const r = await p.evaluate(() => {
      var seen = [];
      var orig = XMLHttpRequest.prototype.setRequestHeader;
      XMLHttpRequest.prototype.setRequestHeader = function (k, v) { seen.push(this.__m + ':' + k + '=' + v); };
      ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].forEach(function (m) {
        var x = new XMLHttpRequest(); x.__m = m;
        try { x.open(m, '/x'); x.send(); } catch (e) {}
      });
      return seen;
    });
    await p.close();
    assert.deepEqual(r.filter((s) => s.includes('X-XSRF-TOKEN')).map((s) => s.split(':')[0]).sort(), ['DELETE', 'PATCH', 'POST', 'PUT']);
    assert.ok(!r.some((s) => s.startsWith('GET:')), r.join(','));
  } },
  { name: '다른 사이트로 가는 XHR에는 토큰을 싣지 않는다 (R-02)', async run() {
    const p = await page();
    const r = await p.evaluate(() => {
      var seen = [];
      XMLHttpRequest.prototype.setRequestHeader = function (k, v) { seen.push(k); };
      var x = new XMLHttpRequest(); x.open('POST', 'https://evil.example.com/x'); try { x.send(); } catch (e) {}
      return seen;
    });
    await p.close();
    assert.deepEqual(r.filter((k) => k === 'X-XSRF-TOKEN'), []);
  } },
  { name: 'POST 폼을 제출하면 숨은 토큰 입력이 들어가고 GET 폼에는 들어가지 않는다 (R-02)', async run() {
    const p = await page('<form id="a" method="post" action="/a"></form><form id="b" method="get" action="/b"></form>');
    const r = await p.evaluate(() => {
      // 이동을 막고 제출 시점의 폼 내용만 본다
      document.querySelectorAll('form').forEach(function (f) { f.addEventListener('submit', function (e) { e.preventDefault(); }); });
      document.getElementById('a').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      document.getElementById('b').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      return { a: (document.querySelector('#a input[name=_csrf]') || {}).value, b: !!document.querySelector('#b input[name=_csrf]') };
    });
    await p.close();
    assert.equal(r.a, 'tok123'); assert.equal(r.b, false);
  } },
  { name: 'form.submit()을 코드로 부르는 경우에도 토큰이 들어간다 (R-02)', async run() {
    const p = await page('<iframe name="sink"></iframe><form id="a" method="post" action="/never" target="sink"></form>');
    const r = await p.evaluate(() => {
      document.getElementById('a').submit();
      return (document.querySelector('#a input[name=_csrf]') || {}).value;
    });
    await p.close();
    assert.equal(r, 'tok123');
  } },
];
