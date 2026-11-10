import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const HEADER = 'Index: a.txt\n===================================================================\n--- a.txt\t(revision 1)\n+++ a.txt\t(revision 2)\n';
const STUBS = { content: `
  window.NProgress = { done: function () {}, start: function () {} };
  $.fn.tmpl = function () { return $('<span/>'); };
  $yona.getTrim = function (s) { return $.trim(s); };
  yona.Files = { init: function () {}, getUploader: function () {}, attach: function () {}, getEnv: function () { return {}; } };
  yona.Attachments = function () { return { destroy: function () {} }; };` };

/** `#commit`에 [diff]를 담아 화면 스크립트를 시작하고 그려진 표의 줄들을 돌려준다. */
async function render(diff) {
  const page = await pageWith({
    html: '<div class="diff-wrap"><div id="commit"></div><div class="diff-body"></div></div><ul class="comments"></ul><div id="minimap"><div class="minimap-wrap"><div class="minimap-curr"></div><div class="minimap-links"></div></div></div>',
    scripts: [STUBS, 'esm/service/code.SvnDiff.js']
  });
  await page.evaluate((d) => { $('#commit').text(d); }, diff);
  const result = await page.evaluate(() => {
    try { new yona.code.SvnDiff({ sCodeURL: '/c', sCommitId: '2', sParentCommitId: '1' }); } catch (e) { return { error: e.message }; }
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
  { name: '미니맵: 화면을 스크롤하면 현재 위치 표시(minimap-curr)가 스크롤 비율만큼 아래로 내려간다(표준 모드에서 body.scrollTop은 늘 0이라 움직이지 않던 결함, D-124)', async run() {
    const page = await pageWith({
      html: '<style>body{margin:0}.minimap-wrap{height:200px;position:relative}.minimap-curr{position:absolute;left:0;width:10px;background:#0a0}</style><div class="diff-wrap"><div id="commit"></div><div class="diff-body"></div></div><ul class="comments"></ul><div id="minimap"><div class="minimap-wrap"><div class="minimap-curr"></div></div></div><div style="height:4000px">긴 문서</div>',
      scripts: [STUBS, 'esm/service/code.SvnDiff.js']
    });
    await page.evaluate((d) => { $('#commit').text(d); window.__h0 = null; new yona.code.SvnDiff({ sCodeURL: '/c', sCommitId: '2', sParentCommitId: '1' }); }, HEADER + '@@ -1,1 +1,1 @@\n context\n');
    await page.evaluate(() => { window.scrollTo(0, 1000); window.dispatchEvent(new Event('scroll')); });
    // 비율은 모듈이 시작할 때의 문서 높이로 정해지므로, 시작 직후의 높이가 아니라 실제 표시값이 스크롤에 비례하는지 본다(내려갔고 비율이 0과 1 사이)
    const after = await page.evaluate(() => ({ top: parseInt($('.minimap-curr').css('top'), 10), ratio: 200 / $(document).height() }));
    await page.close();
    assert.ok(after.top > 0, `현재 위치 표시가 내려가지 않았다: ${after.top}px`); assert.ok(Math.abs(after.top - Math.ceil(1000 * after.ratio)) <= 5, `기대 ${Math.ceil(1000 * after.ratio)}px 근처, 실제 ${after.top}px`);
  } },
];
