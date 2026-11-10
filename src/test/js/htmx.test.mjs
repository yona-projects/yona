// 목록 화면의 영역 교체를 htmx로 한다(decisions.md D-96, A-401). 실제 htmx.js, yona.Htmx.js, yona.Pagination.js, yona.issue.List.js를
// http 주소(가짜 서버 http://yona.test)에서 불러 검색, 탭, 필터, 페이지 이동, 오류 대체, 뒤로 가기를 확인한다.
// pjax를 쓰던 때와 같은 동작을 지킨다: 영역 안쪽만 바뀌고, 주소가 바뀌고, htmx의 HX-Request 헤더로 요청하고,
// 오류나 목록 영역이 없는 응답은 일반 이동으로 처리하고, 뒤로 가기는 그 주소를 새로 불러온다.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, JS, ROOT } from './harness.mjs';

const ORIGIN = 'http://yona.test';
const START = '/acme/web/issues';

/** 실제 템플릿의 list-container 여는 태그를 그대로 가져온다(속성 값이 템플릿과 같아야 한다). */
function containerTag() {
  const text = fs.readFileSync(path.join(ROOT, 'src/main/resources/templates/issue/partial-list-wrap.html'), 'utf8');
  const attr = `(?:[^>"']|"[^"]*"|'[^']*')*`;
  const tag = text.match(new RegExp(`<div${attr}\\blist-container\\b${attr}>`));
  assert.ok(tag, '템플릿에서 list-container를 찾지 못했다');
  return tag[0];
}

function listBody(query) {
  const q = new URLSearchParams(query);
  return `${containerTag()}
  <form id="search" name="search" action="${START}" hx-get="${START}" method="get">
    <input type="text" name="filter" data-search="filter" value="${q.get('filter') || ''}">
    <input type="hidden" name="state" data-search="state" value="${q.get('state') || 'open'}">
    <input type="hidden" name="orderBy" value="createdDate"><input type="hidden" name="orderDir" value="desc">
    <input type="hidden" name="assigneeId" data-search="assigneeId" value="">
    <input type="text" name="dueDate" data-toggle="calendar" data-search="dueDate" value="">
    <select id="authorId" class="form-select" data-toggle="select2"><option>전체</option></select>
  </form>
  <ul class="nav nav-tabs"><li><a href="#" state="open">열림</a></li><li><a href="#" state="closed">닫힘</a></li></ul>
  <a list-filter href="#" id="mine" data-assignee-id="me">내 이슈</a>
  <ul id="rows"><li class="post-item" data-query="${query}">결과 ${query}</li></ul>
  <script>(function () { var el = document.getElementById('authorId'); if (el) { el.classList.add('script-init'); window.__scriptInits = (window.__scriptInits || 0) + 1; } })();</script>
  <div style="height:2000px"></div>
  <div id="pagination" data-total="3"></div>
</div>`;
}

function fullPage(query, title = 'list') {
  const scripts = ['lib/htmx.js', 'common/yona.Htmx.js', 'esm/Pagination.js', 'esm/service/issue.List.js'];
  // 스크립트를 문서 파싱 중에 불러온다(실제 레이아웃 번들과 같은 시점). 공통 도우미와 대역은 htmx보다 먼저 정한다
  return `<!doctype html><html><head><meta charset="utf-8"><title>${title}</title>
<style>body { margin: 0; }</style>
<script src="${ORIGIN}/assets/lib/jquery/jquery-3.7.1.js"></script>
<script>
  window.__np = { start: 0, done: 0 }; window.__twoColumn = 0; window.__notified = [];
  window.NProgress = { start: function () { window.__np.start++; }, done: function () { window.__np.done++; } };
  window.moment = function (v) { return { isValid: function () { return /^\\d{4}-\\d{2}-\\d{2}$/.test(v); } }; };
  window._initTwoColumnMode = function () { window.__twoColumn++; };
  window.__select2 = 0;
  window.$yona = { createNamespace: function (ns) { var p = ns.split('.'), parent = window, name = null; for (var i = 0; i < p.length; i++) { name = p[i]; if (i === p.length - 1) break; if (typeof parent[name] !== 'object') parent[name] = {}; parent = parent[name]; } return { container: parent, name: name }; },
    notify: function (m) { window.__notified.push(m); }, getTrim: function (s) { return $.trim(s); } };
  window.Messages = function (k) { return k; }; window.yona = window.yona || {}; yona.ui = yona.ui || {}; yona.ui.Select2 = function (el) { window.__select2++; $(el).addClass('select2-hidden-accessible').attr('tabindex', '-1'); };
</script>
${scripts.map((s) => (s.startsWith('esm/') ? `<script type="module" src="${ORIGIN}/assets/${s}"></script>` : `<script src="${ORIGIN}/assets/${s}"></script>`)).join('\n')}
</head><body>
${listBody(query)}
</body></html>`;
}

