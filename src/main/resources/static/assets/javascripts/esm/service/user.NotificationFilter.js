import { register } from "../namespace.js";

/**
 * 알림 설정 화면의 프로젝트 검색이다. 프로젝트 목록 위의 검색 상자에 글자를 입력하면 "소유자 / 프로젝트" 표기에
 * 그 글자(대소문자 무시)가 들어 있는 항목만 남기고, 하나도 없으면 안내 문구를 보인다. 서버 요청은 하지 않는다(decisions.md A-582).
 */
const NotificationFilter = function(){

    const htElement = {};

    /**
     * 요소를 잡고 이벤트를 건다.
     */
    function _init(){
        htElement.welInput = $("#notification-project-filter");
        htElement.welItems = $("#notification-projects > li");
        htElement.welEmpty = $("#notification-project-empty");
        htElement.welInput.on("input", _filter);
    }

    /**
     * 입력한 글자에 맞는 항목만 보인다.
     */
    function _filter(){
        const sKeyword = String(htElement.welInput.val() || "").trim().toLowerCase();
        let nShown = 0;
        htElement.welItems.each(function(){
            const bMatch = sKeyword === "" || $(this).text().toLowerCase().indexOf(sKeyword) !== -1;
            this.hidden = !bMatch;
            if (bMatch) {
                nShown++;
            }
        });
        htElement.welEmpty.prop("hidden", nShown > 0);
    }

    _init();
};

export default NotificationFilter;

register("yona.user.NotificationFilter", NotificationFilter);
