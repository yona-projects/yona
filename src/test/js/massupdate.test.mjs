// 이슈 일괄 수정(`esm/service/issue.MassUpdate.js`): 체크한 이슈의 라벨로 "라벨 떼기" 목록을 만들 때 분류와 라벨 이름을 글자로만 보이는지 고정한다.
// 분류 이름에 HTML이나 따옴표가 있어도 마크업으로 실행되거나 선택자 오류가 나지 않아야 한다(저장형 XSS, 11번).
import assert from 'node:assert/strict';
import { pageWith, classicFromEsm } from './harness.mjs';

const TPL = `<script type="text/x-yona-template" id="labelListItem">
<li data-value="\${id}" data-category="\${category}"><a><span class="issue-label active list-label" data-label-id="\${id}">\${name}</span></a></li>
</script>`;
const issueLabels = (category, id, name) => `${category},${id},${name},9,false`;
const HTML = (labelsOfIssue1, sticky = false, state = 'open') => `${TPL}
${sticky ? '<style>body{margin:0}.filter-wrap.mass-update-sticky{position:sticky;top:0;height:30px;background:#fff}</style><div style="height:300px">위 여백</div><div class="filter-wrap mass-update-sticky">막대</div><div style="height:3000px">긴 목록</div>' : ''}<div class="inner">
<div class="mass-update-wrap"><form id="mass-form-bar"><input class="chk" type="checkbox" data-issue-id="1" data-issue-state="${state}" data-issue-labels="${labelsOfIssue1.replace(/"/g, '&quot;')}"><div class="mass-actions"><span class="mass-selected"><b class="mass-count">0</b>개 선택됨</span><button type="button" id="mass-close" data-mass-state="CLOSED" hidden>닫기</button><button type="button" id="mass-reopen" data-mass-state="OPEN" hidden>다시 열기</button><a href="#" id="mass-all" data-mass-select-all>모두 선택</a><a href="#" id="mass-clear" data-mass-clear>선택 해제</a></div></form></div><input class="chk" type="checkbox" data-issue-id="2" data-issue-state="open" data-issue-labels="">
<div id="st"></div><div id="as"></div><div id="ms"></div>
<div id="al"><button>붙이기</button></div><div id="dl" data-name="detachingLabelIds"><button>떼기</button></div>
<ul id="attach-label-list"><li data-category="Bug" data-value="1">Bug1</li></ul><ul id="delete-label-list"></ul>
<div id="btns"><button>b</button></div><form id="mf"></form><div id="pagination"></div></div>`;

