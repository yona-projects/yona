// 달력 위젯 `common/yona.ui.Calendar.js`의 계약을 시험한다(S-09, 라이브러리는 Pikaday에서 vanilla-calendar-pro 3.4.0으로 바꾸었다, A-424).
// 계약은 v1.6의 Pikaday와 같다: 입력란의 왼쪽 아래에 틈 없이 붙고 넘치면 뒤집힌다, 값 형식은 YYYY-MM-DD, 고르거나 setDate 하면 입력란에 change 가 나간다,
// `data-*` 속성이 옵션이다(`data-silent`), 값이 있고 silent 가 아니면 처음에 setDate 를 한 번 부른다, `.btn-calendar`가 달력을 연다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, JS, ROOT } from './harness.mjs';

const LIB = 'lib/vanilla-calendar-pro/index.js';
// 라이브러리 CSS와 Yona의 보정(`_page.scss`)이 함께 있어야 위치가 Pikaday와 같다. 보정은 `./gradlew compileSass`가 만드는 build/generated/sass에 있다.
const CSS = path.join(JS, 'lib/vanilla-calendar-pro/index.css');
const YOBI = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets/yobi.css');

/** 입력란을 [pos](절대 위치)에 두고 위젯을 불러온다. 문서 전체의 change 횟수는 `window.__changes`에 센다. */
async function open({ value = '2026-03-15', attrs = 'data-toggle="calendar"', pos = 'left:40px;top:120px', extra = '', language = null } = {}) {
  const page = await pageWith({
    html: `<style>body{margin:0;height:1400px}</style>${language ? `<script>window.yona = { language: '${language}' };</script>` : ''}<script>window.__changes=0;document.addEventListener('change',function(e){if(e.target.id==='due')window.__changes++;});</script>
      <input id="due" type="text" ${attrs} value="${value}" autocomplete="off" style="position:absolute;${pos};width:230px"><button id="cal" class="btn-calendar" type="button" style="position:absolute;left:300px;top:120px">달력</button>
      <h4 id="outside" style="position:absolute;left:400px;top:20px;margin:0">바깥</h4>${extra}`,
    scripts: [LIB, 'common/yona.ui.Calendar.js'],
  });
  await page.setViewportSize({ width: 700, height: 700 });
  await page.addStyleTag({ path: CSS });
  await page.addStyleTag({ path: YOBI });
  await page.waitForFunction(() => document.getElementById('due') && ($('#due').data('calendar') || false), null, { timeout: 5000 }).catch(() => {});
  return page;
}
const isOpen = (page) => page.evaluate(() => { const p = document.querySelector('[data-vc="calendar"][data-vc-input]'); return !!p && !p.hasAttribute('data-vc-calendar-hidden'); });
const geo = (page) => page.evaluate(() => { const q = document.querySelector('[data-vc="calendar"][data-vc-input]').getBoundingClientRect(), i = document.getElementById('due').getBoundingClientRect();
  return { dx: Math.round(q.left - i.left), dy: Math.round(q.top - i.bottom), right: Math.round(q.right - i.right), aboveGap: Math.round(i.top - q.bottom) }; });
const day = (page, n) => page.locator(`[data-vc="calendar"][data-vc-input] [data-vc-date-month="current"] button`).filter({ hasText: new RegExp(`^${n}$`) });
const selected = (page) => page.evaluate(() => { const b = document.querySelector('[data-vc="calendar"][data-vc-input] [data-vc-date-selected]'); return b && b.textContent.trim(); });
const value = (page) => page.evaluate(() => document.getElementById('due').value);


