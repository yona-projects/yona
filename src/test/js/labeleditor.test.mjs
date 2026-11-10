// 라벨 편집 화면(`esm/service/issue.LabelEditor.js`): 라벨 추가 검증과 요청, 새 분류 확인, 중복 검사, 삭제, 분류와 라벨 수정 요청, 색 입력 처리를 고정한다(시험 채우기, D-123).
// 서버 요청(`yona.http.ajax`)과 알림창, 확인창, Modal, NProgress는 가짜로 바꿔 호출을 기록한다.
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `
<form id="f" action="/labels"><input name="category" value=""><input name="name" value=""><input name="color" value="">
  <div class="label-preset-colors"><button type="button" class="btn-preset-color" style="background-color:#ff0000">r</button><button type="button" class="btn-preset-color" style="background-color:#00ff00">g</button></div>
  <button type="submit">추가</button></form>
<div id="list">
  <div class="category-wrap" data-category-name="Bug"><table><tbody>
    <tr data-label-id="1"><td><span class="issue-label" style="background-color:rgb(0,0,255)" data-label-name="Crash"></span>
      <button class="del" data-delete-uri="/labels/1" data-category-name="Bug" data-label-id="1">삭제</button>
      <button class="upd" data-update-uri="/labels/1" data-label-name="Crash" data-label-color="#0000ff" data-category-id="9">수정</button></td></tr>
  </tbody></table>
  <button class="cat" data-category-update-uri="/cat/9" data-category-id="9" data-category-name="Bug" data-category-is-exclusive="true" data-project-id="4">분류</button></div>
</div>
<div data-category-name="Bug"></div>
<div id="ecat"><input name="name"><select name="isExclusive"><option value="true">t</option><option value="false">f</option></select><button class="btnSubmit">저장</button></div>
<div id="elab"><select name="category.id"><option value="9">Bug</option><option value="10">Doc</option></select><input name="name"><input name="color">
  <div class="label-preset-colors"><button type="button" class="btn-preset-color" style="background-color:#00ff00">g</button></div><button class="btnSubmit">저장</button></div>`;

async function open(opts = {}) {
  const page = await pageWith({ stubs: false, html: HTML, scripts: [{ content: 'window.Messages = function (k) { return k + (arguments.length > 1 ? "|" + [].slice.call(arguments, 1).join(",") : ""); };' }, 'common/yona.Common.js'] });
  await page.evaluate((resp) => {
    window.__ajax = []; window.__alerts = []; window.__confirms = []; window.__modal = []; window.__pop = [];
    yona.ui = yona.ui || {}; yona.ui.Typeahead = function () {};
    window.NProgress = { start() {}, done() {} };
    window.bootstrap = { Modal: { getOrCreateInstance: (el) => ({ show: () => window.__modal.push(['show', el.id]), hide: () => window.__modal.push(['hide', el.id]) }) } };
    $.fn.select2 = function (cmd) { return cmd === 'data' ? [{ text: this.find('option:selected').text() }] : this; };
    $yona.alert = (m, cb) => { window.__alerts.push(m); if (cb) cb(); };
    $yona.confirm = (m, cb, _a, o) => window.__confirms.push({ m, cb, labels: o && o.aButtonLabels });
    $yona.showPopover = (el, o) => window.__pop.push([el.attr('name'), o.content]);
    yona.http = { ajax: (url, o) => { window.__ajax.push([url, o.method, o.data]); const r = resp || {}; const h = { done(cb) { if (r.ok !== undefined) setTimeout(() => cb(r.ok), 0); return h; }, fail(cb) { if (r.fail) setTimeout(() => cb(r.fail), 0); return h; }, always(cb) { setTimeout(cb, 5); return h; } }; return h; } };
  }, opts.resp);
  await page.addScriptTag({ content: classicFromEsm('esm/service/issue.LabelEditor.js') });
  await page.evaluate(() => { new yona.issue.LabelEditor({ list: '#list', form: '#f', editCategoryForm: '#ecat', editLabelForm: '#elab', listURL: '/x' }); });
  return page;
}
const fill = (page, c, n, col) => page.evaluate(([c, n, col]) => { $('#f [name=category]').val(c); $('#f [name=name]').val(n); $('#f [name=color]').val(col); }, [c, n, col]);
const submit = (page) => page.evaluate(() => { $('#f').trigger('submit'); });
const get = (page, k) => page.evaluate((k) => window[k], k);

