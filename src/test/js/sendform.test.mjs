// `$yona.sendForm`(common/yona.Common.js): 작은 폼 전송을 보낸다(관심 등록, 역할 변경, 파일 삭제, 후보 조회). v1.6은 숨은 폼을 만들어 jquery.form 3.09로 보냈고,
// 지금은 `fetch`로 보낸다(B안). 같은 시험을 옛 구현(SF_IMPL=old: 옛 Common.js와 jquery.form을 임시 폴더에서 불러온다)에서 먼저 통과시키고 새 구현에서도 통과시킨다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { launch, JS } from './harness.mjs';

const OLD = process.env.CLAUDE_JOB_DIR ? path.join(process.env.CLAUDE_JOB_DIR, 'tmp/old-sendform') : null;
const IMPL = process.env.SF_IMPL === 'old' ? 'old' : 'new';
const SCRIPTS = IMPL === 'old'
  ? [path.join(JS, 'lib/jquery/jquery-3.7.1.js'), path.join(JS, 'lib/mustache.js'), path.join(OLD, 'jquery.form.js'), path.join(OLD, 'yona.Common.js')]
  : [path.join(JS, 'lib/jquery/jquery-3.7.1.js'), path.join(JS, 'lib/mustache.js'), path.join(JS, 'common/yona.Common.js')];

/** 서버를 흉내 내는 페이지를 연다. [responder](req)는 `{status, type, body}`를 돌려준다. 받은 요청은 `page.reqs`에 쌓인다. */
async function open(responder = () => ({ status: 200, type: 'application/json', body: '{"ok":true}' })) {
  const b = await launch(); const ctx = await b.newContext(); const page = await ctx.newPage(); page.errors = []; page.reqs = [];
  page.on('pageerror', (e) => page.errors.push(String(e.message || e)));
  await page.route('http://sf.test/**', async (route) => {
    const r = route.request(); const u = new URL(r.url());
    if (u.pathname === '/page') return route.fulfill({ contentType: 'text/html', body: '<!doctype html><body></body>' });
    const body = r.postData(); const h = r.headers();
    const req = { method: r.method(), path: u.pathname, query: u.search, type: h['content-type'] || '', xrw: h['x-requested-with'] || '', accept: h['accept'] || '', body: body == null ? null : body };
    page.reqs.push(req); const res = await responder(req);
    if (res.abort) return route.abort();
    return route.fulfill({ status: res.status, contentType: res.type || 'text/plain', headers: res.headers || {}, body: res.body ?? '' });
  });
  await page.goto('http://sf.test/page');
  for (const s of SCRIPTS) await page.addScriptTag({ path: s });
  return { page, ctx };
}
/** sendForm을 부르고 콜백 결과를 모아 돌려준다. [opts]는 페이지 안에서 평가되는 객체 식이다. */
const send = (page, opts) => page.evaluate(async (src) => {
  const out = { load: [], error: [] }; const o = new Function('out', `return ${src};`)(out);
  const rec = (kind) => function (data, status, xhr) { out[kind].push({ data: data, status: status, xhrStatus: xhr && xhr.status, text: xhr && xhr.responseText, range: xhr && xhr.getResponseHeader && xhr.getResponseHeader('Content-Range'), third: typeof xhr }); };
  o.fOnLoad = o.fOnLoad === false ? undefined : rec('load'); o.fOnError = o.fOnError === false ? undefined : function (xhr, status, err) { out.error.push({ xhrStatus: xhr && xhr.status, status: status, text: xhr && xhr.responseText, err: typeof err === 'string' ? err : (err ? 'obj' : '') }); };
  $yona.sendForm(o); await new Promise((r) => setTimeout(r, 400)); out.forms = document.querySelectorAll('form').length; return out;
}, opts);

