// 작은 제3자 파일(humanize.js, rgbcolor.js, parseuri.js, jquery.elevator.js)을 표준 API와 직접 쓴 작은 구현으로 바꾼 것(D-122 2단계)이 옛 파일과 같은 결과를 내는지 확인한다.
// 기대값은 옛 파일을 실행해 얻은 표이다(파일 크기 글자, 색 읽기, 쿼리 문자열, 엘리베이터의 요소와 동작). 엘리베이터는 ELEV_IMPL=old로 옛 플러그인에서도 돌려 볼 수 있다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, JS, launch, esmPage } from './harness.mjs';

const OLD = process.env.CLAUDE_JOB_DIR ? path.join(process.env.CLAUDE_JOB_DIR, 'tmp/old-libs') : null;
const ELEV_OLD = process.env.ELEV_IMPL === 'old';
const UV_OLD = process.env.UV_IMPL === 'old';
const FV_OLD = process.env.FV_IMPL === 'old';
const VAL_OLD = process.env.VAL_IMPL === 'old';

const SIZE_CASES = [["0", "0 bytes"], ["1", "1 bytes"], ["2", "2 bytes"], ["512", "512 bytes"], ["1000", "1,000 bytes"], ["1023", "1,023 bytes"], ["1024", "1.00 Kb"], ["1025", "1.00 Kb"], ["1536", "1.50 Kb"], ["2048", "2.00 Kb"], ["10240", "10.00 Kb"], ["1048575", "1,024.00 Kb"], ["1048576", "1.00 Mb"], ["1572864", "1.50 Mb"], ["5242880", "5.00 Mb"], ["10485760", "10.00 Mb"], ["123456789", "117.74 Mb"], ["1073741824", "1.00 Gb"], ["-1", "0 bytes"], ["5368709120", "5.00 Gb"], ["1099511627776", "1.00 Tb"], ["1125899906842624", "1.00 Pb"], ["1152921504606847000", "1,024.00 Pb"], ["\"2048\"", "2.00 Kb"], ["\"5242880\"", "5.00 Mb"], ["\"\"", "0 bytes"], ["\" \"", "0 bytes"], ["\"abc\"", "0.00 Pb"], ["NaN", "0.00 Pb"], ["undefined", "0.00 Pb"], ["null", "0 bytes"], ["1.5", "2 bytes"], ["1023.9", "1,024 bytes"], ["0.4", "0 bytes"], ["999.5", "1,000 bytes"], ["1e+21", "888,178.42 Pb"]];
const COLOR_CASES = [["yellow", "dimgray", "#ffff00"], ["red", "white", "#ff0000"], ["white", "dimgray", "#ffffff"], ["black", "white", "#000000"], ["dimgray", "white", "#696969"], ["#fff", "dimgray", "#ffffff"], ["#FFF", "dimgray", "#ffffff"], ["#ffff00", "dimgray", "#ffff00"], ["#FFFF00", "dimgray", "#ffff00"], ["ffff00", "dimgray", "#ffff00"], ["#abc", "white", "#aabbcc"], ["rgb(255,255,0)", "dimgray", "#ffff00"], ["rgb(255, 255, 0)", "dimgray", "#ffff00"], ["rgb(0, 0, 0)", "white", "#000000"], ["rgba(0, 0, 0, 0)", "white", false], ["rgba(255, 255, 0, 0.5)", "white", false], ["", "white", false], ["notacolor", "white", false], ["#12", "white", false], ["#12345", "white", false], ["transparent", "white", false], ["lightgoldenrodyellow", "dimgray", "#fafad2"], ["#2196f3", "white", "#2196f3"], ["#4caf50", "white", "#4caf50"], ["#f44336", "white", "#f44336"], [null, "white", false], ["__undef__", "white", false], ["  Red ", "white", "#ff0000"], ["rgb(300,0,0)", "white", "#ff0000"], ["rgb(1,2,3)", "white", "#010203"]];


