// 새 셸 사이드바의 숨기기와 다시 보이기(D-141, A-469 5번): TS 모듈 `shell/sidebar`를 실제 Chromium에서 시험한다.
// 숨김 상태는 `html[data-sidebar="collapsed"]`로 그리고 localStorage(`shellSidebarCollapsed`)에 기억한다(D-135의 첫 그림 거울).
import assert from 'node:assert/strict';
import { buildTs, openTs } from './tsmodule.mjs';

const BODY = `<button type="button" id="t" data-shell-sidebar-toggle>숨기기</button><nav class="shell-sidebar"><a class="nav-link" href="#">홈</a></nav><input id="q"><script type="module">import { initSidebar } from 'yona/shell/sidebar.js'; window.__api = initSidebar(); window.__ready = true;</script>`;

async function open(opts = {}) {
  buildTs();
  const { page, ctx } = await openTs(BODY, opts);
  await page.waitForFunction(() => window.__ready === true, null, { timeout: 5000 });
  return { page, ctx };
}
const state = (page) => page.evaluate(() => [document.documentElement.getAttribute('data-sidebar'), localStorage.getItem('shellSidebarCollapsed')]);

export const tests = [
  { name: '숨기기 단추를 누르면 html에 collapsed가 붙고 기억되며 다시 누르면 풀린다', run: async () => {
    const { page, ctx } = await open();
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.click('#t'); assert.deepEqual(await state(page), ['collapsed', 'true']);
    assert.equal(await page.getAttribute('#t', 'aria-expanded'), 'false');
    await page.click('#t'); assert.deepEqual(await state(page), [null, 'false']);
    assert.equal(await page.getAttribute('#t', 'aria-expanded'), 'true');
    assert.deepEqual(page.errors, []); await ctx.close();
  } },
  { name: '기억해 둔 숨김 상태가 모듈을 불러올 때 다시 적용된다', run: async () => {
    const { page, ctx } = await open({ head: '<script>localStorage.setItem("shellSidebarCollapsed","true")</script>' });
    assert.equal(await page.evaluate(() => document.documentElement.getAttribute('data-sidebar')), 'collapsed');
    assert.equal(await page.getAttribute('#t', 'aria-expanded'), 'false'); await ctx.close();
  } },
  { name: '[ 키로도 숨기고 보이게 하지만 입력란에 글을 쓰는 중에는 동작하지 않는다', run: async () => {
    const { page, ctx } = await open();
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.keyboard.press('['); assert.equal((await state(page))[0], 'collapsed');
    await page.keyboard.press('['); assert.equal((await state(page))[0], null);
    await page.focus('#q'); await page.keyboard.press('['); assert.equal((await state(page))[0], null, '입력란에서는 키가 글자로 들어가야 한다');
    assert.equal(await page.inputValue('#q'), '['); await ctx.close();
  } },
  { name: '좁은 폭(992 미만, 서랍)에서는 숨김 상태를 바꾸거나 기억하지 않는다', run: async () => {
    const { page, ctx } = await open();
    await page.setViewportSize({ width: 390, height: 800 });
    await page.keyboard.press('['); assert.deepEqual(await state(page), [null, null]);
    await page.evaluate(() => document.getElementById('t').click()); assert.deepEqual(await state(page), [null, null]); await ctx.close();
  } },
  { name: '저장소를 쓸 수 없어도(차단, 가득 참) 오류 없이 이번 화면에서는 동작한다', run: async () => {
    const { page, ctx } = await open({ head: '<script>Object.defineProperty(window, "localStorage", { get() { throw new Error("blocked") } })</script>' });
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.click('#t'); assert.equal(await page.evaluate(() => document.documentElement.getAttribute('data-sidebar')), 'collapsed');
    assert.deepEqual(page.errors, []); await ctx.close();
  } },
];
