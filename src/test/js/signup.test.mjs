import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const HTML = `<form name="signup"><input id="loginId" name="loginId"><input id="email" name="email"><input id="password" name="password"><input id="retypedPassword" name="retypedPassword"></form>`;
const STUBS = {
  content: `
    $yobi.getTrim = function (s) { return $.trim(s); };
    window.__popovers = [];
    $yobi.showPopover = function (el, o) { window.__popovers.push(el.attr('name') + ':' + o.content); };
    $yobi.hidePopover = function () {};
    window.FormValidator = function () { this.registerCallback = function () {}; this.setMessage = function () {}; };`
};

export const tests = [{
  name: '비밀번호 칸과 확인 칸의 검사 타이머가 따로 있어 번갈아 입력해도 앞선 검사가 취소되지 않는다 (C025)',
  async run() {
    const page = await pageWith({ html: HTML, scripts: [STUBS, 'service/yobi.user.SignUp.js'] });
    const popovers = await page.evaluate(async () => {
      new yobi.user.SignUp({ sLogindIdCheckUrl: '/x/', sEmailCheckUrl: '/y/' });
      $('#password').val('ab').trigger('keyup');            // 4자 미만이라 오류가 나와야 한다
      $('#retypedPassword').val('zz').trigger('keyup');     // 곧바로 확인 칸을 입력한다
      await new Promise((r) => setTimeout(r, 700));
      return window.__popovers;
    });
    await page.close();
    assert.ok(popovers.some((p) => p.startsWith('password:validation.tooShortPassword')), '비밀번호 길이 검사가 사라졌다: ' + JSON.stringify(popovers));
  }
}];
