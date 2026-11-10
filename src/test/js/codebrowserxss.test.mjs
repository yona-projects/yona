// 코드 브라우저의 파일/폴더 목록(`esm/service/code.Browser.js`): 저장소의 파일 이름이 마크업으로 해석되지 않고 글자로만 보이는지 고정한다.
// 리눅스 파일 이름은 `/`만 없으면 되므로 `<img src=x onerror=...>.txt` 같은 이름이 가능하다(저장형 XSS, 11번).
import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const TPL = `<script id="tplFileListItem" type="text/x-yona-template">
<div id="cb-\${listPath}\${fileName}" class="row listitem" data-path="\${targetPath}">
    <div class="col-lg-6 filename">
        <a href="\${path}" class="\${fileClass}" title="\${fileName}" data-targetPath="\${targetPath}" data-type="\${type}">
            <span class="dynatree-icon vmiddle"></span>\${fileName}
        </a>
    </div>
    <div class="col-lg-5 commitMsg">
        \${avatarImg}
        <span class="ml5"><a href="\${commitUrl}">\${commitMsg}</a></span>
    </div>
    <div class="col-lg-1 commitDate">\${commitDate}</div>
</div>
                </script>`;
const HTML = `<style>.x{}</style>${TPL}
  <div id="breadcrumbs"></div><select id="branches"></select><a id="new-file-link" href="/new?branch=master"></a>
  <div class="code-viewer-wrap">
    <div class="row listitem" data-path="dir"><a data-type="folder" data-targetpath="dir" href="/b/dir">dir</a></div>
    <div class="list-wrap" data-type="folder" data-listpath="dir">
      <div class="row listitem" data-path="dir/sub"><a data-type="folder" data-targetpath="dir/sub" href="/b/dir/sub">sub</a></div>
    </div>
  </div>`;
const STUBS = `
  window.NProgress = { start: function () {}, done: function () {} };
  window.__ajax = [];
  ((window.yona = window.yona || {}).http = (window.yona.http || {})).ajax = function (url, o) { window.__ajax.push(url); if (window.__listing && o && o.success) o.success({ data: window.__listing }); };`;

async function open(listing, hash = '#dir/sub') {
  // 실제 Common.js(htmlspecialchars, xssClean)와 xss 라이브러리로 돌려 실제 화면과 같은 경로를 쓴다
  const page = await pageWith({ stubs: false, html: HTML, scripts: [{ content: 'window.Messages = function (k) { return k; };' }, 'lib/xss.js', 'common/yona.Common.js', { content: STUBS }, 'esm/service/code.Browser.js'] });
  await page.evaluate(([l, h]) => {
    window.__listing = l; window.__xss = 0;
    history.replaceState(null, '', h);
    new yona.code.Browser({ sProjectName: 'p', sMetaInfoURL: '/m', sBasePathURL: '/b', sInitialPath: 'dir' });
  }, [listing, hash]);
  await page.waitForTimeout(100);
  return page;
}
const rows = (page) => page.evaluate(() => ({
  names: $('.list-wrap .filename a').map((i, a) => a.textContent.trim()).get(),
  titles: $('.list-wrap .filename a').map((i, a) => a.getAttribute('title')).get(),
  imgs: $('.list-wrap img[src="x"]').length, xss: window.__xss === 1,
  date: $('.list-wrap .commitDate').map((i, e) => e.textContent).get(),
}));

export const tests = [
  { name: '파일 목록: 평범한 이름은 이름, 제목, 날짜가 그대로 그려진다', run: async () => {
    const page = await open({ 'README.md': { type: 'file', msg: 'first', createdDate: Date.now() - 86400000 } });
    const r = await rows(page); assert.deepEqual(r.names, ['README.md']); assert.deepEqual(r.titles, ['README.md']); assert.deepEqual(r.date, ['a day ago']); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '파일 목록: 이름의 HTML은 마크업으로 해석하지 않고 글자로 보인다(저장형 XSS)', run: async () => {
    const evil = '<img src=x onerror=window.__xss=1>.txt';
    const page = await open({ [evil]: { type: 'file', msg: 'm' } });
    const r = await rows(page);
    assert.equal(r.imgs, 0, '파일 이름의 <img>가 실제 요소가 되었다'); assert.equal(r.xss, false);
    assert.deepEqual(r.names, [evil]); assert.deepEqual(r.titles, [evil]); await page.close();
  } },
  { name: '파일 목록: 이름의 따옴표로 속성을 끊고 이벤트를 심을 수 없다', run: async () => {
    const evil = '" onmouseover="window.__xss=1" x="';
    const page = await open({ [evil]: { type: 'file', msg: 'm' } });
    const r = await page.evaluate(() => { const a = $('.list-wrap .filename a')[0]; return { onmouseover: a.getAttribute('onmouseover'), title: a.getAttribute('title') }; });
    assert.equal(r.onmouseover, null); assert.equal(r.title, evil); await page.close();
  } },
  { name: '주소 해시: 해시의 HTML 조각이 목록 요소로 만들어져 실행되지 않는다(DOM XSS)', run: async () => {
    const evil = 'dir/sub"><img src=x onerror=window.__xss=1><"';
    const page = await open({ 'a.txt': { type: 'file', msg: 'm' } }, '#' + encodeURI(evil));
    // 빵부스러기는 xssClean이 걸러 태그가 남아도 이벤트 속성은 없어야 한다. 목록 요소(list-wrap)에는 해시에서 온 요소가 없어야 한다
    const r = await page.evaluate(() => ({ withHandler: [...document.querySelectorAll('*')].filter((e) => [...e.attributes].some((a) => /^on/i.test(a.name))).length, inList: document.querySelectorAll('.list-wrap img').length, xss: window.__xss === 1 }));
    assert.equal(r.withHandler, 0, '이벤트 속성이 붙은 요소가 있다'); assert.equal(r.inList, 0); assert.equal(r.xss, false); await page.close();
  } },
];