export const tests = [
  { name: '추가: 분류, 이름, 색 중 하나라도 비어 있으면 알림창을 띄우고 요청하지 않는다', run: async () => {
    const page = await open(); await fill(page, 'Bug', '', '#ff0000'); await submit(page);
    assert.deepEqual(await get(page, '__alerts'), ['label.failedTo|label.add\nlabel.error.empty']); assert.deepEqual(await get(page, '__ajax'), []); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '추가: 색 표현이 틀리면 색 오류 알림창을 띄우고 요청하지 않는다', run: async () => {
    const page = await open(); await fill(page, 'Bug', 'New', 'notacolor'); await submit(page);
    assert.deepEqual(await get(page, '__alerts'), ['label.failedTo|label.add\nlabel.error.color|notacolor']); assert.deepEqual(await get(page, '__ajax'), []); await page.close();
  } },
  { name: '추가: 기존 분류에 새 라벨이면 확인창 없이 이름, 색(# 형식), 분류와 함께 POST한다', run: async () => {
    const page = await open({ resp: { ok: 'not-object' } }); await fill(page, 'Bug', ' New ', '#f00'); await submit(page); await page.waitForTimeout(60);
    const ajax = await get(page, '__ajax'); assert.equal(ajax.length, 1); assert.equal(ajax[0][0], await page.evaluate(() => $('#f').prop('action'))); assert.equal(ajax[0][1], 'post');
    assert.equal(ajax[0][2].labelName, 'New'); assert.equal(ajax[0][2].categoryName, 'Bug'); assert.match(ajax[0][2].labelColor, /^#[0-9a-f]{6}$/i); assert.equal(ajax[0][2].categoryIsExclusive, undefined);
    assert.deepEqual(await get(page, '__confirms'), []); await page.close();
  } },
  { name: '추가: 응답이 객체가 아니면 "생성 실패" 알림창을 띄운다', run: async () => {
    const page = await open({ resp: { ok: 'x' } }); await fill(page, 'Bug', 'New', '#ff0000'); await submit(page); await page.waitForTimeout(60);
    assert.deepEqual(await get(page, '__alerts'), ['label.error.creationFailed']); await page.close();
  } },
  { name: '추가: 이미 있는 이름이면 중복 알림창을 띄우고 요청하지 않는다', run: async () => {
    const page = await open(); await fill(page, 'Bug', 'Crash', '#ff0000'); await submit(page);
    assert.deepEqual(await get(page, '__alerts'), ['label.error.duplicated']); assert.deepEqual(await get(page, '__ajax'), []); await page.close();
  } },
  { name: '추가: 새 분류면 확인창(여러 개/하나만 단추)을 띄우고, 두 번째 단추를 고르면 isExclusive=true로 POST한다', run: async () => {
    const page = await open({ resp: { ok: 'x' } }); await fill(page, 'Brand', 'N', '#ff0000'); await submit(page);
    const c = await get(page, '__confirms'); assert.equal(c.length, 1); assert.deepEqual(c[0].labels, ['label.category.option.multiple', 'label.category.option.single']); assert.deepEqual(await get(page, '__ajax'), []);
    await page.evaluate(() => window.__confirms[0].cb({ nButtonIndex: 1 })); await page.waitForTimeout(60);
    const ajax = await get(page, '__ajax'); assert.equal(ajax.length, 1); assert.equal(ajax[0][2].categoryIsExclusive, true); assert.equal(ajax[0][2].categoryName, 'Brand'); await page.close();
  } },
  { name: '추가: 서버가 JSON 오류를 주면 값들을 줄바꿈으로 이어 알림창에 보이고, 아니면 상태 문구를 보인다', run: async () => {
    let page = await open({ resp: { fail: { responseText: '{"name":"이름 오류","color":"색 오류"}', status: 400, statusText: 'Bad' } } }); await fill(page, 'Bug', 'N', '#ff0000'); await submit(page); await page.waitForTimeout(60);
    assert.deepEqual(await get(page, '__alerts'), ['label.failedTo|label.add\n이름 오류\n색 오류']); await page.close();
    page = await open({ resp: { fail: { responseText: 'oops', status: 500, statusText: 'Err' } } }); await fill(page, 'Bug', 'N', '#ff0000'); await submit(page); await page.waitForTimeout(60);
    assert.deepEqual(await get(page, '__alerts'), ['error.failedTo|label.add,500,Err']); await page.close();
  } },
  { name: '색 입력: 이름 칸에 포커스하면 기존 분류는 그 분류 첫 라벨의 색으로 비어 있던 색 칸을 채운다', run: async () => {
    const page = await open(); await page.evaluate(() => { $('#f [name=category]').val('Bug'); $('#f [name=name]').trigger('focus'); });
    assert.equal(await page.evaluate(() => $('#f [name=color]').val()), '#0000ff'); await page.close();
  } },
  { name: '색 입력: 프리셋 단추를 누르면 색 칸이 그 색이 되고 active가 옮겨 간다', run: async () => {
    const page = await open(); await page.evaluate(() => { $('#f .btn-preset-color').eq(1).trigger('click'); });
    assert.equal(await page.evaluate(() => $('#f [name=color]').val()), '#00ff00'); assert.deepEqual(await page.evaluate(() => $('#f .btn-preset-color').map((i, e) => e.classList.contains('active')).get()), [false, true]); await page.close();
  } },
  { name: '색 입력: 틀린 색을 쓰고 벗어나면 알림창을 띄우고 색 칸에 다시 포커스한다', run: async () => {
    const page = await open(); await page.evaluate(() => { $('#f [name=color]').val('#12').trigger('blur'); });
    assert.deepEqual(await get(page, '__alerts'), ['label.error.color|#12']); await page.close();
  } },
  { name: '삭제: 확인창에서 두 번째 단추를 눌러야 _method=delete로 POST하고, 성공하면 행과 빈 분류가 지워진다', run: async () => {
    const page = await open({ resp: { ok: '' } }); await page.evaluate(() => { $('.del').trigger('click'); });
    assert.equal((await get(page, '__confirms')).length, 1); await page.evaluate(() => window.__confirms[0].cb({ nButtonIndex: 0 })); assert.deepEqual(await get(page, '__ajax'), []);
    await page.evaluate(() => window.__confirms[0].cb({ nButtonIndex: 1 })); await page.waitForTimeout(60);
    assert.deepEqual(await get(page, '__ajax'), [['/labels/1', 'post', { _method: 'delete' }]]);
    assert.equal(await page.locator('tr[data-label-id]').count(), 0); assert.equal(await page.locator('div.category-wrap').count(), 0); await page.close();
  } },
  { name: '분류 수정: 단추를 누르면 이름과 배타 여부를 채우고 모달을 연다. 저장하면 PUT으로 보낸다', run: async () => {
    const page = await open({ resp: { fail: { responseText: '{"x":"e"}', status: 400, statusText: 'B' } } });
    await page.evaluate(() => { $('.cat').trigger('click'); });
    assert.equal(await page.evaluate(() => $('#ecat [name=name]').val()), 'Bug'); assert.equal(await page.evaluate(() => $('#ecat [name=isExclusive]').val()), 'true'); assert.deepEqual((await get(page, '__modal'))[0], ['show', 'ecat']);
    await page.evaluate(() => { $('#ecat [name=name]').val(' Bugs '); $('#ecat .btnSubmit').trigger('click'); }); await page.waitForTimeout(60);
    assert.deepEqual(await get(page, '__ajax'), [['/cat/9', 'put', { id: 9, name: 'Bugs', isExclusive: 'true', 'project.id': 4 }]]);
    assert.deepEqual(await get(page, '__alerts'), ['label.failedTo|label.category.edit\ne']); assert.ok((await get(page, '__modal')).some((m) => m[0] === 'hide')); await page.close();
  } },
  { name: '라벨 수정: 단추를 누르면 이름, 색, 분류를 채우고 모달을 연다. 저장하면 PUT으로 보낸다', run: async () => {
    const page = await open({ resp: {} });
    await page.evaluate(() => { $('.upd').trigger('click'); });
    assert.deepEqual(await page.evaluate(() => [$('#elab [name=name]').val(), $('#elab [name=color]').val(), $('#elab [name="category.id"]').val()]), ['Crash', '#0000ff', '9']);
    await page.evaluate(() => { $('#elab [name=name]').val('Crash2'); $('#elab .btnSubmit').trigger('click'); }); await page.waitForTimeout(40);
    assert.deepEqual(await get(page, '__ajax'), [['/labels/1', 'put', { name: 'Crash2', color: '#0000ff', 'category.id': '9' }]]); await page.close();
  } },
  { name: '라벨 수정: 이름을 바꿔 다른 분류의 같은 이름과 겹치거나 색이 틀리면 말풍선을 띄우고 요청하지 않는다', run: async () => {
    const page = await open({ resp: {} });
    await page.evaluate(() => { $('.upd').trigger('click'); $('#elab [name=name]').val('Crash2'); $('#elab [name="category.id"]').val('9'); });
    await page.evaluate(() => { $('div.category-wrap').append('<i data-label-name="Crash2"></i>'); $('#elab .btnSubmit').trigger('click'); });
    await page.evaluate(() => { $('div.category-wrap i').remove(); $('#elab [name=color]').val('#12'); $('#elab .btnSubmit').trigger('click'); });
    assert.deepEqual(await get(page, '__pop'), [['name', 'label.error.duplicated.in.category|Bug'], ['color', 'label.error.color|#12']]); assert.deepEqual(await get(page, '__ajax'), []); await page.close();
  } },
];
