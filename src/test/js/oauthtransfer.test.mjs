// OAuth 앱 소유자 이전의 확인 절차(`esm/service/oauth.Transfer.js`, A-583)를 고정한다.
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `<form id="oauth-transfer-form"><input type="hidden" name="confirm" value=""><select name="target"><option value="u:1">me</option></select></form><button id="btnTransferExec" disabled></button><input id="transferConfirmName"><div id="alertTransfer"></div>`;
async function open() {
  const page = await pageWith({ stubs: false, html: HTML, scripts: ['common/yona.Common.js'] });
  await page.evaluate(() => { window.__submits = 0; HTMLFormElement.prototype.submit = function () { window.__submits += 1; window.__sent = this.querySelector('[name=confirm]').value; }; });
  await page.addScriptTag({ content: classicFromEsm('esm/service/oauth.Transfer.js') });
  await page.evaluate(() => { new yona.oauth.Transfer({ sAppName: '내 앱' }); });
  return page;
}

export const tests = [
  { name: '앱 이전: 앱 이름을 정확히 입력해야 실행 단추가 켜지고, 확인 창을 열 때마다 비운다', run: async () => {
    const page = await open(); const disabled = () => page.evaluate(() => document.getElementById('btnTransferExec').disabled);
    assert.equal(await disabled(), true);
    await page.fill('#transferConfirmName', '내 앱 '); assert.equal(await disabled(), true); await page.fill('#transferConfirmName', '내 앱'); assert.equal(await disabled(), false);
    await page.evaluate(() => $('#alertTransfer').trigger('show.bs.modal')); assert.equal(await page.evaluate(() => document.getElementById('transferConfirmName').value), ''); assert.equal(await disabled(), true); await page.close();
  } },
  { name: '앱 이전: 이름이 맞을 때만 양식을 보내며 확인 값에 이름을 담는다', run: async () => {
    const page = await open();
    await page.evaluate(() => { $('#btnTransferExec').prop('disabled', false).trigger('click'); }); assert.equal(await page.evaluate(() => window.__submits), 0, '이름 없이는 보내지 않는다');
    await page.fill('#transferConfirmName', '내 앱'); await page.evaluate(() => $('#btnTransferExec').trigger('click'));
    assert.equal(await page.evaluate(() => window.__submits), 1); assert.equal(await page.evaluate(() => window.__sent), '내 앱'); await page.close();
  } },
];
