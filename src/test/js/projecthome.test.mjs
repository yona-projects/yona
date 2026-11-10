// 프로젝트 홈 스크립트(yona.project.Home.js)의 살아 있는 동작을 고정한다. 사라진 프로젝트 라벨 편집 코드를 지운 뒤에도(D-97, A-402)
// 저장소 주소 선택, 복사, 설명 수정, 설명 저장이 그대로 동작해야 한다.
import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const HTML = `
  <div class="project-page-wrap">
    <input id="repositoryURL" value="https://yona.test/acme/web.git">
    <button data-toggle="cloneURL"></button>
    <input id="cloneURL" value="https://yona.test/acme/web.git"><button id="cloneURLBtn"></button>
    <div data-toggle="project-description-tab" id="view-tab"><button data-toggle="description-edit" id="edit"></button><div id="project-description"></div></div>
    <div data-toggle="project-description-tab" class="hidden" id="edit-tab"><div class="project-description-edit"><input id="project-description-input" placeholder="설명 없음"></div>
      <button data-toggle="description-cancel" id="cancel"></button><button id="descriptionSaveBtn"></button></div>
    <div id="alertLeave"></div><button id="projectLeaveBtn" data-href="/acme/web/leave"></button><button id="leaveBtn"></button>
  </div>`;

const STUBS = `
  window.__copied = []; window.__notified = []; window.__rendered = []; window.__ajax = [];
  yona.Clipboard = { copy: function (v) { window.__copied.push(v); return Promise.resolve(true); } };
  yona.Markdown = { render: function (el, text) { window.__rendered.push(text); } };
  yona.Common = { notify: function (m) { window.__notified.push(m); } };
  $yona.notify = function () {};
  ((window.yona = window.yona || {}).http = (window.yona.http || {})).ajax = function (opts) { window.__ajax.push(opts); return $.Deferred().resolve({ overview: '새 설명' }).promise(); };
`;

async function open() {
  return pageWith({ html: HTML, scripts: [{ content: STUBS }, 'service/yona.project.Home.js'] });
}

export const tests = [
  { name: '프로젝트 홈: 초기화는 오류 없이 끝난다', async run() {
    const page = await open();
    await page.evaluate(() => { new yona.project.Home({ sURLProject: '/acme/web', sRepoURL: 'x' }); });
    const errors = page.errors; await page.close();
    assert.deepEqual(errors, []);
  } },
  { name: '프로젝트 홈: 복사 단추는 저장소 주소를 복사하고 알린다', async run() {
    const page = await open();
    const out = await page.evaluate(async () => {
      new yona.project.Home({ sURLProject: '/acme/web' });
      document.getElementById('cloneURLBtn').click();
      await new Promise((r) => setTimeout(r, 30));
      return { copied: window.__copied, notified: window.__notified };
    });
    await page.close();
    assert.deepEqual(out.copied, ['https://yona.test/acme/web.git']);
    assert.deepEqual(out.notified, ['code.copyUrl.copied']);
  } },
  { name: '프로젝트 홈: 설명 수정과 취소는 두 영역을 바꿔 보인다', async run() {
    const page = await open();
    const out = await page.evaluate(() => {
      new yona.project.Home({ sURLProject: '/acme/web' });
      const hidden = () => ({ view: $('#view-tab').hasClass('hidden'), edit: $('#edit-tab').hasClass('hidden') });
      const before = hidden();
      $('#edit').trigger('click'); const editing = hidden();
      $('#cancel').trigger('click'); const cancelled = hidden();
      return { before, editing, cancelled };
    });
    await page.close();
    assert.deepEqual(out.before, { view: false, edit: true });
    assert.deepEqual(out.editing, { view: true, edit: false });
    assert.deepEqual(out.cancelled, { view: false, edit: true });
  } },
  { name: '프로젝트 홈: 설명 저장은 PUT으로 보내고 돌아온 설명을 그린다', async run() {
    const page = await open();
    const out = await page.evaluate(() => {
      new yona.project.Home({ sURLProject: '/acme/web' });
      $('#project-description-input').val('새 설명');
      $('#descriptionSaveBtn').trigger('click');
      return { ajax: window.__ajax.map((a) => ({ url: a.url, method: a.method, data: a.data, contentType: a.contentType })), rendered: window.__rendered };
    });
    await page.close();
    assert.deepEqual(out.ajax, [{ url: '/acme/web', method: 'put', data: '{"overview":"새 설명"}', contentType: 'application/json' }]);
    assert.deepEqual(out.rendered, ['새 설명']);
  } },
];
