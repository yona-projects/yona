// 이슈 일괄 수정(`esm/service/issue.MassUpdate.js`): 체크한 이슈의 라벨로 "라벨 떼기" 목록을 만들 때 분류와 라벨 이름을 글자로만 보이는지 고정한다.
// 분류 이름에 HTML이나 따옴표가 있어도 마크업으로 실행되거나 선택자 오류가 나지 않아야 한다(저장형 XSS, 11번).
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const TPL = `<script type="text/x-yona-template" id="labelListItem">
<li data-value="\${id}" data-category="\${category}"><a><span class="issue-label active list-label" data-label-id="\${id}">\${name}</span></a></li>
</script>`;
const issueLabels = (category, id, name) => `${category},${id},${name},9,false`;
const HTML = (labelsOfIssue1) => `${TPL}
<div class="inner"><input id="check-all" type="checkbox">
<input class="chk" type="checkbox" data-issue-id="1" data-issue-labels="${labelsOfIssue1.replace(/"/g, '&quot;')}">
<div id="st"></div><div id="as"></div><div id="ms"></div>
<div id="al"><button>붙이기</button></div><div id="dl" data-name="detachingLabelIds"><button>떼기</button></div>
<ul id="attach-label-list"><li data-category="Bug" data-value="1">Bug1</li></ul><ul id="delete-label-list"></ul>
<div id="btns"><button>b</button></div><form id="mf"></form><div id="pagination"></div></div>`;

async function open(labels) {
  const page = await pageWith({ stubs: false, html: HTML(labels), scripts: [{ content: 'window.Messages = function (k) { return k; };' }, 'common/yona.Common.js'] });
  await page.evaluate(() => {
    yona.ui = yona.ui || {}; yona.ui.Dropdown = function () { this.onChange = function () {}; };
    yona.ShortcutKey = { attach() {} };
  });
  await page.addScriptTag({ content: classicFromEsm('esm/service/issue.MassUpdate.js') });
  await page.evaluate(() => { new yona.issue.MassUpdate({ sIssueCheckBoxesSelector: '.chk', welState: '#st', welAssignee: '#as', welMilestone: '#ms', welAttachingLabel: $('#al'), welDetachingLabel: $('#dl'), welMassUpdateForm: $('#mf'), welMassUpdateButtons: $('#btns button'), sURL: '/x', nTotalPages: 1 }); });
  return page;
}
const check = (page) => page.evaluate(() => { const c = document.querySelector('.chk'); c.checked = true; $(c).trigger('change'); });
const list = (page) => page.evaluate(() => ({
  html: $('#delete-label-list').html(),
  cats: $('#delete-label-list li.disabled').map((i, e) => [[e.getAttribute('data-category'), e.textContent]]).get(),
  labels: $('#delete-label-list li[data-value]').map((i, e) => [[e.getAttribute('data-value'), e.getAttribute('data-category'), e.textContent.trim()]]).get(),
  imgs: $('#delete-label-list img').length, xss: window.__xss === 1,
}));

export const tests = [
  { name: '일괄 수정: 체크한 이슈의 라벨로 "라벨 떼기" 목록을 분류, 라벨 순서로 만든다', run: async () => {
    const page = await open(issueLabels('Bug', 1, 'Crash') + '|' + issueLabels('Bug', 2, 'UI'));
    await check(page); const r = await list(page);
    assert.deepEqual(r.cats, [['Bug', 'Bug']]); assert.deepEqual(r.labels, [['1', 'Bug', 'Crash'], ['2', 'Bug', 'UI']]); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '일괄 수정: 분류 이름의 HTML은 마크업으로 해석하지 않고 글자로 보인다(저장형 XSS)', run: async () => {
    const page = await open(issueLabels('<img src=x onerror=window.__xss=1>', 7, 'L'));
    await page.evaluate(() => { window.__xss = 0; });
    await check(page); await page.waitForTimeout(100); const r = await list(page);
    assert.equal(r.imgs, 0, '분류 이름의 <img>가 실제 요소가 되었다'); assert.equal(r.xss, false);
    assert.deepEqual(r.cats, [['<img src=x onerror=window.__xss=1>', '<img src=x onerror=window.__xss=1>']]); await page.close();
  } },
  { name: '일괄 수정: 분류 이름에 큰따옴표가 있어도 선택자 오류 없이 목록을 만든다', run: async () => {
    const page = await open(issueLabels('A"B', 3, 'L'));
    await check(page); const r = await list(page);
    assert.deepEqual(page.errors, []); assert.deepEqual(r.cats, [['A"B', 'A"B']]); assert.deepEqual(r.labels, [['3', 'A"B', 'L']]); await page.close();
  } },
  { name: '일괄 수정: 라벨 이름의 HTML도 글자로 보인다', run: async () => {
    const page = await open(issueLabels('Bug', 5, '<b>bold</b>'));
    await check(page); const r = await list(page);
    assert.equal(r.imgs, 0); assert.deepEqual(r.labels, [['5', 'Bug', '<b>bold</b>']]); await page.close();
  } },
];
