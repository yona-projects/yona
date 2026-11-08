// 전역 단축키(`common/yona.ShortcutKey.js`)가 Shadow DOM 안의 입력란(`<title-input>`)에서 친 글자를 단축키로 받아들이지 않는지 확인한다(D-102, A-408).
// 프로젝트 메뉴에는 H, B, C, I, M, P처럼 글자 하나로 화면을 옮기는 단축키가 있다. 키보드 이벤트의 대상(`event.target`)은 Shadow DOM 경계에서 호스트로 바뀌므로
// 태그 이름만 보면 입력란을 알아보지 못한다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, JS } from './harness.mjs';

const BUNDLE = path.join(JS, 'lib/title-input/title-input.min.js');
const HTML = '<title-input id="title" name="title"></title-input><input id="plain"><textarea id="ta"></textarea><div id="free" tabindex="0">x</div>';

async function setup() {
  const page = await pageWith({ html: HTML, scripts: ['common/yona.ShortcutKey.js'] });
  await page.addScriptTag({ path: BUNDLE, type: 'module' });
  await page.waitForFunction(() => customElements.get('title-input'));
  await page.evaluate(() => {
    window.__hits = [];
    yona.ShortcutKey.setKeymapLink({ B: '#board' });
    yona.ShortcutKey.attach('G', (info) => window.__hits.push({ tag: info.sTagName, form: info.bFormInput, target: info.welTarget[0].tagName.toLowerCase() }));
  });
  return page;
}
const hash = (page) => page.evaluate(() => location.hash);

export const tests = [
  { name: '입력란이 아닌 곳에서 B를 누르면 단축키가 동작한다(대조군)', run: async () => {
    const page = await setup();
    await page.locator('#free').focus(); await page.keyboard.press('b');
    assert.equal(await hash(page), '#board');
    await page.close();
  } },
  { name: '일반 입력란과 textarea에서 친 글자는 단축키가 아니다', run: async () => {
    const page = await setup();
    await page.locator('#plain').focus(); await page.keyboard.type('bug');
    await page.locator('#ta').focus(); await page.keyboard.type('bug');
    assert.equal(await hash(page), '');
    await page.close();
  } },
  { name: '<title-input>에서 친 글자도 단축키가 아니다(대상이 호스트로 보여도 안쪽 입력란을 본다)', run: async () => {
    const page = await setup();
    await page.locator('title-input input').focus(); await page.keyboard.type('bug');
    assert.equal(await hash(page), '', '제목에 "bug"를 치다가 게시판으로 이동하면 안 된다');
    assert.equal(await page.evaluate(() => document.getElementById('title').value), 'bug');
    await page.close();
  } },
  { name: '핸들러에 알려 주는 대상 정보는 안쪽 입력란이다(sTagName INPUT, bFormInput true)', run: async () => {
    const page = await setup();
    await page.locator('title-input input').focus(); await page.keyboard.press('g');
    assert.deepEqual(await page.evaluate(() => window.__hits), [{ tag: 'INPUT', form: true, target: 'input' }]);
    await page.locator('#free').focus(); await page.keyboard.press('g');
    assert.deepEqual(await page.evaluate(() => window.__hits.at(-1)), { tag: 'DIV', form: false, target: 'div' });
    await page.close();
  } },
];
