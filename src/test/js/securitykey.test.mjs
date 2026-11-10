// 보안 키 로그인(`esm/LoginDialog.js`의 `_useSecurityKey`): 실제 `yona.http`(fetch 기반)로 옵션 요청, 인증, 제출을 이어 쓰고 실패 문구를 보이는지 고정한다.
// `.then(...).then(...).fail(...)` 연결이 jQuery에서는 되지만 `yona.http.then`이 일반 Promise를 돌려주면 `.fail`이 없어 깨진다(D-123 5단계에서 찾음).
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `<div id="loginDialog"><form action="/users/login"><div class="login-step"><input name="loginIdOrEmail"><input name="password"><input type="checkbox" name="rememberMe"></div>
<div class="two-factor-step"><input name="code"><input type="checkbox" name="rememberDevice"><div class="security-key-step"><button type="button" class="security-key-use" data-options-uri="http://t.test/o" data-submit-uri="http://t.test/s"></button></div></div>
<div class="error" style="display:none"><span class="error-message"></span></div></form></div>`;

async function open(responses) {
  const page = await pageWith({ stubs: false, html: HTML, scripts: [{ content: 'window.Messages = function (k) { return k; };' }, 'common/yona.Common.js'] });
  await page.evaluate((responses) => {
    window.__calls = []; $.fn.effect = function () { return this; };
    window.YonaWebAuthn = { supported: () => true, authenticate: async (o) => 'cred:' + o };
    window.fetch = async (url, init) => { window.__calls.push([url, init && init.method, init && init.body]); const r = responses[url]; return new Response(r.body, { status: r.status, headers: { 'Content-Type': r.type } }); };
  }, responses);
  await page.addScriptTag({ content: classicFromEsm('esm/LoginDialog.js') });
  await page.evaluate(() => new Promise((r) => $(r)));
  return page;
}

export const tests = [
  { name: '보안 키: 옵션 요청, 인증, 제출을 차례로 보내고, 제출이 실패하면 서버가 준 message를 오류 칸에 보인다', run: async () => {
    const page = await open({ 'http://t.test/o': { status: 200, type: 'text/plain', body: 'opts' }, 'http://t.test/s': { status: 400, type: 'application/json', body: '{"message":"user.twoFactor.bad"}' } });
    await page.evaluate(() => { $('.security-key-use').trigger('click'); }); await page.waitForTimeout(150);
    const calls = await page.evaluate(() => window.__calls);
    assert.deepEqual(calls.map((c) => [c[0], c[1]]), [['http://t.test/o', 'POST'], ['http://t.test/s', 'POST']]);
    assert.match(calls[1][2], /credential=cred%3Aopts/); assert.match(calls[1][2], /rememberDevice=false/);
    assert.deepEqual(await page.evaluate(() => [$('.error-message').text(), $('.error').css('display')]), ['user.twoFactor.bad', 'block']); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '보안 키: 서버 message가 없으면 기본 문구(user.twoFactor.invalid)를 보인다', run: async () => {
    const page = await open({ 'http://t.test/o': { status: 500, type: 'text/plain', body: 'x' } });
    await page.evaluate(() => { $('.security-key-use').trigger('click'); }); await page.waitForTimeout(150);
    assert.equal(await page.evaluate(() => $('.error-message').text()), 'user.twoFactor.invalid'); await page.close();
  } },
];
