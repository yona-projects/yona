// 데이터 가져오기의 이어 올리기(`esm/service/site.DataImport.js`, X-53, A-597)를 고정한다. 실제 tus 라이브러리 대신 대역을 쓴다.
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const MSG = { content: 'window.Messages = function (k) { return k; };' };
async function open() {
  const page = await pageWith({ stubs: false, html: `<form id="importForm" action="/sites/import" method="post"><input id="file" type="file" name="data"><button id="go" type="submit">go</button><div id="import-progress" hidden><div id="import-bar" style="width:0"></div></div><p id="import-status"></p></form>`, scripts: [MSG, 'common/yona.Common.js'] });
  await page.evaluate(() => {
    window.__uploads = []; window.__posted = []; window.__confirm = true;
    window.confirm = () => window.__confirm;
    window.tus = { isSupported: true, Upload: function (file, options) { this.file = file; this.options = options; this.url = 'http://localhost/ctx/sites/tus/abc'; this.start = () => { window.__started = (window.__started || 0) + 1; }; this.findPreviousUploads = () => Promise.resolve(window.__previous || []); this.resumeFromPreviousUpload = (p) => { window.__resumed = p; }; window.__uploads.push(this); } };
  });
  await page.addScriptTag({ content: classicFromEsm('esm/service/site.DataImport.js') });
  await page.evaluate(() => {
    new yona.site.DataImport({ sURLTus: '/ctx/sites/tus', sURLImport: '/ctx/sites/import/tus', sConfirm: 'Replace all existing data', nChunkSize: 1024,
      fSubmit: (url, data) => window.__posted.push({ url, data }) });
  });
  return page;
}
const pick = (page) => page.setInputFiles('#file', { name: 'data.json', mimeType: 'application/json', buffer: Buffer.from('{}') });

export const tests = [
  { name: '데이터 가져오기: 교체 확인을 취소하면 올리지 않는다', run: async () => {
    const page = await open(); await pick(page); await page.evaluate(() => { window.__confirm = false; }); await page.click('#go');
    assert.equal(await page.evaluate(() => window.__uploads.length), 0); await page.close();
  } },
  { name: '데이터 가져오기: 파일이 없으면 올리지 않는다', run: async () => {
    const page = await open(); await page.click('#go'); assert.equal(await page.evaluate(() => window.__uploads.length), 0); await page.close();
  } },
  { name: '데이터 가져오기: 확인하면 tus로 올린다(주소, 조각 크기, 파일 이름, 단추 잠금)', run: async () => {
    const page = await open(); await pick(page); await page.click('#go');
    const o = await page.evaluate(() => ({ n: window.__uploads.length, ep: window.__uploads[0].options.endpoint, chunk: window.__uploads[0].options.chunkSize, name: window.__uploads[0].options.metadata.filename, started: window.__started, disabled: document.getElementById('go').disabled, shown: !document.getElementById('import-progress').hidden }));
    assert.deepEqual(o, { n: 1, ep: '/ctx/sites/tus', chunk: 1024, name: 'data.json', started: 1, disabled: true, shown: true }); await page.close();
  } },
  { name: '데이터 가져오기: 진행률을 막대와 글자로 보인다', run: async () => {
    const page = await open(); await pick(page); await page.click('#go');
    await page.evaluate(() => window.__uploads[0].options.onProgress(512, 1024));
    assert.equal(await page.evaluate(() => document.getElementById('import-bar').style.width), '50%');
    assert.match(await page.evaluate(() => document.getElementById('import-status').textContent), /50/); await page.close();
  } },
  { name: '데이터 가져오기: 올리기를 마치면 가져오기 요청에 올린 위치를 실어 보낸다(맥락 경로는 뗀다)', run: async () => {
    const page = await open(); await pick(page); await page.click('#go');
    await page.evaluate(() => window.__uploads[0].options.onSuccess());
    assert.deepEqual(await page.evaluate(() => window.__posted), [{ url: '/ctx/sites/import/tus', data: { uploadUri: '/sites/tus/abc' } }]); await page.close();
  } },
  { name: '데이터 가져오기: 실패하면 알리고 단추를 되돌린다', run: async () => {
    const page = await open(); await pick(page); await page.click('#go');
    await page.evaluate(() => window.__uploads[0].options.onError(new Error('boom')));
    assert.equal(await page.evaluate(() => document.getElementById('go').disabled), false);
    assert.match(await page.evaluate(() => document.getElementById('import-status').textContent), /site\.data\.import\.upload\.failed/); await page.close();
  } },
  { name: '데이터 가져오기: 끊긴 올리기가 있으면 이어서 올린다', run: async () => {
    const page = await open(); await page.evaluate(() => { window.__previous = [{ uploadUrl: 'x' }]; }); await pick(page); await page.click('#go'); await page.waitForTimeout(100);
    assert.deepEqual(await page.evaluate(() => window.__resumed), { uploadUrl: 'x' }); assert.equal(await page.evaluate(() => window.__started), 1); await page.close();
  } },
];
