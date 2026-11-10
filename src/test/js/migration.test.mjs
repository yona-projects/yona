// 이전 화면(`esm/service/Migration.js`, Vue 3 앱): 원본·대상 목록, 프로젝트 선택, 담당자 확인, 마일스톤·이슈·게시글 이전 요청과 가공을 고정한다(시험 채우기, D-123).
// 화면은 실제 템플릿(`migration/home.html`)에서 Thymeleaf 표현만 풀어 Vue가 그리게 하고, `fetch`는 GitHub와 Yona 서버를 흉내 내는 가짜로 바꾼다.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pageWith, classicFromEsm, ROOT } from './harness.mjs';

/** 템플릿에서 앱 조각만 뽑고 Thymeleaf 표현을 풀어 Vue가 읽을 DOM으로 만든다. */
function templateHtml() {
  const text = fs.readFileSync(path.join(ROOT, 'src/main/resources/templates/migration/home.html'), 'utf8');
  const start = text.indexOf('<div id="migration-app"'); const end = text.lastIndexOf('</th:block>');
  return text.slice(start, end)
    .replace(/\[\[#\{([A-Za-z.]+)(?:\([^\]]*\))?\}\]\]/g, '$1')       // [[#{키(인자)}]] → 키
    .replace(/ th:attr="[^"]*"/g, '').replace(/ th:placeholder="[^"]*"/g, '');
}

const SOURCE_LIST = [
  { owner: 'alpha', projectName: 'one', full_name: 'alpha/one', members: 3, private: false },
  { owner: 'alpha', projectName: 'two', full_name: 'alpha/two', members: 1, private: true },
  { owner: 'beta', projectName: 'three', full_name: 'beta/three', members: 2, private: false },
];
const SOURCE_DETAIL = { owner: 'alpha', projectName: 'one', full_name: 'alpha/one', milestoneCount: 1, issueCount: 2, postCount: 1, assignees: [{ login: 'kim', name: 'Kim' }] };
const repo = (owner, type, name) => ({ name, full_name: owner + '/' + name, owner: { login: owner, type }, html_url: 'https://github.com/' + owner + '/' + name, private: false });

/** 가짜 서버. [overrides]로 주소별 응답을 바꾼다. 요청은 window.__calls에 쌓인다. */
const SERVER = `
  window.__calls = [];
  window.__routes = {};
  window.fetch = async function (url, init) {
    init = init || {};
    window.__calls.push({ url: String(url), method: init.method || 'GET', headers: init.headers || {}, body: init.body });
    // "POST 주소" 처럼 메서드가 붙은 키를 먼저 찾고, 없으면 주소만 있는 키를 쓴다
    const line = (init.method || 'GET') + ' ' + String(url);
    const key = Object.keys(window.__routes).find(function (k) { return line.indexOf(k) === 0; }) || Object.keys(window.__routes).find(function (k) { return String(url).indexOf(k) === 0; });
    const res = key ? window.__routes[key] : { status: 404, body: {} };
    const noBody = res.status === 204 || res.status === 304;
    return new Response(noBody ? null : JSON.stringify(res.body === undefined ? {} : res.body), { status: res.status || 200, headers: Object.assign({ 'Content-Type': 'application/json' }, res.headers || {}) });
  };`;

async function open({ token = 'tok123', routes = {}, confirmAnswer = true } = {}) {
  const page = await pageWith({ stubs: false, html: templateHtml().replace('data-oauth-state=""', ''), scripts: [{ content: 'window.Messages = function (k) { return k + (arguments.length > 1 ? "|" + [].slice.call(arguments, 1).join(",") : ""); };' }, 'common/yona.Common.js', 'lib/vue/vue.global.prod.js'] });
  await page.evaluate(([server, routes, token, confirmAnswer]) => {
    const root = document.getElementById('migration-app');
    root.setAttribute('data-oauth-state', 'st'); root.setAttribute('data-token', token); root.setAttribute('data-login-id', 'yonauser'); root.setAttribute('data-email', 'y@e.com');
    window.NProgress = { start() {}, done() {} };
    const store = new Map(); Object.defineProperty(window, 'localStorage', { value: { getItem: (k) => (store.has(k) ? store.get(k) : null), setItem: (k, v) => store.set(k, String(v)), removeItem: (k) => store.delete(k) } });
    window.__confirm = []; window.confirm = (m) => { window.__confirm.push(m); return confirmAnswer; }; window.alert = () => {};
    (0, eval)(server);
    Object.assign(window.__routes, routes);
  }, [SERVER, Object.fromEntries(Object.entries(routes).map(([k, v]) => [k, v])), token, confirmAnswer]);
  return page;
}
const ROUTES = {
  '/migration/projects': { body: SOURCE_LIST },
  'https://api.github.com/user/repos': { body: [repo('myorg', 'Organization', 'repoA'), repo('myorg', 'Organization', 'repoB'), repo('me', 'User', 'mine')] },
  'https://api.github.com/user': { body: { login: 'me' } },
};


const copy = (o) => JSON.parse(JSON.stringify(o));
/** 모듈을 올리고 첫 요청들이 끝나기를 기다린다. [clock]이 참이면 가짜 시계를 써서 시간을 직접 흘린다. */
async function mount(options = {}) {
  // 개별 응답이 기본 응답보다 먼저 맞도록 앞에 둔다(주소 앞부분이 같으면 먼저 나온 것을 쓴다)
  const routes = Object.assign({}, options.routes || {}, copy(ROUTES));
  const page = await open({ ...options, routes });
  if (options.clock) await page.clock.install();
  await page.addScriptTag({ content: classicFromEsm('esm/service/Migration.js') });
  await page.waitForTimeout(300);
  return page;
}
const calls = (page) => page.evaluate(() => window.__calls.map((c) => ({ ...c })));
const urls = (page) => page.evaluate(() => window.__calls.map((c) => c.method + ' ' + c.url));
const clickSource = async (page, i = 0) => { await page.locator('.source-project .project-list').nth(i).click(); await page.waitForTimeout(200); };
const clickDestination = async (page, i = 0) => { await page.locator('.destination-project .project-list').nth(i).click(); await page.waitForTimeout(300); };
const ORG_ADMIN = { 'https://api.github.com/orgs/myorg/memberships/me': { body: { role: 'admin' } } };
const NO_DATA = { 'https://api.github.com/repos/myorg/repoA/milestones': { body: [] }, 'https://api.github.com/repos/myorg/repoA/issues': { body: [] } };

export const tests = [
  { name: '시작: 원본 목록과 GitHub 저장소 목록(Link의 다음 쪽까지)과 내 계정을 읽어 소유자가 바뀌는 첫 항목에만 소유자를 보인다', run: async () => {
    const page = await mount({ routes: {
      'https://api.github.com/user/repos?per_page=100&page=1': { body: [repo('myorg', 'Organization', 'repoA')], headers: { Link: '<https://api.github.com/user/repos?per_page=100&page=2>; rel="next", <x>; rel="last"' } },
      'https://api.github.com/user/repos?per_page=100&page=2': { body: [repo('myorg', 'Organization', 'repoB'), repo('me', 'User', 'mine')] } } });
    const u = await urls(page);
    assert.ok(u.includes('GET /migration/projects')); assert.ok(u.includes('GET https://api.github.com/user/repos?per_page=100&page=2')); assert.ok(u.includes('GET https://api.github.com/user'));
    const ui = await page.evaluate(() => ({ src: [...document.querySelectorAll('.source-project .project-list')].map((e) => e.querySelector('.owner') ? e.querySelector('.owner').textContent.trim() : ''), dst: [...document.querySelectorAll('.destination-project .project-list')].map((e) => e.querySelector('.owner') ? e.querySelector('.owner').textContent.trim() : ''), msgs: [...document.querySelectorAll('.messages')].map((e) => e.textContent.trim()) }));
    assert.deepEqual(ui.src, ['alpha', '', 'beta']); assert.deepEqual(ui.dst, ['myorg', '', 'me']); assert.ok(ui.msgs.includes('OK!')); assert.deepEqual(page.errors, []);
    const h = (await calls(page)).find((c) => /user\/repos/.test(c.url)); assert.equal(h.headers.Authorization, 'token tok123'); await page.close();
  } },
  { name: '시작: 요청 머리글 — GET에는 If-Modified-Since, GitHub 호출에는 Authorization이 붙는다', run: async () => {
    const page = await mount(); const cs = await calls(page);
    assert.equal(cs.find((c) => c.url === '/migration/projects').headers['If-Modified-Since'], 'Mon, 26 Jul 1997 05:00:00 GMT');
    assert.equal(cs.find((c) => c.url === 'https://api.github.com/user').headers.Authorization, 'token tok123'); await page.close();
  } },
  { name: '검색: 입력한 글자가 full_name에 든 항목만 남기고 대소문자는 구분하지 않는다', run: async () => {
    const page = await mount();
    await page.fill('.source-project input', 'THREE'); assert.equal(await page.locator('.source-project .project-list').count(), 1);
    await page.fill('.destination-project input', 'myorg/repob'); assert.equal(await page.locator('.destination-project .project-list').count(), 1); await page.close();
  } },
  { name: '원본 선택: 상세를 읽어 제목에 owner/projectName을 보이고 선택 표시(selected)를 단다', run: async () => {
    const page = await mount({ routes: { '/migration/alpha/projects/one': { body: SOURCE_DETAIL } } });
    await clickSource(page, 0);
    assert.ok((await urls(page)).includes('GET /migration/alpha/projects/one'));
    assert.equal((await page.locator('.source-title .project-name').textContent()).trim(), 'alpha/one'); assert.equal(await page.locator('.source-project .project-list').first().evaluate((e) => e.classList.contains('selected')), true); await page.close();
  } },
  { name: '대상 선택(조직, 관리자): 조직 멤버십을 확인하고 이미 있는 마일스톤, 게시글, 이슈를 조회하며 경고는 없다', run: async () => {
    const page = await mount({ routes: { ...ORG_ADMIN, '/migration/alpha/projects/one': { body: SOURCE_DETAIL }, 'https://api.github.com/repos/myorg/repoA/': { body: [] } } });
    await clickSource(page); await clickDestination(page, 0);
    const u = await urls(page);
    assert.ok(u.includes('GET https://api.github.com/orgs/myorg/memberships/me')); assert.ok(u.includes('GET https://api.github.com/repos/myorg/repoA/milestones?state=all'));
    assert.ok(u.includes('GET https://api.github.com/repos/myorg/repoA/issues?state=all&labels=게시글')); assert.ok(u.includes('GET https://api.github.com/repos/myorg/repoA/issues?state=all'));
    assert.deepEqual(await page.evaluate(() => [$('.project-list.selected .warn-no-worker').css('display'), $('.project-list.selected .warn-user-project').css('display')]), ['none', 'none']);
    assert.equal((await page.locator('.destination-title .project-name').textContent()).trim(), 'myorg/repoA'); await page.close();
  } },
  { name: '대상 선택(조직, 관리자가 아님): 작업 계정 경고와 관리자 아님 경고를 보이고 사용자 토큰으로 바꿔 쓴다', run: async () => {
    const page = await mount({ routes: { 'https://api.github.com/orgs/myorg/memberships/me': { body: { role: 'member' } }, '/migration/alpha/projects/one': { body: SOURCE_DETAIL }, 'https://api.github.com/repos/myorg/repoA/': { body: [] } } });
    await clickSource(page); await clickDestination(page, 0);
    // 경고 요소는 저장소마다 있고 v-show로 가려지므로 선택된 항목 안의 것을 본다
    assert.equal(await page.evaluate(() => $('.destination-project .project-list.selected .warn-no-worker').css('display')), 'block');
    assert.equal(await page.locator('.destination-project .project-list.selected .warn-no-worker .text-bg-warning').count(), 1);
    await page.evaluate(() => { window.__calls.length = 0; }); await page.close();
  } },
  { name: '대상 선택(개인 저장소): "User Project" 경고를 보이고 멤버십은 확인하지 않으며 사용자 토큰을 작업 토큰으로 쓴다', run: async () => {
    const page = await mount({ routes: { '/migration/alpha/projects/one': { body: SOURCE_DETAIL }, 'https://api.github.com/repos/me/mine/': { body: [] } } });
    await clickSource(page); await clickDestination(page, 2);
    assert.equal(await page.evaluate(() => $('.destination-project .project-list.selected .warn-user-project').css('display')), 'block');
    const cs = await calls(page); assert.equal(cs.some((c) => /memberships/.test(c.url)), false);
    assert.equal(cs.find((c) => /repos\/me\/mine\/milestones/.test(c.url)).headers.Authorization, 'token tok123'); await page.close();
  } },
  { name: '담당자 확인: 입력하고 0.3초 뒤 협력자 여부를 묻고 204면 확인 표시와 localStorage에 저장하고, 404면 표시를 해제하며 지운다', run: async () => {
    const page = await mount({ clock: true, routes: { ...ORG_ADMIN, '/migration/alpha/projects/one': { body: SOURCE_DETAIL }, 'https://api.github.com/repos/myorg/repoA/collaborators/kimdest': { status: 204, body: undefined }, 'https://api.github.com/repos/myorg/repoA/collaborators/ghost': { status: 404, body: {} }, 'https://api.github.com/repos/myorg/repoA/': { body: [] } } });
    await clickSource(page); await clickDestination(page, 0);
    const input = page.locator('tr.assignee input');
    await input.fill('kimdest'); await input.dispatchEvent('keyup'); await page.clock.runFor(400); await page.waitForTimeout(200);
    assert.ok((await urls(page)).includes('GET https://api.github.com/repos/myorg/repoA/collaborators/kimdest'));
    assert.equal(await page.evaluate(() => localStorage.getItem('kim')), 'kimdest');
    await input.fill('ghost'); await input.dispatchEvent('keyup'); await page.clock.runFor(400); await page.waitForTimeout(200);
    assert.equal(await page.evaluate(() => localStorage.getItem('kim')), null); await page.close();
  } },

  { name: '마일스톤 이전: 확인 창에서 취소하면 아무 것도 보내지 않는다', run: async () => {
    const page = await mount({ confirmAnswer: false, routes: { ...ORG_ADMIN, '/migration/alpha/projects/one': { body: SOURCE_DETAIL }, ...NO_DATA } });
    await clickSource(page); await clickDestination(page, 0); await page.evaluate(() => { window.__calls.length = 0; });
    await page.getByRole('button').filter({ hasText: 'migration.moveMilestones' }).click(); await page.waitForTimeout(200);
    assert.deepEqual(await urls(page), []); assert.equal((await page.evaluate(() => window.__confirm)).length, 1); await page.close();
  } },
  { name: '마일스톤 이전: 원본 마일스톤을 읽어(withWikiCommit=true) 대상에 하나씩 POST하고, 건수를 세며, 이전 기록 이슈를 남긴다', run: async () => {
    const page = await mount({ routes: { ...ORG_ADMIN, '/migration/alpha/projects/one/milestones': { body: { milestones: [{ milestone: { id: 5, title: 'v1', state: 'open' } }] } },
      'POST https://api.github.com/repos/myorg/repoA/milestones': { status: 201, body: { number: 9 } },
      'https://api.github.com/repos/yona-projects/yona-migration-log/issues': { status: 201, body: {} }, '/migration/alpha/projects/one': { body: SOURCE_DETAIL }, ...NO_DATA } });
    await clickSource(page); await clickDestination(page, 0); await page.evaluate(() => { window.__calls.length = 0; });
    await page.getByRole('button').filter({ hasText: 'migration.moveMilestones' }).click(); await page.waitForTimeout(600);
    const cs = await calls(page);
    assert.ok(cs.some((c) => c.method === 'GET' && c.url === '/migration/alpha/projects/one/milestones?withWikiCommit=true'));
    const post = cs.find((c) => c.method === 'POST' && c.url === 'https://api.github.com/repos/myorg/repoA/milestones');
    assert.deepEqual(JSON.parse(post.body), { id: 5, title: 'v1', state: 'open' }); assert.equal(post.headers.Authorization, 'token ');
    const log = cs.find((c) => c.method === 'POST' && /yona-migration-log\/issues/.test(c.url)); const logBody = JSON.parse(log.body);
    assert.equal(logBody.title, 'alpha/one --> myorg/repoA 마일스톤(1)'); assert.deepEqual(logBody.labels, ['alpha/one --> myorg/repoA', 'myorg', 'attachments']); assert.match(logBody.body, /milestone: 1\nissue: 2\nposting: 1/);
    assert.equal((await page.locator('.progress-bar').textContent()).replace(/\s/g, ''), '1/1');
    assert.equal(await page.locator('.error-data').count(), 0, '이전 오류가 기록되었다'); await page.close();
  } },
  { name: '이슈 이전: 라벨과 이슈 라벨 쌍을 읽고, 담당자와 라벨과 마일스톤을 대상에 맞게 바꿔 0.5초 간격으로 import API에 보낸다', run: async () => {
    const issues = { issues: [
      { issue: { id: 11, title: 'a', assignee: 'kim', body: '', milestoneId: 5 }, comments: [] },
      { issue: { id: 12, title: 'b', assignee: 'nobody', body: 'text', milestoneId: 6 }, comments: [] }] };
    const page = await mount({ clock: true, routes: { ...ORG_ADMIN,
      'POST https://api.github.com/repos/myorg/repoA/milestones': { status: 201, body: { number: 9 } },
      '/migration/alpha/projects/one/milestones': { body: { milestones: [{ milestone: { id: 5, title: 'v1' } }] } },
      '/migration/alpha/projects/one/labels': { body: { labels: { 7: { name: 'bug', categoryName: 'Type' } } } },
      '/migration/alpha/projects/one/issuelabel': { body: { issueLabelPairs: [{ issueId: 11, issueLabelId: 7 }] } },
      '/migration/alpha/projects/one/issues': { body: issues }, 'https://api.github.com/repos/myorg/repoA/import/issues': { status: 202, body: {} },
      'https://api.github.com/repos/yona-projects/yona-migration-log/issues': { status: 201, body: {} },
      'https://api.github.com/repos/myorg/repoA/collaborators/kimdest': { status: 204, body: undefined },
      '/migration/alpha/projects/one': { body: SOURCE_DETAIL }, ...NO_DATA } });
    await clickSource(page); await clickDestination(page, 0);
    // 담당자 kim → kimdest, 마일스톤은 먼저 이전해 번호를 대응시킨다
    const input = page.locator('tr.assignee input'); await input.fill('kimdest'); await input.dispatchEvent('keyup'); await page.clock.runFor(400); await page.waitForTimeout(150);
    await page.getByRole('button').filter({ hasText: 'migration.moveMilestones' }).click(); await page.clock.runFor(1000); await page.waitForTimeout(200);
    await page.evaluate(() => { window.__calls.length = 0; });
    await page.getByRole('button').filter({ hasText: 'migration.moveIssues' }).click(); await page.waitForTimeout(300);
    const early = await urls(page);
    assert.ok(early.includes('GET /migration/alpha/projects/one/labels')); assert.ok(early.includes('GET /migration/alpha/projects/one/issuelabel')); assert.ok(early.includes('GET /migration/alpha/projects/one/issues?withWikiCommit=true'));
    await page.clock.runFor(1500); await page.waitForTimeout(200);
    const posts = (await calls(page)).filter((c) => /import\/issues$/.test(c.url));
    assert.equal(posts.length, 2); assert.equal(posts[0].headers.Accept, 'application/vnd.github.golden-comet-preview+json');
    const first = JSON.parse(posts[0].body).issue; const second = JSON.parse(posts[1].body).issue;
    assert.deepEqual(first, { title: 'a', assignee: 'kimdest', body: '-- no contents --', labels: ['bug - Type'], milestone: 9 });
    assert.deepEqual(second, { title: 'b', body: 'text' }); // 담당자 매핑 없음(삭제), 라벨 쌍 없음(undefined는 JSON에서 빠짐), 대응 없는 마일스톤 6은 매핑 표에 없어 undefined
    assert.equal('assignee' in second, false); assert.equal('id' in first, false); assert.equal('milestoneId' in first, false); await page.close();
  } },
  { name: '게시글 이전: 라벨은 "게시글"로 고정하고 본문이 비면 "-- no contents --"로 채워 import API에 보낸다', run: async () => {
    const page = await mount({ clock: true, routes: { ...ORG_ADMIN, '/migration/alpha/projects/one/posts': { body: { issues: [{ issue: { title: 'p1', body: '' }, comments: [] }] } },
      'https://api.github.com/repos/myorg/repoA/import/issues': { status: 202, body: {} }, 'https://api.github.com/repos/yona-projects/yona-migration-log/issues': { status: 201, body: {} },
      '/migration/alpha/projects/one': { body: SOURCE_DETAIL }, ...NO_DATA } });
    await clickSource(page); await clickDestination(page, 0); await page.evaluate(() => { window.__calls.length = 0; });
    await page.getByRole('button').filter({ hasText: 'migration.movePosts' }).click(); await page.clock.runFor(1500); await page.waitForTimeout(250);
    const cs = await calls(page); const post = cs.find((c) => /import\/issues$/.test(c.url));
    assert.ok(cs.some((c) => c.url === '/migration/alpha/projects/one/posts?withWikiCommit=true'));
    assert.deepEqual(JSON.parse(post.body).issue, { title: 'p1', body: '-- no contents --', labels: ['게시글'] });
    assert.equal(await page.evaluate(() => $('.caution:contains("migration.postsWait")').length > 0), true); await page.close();
  } },
];
