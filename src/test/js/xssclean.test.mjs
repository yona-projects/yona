// `$yona.xssClean`: 브라우저가 표준 Sanitizer API(`Element.setHTML`)를 지원하면 표준으로 정화하고, 지원하지 않으면 옛 정규식 필터(`lib/xss.js`의 `Filter.defence`)를 쓴다(S-12, A-425).
// 표준 정화는 위험한 요소와 속성(script, 이벤트 처리기, javascript: 주소)을 항상 걷어내므로, Yona의 옛 금지 요소 목록(form, iframe, input 등)과 변이 XSS를 일으키는 요소(noscript, math, svg)만 더 걷어낸다.
import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

async function open() { return pageWith({ html: '', scripts: ['lib/xss.js', 'common/yona.Common.js'], stubs: false }); }
const clean = (page, html) => page.evaluate((h) => $yona.xssClean(h), html);

export const tests = [
  { name: 'xssClean(표준): script, 이벤트 처리기, javascript: 주소와 data: 주소를 걷어내고 안전한 내용은 그대로 둔다', run: async () => {
    const page = await open(); assert.equal(await page.evaluate(() => typeof Element.prototype.setHTML), 'function', '이 시험은 표준을 지원하는 브라우저에서 돈다');
    const out = await clean(page, '<p onclick="alert(1)">a<script>alert(1)</script><img src=x onerror=alert(1)><a href="javascript:alert(1)">j</a><a href="https://e.com/?a=1&b=2">ok</a></p>');
    assert.ok(!/<script|onclick|onerror|javascript:/i.test(out), out); assert.ok(out.includes('<a href="https://e.com/?a=1&amp;b=2">ok</a>'), out); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: 'xssClean(표준): 마크다운이 만드는 요소와 속성(이미지, 동영상, details, class, id, 스타일, 너비)은 남는다', run: async () => {
    const page = await open();
    const html = '<h1 id="a">t</h1><p class="c" style="color:red" align="center"><img class="emoji" src="https://e.com/a.png" width="10" height="5" alt="x" title="t"><video src="/files/1" controls width="320"></video></p><details><summary>s</summary>b</details><pre><code class="js">var a = 1 &lt; 2;</code></pre><table><tbody><tr><td>1</td></tr></tbody></table><kbd>k</kbd><a id="x" name="y" href="#z" target="_blank">z</a>';
    const out = await clean(page, html);
    for (const part of ['<h1 id="a">', 'class="c"', 'style="color:red"', 'align="center"', '<img class="emoji" src="https://e.com/a.png" width="10" height="5" alt="x" title="t">', '<video src="/files/1" controls="" width="320">', '<details><summary>s</summary>b</details>', '<code class="js">var a = 1 &lt; 2;</code>', '<td>1</td>', '<kbd>k</kbd>', 'id="x"', 'name="y"', 'target="_blank"'])
      assert.ok(out.includes(part), `${part} 가 없다: ${out}`);
    await page.close();
  } },
  { name: 'xssClean(표준): 금지 요소(form, iframe, textarea, style, link, meta, base, object, embed, audio, title)를 그대로 쓰면 요소는 만들어지지 않는다(글자로 보이지는 않는다)', run: async () => {
    const page = await open();
    for (const tag of ['form', 'iframe', 'textarea', 'style', 'link', 'meta', 'base', 'object', 'embed', 'audio', 'title']) {
      const found = await page.evaluate(([h, t]) => { const d = document.createElement('div'); d.innerHTML = $yona.xssClean(h); return !!d.querySelector(t); }, [`<p>a</p><${tag} src="https://e.com" href="https://e.com">x</${tag}><p>b</p>`, tag]);
      assert.equal(found, false, tag);
    }
    await page.close();
  } },
  { name: 'xssClean(표준): 이스케이프해서 쓴 &lt;form&gt;과 코드 블록 안의 form은 글자로 보이고, 이중 이스케이프는 한 번만 풀린다', run: async () => {
    const page = await open();
    const text = (html) => page.evaluate((h) => { const d = document.createElement('div'); d.innerHTML = $yona.xssClean(h); return d.textContent; }, html);
    assert.equal(await text('<p>&lt;form action="x"&gt;로그인&lt;/form&gt; &lt;script&gt;alert(1)&lt;/script&gt;</p>'), '<form action="x">로그인</form> <script>alert(1)</script>');
    assert.equal(await text('<pre><code>&lt;form method="post"&gt;\n  &lt;input name="q"&gt;\n&lt;/form&gt;</code></pre>'), '<form method="post">\n  <input name="q">\n</form>');
    assert.equal(await text('<p>&amp;lt;form&amp;gt;</p>'), '&lt;form&gt;'); await page.close();
  } },
  { name: 'xssClean(표준): 변이 XSS를 일으키는 noscript, math, svg와 style 변이는 사라지고, 결과를 다시 읽어도 실행되는 것이 없다', run: async () => {
    const page = await open();
    for (const payload of ['<noscript><p title="</noscript><img src=x onerror=alert(1)>">', '<math><mtext><table><mglyph><style><!--</style><img title="--&gt;&lt;img src=1 onerror=alert(1)&gt;">', '<svg onload=alert(1)><a xlink:href="javascript:alert(1)"><text>x</text></a></svg>']) {
      const out = await clean(page, payload);
      const risky = await page.evaluate((h) => { const d = document.createElement('div'); d.innerHTML = h; return [...d.querySelectorAll('*')].filter((e) => /^(svg|math|noscript|style|script)$/i.test(e.tagName) || [...e.attributes].some((a) => /^on/i.test(a.name))).map((e) => e.tagName); }, out);
      assert.deepEqual(risky, [], `${payload} → ${out}`);
    }
    await page.close();
  } },
  { name: 'xssClean(표준): 코드 글자는 망가뜨리지 않는다(옛 필터는 document.cookie, javascript:, eval(...)을 지우거나 바꿨다)', run: async () => {
    const page = await open();
    const out = await clean(page, '<pre><code>document.cookie = "a"; var u = "javascript:void(0)"; eval(x);</code></pre>');
    assert.ok(out.includes('document.cookie') && out.includes('javascript:void(0)') && out.includes('eval(x)'), out); await page.close();
  } },
  { name: 'xssClean(표준): 마크다운이 그리는 작업 목록 체크 상자(input type=checkbox)는 남긴다. 서버의 v1.6 정책도 input의 type, disabled, checked를 허용한다', run: async () => {
    const page = await open();
    const out = await clean(page, `<ul><li class='task-list-item'><input class='task-list-item-checkbox' checked="" type="checkbox"> 끝</li><li class="task-list-item"><input class='task-list-item-checkbox' type="checkbox" disabled> 할 일</li></ul>`);
    assert.ok(out.includes(`<input class="task-list-item-checkbox" checked="" type="checkbox"> 끝`) && out.includes(`<input class="task-list-item-checkbox" type="checkbox" disabled=""> 할 일`), out); await page.close();
  } },
  { name: 'xssClean(표준): 체크 상자가 아닌 input은 사라지고, 체크 상자는 type, checked, disabled, class 같은 허용 속성만 남는다(가짜 로그인 입력란 같은 것을 막는다)', run: async () => {
    const page = await open();
    for (const html of ['<input type="text" name="q" value="x">', '<input name="q">', '<input type="password">', '<input type="image" src="https://e.com/a.png">', '<input type="hidden" name="a" value="b">'])
      assert.ok(!/<input/i.test(await clean(page, html)) || /&lt;input/i.test(await clean(page, html)) && !(await page.evaluate((h) => { const d = document.createElement('div'); d.innerHTML = $yona.xssClean(h); return !!d.querySelector('input'); }, html)), html);
    const out = await clean(page, '<input type="CHECKBOX" name="n" value="v" form="f" formaction="https://e.com" autofocus readonly class="c" onclick="alert(1)">');
    assert.equal(out, '<input type="CHECKBOX" class="c">'); await page.close();
  } },
  { name: 'xssClean(표준): 링크의 data: 주소는 걷어내고(옛 필터도 막았다) 이미지의 data: 주소는 남긴다', run: async () => {
    const page = await open();
    const link = await clean(page, '<a href="data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==">x</a><a href=" DATA:text/html,<b>x</b>">y</a>');
    assert.ok(!/href=/i.test(link), link); assert.ok(link.includes('>x</a>') && link.includes('>y</a>'), '글자는 남는다: ' + link);
    const img = await clean(page, '<img src="data:image/png;base64,iVBORw0KGgo=" alt="i">'); assert.ok(img.includes('src="data:image/png;base64,iVBORw0KGgo="'), img); await page.close();
  } },
  { name: 'xssClean(표준): 값이 없거나 글자가 아니어도 오류 없이 글자를 돌려준다', run: async () => {
    const page = await open();
    assert.equal(await clean(page, null), ''); assert.equal(await clean(page, undefined), ''); assert.equal(await page.evaluate(() => $yona.xssClean(12)), '12'); assert.equal(await clean(page, ''), ''); await page.close();
  } },
  { name: 'xssClean(대체 경로): 브라우저가 표준을 지원하지 않으면 옛 필터로 정화한다(form은 글자로 바뀌고 script와 처리기는 사라진다)', run: async () => {
    const page = await open();
    await page.evaluate(() => { delete Element.prototype.setHTML; });
    const out = await clean(page, '<p onclick="alert(1)">a</p><script>alert(1)</script><form action="x">f</form>');
    assert.ok(!/<script|onclick/i.test(out), out); assert.ok(out.includes('&lt;form'), '옛 필터는 금지 요소를 글자로 바꾼다: ' + out);
    assert.equal(await page.evaluate(() => $yona.xssClean(null)), '', '값이 없으면 빈 글자'); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: 'xssClean(대체 경로): Sanitizer 생성이 실패해도(설정을 거부하는 브라우저) 오류 없이 옛 필터로 정화한다', run: async () => {
    const page = await open();
    await page.evaluate(() => { window.Sanitizer = function () { throw new TypeError('거부'); }; });
    const out = await clean(page, '<script>alert(1)</script><p>ok</p>'); assert.ok(!/<script/i.test(out) && out.includes('<p>ok</p>'), out); assert.deepEqual(page.errors, []); await page.close();
  } },
];