async function open(labels, sticky = false, state = 'open') {
  const page = await pageWith({ stubs: false, html: HTML(labels, sticky, state), scripts: [{ content: 'window.Messages = function (k) { return k; };' }, 'common/yona.Common.js'] });
  await page.evaluate(() => {
    yona.ui = yona.ui || {}; yona.ui.Dropdown = function () { this.onChange = function () {}; };
    yona.ShortcutKey = { attach(k, f) { if (k === 'CTRL+A') window.__ctrlA = f; } };
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
  { name: '붙는 막대: 스크롤해서 막대가 화면 위에 닿으면 stuck을 달고, 올라오면 뗀다(경계 포함, 스크롤 위치별로 getBoundingClientRect().top <= 0과 같다)', run: async () => {
    const page = await open(issueLabels('Bug', 1, 'x'), true);
    const out = [];
    for (const y of [0, 100, 250, 298, 299, 300, 301, 302, 400, 1000, 300, 250, 0]) {
      await page.evaluate((y) => window.scrollTo(0, y), y);
      await page.waitForFunction(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(() => r(true)))), null, { timeout: 3000 });
      await page.waitForTimeout(60);
      out.push(await page.evaluate((y) => { const row = document.querySelector('.filter-wrap.mass-update-sticky'); return [y, row.classList.contains('stuck'), row.getBoundingClientRect().top <= 0]; }, y));
    }
    // 막대의 stuck 클래스는 padding을 바꿔 높이가 달라지므로, 판정 기준은 "붙기 전 자연스러운 위치"가 화면 위에 닿았는가이다. 자연 위치 300px 근처 한 칸(1px) 차이는 허용한다
    const mismatches = out.filter(([y, stuck]) => stuck !== (y >= 300));
    assert.deepEqual(mismatches, [], JSON.stringify(out)); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '붙는 막대: 창 크기가 바뀌어 막대가 이미 위에 붙어 있게 되면 stuck을 단다', run: async () => {
    const page = await open(issueLabels('Bug', 1, 'x'), true);
    await page.evaluate(() => window.scrollTo(0, 1000)); await page.waitForTimeout(150);
    assert.equal(await page.evaluate(() => document.querySelector('.filter-wrap.mass-update-sticky').classList.contains('stuck')), true);
    await page.setViewportSize({ width: 700, height: 500 }); await page.waitForTimeout(150);
    assert.equal(await page.evaluate(() => document.querySelector('.filter-wrap.mass-update-sticky').classList.contains('stuck')), true); await page.close();
  } },
  { name: '일괄 수정: 라벨이 없는 이슈를 골라도 프로젝트에 붙일 라벨이 있으면 "라벨 추가"가 켜진다(옛 수정이 선택한 이슈의 라벨 분류만 훑어 꺼 두던 회귀)', run: async () => {
    const page = await open('');
    await check(page);
    assert.equal(await page.evaluate(() => $('#al button').prop('disabled')), false, '붙일 라벨이 있는데 라벨 추가가 꺼져 있다');
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '일괄 수정: 선택한 이슈가 프로젝트의 모든 라벨을 이미 가지면 "라벨 추가"는 꺼진다', run: async () => {
    const page = await open(issueLabels('Bug', 1, 'Bug1'));
    await check(page);
    assert.equal(await page.evaluate(() => $('#al button').prop('disabled')), true, '붙일 라벨이 없는데 라벨 추가가 켜져 있다');
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '일괄 수정: 이슈를 하나도 고르지 않으면 도구줄(has-selection)이 없고, 고르면 생기며 "N개 선택됨"이 맞게 나온다', run: async () => {
    const page = await open('');
    assert.equal(await page.evaluate(() => $('.mass-update-wrap').hasClass('has-selection')), false, '선택 전에는 has-selection이 없어야 한다');
    await check(page);
    assert.equal(await page.evaluate(() => $('.mass-update-wrap').hasClass('has-selection')), true);
    assert.equal(await page.evaluate(() => $('.mass-count').text()), '1');
    await page.evaluate(() => { const c = document.querySelector('.chk'); c.checked = false; $(c).trigger('change'); });
    assert.equal(await page.evaluate(() => $('.mass-update-wrap').hasClass('has-selection')), false, '해제하면 다시 사라진다');
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '일괄 수정: 열린 이슈를 고르면 "닫기"만, 닫힌 이슈를 고르면 "다시 열기"만 보인다', run: async () => {
    const vis = (page) => page.evaluate(() => ({ close: !document.getElementById('mass-close').hidden, reopen: !document.getElementById('mass-reopen').hidden }));
    let page = await open('', false, 'open'); await check(page);
    assert.deepEqual(await vis(page), { close: true, reopen: false }); await page.close();
    page = await open('', false, 'closed'); await check(page);
    assert.deepEqual(await vis(page), { close: false, reopen: true }); assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '일괄 수정: "닫기" 단추를 누르면 선택한 이슈와 state=CLOSED를 담아 제출한다', run: async () => {
    const page = await open(''); await check(page);
    await page.evaluate(() => { window.__sub = null; const f = document.getElementById('mf'); f.submit = function () { window.__sub = $(this).serializeArray(); }; });
    await page.evaluate(() => $('#mass-close').trigger('click'));
    const sub = await page.evaluate(() => window.__sub);
    assert.ok(sub, '제출되지 않았다');
    assert.deepEqual(sub.filter((x) => x.name === 'state').map((x) => x.value), ['CLOSED']);
    assert.deepEqual(sub.filter((x) => x.name === 'issues[0].id').map((x) => x.value), ['1']);
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '일괄 수정: "선택 해제"를 누르면 모든 선택이 풀리고 도구줄이 사라진다', run: async () => {
    const page = await open(''); await check(page);
    await page.evaluate(() => $('#mass-clear').trigger('click'));
    assert.equal(await page.evaluate(() => document.querySelector('.chk').checked), false);
    assert.equal(await page.evaluate(() => $('.mass-update-wrap').hasClass('has-selection')), false);
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '일괄 수정: Ctrl+A는 전체 선택 체크박스 없이 모든 이슈를 고른다', run: async () => {
    const page = await open('');
    assert.equal(await page.evaluate(() => $('#check-all').length), 0, '틀에 전체 선택 체크박스가 있다');
    await page.evaluate(() => window.__ctrlA({ bFormInput: false, weEvt: { preventDefault() {} } }));
    assert.deepEqual(await page.evaluate(() => $('.chk').map((i, e) => e.checked).get()), [true, true]);
    assert.equal(await page.evaluate(() => $('.mass-count').text()), '2');
    assert.deepEqual(page.errors, []); await page.close();
  } },
  { name: '일괄 수정: "모두 선택" 링크는 모든 이슈를 고르고, 이미 모두 골랐으면 숨는다', run: async () => {
    const page = await open('');
    await page.evaluate(() => { const c = $('.chk')[0]; c.checked = true; $(c).trigger('change'); });
    assert.equal(await page.evaluate(() => document.getElementById('mass-all').hidden), false, '일부만 골랐는데 모두 선택이 숨어 있다');
    await page.evaluate(() => $('#mass-all').trigger('click'));
    assert.deepEqual(await page.evaluate(() => $('.chk').map((i, e) => e.checked).get()), [true, true]);
    assert.equal(await page.evaluate(() => document.getElementById('mass-all').hidden), true, '모두 골랐는데 모두 선택이 보인다');
    assert.deepEqual(page.errors, []); await page.close();
  } },
];
