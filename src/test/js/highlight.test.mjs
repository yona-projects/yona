// 코드 강조(highlight.js): 마크다운 보기(`yona.Markdown.js`)가 코드 블록에 색을 입힌다. 브라우저 쪽 변환의 `highlight` 옵션(`hljs.highlight`)과 서버가 만든 `<pre><code>`에 쓰는 `highlightBlock`,
// 글에서 쓰이는 언어 이름과 별칭이 팩에 모두 있는지 확인한다. 9.11에서 먼저 통과시키고 11.x로 올려서도 통과시킨다. HL_DIR(팩 폴더)와 HL_MD(Markdown.js)로 다른 파일을 시험할 수 있다.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, JS, classicFromEsm } from './harness.mjs';

const DIR = process.env.HL_DIR || path.join(JS, 'lib/highlight');
const MD = process.env.HL_MD || path.join(JS, 'esm/Markdown.js');
/** 9.11 팩이 알고 있던 언어 이름과 별칭 전체(글의 코드 블록에 이 이름을 쓸 수 있었다). */
const OLD_NAMES = ["YAML", "accesslog", "adoc", "apache", "apacheconf", "arduino", "asciidoc", "atom", "awk", "bash", "basic", "bat", "c", "c++", "cc", "clj", "clojure", "clojure-repl", "cmake", "cmake.in", "cmd", "coffee", "coffeescript", "console", "cpp", "cs", "csharp", "cson", "css", "dart", "diff", "django", "docker", "dockerfile", "dos", "elixir", "elm", "erl", "erlang", "excel", "gemspec", "go", "golang", "gradle", "groovy", "gyp", "h", "h++", "haml", "haskell", "hpp", "hs", "html", "htmlbars", "http", "https", "iced", "ini", "irb", "java", "javascript", "jinja", "js", "json", "jsp", "jsx", "kotlin", "less", "llvm", "lua", "mak", "makefile", "markdown", "matlab", "md", "mk", "mkd", "mkdown", "mm", "nginx", "nginxconf", "obj-c", "objc", "objectivec", "patch", "perl", "php", "php3", "php4", "php5", "php6", "pl", "plist", "pm", "podspec", "powershell", "ps", "py", "python", "r", "rb", "rs", "rss", "ruby", "rust", "scala", "scss", "sh", "shell", "sql", "swift", "tex", "thor", "toml", "ts", "typescript", "xhtml", "xjb", "xls", "xlsx", "xml", "xsd", "xsl", "yaml", "yml", "zsh"];

async function open(html = '') {
  const page = await pageWith({ html, stubs: false, scripts: ['lib/xss.js', 'common/yona.Common.js', 'lib/marked.js', 'lib/marked-yona.js', { path: path.join(DIR, 'highlight.pack.js') }, (MD.includes('/esm/') ? { content: classicFromEsm('esm/Markdown.js') } : { path: MD })] });
  await page.evaluate(() => { window.yona = window.yona || {}; });
  return page;
}

