// 위험 구역(A-535, A-537)의 확인 창: 동의 체크박스는 없고 확인 창 안에서 이름을 입력한다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

const BS = path.join(ROOT, 'src/main/resources/static/assets/bootstrap/js/bootstrap.bundle.min.js');
// 위험 구역 확인 창의 이름 재입력(A-536, A-538): 이관, 저장소 종류 변경, 삭제 모두 `owner/name`을 정확히 입력해야 "예"가 활성화된다.
// 서버 요청 계약은 그대로이다.
const CASES = [
  { key: 'Delete', module: 'esm/service/project.Delete.js', opts: { sDeleteURL: '/x/delete', sProjectName: 'tester/proj1' }, btn: 'btnDelete', modal: 'alertDeletion', exec: 'btnDeleteExec', input: 'deleteConfirmName', sent: 'delete /x/delete' },
  { key: 'Transfer', module: 'esm/service/project.Transfer.js', opts: { sTransferURL: '/x/transfer', sProjectName: 'tester/proj1' }, btn: 'btnTransfer', modal: 'alertTransfer', exec: 'btnTransferExec', input: 'transferConfirmName', sent: 'put /x/transfer?owner=bob' },
  { key: 'ChangeVCS', module: 'esm/service/project.ChangeVCS.js', opts: { sTransferURL: '/x/changeVCS', sProjectName: 'tester/proj1', oTexts: {} }, btn: 'btnChangeVCS', modal: 'alertChangeVCS', exec: 'btnChangeVCSExec', input: 'vcsConfirmName', sent: 'post /x/changeVCS' },
];
async function openCase(c) {
  const html = `<input type="text" id="owner" value="bob"><a id="${c.btn}" href="#${c.modal}" data-bs-toggle="modal">단추</a>
<div id="${c.modal}" class="modal" tabindex="-1"><div class="modal-dialog"><div class="modal-content"><input type="text" id="${c.input}"><button id="${c.exec}" type="button" disabled>예</button></div></div></div>`;
  const page = await pageWith({ stubs: false, html, scripts: [{ content: 'window.Messages = function (k) { return k; }; window.__sent = []; window.$yona = { alert: function () {} };' }, { path: BS }, c.module] });
  await page.evaluate((o) => {
    window.yona.Request = { send: function (m, u) { window.__sent.push(m + ' ' + u); return Promise.resolve({ ok: true }); } };
    window.yona.http.ajax = function (u, o2) { window.__sent.push(o2.method + ' ' + u); };
    new window.yona.project[o.key](o.opts);
  }, { key: c.key, opts: c.opts });
  return page;
}
const disabled = (page, id) => page.evaluate((i) => document.getElementById(i).disabled, id);

export const tests = CASES.flatMap((c) => [
  { name: `${c.key}: 이름을 입력하기 전, 일부만 입력, 대소문자가 다를 때는 "예"가 비활성이다`, run: async () => {
    const page = await openCase(c);
    assert.equal(await disabled(page, c.exec), true);
    await page.fill('#' + c.input, 'tester/proj');
    assert.equal(await disabled(page, c.exec), true, '일부만 입력했는데 활성이다');
    await page.fill('#' + c.input, 'tester/PROJ1');
    assert.equal(await disabled(page, c.exec), true, '대소문자가 달라도 활성이다');
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: `${c.key}: owner/name을 정확히 입력하면 "예"가 활성화되고 눌러야 요청이 간다`, run: async () => {
    const page = await openCase(c);
    await page.fill('#' + c.input, 'tester/proj1');
    assert.equal(await disabled(page, c.exec), false);
    assert.deepEqual(await page.evaluate(() => window.__sent), [], '누르기 전에 요청이 갔다');
    await page.click('#' + c.exec); await page.waitForTimeout(200);
    assert.deepEqual(await page.evaluate(() => window.__sent), [c.sent]);
    await page.fill('#' + c.input, 'tester/proj1x');
    assert.equal(await disabled(page, c.exec), true, '다시 틀리게 고치면 비활성으로 돌아와야 한다');
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: `${c.key}: 확인 창을 다시 열면 입력란이 비워지고 "예"가 비활성이다`, run: async () => {
    const page = await openCase(c);
    await page.fill('#' + c.input, 'tester/proj1');
    await page.click('#' + c.btn); await page.waitForTimeout(500);
    assert.equal(await page.inputValue('#' + c.input), '');
    assert.equal(await disabled(page, c.exec), true);
    await page.close();
  } },
]);