/** 가짜 서버가 있는 새 페이지를 만든다. `page.xhr`에 htmx 요청(X-PJAX 헤더가 있는 요청)이 쌓인다. */
async function openList({ query = '' } = {}) {
  const b = await launch();
  const context = await b.newContext({ viewport: { width: 1000, height: 700 } });
  const page = await context.newPage();
  page.errors = []; page.xhr = []; page.documents = [];
  page.on('pageerror', (e) => page.errors.push(String(e.message || e)));
  await context.route(`${ORIGIN}/**`, async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    if (url.pathname.startsWith('/assets/')) {
      const file = path.join(JS, url.pathname.slice('/assets/'.length));
      if (!fs.existsSync(file)) return route.fulfill({ status: 404, body: 'missing ' + file });
      return route.fulfill({ path: file, contentType: 'application/javascript' });
    }
    const headers = req.headers();
    const q = url.searchParams;
    if (url.pathname !== START && url.pathname !== `${START}/alt`) return route.fulfill({ status: 404, body: 'not found' });
    if (headers['hx-request']) {
      page.xhr.push({ url: url.pathname + url.search, headers });
      const filter = q.get('filter');
      if (filter === 'fail') return route.fulfill({ status: 500, contentType: 'text/html; charset=utf-8', body: 'server error' });
      if (filter === 'login') return route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: '<html><head><title>login</title></head><body>로그인</body></html>' });
      if (filter === 'slow') { await new Promise((r) => setTimeout(r, 1500)); }
      return route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: listBody(url.search.replace(/^\?/, '')) });
    }
    page.documents.push(url.pathname + url.search);
    if (q.get('filter') === 'login') return route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: '<html><head><title>login</title></head><body>로그인</body></html>' });
    if (q.get('filter') === 'fail' || q.get('filter') === 'slow') return route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: fullPage(url.search.replace(/^\?/, ''), 'full-reload') });
    return route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: fullPage(url.search.replace(/^\?/, ''), q.has('pageNum') || q.has('filter') ? 'full-reload' : 'list') });
  });
  await page.goto(`${ORIGIN}${START}${query}`);
  await page.evaluate(() => { window.__marker = 'same-document'; new yona.issue.List({}); });
  return page;
}

const waitRows = (page, text) => page.waitForFunction((t) => document.querySelector('#rows')?.textContent.includes(t), text, { timeout: 5000 });

