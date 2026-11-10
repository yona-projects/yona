// esm/dates.js: 실제 moment 2.31.0(`moment-with-locales.min.js`)과 같은 입력으로 비교한다(동작 차이 13번).
// isValidDate는 `moment(값).isValid()`와, fromNow는 `moment(날짜).fromNow()`와 모든 입력에서 같아야 한다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, JS } from './harness.mjs';

async function open(timezoneId) {
  const page = await pageWith({ stubs: false, scripts: [{ content: 'window.Messages = function (k) { return k; };' }, 'lib/moment-with-locales.min.js', 'esm/dates.js'] });
  if (timezoneId) await page.emulateTimezone?.(timezoneId);
  await page.evaluate(() => { moment.suppressDeprecationWarnings = true; });
  return page;
}

const SAMPLES = ['2026-11-10', '2026-1-5', '2026/11/10', '2026.11.10', '20261110', '2026-02-30', '2026-13-01', '2026-11-10 10:00', '2026-11-10T10:00:00', '11/10/2026', 'Nov 10 2026', 'abc', '0', '1', '2026', '2026-11', '10-11-2026', '  2026-11-10  ', '2026-11-10x', '', ' ', '2024-02-29', '2023-02-29', '2026-04-31', '2026-00-10', '2026-11-00', '2026-11-10T24:00', '2026-11-10T24:00:01', '2026-11-10T25:00', '2026-11-10T10:60', '2026-11-10T10:00:60', '2026-11-10T10:00:00.123Z', '2026-11-10T10:00:00+09:00', '2026-11-10 10:00 Z', '2026-W45-2', '2026-W54', '2026-W53', '2020-W53-1', '2026-W00', '2026-W45-8', '2026-W45-0', '2026-100', '2026-366', '2024-366', '2026-000', '2026-13', '2026-00', '+002026-11-10', '-000001-01-01', '+275760-09-13', '+275761-01-01', '202611', '2026111', '2026W451', '2026W45', 'Mon, 09 Nov 2026 10:00:00 GMT', 'Tue, 09 Nov 2026 10:00:00 GMT', '31 Feb 2026 10:00 +0900', 'Wed Nov 11 2026', 'tomorrow', 'null', 'undefined', '99999999', '12345', '1234567890123'];

function* generated() {
  // 날짜 조각을 섞어 ISO 모양과 비슷한 입력을 많이 만든다(고정 순서로 결정적)
  const years = ['2026', '2024', '2023', '1999', '0000', '9999', '+002026', '-000001'];
  const months = ['01', '02', '06', '11', '12', '00', '13', '1', '2'];
  const days = ['01', '28', '29', '30', '31', '32', '00', '5'];
  const times = ['', 'T00:00', 'T10:30:15', 'T24:00', 'T24:00:00.000', 'T23:59:59.999', 'T10:30:15,5', 'T1030', 'T103015', 'T10', ' 08:00', 'T10:30+09:00', 'T10:30Z', 'T10:30 Z', 'T25:00', 'T10:61'];
  for (const y of years) for (const m of months) for (const d of days) for (const t of times) yield `${y}-${m}-${d}${t}`;
  for (const y of ['2026', '2024', '2000']) for (const m of months.slice(0, 7)) for (const d of days.slice(0, 5)) yield `${y}${m}${d}`;
  for (const y of ['2026', '2024', '2020', '2015']) for (let w = 0; w <= 54; w += 1) { yield `${y}-W${String(w).padStart(2, '0')}`; for (const e of [0, 1, 7, 8]) yield `${y}-W${String(w).padStart(2, '0')}-${e}`; }
  for (const y of ['2026', '2024']) for (const o of [0, 1, 59, 60, 365, 366, 367, 999]) yield `${y}-${String(o).padStart(3, '0')}`;
}

