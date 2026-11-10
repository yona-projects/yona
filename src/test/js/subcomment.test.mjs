// 하위 댓글(대댓글) 입력란 `esm/SubComment.js`(R-143): "대댓글 추가"를 누르면 입력란이 열리고, 입력란에서 Esc를 누르면 닫히며, 다시 누르면 다시 열린다.
// v1.6은 Esc에서 `display: none`과 `visibility: hidden`을 함께 넣어 다시 열어도 `visibility: hidden`이 남아 보이지 않았다. 처음 상태는 CSS의 `display: none`뿐(visibility는 기본값 visible)이다.
import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const HTML = `<style>.child-comment-input-form{display:none}</style>
  <ul><li class="comment" id="c1"><a class="add-a-comment">대댓글 추가</a><div class="child-comment-input-form" id="f1"><form><textarea id="t1"></textarea></form></div></li>
  <li class="comment" id="c2"><a class="add-a-comment">대댓글 추가</a><div class="child-comment-input-form" id="f2"><form><textarea id="t2"></textarea></form></div></li></ul>`;
const open = () => pageWith({ html: HTML, scripts: ['common/yona.Common.js', 'esm/SubComment.js'], stubs: false });
const state = (page, id) => page.evaluate((i) => { const cs = getComputedStyle(document.getElementById(i)); return { display: cs.display, visibility: cs.visibility, h: Math.round(document.getElementById(i).getBoundingClientRect().height) }; }, id);
const shown = (s) => s.display !== 'none' && s.visibility !== 'hidden';

export const tests = [
  { name: '하위 댓글: 처음에는 visibility가 기본값(visible)이고 display만 none이다', run: async () => {
    const page = await open(); const s = await state(page, 'f1'); assert.deepEqual([s.display, s.visibility], ['none', 'visible']); await page.close();
  } },
  { name: '하위 댓글: "대댓글 추가"를 누르면 입력란이 열리고 글상자에 초점이 간다', run: async () => {
    const page = await open(); await page.click('#c1 .add-a-comment'); await page.waitForTimeout(100);
    assert.equal(shown(await state(page, 'f1')), true); assert.equal(await page.evaluate(() => document.activeElement.id), 't1'); await page.close();
  } },
  { name: '하위 댓글: 입력란에서 Esc를 누르면 닫히고 "대댓글 추가" 링크가 다시 보인다', run: async () => {
    const page = await open(); await page.click('#c1 .add-a-comment'); await page.keyboard.press('Escape'); await page.waitForTimeout(100);
    assert.equal(shown(await state(page, 'f1')), false); assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('#c1 .add-a-comment')).display !== 'none'), true); await page.close();
  } },
  { name: '하위 댓글: Esc로 닫은 뒤 "대댓글 추가"를 다시 누르면 입력란이 다시 보인다(R-143, v1.6은 visibility: hidden이 남아 보이지 않았다)', run: async () => {
    const page = await open(); await page.click('#c1 .add-a-comment'); await page.keyboard.press('Escape'); await page.waitForTimeout(100);
    await page.click('#c1 .add-a-comment'); await page.waitForTimeout(100);
    const s = await state(page, 'f1'); assert.equal(shown(s), true, JSON.stringify(s)); assert.ok(s.h > 0, `높이 ${s.h}`); assert.equal(await page.evaluate(() => document.activeElement.id), 't1', '다시 열어도 글상자에 초점');
    await page.keyboard.press('Escape'); await page.waitForTimeout(100); await page.click('#c1 .add-a-comment'); await page.waitForTimeout(100); assert.equal(shown(await state(page, 'f1')), true, '몇 번을 닫고 열어도 열린다');
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '하위 댓글: Esc는 열린 모든 입력란을 닫고(v1.6 동작), 닫은 뒤 다른 댓글의 입력란도 다시 열 수 있다', run: async () => {
    const page = await open(); await page.click('#c1 .add-a-comment'); await page.click('#c2 .add-a-comment'); await page.keyboard.press('Escape'); await page.waitForTimeout(100);
    assert.deepEqual([shown(await state(page, 'f1')), shown(await state(page, 'f2'))], [false, false]);
    await page.click('#c2 .add-a-comment'); await page.waitForTimeout(100); assert.deepEqual([shown(await state(page, 'f1')), shown(await state(page, 'f2'))], [false, true]); await page.close();
  } },
  { name: '하위 댓글: 한글 조합 중의 Esc(조합 취소)는 입력란을 닫지 않는다', run: async () => {
    const page = await open(); await page.click('#c1 .add-a-comment'); await page.waitForTimeout(100);
    await page.evaluate(() => { document.getElementById('t1').dispatchEvent(new KeyboardEvent('keyup', { key: 'Escape', keyCode: 27, isComposing: true, bubbles: true })); });
    assert.equal(shown(await state(page, 'f1')), true); await page.close();
  } },
  { name: '하위 댓글: 열고 닫기를 여러 번 해도 Cmd+Enter 제출은 한 번만 일어난다(누를 때마다 처리기가 쌓이지 않는다)', run: async () => {
    const page = await open(); await page.evaluate(() => { window.__submits = 0; $(document.getElementById('t1').form).on('submit', (e) => { e.preventDefault(); window.__submits++; }); });
    for (let i = 0; i < 3; i++) { await page.click('#c1 .add-a-comment'); await page.keyboard.press('Escape'); }
    await page.click('#c1 .add-a-comment'); await page.waitForTimeout(100);
    await page.evaluate(() => { document.getElementById('t1').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, metaKey: true, bubbles: true, cancelable: true })); });
    assert.equal(await page.evaluate(() => window.__submits), 1); await page.close();
  } },
  { name: '하위 댓글: Ctrl+Enter와 Cmd(Meta)+Enter는 제출하고 Enter만 누르거나 Shift+Enter는 제출하지 않는다(v1.6은 없는 속성 e.Control을 쓰고 keypress에 걸어 Ctrl+Enter가 안 됐고, Chromium은 Ctrl, Cmd+Enter에 keypress를 내지 않는다)', run: async () => {
    const page = await open(); await page.evaluate(() => { window.__submits = 0; $(document.getElementById('t1').form).on('submit', (e) => { e.preventDefault(); window.__submits++; }); });
    await page.click('#c1 .add-a-comment'); await page.waitForTimeout(100);
    const submits = () => page.evaluate(() => window.__submits);
    await page.keyboard.press('Enter'); assert.equal(await submits(), 0, 'Enter만');
    await page.keyboard.press('Shift+Enter'); assert.equal(await submits(), 0, 'Shift+Enter');
    await page.keyboard.press('Control+Enter'); assert.equal(await submits(), 1, 'Ctrl+Enter');
    await page.keyboard.press('Meta+Enter'); assert.equal(await submits(), 2, 'Cmd+Enter');
    assert.deepEqual(page.errors, []); await page.close();
  } },
];

