// 로그인 대화상자(`esm/LoginDialog.js`), 임시 저장(`esm/service/temporarySaveHandler.js`), 이슈 변경 감지(`esm/service/detectChange.js`)를 고정한다(시험 채우기, D-123).
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const MSG = { content: 'window.Messages = function (k) { return k; };' };
const get = (page, k) => page.evaluate((k) => window[k], k);

const LOGIN = `<a data-login="required" href="/needs-login">로그인 필요</a><input id="txt" data-login="required">
<div id="loginDialog"><form action="/users/login" data-two-factor-action="/users/2fa">
  <div class="login-step"><input name="loginIdOrEmail" value="old"><input name="password" value="old"><input type="checkbox" name="rememberMe" checked></div>
  <div class="two-factor-step" style="display:none"><input name="code" value=""><input type="checkbox" name="rememberDevice"><div class="security-key-step"><button type="button" class="security-key-use" data-options-uri="/o" data-submit-uri="/s"></button></div></div>
  <div class="error" style="display:none"><span class="error-message"></span></div></form></div>`;

async function login() {
  const page = await pageWith({ stubs: false, html: LOGIN, scripts: [MSG, 'common/yona.Common.js'] });
  await page.evaluate(() => {
    window.__ajax = []; window.__modal = []; window.__shake = 0;
    $.fn.effect = function () { window.__shake++; return this; };
    window.bootstrap = { Modal: { getOrCreateInstance: (el) => ({ show: () => window.__modal.push(el.id) }) } };
    yona.http = { ajax: (url, o) => { window.__ajax.push([url, o]); const h = { done(cb) { if (window.__ok !== undefined) setTimeout(() => cb(window.__ok), 0); return h; }, fail(cb) { if (window.__fail) setTimeout(() => cb(window.__fail), 0); return h; },
      // 실제 yona.http처럼 await할 수 있다. 응답을 정하지 않으면 끝내 완료되지 않는다
      then(ok, bad) { return new Promise((res, rej) => { if (window.__ok !== undefined) setTimeout(() => res(window.__ok), 0); else if (window.__fail) setTimeout(() => rej(window.__fail), 0); }).then(ok, bad); } }; return h; } };
  });
  await page.addScriptTag({ content: classicFromEsm('esm/LoginDialog.js') });
  await page.evaluate(() => new Promise((r) => $(r))); // 준비 처리기까지 기다린다
  return page;
}

