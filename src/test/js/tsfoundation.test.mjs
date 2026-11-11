// TypeScript 기반(D-139, P-1): 빌드가 되고, 새 코어 모듈 `core/config.ts`의 `url()`이 컨텍스트 경로를 한 번만 붙이는지 확인한다.
import assert from 'node:assert/strict';
import { buildTs, openTs } from './tsmodule.mjs';

/** 컨텍스트 경로를 [ctxPath]로 정해 두고 `url()`에 [inputs]를 넣어 결과를 돌려준다. */
async function urls(ctxPath, inputs) {
  const head = ctxPath === undefined ? '' : `<script>window.yonaContextPath = ${JSON.stringify(ctxPath)};</script>`;
  const { page, ctx } = await openTs(`<script type="module">import { url } from 'yona/core/config.js'; window.__out = ${JSON.stringify(inputs)}.map((p) => url(p)); window.__done = true;</script>`, { head });
  await page.waitForFunction(() => window.__done === true, null, { timeout: 5000 });
  const out = await page.evaluate(() => window.__out); const errors = page.errors; await ctx.close();
  assert.deepEqual(errors, []);
  return out;
}

export const tests = [
  { name: 'tsc 빌드가 형 검사 오류 없이 끝난다', run: async () => { buildTs(); } },
  { name: "컨텍스트 경로가 '/yona'이면 '/'로 시작하는 주소 앞에 한 번만 붙인다(질의와 해시 유지)", run: async () => {
    buildTs();
    assert.deepEqual(await urls('/yona', ['/a/b', '/a?x=1&y=2', '/a#h', '/']), ['/yona/a/b', '/yona/a?x=1&y=2', '/yona/a#h', '/yona/']);
  } },
  { name: '컨텍스트 경로가 없거나 빈 글자이면 주소를 바꾸지 않는다', run: async () => {
    buildTs();
    assert.deepEqual(await urls(undefined, ['/a/b']), ['/a/b']);
    assert.deepEqual(await urls('', ['/a/b']), ['/a/b']);
  } },
  { name: '컨텍스트 경로 끝의 빗금은 한 번만 쓴다(/yona/ 도 /yona 와 같다)', run: async () => {
    buildTs();
    assert.deepEqual(await urls('/yona/', ['/a']), ['/yona/a']);
  } },
  { name: '외부 주소, 프로토콜 상대 주소, 상대 경로, 해시만 있는 주소는 건드리지 않는다', run: async () => {
    buildTs();
    assert.deepEqual(await urls('/yona', ['https://x.example/y', '//cdn.example/z.js', 'a/b', '#top', '?q=1', 'mailto:a@b.c']), ['https://x.example/y', '//cdn.example/z.js', 'a/b', '#top', '?q=1', 'mailto:a@b.c']);
  } },
];
