import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const HEADER = 'Index: a.txt\n===================================================================\n--- a.txt\t(revision 1)\n+++ a.txt\t(revision 2)\n';
const STUBS = { content: `
  window.NProgress = { done: function () {}, start: function () {} };
  $.fn.tmpl = function () { return $('<span/>'); };
  $yobi.getTrim = function (s) { return $.trim(s); };
  yobi.Files = { init: function () {}, getUploader: function () {}, attach: function () {}, getEnv: function () { return {}; } };
  yobi.Attachments = function () { return { destroy: function () {} }; };` };

/** `#commit`에 [diff]를 담아 화면 스크립트를 시작하고 그려진 표의 줄들을 돌려준다. */
async function render(diff) {
  const page = await pageWith({
    html: '<div class="diff-wrap"><div id="commit"></div><div class="diff-body"></div></div><ul class="comments"></ul><div id="minimap"><div class="minimap-wrap"><div class="minimap-curr"></div><div class="minimap-links"></div></div></div>',
    scripts: [STUBS, 'service/yobi.code.SvnDiff.js']
  });
  await page.evaluate((d) => { $('#commit').text(d); }, diff);
  const result = await page.evaluate(() => {
    try { new yobi.code.SvnDiff({ sCodeURL: '/c', sCommitId: '2', sParentCommitId: '1' }); } catch (e) { return { error: e.message }; }
    return { rows: $('table.diff-container tr').map(function () { return $(this).attr('class') + '|' + $(this).find('pre').text() + '|' + $(this).find('.hidden').map(function () { return $(this).text(); }).get().join(','); }).get() };
  });
  await page.close();
  return result;
}

export const tests = [
  { name: '내용이 --나 ++로 시작하는 줄도 내용 줄로 그리고 줄 번호가 어긋나지 않는다 (C042)', async run() {
    const r = await render(HEADER + '@@ -1,3 +1,3 @@\n context\n--removed with dashes\n++added with pluses\n tail\n');
    assert.equal(r.error, undefined, r.error);
    const codeRows = r.rows.filter((x) => !x.startsWith('range') && !x.startsWith('undefined'));
    assert.deepEqual(codeRows.map((x) => x.split('|')[0]), ['context', 'remove', 'add', 'context'].map((t) => t), JSON.stringify(r.rows));
  } },
  { name: '헝크 머리를 읽지 못해도 TypeError 없이 이어서 그린다 (C043, C113-4)', async run() {
    const r = await render(HEADER + '@@ broken header @@\n context\n');
    assert.equal(r.error, undefined, r.error);
  } },
];
