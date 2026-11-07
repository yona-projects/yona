import { JSDOM } from "jsdom";
import { readFileSync } from "node:fs";

/** 번들 3개를 템플릿과 같은 순서(layout, common, lib)로 가짜 브라우저에 불러와, 번들이 새로 만든 전역 이름을 모은다. */
export function loadGlobals(dir) {
  const baseline = new Set(Object.getOwnPropertyNames(new JSDOM("").window));
  const dom = new JSDOM("<!doctype html><html><body></body></html>", { runScripts: "outside-only", url: "http://localhost/" });
  const errors = [];
  for (const name of ["yona-layout.js", "yona-common.js", "yona-lib.js"]) {
    try {
      dom.window.eval(readFileSync(`${dir}/${name}`, "utf8"));
    } catch (e) {
      errors.push(`${name}: ${e.message}`);
    }
  }
  const names = Object.getOwnPropertyNames(dom.window).filter((n) => !baseline.has(n)).sort();
  return { window: dom.window, names, errors };
}
