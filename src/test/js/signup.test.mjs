import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const HTML = `<form name="signup"><input id="loginId" name="loginId"><input id="email" name="email"><input id="password" name="password"><input id="retypedPassword" name="retypedPassword"></form>`;
const STUBS = {
  content: `
    $yona.getTrim = function (s) { return String(s).trim(); };
    window.__popovers = [];
    $yona.showPopover = function (el, o) { window.__popovers.push(el.attr('name') + ':' + o.content); };
    $yona.hidePopover = function () {};
    window.yona = window.yona || {}; yona.ui = yona.ui || {}; yona.ui.FormValidator = function () { this.registerCallback = function () {}; this.setMessage = function () {}; };`
};

export const tests = [{
  name: '비밀번호 칸과 확인 칸의 검사 타이머가 따로 있어 번갈아 입력해도 앞선 검사가 취소되지 않는다 (C025)',
  async run() {
    const page = await pageWith({ html: HTML, scripts: [STUBS, 'esm/service/user.SignUp.js'] });
    const popovers = await page.evaluate(async () => {
      new yona.user.SignUp({ sLogindIdCheckUrl: '/x/', sEmailCheckUrl: '/y/' });
      $('#password').val('ab').trigger('keyup');            // 4자 미만이라 오류가 나와야 한다
      $('#retypedPassword').val('zz').trigger('keyup');     // 곧바로 확인 칸을 입력한다
      await new Promise((r) => setTimeout(r, 700));
      return window.__popovers;
    });
    await page.close();
    assert.ok(popovers.some((p) => p.startsWith('password:validation.tooShortPassword')), '비밀번호 길이 검사가 사라졌다: ' + JSON.stringify(popovers));
  }
}, {
  name: '이메일과 로그인 ID는 인코딩해서 사용 여부를 묻는다 (v1_6은 +가 공백으로 읽혀 다른 값을 물었다, 고쳤다 usersec-F4)',
  async run() {
    const page = await pageWith({ html: HTML, scripts: [STUBS, 'esm/service/user.SignUp.js'] });
    const urls = await page.evaluate(async () => {
      const seen = [];
      yona.http.ajax = async (o) => { seen.push(o.url); return { isExist: false }; };
      new yona.user.SignUp({ sLogindIdCheckUrl: '/x/', sEmailCheckUrl: '/y/' });
      $('#email').val('pl+us@example.com').trigger('blur');
      $('#loginId').val('a b').trigger('blur');
      await new Promise((r) => setTimeout(r, 100));
      return seen;
    });
    await page.close();
    assert.ok(urls.includes('/y/pl%2Bus%40example.com'), '이메일이 인코딩되지 않았다: ' + JSON.stringify(urls));
  }
}
  , {
  name: '가입 화면의 아이디 검사는 한글을 허용한다 (v1_6의 화면은 한글을 거부했고 서버는 받았다, 서버에 맞춰 고쳤다, usersec-F3)',
  async run() {
    const page = await pageWith({ html: HTML, scripts: [STUBS, 'esm/service/user.SignUp.js'] });
    const out = await page.evaluate(async () => {
      window.__popovers.length = 0;
      new yona.user.SignUp({ sLogindIdCheckUrl: '/x/', sEmailCheckUrl: '/y/' });
      const ok = [];
      for (const id of ['홍길동', 'kim_길동', 'a..b']) { $('#loginId').val(id).trigger('focusout'); await new Promise((r) => setTimeout(r, 50)); ok.push(window.__popovers.filter((p) => p.startsWith('loginId:')).length); window.__popovers.length = 0; }
      return ok;
    });
    await page.close();
    assert.deepEqual(out, [0, 0, 1], '한글 아이디는 형식 오류가 없어야 하고 a..b는 형식 오류여야 한다: ' + JSON.stringify(out));
  }
}
  , {
  name: '비밀번호는 8자 미만이면 길이 오류이고 8자이면 오류가 없다 (welcome-F3, NIST 기준 최소 8자)',
  async run() {
    const page = await pageWith({ html: HTML, scripts: [STUBS, 'esm/service/user.SignUp.js'] });
    const out = await page.evaluate(async () => {
      new yona.user.SignUp({ sLogindIdCheckUrl: '/x/', sEmailCheckUrl: '/y/' });
      const seen = [];
      for (const pw of ['abc1234', 'abc12345']) { window.__popovers.length = 0; $('#password').val(pw).trigger('keyup'); await new Promise((r) => setTimeout(r, 700)); seen.push(window.__popovers.filter((p) => p.startsWith('password:validation.tooShortPassword')).length); }
      return seen;
    });
    await page.close();
    assert.deepEqual(out, [1, 0], JSON.stringify(out));
  }
}
];