/** 엘리베이터(이슈와 게시글 보기의 위, 아래 이동 단추)를 높이 6000px 문서에서 연다. 옛 플러그인(ELEV_IMPL=old)과 새 구현 모두 같은 DOM과 동작을 낸다. */
async function openElevator(options = "{ shape: 'rounded', tooltips: true }") {
  const page = ELEV_OLD
    ? await pageWith({ stubs: false, scripts: [{ path: path.join(OLD, 'elevator/jquery.elevator.js') }], html: '<div style="height:6000px">문서</div>' })
    : await esmPage({ bodyHtml: '<div style="height:6000px">문서</div>', head: '<link rel="stylesheet" href="/assets/javascripts/lib/elevator/jquery.elevator.css">' });
  if (ELEV_OLD) await page.addStyleTag({ path: path.join(OLD, 'elevator/jquery.elevator.css') });
  await page.setViewportSize({ width: 1000, height: 700 });
  await page.evaluate((o) => { const opts = new Function(`return ${o};`)(); if (window.yona && yona.ui && yona.ui.Elevator) yona.ui.Elevator.init(opts); else $.elevator(opts); }, options);
  await page.waitForTimeout(300);
  return page;
}
const elevClasses = (page, sel) => page.evaluate((q) => [...document.querySelector(q).classList].sort(), sel);

export const elevatorTests = [
  { name: '엘리베이터: 오른쪽 아래에 고정된 요소가 만들어지고 위, 아래 단추가 순서대로 있다(둥근 모양, 툴팁은 span)', run: async () => {
    const page = await openElevator(); const r = await page.evaluate(() => { const e = document.querySelector('.jq-elevator'); return { cls: [...e.classList].sort(), pos: getComputedStyle(e).position, kids: [...e.children].map((a) => [a.tagName, a.getAttribute('href'), a.firstChild.textContent, a.querySelector('.jq-title').textContent, a.hasAttribute('title')]), inBody: e.parentNode === document.body }; });
    assert.deepEqual(r.cls, ['align-bottom', 'align-right', 'jq-elevator', 'rounded']); assert.equal(r.pos, 'fixed'); assert.equal(r.inBody, true);
    assert.deepEqual(r.kids, [['A', '#', '▲', 'Move to Top', false], ['A', '#', '▼', 'Move to Bottom', false]]); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '엘리베이터: 툴팁을 끄면 span 대신 title 속성이 붙는다', run: async () => {
    const page = await openElevator("{ shape: 'square', tooltips: false }"); const r = await page.evaluate(() => [...document.querySelectorAll('.jq-elevator a')].map((a) => [a.getAttribute('title'), !!a.querySelector('.jq-title')]));
    assert.deepEqual(r, [['Move to Top', false], ['Move to Bottom', false]]); assert.ok((await elevClasses(page, '.jq-elevator')).includes('square')); await page.close();
  } },
  { name: '엘리베이터: 문서 맨 위에서는 위 단추가 작고 아래 단추가 크다. 중간에서는 둘 다 중간, 맨 아래에서는 반대가 된다', run: async () => {
    const page = await openElevator(); const state = () => page.evaluate(() => [...document.querySelector('.jq-top').classList].filter((c) => /^jq-(sml|mid|big)$/.test(c)).concat('|', [...document.querySelector('.jq-bottom').classList].filter((c) => /^jq-(sml|mid|big)$/.test(c))));
    assert.deepEqual(await state(), ['jq-sml', '|', 'jq-big']);
    await page.evaluate(() => window.scrollTo(0, 3000)); await page.waitForTimeout(400); assert.deepEqual(await state(), ['jq-mid', '|', 'jq-mid']);
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight)); await page.waitForTimeout(400); assert.deepEqual(await state(), ['jq-big', '|', 'jq-sml']); await page.close();
  } },
  { name: '엘리베이터: 아래 단추는 문서 맨 아래로, 위 단추는 맨 위로 부드럽게 이동하고 주소에 #을 붙이지 않는다', run: async () => {
    const page = await openElevator(); const top = () => page.evaluate(() => Math.round(window.scrollY)); const bottomY = await page.evaluate(() => document.documentElement.scrollHeight - innerHeight);
    await page.click('.jq-bottom'); await page.waitForFunction((y) => Math.abs(scrollY - y) < 1, bottomY, { timeout: 4000 }); assert.equal(await page.evaluate(() => location.hash), '');
    await page.click('.jq-top'); await page.waitForFunction(() => scrollY === 0, null, { timeout: 4000 }); assert.equal(await top(), 0); assert.equal(await page.evaluate(() => location.hash), ''); await page.close();
  } },
];


