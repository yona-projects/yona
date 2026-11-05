import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

// 코드 브라우저(yobi.code.Browser.js)의 폴더 목록 화면. `dir` 폴더를 서버가 그린 상태이다.
const HTML = `
  <style>.x{}</style>
  <div id="breadcrumbs"></div><select id="branches"></select><a id="new-file-link" href="/new?branch=master"></a>
  <div class="code-viewer-wrap">
    <div class="row listitem" data-path="dir"><a data-type="folder" data-targetpath="dir" href="/b/dir">dir</a></div>
    <div class="list-wrap" data-type="folder" data-listpath="dir">
      <div class="row listitem" data-path="dir/a"><a data-type="folder" data-targetpath="dir/a" href="/b/dir/a">a</a></div>
      <div class="row listitem" data-path="dir/cb-x"><a data-type="folder" data-targetpath="dir/cb-x" href="/b/dir/cb-x">cb-x</a></div>
    </div>
    <div class="row listitem" data-path="cb-x"><a data-type="folder" data-targetpath="cb-x" href="/b/cb-x">cb-x</a></div>
  </div>`;
const STUBS = `
  window.NProgress = { start: function () {}, done: function () {} };
  window.moment = function () { return { fromNow: function () { return ''; } }; };
  $yobi.xssClean = function (s) { return s; };
  window.__ajax = [];
  $.ajax = function (url) { window.__ajax.push(url); };`;

async function open(hash) {
  const page = await pageWith({ html: HTML, scripts: [{ content: STUBS }, 'service/yobi.code.Browser.js'] });
  await page.evaluate((h) => {
    history.replaceState(null, '', h);
    new yobi.code.Browser({ sProjectName: 'p', sMetaInfoURL: '/m', sBasePathURL: '/b', sInitialPath: 'dir' });
  }, hash);
  return page;
}
const state = (page) => page.evaluate(() => ({
  ajax: window.__ajax,
  crumbs: $('#breadcrumbs a').map((i, a) => $(a).text()).get(),
  listVisible: $('[data-listpath="dir"]').css('display') !== 'none',
  newFile: $('#new-file-link').attr('href')
}));

export const tests = [
  { name: '#cb- 앵커가 붙은 주소로 들어와도 접두어를 경로로 읽지 않는다 (C053, C180)', async run() {
    const page = await open('#cb-dir');
    const s = await state(page);
    await page.close();
    assert.deepEqual(s.ajax, [], '없는 경로 cb-dir로 목록을 요청하면 안 된다');
    assert.deepEqual(s.crumbs, ['p', 'dir']);
    assert.equal(s.listVisible, true, '이미 그려진 목록이 접히면 안 된다');
    assert.match(s.newFile, /path=dir\//);
  } },
  { name: '앵커 없는 해시는 이전처럼 하위 폴더 목록을 요청한다 (C053 회귀)', async run() {
    const page = await open('#dir/a');
    const s = await state(page);
    await page.close();
    assert.deepEqual(s.ajax, ['/m/dir/a']);
    assert.deepEqual(s.crumbs, ['p', 'dir', 'a']);
  } },
  { name: '폴더 이름이 cb-로 시작하면 접두어로 보지 않고 그 폴더로 읽는다 (C053)', async run() {
    const page = await open('#cb-x');
    const s = await state(page);
    await page.close();
    assert.deepEqual(s.ajax, ['/m/cb-x']);
    assert.deepEqual(s.crumbs, ['p', 'cb-x']);
  } },
  { name: '#cb-하위/경로 앵커도 접두어를 벗겨 하위 폴더로 읽는다 (C053)', async run() {
    const page = await open('#cb-dir/a');
    const s = await state(page);
    await page.close();
    assert.deepEqual(s.ajax, ['/m/dir/a']);
    assert.deepEqual(s.crumbs, ['p', 'dir', 'a']);
  } }
];
