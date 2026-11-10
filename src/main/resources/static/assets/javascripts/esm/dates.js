/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * `moment`가 하던 두 가지, 날짜 글자가 올바른지 검사하는 일(`moment(값).isValid()`)과 "3일 전" 같은 상대 시각(`moment(날짜).fromNow()`)을
 * 라이브러리 없이 같은 결과로 계산한다(표준 기능 리팩터링 5단계, D-123, 동작 차이 13번).
 *
 * 규칙은 이 프로젝트가 쓰던 moment 2.31.0 소스에서 그대로 옮겼고, `dates.test.mjs`가 같은 입력을 실제 moment와 대량으로 비교해 결과가 같음을 확인한다.
 * 상대 시각 문구는 앱이 지원하는 언어(en, ko, ja, ru, uz)만 담았고, 그 밖의 언어는 moment처럼 영어로 처리한다.
 */
import { register } from "./namespace.js";

// ---------------------------------------------------------------------------------------------------------------------
// 날짜 글자 검사
// ---------------------------------------------------------------------------------------------------------------------

/** moment의 ISO 8601 확장형(`2026-11-10`, `2026-11-10T10:00`)과 기본형(`20261110`) 정규식. */
const ISO_EXTENDED = /^\s*((?:[+-]\d{6}|\d{4})-(?:\d\d-\d\d|W\d\d-\d|W\d\d|\d\d\d|\d\d))(?:(T| )(\d\d(?::\d\d(?::\d\d(?:[.,]\d+)?)?)?)([+-]\d\d(?::?\d\d)?|\s*Z)?)?$/;
const ISO_BASIC = /^\s*((?:[+-]\d{6}|\d{4})(?:\d\d\d\d|W\d\d\d|W\d\d|\d\d\d|\d\d|))(?:(T| )(\d\d(?:\d\d(?:\d\d(?:[.,]\d+)?)?)?)([+-]\d\d(?::?\d\d)?|\s*Z)?)?$/;

/** 날짜 부분의 모양. moment가 앞에서부터 처음 맞는 것을 쓴다(정규식은 일부러 앞뒤를 고정하지 않는다). */
const ISO_DATES = [
  ["YYYYYY-MM-DD", /[+-]\d{6}-\d\d-\d\d/], ["YYYY-MM-DD", /\d{4}-\d\d-\d\d/], ["GGGG-[W]WW-E", /\d{4}-W\d\d-\d/],
  ["GGGG-[W]WW", /\d{4}-W\d\d/], ["YYYY-DDD", /\d{4}-\d{3}/], ["YYYY-MM", /\d{4}-\d\d/],
  ["YYYYYYMMDD", /[+-]\d{10}/], ["YYYYMMDD", /\d{8}/], ["GGGG[W]WWE", /\d{4}W\d{3}/],
  ["GGGG[W]WW", /\d{4}W\d{2}/], ["YYYYDDD", /\d{7}/], ["YYYYMM", /\d{6}/], ["YYYY", /\d{4}/],
];

/** 시간 부분의 모양. */
const ISO_TIMES = [
  /\d\d:\d\d:\d\d\.\d+/, /\d\d:\d\d:\d\d,\d+/, /\d\d:\d\d:\d\d/, /\d\d:\d\d/,
  /\d\d\d\d\d\d\.\d+/, /\d\d\d\d\d\d,\d+/, /\d\d\d\d\d\d/, /\d\d\d\d/, /\d\d/,
];

const TZ = /Z|[+-]\d\d(?::?\d\d)?/;

