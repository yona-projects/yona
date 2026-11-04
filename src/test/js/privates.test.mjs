import assert from 'node:assert/strict';
import { pageWith, sourceSnippet } from './harness.mjs';

export const tests = [
  { name: '리뷰 스레드가 하나도 없어도 높이 계산이 오류 없이 건너뛴다 (R-70)', async run() {
    const code = sourceSnippet('service/yobi.code.Diff.js', 'function _isReviewContainerStuck()', '_init(htOptions || {});');
    const page = await pageWith({ html: '<div id="wrap"></div>' });
    const out = await page.evaluate((src) => {
      var htElement = { welContainer: $('#wrap'), welReviewList: $('div.review-list'), welReviewContainer: $('#none') };
      try { new Function('htElement', src + '; _setReviewListHeight();')(htElement); } catch (e) { return e.message; }
      return 'ok';
    }, code);
    await page.close();
    assert.equal(out, 'ok', out);
  } },
  { name: '이슈 목록을 읽은 뒤 하위 이슈 스크립트가 없는 화면에서도 오류가 나지 않는다 (R-69)', async run() {
    const code = sourceSnippet('service/yobi.issue.List.js', 'function _onLoadIssueList(){', 'function _listHoverEffect');
    const page = await pageWith({ html: '' });
    const out = await page.evaluate((src) => {
      var noop = 'var NProgress={done:function(){}},htInitialOptions={};' +
        ['_initElement', '_initPagination', '_initSelect2', '_initCalendar', '_initImplicitTitlePrefix', '_addEventAtOrganizationIssueSearchPage', '_listHoverEffect', '_initTwoColumnMode', '_initShowChildList'].map(function (n) { return 'function ' + n + '(){}'; }).join(';') + ';';
      try { new Function(noop + src + '; _onLoadIssueList();')(); } catch (e) { return e.message; }
      return 'ok';
    }, code);
    await page.close();
    assert.equal(out, 'ok', out);
  } },
];
