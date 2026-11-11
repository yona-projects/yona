import assert from 'node:assert/strict';
import { pageWith, templateSnippet } from './harness.mjs';

export const tests = [
  { name: '파일 정보 화면에서 파일 크기를 읽기 쉬운 단위로 바꾼다 (C022, C113-1)', async run() {
    const page = await pageWith({
      html: '<div class="file-wrap" data-type="file"><div id="showFile"><span class="filesize">2048</span></div></div>',
      scripts: [{ content: 'window.$yona = window.$yona || {}; $yona.formatFileSize = function (n) { return (n / 1024) + "KB"; };' }, 'esm/service/code.Browser.js']
    });
    const out = await page.evaluate(() => {
      try { new yona.code.Browser({ sProjectName: 'p', sMetaInfoURL: '/m', sBasePathURL: '/b', sInitialPath: '' }); } catch (e) { return 'ERR ' + e.message; }
      return $('.filesize').text();
    });
    await page.close();
    assert.equal(out, '2KB');
  } },
  { name: '대량 메일은 프로젝트를 고르지 않았거나 요청이 실패하면 안내한다 (v1_6은 빈 mailto를 열거나 단추가 처리 중으로 남았다, 고쳤다 site-F9)', async run() {
    const page = await pageWith({
      html: '<div class="mess-mail-wrap"><input type="radio" name="mailingType" value="projects" checked><input id="input-project"><button id="select-project"></button><div id="selected-projects"></div><button id="write-email" data-loading-text="..."></button><div id="project-list-wrap"></div></div>',
      scripts: [{ content: `
        $yona.getTrim = function (s) { return $.trim(s); };
        window.__sent = 0; window.__alerts = [];
        $yona.alert = function (m) { window.__alerts.push(m); };
        $yona.sendForm = function (o) { window.__sent++; setTimeout(function () { o.fOnError({ status: 400 }, 'error', ''); }, 0); };
        yona.ui.Typeahead = function () {};` }, 'esm/service/site.MassMail.js']
    });
    const r = await page.evaluate(async () => {
      new yona.site.MassMail({ sURLMailList: '/m' });
      $('#write-email')[0].click();            // 프로젝트를 고르지 않고 누른다
      const afterEmpty = { sent: window.__sent, alerts: window.__alerts.length };
      $('#selected-projects').append('<span class="badge">없는프로젝트<a>x</a></span>');
      $('#write-email')[0].click();            // 서버가 400을 준다
      await new Promise((r) => setTimeout(r, 30));
      return { afterEmpty, sent: window.__sent, alerts: window.__alerts.length, disabled: $('#write-email').prop('disabled') };
    });
    await page.close();
    assert.deepEqual(r.afterEmpty, { sent: 0, alerts: 1 }, '선택 없이 눌렀는데 요청이 나가거나 안내가 없다');
    assert.equal(r.sent, 1); assert.equal(r.alerts, 2, '실패 안내가 없다'); assert.equal(r.disabled, false, '단추가 처리 중으로 남았다');
  } },
  { name: '대량 메일은 문서에 붙인 폼으로 mailto를 연다 (C026)', async run() {
    const page = await pageWith({
      html: '<div class="mess-mail-wrap"><input type="radio" name="mailingType" value="all" checked><input id="input-project"><button id="select-project"></button><div id="selected-projects"></div><button id="write-email"></button><div id="project-list-wrap"></div></div>',
      scripts: [{ content: `
        $yona.getTrim = function (s) { return $.trim(s); };
        $yona.sendForm = function (o) { setTimeout(function () { o.fOnLoad(['a@x.io', 'b@x.io']); }, 0); };
        yona.ui.Typeahead = function () {};` }, 'esm/service/site.MassMail.js']
    });
    const seen = await page.evaluate(async () => {
      var seen = [];
      HTMLFormElement.prototype.submit = function () { seen.push({ connected: this.isConnected, action: this.getAttribute('action') }); };
      new yona.site.MassMail({ sURLMailList: '/m' });
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
      scripts: [{ content: '$yona.hidePopover = function () {};' }, 'esm/service/project.New.js']
    });
    await page.evaluate(() => { new yona.project.New({ sFormId: '#f' }); $('#menuSettingCode')[0].click(); });
    const errors = page.errors; await page.close();
    assert.deepEqual(errors, [], errors.join(' / '));
  } },
  { name: '프로젝트 생성 화면은 서버가 거절하는 이름(.wiki로 끝남, 대소문자가 다른 예약어)도 보내기 전에 막는다 (E-F13)', async run() {
    const page = await pageWith({
      html: '<form id="f"><input id="project-name" name="name"><input id="project-owner"><input id="url"><input id="vcs"><input id="svn"><input type="checkbox" id="menuSettingCode" checked><input type="checkbox" id="menuSettingPullRequest" checked><input type="checkbox" id="menuSettingReview" checked></form>',
      scripts: [{ content: 'window.__pop = []; $yona.hidePopover = function () {}; $yona.showPopover = function (el, o) { window.__pop.push(String(o && o.content || o)); }; window.NProgress = { start() {} }; window.Messages = function (k) { return k; };' }, 'esm/service/project.New.js']
    });
    const results = await page.evaluate(() => {
      new yona.project.New({ sFormId: '#f' });
      const out = {};
      for (const name of ['demo', 'foo.wiki', 'Foo.WIKI', '.GIT', '..']) {
        window.__pop.length = 0; $('#project-name').val(name); $('#url').val('https://example.com/r.git');
        const e = $.Event('submit'); $('#f').trigger(e); out[name] = e.isDefaultPrevented();
      }
      return out;
    });
    const errors = page.errors; await page.close();
    assert.deepEqual(errors, [], errors.join(' / '));
    assert.deepEqual(results, { demo: false, 'foo.wiki': true, 'Foo.WIKI': true, '.GIT': true, '..': true });
  } },
  { name: '검색어 강조는 amp, lt 같은 키워드가 HTML 엔티티와 겹쳐도 마크업을 깨뜨리지 않는다 (R-50-3)', async run() {
    const code = templateSnippet('search/partial-search.html', 'function encode(text)', '\n});\n</script>');
    async function highlight(keyword, text) {
      const page = await pageWith({ html: '<input id="searchKeyword"><div class="search-result-wrap"><p class="search-content-body"></p><a class="title"></a></div>' });
      const out = await page.evaluate(({ keyword, text, code }) => {
        $('#searchKeyword').val(keyword);
        $('.search-content-body').text(text); $('.title').text(text);
        new Function(code)();
        return { body: $('.search-content-body').html(), title: $('.title').html(), plain: $('.search-content-body').text() };
      }, { keyword, text, code });
      const errors = page.errors; await page.close();
      assert.deepEqual(errors, [], errors.join(' / '));
      return out;
    }
    const amp = await highlight('amp', 'Tom & Jerry <amp> lamp');
    assert.equal(amp.body, 'Tom &amp; Jerry &lt;<strong class="keyword">amp</strong>&gt; l<strong class="keyword">amp</strong>');
    assert.equal(amp.plain, 'Tom & Jerry <amp> lamp', '표시되는 글이 바뀌면 안 된다');
    assert.equal(amp.title, amp.body);
    const lt = await highlight('lt', 'a < b, salt');
    assert.equal(lt.body, 'a &lt; b, sa<strong class="keyword">lt</strong>');
    const plain = await highlight('버그', '로그인 버그 수정 버그');
    assert.equal(plain.body, '로그인 <strong class="keyword">버그</strong> 수정 <strong class="keyword">버그</strong>');
    const special = await highlight('a.b', 'a.b axb');
    assert.equal(special.body, '<strong class="keyword">a.b</strong> axb', '정규식 특수문자는 이스케이프된다');
  } },
];
