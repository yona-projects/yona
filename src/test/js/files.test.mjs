import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pageWith, JS, FAKE_XHR } from './harness.mjs';

const STUBS = { content: 'window.$yona = window.$yona || {}; $yona.formatFileSize = function (n) { return n + "B"; };' };
const XHR = { content: FAKE_XHR };

/** 업로더를 만들고 파일을 올린 뒤 [respond]로 서버 응답을 흉내 낸다. 받은 처리기 호출을 모아 돌려준다. */
async function upload({ respond, file = { name: 'a.txt', content: 'x' }, namespace = 'ns', maxFileSize } = {}) {
  const page = await pageWith({ html: '', scripts: [STUBS, XHR, 'common/yona.Files.js'] });
  const r = await page.evaluate(async ([respondSource, file, ns, max]) => {
    yona.Files.init({ sUploadURL: '/up', maxFileSize: max });
    var seen = { error: [], success: [], progress: [], before: 0 };
    yona.Files.attach({
      beforeUpload: function () { seen.before++; },
      errorUpload: function (d) { seen.error.push(d.oRes); }, successUpload: function (d) { seen.success.push(d.oRes); },
      uploadProgress: function (d) { seen.progress.push(d.nPercentComplete); },
    }, ns);
    yona.Files.uploadFile(new File([file.content], file.name), ns);
    var x = window.__xhrs[0];
    if (x && respondSource) new Function('x', respondSource)(x);
    await new Promise((r) => setTimeout(r, 30));
    return { seen: seen, request: x ? { method: x.method, url: x.url, headers: x.headers, hasBody: x.body instanceof FormData, filePath: x.body instanceof FormData ? [x.body.get('filePath').name, x.body.get('filePath').size] : null } : null, requests: window.__xhrs.length };
  }, [respond || '', file, namespace, maxFileSize]);
  r.errors = page.errors;
  await page.close();
  return r;
}

