import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pageWith, ROOT, JS, classicFromEsm } from './harness.mjs';

// select2 4.1.0과 `yona.ui.Select2.js`의 동작 시험이다(decisions.md D-95, architecture/select2-upgrade-plan.md).
// 실제 select2.js와 실제 `common/select2.html`의 항목 모양 템플릿을 불러, 마우스로 여는 것과 같은 순서로 목록을 열고 고른다.

/** `common/select2.html`의 `<script id="tpl…" type="text/x-jquery-tmpl">` 블록만 뽑는다(서버가 싣는 것과 같다). */
function templateBlocks() {
  const text = fs.readFileSync(path.join(ROOT, 'src/main/resources/templates/common/select2.html'), 'utf8');
  return (text.match(/<script id="tpl[^>]*>[\s\S]*?<\/script>/g) || []).join('\n');
}

async function open(html, { wait = true } = {}) {
  const page = await pageWith({
    html: `${templateBlocks()}<form id="f">${html}</form>`,
    scripts: ['lib/select2/select2.js'],
  });
  await page.addScriptTag({ content: 'window.Messages = function (k) { return k; };' });
  await page.addScriptTag({ path: path.join(JS, 'common/yona.ui.Select2.js') });
  page.setDefaultTimeout(3000);
  // yona.ui.Select2.js 끝의 `$(function(){ … })`가 문서 준비 뒤에 모든 `[data-toggle="select2"]`를 초기화한다
  if (wait) await page.waitForSelector('.select2-container', { state: 'attached' });
  return page;
}

const openList = async (page, selector) => {
  await page.click(`${selector} + .select2-container .select2-selection`);
  await page.waitForSelector('.select2-dropdown .select2-results__option');
};
const texts = (page) => page.$$eval('.select2-results__option', (els) => els.map((e) => e.textContent.replace(/\s+/g, ' ').trim()));
const formValue = (page, name) => page.evaluate((n) => new FormData(document.getElementById('f')).getAll(n), name);

const USER_SELECT = `<select id="u" name="assignee" data-toggle="select2" data-format="user">
  <option value="">없음</option>
  <option value="1" data-avatar-url="/a/1.png" data-login-id="kim">김철수</option>
  <option value="2" data-avatar-url="/a/2.png" data-login-id="lee">이영희</option>
</select>`;

export const tests = [
  {
    name: '4.1.0이 불러와지고 선택 상자가 select2 컨테이너로 바뀐다',
    async run() {
      const page = await open(USER_SELECT);
      assert.equal(await page.evaluate(() => $.fn.select2.amd && 'amd'), 'amd', '4.x에는 AMD 로더가 있다');
      assert.equal(await page.locator('#u + .select2-container .select2-selection').count(), 1);
      assert.deepEqual(page.errors, []);
    },
  },
  {
    name: 'user 형식: 목록에 아바타, 이름, @로그인ID가 보이고 로그인ID로 검색된다',
    async run() {
      const page = await open(USER_SELECT);
      await openList(page, '#u');
      const first = await page.$$eval('.select2-results__option .usf-group', (els) => els.map((e) => ({
        img: e.querySelector('img') && e.querySelector('img').getAttribute('src'),
        name: e.querySelector('.name').textContent, loginId: e.querySelector('.loginid').textContent })));
      assert.deepEqual(first, [{ img: '/a/1.png', name: '김철수', loginId: '@kim' }, { img: '/a/2.png', name: '이영희', loginId: '@lee' }]);
      await page.fill('.select2-search__field', 'lee');
      assert.equal((await texts(page)).length, 1);
      assert.match((await texts(page))[0], /이영희/);
    },
  },
  {
    name: 'user 형식: 고르면 선택 칸에 같은 모양이 보이고 폼 값은 option 값이다',
    async run() {
      const page = await open(USER_SELECT);
      await openList(page, '#u');
      await page.click('.select2-results__option:has-text("김철수")');
      assert.equal(await page.locator('.select2-selection__rendered .usf-group .name').textContent(), '김철수');
      assert.deepEqual(await formValue(page, 'assignee'), ['1']);
    },
  },
  {
    name: 'user 형식: 아바타가 없는 항목은 이름만 보인다',
    async run() {
      const page = await open(`<select id="u" data-toggle="select2" data-format="user"><option value="1">홍길동</option></select>`);
      await openList(page, '#u');
      assert.equal(await page.locator('.select2-results__option .usf-group').count(), 0);
      assert.equal((await texts(page))[0], '홍길동');
    },
  },
  {
    name: 'projects 형식: 소유자와 프로젝트 이름이 보이고 소유자로 검색된다',
    async run() {
      const page = await open(`<select id="p" data-toggle="select2" data-format="projects">
        <option value="1" data-avatar-url="/logo/1.png" data-owner="kim /">proj1</option>
        <option value="2" data-avatar-url="project_default_logo.png" data-owner="lee /">proj2</option></select>`);
      await openList(page, '#p');
      const rows = await page.$$eval('.select2-results__option .usf-group', (els) => els.map((e) => ({
        hasImg: !!e.querySelector('img'), owner: e.querySelector('.loginid').textContent, name: e.querySelector('.name').textContent })));
      assert.deepEqual(rows, [{ hasImg: true, owner: 'kim /', name: 'proj1' }, { hasImg: false, owner: 'lee /', name: 'proj2' }]);
      await page.fill('.select2-search__field', 'lee');
      assert.equal((await texts(page)).length, 1);
    },
  },
  {
    name: 'milestone 형식: 상태가 있으면 "[상태] 이름" 제목으로 보이고 없으면 이름만 보인다',
    async run() {
      const page = await open(`<select id="m" data-toggle="select2" data-format="milestone">
        <option value="">없음</option><option value="1" data-state="OPEN">1차</option></select>`);
      await openList(page, '#m');
      const titles = await page.$$eval('.select2-results__option > div[title]', (els) => els.map((e) => e.getAttribute('title')));
      assert.deepEqual(titles, ['[milestone.state.open] 1차']);
      assert.equal((await texts(page))[0], '없음');
    },
  },
  {
    name: 'branch 형식: refs/heads 는 branch, refs/tags 는 tag 배지로 보이고 구역 제목은 글자만 보인다',
    async run() {
      const page = await open(`<select id="b" data-toggle="select2" data-format="branch">
        <optgroup label="브랜치"><option value="refs/heads/master">refs/heads/master</option></optgroup>
        <optgroup label="태그"><option value="refs/tags/v1">refs/tags/v1</option></optgroup></select>`);
      await openList(page, '#b');
      const badges = await page.$$eval('.select2-results__option .branch-label', (els) => els.map((e) => `${e.className.replace('branch-label ', '')}:${e.textContent}|${e.parentElement.textContent.trim()}`));
      assert.deepEqual(badges, ['branch:branch|branch master', 'tag:tag|tag v1']);
      assert.equal(await page.locator('.select2-results__group .branch-label').count(), 0);
    },
  },
  {
    name: 'issuelabel 형식: 배타 분류에서는 같은 분류의 앞선 선택이 빠지고 아닌 분류는 쌓인다',
    async run() {
      const page = await open(`<select id="labelIds" name="labelIds" multiple style="width:300px" data-toggle="select2" data-format="issuelabel" data-allow-clear="true" data-placeholder="라벨" data-dropdown-css-class="issue-labels">
        <optgroup label="크기" data-category-id="1" data-category-is-exclusive="true">
          <option value="11" data-category-id="1" data-category-is-exclusive="true">S</option>
          <option value="12" data-category-id="1" data-category-is-exclusive="true">L</option></optgroup>
        <optgroup label="종류" data-category-id="2" data-category-is-exclusive="false">
          <option value="21" data-category-id="2" data-category-is-exclusive="false">버그</option>
          <option value="22" data-category-id="2" data-category-is-exclusive="false">문서</option></optgroup></select>`);
      // 구역 제목 줄(role=group)도 자식 글자를 포함하므로 항목(role=option)만, 글자가 정확히 같은 것을 고른다
      const pick = async (label) => {
        // 칩의 "×"를 누르지 않도록 선택 칸 안의 검색 칸을 눌러 연다
        await page.click('#labelIds + .select2-container .select2-search__field');
        await page.locator('.select2-results__option[role="option"]', { hasText: new RegExp(`^\\s*${label}\\s*$`) }).click();
      };
      await pick('S'); await pick('L');
      assert.deepEqual(await formValue(page, 'labelIds'), ['12'], '배타 분류는 마지막 선택만 남는다');
      await pick('버그'); await pick('문서');
      assert.deepEqual((await formValue(page, 'labelIds')).sort(), ['12', '21', '22'], '비배타 분류는 쌓인다');
      assert.equal(await page.locator('.select2-results__group .issue-label').count(), 0);
    },
  },
  {
    name: 'issuelabel 형식: 라벨은 badge 로 그려지고 분류 제목에는 툴팁 아이콘이 붙는다',
    async run() {
      const page = await open(`<select id="labelIds" multiple data-toggle="select2" data-format="issuelabel">
        <optgroup label="크기" data-category-id="1" data-category-is-exclusive="true"><option value="11" data-category-id="1" data-category-is-exclusive="true">S</option></optgroup></select>`);
      await page.click('#labelIds + .select2-container .select2-selection');
      assert.equal(await page.locator('.select2-results__option .issue-label[data-label-id="11"]').count(), 1);
      assert.equal(await page.locator('.select2-results__group i.category-exclusive').count(), 1);
    },
  },
  {
    name: '컨테이너 클래스와 드롭다운 클래스 속성(data-container-css-class, data-dropdown-css-class)이 적용된다',
    async run() {
      const page = await open(`<select id="s" data-toggle="select2" data-container-css-class="fullsize bordered" data-dropdown-css-class="branches"><option value="1">a</option></select>`);
      assert.equal(await page.locator('#s + .select2-container.fullsize.bordered').count(), 1);
      await openList(page, '#s');
      assert.equal(await page.locator('.select2-dropdown.branches').count(), 1);
    },
  },
  {
    name: 'allowClear: 지우기 단추로 선택을 비우면 폼 값이 빈 문자열이 된다',
    async run() {
      const page = await open(`<select id="c" name="c" data-toggle="select2" data-allow-clear="true" data-placeholder="선택"><option></option><option value="1" selected>하나</option></select>`);
      assert.equal(await page.locator('.select2-selection__clear').count(), 1);
      await page.click('.select2-selection__clear');
      assert.deepEqual(await formValue(page, 'c'), ['']);
    },
  },
  {
    name: '폭은 data-width로 정한다: 100%는 부모 폭을, 220px은 그 값을 컨테이너가 갖는다 (D-100)',
    async run() {
      const page = await open(`<div id="wrap" style="width:300px"><select id="full" data-toggle="select2" data-width="100%"><option>가</option></select></div>
        <div style="width:600px"><select id="fixed" data-toggle="select2" data-width="220px"><option>나</option></select></div>`);
      // 컨테이너 폭이 보이려면 select2 CSS가 필요하다(인라인 span은 폭을 갖지 못한다)
      await page.addStyleTag({ path: path.join(JS, 'lib/select2/select2.css') });
      const widths = await page.evaluate(() => ({
        full: document.querySelector('#full + .select2-container').getBoundingClientRect().width,
        fixed: document.querySelector('#fixed + .select2-container').getBoundingClientRect().width,
        fullStyle: document.querySelector('#full + .select2-container').style.width,
      }));
      assert.deepEqual(widths, { full: 300, fixed: 220, fullStyle: '100%' });
      assert.deepEqual(page.errors, []);
    },
  },
  {
    name: '한국어 문구: 공식 4.1.0 i18n(ko)을 싣고 기본 언어를 정하면 결과가 없을 때 "결과가 없습니다."가 보인다',
    async run() {
      const page = await open(USER_SELECT);
      await page.addScriptTag({ path: path.join(JS, 'lib/select2/i18n/ko.js') });
      await page.evaluate(() => { $.fn.select2.defaults.set('language', 'ko'); $('#u').select2('destroy'); yona.ui.Select2('#u'); });
      await openList(page, '#u');
      await page.fill('.select2-search__field', 'zzz');
      await page.waitForSelector('.select2-results__message');
      assert.equal((await page.locator('.select2-results__message').textContent()).trim(), '결과가 없습니다.');
    },
  },
];

export const guardTests = [
  {
    name: '없는 요소를 넘겨도 오류 없이 빈 jQuery 객체를 돌려준다 (풀 리퀘스트 보낸 목록의 #contributors)',
    async run() {
      const page = await open('<p>선택 상자 없음</p>', { wait: false });
      const length = await page.evaluate(() => yona.ui.Select2('#contributors').length);
      assert.equal(length, 0);
      assert.deepEqual(page.errors, []);
    },
  },
];
tests.push(...guardTests);

// ---- 숨은 input이던 원격 검색 선택 상자 4곳(담당자, 공유자, 리뷰 요청, 풀 리퀘스트 담당자와 라벨)

const API = 'http://yona.test';
const KIM = { loginId: 'kim', name: '김철수', avatarUrl: '/a/kim.png', type: 'user' };
const LEE = { loginId: 'lee', name: '이영희', avatarUrl: '/a/lee.png', type: 'user' };

/** 원격 API를 흉내 낸다. [routes]는 `경로 접두어 → (요청) => 응답 JSON`이고, 모든 요청은 `calls`에 쌓인다. */
async function open2(html, scripts, routes) {
  const page = await open(html, { wait: false });
  const calls = [];
  await page.route(`${API}/**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    calls.push({ method: request.method(), path: url.pathname, query: Object.fromEntries(url.searchParams), body: request.postData() });
    const key = Object.keys(routes).find((k) => url.pathname.startsWith(k));
    const body = key ? routes[key](url, request) : {};
    await route.fulfill({ status: 200, contentType: 'application/json', headers: { 'access-control-allow-origin': '*' }, body: JSON.stringify(body) });
  });
  // 실제 Common.js의 notify와 자체 도우미 tmpl(`${키}` 치환) 대신 쓰는 같은 동작의 대역이다
  await page.addScriptTag({ content: `window.$yona.notify = function (m) { (window.__notes = window.__notes || []).push(m); };
    window.$yona.tmpl = function (tpl, data) { return tpl.replace(/\\$\\{(\\w+)\\}/g, function (m, k) { return data[k]; }); };` });
  for (const s of scripts) await page.addScriptTag(s.startsWith('esm/') ? { content: classicFromEsm(s) } : { path: path.join(JS, s) });
  return { page, calls };
}

export const remoteTests = [
  {
    name: '담당자: 미리 선택된 loginId를 서버에서 읽어 아바타 모양으로 채우고 폼 값은 loginId다',
    async run() {
      const { page, calls } = await open2(
        `<select id="assignee" name="assigneeLoginId" data-placeholder="없음" style="width:300px"><option value=""></option><option value="kim" selected>kim</option></select>`,
        ['esm/service/issue.Assginee.js'], { '/users': () => [KIM] });
      await page.evaluate((api) => yonaAssgineeModule(`${api}/users`, null, '담당자'), API);
      await page.waitForSelector('.select2-selection__rendered .usf-group .name');
      assert.equal(await page.locator('.select2-selection__rendered .usf-group .name').textContent(), '김철수');
      assert.deepEqual(calls.map((c) => c.query), [{ query: 'kim', type: 'loginId' }]);
      assert.deepEqual(await formValue(page, 'assigneeLoginId'), ['kim']);
    },
  },
  {
    name: '담당자: 검색어로 후보를 찾아 고르면 변경 API를 부르고 폼 값이 바뀐다',
    async run() {
      const { page, calls } = await open2(
        `<select id="assignee" name="assigneeLoginId" data-placeholder="없음" style="width:300px"><option value=""></option></select>`,
        ['esm/service/issue.Assginee.js'],
        { '/users': (url) => (url.searchParams.get('query') === 'le' ? [LEE] : []), '/assign': () => ({ assignee: { name: '이영희' } }) });
      await page.evaluate((api) => yonaAssgineeModule(`${api}/users`, `${api}/assign`, '담당자'), API);
      await page.click('.select2-selection');
      await page.fill('.select2-search__field', 'le');
      await page.locator('.select2-results__option[role="option"] .usf-group').first().click();
      await page.waitForFunction(() => (window.__notes || []).length > 0);
      const post = calls.find((c) => c.method === 'POST');
      assert.deepEqual(JSON.parse(post.body), { assignees: ['lee'] });
      assert.deepEqual(await formValue(page, 'assigneeLoginId'), ['lee']);
      assert.equal(await page.evaluate(() => window.__notes[0]), '담당자: 이영희');
    },
  },
  {
    name: '공유자: 미리 선택된 공유자의 유형을 읽어 채우고 추가와 삭제를 변경 API로 알리며 개수를 갱신한다',
    async run() {
      const { page, calls } = await open2(
        `<span class="issue-sharer-count"></span><select id="issueSharer" multiple data-placeholder="공유자" style="width:300px">
           <option value="kim" selected>김철수</option></select>`,
        ['esm/service/issue.Sharer.js'],
        { '/byids': () => [KIM], '/sharable': (url) => (url.searchParams.get('query') === 'le' ? [LEE] : []), '/share': () => ({ action: 'add', sharer: 'x' }) });
      await page.evaluate((api) => yonaIssueSharerModule(`${api}/byids`, `${api}/sharable`, `${api}/share`, '공유자'), API);
      await page.waitForSelector('.select2-selection__choice .usf-group .name');
      assert.deepEqual(calls[0].query, { query: 'kim' });
      // 추가
      await page.click('.select2-search__field');
      await page.fill('.select2-search__field', 'le');
      await page.locator('.select2-results__option[role="option"] .usf-group').first().click();
      await page.waitForFunction(() => document.querySelector('.issue-sharer-count').textContent === '2');
      // 삭제 (처음 칩의 ×)
      await page.locator('.select2-selection__choice__remove').first().click();
      await page.waitForFunction(() => document.querySelector('.issue-sharer-count').textContent === '1');
      const posts = calls.filter((c) => c.method === 'POST').map((c) => JSON.parse(c.body));
      assert.deepEqual(posts, [{ sharer: { loginId: 'lee', type: 'user' }, action: 'add' }, { sharer: { loginId: 'kim', type: 'user' }, action: 'delete' }]);
    },
  },
  {
    name: '리뷰 요청: 한 글자 이상 입력해 고르면 요청 API로 reviewer를 보낸다',
    async run() {
      const { page, calls } = await open2(
        `<div id="review-area"><select id="review-request" data-placeholder="요청" style="width:300px" data-candidates-url="${API}/cand" data-request-url="${API}/req" data-timeline-url=""><option value=""></option></select></div>`,
        ['service/yona.pullrequest.Reviewers.js'], { '/cand': () => [LEE], '/req': () => ({}) });
      await page.click('.select2-selection');
      await page.fill('.select2-search__field', 'l');
      await page.locator('.select2-results__option[role="option"] .usf-group').first().click();
      await page.waitForFunction(() => (window.__notes || []).length > 0);
      assert.deepEqual(JSON.parse(calls.find((c) => c.path === '/req').body), { reviewer: 'lee' });
    },
  },
  {
    name: '풀 리퀘스트 담당자: 지우면 빈 담당자를 보내고, 라벨은 붙이고 뗀 번호만 보낸다',
    async run() {
      const { page, calls } = await open2(
        `<div id="pr-meta" data-assignees-url="${API}/assignees" data-labels-url="${API}/labels" data-timeline-url="${API}/timeline" data-candidates-url="${API}/cand">
           <select id="assignee" name="assigneeLoginId" data-placeholder="없음" style="width:300px"><option value=""></option>
             <option value="kim" selected data-name="김철수" data-avatar-url="/a/kim.png">김철수</option></select>
           <select id="labelIds" name="labelIds" multiple style="width:300px" data-toggle="select2" data-format="issuelabel" data-placeholder="라벨">
             <optgroup label="크기" data-category-id="1" data-category-is-exclusive="true">
               <option value="11" data-category-id="1" data-category-is-exclusive="true" selected>S</option>
               <option value="12" data-category-id="1" data-category-is-exclusive="true">L</option></optgroup></select></div>
         <ul class="board-comment-wrap"></ul>`,
        ['service/yona.pullrequest.Meta.js'], { '/assignees': () => ({}), '/labels': () => ({}), '/timeline': () => '' });
      await page.waitForSelector('#assignee + .select2-container .usf-group .name');
      assert.equal(await page.locator('#assignee + .select2-container .usf-group .name').textContent(), '김철수', '담당자는 option의 data-*로 채워진다');
      // 라벨: S → L (같은 배타 분류라 S가 빠진다)
      await page.click('#labelIds + .select2-container .select2-search__field');
      await page.locator('.select2-results__option[role="option"]', { hasText: /^\s*L\s*$/ }).click();
      await page.waitForFunction(() => (window.__notes || []).length > 0);
      const label = calls.find((c) => c.path === '/labels');
      assert.equal(label.body.includes('attachingLabelIds=12') && label.body.includes('detachingLabelIds=11'), true, label.body);
      // 담당자 지우기
      await page.click('#assignee + .select2-container .select2-selection__clear');
      await page.waitForFunction(() => (window.__notes || []).length > 1);
      assert.deepEqual(JSON.parse(calls.find((c) => c.path === '/assignees').body), { assignees: [] });
    },
  },
  {
    name: '리뷰 요청: 성공하면 현재 화면 주소를 다시 읽어 #review-area 안쪽만 바꾸고 읽어 온 문서의 script는 실행하지 않는다 (S-13, A-427)',
    async run() {
      const { page } = await open2(
        `<div id="review-area"><select id="review-request" data-placeholder="요청" style="width:300px" data-candidates-url="${API}/cand" data-request-url="${API}/req" data-timeline-url=""><option value=""></option></select></div>`,
        ['service/yona.pullrequest.Reviewers.js'], { '/cand': () => [LEE], '/req': () => ({}) });
      await page.evaluate(() => { window.__ran = 0; ((window.yona = window.yona || {}).http = (window.yona.http || {})).get = function (url, cb) { window.__got = url; cb('<!doctype html><html><head><script>window.__ran++</script></head><body><div id="other">x</div><div id="review-area"><p class="fresh">새 리뷰 영역</p><script>window.__ran++</script></div></body></html>'); }; });
      await page.click('.select2-selection');
      await page.fill('.select2-search__field', 'l');
      await page.locator('.select2-results__option[role="option"] .usf-group').first().click();
      await page.waitForSelector('#review-area .fresh', { timeout: 5000 });
      assert.equal(await page.evaluate(() => document.querySelector('#review-area .fresh').textContent), '새 리뷰 영역');
      assert.equal(await page.evaluate(() => document.querySelectorAll('#review-area script').length + window.__ran), 0, 'script는 가져오지도 실행하지도 않는다');
      assert.equal(await page.evaluate(() => document.getElementById('other')), null, '#review-area 밖은 가져오지 않는다');
    },
  },
];
tests.push(...remoteTests);
