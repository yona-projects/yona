// 프로필 이미지 자르기를 Jcrop에서 Cropper.js 2로 바꾼다(decisions.md D-101, A-405).
// 실제 cropper.js, 실제 yona.user.Setting.js, 실제 user/edit.html의 자르기 모달 마크업을 불러 이미지를 올리는 것과 같은 순서로 확인한다.
// 동작은 Jcrop 때와 같아야 한다: 1:1, 최소 32px, 초기 선택 128x128, 선택 좌표로 미리보기 갱신, 자른 128x128을 업로드, 모달을 닫으면 정리.
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { pageWith, ROOT, JS } from './harness.mjs';

const TEMPLATE = path.join(ROOT, 'src/main/resources/templates/user/edit.html');
const ASSETS = path.join(ROOT, 'src/main/resources/static/assets');

/** 실제 템플릿의 자르기 모달 마크업(Thymeleaf 속성은 뺀다). */
function cropModalHtml() {
  const text = fs.readFileSync(TEMPLATE, 'utf8');
  const a = text.indexOf('<div id="avatarCropWrap"');
  assert.ok(a >= 0, '템플릿에 #avatarCropWrap이 없다');
  const end = '</div></div></div>';
  const b = text.indexOf(end, a);
  return text.slice(a, b + end.length).replace(/\sth:[a-z]+="[^"]*"/g, '');
}

const PAGE_HTML = () => `
  <form id="frmBasic"></form>
  <form id="frmAvatar" class="avatar-frm"><span class="avatar-wrap"><img src=""></span><button type="button" class="btnUploadAvatar"></button>
    <div class="upload-progress"><div class="bar"></div></div></form>
  <form id="frmPassword"><input id="oldPassword" name="oldPassword"><input id="password" name="password"><input id="retypedPassword" name="retypedPassword"></form>
  <input type="checkbox" class="notiUpdate" data-href="/n">
  ${cropModalHtml()}`;

const STUBS = `
  window.__uploads = []; window.__alerts = [];
  yona.Files = { handlers: {},
    attach: function (h, id) { this.handlers[id || 'default'] = h; },
    getUploader: function () {}, getEnv: function () { return { bXHR2: true }; },
    uploadFile: function (file, id) { window.__uploads.push({ file: file, id: id }); }, deleteFile: function () {} };
  window.FormValidator = function () { this._validateForm = function () {}; this.setMessage = function () {}; };
  $yona.isImageFile = function () { return true; }; $yona.showTab = function () {}; $yona.showPopover = function () {}; $yona.hidePopover = function () {};
  $yona.alert = function (m) { window.__alerts.push(m); };
`;

async function open() {
  const page = await pageWith({
    html: PAGE_HTML(),
    scripts: [{ content: STUBS }, 'lib/cropperjs/cropper.js', 'service/yona.user.Setting.js'],
  });
  await page.evaluate(() => { new yona.user.Setting({}); });
  page.setDefaultTimeout(5000);
  return page;
}

/** 가로 800, 세로 600의 100px 세로 줄무늬 이미지를 data URL로 만든다. [w], [h]로 크기를 바꿀 수 있다. */
const stripedImage = (page, w = 800, h = 600) => page.evaluate(({ w, h }) => {
  const c = document.createElement('canvas'); c.width = w; c.height = h; const x = c.getContext('2d');
  for (let i = 0; i * 100 < w; i++) { x.fillStyle = `hsl(${i * 45},70%,50%)`; x.fillRect(i * 100, 0, 100, h); }
  window.__source = c; return c.toDataURL('image/png');
}, { w, h });

/** 업로드가 끝났다는 신호를 줘서 자르기 모달을 연다. */
async function showCrop(page, url) {
  await page.evaluate((u) => { yona.Files.handlers.default.successUpload({ oRes: { mimeType: 'image/png', url: u, id: 7, size: 1000 } }); }, url);
  await page.waitForSelector('#avatarCropWrap cropper-canvas');
  await page.waitForFunction(() => { const s = document.querySelector('#avatarCropWrap cropper-selection'); return s && s.width > 0; });
}

