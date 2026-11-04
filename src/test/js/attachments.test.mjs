import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const HTML = `<form id="f"><div id="c"><ul class="attached-files"></ul><p class="help"></p><input type="file"><textarea id="t"></textarea></div></form>`;
const STUBS = { content: `
  window.humanize = { filesize: function (n) { return n + 'B'; } };
  window.NProgress = { done: function () {} };
  $.tmpl = function () { return $('<li class="attach"><div class="progress"><div class="bar"></div></div></li>'); };
  $yobi.notify = function (m) { (window.__notified = window.__notified || []).push(m); };` };

/** 임시 링크가 들어간 업로드를 시작하고 서버 응답으로 [response]를 돌려준 뒤 상태를 읽는다. */
async function uploadWith(response) {
  const page = await pageWith({ html: HTML, scripts: [STUBS, 'common/yobi.Files.js', 'common/yobi.Attachments.js'] });
  const state = await page.evaluate(async (resp) => {
    var ajaxOpts; $.ajax = function (o) { ajaxOpts = o; };
    yobi.Files.init({ sUploadURL: '/up' });
    yobi.Files.getEnv().bXHR2 = true;
    new yobi.Attachments({ elContainer: $('#c')[0], elTextarea: $('#t')[0], sUploaderId: 'ns', targetFormId: $('#f') });
    var id = null;
    yobi.Files.attach('beforeUpload', function (d) { id = d.oFile.nSubmitId; $('#t').val('앞 <!--_' + id + '_--> 뒤'); }, 'ns');
    var file = new File(['x'], 'a.txt');
    yobi.Files.uploadFile(file, 'ns');
    ajaxOpts.success(resp);
    await new Promise((r) => setTimeout(r, 30));
    return { text: $('#t').val(), ids: $('input[name=temporaryUploadFiles]').val(), notified: window.__notified || [] };
  }, response);
  const errors = page.errors; await page.close();
  return { ...state, errors };
}

export const tests = [
  { name: '응답 검증에 실패하면 오류 없이 임시 링크를 지우고 아이디를 등록하지 않는다 (C005, C006)', async run() {
    const r = await uploadWith({ id: 7 });
    assert.deepEqual(r.errors, [], r.errors.join(' / '));
    assert.equal(r.ids, '', '검증에 실패한 파일 아이디가 등록되었다: ' + r.ids);
    assert.ok(!r.text.includes('<!--_'), '임시 링크가 남았다: ' + r.text);
    assert.equal(r.notified.length, 1);
  } },
  { name: '올바른 응답이면 아이디를 등록한다', async run() {
    const r = await uploadWith({ id: 7, name: 'a.txt', url: '/f/7', mimeType: 'text/plain', size: 1 });
    assert.deepEqual(r.errors, [], r.errors.join(' / '));
    assert.equal(r.ids, '7');
  } },
];