export const tests = [
  { name: 'htmx 2.0.11이 있고 pjax는 없다. 이동 이력은 서버에서 새로 불러오게 정했다', async run() {
    const page = await openList();
    const info = await page.evaluate(() => ({ v: htmx.version, cache: htmx.config.historyCacheSize, miss: htmx.config.refreshOnHistoryMiss, pjax: typeof $.pjax, indicator: htmx.config.includeIndicatorStyles, eval: htmx.config.allowEval, settle: htmx.config.attributesToSettle }));
    const errors = page.errors; await page.context().close();
    assert.deepEqual(errors, []);
    assert.deepEqual(info, { v: '2.0.11', cache: 0, miss: true, pjax: 'undefined', indicator: false, eval: false, settle: [] });
  } },
  { name: '검색 폼 제출은 영역 안쪽만 바꾸고 주소를 바꾸며 HX-Request 헤더로 요청한다(문서 이동 없음)', async run() {
    const page = await openList();
    await page.evaluate(() => { window.__container = document.querySelector('[list-container]'); document.querySelector('input[name=filter]').value = 'dark'; document.querySelector('#search').requestSubmit(); });
    await waitRows(page, 'filter=dark');
    const out = await page.evaluate(() => ({ same: document.querySelector('[list-container]') === window.__container, nested: document.querySelectorAll('[list-container] [list-container]').length, marker: window.__marker, loc: location.pathname + location.search, forms: document.querySelectorAll('form[name=search]').length }));
    const xhr = page.xhr; const documents = page.documents; const errors = page.errors; await page.context().close();
    assert.deepEqual(errors, []);
    assert.equal(xhr.length, 1);
    assert.equal(xhr[0].headers['hx-request'], 'true');
    assert.equal(xhr[0].headers['x-pjax'], undefined, '옛 X-PJAX 헤더가 갔다');
    assert.equal(xhr[0].headers['x-requested-with'], undefined, 'X-Requested-With 헤더가 갔다');
    assert.ok(!xhr[0].url.includes('_pjax'), xhr[0].url);
    assert.ok(xhr[0].url.startsWith(`${START}?filter=dark&state=open&orderBy=createdDate&orderDir=desc`), xhr[0].url);
    assert.equal(out.loc, xhr[0].url);
    assert.equal(out.same, true, '컨테이너 요소가 바뀌었다');
    assert.equal(out.nested, 0, '컨테이너가 중첩되었다');
    assert.equal(out.forms, 1);
    assert.equal(out.marker, 'same-document', '문서를 새로 불러왔다');
    assert.deepEqual(documents, [START], '처음 문서 말고 문서 요청이 있다');
  } },
  { name: '교체한 뒤 id가 있는 요소에 초기화(select2)가 붙인 속성이 정착 단계에서 지워지지 않는다', async run() {
    const page = await openList();
    await page.evaluate(() => { document.querySelector('input[name=filter]').value = 'keep'; document.querySelector('#search').requestSubmit(); });
    await waitRows(page, 'filter=keep');
    await page.waitForTimeout(300); // htmx 정착(settle) 단계가 끝난 뒤를 본다
    const out = await page.evaluate(() => ({ cls: document.querySelector('#authorId').className, tab: document.querySelector('#authorId').getAttribute('tabindex'), inits: window.__select2 }));
    await page.context().close();
    assert.ok(out.cls.split(' ').includes('select2-hidden-accessible'), out.cls);
    assert.equal(out.tab, '-1');
    assert.ok(out.inits >= 1, 'select2 초기화가 불리지 않았다');
  } },
  { name: '응답 안의 인라인 스크립트가 교체 중에 붙인 속성도 정착 단계에서 지워지지 않는다(풀 리퀘스트 목록의 select2)', async run() {
    const page = await openList();
    await page.evaluate(() => { document.querySelector('input[name=filter]').value = 'inline'; document.querySelector('#search').requestSubmit(); });
    await waitRows(page, 'filter=inline');
    await page.waitForTimeout(300);
    const out = await page.evaluate(() => ({ cls: document.querySelector('#authorId').className, runs: window.__scriptInits }));
    await page.context().close();
    assert.ok(out.cls.split(' ').includes('script-init'), out.cls);
    assert.ok(out.runs >= 2, '응답 안의 인라인 스크립트가 실행되지 않았다');
  } },
  { name: '닫힘 탭과 필터 링크는 폼을 채워 제출하고, 교체 뒤에도 다시 동작한다', async run() {
    const page = await openList();
    await page.click('a[state=closed]');
    await waitRows(page, 'state=closed');
    await page.click('#mine');
    await waitRows(page, 'assigneeId=me');
    const out = await page.evaluate(() => ({ marker: window.__marker, loc: location.search }));
    const xhr = page.xhr; const errors = page.errors; await page.context().close();
    assert.deepEqual(errors, []);
    assert.equal(xhr.length, 2, JSON.stringify(xhr.map((x) => x.url)));
    assert.ok(xhr[0].url.includes('state=closed'));
    assert.ok(xhr[1].url.includes('assigneeId=me') && xhr[1].url.includes('state=closed'), xhr[1].url);
    assert.equal(out.marker, 'same-document', '교체한 폼이 htmx로 처리되지 않아 문서를 새로 불러왔다');
  } },
  { name: '페이지 링크는 htmx로 요청하고 교체 뒤 페이지 막대를 다시 그린다', async run() {
    const page = await openList();
    const hasBoost = await page.evaluate(() => Array.from(document.querySelectorAll('#pagination a[list-page]')).every((a) => a.getAttribute('hx-boost') === 'true') && document.querySelectorAll('#pagination a[list-page]').length > 0);
    assert.equal(hasBoost, true, '페이지 링크에 hx-boost가 없다');
    await page.click('#pagination li.page-num a[list-page] >> nth=-1');
    await waitRows(page, 'pageNum=2');
    await page.waitForFunction(() => window.__twoColumn >= 2, null, { timeout: 5000 }); // 초기화는 htmx 정착(settle) 뒤에 불린다
    const out = await page.evaluate(() => ({ marker: window.__marker, loc: location.search, links: document.querySelectorAll('#pagination a[list-page]').length, boosted: Array.from(document.querySelectorAll('#pagination a[list-page]')).every((a) => a.getAttribute('hx-boost') === 'true'), twoColumn: window.__twoColumn }));
    const xhr = page.xhr; const errors = page.errors; await page.context().close();
    assert.deepEqual(errors, []);
    assert.equal(xhr.length, 1);
    assert.ok(xhr[0].url.includes('pageNum=2'), xhr[0].url);
    assert.equal(xhr[0].headers['hx-request'], 'true');
    assert.equal(out.marker, 'same-document');
    assert.ok(out.links >= 2 && out.boosted, '페이지 막대가 다시 그려지지 않았다');
    assert.ok(out.twoColumn >= 2, '교체 뒤 2단 보기 초기화가 불리지 않았다');
  } },
  { name: 'Ctrl을 누르고 페이지 링크를 누르면 htmx 요청을 보내지 않는다(새 탭용)', async run() {
    const page = await openList();
    await page.click('#pagination li.page-num a[list-page] >> nth=-1', { modifiers: ['Control'] });
    await page.waitForTimeout(400);
    const xhr = page.xhr; const marker = await page.evaluate(() => window.__marker); await page.context().close();
    assert.equal(xhr.length, 0, JSON.stringify(xhr));
    assert.equal(marker, 'same-document');
  } },
  { name: '요청 중에는 진행 막대를 시작하고 끝나면 끝낸다', async run() {
    const page = await openList();
    await page.evaluate(() => { document.querySelector('input[name=filter]').value = 'bar'; document.querySelector('#search').requestSubmit(); });
    await waitRows(page, 'filter=bar');
    const np = await page.evaluate(() => window.__np);
    await page.context().close();
    assert.equal(np.start, 1);
    assert.ok(np.done >= 1, JSON.stringify(np));
  } },
  { name: '마감일이 잘못되면 요청을 보내지 않고 알린다', async run() {
    const page = await openList();
    await page.evaluate(() => { document.querySelector('input[name=dueDate]').value = 'bad'; document.querySelector('#search').requestSubmit(); });
    await page.waitForTimeout(500);
    const out = await page.evaluate(() => ({ notified: window.__notified, marker: window.__marker }));
    const xhr = page.xhr; const errors = page.errors; await page.context().close();
    assert.deepEqual(errors, []);
    assert.equal(xhr.length, 0, JSON.stringify(xhr));
    assert.deepEqual(out.notified, ['issue.error.invalid.duedate']);
    assert.equal(out.marker, 'same-document');
  } },
  { name: '교체한 뒤 맨 위로 스크롤한다', async run() {
    const page = await openList();
    await page.evaluate(() => { window.scrollTo(0, 600); document.querySelector('input[name=filter]').value = 'top'; document.querySelector('#search').requestSubmit(); });
    await waitRows(page, 'filter=top');
    await page.waitForTimeout(200);
    const out = await page.evaluate(() => ({ y: window.scrollY, marker: window.__marker }));
    await page.context().close();
    assert.equal(out.marker, 'same-document', '문서를 새로 불러왔다');
    assert.equal(out.y, 0);
  } },
  { name: '서버 오류(500)는 일반 이동으로 그 주소를 다시 연다', async run() {
    const page = await openList();
    await page.evaluate(() => { document.querySelector('input[name=filter]').value = 'fail'; document.querySelector('#search').requestSubmit(); });
    await page.waitForFunction(() => document.title === 'full-reload', null, { timeout: 5000 });
    const out = await page.evaluate(() => ({ marker: window.__marker || null, loc: location.search }));
    const documents = page.documents; const xhr = page.xhr; await page.context().close();
    assert.equal(xhr.length, 1, 'htmx 요청을 먼저 보내지 않았다');
    assert.equal(out.marker, null, '문서를 새로 불러오지 않았다');
    assert.ok(out.loc.startsWith('?filter=fail'), out.loc);
    assert.equal(documents.length, 2);
  } },
  { name: '응답에 목록 영역이 없으면(로그인 화면 등) 일반 이동으로 그 주소를 다시 연다', async run() {
    const page = await openList();
    await page.evaluate(() => { document.querySelector('input[name=filter]').value = 'login'; document.querySelector('#search').requestSubmit(); });
    await page.waitForFunction(() => document.title === 'login', null, { timeout: 5000 });
    const body = await page.evaluate(() => document.body.textContent.trim());
    const xhr = page.xhr; await page.context().close();
    assert.equal(xhr.length, 1, 'htmx 요청을 먼저 보내지 않았다');
    assert.equal(body, '로그인');
  } },
  { name: '시간 제한을 넘기면 일반 이동으로 그 주소를 다시 연다', async run() {
    const page = await openList();
    await page.evaluate(() => { document.querySelector('[list-container]').setAttribute('hx-request', '{"timeout":300}'); document.querySelector('input[name=filter]').value = 'slow'; document.querySelector('#search').requestSubmit(); });
    await page.waitForFunction(() => document.title === 'full-reload', null, { timeout: 5000 });
    const marker = await page.evaluate(() => window.__marker || null);
    const xhr = page.xhr; await page.context().close();
    assert.equal(xhr.length, 1, 'htmx 요청을 먼저 보내지 않았다');
    assert.equal(marker, null);
  } },
  { name: '뒤로 가기는 그 주소를 서버에서 새로 불러온다', async run() {
    const page = await openList();
    await page.evaluate(() => { document.querySelector('input[name=filter]').value = 'dark'; document.querySelector('#search').requestSubmit(); });
    await waitRows(page, 'filter=dark');
    await page.goBack();
    await page.waitForFunction(() => !window.__marker && document.querySelector('#rows'), null, { timeout: 5000 });
    const out = await page.evaluate(() => ({ loc: location.pathname + location.search, rows: document.querySelector('#rows').textContent.trim() }));
    const documents = page.documents; await page.context().close();
    assert.equal(out.loc, START);
    assert.equal(out.rows, '결과');
    assert.equal(documents.length, 2, '뒤로 가기가 문서를 새로 요청하지 않았다');
  } },
  { name: '폼의 action을 바꾼 뒤 제출하면 바뀐 action으로 요청한다(풀 리퀘스트 목록의 탭)', async run() {
    const page = await openList();
    await page.evaluate(() => { document.querySelector('#search').setAttribute('action', '/acme/web/issues/alt'); document.querySelector('input[name=filter]').value = 'tab'; document.querySelector('#search').requestSubmit(); });
    await waitRows(page, 'filter=tab');
    const out = await page.evaluate(() => ({ loc: location.pathname + location.search, marker: window.__marker }));
    const xhr = page.xhr; await page.context().close();
    assert.equal(xhr.length, 1);
    assert.ok(xhr[0].url.startsWith(`${START}/alt?filter=tab`), xhr[0].url);
    assert.ok(out.loc.startsWith(`${START}/alt?filter=tab`), out.loc);
    assert.equal(out.marker, 'same-document');
  } },
  { name: 'htmx 속성이 없는 폼은 가로채지 않고 일반 제출한다', async run() {
    const page = await openList();
    await page.evaluate(() => {
      const f = document.createElement('form'); f.id = 'plain'; f.action = '/acme/web/issues'; f.method = 'get';
      f.innerHTML = '<input name="filter" value="plain">'; document.body.appendChild(f); f.requestSubmit();
    });
    await page.waitForFunction(() => location.search.includes('filter=plain') && window.__marker === undefined, null, { timeout: 5000 });
    const xhr = page.xhr; await page.context().close();
    assert.equal(xhr.length, 0, 'htmx 속성이 없는 폼이 가로채졌다');
  } },
];
