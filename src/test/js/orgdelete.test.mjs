// 그룹 삭제(`esm/service/organization.Delete.js`, A-578)의 이름 재입력 확인과 삭제 요청, 실패 알림을 고정한다.
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const MSG = { content: 'window.Messages = function (k) { return k; };' };
async function open() {
  const page = await pageWith({ stubs: false, html: `<button id="btnDeleteExec" disabled></button><input id="deleteConfirmName"><div id="alertDeletion"></div>`, scripts: [MSG, 'common/yona.Common.js'] });
  await page.evaluate(() => {
    window.__ajax = []; window.__alerts = []; window.__hidden = [];
    $yona.alert = (m) => window.__alerts.push(m);
    window.bootstrap = { Modal: { getOrCreateInstance: (el) => ({ hide: () => window.__hidden.push(el.id) }) } };
    yona.http = { ajax: (o) => { window.__ajax.push(o); return {}; } };
  });
  await page.addScriptTag({ content: classicFromEsm('esm/service/organization.Delete.js') });
  await page.evaluate(() => { new yona.organization.Delete({ sDeleteURL: '/organizations/orgx', sOrgName: 'orgx', sHomeURL: '/' }); });
  return page;
}

export const tests = [
  { name: '그룹 삭제: 이름을 정확히 입력해야 실행 단추가 켜지고, 확인 창을 열 때마다 입력을 비운다', run: async () => {
    const page = await open(); const disabled = () => page.evaluate(() => document.getElementById('btnDeleteExec').disabled);
    assert.equal(await disabled(), true);
    await page.fill('#deleteConfirmName', 'orgy'); assert.equal(await disabled(), true);
    await page.fill('#deleteConfirmName', 'orgx'); assert.equal(await disabled(), false);
    await page.fill('#deleteConfirmName', 'ORGX'); assert.equal(await disabled(), true, '대소문자까지 같아야 한다');
    await page.fill('#deleteConfirmName', 'orgx');
    await page.evaluate(() => $('#alertDeletion').trigger('show.bs.modal'));
    assert.equal(await page.evaluate(() => document.getElementById('deleteConfirmName').value), ''); assert.equal(await disabled(), true); await page.close();
  } },
  { name: '그룹 삭제: 이름이 맞을 때만 DELETE를 보내고 성공하면 첫 화면으로 간다', run: async () => {
    const page = await open();
    await page.evaluate(() => { $('#btnDeleteExec').prop('disabled', false).trigger('click'); }); assert.equal((await page.evaluate(() => window.__ajax)).length, 0, '이름을 입력하지 않으면 요청하지 않는다');
    await page.fill('#deleteConfirmName', 'orgx'); await page.evaluate(() => $('#btnDeleteExec').trigger('click'));
    const req = (await page.evaluate(() => window.__ajax.map((o) => ({ type: o.type, url: o.url }))))[0]; assert.deepEqual(req, { type: 'delete', url: '/organizations/orgx' }); await page.close();
  } },
  { name: '그룹 삭제: 실패하면 모달을 닫고 서버의 오류 문구(없으면 기본 문구)를 알린다', run: async () => {
    const page = await open(); await page.fill('#deleteConfirmName', 'orgx'); await page.evaluate(() => $('#btnDeleteExec').trigger('click'));
    await page.evaluate(() => window.__ajax[0].error({ responseText: JSON.stringify({ errorMsg: 'organization.delete.impossible.project.exist' }) }));
    await page.evaluate(() => window.__ajax[0].error({ responseText: 'not json' }));
    assert.deepEqual(await page.evaluate(() => window.__alerts), ['organization.delete.impossible.project.exist', 'organization.delete.error']);
    assert.deepEqual(await page.evaluate(() => window.__hidden), ['alertDeletion', 'alertDeletion']); await page.close();
  } },
];
