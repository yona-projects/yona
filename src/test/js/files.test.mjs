import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const STUBS = { content: 'window.humanize = { filesize: function (n) { return n + "B"; } };' };

/** 업로드 응답을 흉내 내어 [namespace]에 붙인 오류 처리기가 불리는 횟수를 센다. */
async function upload(response, namespace) {
  const page = await pageWith({ html: '', scripts: [STUBS, 'common/yobi.Files.js'] });
  const r = await page.evaluate(async ([resp, ns]) => {
    $.ajax = function (o) { setTimeout(function () { o.success(resp); }, 0); };
    yobi.Files.init({ sUploadURL: '/up' });
    var seen = { error: 0, success: 0 };
    yobi.Files.attach({ errorUpload: function () { seen.error++; }, successUpload: function () { seen.success++; } }, ns);
    yobi.Files.getEnv().bXHR2 = true;
    yobi.Files.uploadFile(new File(['x'], 'a.txt'), ns);
    await new Promise((r) => setTimeout(r, 50));
    return seen;
  }, [response, namespace]);
  await page.close();
  return r;
}

export const tests = [
  { name: '응답 검증에 실패하면 이름 공간을 붙인 오류 처리기가 불린다 (C004)', async run() {
    const seen = await upload({}, 'ns');
    assert.deepEqual(seen, { error: 1, success: 0 });
  } },
  { name: '올바른 응답이면 성공 처리기만 불린다', async run() {
    const seen = await upload({ name: 'a', url: '/u' }, 'ns');
    assert.deepEqual(seen, { error: 0, success: 1 });
  } },
  { name: '표를 붙여넣으면 전역 window.event가 아니라 받은 이벤트로 마크다운 표를 만든다 (C010)', async run() {
    const page = await pageWith({ html: '<div id="c"><input type="file"><textarea id="t"></textarea></div>', scripts: [STUBS, 'common/yobi.Files.js'] });
    const out = await page.evaluate(() => {
      yobi.Files.init({ sUploadURL: '/up' });
      var got = null;
      yobi.Files.attach('pasteMarkdownTable', function (d) { got = d.markdownTableText; }, 'ns');
      yobi.Files.getEnv().bPastable = true;
      yobi.Files.getUploader($('#c')[0], $('#t')[0], 'ns');
      var items = [{ kind: 'string', type: 'text/plain' }, { kind: 'file', type: 'image/png', getAsFile: function () { return null; } }];
      var e = $.Event('paste'); e.originalEvent = { clipboardData: { items: items, getData: function () { return 'a\tb\n1\t2'; } }, preventDefault: function () {} };
      try { $('#t').trigger(e); } catch (err) { return 'ERR ' + err.message; }
      return got;
    });
    await page.close();
    assert.ok(out && out.startsWith('| a'), String(out));
  } },
];
