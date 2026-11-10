// 조직 설정, 조직 만들기, 조직 보기, 조직 전역, 마일스톤 보기, 하위 작업 보기, 코드 Nohead, 비밀번호 재설정을 고정한다(시험 채우기, D-123).
// 조직 이름 검증과 비밀번호 재설정은 실제 `FormValidator`를 연결해 검증이 동작하는지 함께 확인한다.
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const MSG = { content: 'window.Messages = function (k) { return k; };' };
async function base(html, file, ctor, options, { validator = false } = {}) {
  const page = await pageWith({ stubs: false, html, scripts: [MSG, 'common/yona.Common.js', ...(validator ? ['lib/validate.js'] : [])] });
  await page.evaluate(() => {
    window.__alerts = []; window.__notes = []; window.__ajax = []; window.__modal = []; window.__np = []; window.__pop = []; window.__att = 0;
    $yona.alert = (m) => window.__alerts.push(m); $yona.showAlert = $yona.alert; $yona.notify = (m) => window.__notes.push(m);
    $yona.isImageFile = (el) => /\.(png|jpg)$/i.test(el.data('fake') || '');
    $yona.showPopover = (el, o) => window.__pop.push([el.attr('name'), o.placement, o.content]);
    window.NProgress = { start: () => window.__np.push('start'), done() {} };
    window.bootstrap = { Modal: { getOrCreateInstance: (el) => ({ show: () => window.__modal.push(['show', el.id]), hide: () => window.__modal.push(['hide', el.id]) }) } };
    yona.Attachments = function () { window.__att++; };
    yona.http = { ajax: (url, o) => { window.__ajax.push([url, o]); return {}; } };
  });
  await page.addScriptTag({ content: classicFromEsm('esm/ui/FormValidator.js') });
  await page.addScriptTag({ content: classicFromEsm(file) });
  await page.evaluate(([c, o]) => { new (c.split('.').reduce((a, k) => a[k], window))(o); }, [ctor, options || {}]);
  return page;
}
const get = (page, k) => page.evaluate((k) => window[k], k);

const ORG_FORM = (name) => `<form name="${name}" id="${name}"><input id="name" name="name" value=""><span class="warning">w</span><span class="msg" style="display:none"></span><button type="submit">저장</button></form>`;

