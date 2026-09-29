import {setupTwoColumn} from '/javascripts/service/yona.turbo.TwoColumn.js';

setupTwoColumn({
    layout: '.issue-columns',
    list: 'issue-list',
    detail: 'issue-detail',
    detailRoot: 'issue-detail-content',
    mountDetail: root => yona.mountIssueDetail(root)
});
