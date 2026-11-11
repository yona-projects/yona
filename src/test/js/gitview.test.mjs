// 풀 리퀘스트 보기(`esm/service/git.View.js`)의 상태 갱신 타이머를 확인한다(S-02, A-416).
// 10초마다 서버에서 상태 조각을 받되, 사용자 입력이 60초 동안 없으면 멈추고 입력이 오면 다시 받는다(v1.6 `yona.Interval`의 규칙을 이 화면 안으로 옮겼다).
// 가짜 시계(`page.clock`)로 시간을 흘려 시험한다.
import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const HTML = '<div id="state"></div><div id="merge-box" style="display:inline-block"><div class="btn-group"><button id="btnAccept" style="display:inline-block">accept</button><button id="btnMergeOptions">options</button></div></div><div id="comments"></div>';

/** [pending]이 참이면 응답을 끝내지 않는다(요청이 겹치는지 보려고). */
async function open({ pending = false } = {}) {
  const page = await pageWith({ html: HTML, scripts: ['esm/service/git.View.js'] });
  await page.clock.install();
  await page.evaluate((pending) => {
    window.__gets = []; window.__resp = { html: '<p id="s">A</p>', isConflict: false };
    yona.Files = { getUploader: function () { return null; } };
    // 실제 yona.http처럼 await할 수 있는 네이티브 Promise를 돌려준다(jQuery Deferred는 then에서 setTimeout을 써서 가짜 시계에 묶인다)
    ((window.yona = window.yona || {}).http = (window.yona.http || {})).get = function (url) {
      window.__gets.push(url);
      if (pending) return new Promise(function () {});
      return Promise.resolve(JSON.parse(JSON.stringify(window.__resp)));
    };
    new yona.git.View({ sStateUrl: '/state', bCommentable: false });
  }, pending);
  return page;
}
const gets = (page) => page.evaluate(() => window.__gets.length);
const input = (page, type = 'mousemove') => page.evaluate((t) => window.dispatchEvent(new Event(t)), type);

export const tests = [
  { name: 'yona.Interval 없이 화면이 열린다(이 클래스는 더 이상 없다)', run: async () => {
    const page = await open();
    assert.equal(await page.evaluate(() => typeof yona.Interval), 'undefined');
    assert.deepEqual(page.errors, []);
    await page.close();
  } },
  { name: '10초마다 상태 조각을 받아 온다', run: async () => {
    const page = await open();
    assert.equal(await gets(page), 0);
    await page.clock.runFor(10000); assert.equal(await gets(page), 1);
    await page.clock.runFor(10000); assert.equal(await gets(page), 2);
    assert.equal(await page.evaluate(() => window.__gets[0]), '/state');
    await page.close();
  } },
  { name: '입력이 60초 동안 없으면 멈춘다(10초 간격이면 50초까지 5번)', run: async () => {
    const page = await open();
    await page.clock.runFor(120000);
    assert.equal(await gets(page), 5);
    await page.close();
  } },
  { name: '입력이 있으면 60초 기준이 다시 시작된다', run: async () => {
    const page = await open();
    await page.clock.runFor(50000);
    await input(page, 'mousedown');
    await page.clock.runFor(50000); // 입력 뒤 50초: 아직 60초가 안 지났다
    assert.equal(await gets(page), 10);
    await page.clock.runFor(30000); // 입력 뒤 80초: 60초가 되는 경계 근처라 한 번 더 받을 수 있다
    const atEdge = await gets(page);
    assert.ok(atEdge <= 11, `경계 이후 한 번까지만 허용: ${atEdge}`);
    await page.clock.runFor(60000); // 입력 뒤 140초: 이미 멈춘 상태라 더 늘지 않는다
    assert.equal(await gets(page), atEdge);
    await page.close();
  } },
  { name: '멈춘 뒤 입력이 오면 다시 받아 온다(scroll, mousedown, mousemove, keypress 모두)', run: async () => {
    for (const type of ['scroll', 'mousedown', 'mousemove', 'keypress']) {
      const page = await open();
      await page.clock.runFor(100000);
      const before = await gets(page); assert.equal(before, 5, type);
      await input(page, type);
      await page.clock.runFor(10000);
      assert.equal(await gets(page), before + 1, `${type}: 입력 뒤 10초 안에 다시 받아야 한다`);
      await page.close();
    }
  } },
  { name: '응답이 끝나기 전에는 다음 요청을 겹쳐 보내지 않는다', run: async () => {
    const page = await open({ pending: true });
    await page.clock.runFor(40000);
    assert.equal(await gets(page), 1);
    await page.close();
  } },
  { name: '충돌이면 병합 상자 전체(수락 단추와 방식 드롭다운)를 숨기고 충돌이 풀리면 다시 보인다 (pullrequest-F15)', run: async () => {
    const page = await open();
    await page.evaluate(() => { window.__resp = { html: '<p id="s">C</p>', isConflict: true }; });
    await input(page); await page.clock.runFor(10000);
    assert.equal(await page.evaluate(() => document.getElementById('btnMergeOptions').offsetParent), null, '방식 드롭다운 단추가 보인다');
    await page.evaluate(() => { window.__resp = { html: '<p id="s">D</p>', isConflict: false }; });
    await input(page); await page.clock.runFor(10000);
    assert.equal(await page.evaluate(() => document.getElementById('btnMergeOptions').offsetParent !== null), true);
    assert.equal(await page.evaluate(() => document.getElementById('merge-box').style.display), 'inline-block');
    await page.close();
  } },
  { name: '상태 HTML이 달라졌을 때만 갈아 끼우고, 충돌이면 수락 단추를 숨긴다', run: async () => {
    const page = await open();
    await page.clock.runFor(10000);
    await page.evaluate(() => { document.getElementById('s').dataset.mark = 'kept'; });
    await page.clock.runFor(10000); // 같은 HTML: 그대로
    assert.equal(await page.evaluate(() => document.getElementById('s').dataset.mark), 'kept');
    assert.equal(await page.evaluate(() => document.getElementById('btnAccept').style.display), 'inline-block');
    await page.evaluate(() => { window.__resp = { html: '<p id="s">B</p>', isConflict: true }; });
    await input(page);
    await page.clock.runFor(10000);
    assert.equal(await page.evaluate(() => document.getElementById('s').textContent), 'B');
    assert.equal(await page.evaluate(() => document.getElementById('btnAccept').style.display), 'none');
    await page.close();
  } },
];
