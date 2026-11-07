import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// jquery.tmpl을 Mustache로 바꾼 것(decisions.md D-94)의 동작 시험이다. 기대값은 바꾸기 전 jquery.tmpl 1.0.0의 실제 출력이다
// (`$.tmpl('<i title="${t}">${t}</i>', …)` 등을 같은 자료로 돌려 얻은 값). `$yona.render`와 `$yona.renderElement`가 같은 DOM을 만들어야 한다.

async function open(html = '') {
  const page = await pageWith({ html, scripts: ['lib/mustache.js', 'common/yona.Common.js'], stubs: false });
  assert.deepEqual(page.errors, []);
  return page;
}

/** 돌려받은 jQuery 객체의 노드를 요소는 outerHTML로, 글자는 값으로 나타낸다. */
const nodes = (page, expression, data) => page.evaluate(([e, d]) => {
  const result = new Function('$yona', 'data', `return ${e};`)($yona, d);
  return result.map(function () { return this.nodeType === 1 ? this.outerHTML : JSON.stringify(this.nodeValue); }).get();
}, [expression, data]);

export const tests = [
  {
    name: 'Mustache 전역과 $yona.render가 있고 jQuery 객체를 돌려준다',
    async run() {
      const page = await open();
      assert.equal(await page.evaluate(() => typeof Mustache.render + typeof $yona.render + typeof $yona.renderElement), 'functionfunctionfunction');
      assert.equal(await page.evaluate(() => $yona.render('<b>{{x}}</b>', { x: 1 }) instanceof $), true);
    },
  },
  {
    name: '값은 jquery.tmpl의 ${}처럼 < > " \' 를 이스케이프하고 &는 그대로 둔다',
    async run() {
      const page = await open();
      const out = await nodes(page, `$yona.render('<i title="{{t}}">{{t}}</i>', data)`, { t: `<a href="x?a=1&b=2">"q" 'r'/\`=` });
      assert.deepEqual(out, [`<i title="&lt;a href=&quot;x?a=1&amp;b=2&quot;&gt;&quot;q&quot; 'r'/\`=">&lt;a href="x?a=1&amp;b=2"&gt;"q" 'r'/\`=</i>`]);
    },
  },
  {
    name: '없는 값과 null과 undefined는 빈 글자이고 0과 false는 그대로 나온다',
    async run() {
      const page = await open();
      assert.deepEqual(await nodes(page, `$yona.render('<i>{{x}}|{{y}}</i>', data)`, { y: 'k' }), ['<i>|k</i>']);
      assert.deepEqual(await nodes(page, `$yona.render('<i>{{a}}|{{b}}|{{c}}|{{d}}</i>', data)`, { a: null, b: 0, c: false }), ['<i>|0|false|</i>']);
    },
  },
  {
    name: '중첩 속성, 앞뒤 공백, 글자만 있는 템플릿, 뿌리가 둘인 템플릿',
    async run() {
      const page = await open();
      assert.deepEqual(await nodes(page, `$yona.render('<i>{{a.b}}</i>', data)`, { a: { b: 'z' } }), ['<i>z</i>']);
      assert.deepEqual(await nodes(page, `$yona.render('\\n   <li>{{n}}</li>\\n', data)`, { n: 1 }), ['<li>1</li>']);
      assert.deepEqual(await nodes(page, `$yona.render('plain {{n}}', data)`, { n: 1 }), ['"plain 1"']);
      assert.deepEqual(await nodes(page, `$yona.render('<a>{{n}}</a><b>{{n}}</b>', data)`, { n: 'm' }), ['<a>m</a>', '<b>m</b>']);
    },
  },
  {
    name: 'renderElement는 요소 안의 템플릿으로 그리고 자료가 없으면 빈 값으로 그린다',
    async run() {
      const page = await open('<script id="t1" type="text/x-mustache">\n  <li class="a" data-n="{{name}}">{{name}} ({{size}})</li>\n</script>');
      assert.deepEqual(await nodes(page, `$yona.renderElement('#t1', data)`, { name: '<x>', size: 3 }), ['<li class="a" data-n="&lt;x&gt;">&lt;x&gt; (3)</li>']);
      assert.deepEqual(await nodes(page, `$yona.renderElement('#t1')`, null), ['<li class="a" data-n=""> ()</li>']);
    },
  },
  {
    name: '없는 요소의 템플릿은 오류 없이 빈 jQuery 객체를 돌려준다(jquery.tmpl의 빈 집합과 같다)',
    async run() {
      const page = await open();
      assert.equal(await page.evaluate(() => $yona.renderElement('#nothing', { a: 1 }).length), 0);
      assert.equal(await page.evaluate(() => $yona.render(undefined).length), 0);
      assert.deepEqual(page.errors, []);
    },
  },
  {
    name: '세 중괄호는 이스케이프하지 않는다',
    async run() {
      const page = await open();
      assert.deepEqual(await nodes(page, `$yona.render('<i>{{{h}}}</i>', data)`, { h: '<b>x</b>' }), ['<i><b>x</b></i>']);
    },
  },
];

