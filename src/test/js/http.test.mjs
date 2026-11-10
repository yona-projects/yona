// yona.http(esm/http.js)가 jQuery의 $.ajax, $.get, $.post, $.getJSON과 같은 요청을 보내고 같은 결과를 내는지 같은 입력으로 비교한다(표준 기능 리팩터링 3단계, D-123).
// 한 페이지에 jQuery와 yona.http를 함께 올리고, 서버(page.route)가 받은 요청과 콜백이 받은 값을 기록해 둘을 비교한다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { esmPage, JS } from './harness.mjs';

const RESPONSES = {
  '/api/json': { status: 200, type: 'application/json', body: '{"a":1,"b":[1,2]}', headers: { 'X-T': 'yes' } },
  '/api/wrongtype': { status: 200, type: 'text/plain', body: '{"x":2}' },
  '/api/html': { status: 200, type: 'text/html', body: '<b>hi</b>' },
  '/api/empty204': { status: 204, type: 'application/json', body: '', headers: { Location: '/next' } },
  '/api/emptyjson': { status: 200, type: 'application/json', body: '' },
  '/api/badjson': { status: 200, type: 'application/json', body: '{bad' },
  '/api/404': { status: 404, type: 'text/plain', body: 'nope' },
  '/api/500json': { status: 500, type: 'application/json', body: '{"error":"x"}' },
  '/api/notmod': { status: 304, type: 'text/plain', body: '' },
};

async function open() {
  const requests = [];
  const page = await esmPage({ bodyHtml: '' });
  await page.addScriptTag({ path: path.join(JS, 'lib/jquery/jquery-3.7.1.js') });
  await page.unroute('http://esm.test/**').catch(() => {});
  const handler = async (route) => {
    const r = route.request(); const u = new URL(r.url());
    if (u.pathname.startsWith('/assets/') || u.pathname === '/page') return route.fallback();
    const h = r.headers();
    requests.push({ m: r.method(), url: (u.pathname + u.search).replace(/_=\d+/, '_=TS'), ct: (h['content-type'] || '').replace(/; boundary=.*/, '; boundary=B'), accept: h.accept || '', xrw: h['x-requested-with'] || '', extra: h['x-extra'] || '', body: (r.postData() || '').replace(/-+(WebKitFormBoundary|geckoformboundary)[A-Za-z0-9]+/g, '--B') });
    const res = RESPONSES[u.pathname];
    if (!res) return route.abort();
    return route.fulfill({ status: res.status, contentType: res.type, headers: res.headers || {}, body: res.body });
  };
  await page.route('http://esm.test/api/**', handler);
  return { page, requests };
}

/** 같은 옵션으로 두 구현을 차례로 부르고 (서버가 받은 요청, 콜백이 받은 값) 쌍을 돌려준다. [make]는 페이지 안에서 `(impl)`을 받아 호출하는 식이다. */
async function both(page, requests, source) {
  const out = {};
  for (const impl of ['jquery', 'yona']) {
    requests.length = 0;
    out[impl] = { events: await page.evaluate(async ([src, which]) => {
      const events = []; const ctx = { id: 'ctx' };
      const rec = (label) => function (a, b, c) { const xhr = [a, b, c].find((v) => v && typeof v === 'object' && typeof v.getResponseHeader === 'function'); events.push([label, typeof a === 'object' && a && a.getResponseHeader ? undefined : a, typeof b === 'string' ? b : undefined, xhr ? xhr.status : undefined, xhr ? xhr.getResponseHeader('X-T') : undefined, this === ctx ? 'ctx' : 'other', xhr ? JSON.stringify(xhr.responseJSON) : undefined]); };
      const api = which === 'jquery' ? $ : { ajax: yona.http.ajax, get: yona.http.get, post: yona.http.post, getJSON: yona.http.getJSON };
      const fn = new Function('api', 'rec', 'ctx', `return (${src});`);
      const jq = fn(api, rec, ctx);
      await new Promise((r) => setTimeout(r, 600));
      return events;
    }, [source, impl]), requests: requests.map((r) => ({ ...r })) };
  }
  return out;
}
const check = async (source) => { const { page, requests } = await open(); const r = await both(page, requests, source); await page.closeAll(); assert.deepEqual(r.yona.requests, r.jquery.requests, '서버가 받은 요청'); assert.deepEqual(r.yona.events, r.jquery.events, '콜백이 받은 값'); assert.ok(r.jquery.requests.length >= 1); return r; };

