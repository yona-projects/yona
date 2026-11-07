import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig, minifySync } from "vite";
import { bundles } from "./bundles.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const assets = resolve(here, "../../src/main/resources/static/assets");
const EMPTY_ENTRY = "\0yona-empty-entry";

/**
 * 입력 파일을 모듈로 만들지 않고 순서대로 이어 붙인 뒤 압축해 번들로 내보내는 플러그인.
 *
 * Yona의 JS는 `import`가 없는 전역 스크립트라서 Rollup의 모듈 번들 방식을 쓰면 전역 함수와 변수가
 * 모듈 안으로 들어가 템플릿이 깨진다. 그래서 vite의 빌드 틀(설정, 출력 폴더, 압축기)만 쓰고
 * 합치는 일은 직접 한다. 최상위 이름은 압축기가 바꾸지 않는다(스크립트로 압축).
 */
function concatScripts() {
  return {
    name: "yona-concat-scripts",
    resolveId: (id) => (id === EMPTY_ENTRY ? id : null),
    load: (id) => (id === EMPTY_ENTRY ? "export {}" : null),
    generateBundle(_options, bundle) {
      // vite가 요구하는 빈 진입점의 결과물은 버린다.
      for (const key of Object.keys(bundle)) delete bundle[key];
      for (const [name, inputs] of Object.entries(bundles)) {
        const source = inputs.map((file) => {
          const path = join(assets, file);
          this.addWatchFile(path);
          // 파일이 `;` 없이 끝나도 다음 파일과 붙지 않도록 줄바꿈과 `;`로 구분한다.
          return readFileSync(path, "utf8") + "\n;\n";
        }).join("");
        const result = minifySync(name, source, { compress: { target: "es2015" }, sourcemap: false });
        if (result.errors.length > 0) this.error(`${name}: ${result.errors.map((e) => e.message).join("; ")}`);
        this.emitFile({ type: "asset", fileName: name, source: result.code });
      }
    },
  };
}

export default defineConfig({
  root: here,
  logLevel: "warn",
  publicDir: false,
  plugins: [concatScripts()],
  build: {
    // 기본은 저장소의 번들 위치이고, 시험은 YONA_JS_OUT으로 임시 폴더에 만든다.
    outDir: process.env.YONA_JS_OUT || join(assets, "javascripts"),
    emptyOutDir: false,
    minify: false,
    reportCompressedSize: false,
    rollupOptions: { input: EMPTY_ENTRY },
  },
});