// ---- 실제 템플릿 파일의 Mustache 블록(서버가 내려 주는 것과 같다)을 그려 본다

/** 템플릿 파일에서 `<script … id="[id]" type="text/x-mustache">…</script>` 블록의 본문을 꺼낸다. */
function block(file, id) {
  const text = fs.readFileSync(path.join(ROOT, 'src/main/resources/templates', file), 'utf8');
  const m = text.match(new RegExp(`<script[^>]*id="${id}"[^>]*>([\\s\\S]*?)</script>`));
  assert.ok(m, `${file}에 ${id} 블록이 없다`);
  assert.ok(m[0].includes('text/x-mustache'), `${file}의 ${id}는 text/x-mustache여야 한다`);
  return m[1];
}

const FILE = { fileId: 7, fileName: 'a<b>.png', fileHref: '/files/7', fileSize: 10, fileSizeReadable: '10 B', mimeType: 'image/png' };

export const blockTests = [
  ...['common/file-uploader.html', 'issue/view.html', 'board/view.html'].map((file) => ({
    name: `${file}의 tplAttachedFile은 파일 항목을 그린다`,
    async run() {
      const page = await open();
      const tpl = block(file, 'tplAttachedFile');
      const info = await page.evaluate(([t, d]) => {
        const li = $yona.render(t, d).filter('li');
        return { cls: li.attr('class'), name: li.attr('data-name'), href: li.attr('data-href'), mime: li.attr('data-mime'), size: li.attr('data-size'), text: li.text().replace(/\s+/g, ' ').trim() };
      }, [tpl, FILE]);
      assert.equal(info.cls, 'attached-file');
      assert.equal(info.name, 'a<b>.png');
      assert.equal(info.href, '/files/7');
      assert.equal(info.mime, 'image/png');
      assert.equal(info.size, '10');
      assert.match(info.text, /a<b>\.png/);
      assert.match(info.text, /10 B/);
    },
  })),
  {
    name: 'site/user-list.html의 dismissable-alerts는 새 비밀번호를 그린다',
    async run() {
      const page = await open();
      const text = await page.evaluate((t) => $yona.render(t, { newPassword: 'pw<1>' }).text().replace(/\s+/g, ' ').trim(), block('site/user-list.html', 'dismissable-alerts'));
      assert.match(text, /pw<1>/);
    },
  },
  {
    name: 'code/svn-diff.html의 세 템플릿은 요소를 그린다',
    async run() {
      const page = await open();
      const tags = [];
      for (const id of ['comment-icon-template', 'linenum-column-template', 'comment-button-template']) {
        tags.push(await page.evaluate((t) => $yona.render(t, {}).filter('*').prop('tagName'), block('code/svn-diff.html', id)));
      }
      assert.deepEqual(tags, ['I', 'TD', 'BUTTON']);
    },
  },
];
tests.push(...blockTests);
