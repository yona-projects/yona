// 대화상자(`esm/ui/Dialog.js`): 기본 확인 단추와 사용자 지정 단추(라벨, 스타일, 기본 강조), 단추 클릭 처리기, 표시/숨김 콜백, 메시지 줄바꿈과 비우기를 고정한다(시험 채우기, D-123).
// 부트스트랩 모달은 show/hide 때 shown/hidden 이벤트를 곧바로 일으키는 가짜로 바꾼다.
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const HTML = `<div id="dlg" class="modal"><div class="msg"></div><div class="desc"></div><div class="buttons"></div></div>`;
async function open(options) {
  const page = await pageWith({ stubs: false, html: HTML, scripts: [{ content: 'window.Messages = function (k) { return k; };' }, 'common/yona.Common.js'] });
  await page.evaluate(() => {
    window.__shown = 0; window.__hidden = 0;
    // 복제된 컨테이너는 문서에 붙지 않으므로 clone 결과를 가로채 시험에서 단추와 메시지를 직접 본다
    const clone = $.fn.clone; $.fn.clone = function () { const r = clone.apply(this, arguments); if (this.is('#dlg')) window.__c = r; return r; };
    window.bootstrap = { Modal: Object.assign(function (el) { this.el = el; }, { getOrCreateInstance: (el) => ({ show: () => { window.__shown++; $(el).trigger('shown.bs.modal'); }, hide: () => { window.__hidden++; $(el).trigger('hidden.bs.modal'); } }) }) };
  });
  await page.addScriptTag({ content: classicFromEsm('esm/ui/Dialog.js') });
  await page.evaluate((o) => { window.__d = new yona.ui.Dialog('#dlg', o); }, options || {});
  return page;
}
const btns = (page) => page.evaluate(() => $(window.__c).find('.buttons').html());

export const tests = [
  { name: '대화상자: 원본 요소를 복제해서 쓰므로 원본은 바뀌지 않는다', run: async () => {
    const page = await open(); await page.evaluate(() => { __d.show('hello', 'desc', {}); });
    assert.equal(await page.evaluate(() => [$('#dlg .msg').text(), $('#dlg .buttons').html()].join('|')), '|'); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '대화상자: 단추 라벨이 없으면 기본 확인 단추 하나를 보여 주고 모달을 연다. 메시지와 설명의 줄바꿈은 <br>로 바뀐다', run: async () => {
    const page = await open(); await page.evaluate(() => { __d.show('a\nb', 'x\ny', {}); });
    assert.equal(await btns(page), '<button type="button" class="ybtn ybtn-info" data-bs-dismiss="modal">button.confirm</button>');
    assert.deepEqual(await page.evaluate(() => [$(window.__c).find('.msg').html(), $(window.__c).find('.desc').html(), window.__shown]), ['a<br>b', 'x<br>y', 1]); await page.close();
  } },
  { name: '대화상자: 사용자 지정 라벨은 단추 순서대로 만들고, 스타일이 없으면 마지막만 primary, 나머지는 default이다', run: async () => {
    const page = await open();
    await page.evaluate(() => { __d.show('m', '', { aButtonLabels: ['아니오', '예'] }); });
    assert.equal(await btns(page), '<button type="button" class="ybtn ybtn-default">아니오</button><button type="button" class="ybtn ybtn-primary">예</button>');
    await page.evaluate(() => { __d.show('m', '', { aButtonLabels: ['A', 'B'], aButtonStyles: ['ybtn-danger', 'ybtn-success'] }); });
    assert.equal(await btns(page), '<button type="button" class="ybtn ybtn-danger">A</button><button type="button" class="ybtn ybtn-success">B</button>'); await page.close();
  } },
  { name: '대화상자: 단추를 누르면 처리기가 단추 번호와 함께 불리고, false를 돌려주면 닫지 않으며 아니면 닫는다', run: async () => {
    const page = await open();
    const r = await page.evaluate(() => { const calls = []; let ret = false; __d.show('m', '', { aButtonLabels: ['no', 'yes'], fOnClickButton: (e) => { calls.push(e.nButtonIndex); return ret ? undefined : false; } });
      $(window.__c).find('button.ybtn').eq(1).trigger('click'); const h1 = window.__hidden; ret = true; $(window.__c).find('button.ybtn').eq(0).trigger('click'); return [calls, h1, window.__hidden]; });
    assert.deepEqual(r, [[1, 0], 0, 1]); await page.close();
  } },
  { name: '대화상자: 표시 뒤 콜백과 숨김 뒤 콜백이 불리고, 숨기면 메시지를 비운다. 마지막 단추에 포커스를 준다(옵션으로 끌 수 있다)', run: async () => {
    const page = await open();
    const r = await page.evaluate(() => { const log = []; document.body.appendChild(window.__c[0]); __d.show('m', '', { aButtonLabels: ['no', 'yes'], fOnAfterShow: () => log.push('show'), fOnAfterHide: () => log.push('hide') }); const focused = document.activeElement.textContent; __d.hide(); return [log, focused, $(window.__c).find('.msg').html()]; });
    assert.deepEqual(r, [['show', 'hide'], 'yes', '']); await page.close();
  } },
  { name: '대화상자: bAutoFocusOnLastButton이 false이면 포커스를 옮기지 않는다', run: async () => {
    const page = await open({ bAutoFocusOnLastButton: false });
    const focused = await page.evaluate(() => { document.body.appendChild(window.__c[0]); __d.show('m', '', { aButtonLabels: ['no', 'yes'] }); return document.activeElement.tagName; });
    assert.equal(focused, 'BODY'); await page.close();
  } },
];
