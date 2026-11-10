// 비밀번호 로그인(POST /users/login)을 실제 브라우저로 확인한다: 틀린 비밀번호의 안내와 redirectUrl 유지, 로그인과 로그인 유지 쿠키(30일, HttpOnly),
// 쿠키만으로 로그인, 로그아웃, 팝업(AJAX) 로그인의 응답(성공 text/plain {}, 실패 403 JSON, 입력 누락 400 JSON), 입력 누락 폼의 400(X-43 3단계, D-129).
//
// 실행: BASE=http://127.0.0.1:19402 E2E_USER=admin E2E_PASS=... [E2E_OUT=캡처 폴더] node e2e/login-flow.mjs
// 서버는 실행 중이어야 한다. 하나라도 어긋나면 종료 코드 1이다.
import { chromium } from '/home/jiho/yona-convert/node_modules/playwright/index.mjs';
const B = process.env.BASE ?? 'http://127.0.0.1:19402', OUT = process.env.E2E_OUT ?? '/tmp';
const USER = process.env.E2E_USER ?? 'admin', PASS = process.env.E2E_PASS;
if (!PASS) { console.error('E2E_PASS가 필요하다'); process.exit(2); }
let failed = 0;
const check = (name, ok, detail = '') => { console.log(`${ok ? '통과' : '실패'}  ${name}${detail ? '  ' + detail : ''}`); if (!ok) failed++; };
const b = await chromium.launch();
const ctx = await b.newContext({ viewport: { width: 1100, height: 700 }, locale: 'ko-KR' });
const p = await ctx.newPage();
const loggedIn = () => p.evaluate(async () => (await fetch('/user/editform', { redirect: 'manual' })).type !== 'opaqueredirect');
const login = async (id, pw, remember = false) => {
  await p.fill('.login-form-wrap input[name=loginIdOrEmail]', id);
  await p.fill('.login-form-wrap input[name=password]', pw);
  if (remember) await p.check('.login-form-wrap #remember-me');
  await Promise.all([p.waitForLoadState('load'), p.click('.login-form-wrap button[type=submit]')]);
};

// 1) 틀린 비밀번호: 로그인 화면으로 돌아오고 안내가 보인다
await p.goto(B + '/users/loginform?redirectUrl=/projects', { waitUntil: 'load' });
await login(USER, 'wrong-password');
check('틀린 비밀번호는 로그인 화면으로 돌아온다', p.url().includes('/users/loginform'), p.url().replace(B, ''));
check('redirectUrl을 유지한다', p.url().includes('redirectUrl=/projects') || p.url().includes('redirectUrl=%2Fprojects'));
const shown = await p.waitForFunction(() => document.body.innerText.includes('잘못되었습니다'), null, { timeout: 4000 }).then(() => true, () => false);
check('실패 안내가 화면에 보인다', shown, '아이디, 이메일 또는 비밀번호가 잘못되었습니다');
await p.screenshot({ path: `${OUT}/1-wrong-password.png` });
check('로그인되지 않았다', !(await loggedIn()));

// 2) 맞는 비밀번호 + 로그인 유지: redirectUrl로 이동하고 로그인 유지 쿠키가 생긴다
await p.goto(B + '/users/loginform?redirectUrl=/projects', { waitUntil: 'load' });
await login(USER, PASS, true);
check('로그인하면 redirectUrl로 이동한다', p.url().replace(B, '').startsWith('/projects'), p.url().replace(B, ''));
check('로그인되었다', await loggedIn());
const remember = (await ctx.cookies(B)).find((c) => c.name === 'yona.token');
check('로그인 유지 쿠키(yona.token)가 있다', !!remember);
const days = remember ? Math.round((remember.expires - Date.now() / 1000) / 86400) : 0;
check('쿠키 유효 기간은 약 30일이다', days >= 29 && days <= 30, `${days}일`);
check('쿠키는 HttpOnly이다', remember?.httpOnly === true);
await p.screenshot({ path: `${OUT}/2-after-login.png` });

// 3) 로그인 유지 쿠키만 가진 새 브라우저 문맥도 로그인되어 있다
const ctx2 = await b.newContext({ locale: 'ko-KR' });
await ctx2.addCookies([remember]);
const p2 = await ctx2.newPage();
await p2.goto(B + '/', { waitUntil: 'load' });
const viaCookie = await p2.evaluate(async () => (await fetch('/user/editform', { redirect: 'manual' })).type !== 'opaqueredirect');
check('쿠키만으로 로그인된 상태가 된다', viaCookie);
await ctx2.close();

// 4) 로그아웃하면 로그인 상태가 풀린다
await p.goto(B + '/users/logout', { waitUntil: 'load' });
check('로그아웃하면 로그인되어 있지 않다', !(await loggedIn()));

// 5) 팝업(AJAX) 로그인: 성공은 text/plain의 {}, 실패는 403 JSON, 입력 누락은 400 JSON
const ajax = (id, pw) => p.evaluate(async ([id, pw]) => {
  const body = new URLSearchParams(); if (id !== null) body.set('loginIdOrEmail', id); if (pw !== null) body.set('password', pw);
  const r = await fetch('/users/login', { method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/x-www-form-urlencoded' }, body });
  return { status: r.status, type: r.headers.get('content-type'), text: await r.text() };
}, [id, pw]);
const bad = await ajax(USER, 'nope');
check('AJAX 실패는 403 JSON이다', bad.status === 403 && bad.text.includes('user.login.invalid') && /application\/json/.test(bad.type), `${bad.status} ${bad.text}`);
const missing = await ajax(USER, null);
check('AJAX 입력 누락은 400 JSON이다', missing.status === 400 && missing.text.includes('user.login.required'), `${missing.status} ${missing.text}`);
const good = await ajax(USER, PASS);
check('AJAX 성공은 text/plain의 {}이다', good.status === 200 && good.text === '{}' && /text\/plain/.test(good.type), `${good.status} ${good.type} ${good.text}`);
check('AJAX 로그인 뒤 로그인되었다', await loggedIn());

// 6) 입력 누락 폼은 400으로 로그인 화면을 다시 그린다
const formMissing = await p.evaluate(async () => { const body = new URLSearchParams({ loginIdOrEmail: 'someone' }); const r = await fetch('/users/login', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body }); return { status: r.status, html: (await r.text()).includes('loginIdOrEmail') }; });
check('폼 입력 누락은 400이고 로그인 화면을 다시 그린다', formMissing.status === 400 && formMissing.html, `${formMissing.status}`);

await b.close();
console.log(failed === 0 ? '\n모두 통과' : `\n실패 ${failed}건`);
process.exit(failed === 0 ? 0 : 1);
