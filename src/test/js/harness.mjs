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

/** 모든 모듈이 쓰는 최소한의 공통 도우미이다(`$yobi.createNamespace` 등). 실제 yobi.Common.js는 쓰지 않는다. */
export const COMMON_STUBS = `
  window.$yobi = {
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
  window.Messages = function (key) { return key; };
  window.bootstrap = { Modal: { getOrCreateInstance: function () { return { show: function () { (window.__modal = window.__modal || []).push('show'); }, hide: function () {} }; } } };
  window.yobi = window.yobi || {}; yobi.ui = yobi.ui || {}; yobi.ui.Typeahead = function () {};
`;

/**
 * 새 페이지를 만든다. [html]을 본문으로 하고 [scripts](JS 디렉터리 기준 경로 또는 `{content}`)를 차례로 불러온다.
 * 페이지에서 난 스크립트 오류는 `page.errors`에 모인다.
 */
export async function pageWith({ html = '', scripts = [], stubs = true } = {}) {
  const b = await launch();
  const page = await b.newPage();
  page.errors = [];
  page.on('pageerror', (e) => page.errors.push(String(e.message || e)));
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"></head><body>${html}</body></html>`);
  await page.addScriptTag({ path: path.join(JS, 'lib/jquery/jquery-3.3.1.js') });
  if (stubs) await page.addScriptTag({ content: COMMON_STUBS });
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
