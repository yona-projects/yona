// 제목 머리말 연결(`common/yona.TitleHeadAutoCompletion.js`)을 실제 `<title-input>` 번들, 실제 jQuery와 함께 시험한다(D-102, A-408).
// 서버는 `page.route`의 가짜 응답이다. 라벨 선택(단일 선택 분류, select2 갱신, 알림), 진행 표시, 본문 이동(Enter)을 확인한다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, JS } from './harness.mjs';

const BUNDLE = path.join(JS, 'lib/title-input/title-input.min.js');
const HEADS = [
  { name: 'urgent', frequency: 5, category: '', searchText: 'urgent' },
  { name: 'bug', frequency: 0, category: 'Type', categoryId: 1, id: 11, labelColor: 'ff0000', isExclusive: true, searchText: 'bug/Type' },
  { name: 'feature', frequency: 0, category: 'Type', categoryId: 1, id: 12, labelColor: '00aa00', isExclusive: true, searchText: 'feature/Type' },
  { name: 'high', frequency: 0, category: 'Priority', categoryId: 2, id: 21, labelColor: '0000ff', isExclusive: true, searchText: 'high/Priority' },
];
const FORM = `<form id="f"><title-input id="title" name="title" maxlength="250" tabindex="1" class="text title"></title-input>
  <textarea id="body" tabindex="2"></textarea>
  <select id="labelIds" multiple><optgroup label="Type"><option value="11" data-category-id="1">bug</option><option value="12" data-category-id="1">feature</option></optgroup>
  <optgroup label="Priority"><option value="21" data-category-id="2">high</option></optgroup></select></form>`;

/** 번들과 연결 모듈을 불러온 페이지를 만든다. [withLabels]가 거짓이면 라벨 `<select>`가 없는 화면이다. */
async function setup({ withLabels = true } = {}) {
  const page = await pageWith({ html: FORM.replace(withLabels ? '' : /<select[\s\S]*<\/select>/, '') });
  page.requests = [];
  await page.route('http://yona.test/**', (route) => {
    const u = new URL(route.request().url());
    page.requests.push({ query: u.searchParams.get('query'), accept: route.request().headers()['accept'] });
    route.fulfill({ contentType: 'application/json', headers: { 'access-control-allow-origin': '*' }, body: JSON.stringify({ result: HEADS }) });
  });
  await page.evaluate(() => {
    window.__notes = []; window.__np = [];
    window.$yona.notify = (m) => window.__notes.push(m);
    window.NProgress = { start: () => window.__np.push('start'), done: () => window.__np.push('done') };
  });
  await page.addScriptTag({ path: BUNDLE, type: 'module' });
  await page.addScriptTag({ path: path.join(JS, 'common/yona.TitleHeadAutoCompletion.js') });
  await page.waitForFunction(() => customElements.get('title-input'));
  await page.evaluate(() => yonaTitleHeadModule({ target: 'title-input#title', url: 'http://yona.test/titleHeads' }));
  return page;
}
const input = (page) => page.locator('title-input input');
const pick = async (page, text) => {
  await input(page).click(); await input(page).fill(''); await input(page).pressSequentially(text, { delay: 25 });
  await page.locator('title-input [role=listbox]').waitFor({ state: 'visible', timeout: 3000 });
  await page.keyboard.press('Enter'); await page.waitForTimeout(150);
};
const selected = (page) => page.evaluate(() => Array.from(document.getElementById('labelIds').selectedOptions).map((o) => o.value));
const value = (page) => page.evaluate(() => document.getElementById('title').value);
const done = async (page) => { assert.deepEqual(page.errors, [], '페이지 오류'); await page.close(); };

export const tests = [
  { name: 'url 옵션이 src가 되어 서버에 v1.6과 같은 질의로 묻는다', run: async () => {
    const page = await setup();
    await input(page).click(); await input(page).pressSequentially('[bu', { delay: 25 });
    await page.locator('title-input [role=listbox]').waitFor({ state: 'visible', timeout: 3000 });
    assert.equal(await page.evaluate(() => document.getElementById('title').getAttribute('src')), 'http://yona.test/titleHeads');
    assert.equal(page.requests.at(-1).query, 'bu'); assert.match(page.requests.at(-1).accept, /json/);
    await done(page);
  } },
  { name: '라벨을 고르면 select에서 선택되고 글자는 남지 않으며 알림이 뜬다', run: async () => {
    const page = await setup();
    await pick(page, '[bu');
    assert.deepEqual(await selected(page), ['11']); assert.equal(await value(page), '');
    assert.deepEqual(await page.evaluate(() => window.__notes), ['Label: bug']);
    await done(page);
  } },
  { name: '같은 단일 선택 분류의 라벨을 고르면 교체하고 다른 분류는 함께 선택된다', run: async () => {
    const page = await setup();
    await pick(page, '[bu'); await pick(page, '[fe');
    assert.deepEqual(await selected(page), ['12']);
    await pick(page, '[hi');
    assert.deepEqual((await selected(page)).sort(), ['12', '21']);
    await done(page);
  } },
  { name: '키워드는 "[이름]" 글자로 들어가고 select는 바뀌지 않는다', run: async () => {
    const page = await setup();
    await pick(page, '[ur');
    assert.equal(await value(page), '[urgent]'); assert.deepEqual(await selected(page), []);
    await done(page);
  } },
  { name: '라벨 select가 없는 화면에서는 라벨도 "[이름]" 글자로 들어간다(v1.6의 else 분기)', run: async () => {
    const page = await setup({ withLabels: false });
    await pick(page, '[bu');
    assert.equal(await value(page), '[bug]');
    await done(page);
  } },
  { name: '조회 시작과 끝에 NProgress를 부른다', run: async () => {
    const page = await setup();
    await pick(page, '[bu'); await page.waitForTimeout(100);
    assert.deepEqual(await page.evaluate(() => window.__np.slice(0, 2)), ['start', 'done']);
    await done(page);
  } },
  { name: 'yona.issue.Write.js처럼 jQuery로 값 읽기, focus(), keydown(Enter)을 쓰면 본문으로 포커스가 간다', run: async () => {
    const page = await setup();
    await page.evaluate(() => {
      $('#title').on('keydown', (e) => { if ((e.keyCode || e.which) === 13) { e.preventDefault(); $('#body').focus(); } });
      $('#title').focus();
    });
    assert.equal(await page.evaluate(() => document.getElementById('title').shadowRoot.activeElement?.tagName), 'INPUT');
    await input(page).fill('제목'); assert.equal(await page.evaluate(() => $('#title').val()), '제목');
    await page.evaluate(() => { window.__submitted = 0; document.getElementById('f').addEventListener('submit', (e) => { e.preventDefault(); window.__submitted++; }); });
    await page.keyboard.press('Enter'); await page.waitForTimeout(200);
    assert.equal(await page.evaluate(() => document.activeElement.id), 'body');
    assert.equal(await page.evaluate(() => window.__submitted), 0, 'Enter로 폼이 제출되면 안 된다');
    await done(page);
  } },
  { name: 'jQuery val(값)으로 쓴 제목이 폼 제출 값에 들어간다(draft 복원 등)', run: async () => {
    const page = await setup();
    await page.evaluate(() => $('#title').val('복원한 제목'));
    assert.equal(await page.evaluate(() => new FormData(document.getElementById('f')).get('title')), '복원한 제목');
    await done(page);
  } },
];
