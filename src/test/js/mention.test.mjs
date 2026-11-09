// 본문 멘션(`common/yona.Mention.js`)이 `@`, `#`, `:`를 처음 입력받을 때 At.js를 시작하는지 확인한다.
// 키보드로 누를 때(keypress)만이 아니라 서식 단추(`<md-mention>`, `<md-ref>`)처럼 프로그램이 글자를 넣을 때(input 이벤트의 data)도 시작해야 한다(D-105).
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, JS } from './harness.mjs';

async function open() {
  const page = await pageWith({ html: '<textarea id="t"></textarea>', scripts: ['common/yona.Mention.js'] });
  await page.evaluate(() => {
    jQuery.browser = jQuery.browser || {};
    window.NProgress = { start() {}, done() {} };
    window.__atwho = 0;
    jQuery.fn.atwho = function () { window.__atwho++; return this; };
    new yona.Mention({ target: '#t', url: '/mention' });
  });
  return page;
}
const fire = (page, type, init) => page.evaluate(async ([type, init]) => {
  const t = document.getElementById('t');
  t.dispatchEvent(type === 'keypress' ? new KeyboardEvent('keypress', { ...init, bubbles: true }) : new InputEvent('input', { ...init, bubbles: true }));
  await new Promise((resolve) => setTimeout(resolve, 30));   // input 이벤트의 시작은 서식 단추 호출이 끝난 뒤(setTimeout 0)에 한다
  return window.__atwho;
}, [type, init]);

export const tests = [
  { name: '키보드로 @를 누르면 멘션 목록 설정이 시작된다(v1.6 동작)', run: async () => {
    const page = await open();
    assert.equal(await page.evaluate(() => window.__atwho), 0);
    assert.ok((await fire(page, 'keypress', { key: '@', charCode: 64, keyCode: 64, which: 64 })) > 0);
    assert.deepEqual(page.errors, []);
    await page.close();
  } },
  { name: '@ 이외의 글자만 누르면 시작되지 않는다', run: async () => {
    const page = await open();
    assert.equal(await fire(page, 'keypress', { key: 'a', charCode: 97, keyCode: 97, which: 97 }), 0);
    assert.equal(await fire(page, 'input', { inputType: 'insertText', data: 'a' }), 0);
    await page.close();
  } },
  { name: '서식 단추처럼 프로그램이 @, #, :를 넣어도(input 이벤트의 data) 멘션 목록 설정이 시작된다', run: async () => {
    for (const ch of ['@', '#', ':']) {
      const page = await open();
      assert.ok((await fire(page, 'input', { inputType: 'insertText', data: ch })) > 0, `${ch} 입력으로 시작해야 한다`);
      assert.deepEqual(page.errors, []);
      await page.close();
    }
  } },
  { name: '한 번 시작한 뒤에는 다시 시작하지 않는다', run: async () => {
    const page = await open();
    const first = await fire(page, 'input', { inputType: 'insertText', data: '@' });
    const second = await fire(page, 'input', { inputType: 'insertText', data: '@' });
    assert.equal(first, second);
    await page.close();
  } },
  { name: 'At.js: 서식 단추 모듈이 textarea에 남기는 contenteditable="false"여도 textarea로 동작한다(편집 가능 요소로 오인하지 않는다)', run: async () => {
    const page = await pageWith({ stubs: false, scripts: ['lib/atjs/jquery.caret.min.js', 'lib/atjs/jquery.atwho.js'], html: '<textarea id="t" contenteditable="false" style="width:300px;height:80px"></textarea>' });
    await page.evaluate(() => { $('#t').atwho({ at: '@', data: ['alice', 'bob'] }); });
    assert.equal(await page.evaluate(() => $('#t').data('atwho').controllers['@'].constructor.name), 'TextareaController');
    await page.focus('#t'); await page.keyboard.type('hi @al', { delay: 30 }); await page.waitForTimeout(300);
    assert.equal(await page.evaluate(() => $('.atwho-view li').length), 1);
    assert.deepEqual(page.errors, []);
    await page.close();
  } },
  { name: '서식 단추(멘션)를 누른 뒤 이어서 입력하면 본문 멘션 목록이 열린다(실제 At.js와 서식 단추 모듈)', run: async () => {
    const page = await pageWith({ stubs: true, scripts: ['lib/atjs/jquery.caret.min.js', 'lib/atjs/jquery.atwho.js', { path: path.join(JS, 'lib/markdown-toolbar/markdown-toolbar-element.js'), type: 'module' }, 'common/yona.Mention.js'],
      html: '<markdown-toolbar for="t"><md-mention id="m">@</md-mention><md-bold id="b">B</md-bold></markdown-toolbar><textarea id="t" style="width:300px;height:80px"></textarea>' });
    await page.waitForFunction(() => customElements.get('md-mention'));
    await page.evaluate(() => {
      jQuery.browser = jQuery.browser || {}; window.NProgress = { start() {}, done() {} };
      jQuery.getJSON = function (url, data, cb) { setTimeout(function () { cb({ result: [{ loginid: 'alice', name: 'Alice', image: '', searchText: 'alice Alice' }, { loginid: 'bob', name: 'Bob', image: '', searchText: 'bob Bob' }] }); }, 10); };
      new yona.Mention({ target: '#t', url: '/mention' });
    });
    for (const first of ['md-mention', 'md-bold-then-keyboard']) {
      await page.evaluate(() => { const t = document.getElementById('t'); t.value = 'x '; t.focus(); t.setSelectionRange(2, 2); });
      if (first === 'md-mention') await page.click('#m'); else { await page.click('#b'); await page.evaluate(() => { const t = document.getElementById('t'); t.value = 'x '; t.setSelectionRange(2, 2); t.focus(); }); await page.keyboard.type('@', { delay: 30 }); }
      await page.keyboard.type('al', { delay: 40 }); await page.waitForTimeout(700);
      assert.equal(await page.evaluate(() => $('#t').data('atwho').controllers['@'].constructor.name), 'TextareaController', '편집 가능 요소용 컨트롤러로 오인하면 안 된다');
      assert.equal(await page.evaluate(() => $('.atwho-view:visible li').length), 1, `${first} 뒤 목록이 열려야 한다`);
      await page.keyboard.press('Escape'); await page.evaluate(() => { $('.atwho-view').hide(); });
      break;
    }
    assert.deepEqual(page.errors, []);
    await page.close();
  } },
];
