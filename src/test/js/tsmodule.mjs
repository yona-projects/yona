// TypeScript로 새로 쓰는 모듈(D-139)을 시험하는 도우미이다. `build-tools/ts`의 `tsc`로 `src/main/frontend/ts`를 빌드한 산출물을
// 실제 Chromium에서 import map(`yona/` 접두어)으로 불러 검증한다. 옮긴 옛 모듈의 시험은 기존 하네스(`harness.mjs`)가 산출 JS를 그대로 읽는다.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { launch, ROOT, JS } from './harness.mjs';

/** `tsc`가 내보내는 폴더이다. Gradle `compileTs`와 같은 위치이다. */
export const TS_OUT = path.join(ROOT, 'build/generated/ts/static/assets/javascripts');
const TSC = path.join(ROOT, 'build-tools/ts/node_modules/.bin/tsc');
const IMPORT_MAP = '{"imports": {"yona/": "/assets/javascripts/esm/"}}';

/** TS 소스를 빌드한다. 형 검사 오류가 있으면 오류 글자를 담아 던진다(`tsc`는 오류가 있어도 산출하므로 종료 코드로 판정한다). */
export function buildTs() {
  if (!fs.existsSync(TSC)) throw new Error('tsc가 없습니다. 먼저 `npm ci --prefix build-tools/ts` 또는 `./gradlew installTs`를 실행하세요.');
  try { execFileSync(TSC, ['-p', path.join(ROOT, 'build-tools/ts/tsconfig.json')], { stdio: 'pipe' }); }
  catch (e) { throw new Error('tsc 빌드 실패:\n' + String(e.stdout || e.message)); }
}

/**
 * 모듈 방식 시험용 페이지를 연다. `esm/`은 TS 산출물을 먼저 찾고 없으면 저장소의 옛 JS를 내려 준다.
 * [head]는 모듈보다 먼저 실행할 인라인 스크립트(예: `window.yonaContextPath = ...`)이다.
 */
export async function openTs(body, { head = '' } = {}) {
  const browser = await launch(); const ctx = await browser.newContext(); const page = await ctx.newPage(); page.errors = [];
  page.on('pageerror', (e) => page.errors.push(String(e.message || e)));
  await page.route('http://ts.test/**', (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/page') return route.fulfill({ contentType: 'text/html', body: `<!doctype html><head><script type="importmap">${IMPORT_MAP}</script>${head}</head><body>${body}</body>` });
    const rel = url.pathname.replace('/assets/javascripts/', '');
    for (const base of [TS_OUT, JS]) {
      const file = path.join(base, rel);
      if (url.pathname.startsWith('/assets/javascripts/') && fs.existsSync(file) && fs.statSync(file).isFile()) return route.fulfill({ contentType: 'text/javascript', body: fs.readFileSync(file) });
    }
    return route.fulfill({ status: 404, body: '' });
  });
  await page.goto('http://ts.test/page');
  return { page, ctx };
}