export const tests = [
  { name: '조직 만들기: 시작하면 이름 칸에 포커스한다. 이름 규칙에 어긋나면 첫 오류 문구를 .msg에 보이고 제출을 막는다', run: async () => {
    const page = await base(ORG_FORM('new-org'), 'esm/service/organization.New.js', 'yona.organization.New');
    assert.equal(await page.evaluate(() => document.activeElement.id), 'name');
    const r = await page.evaluate(() => { $('#name').val('bad name!'); const e = $.Event('submit'); $('form').trigger(e); return [e.isDefaultPrevented(), $('.msg').text(), $('.msg').css('display'), $('.warning').css('display')]; });
    assert.deepEqual(r, [true, 'organization.name.alert', 'inline', 'none']); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '조직 만들기: 이름 규칙(영숫자, 하이픈, 한글, 중간의 _ 또는 .)을 지키면 NProgress를 시작한다', run: async () => {
    const page = await base(ORG_FORM('new-org'), 'esm/service/organization.New.js', 'yona.organization.New');
    const r = await page.evaluate(() => { const out = []; ['my-org', '한글조직', 'a.b_c', 'a b', '_a', 'a_', ''].forEach((v) => { window.__np.length = 0; $('#name').val(v); $('form').on('submit', (e) => e.preventDefault()).trigger('submit'); out.push([v, window.__np.length > 0]); }); return out; });
    assert.deepEqual(r, [['my-org', true], ['한글조직', true], ['a.b_c', true], ['a b', false], ['_a', false], ['a_', false], ['', false]]); await page.close();
  } },
  { name: '조직 설정: 같은 이름 규칙을 쓰고(폼 이름 update-org), 로고가 이미지가 아니면 알림 후 비운다. 이미지면 폼을 제출한다', run: async () => {
    const page = await base(`<form id="saveSetting" name="update-org"><input id="logoPath" type="file"><input id="name" name="name" value="bad name!"><span class="warning"></span><span class="msg"></span></form>`, 'esm/service/organization.Setting.js', 'yona.organization.Setting');
    const r = await page.evaluate(() => { let sub = 0; $('#saveSetting').on('submit', (e) => { sub++; e.preventDefault(); }); $('#logoPath').data('fake', 'a.txt').trigger('change'); const bad = sub; $('#logoPath').data('fake', 'a.png').trigger('change'); return [bad, sub]; });
    assert.deepEqual(r, [0, 1]); assert.deepEqual(await get(page, '__alerts'), ['project.logo.alert']);
    assert.equal(await page.evaluate(() => { const e = $.Event('submit'); $('#saveSetting').trigger(e); return $('.msg').text(); }), 'organization.name.alert'); await page.close();
  } },

  { name: '조직 보기: 탈퇴 단추는 모달을 열고, 확인하면 DELETE로 data-href를 호출한다', run: async () => {
    const page = await base(`<button id="groupLeaveBtn" data-href="/org/leave"></button><button id="leaveBtn"></button><div id="alertLeave"></div>`, 'esm/service/organization.View.js', 'yona.organization.View');
    await page.evaluate(() => { $('#groupLeaveBtn').trigger('click'); $('#leaveBtn').trigger('click'); });
    assert.deepEqual(await get(page, '__modal'), [['show', 'alertLeave']]); const [url, o] = (await get(page, '__ajax'))[0]; assert.deepEqual([url, o.method, o.dataType], ['/org/leave', 'delete', 'html']); await page.close();
  } },
  { name: '조직 보기: 탈퇴 실패면 모달을 닫고 서버 errorMsg(없으면 알 수 없는 오류)를 알린다', run: async () => {
    const page = await base(`<button id="groupLeaveBtn" data-href="/org/leave"></button><button id="leaveBtn"></button><div id="alertLeave"></div>`, 'esm/service/organization.View.js', 'yona.organization.View');
    await page.evaluate(() => { $('#leaveBtn').trigger('click'); const err = window.__ajax[0][1].error; err({ responseText: '{"errorMsg":"organization.member.leave.last"}' }); err({ responseText: 'oops' }); });
    assert.deepEqual(await get(page, '__notes'), ['organization.member.leave.last', 'organization.member.leave.unknownerror']); assert.deepEqual(await get(page, '__modal'), [['hide', 'alertLeave'], ['hide', 'alertLeave']]); await page.close();
  } },
  { name: '조직 전역: 가입 신청 단추는 href로 POST하고 기본 동작을 막으며, 실패하면 "Server Error"를 알린다', run: async () => {
    const page = await base(`<a id="enrollBtn" href="/org/enroll">e</a>`, 'esm/service/organization.Global.js', 'yona.organization.Global');
    const prevented = await page.evaluate(() => { const e = $.Event('click'); $('#enrollBtn').trigger(e); return e.isDefaultPrevented(); });
    const [url, o] = (await get(page, '__ajax'))[0]; assert.equal(prevented, true); assert.deepEqual([url, o.method], ['/org/enroll', 'post']);
    await page.evaluate(() => { window.__ajax[0][1].error(); }); assert.deepEqual(await get(page, '__notes'), ['Server Error']); await page.close();
  } },

  { name: '마일스톤 보기: 첨부 영역마다 내려받기를 붙이되 이미 붙은 곳은 건너뛴다', run: async () => {
    const page = await base(`<div class="attachments" id="a1"></div><div class="attachments"></div>`, 'esm/service/milestone.View.js', 'yona.milestone.View', {});
    // 생성 전에 표시를 못 하므로 새로 만들어 두 번째 생성 시 건너뜀을 확인한다
    assert.equal(await get(page, '__att'), 2);
    await page.evaluate(() => { $('#a1').data('isYobiAttachment', true); new yona.milestone.View({}); }); assert.equal(await get(page, '__att'), 3); await page.close();
  } },
  { name: '하위 작업 보기: 저장된 값이 true면 체크하고 목록을 보이며, 체크 상태를 바꾸면 값을 저장하고 목록을 보이거나 숨긴다', run: async () => {
    const page = await pageWith({ stubs: false, html: `<input type="checkbox" id="toggle-show-subtasks"><div class="child-issue-list" style="display:none"></div><div class="post-item highlightBg"></div>`, scripts: [MSG, 'common/yona.Common.js'] });
    // about:blank 문서는 localStorage를 막으므로 같은 인터페이스의 가짜 저장소를 둔다
    await page.evaluate(() => { const m = new Map([['showSubtasksAlways', 'true']]); Object.defineProperty(window, 'localStorage', { value: { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)) } }); });
    await page.addScriptTag({ content: classicFromEsm('esm/service/showSubtask.js') });
    assert.deepEqual(await page.evaluate(() => [document.getElementById('toggle-show-subtasks').checked, $('.child-issue-list').css('display')]), [true, 'block']);
    await page.click('#toggle-show-subtasks');
    assert.deepEqual(await page.evaluate(() => [localStorage.getItem('showSubtasksAlways'), $('.child-issue-list').css('display'), $('.post-item').hasClass('highlightBg')]), ['false', 'none', false]);
    await page.click('#toggle-show-subtasks'); assert.deepEqual(await page.evaluate(() => [localStorage.getItem('showSubtasksAlways'), $('.child-issue-list').css('display')]), ['true', 'block']);
    assert.equal(typeof (await page.evaluate(() => typeof window._initShowSubtasks)), 'string'); await page.close();
  } },
  { name: '코드 Nohead: 창이 포커스를 받으면 바로 한 번 확인하고 주기를 시작하며, 포커스를 잃으면 주기를 멈춘다', run: async () => {
    const page = await base('', 'esm/service/code.Nohead.js', 'yona.code.Nohead', { nInterval: 50 });
    await page.evaluate(() => { $(window).trigger('focus'); }); await page.waitForTimeout(180);
    const during = (await get(page, '__ajax')).length; assert.ok(during >= 3, `주기 확인이 ${during}번`); assert.equal((await get(page, '__ajax'))[0][0].url, 'code/HEAD/!/');
    await page.evaluate(() => { $(window).trigger('blur'); }); const at = (await get(page, '__ajax')).length; await page.waitForTimeout(200); assert.equal((await get(page, '__ajax')).length, at); await page.close();
  } },
  { name: '비밀번호 재설정: 비밀번호가 4자 미만이면 말풍선(왼쪽)으로 문구를 보이고, 재입력이 다르면 불일치 문구를 보인다', run: async () => {
    const page = await base(`<form name="passwordReset"><input id="password" name="password"><input id="retypedPassword" name="retypedPassword"></form>`, 'esm/service/resetPassword.js', 'yona.resetPassword');
    await page.evaluate(() => { $('#password').val('abc'); $('#retypedPassword').val('abd'); $('#password').trigger('focusout'); });
    const pops = await get(page, '__pop'); assert.ok(pops.some((p) => p[0] === 'password' && p[1] === 'left' && p[2] === 'validation.tooShortPassword'), JSON.stringify(pops));
    assert.ok(pops.some((p) => p[0] === 'retypedPassword' && p[2] === 'validation.passwordMismatch'), JSON.stringify(pops)); assert.deepEqual(page.errors, []); await page.close();
  } },
];
