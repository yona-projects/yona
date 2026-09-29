import {setupTwoColumn} from '/javascripts/service/yona.turbo.TwoColumn.js';

// 조직 게시판처럼 여러 프로젝트에 걸친 게시글 목록의 2단 보기.
// 선택 상태를 ?detail=post:<owner>/<project>/<번호> 복합 키로 표현한다.
setupTwoColumn({
    layout: '.board-columns',
    list: 'post-list',
    detail: 'post-detail',
    detailRoot: 'post-detail-content',
    param: 'detail',
    searchField: '#option_form input[name="detail"]',
    mountDetail: root => yona.mountBoardDetail(root)
});