const isLeapYear = (year) => (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
const daysInYear = (year) => (isLeapYear(year) ? 366 : 365);
const mod = (n, x) => ((n % x) + x) % x;

/** moment `daysInMonth`: 월은 0부터이고 범위를 넘어도 해를 넘겨 계산한다. */
function daysInMonth(year, month) {
  const modMonth = mod(month, 12);
  const y = year + (month - modMonth) / 12;
  return modMonth === 1 ? (isLeapYear(y) ? 29 : 28) : 31 - ((modMonth % 7) % 2);
}

/** 그 해의 ISO 주 수(52 또는 53). 1월 1일이 목요일이거나, 윤년이면서 수요일이면 53주이다. */
function isoWeeksInYear(year) {
  const jan1 = new Date(Date.UTC(2000, 0, 1));
  jan1.setUTCFullYear(year);
  const weekday = jan1.getUTCDay(); // 0=일요일
  return weekday === 4 || (isLeapYear(year) && weekday === 3) ? 53 : 52;
}

/**
 * ISO 8601로 읽히는 글자가 오버플로(달, 날, 시, 분, 초, 주, 요일, 연중 일수의 범위 초과) 없이 올바른 날짜인지 본다.
 * moment가 ISO 정규식에 맞는 글자는 범위를 벗어나도 다른 해석으로 넘기지 않고 그대로 "올바르지 않음"으로 두는 것과 같다.
 *
 * @param {RegExpExecArray} match
 * @return {boolean}
 */
function isValidIso(match) {
  const datePart = match[1];
  const dateRule = ISO_DATES.find(([, rx]) => rx.test(datePart));
  if (!dateRule) { return false; }
  const format = dateRule[0];
  let year; let month = 0; let day = 1;
  let weekday = null; let week = null; let dayOfYear = null;
  const digits = (text) => text.replace(/[^0-9]/g, "");
  const signedYear = (text) => parseInt(text, 10);
  switch (format) {
    case "YYYYYY-MM-DD": year = signedYear(datePart.slice(0, 7)); month = parseInt(datePart.slice(8, 10), 10) - 1; day = parseInt(datePart.slice(11, 13), 10); break;
    case "YYYY-MM-DD": year = parseInt(datePart.slice(0, 4), 10); month = parseInt(datePart.slice(5, 7), 10) - 1; day = parseInt(datePart.slice(8, 10), 10); break;
    case "YYYY-MM": year = parseInt(datePart.slice(0, 4), 10); month = parseInt(datePart.slice(5, 7), 10) - 1; break;
    case "YYYY-DDD": year = parseInt(datePart.slice(0, 4), 10); dayOfYear = parseInt(datePart.slice(5, 8), 10); break;
    case "GGGG-[W]WW-E": year = parseInt(datePart.slice(0, 4), 10); week = parseInt(datePart.slice(6, 8), 10); weekday = parseInt(datePart.slice(9, 10), 10); break;
    case "GGGG-[W]WW": year = parseInt(datePart.slice(0, 4), 10); week = parseInt(datePart.slice(6, 8), 10); break;
    case "YYYYYYMMDD": year = signedYear(datePart.slice(0, 7)); month = parseInt(datePart.slice(7, 9), 10) - 1; day = parseInt(datePart.slice(9, 11), 10); break;
    case "YYYYMMDD": year = parseInt(datePart.slice(0, 4), 10); month = parseInt(datePart.slice(4, 6), 10) - 1; day = parseInt(datePart.slice(6, 8), 10); break;
    case "GGGG[W]WWE": year = parseInt(datePart.slice(0, 4), 10); week = parseInt(datePart.slice(5, 7), 10); weekday = parseInt(datePart.slice(7, 8), 10); break;
    case "GGGG[W]WW": year = parseInt(datePart.slice(0, 4), 10); week = parseInt(datePart.slice(5, 7), 10); break;
    case "YYYYDDD": year = parseInt(datePart.slice(0, 4), 10); dayOfYear = parseInt(datePart.slice(4, 7), 10); break;
    case "YYYYMM": year = parseInt(datePart.slice(0, 4), 10); month = parseInt(datePart.slice(4, 6), 10) - 1; break;
    default: year = parseInt(datePart.slice(0, 4), 10); break; // YYYY
  }
  if (Math.abs(year) > 275760) { return false; } // JS Date 범위 밖
  if (dayOfYear !== null) {
    if (dayOfYear > daysInYear(year) || dayOfYear === 0) { return false; }
  } else if (week !== null) {
    if (week < 1 || week > isoWeeksInYear(year)) { return false; }
    if (weekday !== null && (weekday < 1 || weekday > 7)) { return false; }
  } else if (month < 0 || month > 11 || day < 1 || day > daysInMonth(year, month)) {
    return false;
  }

  // 시간 부분
  let hour = 0; let minute = 0; let second = 0; let fraction = 0;
  if (match[3]) {
    const timeText = match[3];
    if (!ISO_TIMES.some((rx) => rx.test(timeText))) { return false; }
    const parts = digits(timeText.split(/[.,]/)[0]);
    hour = parseInt(parts.slice(0, 2), 10);
    minute = parts.length >= 4 ? parseInt(parts.slice(2, 4), 10) : 0;
    second = parts.length >= 6 ? parseInt(parts.slice(4, 6), 10) : 0;
    const frac = timeText.split(/[.,]/)[1];
    fraction = frac ? Math.floor(parseFloat("0." + frac) * 1000) : 0;
  }
  if (match[4] && !TZ.test(match[4])) { return false; }
  if (hour < 0 || hour > 24 || (hour === 24 && (minute !== 0 || second !== 0 || fraction !== 0))) { return false; }
  return minute >= 0 && minute <= 59 && second >= 0 && second <= 59 && fraction >= 0 && fraction <= 999;
}

/** moment의 RFC 2822 정규식(`Mon, 09 Nov 2026 10:00:00 GMT`). */
const RFC_2822 = /^(?:(Mon|Tue|Wed|Thu|Fri|Sat|Sun),?\s)?(\d{1,2})\s(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s(\d{2,4})\s(\d\d):(\d\d)(?::(\d\d))?\s(?:(UT|GMT|[ECMP][SD]T)|([Zz])|([+-]\d{4}))$/;
const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/** RFC 2822 글자: 요일이 그 날짜의 요일과 다르거나 달, 날, 시, 분, 초가 범위를 넘으면 올바르지 않다. */
function isValidRfc2822(match) {
  const rawYear = parseInt(match[4], 10);
  const year = rawYear <= 49 ? 2000 + rawYear : (rawYear <= 999 ? 1900 + rawYear : rawYear);
  const month = MONTHS.indexOf(match[3]);
  const day = parseInt(match[2], 10);
  const hour = parseInt(match[5], 10);
  const minute = parseInt(match[6], 10);
  const second = match[7] ? parseInt(match[7], 10) : 0;
  if (match[1] && WEEKDAYS.indexOf(match[1]) !== new Date(year, month, day).getDay()) { return false; }
  if (day < 1 || day > daysInMonth(year, month)) { return false; }
  if (hour > 24 || (hour === 24 && (minute !== 0 || second !== 0)) || minute > 59 || second > 59) { return false; }
  return true;
}

/**
 * `moment(값).isValid()`와 같은 결과를 돌려준다. ISO 8601 모양이면 범위(2월 30일, 13월 등)까지 검사하고,
 * RFC 2822 모양도 같은 방식으로 검사하며, 그 밖의 글자는 moment처럼 `new Date(값)`으로 읽어 올바른지 본다.
 *
 * @param {*} value 보통 날짜 입력칸의 글자
 * @return {boolean}
 */
export function isValidDate(value) {
  const text = String(value);
  const match = ISO_EXTENDED.exec(text) || ISO_BASIC.exec(text);
  if (match) { return isValidIso(match); }
  const rfc = RFC_2822.exec(text);
  if (rfc) { return isValidRfc2822(rfc); }
  return !Number.isNaN(new Date(text).getTime());
}

// ---------------------------------------------------------------------------------------------------------------------
// 상대 시각
// ---------------------------------------------------------------------------------------------------------------------

/** moment 2.31.0 로케일의 relativeTime. 숫자 자리는 `%d`이다. */
const PHRASES = {
  en: { future: "in %s", past: "%s ago", s: "a few seconds", m: "a minute", mm: "%d minutes", h: "an hour", hh: "%d hours", d: "a day", dd: "%d days", M: "a month", MM: "%d months", y: "a year", yy: "%d years" },
  ko: { future: "%s 후", past: "%s 전", s: "몇 초", m: "1분", mm: "%d분", h: "한 시간", hh: "%d시간", d: "하루", dd: "%d일", M: "한 달", MM: "%d달", y: "일 년", yy: "%d년" },
  ja: { future: "%s後", past: "%s前", s: "数秒", m: "1分", mm: "%d分", h: "1時間", hh: "%d時間", d: "1日", dd: "%d日", M: "1ヶ月", MM: "%dヶ月", y: "1年", yy: "%d年" },
  uz: { future: "Якин %s ичида", past: "%s олдин", s: "фурсат", m: "бир дакика", mm: "%d дакика", h: "бир соат", hh: "%d соат", d: "бир кун", dd: "%d кун", M: "бир ой", MM: "%d ой", y: "бир йил", yy: "%d йил" },
};

/** ru는 숫자에 따라 단어 꼴이 바뀐다. 접미사가 있는 상대 시각(fromNow)의 꼴만 담았다. */
const RU = {
  future: "через %s", past: "%s назад", s: "несколько секунд", m: "минуту", h: "час", d: "день", M: "месяц", y: "год",
  plural: { mm: "минуту_минуты_минут", hh: "час_часа_часов", dd: "день_дня_дней", MM: "месяц_месяца_месяцев", yy: "год_года_лет" },
};

function ruText(key, count) {
  const forms = RU.plural[key].split("_");
  const word = count % 10 === 1 && count % 100 !== 11 ? forms[0]
    : (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20) ? forms[1] : forms[2]);
  return count + " " + word;
}

/** moment 임계값: 초 44, 분 45, 시 22, 일 26, 월 11. */
const THRESHOLD = { ss: 44, m: 45, h: 22, d: 26, M: 11 };

const daysToMonths = (days) => (days * 4800) / 146097;
const monthsToDays = (months) => (months * 146097) / 4800;

/** moment `add(n, 'M')`: 말일을 넘으면 그 달의 말일로 맞춘다. */
function addMonths(date, n) {
  const d = new Date(date.getTime());
  const day = Math.min(d.getDate(), daysInMonth(d.getFullYear(), d.getMonth() + n));
  d.setMonth(d.getMonth() + n, day);
  return d;
}

/** [base] 이후의 [other]까지 달력 기준 개월 수와 남은 밀리초(moment `positiveMomentsDifference`). */
function positiveDifference(base, other) {
  let months = other.getMonth() - base.getMonth() + (other.getFullYear() - base.getFullYear()) * 12;
  if (addMonths(base, months).getTime() > other.getTime()) { months -= 1; }
  return { months, ms: other.getTime() - addMonths(base, months).getTime() };
}

/**
 * `moment(date).fromNow()`와 같은 문구를 돌려준다.
 *
 * @param {Date|number} date 비교할 시각
 * @param {String} [language] en, ko, ja, ru, uz. 그 밖의 값은 영어(기본값은 `window.yonaLanguage`)
 * @param {number} [now] 지금 시각(밀리초). 시험에서 고정할 때 쓴다
 * @return {String} 예: "3일 전", "in 2 hours"
 */
export function fromNow(date, language = (typeof window !== "undefined" ? window.yonaLanguage : "en"), now = Date.now()) {
  const target = new Date(date instanceof Date ? date.getTime() : date);
  if (Number.isNaN(target.getTime())) { return "Invalid date"; }
  const base = new Date(now);
  const isFuture = target.getTime() > base.getTime();
  const diff = target.getTime() >= base.getTime() ? positiveDifference(base, target) : positiveDifference(target, base);

  // Duration.abs().as(...): 개월은 반올림한 일수로, 일 이하는 그 일수와 밀리초로 환산한다
  const wholeDays = Math.round(monthsToDays(diff.months));
  const seconds = Math.round(wholeDays * 86400 + diff.ms / 1000);
  const minutes = Math.round(wholeDays * 1440 + diff.ms / 6e4);
  const hours = Math.round(wholeDays * 24 + diff.ms / 36e5);
  const days = Math.round(wholeDays + diff.ms / 864e5);
  const months = Math.round(diff.months + daysToMonths(diff.ms / 864e5));
  const years = Math.round((diff.months + daysToMonths(diff.ms / 864e5)) / 12);

  let key; let count;
  if (seconds <= THRESHOLD.ss) { key = "s"; } // 45초 이상부터 분 단위이므로 moment의 "ss" 분기는 도달하지 않는다
  else if (minutes <= 1) { key = "m"; }
  else if (minutes < THRESHOLD.m) { key = "mm"; count = minutes; }
  else if (hours <= 1) { key = "h"; }
  else if (hours < THRESHOLD.h) { key = "hh"; count = hours; }
  else if (days <= 1) { key = "d"; }
  else if (days < THRESHOLD.d) { key = "dd"; count = days; }
  else if (months <= 1) { key = "M"; }
  else if (months < THRESHOLD.M) { key = "MM"; count = months; }
  else if (years <= 1) { key = "y"; }
  else { key = "yy"; count = years; }

  const isRu = language === "ru";
  const table = isRu ? RU : (PHRASES[language] || PHRASES.en);
  let phrase;
  if (isRu) {
    phrase = RU.plural[key] ? ruText(key, count) : RU[key];
  } else {
    phrase = table[key].replace("%d", count);
  }
  return (isFuture ? table.future : table.past).replace("%s", phrase);
}

register("yona.dates", { isValidDate, fromNow });
