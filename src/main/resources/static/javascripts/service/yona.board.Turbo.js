import {setupTwoColumn} from '/javascripts/service/yona.turbo.TwoColumn.js';

setupTwoColumn({
    layout: '.board-columns',
    list: 'post-list',
    detail: 'post-detail',
    detailRoot: 'post-detail-content',
    searchField: '#option_form input[name="selected"]',
    mountDetail: root => yona.mountBoardDetail(root)
});
