import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

export const tests = [
  { name: '파일 정보 화면에서 파일 크기를 읽기 쉬운 단위로 바꾼다 (C022, C113-1)', async run() {
    const page = await pageWith({
      html: '<div class="file-wrap" data-type="file"><div id="showFile"><span class="filesize">2048</span></div></div>',
      scripts: [{ content: 'window.humanize = { filesize: function (n) { return (n / 1024) + "KB"; } };' }, 'service/yobi.code.Browser.js']
    });
    const out = await page.evaluate(() => {
      try { new yobi.code.Browser({ sProjectName: 'p', sMetaInfoURL: '/m', sBasePathURL: '/b', sInitialPath: '' }); } catch (e) { return 'ERR ' + e.message; }
      return $('.filesize').text();
    });
    await page.close();
    assert.equal(out, '2KB');
  } },
  { name: '대량 메일은 문서에 붙인 폼으로 mailto를 연다 (C026)', async run() {
    const page = await pageWith({
      html: '<div class="mess-mail-wrap"><input type="radio" name="mailingType" value="all" checked><input id="input-project"><button id="select-project"></button><div id="selected-projects"></div><button id="write-email"></button><div id="project-list-wrap"></div></div>',
      scripts: [{ content: `
        $yobi.getTrim = function (s) { return $.trim(s); };
        $yobi.sendForm = function (o) { setTimeout(function () { o.fOnLoad(['a@x.io', 'b@x.io']); }, 0); };
        yobi.ui.Typeahead = function () {};` }, 'service/yobi.site.MassMail.js']
    });
    const seen = await page.evaluate(async () => {
      var seen = [];
      HTMLFormElement.prototype.submit = function () { seen.push({ connected: this.isConnected, action: this.getAttribute('action') }); };
      new yobi.site.MassMail({ sURLMailList: '/m' });
      $('#write-email')[0].click();
      await new Promise((r) => setTimeout(r, 30));
      return seen;
    });
    const errors = page.errors; await page.close();
    assert.deepEqual(errors, [], errors.join(' / '));
    assert.equal(seen.length, 1);
    // 문서에 붙지 않은 폼은 최신 브라우저가 제출하지 않아 메일 창이 열리지 않았다(C026)
    assert.equal(seen[0].connected, true, '폼이 문서에 붙어 있지 않다');
    assert.equal(seen[0].action, 'mailto:a@x.io,b@x.io,');
  } },
  { name: '프로젝트 생성 화면에서 코드 메뉴를 끄면 오류가 나지 않는다 (C032, R-78)', async run() {
    const page = await pageWith({
      html: '<form id="f"><input id="project-name"><input id="project-owner"><input id="url"><input id="vcs"><input id="svn"><input type="checkbox" id="menuSettingCode" checked><input type="checkbox" id="menuSettingPullRequest" checked><input type="checkbox" id="menuSettingReview" checked></form>',
      scripts: [{ content: '$yobi.hidePopover = function () {};' }, 'service/yobi.project.New.js']
    });
    await page.evaluate(() => { new yobi.project.New({ sFormId: '#f' }); $('#menuSettingCode')[0].click(); });
    const errors = page.errors; await page.close();
    assert.deepEqual(errors, [], errors.join(' / '));
  } },
];