const selection = (page) => page.evaluate(() => { const s = document.querySelector('#avatarCropWrap cropper-selection'); return { x: s.x, y: s.y, w: s.width, h: s.height }; });

export const tests = [
  {
    name: '템플릿과 파일: Jcrop은 없고 Cropper.js 2가 공식 파일 그대로 있다',
    async run() {
      const html = fs.readFileSync(TEMPLATE, 'utf8');
      assert.ok(!/jcrop/i.test(html), 'user/edit.html에 Jcrop이 남아 있다');
      assert.ok(html.includes('lib/cropperjs/cropper.js'), '템플릿이 cropper.js를 불러오지 않는다');
      assert.ok(!html.includes('<canvas'), '자르기에 쓰던 숨은 canvas가 남아 있다');
      for (const f of ['javascripts/lib/jquery/jquery.Jcrop.min.js', 'stylesheets/jcrop/jquery.Jcrop.css', 'stylesheets/jcrop/Jcrop.gif']) {
        assert.ok(!fs.existsSync(path.join(ASSETS, f)), `${f}가 남아 있다`);
      }
      const file = path.join(JS, 'lib/cropperjs/cropper.js');
      assert.ok(fs.readFileSync(file, 'utf8').startsWith('/*! Cropper.js v2.3.1'), 'Cropper.js 2.3.1이 아니다');
      const sha = crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
      const row = fs.readFileSync(path.join(ROOT, 'src/test/resources/js-vendor-manifest.tsv'), 'utf8').split('\n').find((l) => l.startsWith('cropperjs/cropper.js\t'));
      assert.ok(row, '기준 목록에 cropperjs/cropper.js가 없다');
      assert.equal(row.split('\t')[3], sha);
    },
  },
  {
    name: '이미지를 올리면 자르기 상자가 뜬다: 캔버스는 이미지 표시 크기(최대 500)와 같고 선택은 (0,0)에서 128x128이다',
    async run() {
      const page = await open();
      await showCrop(page, await stripedImage(page));
      const out = await page.evaluate(() => {
        const c = document.querySelector('#avatarCropWrap cropper-canvas'); const r = c.getBoundingClientRect();
        const img = document.querySelector('#avatarCropWrap .modal-body > img');
        return { cw: Math.round(r.width), ch: Math.round(r.height), imgHidden: getComputedStyle(img).display === 'none', modalShown: window.__modal && window.__modal.length };
      });
      assert.deepEqual({ cw: out.cw, ch: out.ch, imgHidden: out.imgHidden }, { cw: 500, ch: 375, imgHidden: true });
      assert.equal(out.modalShown, 1, '모달을 열지 않았다');
      assert.deepEqual(await selection(page), { x: 0, y: 0, w: 128, h: 128 });
      assert.deepEqual(page.errors, []);
    },
  },
  {
    name: '선택을 바꾸면 미리보기가 Jcrop 때와 같은 식으로 갱신된다(128 / 선택 폭 x 표시 폭)',
    async run() {
      const page = await open();
      await showCrop(page, await stripedImage(page));
      await page.evaluate(() => { document.querySelector('#avatarCropWrap cropper-selection').$change(50, 40, 100, 100); });
      await page.waitForTimeout(100);
      const css = await page.evaluate(() => { const el = document.querySelector('#avatarCropWrap .avatar-wrap > img'); return { w: el.style.width, h: el.style.height, ml: el.style.marginLeft, mt: el.style.marginTop }; });
      // nRx = 128 / 100 = 1.28, 표시 크기 500x375
      assert.deepEqual(css, { w: '640px', h: '480px', ml: '-64px', mt: '-51px' });
      assert.deepEqual(page.errors, []);
    },
  },
  {
    name: '비율은 1:1로 유지되고 최소 32px보다 작게는 줄지 않는다',
    async run() {
      const page = await open();
      await showCrop(page, await stripedImage(page));
      const out = await page.evaluate(() => {
        const s = document.querySelector('#avatarCropWrap cropper-selection');
        s.$resize('se-resize', 40, 0);                        // 폭만 늘려도 높이가 따라온다
        const grown = { w: s.width, h: s.height };
        s.$change(10, 10, 100, 100);
        s.$change(10, 10, 20, 20);                            // 최소 32px보다 작게는 바뀌지 않는다
        const kept = { w: s.width, h: s.height };
        s.$change(10, 10, 32, 32);                            // 32는 허용
        return { grown, kept, min: { w: s.width, h: s.height } };
      });
      assert.equal(out.grown.w, out.grown.h, JSON.stringify(out.grown));
      assert.ok(out.grown.w > 128);
      assert.deepEqual(out.kept, { w: 100, h: 100 });
      assert.deepEqual(out.min, { w: 32, h: 32 });
    },
  },
  {
    name: '마우스로 끌어 옮기고, 오른쪽 아래 모서리를 끌어 크기를 바꾸며(1:1), 작아져도 32px 밑으로 내려가지 않는다',
    async run() {
      const page = await open();
      await showCrop(page, await stripedImage(page));
      const box = await page.evaluate(() => { const r = document.querySelector('#avatarCropWrap cropper-canvas').getBoundingClientRect(); return { x: r.x, y: r.y }; });
      // 가운데를 잡고 (+60,+40) 옮긴다
      await page.mouse.move(box.x + 64, box.y + 64); await page.mouse.down(); await page.mouse.move(box.x + 94, box.y + 84, { steps: 4 }); await page.mouse.move(box.x + 124, box.y + 104, { steps: 4 }); await page.mouse.up();
      const moved = await selection(page);
      // 끌기가 시작되는 첫 이동 몇 픽셀을 라이브러리가 흡수할 수 있어 옮긴 거리는 60, 40에서 몇 픽셀 이내로 본다
      assert.ok(Math.abs(moved.x - 60) <= 8 && Math.abs(moved.y - 40) <= 8 && moved.w === 128 && moved.h === 128, JSON.stringify(moved));
      // 오른쪽 아래 모서리를 (+40,+10) 끌면 1:1을 유지한 채 커진다
      await page.mouse.move(box.x + moved.x + moved.w, box.y + moved.y + moved.h); await page.mouse.down(); await page.mouse.move(box.x + moved.x + moved.w + 20, box.y + moved.y + moved.h + 5, { steps: 4 }); await page.mouse.move(box.x + moved.x + moved.w + 40, box.y + moved.y + moved.h + 10, { steps: 4 }); await page.mouse.up();
      const grown = await selection(page);
      assert.equal(grown.w, grown.h, JSON.stringify(grown));
      assert.ok(grown.w > 128, JSON.stringify(grown));
      assert.equal(grown.x, moved.x); assert.equal(grown.y, moved.y);
      // 같은 모서리를 크게 안쪽으로 끌어 줄여도 32px 밑으로는 내려가지 않는다
      const br = { x: box.x + grown.x + grown.w, y: box.y + grown.y + grown.h };
      await page.mouse.move(br.x, br.y); await page.mouse.down(); await page.mouse.move(br.x - 60, br.y - 60, { steps: 4 }); await page.mouse.move(box.x + grown.x + 5, box.y + grown.y + 5, { steps: 6 }); await page.mouse.up();
      const shrunk = await selection(page);
      assert.ok(shrunk.w >= 32 && shrunk.w === shrunk.h, JSON.stringify(shrunk));
      assert.deepEqual(page.errors, []);
    },
  },
  {
    name: '저장을 누르면 선택 부분을 원본 화소로 잘라 128x128 PNG로 avatarCropUpload에 올린다',
    async run() {
      const page = await open();
      await showCrop(page, await stripedImage(page));
      await page.evaluate(() => { document.querySelector('#avatarCropWrap cropper-selection').$change(50, 40, 100, 100); });
      await page.click('#avatarCropWrap .btnSubmitCrop');
      await page.waitForFunction(() => window.__uploads.length > 0);
      const out = await page.evaluate(async () => {
        const u = window.__uploads[0]; const bmp = await createImageBitmap(u.file);
        const c = document.createElement('canvas'); c.width = bmp.width; c.height = bmp.height; const x = c.getContext('2d'); x.drawImage(bmp, 0, 0);
        const px = (ctx, a, b) => Array.from(ctx.getImageData(a, b, 1, 1).data);
        const src = window.__source.getContext('2d');
        // 표시 크기 500x375에서 (50,40,100,100)은 원본(1.6배) 80,64,160,160이다. 가운데 (64,64)는 원본 (160,144), 왼쪽 위 (1,1)은 (81,65)
        return { id: u.id, type: u.file.type, size: [bmp.width, bmp.height], center: px(x, 64, 64), expectedCenter: px(src, 160, 144), corner: px(x, 1, 1), expectedCorner: px(src, 81, 65) };
      });
      assert.equal(out.id, 'avatarCropUpload');
      assert.equal(out.type, 'image/png');
      assert.deepEqual(out.size, [128, 128]);
      assert.deepEqual(out.center, out.expectedCenter);
      assert.deepEqual(out.corner, out.expectedCorner);
      assert.notDeepEqual(out.center, out.corner, '줄무늬가 서로 달라야 자른 위치를 알 수 있다');
    },
  },
  {
    name: '자른 이미지가 올라가면 avatarId를 폼에 넣고 제출한다',
    async run() {
      const page = await open();
      const out = await page.evaluate(() => {
        let submitted = 0; document.getElementById('frmAvatar').submit = function () { submitted++; };
        yona.Files.handlers.avatarCropUpload.successUpload({ oRes: { id: 42 } });
        return { avatarId: document.querySelector('#frmAvatar input[name=avatarId]').value, submitted };
      });
      assert.deepEqual(out, { avatarId: '42', submitted: 1 });
    },
  },
  {
    name: '모달을 닫으면 자르기 상자를 없애고 이미지를 비우며, 다시 열면 새로 만든다',
    async run() {
      const page = await open();
      await showCrop(page, await stripedImage(page));
      await page.evaluate(() => { $('#avatarCropWrap').trigger('hidden.bs.modal'); });
      const closed = await page.evaluate(() => ({ canvases: document.querySelectorAll('#avatarCropWrap cropper-canvas').length, src: document.querySelector('#avatarCropWrap .modal-body > img').getAttribute('src'), previewSrc: document.querySelector('#avatarCropWrap .avatar-wrap > img').getAttribute('src') }));
      assert.deepEqual(closed, { canvases: 0, src: '', previewSrc: '' });
      await showCrop(page, await stripedImage(page, 400, 300));
      const again = await page.evaluate(() => { const r = document.querySelector('#avatarCropWrap cropper-canvas').getBoundingClientRect(); return { n: document.querySelectorAll('#avatarCropWrap cropper-canvas').length, w: Math.round(r.width), h: Math.round(r.height) }; });
      assert.deepEqual(again, { n: 1, w: 400, h: 300 });
      assert.deepEqual(page.errors, []);
    },
  },
  {
    name: '작은 이미지(60x40)에서도 오류 없이 이미지 안에서 선택이 시작된다',
    async run() {
      const page = await open();
      await showCrop(page, await stripedImage(page, 60, 40));
      const sel = await selection(page);
      assert.ok(sel.w > 0 && sel.w === sel.h && sel.w <= 40 && sel.x >= 0 && sel.y >= 0, JSON.stringify(sel));
      assert.deepEqual(page.errors, []);
    },
  },
];