/** 사용자 보기 화면에서 `daysAgo` 입력에 Enter를 쳤을 때 주소 표시줄에 기억하는 쿼리 문자열을 얻는다(`history.replaceState`의 인자). */
async function rememberedQuery(search, daysAgo) {
  const browser = await launch(); const ctx = await browser.newContext(); const page = await ctx.newPage(); page.errors = []; 
  page.on('pageerror', (e) => page.errors.push(String(e.message || e)));
  await page.route('http://uv.test/**', (route) => route.fulfill({ contentType: 'text/html', body: '<!doctype html><body><input id="daysAgoBtn" value="' + daysAgo + '"></body>' }));
  await page.goto('http://uv.test/user/someone' + search);
  const scripts = [path.join(JS, 'lib/jquery/jquery-3.7.1.js'), ...(UV_OLD ? [path.join(OLD, 'parseuri.js'), path.join(OLD, 'yona.Common.js'), path.join(OLD, 'yona.user.View.js')] : [path.join(JS, 'common/yona.Common.js'), path.join(JS, 'service/yona.user.View.js')])];
  for (const f of scripts) await page.addScriptTag({ path: f });
  await page.evaluate(() => { const orig = history.replaceState.bind(history); history.replaceState = (st, t, url) => { sessionStorage.setItem('seen', JSON.stringify(JSON.parse(sessionStorage.getItem('seen') || '[]').concat(String(url)))); return orig(st, t, url); }; window.Messages = (k) => k; window._initTwoColumnMode = () => {}; new yona.user.View({ sTabSelected: 'issues' }); $('#daysAgoBtn').trigger($.Event('keypress', { key: 'Enter' })); });
  await page.waitForTimeout(500); const recorded = await page.evaluate(() => JSON.parse(sessionStorage.getItem('seen') || '[]')); await ctx.close(); return { seen: recorded, errors: page.errors };
}

export const userViewTests = [
  { name: '사용자 보기: daysAgo에서 Enter를 치면 daysAgo, 선택한 탭과 주소의 groups 값(인코딩 그대로)을 쿼리 문자열로 기억한다', run: async () => {
    const withGroups = await rememberedQuery('?groups=a%20b&x=1', '7'); assert.deepEqual(withGroups.seen, ['?daysAgo=7&selected=issues&groups=a%20b'], JSON.stringify(withGroups)); assert.deepEqual(withGroups.errors, []);
    const without = await rememberedQuery('?x=1', '30'); assert.deepEqual(without.seen, ['?daysAgo=30&selected=issues']);
    const none = await rememberedQuery('', '1'); assert.deepEqual(none.seen, ['?daysAgo=1&selected=issues']);
  } },
];