export const tests = [
  { name: 'isValidDate: 대표 입력 60여 개에서 moment(값).isValid()와 같다', run: async () => {
    const page = await open();
    const diff = await page.evaluate((samples) => samples.filter((s) => moment(s).isValid() !== yona.dates.isValidDate(s)).map((s) => [s, moment(s).isValid(), yona.dates.isValidDate(s)]), SAMPLES);
    assert.deepEqual(diff, []); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: 'isValidDate: 날짜 조각을 섞은 입력 수만 개에서 moment(값).isValid()와 같다', run: async () => {
    const page = await open(); const all = [...generated()];
    const diff = await page.evaluate((samples) => { const bad = []; for (const s of samples) { const a = moment(s).isValid(); const b = yona.dates.isValidDate(s); if (a !== b && bad.length < 40) bad.push([s, a, b]); } return bad; }, all);
    assert.ok(all.length > 10000, `입력이 적다: ${all.length}`);
    assert.deepEqual(diff, [], `다른 입력 예: ${JSON.stringify(diff.slice(0, 10))}`); await page.close();
  } },
];

// ---- fromNow ----
import { launch, classicFromEsm } from './harness.mjs';

/** 시간대를 지정한 페이지에 moment와 dates.js를 올린다. */
async function openZone(timezoneId) {
  const browser = await launch(); const ctx = await browser.newContext({ timezoneId }); const page = await ctx.newPage(); page.errors = [];
  page.on('pageerror', (e) => page.errors.push(String(e.message || e)));
  await page.setContent('<!doctype html><body></body>');
  await page.addScriptTag({ path: path.join(JS, 'lib/moment-with-locales.min.js') });
  await page.addScriptTag({ content: classicFromEsm('esm/dates.js') });
  await page.evaluate(() => { moment.suppressDeprecationWarnings = true; });
  return page;
}

const SECOND = 1000, MINUTE = 60 * SECOND, HOUR = 60 * MINUTE, DAY = 24 * HOUR;
/** 임계값 경계 근처의 차이(밀리초). 양수와 음수를 모두 쓴다. */
const EDGE_OFFSETS = [0, 1, 499, 500, 501, 999, 1000, 30 * SECOND, 44 * SECOND, 44.4 * SECOND, 44.5 * SECOND, 44.6 * SECOND, 45 * SECOND, 59 * SECOND, 89 * SECOND, 89.5 * SECOND, 90 * SECOND, 91 * SECOND,
  2 * MINUTE, 10 * MINUTE, 44 * MINUTE, 44.5 * MINUTE, 44.6 * MINUTE, 45 * MINUTE, 59 * MINUTE, 89 * MINUTE, 89.5 * MINUTE, 90 * MINUTE, 91 * MINUTE, 3 * HOUR, 21 * HOUR, 21.5 * HOUR, 21.6 * HOUR, 22 * HOUR, 23 * HOUR, 35 * HOUR, 35.5 * HOUR, 36 * HOUR, 37 * HOUR,
  2 * DAY, 10 * DAY, 25 * DAY, 25.5 * DAY, 25.6 * DAY, 26 * DAY, 27 * DAY, 29 * DAY, 30 * DAY, 31 * DAY, 45 * DAY, 46 * DAY, 60 * DAY, 100 * DAY, 300 * DAY, 319 * DAY, 320 * DAY, 335 * DAY, 345 * DAY, 350 * DAY, 364 * DAY, 365 * DAY, 366 * DAY, 400 * DAY, 540 * DAY, 548 * DAY, 549 * DAY, 730 * DAY, 1000 * DAY, 3650 * DAY];
const NOWS = ['2026-11-10T12:00:00+09:00', '2024-02-29T12:00:00Z', '2026-01-31T00:00:00Z', '2026-03-29T00:30:00Z', '2026-10-25T00:30:00Z', '2026-03-08T06:30:00Z', '2026-11-01T05:30:00Z', '2025-12-31T23:59:59Z', '2026-05-31T10:00:00Z', '2023-08-31T22:00:00Z'].map((s) => Date.parse(s));
const LANGS = ['en', 'ko', 'ja', 'ru', 'uz', 'fr'];

function randomOffsets(count) {
  let seed = 12345; const next = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
  return Array.from({ length: count }, () => Math.round(Math.pow(10, 3 + next() * 9.2) * (next() < 0.5 ? -1 : 1)));
}

for (const zone of ['Asia/Seoul', 'UTC', 'America/New_York', 'Europe/Moscow']) {
  tests.push({ name: `fromNow: ${zone}에서 5개 언어(+미지원 fr)로 경계값과 무작위 차이 수천 개가 moment(날짜).from(now)와 같다`, run: async () => {
    const page = await openZone(zone);
    const offsets = [...EDGE_OFFSETS.flatMap((o) => [o, -o]), ...randomOffsets(1500)];
    const diff = await page.evaluate(({ nows, offsets, langs }) => {
      const bad = []; let n = 0;
      for (const now of nows) for (const off of offsets) for (const lang of langs) {
        const date = now + off; const expected = moment(date).locale(lang === 'fr' ? 'en' : lang).from(moment(now)); const actual = yona.dates.fromNow(date, lang, now); n += 1;
        if (expected !== actual && bad.length < 30) bad.push([new Date(now).toISOString(), off, lang, expected, actual]);
      }
      return { bad, n };
    }, { nows: NOWS, offsets, langs: LANGS });
    assert.ok(diff.n > 90000, `비교 수가 적다: ${diff.n}`);
    assert.deepEqual(diff.bad, [], `다른 예 ${JSON.stringify(diff.bad.slice(0, 5))}`); assert.deepEqual(page.errors, []); await page.close();
  } });
}
