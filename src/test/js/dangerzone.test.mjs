// 위험 구역(A-535)의 이관, 저장소 종류 변경, 삭제: 동의 체크박스에 동의하지 않으면 경고만 하고 확인 창을 열지 않으며, 동의한 뒤에만 확인 창을 연다.
// 예전에는 단추가 `data-bs-toggle="modal"`이라 스크립트의 `return false`로 BS5의 모달 열기를 막지 못해, 동의하지 않아도 확인 창이 열렸다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

const BS = path.join(ROOT, 'src/main/resources/static/assets/bootstrap/js/bootstrap.bundle.min.js');
const CASES = [
  { key: 'Transfer', module: 'esm/service/project.Transfer.js', opts: '{ sTransferURL: "/x/transfer" }', accept: 'acceptTransfer', btn: 'btnTransfer', modal: 'alertTransfer', alertKey: 'project.transfer.alert' },
  { key: 'Delete', module: 'esm/service/project.Delete.js', opts: '{ sDeleteURL: "/x/delete" }', accept: 'acceptDelete', btn: 'btnDelete', modal: 'alertDeletion', alertKey: 'project.delete.alert' },
  { key: 'ChangeVCS', module: 'esm/service/project.ChangeVCS.js', opts: '{ sTransferURL: "/x/changeVCS", oTexts: {} }', accept: 'acceptChangeVCS', btn: 'btnChangeVCS', modal: 'alertChangeVCS', alertKey: 'project.changeVCS.alert' },
];
const HTML = (c) => `<input type="text" id="owner"><input type="checkbox" id="${c.accept}"><a id="${c.btn}" href="#${c.modal}">단추</a>
<button id="${c.btn}Exec" type="button">예</button><div id="${c.modal}" class="modal" tabindex="-1"><div class="modal-dialog"><div class="modal-content">확인</div></div></div>`;

async function open(c) {
  const page = await pageWith({ stubs: false, html: HTML(c), scripts: [{ content: 'window.Messages = function (k) { return k; }; window.__alerts = []; window.$yona = { alert: function (m) { window.__alerts.push(m); } };' }, { path: BS }, c.module] });
  await page.evaluate((o) => { new window.yona.project[o.key](eval('(' + o.opts + ')')); }, { key: c.key, opts: c.opts });
  return page;
}
const shown = (page, id) => page.evaluate((i) => document.getElementById(i).classList.contains('show'), id);

export const tests = CASES.flatMap((c) => [
  { name: `${c.key}: 동의하지 않고 단추를 누르면 경고만 하고 확인 창은 열리지 않는다`, run: async () => {
    const page = await open(c);
    await page.click(`#${c.btn}`); await page.waitForTimeout(500);
    assert.deepEqual(await page.evaluate(() => window.__alerts), [c.alertKey], '동의 경고');
    assert.equal(await shown(page, c.modal), false, '동의하지 않았는데 확인 창이 열렸다');
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: `${c.key}: 동의한 뒤 단추를 누르면 확인 창이 열리고 경고는 없다`, run: async () => {
    const page = await open(c);
    await page.check(`#${c.accept}`); await page.click(`#${c.btn}`); await page.waitForTimeout(600);
    assert.equal(await shown(page, c.modal), true, '동의했는데 확인 창이 열리지 않는다');
    assert.deepEqual(await page.evaluate(() => window.__alerts), [], '동의했는데 경고가 나온다');
    assert.deepEqual(page.errors, []); await page.close();
  } },
]);

// 삭제 확인 창의 이름 재입력(A-536): `owner/name`을 정확히 입력해야 "예"가 활성화된다. 서버 요청 계약은 그대로이다.
async function openDelete(name = 'tester/proj1') {
  const html = `<input type="checkbox" id="acceptDelete" checked><a id="btnDelete" href="#alertDeletion">삭제</a>
<div id="alertDeletion" class="modal" tabindex="-1"><div class="modal-dialog"><div class="modal-content"><input type="text" id="deleteConfirmName"><button id="btnDeleteExec" type="button" disabled>예</button></div></div></div>`;
  const page = await pageWith({ stubs: false, html, scripts: [{ content: 'window.Messages = function (k) { return k; }; window.__sent = []; window.$yona = { alert: function () {} };' }, { path: BS }, 'esm/service/project.Delete.js'] });
  await page.evaluate((n) => {
    window.yona.Request = { send: function (m, u) { window.__sent.push(m + ' ' + u); return Promise.resolve({ ok: true }); } };
    new window.yona.project.Delete({ sDeleteURL: '/x/delete', sProjectName: n });
  }, name);
  return page;
}
const disabled = (page) => page.evaluate(() => document.getElementById('btnDeleteExec').disabled);

tests.push(
  { name: 'Delete: 이름을 입력하기 전에는 "예"가 비활성이고, 틀린 이름이어도 비활성이다', run: async () => {
    const page = await openDelete();
    assert.equal(await disabled(page), true);
    await page.fill('#deleteConfirmName', 'tester/proj');
    assert.equal(await disabled(page), true, '일부만 입력했는데 활성이다');
    await page.fill('#deleteConfirmName', 'tester/PROJ1');
    assert.equal(await disabled(page), true, '대소문자가 달라도 활성이다');
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: 'Delete: owner/name을 정확히 입력하면 "예"가 활성화되고 눌러야 삭제 요청이 간다', run: async () => {
    const page = await openDelete();
    await page.fill('#deleteConfirmName', 'tester/proj1');
    assert.equal(await disabled(page), false);
    assert.deepEqual(await page.evaluate(() => window.__sent), [], '누르기 전에 요청이 갔다');
    await page.click('#btnDeleteExec'); await page.waitForTimeout(200);
    assert.deepEqual(await page.evaluate(() => window.__sent), ['delete /x/delete']);
    await page.fill('#deleteConfirmName', 'tester/proj1x');
    assert.equal(await disabled(page), true, '다시 틀리게 고치면 비활성으로 돌아와야 한다');
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: 'Delete: 확인 창을 다시 열면 입력란이 비워지고 "예"가 비활성이다', run: async () => {
    const page = await openDelete();
    await page.fill('#deleteConfirmName', 'tester/proj1');
    await page.click('#btnDelete'); await page.waitForTimeout(500);
    assert.equal(await page.inputValue('#deleteConfirmName'), '');
    assert.equal(await disabled(page), true);
    await page.close();
  } },
);
