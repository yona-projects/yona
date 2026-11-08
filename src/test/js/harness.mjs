// 화면 스크립트(JS) 결함 수정을 검증하는 시험 도구이다. 실제 JS 파일을 Playwright(Chromium)에 불러 DOM을 만들고 함수를 실행한다.
// 실행: `node src/test/js/run.mjs` (저장소 루트에서). Playwright는 상위 디렉터리의 node_modules에서 찾는다.
import { chromium } from 'playwright';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
export const JS = path.join(ROOT, 'src/main/resources/static/assets/javascripts');

let browser;
export async function launch() { browser ||= await chromium.launch(); return browser; }
export async function close() { await browser?.close(); browser = null; }

/** 모든 모듈이 쓰는 최소한의 공통 도우미이다(`$yona.createNamespace` 등). 실제 yona.Common.js는 쓰지 않는다. */
export const COMMON_STUBS = `
  window.$yona = {
    createNamespace: function (ns) {
      var parts = ns.split('.'), parent = window, name = null;
      for (var i = 0; i < parts.length; i++) {
        name = parts[i];
        if (i === parts.length - 1) break;
        if (typeof parent[name] !== 'object') parent[name] = {};
        parent = parent[name];
      }
      return { container: parent, name: name };
    },
    alert: function (m) { (window.__alerts = window.__alerts || []).push(m); },
    showAlert: function (m) { (window.__alerts = window.__alerts || []).push(m); }
  };
  // 실제 yona.Common.js의 render, renderElement와 같은 동작의 대역이다(실제 구현은 template.test.mjs가 검증한다)
  Mustache.escape = function (v) { return ('' + v).split('<').join('&lt;').split('>').join('&gt;').split('"').join('&#34;').split("'").join('&#39;'); };
  window.$yona.render = function (t, d) { if (typeof t !== 'string') return $(); return $($.parseHTML(Mustache.render(t, d == null ? {} : d).trim())); };
  window.$yona.renderElement = function (el, d) { return window.$yona.render($(el).html(), d); };
  window.Messages = function (key) { return key; };
  window.bootstrap = { Modal: { getOrCreateInstance: function () { return { show: function () { (window.__modal = window.__modal || []).push('show'); }, hide: function () {} }; } } };
  window.yona = window.yona || {}; yona.ui = yona.ui || {}; yona.ui.Typeahead = function () {};
`;

/** jQuery 4가 지우는 API와 `$.expr.filters`를 jQuery 3.7.1에서 없앤다. */
export const JQUERY4_REMOVALS = `
  ['trim', 'parseJSON', 'isNumeric', 'isFunction', 'isArray', 'isWindow', 'camelCase', 'nodeName', 'type', 'now', 'unique'].forEach(function (k) { delete jQuery[k]; });
  delete jQuery.expr.filters;
`;

/**
 * 새 페이지를 만든다. [html]을 본문으로 하고 [scripts](JS 디렉터리 기준 경로 또는 `{content}`)를 차례로 불러온다.
 * 페이지에서 난 스크립트 오류는 `page.errors`에 모인다.
 */
export async function pageWith({ html = '', scripts = [], stubs = true, jq4 = true } = {}) {
  const b = await launch();
  const page = await b.newPage();
  page.errors = [];
  page.on('pageerror', (e) => page.errors.push(String(e.message || e)));
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"></head><body>${html}</body></html>`);
  await page.addScriptTag({ path: path.join(JS, 'lib/jquery/jquery-3.7.1.js') });
  // jQuery 4처럼 지워지는 API를 없앤 환경에서 시험한다(`Jquery4ReadinessTest`, D-103). 앱 코드와 플러그인이 표준 JS만 쓰는지 실제로 실행해 확인한다.
  if (jq4) await page.evaluate(JQUERY4_REMOVALS);
  if (stubs) {
    await page.addScriptTag({ path: path.join(JS, 'lib/mustache.js') });
    await page.addScriptTag({ content: COMMON_STUBS });
  }
  for (const s of scripts) await page.addScriptTag(typeof s === 'string' ? { path: path.join(JS, s) } : s);
  return page;
}

/**
 * Thymeleaf 템플릿의 인라인 스크립트 조각을 [from]부터 [to] 직전까지 잘라 돌려준다.
 * `/*[[...]]*\/ ""` 꼴의 인라인 표현은 주석과 빈 문자열이라 그대로 실행해도 문법이 맞는다.
 */
import fs from 'node:fs';
export function templateSnippet(relativePath, from, to) {
  const text = fs.readFileSync(path.join(ROOT, 'src/main/resources/templates', relativePath), 'utf8');
  const start = text.indexOf(from);
  if (start < 0) throw new Error(`시작 표지를 찾지 못했다: ${from}`);
  const end = to ? text.indexOf(to, start) : text.length;
  if (end < 0) throw new Error(`끝 표지를 찾지 못했다: ${to}`);
  return text.slice(start, end);
}

/** JS 소스 파일에서 [from]부터 [to] 직전까지 잘라 돌려준다. 모듈 안쪽 비공개 함수를 따로 시험할 때 쓴다. */
export function sourceSnippet(relativePath, from, to) {
  const text = fs.readFileSync(path.join(JS, relativePath), 'utf8');
  const start = text.indexOf(from);
  if (start < 0) throw new Error(`시작 표지를 찾지 못했다: ${from}`);
  const end = to ? text.indexOf(to, start) : text.length;
  if (end < 0) throw new Error(`끝 표지를 찾지 못했다: ${to}`);
  return text.slice(start, end);
}