/** 32x32 초록 파비콘에 position up, 파란 배경으로 배지 'N'을 그린 결과를 읽는다. 배지 영역(파랑)과 글자(흰색)의 경계 상자와 바뀌지 않은 화소 수. */
async function badgeResult(drawCall) {
  const page = FV_OLD ? await pageWith({ stubs: false, html: '', scripts: [{ path: path.join(OLD, 'favico.min.js') }] }) : await esmPage({});
  const r = await page.evaluate(async (call) => {
    const c = document.createElement('canvas'); c.width = c.height = 32; const x = c.getContext('2d'); x.fillStyle = '#2e8b57'; x.fillRect(0, 0, 32, 32);
    const link = document.createElement('link'); link.rel = 'icon'; link.type = 'image/png'; link.href = c.toDataURL('image/png'); document.head.appendChild(link);
    const f = window.Favico ? new Favico({ position: 'up', bgColor: '#4183c4', animation: 'none' }) : new yona.ui.Favicon({ position: 'up', bgColor: '#4183c4' });
    if (call === 'badge') f.badge('N'); else { f.badge('N'); await new Promise((r) => setTimeout(r, 400)); f.reset(); }
    await new Promise((r) => setTimeout(r, 500));
    const links = [...document.querySelectorAll('link[rel$=icon]')];
    const img = new Image(); img.src = links[links.length - 1].href; await img.decode();
    const o = document.createElement('canvas'); o.width = img.width; o.height = img.height; const ox = o.getContext('2d'); ox.drawImage(img, 0, 0); const d = ox.getImageData(0, 0, o.width, o.height).data;
    const box = () => [1e9, 1e9, -1, -1]; const blue = box(), white = box(); let base = 0;
    const near = (a, b) => Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]) < 30;
    const grow = (bb, xx, y) => { bb[0] = Math.min(bb[0], xx); bb[1] = Math.min(bb[1], y); bb[2] = Math.max(bb[2], xx); bb[3] = Math.max(bb[3], y); };
    for (let y = 0; y < o.height; y++) for (let xx = 0; xx < o.width; xx++) { const i = (y * o.width + xx) * 4; const rgb = [d[i], d[i + 1], d[i + 2]];
      if (near(rgb, [0x41, 0x83, 0xc4])) grow(blue, xx, y); else if (near(rgb, [255, 255, 255]) && d[i + 3] > 200) grow(white, xx, y); else if (near(rgb, [0x2e, 0x8b, 0x57])) base++; }
    return { count: links.length, type: links[links.length - 1].type, isData: links[links.length - 1].href.startsWith('data:image/png'), size: [o.width, o.height], blue, white, base, bottomLeft: [...d.slice((31 * 32) * 4, (31 * 32) * 4 + 4)] };
  }, drawCall);
  await page.close(); return r;
}

export const faviconTests = [
  { name: '파비콘 배지: 32x32 아이콘의 오른쪽 위에 파란 원과 흰 글자 N을 그리고 나머지 화소는 그대로 둔다(링크는 하나, data:image/png로 바뀐다)', run: async () => {
    const r = await badgeResult('badge');
    assert.equal(r.count, 1); assert.equal(r.isData, true); assert.deepEqual(r.size, [32, 32]); assert.deepEqual(r.bottomLeft, [46, 139, 87, 255]);
    const near = (a, b, t) => a.every((v, i) => Math.abs(v - b[i]) <= t);
    assert.ok(near(r.blue, [13, 0, 31, 18], 2), `파란 영역 ${r.blue}`); assert.ok(near(r.white, [17, 3, 26, 15], 3), `흰 글자 ${r.white}`); assert.ok(r.base > 600, `바뀌지 않은 화소 ${r.base}`);
  } },
  { name: '파비콘 배지: reset()하면 원래 아이콘으로 돌아간다(배지 색과 글자가 없다)', run: async () => {
    const r = await badgeResult('reset'); assert.equal(r.blue[2], -1, `파란색이 남았다 ${r.blue}`); assert.equal(r.white[2], -1); assert.equal(r.base, 1024);
  } },
];


