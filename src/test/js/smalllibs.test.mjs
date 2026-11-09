// 작은 제3자 파일(humanize.js, rgbcolor.js, parseuri.js, jquery.elevator.js)을 표준 API와 직접 쓴 작은 구현으로 바꾼 것(D-122 2단계)이 옛 파일과 같은 결과를 내는지 확인한다.
// 기대값은 옛 파일을 실행해 얻은 표이다(파일 크기 글자, 색 읽기, 쿼리 문자열, 엘리베이터의 요소와 동작). 엘리베이터는 ELEV_IMPL=old로 옛 플러그인에서도 돌려 볼 수 있다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, JS, launch } from './harness.mjs';

const OLD = process.env.CLAUDE_JOB_DIR ? path.join(process.env.CLAUDE_JOB_DIR, 'tmp/old-libs') : null;
const ELEV_OLD = process.env.ELEV_IMPL === 'old';
const UV_OLD = process.env.UV_IMPL === 'old';

const SIZE_CASES = [["0", "0 bytes"], ["1", "1 bytes"], ["2", "2 bytes"], ["512", "512 bytes"], ["1000", "1,000 bytes"], ["1023", "1,023 bytes"], ["1024", "1.00 Kb"], ["1025", "1.00 Kb"], ["1536", "1.50 Kb"], ["2048", "2.00 Kb"], ["10240", "10.00 Kb"], ["1048575", "1,024.00 Kb"], ["1048576", "1.00 Mb"], ["1572864", "1.50 Mb"], ["5242880", "5.00 Mb"], ["10485760", "10.00 Mb"], ["123456789", "117.74 Mb"], ["1073741824", "1.00 Gb"], ["-1", "0 bytes"], ["5368709120", "5.00 Gb"], ["1099511627776", "1.00 Tb"], ["1125899906842624", "1.00 Pb"], ["1152921504606847000", "1,024.00 Pb"], ["\"2048\"", "2.00 Kb"], ["\"5242880\"", "5.00 Mb"], ["\"\"", "0 bytes"], ["\" \"", "0 bytes"], ["\"abc\"", "0.00 Pb"], ["NaN", "0.00 Pb"], ["undefined", "0.00 Pb"], ["null", "0 bytes"], ["1.5", "2 bytes"], ["1023.9", "1,024 bytes"], ["0.4", "0 bytes"], ["999.5", "1,000 bytes"], ["1e+21", "888,178.42 Pb"]];
const COLOR_CASES = [["yellow", "dimgray", "#ffff00"], ["red", "white", "#ff0000"], ["white", "dimgray", "#ffffff"], ["black", "white", "#000000"], ["dimgray", "white", "#696969"], ["#fff", "dimgray", "#ffffff"], ["#FFF", "dimgray", "#ffffff"], ["#ffff00", "dimgray", "#ffff00"], ["#FFFF00", "dimgray", "#ffff00"], ["ffff00", "dimgray", "#ffff00"], ["#abc", "white", "#aabbcc"], ["rgb(255,255,0)", "dimgray", "#ffff00"], ["rgb(255, 255, 0)", "dimgray", "#ffff00"], ["rgb(0, 0, 0)", "white", "#000000"], ["rgba(0, 0, 0, 0)", "white", false], ["rgba(255, 255, 0, 0.5)", "white", false], ["", "white", false], ["notacolor", "white", false], ["#12", "white", false], ["#12345", "white", false], ["transparent", "white", false], ["lightgoldenrodyellow", "dimgray", "#fafad2"], ["#2196f3", "white", "#2196f3"], ["#4caf50", "white", "#4caf50"], ["#f44336", "white", "#f44336"], [null, "white", false], ["__undef__", "white", false], ["  Red ", "white", "#ff0000"], ["rgb(300,0,0)", "white", "#ff0000"], ["rgb(1,2,3)", "white", "#010203"]];


/** 엘리베이터(이슈와 게시글 보기의 위, 아래 이동 단추)를 높이 6000px 문서에서 연다. 옛 플러그인(ELEV_IMPL=old)과 새 구현 모두 같은 DOM과 동작을 낸다. */
async function openElevator(options = "{ shape: 'rounded', tooltips: true }") {
  const scripts = ELEV_OLD ? [{ path: path.join(OLD, 'elevator/jquery.elevator.js') }] : ['common/yona.Common.js', 'common/yona.ui.Elevator.js'];
  const page = await pageWith({ stubs: false, scripts, html: '<div style="height:6000px">문서</div>' });
  await page.addStyleTag({ path: ELEV_OLD ? path.join(OLD, 'elevator/jquery.elevator.css') : path.join(JS, 'lib/elevator/jquery.elevator.css') });
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
];
