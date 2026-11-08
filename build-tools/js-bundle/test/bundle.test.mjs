import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, existsSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { loadGlobals } from "./globals.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..");
const expected = JSON.parse(readFileSync(join(here, "expected-globals.json"), "utf8"));
const out = mkdtempSync(join(tmpdir(), "yona-js-bundle-"));

/** 번들을 임시 폴더에 만든다(저장소의 번들을 건드리지 않는다). 두 번 만들어 결과가 같은지도 본다. */
function build(dir) {
  execFileSync(join(root, "node_modules/.bin/vite"), ["build"], {
    cwd: root, env: { ...process.env, YONA_JS_OUT: dir }, stdio: "pipe",
  });
}
build(out);

test("번들 3개가 만들어진다", () => {
  for (const name of ["yona-layout.js", "yona-common.js", "yona-lib.js"]) {
    assert.ok(existsSync(join(out, name)), `${name}이 없다`);
    assert.ok(statSync(join(out, name)).size > 10000, `${name}이 너무 작다`);
  }
});

test("모듈로 감싸지 않아 템플릿이 쓰는 전역 이름이 그대로 남는다", () => {
  const r = loadGlobals(out);
  assert.deepEqual(r.errors, []);
  for (const n of expected.globals) assert.ok(r.names.includes(n), `전역 ${n}이 없다`);
  assert.equal(typeof r.window.temporarySaveHandler, "function");
  assert.equal(typeof r.window.removeCurrentPageTemprarySavedContent, "function");
});

test("jQuery 3.7.1과 플러그인, yobi 모듈이 옛 번들과 같은 이름을 가진다", () => {
  const r = loadGlobals(out);
  assert.equal(r.window.jQuery.fn.jquery, "3.7.1");
  assert.equal(r.window.$, r.window.jQuery);
  for (const n of expected.jqueryFn) assert.ok(n in r.window.jQuery.fn, `jQuery.fn.${n}이 없다`);
  for (const n of expected.jqueryStatic) assert.ok(n in r.window.jQuery, `jQuery.${n}이 없다`);
  for (const n of expected.yona) assert.ok(n in r.window.yona, `yona.${n}이 없다`);
  for (const n of expected.$yona) assert.ok(n in r.window.$yona, `$yona.${n}이 없다`);
});

test("옛 이름 yobi와 $yobi는 더 이상 전역에 없다(D-93)", () => {
  const r = loadGlobals(out);
  assert.ok(!("yobi" in r.window), "전역 yobi가 남아 있다");
  assert.ok(!("$yobi" in r.window), "전역 $yobi가 남아 있다");
});

test("쓰이지 않는 zclip 코드는 번들에 들어가지 않는다", () => {
  const r = loadGlobals(out);
  assert.ok(!("ZeroClipboard" in r.window));
  assert.ok(!("zclip" in r.window.jQuery.fn));
  // 쓰이지 않던 jquery.validate와 jquery.placeholder 플러그인도 빠진다(A-398)
  for (const n of expected.removedFn) assert.ok(!(n in r.window.jQuery.fn), `jQuery.fn.${n}이 남아 있다`);
  for (const n of expected.removedStatic) assert.ok(!(n in r.window.jQuery), `jQuery.${n}이 남아 있다`);
  assert.equal(typeof r.window.ClipboardJS, "function");
  assert.equal(r.window.FormValidator !== undefined, true, "lib/validate.js의 FormValidator는 계속 있다");
  // jquery.tmpl 대신 Mustache를 쓴다(D-94): 전역 Mustache와 $yona.render가 있다
  assert.equal(typeof r.window.Mustache.render, "function");
  assert.equal(typeof r.window.$yona.render, "function");
  assert.equal(typeof r.window.$yona.renderElement, "function");
  // jquery-pjax 대신 htmx를 쓴다(D-96): 전역 htmx가 있고 jQuery.pjax는 없다. Yona 설정(yona.Htmx.js)이 적용된다
  assert.equal(r.window.htmx.version, "2.0.11");
  assert.ok(!("pjax" in r.window.jQuery) && !("pjax" in r.window.jQuery.fn), "jQuery.pjax가 남아 있다");
  assert.equal(r.window.htmx.config.historyCacheSize, 0);
  assert.equal(r.window.htmx.config.refreshOnHistoryMiss, true);
});

test("같은 입력으로 다시 만들면 byte 단위로 같다(재현성)", () => {
  const again = mkdtempSync(join(tmpdir(), "yona-js-bundle-"));
  build(again);
  for (const name of ["yona-layout.js", "yona-common.js", "yona-lib.js"]) {
    assert.ok(readFileSync(join(out, name)).equals(readFileSync(join(again, name))), `${name}이 다르다`);
  }
});

test("저장소에 들어 있는 번들은 지금의 입력 파일로 만든 결과와 같다(입력을 고치고 번들을 다시 만들지 않은 실수를 막는다)", () => {
  const committed = join(root, "../../src/main/resources/static/assets/javascripts");
  for (const name of ["yona-layout.js", "yona-common.js", "yona-lib.js"]) {
    assert.ok(readFileSync(join(committed, name)).equals(readFileSync(join(out, name))),
      `${name}이 입력과 다르다. build-tools/minify-js.sh로 다시 만든다`);
  }
});

test("번들은 옛 Closure 번들보다 크게 늘지 않는다", () => {
  const old = join(root, "../../src/main/resources/static/assets/javascripts");
  for (const name of ["yona-layout.js", "yona-common.js", "yona-lib.js"]) {
    const a = statSync(join(old, name)).size, b = statSync(join(out, name)).size;
    assert.ok(b < a * 1.25, `${name}: ${b} > ${a} * 1.25`);
  }
});
