import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// 프로젝트 목록의 로고 영역(A-495). 기본 로고는 흰 바탕의 불투명 이미지라 흰 화면 위에서는 영역의 경계가 보이지 않았다(선생님 지적).
// 로고마다 얇은 테두리를 둬서 영역이 한눈에 구분되는지 실제 CSS로 확인한다.
const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const HTML = `<ul class="all-projects"><li class="project"><div class="info-wrap"><div class="owner-avatar-wrap"><a href="#"><img alt="p" width="1" height="1" src="data:image/gif;base64,R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw=="></a></div></div></li></ul>`;

async function open() {
  const page = await pageWith({ html: HTML, stubs: false });
  await page.setViewportSize({ width: 1280, height: 700 });
  for (const css of ['bootstrap.css', 'usermenu.css', 'yobi.css']) await page.addStyleTag({ path: path.join(CSS, css) });
  return page;
}
const style = (page, sel, props) => page.evaluate(([s, p]) => { const cs = getComputedStyle(document.querySelector(s)); return Object.fromEntries(p.map((k) => [k, cs[k]])); }, [sel, props]);

export const tests = [
  {
    name: '로고 영역은 50px 정사각형이고 흰 바탕과 구분되는 얇은 테두리를 가진다(A-495)',
    async run() {
      const page = await open();
      const s = await style(page, '.all-projects .owner-avatar-wrap', ['width', 'height', 'borderTopWidth', 'borderTopStyle', 'borderTopColor', 'borderTopLeftRadius']);
      assert.equal(s.width, '50px', `안쪽 폭 ${s.width}`);
      assert.equal(s.height, '50px');
      const outer = await page.evaluate(() => { const r = document.querySelector('.owner-avatar-wrap').getBoundingClientRect(); return [Math.round(r.width), Math.round(r.height)]; });
      assert.deepEqual(outer, [52, 52], '테두리 2px가 바깥에 더해진다');
      assert.equal(s.borderTopWidth, '1px');
      assert.equal(s.borderTopStyle, 'solid');
      assert.notEqual(s.borderTopColor, 'rgb(255, 255, 255)', '흰색 테두리는 흰 바탕에서 보이지 않는다');
      const img = await page.evaluate(() => { const r = document.querySelector('.owner-avatar-wrap img').getBoundingClientRect(); return [Math.round(r.width), Math.round(r.height)]; });
      assert.deepEqual(img, [50, 50], '이미지 크기는 v1.6의 50px 그대로이다');
    },
  },
];
