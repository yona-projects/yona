import {setupTwoColumn} from '/javascripts/service/yona.turbo.TwoColumn.js';

// 내 이슈는 여러 프로젝트에 걸친 목록이라 선택 상태를 ?detail=issue:<owner>/<project>/<번호>로 표현한다.
setupTwoColumn({
    layout: '.issue-columns',
    list: 'issue-list',
    detail: 'issue-detail',
    detailRoot: 'issue-detail-content',
    param: 'detail',
    mountDetail: root => yona.mountIssueDetail(root)
});
