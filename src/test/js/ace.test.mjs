// 코드 보기의 편집기 Ace(1.45.2, D-104, A-411)가 코드 보기(`yona.code.Browser.js`)가 쓰는 방식 그대로 동작하는지 실제 브라우저에서 확인한다.
// Ace는 모드, 테마, 작업자를 ace.js가 있는 폴더에서 필요할 때 내려받으므로 http 주소가 필요하다. `page.route`로 저장소의 파일을 내려 준다.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { launch, JS } from './harness.mjs';

const ACE = path.join(JS, 'lib/ace');

/** 코드 보기의 확장자 표(`htExtMap`)가 가리키는 모드 이름들. */
function modeNames() {
  const source = fs.readFileSync(path.join(JS, 'service/yona.code.Browser.js'), 'utf8');
  const start = source.indexOf('htVar.htExtMap = htVar.htExtMap || {');
  const block = source.slice(start, source.indexOf('};', start));
  return [...block.matchAll(/^\s*"([a-z0-9_]+)":\s*\[/gm)].map((m) => m[1]);
}

/** Ace 파일을 http로 내려 주는 페이지를 연다. 내려 주지 못한 요청은 `page.missing`에 모인다. */
async function openAce() {
  const browser = await launch();
  const page = await browser.newPage();
  page.errors = [];
  page.missing = [];
  page.on('pageerror', (e) => page.errors.push(String(e.message || e)));
  await page.route('http://ace.test/**', (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/page.html') {
      return route.fulfill({ contentType: 'text/html', body: '<!doctype html><body><div id="showCode" style="width:600px;height:200px"></div><script src="/lib/ace/ace.js"></script></body>' });
    }
    const file = path.join(ACE, url.pathname.replace('/lib/ace/', ''));
    if (url.pathname.startsWith('/lib/ace/') && fs.existsSync(file) && fs.statSync(file).isFile()) {
      return route.fulfill({ contentType: 'text/javascript', body: fs.readFileSync(file) });
    }
    page.missing.push(url.pathname);
    return route.fulfill({ status: 404, body: '' });
  });
  await page.goto('http://ace.test/page.html');
  await page.waitForFunction(() => window.ace && window.ace.edit);
  return page;
}

/** 코드 보기의 `_getEditor`와 같은 설정으로 편집기를 만든다. */
const CREATE_EDITOR = `
  var editor = ace.edit("showCode");
  editor.setTheme("ace/theme/clouds");
  editor.setHighlightActiveLine(false);
  editor.setReadOnly(true);
  editor.setFontSize(13);
  editor.setShowPrintMargin(false);
  window.editor = editor;
  window.session = editor.getSession();
`;

export const tests = [
  {
    name: 'ace.js는 1.45.2이고 코드 보기와 같은 설정(테마 clouds, 읽기 전용)으로 편집기가 만들어진다',
    run: async () => {
      const page = await openAce();
      assert.equal(await page.evaluate(() => ace.version), '1.45.2');
      await page.evaluate(CREATE_EDITOR);
      await page.waitForFunction(() => document.querySelector('#showCode.ace-clouds'), null, { timeout: 5000 });
      assert.equal(await page.evaluate(() => window.editor.getReadOnly()), true);
      assert.deepEqual(page.errors, []);
      assert.deepEqual(page.missing, []);
      await page.close();
    },
  },
  {
    name: '읽기 전용이면 사용자가 키를 눌러도 내용이 바뀌지 않고 setValue로 채운 내용은 그대로 보인다',
    run: async () => {
      const page = await openAce();
      await page.evaluate(CREATE_EDITOR);
      await page.evaluate(() => { window.session.setValue('first line\nsecond line'); window.editor.focus(); });
      await page.locator('#showCode textarea').focus();
      await page.keyboard.type('XYZ');
      await page.keyboard.press('Backspace');
      await page.keyboard.press('Enter');
      assert.equal(await page.evaluate(() => window.session.getValue()), 'first line\nsecond line');
      assert.equal(await page.evaluate(() => window.session.getLength()), 2);
      await page.close();
    },
  },
  {
    name: '확장자 표가 가리키는 모든 모드가 내려받아져 적용된다(없는 파일 요청 0건)',
    run: async () => {
      const modes = modeNames();
      assert.ok(modes.length >= 30, `모드 이름을 읽지 못했다: ${modes}`);
      const page = await openAce();
      await page.evaluate(CREATE_EDITOR);
      const notApplied = [];
      for (const mode of modes) {
        await page.evaluate((m) => window.session.setMode(`ace/mode/${m}`), mode);
        const ok = await page.waitForFunction((m) => window.session.$modeId === `ace/mode/${m}`, mode, { timeout: 8000 }).then(() => true, () => false);
        if (!ok) notApplied.push(mode);
      }
      assert.deepEqual(notApplied, [], '적용되지 않은 모드');
      assert.deepEqual(page.missing, [], '내려 주지 못한 파일');
      assert.deepEqual(page.errors, []);
      await page.close();
    },
  },
  {
    name: '모드를 적용하면 구문이 나뉘어 색이 입혀진다(javascript, python, markdown)',
    run: async () => {
      const page = await openAce();
      await page.evaluate(CREATE_EDITOR);
      const samples = { javascript: 'var a = 1; // note', python: 'def f(x):\n    return x', markdown: '# Title\n\n* item' };
      for (const [mode, code] of Object.entries(samples)) {
        await page.evaluate(([m, c]) => { window.session.setMode(`ace/mode/${m}`); window.session.setValue(c); }, [mode, code]);
        await page.waitForFunction((m) => window.session.$modeId === `ace/mode/${m}`, mode, { timeout: 8000 });
        // 구문 분석은 배경에서 이어서 하므로 토큰이 text가 아닌 것이 나올 때까지 기다린다.
        const colored = await page.waitForFunction(() => {
          for (let i = 0; i < window.session.getLength(); i++) if (window.session.getTokens(i).some((t) => t.type !== 'text')) return true;
          return false;
        }, null, { timeout: 8000 }).then(() => true, () => false);
        assert.ok(colored, `${mode}: 구문 토큰이 없다`);
      }
      assert.deepEqual(page.errors, []);
      await page.close();
    },
  },
  {
    name: '글꼴 크기와 인쇄 여백 설정이 적용된다',
    run: async () => {
      const page = await openAce();
      await page.evaluate(CREATE_EDITOR);
      assert.equal(await page.evaluate(() => window.editor.getOption('showPrintMargin')), false);
      assert.equal(parseInt(await page.evaluate(() => window.editor.getFontSize()), 10), 13);
      assert.equal(await page.evaluate(() => window.editor.getOption('highlightActiveLine')), false);
      await page.close();
    },
  },
];