/** 항상 보이는 달력(마일스톤, API 토큰)을 연다. */
async function openInline({ value = '2026-03-15', options = '{}' } = {}) {
  const page = await pageWith({ html: `<style>body{margin:0}</style><input id="d" type="text" value="${value}"><div id="box"></div>`, scripts: [LIB, 'common/yona.ui.Calendar.js'] });
  await page.setViewportSize({ width: 700, height: 700 });
  await page.addStyleTag({ path: CSS }); await page.addStyleTag({ path: YOBI });
  await page.evaluate((o) => { window.__changes = 0; document.getElementById('d').addEventListener('change', () => { window.__changes++; }); const opts = new Function('return ' + o)(); opts.input = '#d'; window.cal = yona.ui.Calendar.inline('#box', opts); }, options);
  return page;
}
const inlineDay = (page, n) => page.locator('#box [data-vc-date-month="current"] button').filter({ hasText: new RegExp(`^${n}$`) });
const inlineSelected = (page) => page.evaluate(() => { const b = document.querySelector('#box [data-vc-date-selected]'); return b && b.textContent.trim(); });

export const tests = [
  { name: '달력: 입력란을 누르면 열리고 입력란의 날짜가 선택일이며 월 이름과 요일은 v1.6처럼 영어이고 일요일이 먼저다', run: async () => {
    const page = await open(); await page.click('#due'); await page.waitForTimeout(200);
    assert.equal(await isOpen(page), true); assert.equal(await selected(page), '15');
    const text = await page.evaluate(() => document.querySelector('[data-vc="calendar"][data-vc-input]').textContent.replace(/\s+/g, ' '));
    assert.ok(/March/.test(text) && /2026/.test(text), text);
    const first = await page.evaluate(() => document.querySelector('[data-vc="calendar"][data-vc-input] [data-vc-week-day]').textContent.trim());
    assert.equal(first, 'Sun', '첫 요일'); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '달력: 입력란에 초점이 가도 열린다', run: async () => {
    const page = await open(); await page.evaluate(() => document.getElementById('due').focus()); await page.waitForTimeout(200);
    assert.equal(await isOpen(page), true); await page.close();
  } },
  { name: '달력: 위치는 입력란의 왼쪽 아래에 틈 없이 붙는다(Pikaday와 같다)', run: async () => {
    const page = await open(); await page.click('#due'); await page.waitForTimeout(200);
    const g = await geo(page); assert.deepEqual([g.dx, g.dy], [0, 0], JSON.stringify(g)); await page.close();
  } },
  { name: '달력: 오른쪽이 화면을 넘치면 입력란의 오른쪽에 맞춘다', run: async () => {
    const page = await open({ pos: 'left:460px;top:120px' }); await page.click('#due'); await page.waitForTimeout(200);
    const g = await geo(page); assert.equal(g.right, 0, JSON.stringify(g)); assert.equal(g.dy, 0, JSON.stringify(g)); await page.close();
  } },
  { name: '달력: 아래가 화면을 넘치면 입력란 위로 뒤집히며 틈이 없다', run: async () => {
    const page = await open({ pos: 'left:40px;top:620px' }); await page.click('#due'); await page.waitForTimeout(200);
    const g = await geo(page); assert.ok(g.aboveGap >= 0 && g.aboveGap <= 1, JSON.stringify(g)); await page.close();
  } },
  { name: '달력: 날짜를 고르면 YYYY-MM-DD가 입력란에 채워지고 달력이 닫히며 change 가 한 번 나간다', run: async () => {
    const page = await open(); await page.evaluate(() => { window.__changes = 0; }); await page.click('#due'); await page.waitForTimeout(200);
    await day(page, 20).click(); await page.waitForTimeout(250);
    assert.equal(await value(page), '2026-03-20'); assert.equal(await isOpen(page), false); assert.equal(await page.evaluate(() => window.__changes), 1);
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '달력: 바깥을 누르면 닫힌다', run: async () => {
    const page = await open(); await page.click('#due'); await page.waitForTimeout(200); await page.click('#outside'); await page.waitForTimeout(250);
    assert.equal(await isOpen(page), false); await page.close();
  } },
  { name: '달력: .btn-calendar 단추가 달력을 연다', run: async () => {
    const page = await open(); await page.click('#cal'); await page.waitForTimeout(250); assert.equal(await isOpen(page), true); await page.close();
  } },
  { name: '달력: setDate(문자열)은 입력란을 고치고 change 를 한 번 내며 getDate 는 같은 날의 Date 를 준다', run: async () => {
    const page = await open(); await page.evaluate(() => { window.__changes = 0; });
    const r = await page.evaluate(() => { const c = yona.ui.Calendar(document.getElementById('due'), { silent: true }); c.setDate('2026-12-24'); const d = c.getDate();
      return { v: document.getElementById('due').value, n: window.__changes, isDate: d instanceof Date, ymd: d && [d.getFullYear(), d.getMonth() + 1, d.getDate()].join('-') }; });
    assert.deepEqual(r, { v: '2026-12-24', n: 1, isDate: true, ymd: '2026-12-24' }); await page.close();
  } },
  { name: '달력: setDate 에 날짜로 읽을 수 없는 글자를 주면 아무것도 바뀌지 않는다', run: async () => {
    const page = await open(); await page.evaluate(() => { window.__changes = 0; });
    const r = await page.evaluate(() => { const c = yona.ui.Calendar(document.getElementById('due'), { silent: true }); c.setDate('abc'); c.setDate('2026-02-31'); return { v: document.getElementById('due').value, n: window.__changes }; });
    assert.deepEqual(r, { v: '2026-03-15', n: 0 }); await page.close();
  } },
  { name: '달력: setDate("")는 v1.6의 Pikaday처럼 선택만 풀고 입력란 값은 그대로 둔다(v1.6 동작 유지)', run: async () => {
    const page = await open(); await page.evaluate(() => { window.__changes = 0; });
    const r = await page.evaluate(() => { const c = yona.ui.Calendar(document.getElementById('due'), { silent: true }); c.setDate(''); return { v: document.getElementById('due').value, n: window.__changes, d: c.getDate() }; });
    assert.deepEqual(r, { v: '2026-03-15', n: 0, d: null }); await page.close();
  } },
  { name: '달력: 입력란에 직접 쓰고 change 가 나면 달력이 그 날로 가고 값은 YYYY-MM-DD로 바로잡히며 change 는 새로 나가지 않는다(R-146 (2): 원래 change 하나가 바로잡힌 값을 들고 간다)', run: async () => {
    const page = await open(); await page.evaluate(() => { window.__changes = 0; window.__seen = []; document.addEventListener('change', (e) => { if (e.target.id === 'due') window.__seen.push(e.target.value); }); });
    await page.evaluate(() => { const i = document.getElementById('due'); i.value = '2027-2-3'; i.dispatchEvent(new Event('change', { bubbles: true })); });
    assert.equal(await value(page), '2027-02-03'); assert.equal(await page.evaluate(() => window.__changes), 1, '쓴 change 하나뿐');
    assert.deepEqual(await page.evaluate(() => window.__seen), ['2027-02-03'], '뒤의 처리기는 바로잡힌 값을 본다');
    await page.click('#cal'); await page.waitForTimeout(250); assert.equal(await selected(page), '3'); await page.close();
  } },
  { name: '달력: 이미 정규 형식인 값을 직접 써도 change 는 한 번이다(마감일 저장이 두 번 나가던 것)', run: async () => {
    const page = await open(); await page.evaluate(() => { window.__changes = 0; });
    await page.evaluate(() => { const i = document.getElementById('due'); i.value = '2027-03-09'; i.dispatchEvent(new Event('change', { bubbles: true })); });
    assert.equal(await page.evaluate(() => window.__changes), 1); await page.close();
  } },
  { name: '달력: 날짜가 아닌 글자를 쓰고 change 가 나면 값은 그대로 두고 달력도 바뀌지 않는다', run: async () => {
    const page = await open(); await page.evaluate(() => { window.__changes = 0; });
    await page.evaluate(() => { const i = document.getElementById('due'); i.value = '내일'; i.dispatchEvent(new Event('change', { bubbles: true })); });
    assert.equal(await value(page), '내일'); assert.equal(await page.evaluate(() => window.__changes), 1); await page.close();
  } },
  { name: '달력: 처음 초기화할 때는 change 를 내지 않고(R-146 (3), 값이 있어도 silent 여부와 무관), 입력란 값은 YYYY-MM-DD로 맞추며 선택일이 된다', run: async () => {
    for (const attrs of ['data-toggle="calendar"', 'data-toggle="calendar" data-silent="true"']) {
      const a = await open({ attrs, value: '2026-3-15' }); assert.equal(await a.evaluate(() => window.__changes), 0, attrs); assert.equal(await value(a), '2026-03-15', '값은 조용히 바로잡는다');
      await a.click('#cal'); await a.waitForTimeout(250); assert.equal(await selected(a), '15'); await a.close();
    }
    const c = await open({ value: '' }); assert.equal(await c.evaluate(() => window.__changes), 0); assert.equal(await value(c), ''); await c.close();
  } },
  { name: '달력: 같은 입력란을 다시 초기화해도 달력은 새로 만들어지지 않는다', run: async () => {
    const page = await open();
    const r = await page.evaluate(() => { const before = $('#due').data('calendar'); yona.ui.Calendar(document.getElementById('due'), { silent: true }); $('#due').data('calendar').show(); return before === $('#due').data('calendar') && document.querySelectorAll('[data-vc="calendar"][data-vc-input]').length === 1; });
    assert.equal(r, true); await page.close();
  } },
  { name: '달력: 월을 넘기면 그 달의 날짜를 고를 수 있다', run: async () => {
    const page = await open(); await page.click('#due'); await page.waitForTimeout(200);
    await page.locator('[data-vc="calendar"][data-vc-input] [data-vc-arrow="next"]').click(); await page.waitForTimeout(150);
    await day(page, 5).click(); await page.waitForTimeout(250); assert.equal(await value(page), '2026-04-05'); await page.close();
  } },
  { name: '항상 보이는 달력: 처음부터 보이고 입력란의 날짜가 선택되어 있다', run: async () => {
    const page = await openInline(); assert.equal(await inlineSelected(page), '15');
    const box = await page.evaluate(() => document.getElementById('box').getBoundingClientRect().height); assert.ok(box > 100, '달력이 그려져 있다');
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '항상 보이는 달력: 날짜를 고르면 입력란에 YYYY-MM-DD를 채우고 change 는 내지 않는다(Pikaday의 onSelect와 같다)', run: async () => {
    const page = await openInline(); await inlineDay(page, 22).click(); await page.waitForTimeout(150);
    assert.equal(await page.evaluate(() => document.getElementById('d').value), '2026-03-22'); assert.equal(await page.evaluate(() => window.__changes), 0); assert.equal(await inlineSelected(page), '22');
    await inlineDay(page, 22).click(); await page.waitForTimeout(100); assert.equal(await inlineSelected(page), '22', '같은 날을 다시 눌러도 선택이 풀리지 않는다'); await page.close();
  } },
  { name: '항상 보이는 달력: 입력란에 쓰고 blur 하면 달력이 그 달로 가고 값이 YYYY-MM-DD로 바로잡힌다', run: async () => {
    const page = await openInline(); await page.fill('#d', '2027-1-8'); await page.locator('#d').blur(); await page.waitForTimeout(150);
    assert.equal(await page.evaluate(() => document.getElementById('d').value), '2027-01-08'); assert.equal(await inlineSelected(page), '8');
    const title = await page.evaluate(() => document.getElementById('box').textContent.replace(/\s+/g, ' ')); assert.ok(/January/.test(title) && /2027/.test(title), title); await page.close();
  } },
  { name: '항상 보이는 달력: 날짜가 아닌 글자를 쓰면 입력란도 달력도 그대로이고, 빈 값이면 선택만 풀린다', run: async () => {
    const page = await openInline(); await page.fill('#d', 'abc'); await page.locator('#d').blur(); await page.waitForTimeout(100);
    assert.equal(await page.evaluate(() => document.getElementById('d').value), 'abc'); assert.equal(await inlineSelected(page), '15');
    await page.fill('#d', ''); await page.locator('#d').blur(); await page.waitForTimeout(100); assert.equal(await inlineSelected(page), null); await page.close();
  } },
  { name: '항상 보이는 달력: minDate 이전 날짜는 고를 수 없고 쓴 값은 minDate로 맞춘다(API 토큰은 내일 이후만)', run: async () => {
    const page = await openInline({ value: '', options: `{ minDate: new Date(new Date().getFullYear(), new Date().getMonth(), new Date().getDate() + 1) }` }); await page.waitForTimeout(100);
    const today = await page.evaluate(() => new Date().getDate());
    await inlineDay(page, today).click({ force: true }).catch(() => {}); await page.waitForTimeout(100);
    assert.equal(await page.evaluate(() => document.getElementById('d').value), '', '오늘은 막혀 있어 값을 바꾸지 않는다');
    const tomorrow = await page.evaluate(() => { const d = new Date(); d.setDate(d.getDate() + 1); return [d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'), String(d.getDate()).padStart(2, '0')].join('-'); });
    await page.fill('#d', '2020-01-01'); await page.locator('#d').blur(); await page.waitForTimeout(100);
    assert.equal(await page.evaluate(() => document.getElementById('d').value), tomorrow, 'Pikaday처럼 minDate로 맞춘다'); await page.close();
  } },
  { name: '항상 보이는 달력: 같은 화면에 둘을 두어도 서로 영향을 주지 않는다', run: async () => {
    const page = await pageWith({ html: '<input id="a" value="2026-03-01"><div id="ba"></div><input id="b" value="2026-05-20"><div id="bb"></div>', scripts: [LIB, 'common/yona.ui.Calendar.js'] });
    await page.addStyleTag({ path: CSS });
    await page.evaluate(() => { yona.ui.Calendar.inline('#ba', { input: '#a' }); yona.ui.Calendar.inline('#bb', { input: '#b' }); });
    const r = await page.evaluate(() => [document.querySelector('#ba [data-vc-date-selected]').textContent.trim(), document.querySelector('#bb [data-vc-date-selected]').textContent.trim()]);
    assert.deepEqual(r, ['1', '20']); await page.close();
  } },
  { name: '달력: 월 이름을 누르면 12개월 중에서, 연도를 누르면 연도 목록에서 자유롭게 고를 수 있고 목록은 화살표로 넘긴다(Pikaday의 월과 연 선택 상자를 대신한다)', run: async () => {
    const page = await open(); await page.click('#due'); await page.waitForTimeout(200);
    const P = '[data-vc="calendar"][data-vc-input]';
    await page.click(P + ' [data-vc="month"]'); await page.waitForTimeout(150);
    assert.equal(await page.locator(P + ' [data-vc-months-month]').count(), 12);
    await page.locator(P + ' [data-vc-months-month]').filter({ hasText: 'Aug' }).first().click(); await page.waitForTimeout(150);
    await page.click(P + ' [data-vc="year"]'); await page.waitForTimeout(150);
    const years = await page.locator(P + ' [data-vc-years-year]').allTextContents(); assert.ok(years.includes('2026') && years.length >= 12, years.join());
    await page.locator(P + ' [data-vc-arrow="next"]').first().click(); await page.waitForTimeout(150);
    const next = await page.locator(P + ' [data-vc-years-year]').allTextContents(); assert.ok(Number(next[0]) > Number(years[years.length - 1]) - 1, `다음 쪽 ${next.join()}`);
    await page.locator(P + ' [data-vc-years-year]').filter({ hasText: new RegExp(`^${next[next.length - 1]}$`) }).click(); await page.waitForTimeout(150);
    await day(page, 10).click(); await page.waitForTimeout(200);
    assert.equal(await value(page), `${next[next.length - 1]}-08-10`); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '달력: 언어는 Yona의 언어 설정(yona.language)을 따른다. 한국어와 일본어로 월 이름과 요일이 바뀌고 일요일 시작은 그대로이다', run: async () => {
    const text = (page) => page.evaluate(() => document.querySelector('[data-vc="calendar"][data-vc-input]').textContent.replace(/\s+/g, ' '));
    const first = (page) => page.evaluate(() => document.querySelector('[data-vc="calendar"][data-vc-input] [data-vc-week-day]').textContent.trim());
    const ko = await open({ language: 'ko' }); await ko.click('#due'); await ko.waitForTimeout(200);
    assert.ok(/2026/.test(await text(ko)) && /3월/.test(await text(ko)), await text(ko)); assert.equal(await first(ko), '일'); await ko.close();
    const ja = await open({ language: 'ja' }); await ja.click('#due'); await ja.waitForTimeout(200);
    assert.ok(/3月/.test(await text(ja)), await text(ja)); assert.equal(await first(ja), '日'); await ja.close();
    const en = await open({ language: 'en' }); await en.click('#due'); await en.waitForTimeout(200); assert.ok(/March/.test(await text(en))); assert.equal(await first(en), 'Sun'); await en.close();
  } },
  { name: '달력: 언어 설정이 없거나 알 수 없는 값이면 영어로 연다(오류 없이)', run: async () => {
    for (const language of [null, 'xx-invalid-tag-!!']) {
      const page = await open({ language }); await page.click('#due'); await page.waitForTimeout(200);
      assert.ok(/March/.test(await page.evaluate(() => document.querySelector('[data-vc="calendar"][data-vc-input]').textContent)), String(language)); assert.deepEqual(page.errors, []); await page.close();
    }
  } },
  { name: '항상 보이는 달력도 언어 설정을 따른다', run: async () => {
    const page = await pageWith({ html: `<script>window.yona = { language: 'ko' };</script><input id="d" value="2026-03-15"><div id="box"></div>`, scripts: [LIB, 'common/yona.ui.Calendar.js'] });
    await page.evaluate(() => { yona.ui.Calendar.inline('#box', { input: '#d' }); });
    assert.ok(/3월/.test(await page.evaluate(() => document.getElementById('box').textContent)), '한국어'); await page.close();
  } },
  { name: '달력: 한 주의 시작 요일은 언어의 관습을 따른다(ko, ja, en은 일요일, ru와 uz는 월요일)', run: async () => {
    // 기대값은 브라우저의 Intl로 월요일(2024-01-01)과 일요일(2023-12-31)의 짧은 이름을 구해 비교한다.
    const name = (page, lang, y, m, d) => page.evaluate(([l, a, b, c]) => new Date(a, b, c).toLocaleDateString(l, { weekday: 'short' }), [lang, y, m, d]);
    const first = (page) => page.evaluate(() => document.querySelector('[data-vc="calendar"][data-vc-input] [data-vc-week-day]').textContent.trim());
    const grid = (page) => page.evaluate(() => { const c = document.querySelector('[data-vc="calendar"][data-vc-input] [data-vc-date-month="current"]'); return c.closest('[data-vc-date]').getAttribute('data-vc-date'); });
    for (const [lang, monday] of [['ko', false], ['ja', false], ['en', false], ['ru', true], ['uz', true]]) {
      const page = await open({ language: lang }); await page.click('#due'); await page.waitForTimeout(200);
      const expected = monday ? await name(page, lang, 2024, 0, 1) : await name(page, lang, 2023, 11, 31);
      assert.equal((await first(page)).toLowerCase(), expected.toLowerCase(), `${lang}: 첫 요일`);
      // 2026-03-01은 일요일이다. 월요일 시작이면 첫 줄 첫 칸은 2월 23일, 일요일 시작이면 3월 1일이다(이전 달 날짜는 흐리게 보인다).
      const firstCell = await page.evaluate(() => document.querySelector('[data-vc="calendar"][data-vc-input] [data-vc-date]').getAttribute('data-vc-date'));
      assert.equal(firstCell, monday ? '2026-02-23' : '2026-03-01', `${lang}: 첫 칸`); await page.close();
    }
  } },
];
