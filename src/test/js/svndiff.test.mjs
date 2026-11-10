import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

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
    // 스크롤 반영은 프레임당 한 번(rAF)이므로 한 프레임을 기다린다
    await page.evaluate(async () => { window.scrollTo(0, 1000); window.dispatchEvent(new Event('scroll')); await new Promise((x) => requestAnimationFrame(() => requestAnimationFrame(x))); });
    // 비율은 모듈이 시작할 때의 문서 높이로 정해지므로, 시작 직후의 높이가 아니라 실제 표시값이 스크롤에 비례하는지 본다(내려갔고 비율이 0과 1 사이)
    const after = await page.evaluate(() => ({ top: parseInt($('.minimap-curr').css('top'), 10), ratio: 200 / $(document).height() }));
    await page.close();
    assert.ok(after.top > 0, `현재 위치 표시가 내려가지 않았다: ${after.top}px`); assert.ok(Math.abs(after.top - Math.ceil(1000 * after.ratio)) <= 5, `기대 ${Math.ceil(1000 * after.ratio)}px 근처, 실제 ${after.top}px`);
  } },
  // ---- 미니맵 처리기: 프레임당 한 번(rAF), passive, ResizeObserver (D-124, 동작 차이 10번) ----
  { name: '미니맵: 스크롤 이벤트가 한 프레임에 여러 번 와도 현재 위치 표시는 프레임당 한 번만 갱신한다', async run() {
    const page = await minimapPage();
    const n = await page.evaluate(async () => { let count = 0; const css = $.fn.css; $.fn.css = function (k) { if (k === 'top' && this.hasClass('minimap-curr')) count++; return css.apply(this, arguments); };
      for (let i = 0; i < 10; i++) { window.scrollTo(0, 100 * (i + 1)); window.dispatchEvent(new Event('scroll')); }
      await new Promise((x) => requestAnimationFrame(() => requestAnimationFrame(x))); return count; });
    await page.close(); assert.equal(n, 1);
  } },
  { name: '미니맵: 스크롤과 크기 변경 처리기는 passive로 등록한다', async run() {
    const page = await minimapPage((p) => p.evaluate(() => { window.__reg = []; const add = window.addEventListener; window.addEventListener = function (type, fn, opt) { window.__reg.push([type, !!(opt && opt.passive)]); return add.apply(this, arguments); }; }));
    const reg = await page.evaluate(() => window.__reg.filter(([t]) => t === 'scroll' || t === 'resize'));
    await page.close(); assert.ok(reg.some(([t, passive]) => t === 'scroll' && passive), JSON.stringify(reg)); assert.ok(reg.some(([t, passive]) => t === 'resize' && passive), JSON.stringify(reg));
  } },
  { name: '미니맵: 문서 높이가 바뀌면(ResizeObserver) 비율을 다시 계산해 현재 위치 표시의 높이를 맞춘다', async run() {
    const page = await minimapPage();
    const before = await page.evaluate(() => parseInt($('.minimap-curr').css('height'), 10));
    await page.evaluate(() => { const d = document.createElement('div'); d.style.height = '12000px'; document.body.appendChild(d); });
    await page.waitForFunction((b) => parseInt($('.minimap-curr').css('height'), 10) < b, before, { timeout: 3000 });
    const after = await page.evaluate(() => parseInt($('.minimap-curr').css('height'), 10)); await page.close(); assert.ok(after < before, `${before} → ${after}`);
  } },
  { name: '미니맵: 창 높이만 바뀌어도(문서 크기는 그대로) 현재 위치 표시의 높이를 맞춘다', async run() {
    const page = await minimapPage();
    const before = await page.evaluate(() => parseInt($('.minimap-curr').css('height'), 10));
    await page.setViewportSize({ width: 1280, height: 400 });
    await page.waitForFunction((b) => parseInt($('.minimap-curr').css('height'), 10) !== b, before, { timeout: 3000 });
    await page.close();
  } },
];

const MINIMAP_HTML = '<style>body{margin:0}.minimap-wrap{height:200px;position:relative}.minimap-curr{position:absolute;left:0;width:10px;background:#0a0}</style><div class="diff-wrap"><div id="commit"></div><div class="diff-body"></div></div><ul class="comments"></ul><div id="minimap"><div class="minimap-wrap"><div class="minimap-curr"></div><div class="minimap-links"></div></div></div><div style="height:4000px">긴 문서</div>';
/** 미니맵이 있는 긴 문서에서 SVN 비교 화면을 시작한다. [beforeLoad]는 모듈을 올리기 전에 부른다. */
async function minimapPage(beforeLoad) {
  const page = await pageWith({ html: MINIMAP_HTML, scripts: [STUBS] });
  if (beforeLoad) await beforeLoad(page);
  await page.addScriptTag({ content: classicFromEsm('esm/service/code.SvnDiff.js') });
  await page.evaluate((d) => { $('#commit').text(d); new yona.code.SvnDiff({ sCodeURL: '/c', sCommitId: '2', sParentCommitId: '1' }); }, HEADER + '@@ -1,1 +1,1 @@\n context\n');
  await page.waitForTimeout(100);
  return page;
}