export const tests = [
  { name: 'sendForm: 기본은 POST이고 값은 urlencoded 본문으로 가며 X-Requested-With가 붙고 숨은 폼은 남지 않는다', run: async () => {
    const { page, ctx } = await open(); const out = await send(page, `{ sURL: '/watch', htData: { a: 1, b: 'x y', c: '한글' } }`);
    const r = page.reqs[0]; assert.equal(r.method, 'POST'); assert.equal(r.path, '/watch'); assert.match(r.type, /^application\/x-www-form-urlencoded/); assert.equal(r.body, 'a=1&b=x+y&c=%ED%95%9C%EA%B8%80');
    assert.equal(r.xrw, 'XMLHttpRequest'); assert.equal(out.forms, 0, '숨은 폼이 남지 않는다'); assert.equal(out.load.length, 1); assert.equal(out.error.length, 0); assert.deepEqual(page.errors, []); await ctx.close();
  } },
  { name: 'sendForm: htData가 없으면 본문 없이(빈 본문) POST로 간다(관심 등록과 해제)', run: async () => {
    const { page, ctx } = await open(() => ({ status: 200, type: 'text/plain', body: 'ok' })); const out = await send(page, `{ sURL: '/watch/1' }`);
    assert.equal(page.reqs[0].method, 'POST'); assert.ok(page.reqs[0].body === null || page.reqs[0].body === ''); assert.equal(out.load.length, 1); await ctx.close();
  } },
  { name: 'sendForm: GET이면 값이 질의 문자열로 붙고(기존 질의가 있으면 &로 잇는다) 본문이 없다', run: async () => {
    const { page, ctx } = await open();
    await send(page, `{ sURL: '/users', htOptForm: { method: 'get' }, htData: { query: 'ab', limit: 8 } }`); await send(page, `{ sURL: '/users?x=1', htOptForm: { method: 'get' }, htData: { query: 'z' } }`);
    assert.deepEqual(page.reqs.map((r) => [r.method, r.path, r.query, r.body]), [['GET', '/users', '?query=ab&limit=8', null], ['GET', '/users', '?x=1&query=z', null]]); await ctx.close();
  } },
  { name: 'sendForm: htOptForm에 method가 없으면 폼 기본인 GET이고, htOptForm 자체가 없으면 POST이다', run: async () => {
    const { page, ctx } = await open(); await send(page, `{ sURL: '/a', htOptForm: { enctype: 'text/plain' }, htData: { k: 1 } }`); await send(page, `{ sURL: '/b', htData: { k: 1 } }`);
    assert.deepEqual(page.reqs.map((r) => r.method), ['GET', 'POST']); await ctx.close();
  } },
  { name: 'sendForm: method는 대소문자를 가리지 않는다(POST, post)', run: async () => {
    const { page, ctx } = await open(); await send(page, `{ sURL: '/a', htOptForm: { method: 'POST' }, htData: { k: 1 } }`); await send(page, `{ sURL: '/b', htOptForm: { method: 'post' }, htData: { k: 2 } }`);
    assert.deepEqual(page.reqs.map((r) => [r.method, r.body]), [['POST', 'k=1'], ['POST', 'k=2']]); await ctx.close();
  } },
  { name: 'sendForm: enctype이 multipart/form-data이면 multipart 본문으로 간다(파일 삭제의 _method=delete)', run: async () => {
    const { page, ctx } = await open(); await send(page, `{ sURL: '/files/1', htData: { _method: 'delete' }, htOptForm: { method: 'post', enctype: 'multipart/form-data' } }`);
    const r = page.reqs[0]; assert.equal(r.method, 'POST'); assert.match(r.type, /^multipart\/form-data; boundary=/); assert.ok(/name="_method"\r?\n\r?\ndelete/.test(r.body), r.body); await ctx.close();
  } },
  { name: 'sendForm: sDataType json이면 Accept에 json을 싣고 응답을 객체로 읽어 콜백에 (data, "success", xhr)를 준다(xhr에서 헤더와 본문 글자를 읽는다)', run: async () => {
    const { page, ctx } = await open(() => ({ status: 200, type: 'application/json', headers: { 'Content-Range': 'items 3/3' }, body: '["alpha","beta"]' }));
    const out = await send(page, `{ sURL: '/projects', htOptForm: { method: 'get' }, sDataType: 'json', htData: { query: 'a' } }`);
    assert.match(page.reqs[0].accept, /application\/json/); assert.deepEqual(out.load, [{ data: ['alpha', 'beta'], status: 'success', xhrStatus: 200, text: '["alpha","beta"]', range: 'items 3/3', third: 'object' }]); await ctx.close();
  } },
  { name: 'sendForm: sDataType이 없으면 응답의 Content-Type으로 읽는다(json이면 객체, 글자면 글자)', run: async () => {
    const a = await open(() => ({ status: 200, type: 'application/json', body: '{"location":"/x"}' })); const oa = await send(a.page, `{ sURL: '/r', htData: { id: 1 } }`);
    assert.deepEqual(oa.load[0].data, { location: '/x' }); assert.equal(oa.load[0].text, '{"location":"/x"}'); await a.ctx.close();
    const b = await open(() => ({ status: 200, type: 'text/html', body: '<b>ok</b>' })); const ob = await send(b.page, `{ sURL: '/r', htData: { id: 1 } }`);
    assert.equal(ob.load[0].data, '<b>ok</b>'); await b.ctx.close();
  } },
  { name: 'sendForm: 200이고 본문이 비었으면 sDataType이 없을 때 성공이다(관심 등록 응답)', run: async () => {
    const { page, ctx } = await open(() => ({ status: 200, type: 'text/plain', body: '' })); const out = await send(page, `{ sURL: '/watch' }`);
    assert.equal(out.load.length, 1); assert.equal(out.error.length, 0); assert.equal(out.load[0].xhrStatus, 200); await ctx.close();
  } },
  { name: 'sendForm: sDataType json인데 본문이 비었거나 JSON이 아니면 오류 처리기가 불린다(parsererror)', run: async () => {
    for (const body of ['', 'not json']) {
      const { page, ctx } = await open(() => ({ status: 200, type: 'application/json', body })); const out = await send(page, `{ sURL: '/r', sDataType: 'json', htData: { a: 1 } }`);
      assert.equal(out.load.length, 0, JSON.stringify(body)); assert.equal(out.error.length, 1); assert.equal(out.error[0].status, 'parsererror'); await ctx.close();
    }
  } },
  { name: 'sendForm: 204는 sDataType json이어도 성공이다', run: async () => {
    const { page, ctx } = await open(() => ({ status: 204, type: 'application/json', body: '' })); const out = await send(page, `{ sURL: '/r', sDataType: 'json' }`);
    assert.equal(out.load.length, 1); assert.equal(out.error.length, 0); await ctx.close();
  } },
  { name: 'sendForm: 4xx, 5xx는 오류 처리기만 부르고 xhr에서 상태와 본문을 읽을 수 있다', run: async () => {
    for (const status of [400, 403, 404, 500]) {
      const { page, ctx } = await open(() => ({ status, type: 'text/plain', body: `err${status}` })); const out = await send(page, `{ sURL: '/r', htData: { a: 1 } }`);
      assert.equal(out.load.length, 0); assert.deepEqual([out.error.length, out.error[0].xhrStatus, out.error[0].status, out.error[0].text], [1, status, 'error', `err${status}`]); await ctx.close();
    }
  } },
  { name: 'sendForm: 연결이 실패하면 오류 처리기가 불린다', run: async () => {
    const { page, ctx } = await open(() => ({ abort: true })); const out = await send(page, `{ sURL: '/r', htData: { a: 1 } }`);
    assert.equal(out.load.length, 0); assert.equal(out.error.length, 1); assert.equal(out.error[0].xhrStatus, 0); await ctx.close();
  } },
  { name: 'sendForm: 콜백을 주지 않아도 오류 없이 지나간다(성공, 실패 모두)', run: async () => {
    const a = await open(); await send(a.page, `{ sURL: '/r', fOnLoad: false, fOnError: false, htData: { a: 1 } }`); assert.deepEqual(a.page.errors, []); await a.ctx.close();
    const b = await open(() => ({ status: 500, body: 'x' })); await send(b.page, `{ sURL: '/r', fOnLoad: false, fOnError: false, htData: { a: 1 } }`); assert.deepEqual(b.page.errors, []); await b.ctx.close();
  } },
  { name: 'sendForm: 값이 배열이나 undefined여도 글자로 바꿔 보낸다(v1.6 속성 이어 붙이기와 같은 글자)', run: async () => {
    const { page, ctx } = await open(); await send(page, `{ sURL: '/r', htData: { list: [1, 2], none: undefined, nul: null, zero: 0, empty: '' } }`);
    assert.equal(page.reqs[0].body, 'list=1%2C2&none=undefined&nul=null&zero=0&empty='); await ctx.close();
  } },
  { name: 'sendForm: 주소의 &amp;는 &로 읽어 보낸다(v1.6은 주소를 폼 action 속성에 넣어 브라우저가 풀어 주었다. 관심 등록 주소가 이렇게 온다)', run: async () => {
    const { page, ctx } = await open(); await send(page, `{ sURL: '/watch?resource.type=board_post&amp;resource.id=4' }`);
    assert.equal(page.reqs[0].path + page.reqs[0].query, '/watch?resource.type=board_post&resource.id=4'); await ctx.close();
  } },
  { name: 'sendForm: 요청은 서로 섞이지 않는다(연달아 보낸 둘이 각자의 콜백을 부른다)', run: async () => {
    const { page, ctx } = await open((r) => ({ status: 200, type: 'text/plain', body: 'r' + r.body }));
    const out = await page.evaluate(async () => { const got = []; $yona.sendForm({ sURL: '/a', htData: { n: 1 }, fOnLoad: (d) => got.push(['a', d]) }); $yona.sendForm({ sURL: '/b', htData: { n: 2 }, fOnLoad: (d) => got.push(['b', d]) }); await new Promise((r) => setTimeout(r, 500)); return got.sort(); });
    assert.deepEqual(out, [['a', 'rn=1'], ['b', 'rn=2']]); await ctx.close();
  } },
  ...(IMPL === 'old' ? [] : [{ name: 'sendForm(결함 수정): 값에 따옴표나 꺾쇠가 있어도 그대로 보낸다(v1.6은 숨은 입력의 속성을 이어 붙여 값이 잘렸다)', run: async () => {
    const { page, ctx } = await open(); await send(page, `{ sURL: '/users', htOptForm: { method: 'get' }, htData: { query: 'a"b<c>&d' } }`);
    assert.equal(page.reqs[0].query, '?query=a%22b%3Cc%3E%26d'); await ctx.close();
  } }]),
];