/** 가입, 비밀번호 변경, 비밀번호 재설정 화면과 같은 규칙으로 양식을 만들고(옛 validate.js의 FormValidator 또는 새 yona.ui.FormValidator) 제출 검증 결과를 모은다. */
async function openForm() {
  const html = `<form name="signup" id="signup" action="#"><input name="loginId" id="loginId" value=""><input name="email" id="email" value=""><input name="password" id="password" type="password" value=""><input name="retypedPassword" id="retypedPassword" type="password" value=""><input name="agree" id="agree" type="checkbox"><button type="submit">ok</button></form>`;
  const page = VAL_OLD ? await pageWith({ stubs: false, html, scripts: [{ path: path.join(OLD, 'validate.js') }] }) : await esmPage({ bodyHtml: html });
  await page.evaluate(() => {
    const V = window.FormValidator || yona.ui.FormValidator; window.__calls = [];
    const rules = [{ name: 'loginId', rules: 'required|callback_check_loginId' }, { name: 'email', rules: 'required|valid_email' }, { name: 'password', rules: 'required|min_length[4]' }, { name: 'retypedPassword', rules: 'required|matches[password]' }, { name: 'agree', display: '약관', rules: 'required' }];
    window.__v = new V('signup', rules, (errors, evt) => { window.__calls.push({ errors: errors.map((e) => ({ id: e.id, name: e.name, message: e.message })), hasEvent: !!evt }); return window.__ret; });
    window.__ret = undefined;
    __v.registerCallback('check_loginId', (value) => /^[a-z0-9_-]+$/i.test(value));
    __v.setMessage('check_loginId', 'ID 문자').setMessage('required', '%s 필수').setMessage('min_length', '%s는 %s자 이상').setMessage('matches', '%s와 %s가 다름').setMessage('valid_email', '%s 이메일 오류');
  });
  return page;
}
const fill = (page, v) => page.evaluate((vals) => { for (const [k, val] of Object.entries(vals)) { const el = document.forms.signup[k]; if (el.type === 'checkbox') el.checked = !!val; else el.value = val; } }, v);
const submit = (page) => page.evaluate(() => { const f = document.forms.signup; const evt = new Event('submit', { cancelable: true, bubbles: true }); const ret = f.onsubmit(evt); return { ret, prevented: evt.defaultPrevented, calls: JSON.parse(JSON.stringify(window.__calls)) }; });

