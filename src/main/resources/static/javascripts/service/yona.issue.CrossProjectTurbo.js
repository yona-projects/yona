import {setupTwoColumn} from '/javascripts/service/yona.turbo.TwoColumn.js';

// 내 이슈·조직 이슈처럼 여러 프로젝트에 걸친 이슈 목록의 2단 보기.
// 선택 상태를 ?detail=issue:<owner>/<project>/<번호> 복합 키로 표현한다.
setupTwoColumn({
    layout: '.issue-columns',
    list: 'issue-list',
    detail: 'issue-detail',
    detailRoot: 'issue-detail-content',
    param: 'detail',
    mountDetail: root => yona.mountIssueDetail(root)
});
