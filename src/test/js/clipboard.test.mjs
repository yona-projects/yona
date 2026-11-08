// `common/yona.Clipboard.js`와 이슈 보기의 메일 복사 단추(clipboard.js를 대신한다, S-01)를 확인한다.
import assert from 'node:assert/strict';
import { pageWith, templateSnippet } from './harness.mjs';

/** 보안 연결 여부와 표준 API, execCommand를 시험용으로 바꿔 놓은 페이지. */
async function setup(extra = '') {
  const page = await pageWith({ html: '<div id="box"></div><textarea id="paste"></textarea>', scripts: ['common/yona.Clipboard.js'] });
  await page.evaluate(`
    window.__calls = [];
    window.__secure = true;
    Object.defineProperty(window, 'isSecureContext', { get: function () { return window.__secure; }, configurable: true });
    ${extra}
  `);
  return page;
}

export const tests = [
  { name: '보안 연결에서는 표준 navigator.clipboard.writeText로 복사하고 true로 이행한다', async run() {
    const page = await setup(`Object.defineProperty(navigator, 'clipboard', { value: { writeText: function (t) { window.__calls.push(['api', t]); return Promise.resolve(); } }, configurable: true });`);
    const r = await page.evaluate(async () => [await yona.Clipboard.copy('안녕 abc'), window.__calls]);
    assert.deepEqual(r, [true, [['api', '안녕 abc']]]);
    await page.close();
  } },
  { name: '표준 API가 거절하면 대체 경로(execCommand)로 한 번 더 복사한다', async run() {
    const page = await setup(`
      Object.defineProperty(navigator, 'clipboard', { value: { writeText: function () { return Promise.reject(new Error('denied')); } }, configurable: true });
      document.execCommand = function (c) { window.__calls.push(['exec', c, document.activeElement && document.activeElement.value]); return true; };`);
    const r = await page.evaluate(async () => [await yona.Clipboard.copy('값'), window.__calls]);
    assert.deepEqual(r, [true, [['exec', 'copy', '값']]]);
    await page.close();
  } },
  { name: '보안 연결이 아니면 표준 API를 건드리지 않고 execCommand로 복사하며 임시 입력 칸은 지운다', async run() {
    const page = await setup(`
      window.__secure = false;
      Object.defineProperty(navigator, 'clipboard', { value: { writeText: function () { window.__calls.push(['api']); return Promise.resolve(); } }, configurable: true });
      document.execCommand = function (c) { window.__calls.push(['exec', c]); return true; };`);
    const r = await page.evaluate(async () => [await yona.Clipboard.copy('http에서'), window.__calls, document.querySelectorAll('textarea[readonly]').length]);
    assert.deepEqual(r, [true, [['exec', 'copy']], 0]);
    await page.close();
  } },
  { name: '복사하지 못하면 거절하지 않고 false로 이행한다', async run() {
    const page = await setup(`window.__secure = false; document.execCommand = function () { return false; };`);
    assert.equal(await page.evaluate(() => yona.Clipboard.copy('x')), false);
    await page.close();
  } },
  { name: '임시 입력 칸을 넘겨 준 컨테이너 안에 만들어 복사하고 원래 포커스를 되돌린다', async run() {
    const page = await setup(`
      window.__secure = false;
      document.execCommand = function (c) { var a = document.activeElement; window.__calls.push(['exec', a.parentNode.id, a.value]); return true; };`);
    await page.evaluate(() => { document.getElementById('box').innerHTML = '<div id="modalLike"><button id="btn">b</button></div>'; document.getElementById('btn').focus(); });
    const r = await page.evaluate(async () => [await yona.Clipboard.copy('모달 안', document.getElementById('modalLike')), window.__calls, document.activeElement.id]);
    assert.deepEqual(r, [true, [['exec', 'modalLike', '모달 안']], 'btn']);
    await page.close();
  } },
  { name: 'Bootstrap 모달처럼 포커스를 가두는 곳에서도 컨테이너를 주면 실제로 복사된다(execCommand와 붙여넣기)', async run() {
    const page = await setup();
    await page.evaluate(() => {
      window.__secure = false;
      document.getElementById('box').innerHTML = '<div id="modal" tabindex="-1"><button id="copyBtn">copy</button></div>';
      var modal = document.getElementById('modal');
      // Bootstrap 모달의 포커스 가두기(enforceFocus)와 같다: 모달 밖으로 포커스가 가면 모달로 되돌린다.
      document.addEventListener('focusin', function (e) { if (!modal.contains(e.target) && e.target.id !== 'paste') modal.focus(); });
      document.getElementById('copyBtn').addEventListener('click', function () { window.__done = yona.Clipboard.copy('가둔 곳 복사', modal); });
    });
    await page.click('#copyBtn');
    assert.equal(await page.evaluate(() => window.__done), true);
    await page.focus('#paste'); await page.keyboard.press('Control+V');
    assert.equal(await page.evaluate(() => document.getElementById('paste').value), '가둔 곳 복사');
    await page.close();
  } },
  { name: '이슈 보기의 메일 복사 단추: 누르면 data-clipboard-text를 도우미로 복사하고 성공 알림을 띄운다', async run() {
    const snippet = templateSnippet('issue/view.html', '// 이메일 복사 단추', '// timeline label text color adjusting');
    const page = await setup(`
      window.__alerts = []; window.__notes = [];
      $yona.alert = function (m) { window.__alerts.push(m); }; $yona.notify = function (m) { window.__notes.push(m); };
      yona.Clipboard.copy = function (t, c) { window.__calls.push(['copy', t, c && c.id]); return Promise.resolve(window.__result !== false); };`);
    await page.evaluate(() => { document.getElementById('box').innerHTML = '<div class="modal" id="voters"><button id="copyEmailBtn" data-clipboard-text="A &lt;a@x.org&gt;;B &lt;b@x.org&gt;;">e</button></div>'; });
    await page.evaluate((code) => new Function(code)(), snippet.replace(/\[\(\$\{[^}]*\}\)\]/g, 'MSG'));
    await page.click('#copyEmailBtn'); await page.waitForTimeout(50);
    const r = await page.evaluate(() => [window.__calls, window.__alerts.length, window.__notes.length]);
    assert.deepEqual(r[0], [['copy', 'A <a@x.org>;B <b@x.org>;', 'voters']]);
    assert.equal(r[1], 1); assert.equal(r[2], 0);
    assert.deepEqual(page.errors, []);
    await page.close();
  } },
  { name: '이슈 보기의 메일 복사 단추: 복사하지 못하면 알림 대신 클립보드 미지원 안내를 띄운다', async run() {
    const snippet = templateSnippet('issue/view.html', '// 이메일 복사 단추', '// timeline label text color adjusting');
    const page = await setup(`
      window.__alerts = []; window.__notes = [];
      $yona.alert = function (m) { window.__alerts.push(m); }; $yona.notify = function (m) { window.__notes.push(m); };
      yona.Clipboard.copy = function () { return Promise.resolve(false); };`);
    await page.evaluate(() => { document.getElementById('box').innerHTML = '<div class="modal"><button id="copyEmailBtn" data-clipboard-text="x">e</button></div>'; });
    await page.evaluate((code) => new Function(code)(), snippet.replace(/\[\(\$\{[^}]*\}\)\]/g, 'MSG'));
    await page.click('#copyEmailBtn'); await page.waitForTimeout(50);
    const r = await page.evaluate(() => [window.__alerts.length, window.__notes.length]);
    assert.deepEqual(r, [0, 1]);
    await page.close();
  } },
];
