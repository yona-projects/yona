// 마크다운 본문 이미지 확대 보기(Viewer.js + jquery-viewer 래퍼, `layout/layout.html`의 설정과 같게 쓴다): 열기, 이동, 확대, 회전, 닫기, 아래쪽 썸네일 목록의 가운데 맞춤.
// 1.6.1(Yona 패치 한 줄: renderList)에서 먼저 통과시키고 1.15.2로 올려서도 통과시킨다. VW_DIR로 다른 폴더의 viewer.js, viewer.css를 시험할 수 있다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, JS } from './harness.mjs';

const DIR = process.env.VW_DIR || path.join(JS, 'lib/viewerjs');
const svg = (w, h, c) => `data:image/svg+xml;utf8,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}"><rect width="${w}" height="${h}" fill="${c}"/></svg>`)}`;
const IMGS = [svg(400, 200, 'red'), svg(200, 400, 'green'), svg(300, 300, 'blue'), svg(500, 250, 'orange'), svg(250, 250, 'purple')];

/** layout.html의 초기화와 같은 방식으로 이미지 5장이 든 `.markdown-wrap`을 연다. */
async function open() {
  const page = await pageWith({ stubs: false, scripts: [{ path: path.join(DIR, 'viewer.js') }, { path: path.join(DIR, '../viewerjs/jquery-viewer.js') }],
    html: `<div class="markdown-wrap">${IMGS.map((s, i) => `<img id="i${i}" src="${s}" alt="그림 ${i}" width="80" height="60">`).join('')}</div>` });
  await page.addStyleTag({ path: path.join(DIR, 'viewer.css') });
  await page.setViewportSize({ width: 1000, height: 700 });
  await page.evaluate(() => { Viewer.setDefaults({ transition: false }); $('.markdown-wrap').each(function () { $(this).viewer(); }); });
  return page;
}
const shown = (page) => page.evaluate(() => { const c = document.querySelector('.viewer-container'); return !!c && c.classList.contains('viewer-in') && getComputedStyle(c).display !== 'none'; });
const current = (page) => page.evaluate(() => { const i = document.querySelector('.viewer-canvas img'); return i ? IMGS_OF(i.src) : null; function IMGS_OF(s) { return decodeURIComponent(s).match(/fill="(\w+)"/)?.[1]; } });
const openAt = async (page, n) => { await page.click(`#i${n}`); await page.waitForFunction(() => document.querySelector('.viewer-container.viewer-in'), null, { timeout: 5000 }); await page.waitForTimeout(700); };
export const tests = [
  { name: '이미지를 누르면 확대 보기가 열리고 누른 이미지가 보인다(마크다운 본문의 이미지)', run: async () => {
    const page = await open(); assert.equal(await shown(page), false); await openAt(page, 2);
    assert.equal(await shown(page), true); assert.equal(await current(page), 'blue'); assert.equal(await page.locator('.viewer-list li').count(), 5); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '다음과 이전 단추로 옮겨 가고 끝에서는 돌아가지 않는다(loop 끔이 아니면 처음으로 돈다)', run: async () => {
    const page = await open(); await openAt(page, 0);
    await page.click('.viewer-toolbar [data-viewer-action="next"]'); await page.waitForTimeout(150); assert.equal(await current(page), 'green');
    await page.click('.viewer-toolbar [data-viewer-action="prev"]'); await page.waitForTimeout(150); assert.equal(await current(page), 'red');
    await page.click('.viewer-toolbar [data-viewer-action="prev"]'); await page.waitForTimeout(150); const afterPrev = await current(page);
    assert.ok(['purple', 'red'].includes(afterPrev), afterPrev); await page.close();
  } },
  { name: '키보드: 오른쪽, 왼쪽 화살표로 이동하고 Esc로 닫힌다', run: async () => {
    const page = await open(); await openAt(page, 1);
    await page.keyboard.press('ArrowRight'); await page.waitForTimeout(150); assert.equal(await current(page), 'blue');
    await page.keyboard.press('ArrowLeft'); await page.waitForTimeout(150); assert.equal(await current(page), 'green');
    await page.keyboard.press('Escape'); await page.waitForTimeout(300); assert.equal(await shown(page), false); await page.close();
  } },
  { name: '썸네일 목록에서 항목을 누르면 그 이미지로 옮겨 간다', run: async () => {
    const page = await open(); await openAt(page, 0); await page.locator('.viewer-list li').nth(3).click(); await page.waitForTimeout(250);
    assert.equal(await current(page), 'orange'); await page.close();
  } },
  { name: '확대 단추를 누르면 이미지가 커지고 축소하면 작아진다. 원래 크기 단추로 1:1이 된다', run: async () => {
    const page = await open(); await openAt(page, 0); const w = () => page.evaluate(() => document.querySelector('.viewer-canvas img').getBoundingClientRect().width);
    const w0 = await w(); await page.click('.viewer-toolbar [data-viewer-action="zoom-in"]'); await page.waitForTimeout(150); const w1 = await w();
    await page.click('.viewer-toolbar [data-viewer-action="zoom-out"]'); await page.click('.viewer-toolbar [data-viewer-action="zoom-out"]'); await page.waitForTimeout(150); const w2 = await w();
    assert.ok(w1 > w0 && w2 < w1, `${w0} ${w1} ${w2}`);
    await page.click('.viewer-toolbar [data-viewer-action="one-to-one"]'); await page.waitForTimeout(150); assert.ok(Math.abs((await w()) - 400) <= 1, String(await w())); await page.close();
  } },
  { name: '회전 단추는 이미지를 90도씩 돌린다', run: async () => {
    const page = await open(); await openAt(page, 1);
    const rot = () => page.evaluate(() => { const m = new DOMMatrix(getComputedStyle(document.querySelector('.viewer-canvas img')).transform); return Math.round(Math.atan2(m.b, m.a) * 180 / Math.PI); });
    assert.equal(await rot(), 0); await page.click('.viewer-toolbar [data-viewer-action="rotate-right"]'); await page.waitForTimeout(150); assert.equal(await rot(), 90);
    await page.click('.viewer-toolbar [data-viewer-action="rotate-left"]'); await page.click('.viewer-toolbar [data-viewer-action="rotate-left"]'); await page.waitForTimeout(150); assert.equal(Math.abs(await rot()), 90); await page.close();
  } },
  { name: '닫기 단추로 닫히고 다시 열 수 있다', run: async () => {
    const page = await open(); await openAt(page, 2); await page.click('.viewer-close'); await page.waitForTimeout(300); assert.equal(await shown(page), false);
    await openAt(page, 4); assert.equal(await current(page), 'purple'); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '표시줄: 현재 순서와 제목(alt)이 보인다', run: async () => {
    const page = await open(); await openAt(page, 2);
    const t = await page.evaluate(() => document.querySelector('.viewer-title')?.textContent.replace(/\s+/g, ' ').trim()); assert.match(t, /그림 2/); assert.match(t, /\b5\b|3/); await page.close();
  } },
  { name: '썸네일 목록(Yona 패치가 막던 문제): 이미지가 많아도 처음 연 이미지의 활성 썸네일이 화면 안에 보인다', run: async () => {
    const page = await pageWith({ stubs: false, scripts: [{ path: path.join(DIR, 'viewer.js') }, { path: path.join(DIR, '../viewerjs/jquery-viewer.js') }],
      html: `<div class="markdown-wrap">${Array.from({ length: 20 }, (_, i) => `<img id="i${i}" src="${svg(400, 200, `hsl(${i * 18},70%,50%)`)}" width="40" height="30" alt="g${i}">`).join('')}</div>` });
    await page.addStyleTag({ path: path.join(DIR, 'viewer.css') }); await page.setViewportSize({ width: 1000, height: 700 });
    await page.evaluate(() => { Viewer.setDefaults({ transition: false }); $('.markdown-wrap').each(function () { $(this).viewer(); }); });
    await openAt(page, 15);
    const r = await page.evaluate(() => { const a = document.querySelector('.viewer-list li.viewer-active').getBoundingClientRect(); return { left: Math.round(a.left), right: Math.round(a.right), inside: a.left >= 0 && a.right <= innerWidth }; });
    assert.equal(r.inside, true, JSON.stringify(r)); await page.close();
  } },
  { name: '래퍼: 같은 요소에 다시 viewer()를 불러도 오류가 없고 destroy가 동작한다', run: async () => {
    const page = await open(); const r = await page.evaluate(() => { const g = $('.markdown-wrap'); const had = !!g.data('viewer'); g.viewer('destroy'); return { had, destroyed: !g.data('viewer') }; });
    assert.deepEqual(r, { had: true, destroyed: true }); assert.deepEqual(page.errors, []); await page.close();
  } },
];