export const tests = [
  { name: '응답 검증에 실패하면 이름 공간을 붙인 오류 처리기가 불린다 (C004)', async run() {
    const r = await upload({ respond: `x.finish(200, 'OK', 'application/json', '{}')` });
    assert.equal(r.seen.error.length, 1); assert.equal(r.seen.success.length, 0);
  } },
  { name: '올바른 응답이면 성공 처리기만 불린다', async run() {
    const r = await upload({ respond: `x.finish(200, 'OK', 'application/json;charset=UTF-8', '{"name":"a","url":"/u"}')` });
    assert.deepEqual(r.seen.success, [{ name: 'a', url: '/u' }]); assert.equal(r.seen.error.length, 0);
  } },
  // ---- S-06(A-421): 네이티브 XMLHttpRequest ----
  { name: '요청: POST로 업로드 주소에 FormData(filePath)를 보내고 X-Requested-With 머리글을 싣는다', async run() {
    const r = await upload({});
    assert.equal(r.requests, 1);
    assert.equal(r.request.method, 'POST'); assert.equal(r.request.url, '/up'); assert.equal(r.request.hasBody, true);
    assert.deepEqual(r.request.filePath, ['a.txt', 1]);
    assert.equal(r.request.headers['X-Requested-With'], 'XMLHttpRequest');
    assert.ok(!('Content-Type' in r.request.headers), 'Content-Type 은 브라우저가 경계(boundary)와 함께 정하므로 직접 달면 안 된다');
  } },
  { name: '요청: 붙여넣은 이미지(image.png)는 올리는 번호를 이름으로 한다', async run() {
    const r = await upload({ file: { name: 'image.png', content: 'p' } });
    assert.match(r.request.filePath[0], /^\d.*\.png$/);
  } },
  { name: '진행률: upload 의 progress 이벤트를 퍼센트(올림)로 알린다', async run() {
    const r = await upload({ respond: `x.progress(1, 3); x.progress(2, 3); x.progress(3, 3); x.progress(5, 0, false);` });
    assert.deepEqual(r.seen.progress, [34, 67, 100]);
  } },
  { name: '오류: HTTP 오류는 status 와 statusText 를 가진 객체로 오류 처리기에 간다', async run() {
    const r = await upload({ respond: `x.finish(413, 'Payload Too Large', 'text/html', '<h1>too big</h1>')` });
    assert.equal(r.seen.error.length, 1); assert.equal(r.seen.error[0].status, 413); assert.equal(r.seen.error[0].statusText, 'Payload Too Large');
  } },
  { name: '오류: 네트워크 오류는 status 0 으로 간다', async run() {
    const r = await upload({ respond: `x.fail()` });
    assert.equal(r.seen.error.length, 1); assert.equal(r.seen.error[0].status, 0); assert.equal(r.seen.success.length, 0);
  } },
  { name: '오류: JSON 이라고 했는데 깨진 응답은 parsererror 로 오류 처리기에 간다', async run() {
    const r = await upload({ respond: `x.finish(200, 'OK', 'application/json', '{깨짐')` });
    assert.equal(r.seen.error.length, 1); assert.equal(r.seen.error[0].statusText, 'parsererror'); assert.equal(r.seen.success.length, 0);
  } },
  { name: '오류: 응답이 JSON 이 아니면 문자열 그대로 검증에서 걸려 오류 처리기에 간다', async run() {
    const r = await upload({ respond: `x.finish(200, 'OK', 'text/html', '<html></html>')` });
    assert.equal(r.seen.error.length, 1); assert.equal(r.seen.success.length, 0);
  } },
  { name: '큰 파일: 한도를 넘으면 요청을 보내지 않고 오류 처리기에 크기 글자를 알린다', async run() {
    const r = await upload({ file: { name: 'big.bin', content: 'xxxxxxxxxx' }, maxFileSize: 5 });
    assert.equal(r.requests, 0); assert.equal(r.seen.error.length, 1); assert.equal(r.seen.error[0].status, '10B');
  } },
  { name: '옛 경로가 없다: 폼 업로드, IE 확인, bXHR2, htUploadOpts, ajaxForm 은 남지 않는다', async run() {
    const text = fs.readFileSync(path.join(JS, 'common/yona.Files.js'), 'utf8');
    for (const gone of ['_uploadFileForm', 'trident', 'bXHR2', 'htUploadOpts', 'ajaxForm', '$.ajaxSettings']) assert.ok(!text.includes(gone), `${gone} 가 남아 있다`);
    const upload = text.slice(text.indexOf('function _uploadFileXHR'), text.indexOf('function _readResponse'));
    assert.ok(upload.includes('new XMLHttpRequest') && !upload.includes('$.ajax'), '업로드는 네이티브 XMLHttpRequest 만 쓴다(목록 조회의 $.ajax 는 S-14에서 바꾼다)');
    const page = await pageWith({ html: '', scripts: [STUBS, 'common/yona.Files.js'] });
    const env = await page.evaluate(() => { yona.Files.init({}); return Object.keys(yona.Files.getEnv()).sort(); });
    assert.ok(!env.includes('bXHR2') && !env.includes('htUploadOpts'), JSON.stringify(env));
    await page.close();
  } },
  { name: '표를 붙여넣으면 전역 window.event가 아니라 받은 이벤트로 마크다운 표를 만든다 (C010)', async run() {
    const page = await pageWith({ html: '<div id="c"><input type="file"><textarea id="t"></textarea></div>', scripts: [STUBS, 'common/yona.Files.js'] });
    const out = await page.evaluate(() => {
      yona.Files.init({ sUploadURL: '/up' });
      var got = null;
      yona.Files.attach('pasteMarkdownTable', function (d) { got = d.markdownTableText; }, 'ns');
      yona.Files.getEnv().bPastable = true;
      yona.Files.getUploader($('#c')[0], $('#t')[0], 'ns');
      var items = [{ kind: 'string', type: 'text/plain' }, { kind: 'file', type: 'image/png', getAsFile: function () { return null; } }];
      var e = $.Event('paste'); e.originalEvent = { clipboardData: { items: items, getData: function () { return 'a\tb\n1\t2'; } }, preventDefault: function () {} };
      try { $('#t').trigger(e); } catch (err) { return 'ERR ' + err.message; }
      return got;
    });
    await page.close();
    assert.ok(out && out.startsWith('| a'), String(out));
  } },
  { name: '붙여넣기 업로드는 Firefox에서도 켜져 있고 붙여넣은 이미지를 올린다(F-03: 옛 Firefox 제외 조건은 철자 오류로 동작한 적이 없고 이제 필요 없다)', async run() {
    const page = await pageWith({ html: '<div id="c"><input type="file"><textarea id="t"></textarea></div>', scripts: [STUBS, XHR, 'common/yona.Files.js'] });
    await page.evaluate(() => { Object.defineProperty(navigator, 'userAgent', { get: () => 'Mozilla/5.0 (X11; Linux x86_64; rv:155.0) Gecko/20100101 Firefox/155.0' }); });
    const out = await page.evaluate(() => {
      yona.Files.init({ sUploadURL: '/up' });
      var pasted = null; yona.Files.attach('pasteFile', function (d) { pasted = d.oFile.type; }, 'ns');
      yona.Files.getUploader($('#c')[0], $('#t')[0], 'ns');
      var png = new File(['x'], 'p.png', { type: 'image/png' });
      var e = $.Event('paste'); e.originalEvent = { clipboardData: { items: [{ kind: 'file', type: 'image/png', getAsFile: function () { return png; } }] }, preventDefault: function () {} };
      $('#t').trigger(e);
      return { pastable: yona.Files.getEnv().bPastable, pasted: pasted, uploads: (window.__xhrs || []).length };
    });
    assert.deepEqual(out, { pastable: true, pasted: 'image/png', uploads: 1 }); assert.deepEqual(page.errors, []); await page.close();
  } },
];