export const validatorTests = [
  { name: '양식 검사: 비었을 때 필수 오류가 필드마다 하나씩 나오고 메시지의 %s는 표시 이름(없으면 필드 이름)으로 바뀐다. 제출은 막힌다', run: async () => {
    const page = await openForm(); const r = await submit(page); const last = r.calls[r.calls.length - 1];
    assert.deepEqual(last.errors.map((e) => [e.name, e.id, e.message]), [['loginId', 'loginId', 'loginId 필수'], ['email', 'email', 'email 필수'], ['password', 'password', 'password 필수'], ['retypedPassword', 'retypedPassword', 'retypedPassword 필수'], ['agree', 'agree', '약관 필수']]);
    assert.equal(last.hasEvent, true); assert.equal(r.prevented, true); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '양식 검사: 규칙마다 먼저 실패한 것 하나만 보고하고(필수 다음 길이, 형식, 일치, 이름 지정 검사) 메시지에 두 번째 %s가 들어간다', run: async () => {
    const page = await openForm();
    await fill(page, { loginId: 'bad id!', email: 'a@', password: 'abc', retypedPassword: 'abd', agree: false }); let last = (await submit(page)).calls.pop();
    assert.deepEqual(last.errors.map((e) => [e.name, e.message]), [['loginId', 'ID 문자'], ['email', 'email 이메일 오류'], ['password', 'password는 4자 이상'], ['retypedPassword', 'retypedPassword와 password가 다름'], ['agree', '약관 필수']]);
    await fill(page, { loginId: 'good_id', email: 'a@b.co', password: 'abcd', retypedPassword: 'abcd', agree: true }); const ok = await submit(page); last = ok.calls.pop();
    assert.deepEqual(last.errors, []); assert.equal(ok.prevented, false); assert.equal(ok.ret, true); await page.close();
  } },
  { name: '양식 검사: 필수가 아닌 규칙은 값이 비었으면 건너뛴다(비어 있는 선택 입력은 오류가 없다)', run: async () => {
    const page = await openForm(); const r = await page.evaluate(() => { const f = document.forms.signup; const V = window.FormValidator || yona.ui.FormValidator; const out = []; const v = new V('signup', [{ name: 'email', rules: 'valid_email' }, { name: 'password', rules: 'min_length[8]' }], (e) => { out.push(e.map((x) => x.name)); }); f.email.value = ''; f.password.value = ''; f.onsubmit(new Event('submit', { cancelable: true })); f.email.value = 'x'; f.password.value = 'short'; f.onsubmit(new Event('submit', { cancelable: true })); return out; });
    assert.deepEqual(r, [[], ['email', 'password']]); await page.close();
  } },
  { name: '양식 검사: 콜백이 true나 false를 돌려주면 그 값이 제출 처리기의 반환값이 되고, 아니면 오류가 있을 때 기본 동작을 막는다', run: async () => {
    const page = await openForm(); await fill(page, { loginId: 'x' });
    await page.evaluate(() => { window.__ret = true; }); let r = await submit(page); assert.equal(r.ret, true); assert.equal(r.prevented, false, '콜백이 true면 오류가 있어도 막지 않는다');
    await page.evaluate(() => { window.__ret = false; }); r = await submit(page); assert.equal(r.ret, false);
    await page.evaluate(() => { window.__ret = undefined; }); r = await submit(page); assert.equal(r.prevented, true); await page.close();
  } },
  { name: '양식 검사: 오류 처리기는 제출과 별개로 직접 부를 수도 있다(비밀번호 변경 화면이 쓰는 검사 호출)', run: async () => {
    const page = await openForm(); const r = await page.evaluate(() => { const v = window.__v; const ret = (v._validateForm || v.validate).call(v); return { ret, errors: v.errors.length }; });
    assert.equal(r.errors, 5); assert.equal(typeof r.ret, 'boolean'); await page.close();
  } },
];

export const tests = [
  { name: '파일 크기 글자: $yona.formatFileSize가 humanize.filesize와 같은 글자를 낸다(0, 음수, 경계, 문자열, NaN, 아주 큰 수까지 36개)', run: async () => {
    const page = await pageWith({ stubs: false, scripts: ['common/yona.Common.js'] });
    const wrong = await page.evaluate((cases) => cases.filter(([src, want]) => { const v = new Function(`return (${src});`)(); return $yona.formatFileSize(v) !== want; }).map(([src, want]) => [src, want, $yona.formatFileSize(new Function(`return (${src});`)())]), SIZE_CASES);
    assert.deepEqual(wrong, []); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '색 읽기: $yona.getContrastColor와 $yona.getHexColor가 rgbcolor.js와 같다(이름, #rgb, #rrggbb, # 없는 16진수, rgb(), 투명, 잘못된 값 30개)', run: async () => {
    const page = await pageWith({ stubs: false, scripts: ['common/yona.Common.js'] });
    const wrong = await page.evaluate((cases) => cases.map(([input, contrast, hex]) => { const v = input === '__undef__' ? undefined : input; return [input, contrast, hex, $yona.getContrastColor(v), $yona.getHexColor(v)]; }).filter(([, c, h, gc, gh]) => c !== gc || h !== gh), COLOR_CASES);
    assert.deepEqual(wrong, []); await page.close();
  } },
  { name: '색 읽기: 알파가 있는 색(rgba, transparent)은 옛 파일처럼 읽지 못한 것으로 보고 검정 취급이다', run: async () => {
    const page = await pageWith({ stubs: false, scripts: ['common/yona.Common.js'] });
    const r = await page.evaluate(() => [$yona.getHexColor('rgba(255, 255, 0, 0.5)'), $yona.getHexColor('transparent'), $yona.getContrastColor('rgba(255, 255, 0, 0.5)'), $yona.getContrastColor('rgba(0, 0, 0, 0)')]);
    assert.deepEqual(r, [false, false, 'white', 'white']); await page.close();
  } },
  ...elevatorTests,
  ...userViewTests,
  ...faviconTests,
  ...validatorTests,
];
