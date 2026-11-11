// 마일스톤 보기(`esm/service/milestone.View.js`): 열기, 닫기 요청이 실패하면 알린다(milestone-F7, v1.6은 아무 반응이 없었다).
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `<div class="attachments"></div>
<button id="close" type="button" data-request-method="post" data-request-uri="/m/1/close"> 마일스톤 닫기 </button>`;

async function open() {
  const page = await pageWith({ stubs: false, html: HTML, scripts: [{ content: 'window.Messages = function (k) { return k + "|" + [].slice.call(arguments, 1).join(","); };' }, 'common/yona.Common.js'] });
  await page.evaluate(() => { window.__alerts = []; $yona.alert = (m) => window.__alerts.push(m); yona.Attachments = function () {}; });
  await page.addScriptTag({ content: classicFromEsm('esm/service/milestone.View.js') });
  await page.evaluate(() => { new yona.milestone.View({ sMilestoneId: '1', sURLLabels: '/issues' }); });
  return page;
}

export const tests = [
  { name: '열기, 닫기 요청이 실패하면 단추 글자와 상태 코드로 알림창을 띄운다 (v1_6은 아무 반응이 없었다, 고쳤다, milestone-F7)', run: async () => {
    const page = await open();
    await page.evaluate(() => { document.getElementById('close').dispatchEvent(new CustomEvent('request:error', { detail: { status: 404, response: null } })); });
    assert.deepEqual(await page.evaluate(() => window.__alerts), ['error.failedTo|마일스톤 닫기,404,']); await page.close();
  } },
];
