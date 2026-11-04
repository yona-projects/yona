import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const HTML = '<form><textarea id="t"></textarea><input class="parentCommentId"><div class="notification-receiver-list">기존</div></form>';
const STUBS = { content: 'window.NProgress = { done: function () {} };' };

async function run(response) {
  const page = await pageWith({ html: HTML, scripts: [STUBS, 'common/yona.ReceiverList.js'] });
  const out = await page.evaluate(async (resp) => {
    $.ajax = function () { var d = $.Deferred(); setTimeout(function () { d.resolve(resp); }, 0); return d.promise(); };
    findNotiReceiversHandler($('#t'), '/r');
    await new Promise((r) => setTimeout(r, 50));
    return $('.notification-receiver-list').html();
  }, response);
  const errors = page.errors; await page.close();
  return { out, errors };
}

export const tests = [
  { name: '수신자가 없는 응답({})이어도 오류 없이 기존 표시를 유지한다 (C009)', async run() {
    const r = await run({});
    assert.deepEqual(r.errors, [], r.errors.join(' / ')); assert.equal(r.out, '기존');
  } },
  { name: '응답이 비어 있어도(null) 오류 없이 기존 표시를 유지한다 (C009)', async run() {
    const r = await run(null);
    assert.deepEqual(r.errors, [], r.errors.join(' / ')); assert.equal(r.out, '기존');
  } },
  { name: '수신자가 있으면 이름순으로 표시한다', async run() {
    const r = await run({ receivers: [{ name: 'b', loginId: 'b', pureNameOnly: 'B' }, { name: 'a', loginId: 'a', pureNameOnly: 'A' }] });
    assert.ok(r.out.indexOf('>A<') < r.out.indexOf('>B<'), r.out);
  } },
];
