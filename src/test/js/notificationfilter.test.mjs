// 알림 설정의 프로젝트 검색(`esm/service/user.NotificationFilter.js`, A-582)을 고정한다.
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `<input id="notification-project-filter"><ul id="notification-projects"><li class="active"><a>admin / alpha</a></li><li><a>admin / Beta-Two</a></li><li><a>orgx / gamma</a></li></ul><div id="notification-project-empty" hidden></div>`;
async function open() {
  const page = await pageWith({ stubs: false, html: HTML, scripts: ['common/yona.Common.js'] });
  await page.addScriptTag({ content: classicFromEsm('esm/service/user.NotificationFilter.js') });
  await page.evaluate(() => { new yona.user.NotificationFilter(); });
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
];
