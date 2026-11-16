// 알림 설정의 프로젝트 검색과 선택한 프로젝트 제목(`esm/service/user.NotificationProjects.js`, A-582, A-586)을 고정한다.
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `<input id="notification-project-filter"><h4 id="notification-project-title">admin / alpha</h4><ul id="notification-projects"><li class="active"><a data-bs-toggle="tab">admin / alpha</a></li><li><a data-bs-toggle="tab">admin / Beta-Two</a></li><li><a data-bs-toggle="tab">orgx / gamma</a></li></ul><div id="notification-project-empty" hidden></div>`;
async function open() {
  const page = await pageWith({ stubs: false, html: HTML, scripts: ['common/yona.Common.js'] });
  await page.addScriptTag({ content: classicFromEsm('esm/service/user.NotificationProjects.js') });
  await page.evaluate(() => { new yona.user.NotificationProjects(); });
  return page;
}
const visible = (page) => page.evaluate(() => [...document.querySelectorAll('#notification-projects li')].filter((e) => !e.hidden).map((e) => e.textContent.trim()));

export const tests = [
  { name: '알림 프로젝트 검색: 입력한 글자가 소유자나 프로젝트 이름에 들어 있는 항목만 남긴다(대소문자 무시)', run: async () => {
    const page = await open();
    await page.fill('#notification-project-filter', 'beta'); assert.deepEqual(await visible(page), ['admin / Beta-Two']);
    await page.fill('#notification-project-filter', 'ORGX'); assert.deepEqual(await visible(page), ['orgx / gamma']);
    await page.fill('#notification-project-filter', 'admin /'); assert.equal((await visible(page)).length, 2); await page.close();
  } },
  { name: '알림 프로젝트 검색: 지우면 모두 다시 보이고, 하나도 없으면 안내를 보인다', run: async () => {
    const page = await open();
    await page.fill('#notification-project-filter', 'zzz'); assert.deepEqual(await visible(page), []); assert.equal(await page.evaluate(() => document.getElementById('notification-project-empty').hidden), false);
    await page.fill('#notification-project-filter', ''); assert.equal((await visible(page)).length, 3); assert.equal(await page.evaluate(() => document.getElementById('notification-project-empty').hidden), true); await page.close();
  } },
  { name: '알림 프로젝트 선택: 목록에서 프로젝트를 고르면 표 위의 제목이 그 프로젝트 이름으로 바뀐다(A-586)', run: async () => {
    const page = await open(); const title = () => page.evaluate(() => document.getElementById('notification-project-title').textContent.trim());
    assert.equal(await title(), 'admin / alpha');
    await page.evaluate(() => { const a = document.querySelectorAll('#notification-projects a')[2]; $(a).trigger('shown.bs.tab'); });
    assert.equal(await title(), 'orgx / gamma'); await page.close();
  } },
];