export const tests = [
  { name: '코드 블록을 마크다운으로 바꿀 때 언어 이름이 있으면 색 입힌 토큰(hljs-)이 생긴다', run: async () => {
    const page = await open('<div id="m" markdown>```js\nvar a = 1; // note\n```\n\n```java\npublic class A { int x = 1; }\n```\n\n```python\ndef f(x):\n    return x\n```</div>');
    await page.evaluate(() => { hljs.configure({ languages: [] }); yona.Markdown.init({}); });
    const r = await page.evaluate(() => [...document.querySelectorAll('#m pre code')].map((c) => [c.querySelectorAll('span[class*="hljs-"]').length > 0, c.textContent.trim().split('\n')[0]]));
    assert.deepEqual(r, [[true, 'var a = 1; // note'], [true, 'public class A { int x = 1; }'], [true, 'def f(x):']]); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '언어 이름 대소문자는 가리지 않는다(JS, Java)', run: async () => {
    const page = await open('<div id="m" markdown>```JS\nvar a = 1;\n```\n\n```Java\nint x = 1;\n```</div>');
    await page.evaluate(() => { hljs.configure({ languages: [] }); yona.Markdown.init({}); });
    assert.deepEqual(await page.evaluate(() => [...document.querySelectorAll('#m pre code')].map((c) => c.querySelectorAll('span[class*="hljs-"]').length > 0)), [true, true]); await page.close();
  } },
  { name: '모르는 언어 이름이나 언어 이름이 없는 코드 블록은 오류 없이 색 없이 그대로 나온다(자동 판별을 쓰지 않는다)', run: async () => {
    const page = await open('<div id="m" markdown>```foo\nvar a = 1;\n```\n\n```\nvar b = 2; a &lt; b\n```</div>');
    await page.evaluate(() => { hljs.configure({ languages: [] }); yona.Markdown.init({}); });
    const r = await page.evaluate(() => [...document.querySelectorAll('#m pre code')].map((c) => [c.querySelectorAll('span[class*="hljs-"]').length, c.textContent.trim()]));
    assert.deepEqual(r, [[0, 'var a = 1;'], [0, 'var b = 2; a < b']]); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '서버가 만든 <pre><code class="language-...">는 highlightBlock으로 색이 입혀지고 언어 이름이 없으면 그대로 둔다(렌더 요청의 성공 경로)', run: async () => {
    const page = await open('<div id="t" class="x"></div>');
    await page.route('http://hl.test/md', (route) => route.fulfill({ contentType: 'text/html', headers: { 'access-control-allow-origin': '*' }, body: '<p>a</p><pre><code class="language-java">public class A { int x = 1; }\n</code></pre><pre><code>var plain = 1;\n</code></pre>' }));
    await page.evaluate(() => { hljs.configure({ languages: [] }); yona.Markdown.init({ sMarkdownRendererUrl: 'http://hl.test/md' }); yona.Markdown.render($('#t'), 'x'); });
    await page.waitForFunction(() => document.querySelectorAll('#t pre code').length === 2, null, { timeout: 5000 }); await page.waitForTimeout(400);
    const r = await page.evaluate(() => [...document.querySelectorAll('#t pre code')].map((c) => [c.querySelectorAll('span[class*="hljs-"]').length > 0, c.classList.contains('hljs')]));
    assert.deepEqual(r[0], [true, true], JSON.stringify(r)); assert.equal(r[1][0], false, JSON.stringify(r)); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '옛 팩이 알던 언어 이름과 별칭(126개)이 모두 새 팩에도 있다(없어진 PHP 별칭은 Markdown.js가 다시 등록한다)', run: async () => {
    const page = await open(); await page.evaluate(() => yona.Markdown.init({ aTarget: '#none' })); const missing = await page.evaluate((names) => names.filter((n) => !hljs.getLanguage(n)), OLD_NAMES);
    assert.deepEqual(missing, []); await page.close();
  } },
  { name: '언어마다 대표 코드에 색이 입혀진다(주요 언어 24개)', run: async () => {
    const page = await open(); const samples = { java: 'public class A {}', javascript: 'var a = 1;', typescript: 'let a: number = 1;', python: 'def f(): pass', ruby: 'def f; end', go: 'func main() {}', php: '<?php echo 1; ?>', cpp: 'int main() { return 0; }', cs: 'class A { }', kotlin: 'fun main() {}', swift: 'func f() {}', rust: 'fn main() {}', scala: 'object A', sql: 'select * from t', bash: 'echo $a', json: '{"a": 1}', xml: '<a b="1"/>', html: '<div class="x">y</div>', css: 'a { color: red }', yaml: 'a: 1', dockerfile: 'FROM x', nginx: 'server { listen 80; }', markdown: '# t', diff: '+a\n-b' };
    const out = await page.evaluate((s) => Object.entries(s).filter(([lang, code]) => { try { return ((hljs.versionString || '9').startsWith('9') ? hljs.highlight(lang, code) : hljs.highlight(code, { language: lang })).value.indexOf('<span') < 0; } catch (e) { return true; } }).map(([l]) => l), samples);
    assert.deepEqual(out, []); await page.close();
  } },
];