const CASES = [
  ['GET 기본(응답 Content-Type이 json이면 객체로 읽는다)', "api.ajax({ url: '/api/json', success: rec('success'), error: rec('error'), complete: rec('complete') })"],
  ['GET dataType json과 data 객체(배열, 중첩 객체, 공백)가 주소 뒤에 붙는다', "api.ajax({ url: '/api/json?x=1', data: { a: 1, b: [1, 2], c: { d: 'x y', e: [{ f: 1 }] }, g: '한글&=' }, dataType: 'json', success: rec('success') })"],
  ['traditional: 배열을 key=1&key=2로 쓴다', "api.ajax({ url: '/api/json', method: 'POST', data: { ids: [1, 2], o: { a: 1 } }, traditional: true, success: rec('success') })"],
  ['POST 본문의 공백과 한글', "api.ajax({ url: '/api/json', method: 'POST', data: { q: 'a b', k: '한글' }, success: rec('success') })"],
  ['POST data 객체는 urlencoded 본문이다', "api.ajax({ url: '/api/json', method: 'POST', data: { a: 1, b: ['x', 'y'], c: { d: 2 } }, success: rec('success') })"],
  ['POST 문자열 data와 contentType json', "api.ajax({ url: '/api/json', type: 'post', data: '{\"k\":1}', contentType: 'application/json', dataType: 'json', success: rec('success') })"],
  ['PUT 204 빈 응답(Location 머리글), dataType json도 성공', "api.ajax({ url: '/api/empty204', method: 'put', dataType: 'json', success: rec('success'), error: rec('error') })"],
  ['DELETE(소문자 방식)', "api.ajax({ url: '/api/json', method: 'delete', success: rec('success') })"],
  ['Content-Type이 json이 아니고 dataType도 없으면 글자 그대로', "api.ajax({ url: '/api/wrongtype', success: rec('success') })"],
  ['dataType json이면 Content-Type이 달라도 객체로 읽는다', "api.ajax({ url: '/api/wrongtype', dataType: 'json', success: rec('success') })"],
  ['HTML 응답', "api.ajax({ url: '/api/html', dataType: 'html', success: rec('success') })"],
  ['404는 error(xhr, textStatus, 오류 글자)', "api.ajax({ url: '/api/404', success: rec('success'), error: rec('error'), complete: rec('complete') })"],
  ['500 JSON 본문도 error이고 xhr 상태를 읽을 수 있다', "api.ajax({ url: '/api/500json', dataType: 'json', error: rec('error') })"],
  ['500 JSON 본문은 dataType이 없어도 Content-Type으로 읽어 responseJSON을 채운다', "api.ajax({ url: '/api/500json', error: rec('error') })"],
  ['404 HTML 본문은 responseJSON이 없다', "api.ajax({ url: '/api/404', dataType: 'json', error: rec('error') })"],
  ['dataType json인데 본문이 비었으면 parsererror', "api.ajax({ url: '/api/emptyjson', dataType: 'json', success: rec('success'), error: rec('error') })"],
  ['dataType json인데 JSON이 깨졌으면 parsererror', "api.ajax({ url: '/api/badjson', dataType: 'json', success: rec('success'), error: rec('error') })"],
  ['304는 success(notmodified)', "api.ajax({ url: '/api/notmod', success: rec('success'), error: rec('error') })"],
  ['연결 실패는 error(status 0)', "api.ajax({ url: '/api/none', error: rec('error'), complete: rec('complete') })"],
  ['headers와 beforeSend의 setRequestHeader', "api.ajax({ url: '/api/json', headers: { 'X-Extra': 'a' }, beforeSend: (xhr) => xhr.setRequestHeader('X-Extra', 'b'), success: rec('success') })"],
  ['cache: false는 GET 주소에 _= 값을 더한다', "api.ajax({ url: '/api/json', cache: false, success: rec('success') })"],
  ['context 옵션이 콜백의 this가 된다', "api.ajax({ url: '/api/json', context: ctx, success: rec('success'), complete: rec('complete') })"],
  ['.done, .fail, .always 연결', "(() => { const p = api.ajax({ url: '/api/404' }); p.done(rec('done')).fail(rec('fail')).always(rec('always')); return p; })()"],
  ['성공에서 .done, .always', "(() => { const p = api.ajax('/api/json', { dataType: 'json' }); p.done(rec('done')).fail(rec('fail')).always(rec('always')); return p; })()"],
  ['$.get(url, 콜백)', "api.get('/api/json', rec('success'))"],
  ['$.get(url, 데이터, 콜백, dataType)', "api.get('/api/json', { q: 'a b' }, rec('success'), 'json')"],
  ['$.post(url, 데이터, 콜백, dataType)', "api.post('/api/json', { q: 'a b' }, rec('success'), 'json')"],
  ['$.post(url) 데이터 없음', "api.post('/api/json', rec('success'))"],
  ['$.getJSON(url, 콜백)', "api.getJSON('/api/wrongtype', rec('success'))"],
  ['폼 필드 배열 data([{name, value}])', "api.ajax({ url: '/api/json', method: 'post', data: [{ name: 'a', value: '1' }, { name: 'a', value: '2' }], success: rec('success') })"],
];

export const tests = CASES.map(([name, source]) => ({ name: `yona.http: ${name}`, run: async () => { await check(source); } })).concat([
  { name: 'yona.http: FormData 본문(contentType false, processData false)은 multipart로 간다', run: async () => {
    const r = await check("(() => { const fd = new FormData(); fd.append('f', 'v'); return api.ajax({ url: '/api/json', method: 'POST', data: fd, processData: false, contentType: false, success: rec('success') }); })()");
    assert.match(r.yona.requests[0].ct, /^multipart\/form-data/);
  } },
  { name: 'yona.http: then(성공, 실패)와 catch가 jQuery 3처럼 이어진다', run: async () => {
    const { page, requests } = await open();
    const r = await page.evaluate(async () => {
      const out = {};
      for (const [name, api] of [['jquery', $], ['yona', { ajax: yona.http.ajax }]]) {
        const got = [];
        await api.ajax({ url: '/api/json', dataType: 'json' }).then((d) => got.push(['ok', d.a]));
        await api.ajax({ url: '/api/404' }).then(() => got.push('bad'), (x) => got.push(['fail', x.status]));
        out[name] = got;
      }
      return out;
    });
    await page.closeAll(); assert.deepEqual(r.yona, r.jquery);
  } },
]);