export const tests = [
  { name: '로그인 대화상자: 로그인 필요 요소를 누르면 입력을 비우고 모달을 열며 링크 이동을 막는다. 입력칸이면 포커스를 뗀다', run: async () => {
    const page = await login();
    const r = await page.evaluate(() => { const e = $.Event('click'); $('a[data-login]').trigger(e); return [e.isDefaultPrevented(), e.isPropagationStopped(), $('[name=loginIdOrEmail]').val(), $('[name=password]').val(), window.__modal]; });
    assert.deepEqual(r, [true, true, '', '', ['loginDialog']]);
    await page.evaluate(() => { $('#txt').trigger('focus'); $('#txt').trigger('click'); }); assert.notEqual(await page.evaluate(() => document.activeElement.id), 'txt'); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '로그인 대화상자: 제출하면 폼 주소로 아이디, 비밀번호, 로그인 유지를 보내고 기본 제출은 막는다', run: async () => {
    const page = await login();
    const r = await page.evaluate(() => { $('[name=loginIdOrEmail]').val('kim'); $('[name=password]').val('pw'); const e = $.Event('submit'); $('form').trigger(e); const [url, o] = window.__ajax[0]; return [e.isDefaultPrevented(), url, o.type, o.dataType, o.data]; });
    assert.deepEqual(r, [true, '/users/login', 'post', 'json', { loginIdOrEmail: 'kim', password: 'pw', rememberMe: true }]); await page.close();
  } },
  { name: '로그인 대화상자: 2단계 인증이 필요하다는 응답이면 코드 단계로 바꾸고 보안 키 단계는 지원될 때만 보인다', run: async () => {
    const page = await login();
    await page.evaluate(() => { window.__ok = { twoFactor: true, securityKey: true }; window.YonaWebAuthn = { supported: () => true }; $('form').trigger('submit'); }); await page.waitForTimeout(40);
    assert.deepEqual(await page.evaluate(() => [$('.login-step').css('display'), $('.two-factor-step').css('display'), $('.security-key-step').css('display')]), ['none', 'block', 'block']);
    await page.evaluate(() => { window.__ajax.length = 0; $('[name=code]').val('123456'); $('form').trigger('submit'); });
    const [url, o] = (await get(page, '__ajax'))[0]; assert.equal(url, '/users/2fa'); assert.deepEqual(o.data, { code: '123456', rememberDevice: false }); await page.close();
  } },
  { name: '로그인 대화상자: 실패하면 네트워크(readyState 0), 서버 메시지(JSON), 4xx, 5xx, 그 밖의 문구를 오류 칸에 보이고 흔든다', run: async () => {
    const page = await login(); const cases = [{ readyState: 0, status: 0 }, { readyState: 4, status: 401, responseText: '{"message":"user.login.bad"}' }, { readyState: 4, status: 404, responseText: 'html' }, { readyState: 4, status: 500, responseText: '' }, { readyState: 4, status: 302, responseText: '' }];
    const out = []; for (const c of cases) { await page.evaluate((c) => { window.__fail = c; $('form').trigger('submit'); }, c); await page.waitForTimeout(40); out.push(await page.evaluate(() => [$('.error-message').text(), $('.error').css('display')])); }
    assert.deepEqual(out, [['user.login.failed.network', 'block'], ['user.login.bad', 'block'], ['user.login.failed.client', 'block'], ['user.login.failed.server', 'block'], ['user.login.failed', 'block']]); assert.equal(await get(page, '__shake'), 5); await page.close();
  } },
  { name: '로그인 대화상자: 주소가 /users/ 아래이면 아무 처리기도 붙이지 않는다', run: async () => {
    const page = await pageWith({ stubs: false, html: LOGIN, scripts: [MSG, 'common/yona.Common.js'] });
    await page.route('http://t.test/**', (r) => r.fulfill({ contentType: 'text/html', body: '<!doctype html><body></body>' })); await page.goto('http://t.test/users/loginform');
    await page.setContent(LOGIN); await page.addScriptTag({ path: '/home/jiho/yona-convert/yona/src/main/resources/static/assets/javascripts/lib/jquery/jquery-3.7.1.js' });
    await page.evaluate(() => { window.bootstrap = { Modal: { getOrCreateInstance: () => ({ show: () => { window.__shown = true; } }) } }; });
    await page.addScriptTag({ content: 'window.Messages = function(k){return k;};' }); await page.addScriptTag({ content: classicFromEsm('esm/LoginDialog.js') });
    await page.evaluate(() => new Promise((r) => $(r))); await page.evaluate(() => { $('a[data-login]').trigger('click'); }); assert.equal(await get(page, '__shown'), undefined); await page.close();
  } },

  { name: '임시 저장: 키를 치고 5초 뒤 현재 경로 키로 localStorage에 저장하고, 비우면 지운다', run: async () => {
    const page = await pageWith({ stubs: false, html: `<div class="editor-notice-label"><b>x</b></div><textarea id="t"></textarea>`, scripts: [MSG, 'common/yona.Common.js'] });
    await page.evaluate(() => { const m = new Map(); Object.defineProperty(window, 'localStorage', { value: { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)), removeItem: (k) => m.delete(k), key: (i) => [...m.keys()][i], get length() { return m.size; } } }); window.__ls = m; window.__real = window.setTimeout; window.setTimeout = (fn, ms) => window.__real(fn, ms === 5000 ? 30 : ms); });
    await page.addScriptTag({ content: classicFromEsm('esm/service/temporarySaveHandler.js') });
    await page.evaluate(() => { temporarySaveHandler($('#t')); $('#t').val('초안').trigger('keyup'); }); await page.waitForTimeout(100);
    const key = await page.evaluate(() => location.pathname); assert.equal(await page.evaluate((k) => window.__ls.get(k), key), '초안'); assert.equal(await page.evaluate(() => $('.editor-notice-label').text()), 'Draft saved');
    await page.evaluate(() => { $('#t').val('').trigger('keyup'); }); assert.equal(await page.evaluate((k) => window.__ls.has(k), key), false); await page.close();
  } },
  { name: '임시 저장: 수정 댓글 입력칸은 별도 키(-last-comment-update-draft)에 저장한다. 저장된 초안이 있으면 입력칸을 채우고, 마지막 수정 댓글과 같으면 초안을 지운다', run: async () => {
    const page = await pageWith({ stubs: false, html: `<div class="editor-notice-label"></div><textarea id="t"></textarea><textarea id="u" data-editor-mode="update-comment-body"></textarea>`, scripts: [MSG, 'common/yona.Common.js'] });
    await page.evaluate(() => { const m = new Map(); Object.defineProperty(window, 'localStorage', { value: { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)), removeItem: (k) => m.delete(k), key: (i) => [...m.keys()][i], get length() { return m.size; } } }); window.__ls = m; window.__real = window.setTimeout; window.setTimeout = (fn, ms) => window.__real(fn, ms === 5000 ? 30 : ms); });
    await page.addScriptTag({ content: classicFromEsm('esm/service/temporarySaveHandler.js') });
    await page.evaluate(() => { temporarySaveHandler($('#u')); $('#u').val('수정초안').trigger('keyup'); }); await page.waitForTimeout(100);
    assert.equal(await page.evaluate(() => window.__ls.get(location.pathname + '-last-comment-update-draft')), '수정초안'); assert.equal(await page.evaluate(() => window.__ls.has(location.pathname)), false);
    await page.evaluate(() => { window.__ls.set(location.pathname, '저장된 초안'); temporarySaveHandler($('#t')); }); assert.equal(await page.evaluate(() => $('#t').val()), '저장된 초안');
    await page.evaluate(() => { $('textarea.content').remove(); $('body').append('<textarea class="content" data-editor-mode="update-comment-body">저장된 초안</textarea>'); $('#t').val(''); temporarySaveHandler($('#t')); });
    assert.deepEqual(await page.evaluate(() => [$('#t').val(), window.__ls.has(location.pathname)]), ['', false]); await page.close();
  } },
  { name: '임시 저장: contentInitialized가 false면 저장된 초안으로 입력칸을 채우지 않는다. removeCurrentPage...는 두 키를 모두 지운다', run: async () => {
    const page = await pageWith({ stubs: false, html: `<textarea id="t"></textarea>`, scripts: [MSG, 'common/yona.Common.js'] });
    await page.evaluate(() => { const m = new Map(); Object.defineProperty(window, 'localStorage', { value: { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)), removeItem: (k) => m.delete(k), key: (i) => [...m.keys()][i], get length() { return m.size; } } }); window.__ls = m; });
    await page.addScriptTag({ content: classicFromEsm('esm/service/temporarySaveHandler.js') });
    await page.evaluate(() => { window.__ls.set(location.pathname, 'x'); window.__ls.set(location.pathname + '-last-comment-update-draft', 'y'); temporarySaveHandler($('#t'), false); });
    assert.equal(await page.evaluate(() => $('#t').val()), '');
    await page.evaluate(() => removeCurrentPageTemprarySavedContent()); assert.equal(await page.evaluate(() => window.__ls.size), 0); await page.close();
  } },

  { name: '변경 감지: 주기마다 현재 체크섬, 댓글 수, 수정일을 POST하고, 댓글이 늘면 새로고침 알림과 N 배지를 띄운다(간격은 1.2배씩 늘어난다)', run: async () => {
    const page = await pageWith({ stubs: false, html: `<input id="issueBodyChecksum" value="abc"><input id="numOfComments" value="2"><input id="issueUpdateDate" value="D">`, scripts: [MSG, 'common/yona.Common.js'] });
    await page.evaluate(() => {
      window.__ajax = []; window.__notes = []; window.__badge = []; window.__delays = [];
      window.__real = window.setTimeout; window.setTimeout = (fn, ms) => { window.__delays.push(ms); return window.__real(fn, 5); };
      yona.ui = yona.ui || {}; yona.ui.Favicon = function () { this.badge = (c) => window.__badge.push(c); };
      $yona.notify = (m, t, title) => window.__notes.push([t, title]);
      let n = 0; yona.http = { ajax: (o) => { window.__ajax.push(o); n++; return Promise.resolve(n === 1 ? { numOfComments: 3, issueBodyChanged: false } : { numOfComments: 3, issueBodyChanged: true, issueBodyChecksum: 'new' }); } };
    });
    await page.addScriptTag({ content: classicFromEsm('esm/service/detectChange.js') });
    await page.evaluate(() => detectPageChange('/detect')); await page.waitForTimeout(120);
    const ajax = await get(page, '__ajax'); assert.equal(ajax[0].url, '/detect'); assert.deepEqual(JSON.parse(ajax[0].data), { issueBodyChecksum: 'abc', numOfComments: '2', lastUpdateDate: 'D' });
    assert.deepEqual(JSON.parse(ajax[1].data).issueBodyChecksum, 'abc'); assert.deepEqual(JSON.parse(ajax[1].data).numOfComments, 3);
    assert.deepEqual((await get(page, '__badge')).slice(0, 2), ['N', 'N']); assert.deepEqual((await get(page, '__delays')).slice(0, 3).map((d) => Math.round(d)), [3600, 4320, 5184]);
    await page.close();
  } },
];
