// 변경된 내용을 보여 주는 화면(커밋, 비교, 코드 주고받기 변경 내용, Subversion)의 변경 파일 트리(`esm/service/code.DiffTree.js`, A-571)를 검증한다.
// 파일이 둘 이상이면 diff 왼쪽에 경로 트리를 보이고, 파일을 고르면 그 파일의 diff만 보인다.
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const MSG = { content: 'window.Messages = function (k, a) { return a === undefined ? k : k + ":" + a; };' };
const block = (path, i) => `<div class="diff-partial-outer" id="b${i}"><div class="diff-partial-inner"><div class="diff-partial-meta"><div class="diff-partial-file"><span class="filename">${path}</span></div></div>
<div class="diff-partial-code"><table class="diff-container" data-file-path="${path}"><tbody><tr><td>x</td></tr></tbody></table></div></div></div>`;
const PAGE = (paths) => `<div class="code-browse-wrap" id="wrap"><div class="diff-body" id="db">${paths.map(block).join('')}</div></div>`;

async function open(paths) {
  const page = await pageWith({ stubs: false, html: PAGE(paths), scripts: [MSG, 'common/yona.Common.js'] });
  await page.addScriptTag({ content: classicFromEsm('esm/service/code.DiffTree.js') });
  await page.evaluate(() => { new yona.code.DiffTree({ sContainer: '.diff-body' }); });
  await page.waitForTimeout(100);
  return page;
}
const visible = (page) => page.evaluate(() => [...document.querySelectorAll('.diff-partial-outer')].map((e) => getComputedStyle(e).display !== 'none'));

export const tests = [
  { name: '변경 파일 트리: 파일이 둘 이상이면 diff 왼쪽에 경로 트리를 만들고 폴더는 묶어 보인다', run: async () => {
    const page = await open(['docs/guide.md', 'src/Main.kt', 'src/util/Helper.kt']);
    const m = await page.evaluate(() => ({ aside: !!document.querySelector('.diff-tree-layout > aside.diff-tree'), folders: [...document.querySelectorAll('.diff-tree li.folder > .name')].map((e) => e.textContent.trim()), files: [...document.querySelectorAll('.diff-tree li.file > a')].map((e) => e.textContent.trim()), title: document.querySelector('.diff-tree .diff-tree-title').textContent.trim(),
      leftOfBody: document.querySelector('.diff-tree').getBoundingClientRect().right <= document.getElementById('db').getBoundingClientRect().left + 1 }));
    assert.ok(m.aside, '트리 상자가 diff 옆 틀 안에 있어야 한다'); assert.deepEqual(m.folders, ['docs', 'src', 'util']); assert.deepEqual(m.files, ['guide.md', 'Helper.kt', 'Main.kt'], '폴더 안의 파일이 먼저, 그 폴더의 바로 아래 파일이 뒤에 온다');
    assert.equal(m.title, 'code.diff.tree.title:3'); await page.close();
  } },
  { name: '변경 파일 트리: 파일을 고르면 그 파일의 diff만 보이고, 같은 파일을 다시 누르거나 전체 보기를 누르면 모두 보인다', run: async () => {
    const page = await open(['a.txt', 'dir/b.txt', 'dir/c.txt']);
    assert.deepEqual(await visible(page), [true, true, true]);
    const file = (i) => `.diff-tree li.file[data-index="${i}"]`;
    await page.click(file(1)); assert.deepEqual(await visible(page), [false, true, false]);
    assert.deepEqual(await page.evaluate(() => [...document.querySelectorAll('.diff-tree li.file')].map((e) => e.getAttribute('data-index') + ':' + e.classList.contains('active')).sort()), ['0:false', '1:true', '2:false']);
    await page.click(file(2)); assert.deepEqual(await visible(page), [false, false, true]);
    await page.click('.diff-tree .diff-tree-all'); assert.deepEqual(await visible(page), [true, true, true]);
    await page.click(file(0)); await page.click(file(0)); assert.deepEqual(await visible(page), [true, true, true], '같은 파일을 다시 누르면 전체가 보인다');
    await page.close();
  } },
  { name: '변경 파일 트리: 파일이 하나뿐이면 트리를 만들지 않고, 두 번 불러도 한 번만 만든다', run: async () => {
    const one = await open(['only.txt']); assert.equal(await one.evaluate(() => document.querySelectorAll('.diff-tree').length), 0); await one.close();
    const two = await open(['a.txt', 'b.txt']); await two.evaluate(() => { new yona.code.DiffTree({ sContainer: '.diff-body' }); });
    assert.equal(await two.evaluate(() => document.querySelectorAll('.diff-tree').length), 1); await two.close();
  } },
  { name: '변경 파일 트리: diff가 나중에 그려지는 화면(Subversion)은 그려진 뒤에 트리를 만든다', run: async () => {
    const page = await pageWith({ stubs: false, html: '<div class="code-browse-wrap"><div class="diff-body" id="db"></div></div>', scripts: [MSG, 'common/yona.Common.js'] });
    await page.addScriptTag({ content: classicFromEsm('esm/service/code.DiffTree.js') });
    await page.evaluate((h) => { new yona.code.DiffTree({ sContainer: '.diff-body' }); setTimeout(() => { document.getElementById('db').innerHTML = h; }, 250); }, [block('x/a.py', 1), block('y/b.py', 2)].join(''));
    await page.waitForSelector('.diff-tree', { timeout: 3000 }); assert.equal(await page.evaluate(() => document.querySelectorAll('.diff-tree li.file').length), 2); await page.close();
  } },
];
