import { register } from "../namespace.js";

/**
 * 알림 설정 화면의 프로젝트 목록 동작이다. 첫째, 프로젝트 목록 위의 검색 상자에 글자를 입력하면 항목을 걸러 보이고, 둘째, 프로젝트를 고르면 표 위의 제목을 선택한 프로젝트 이름으로 바꾼다.
 *
 * 검색: 프로젝트 목록 위의 검색 상자에 글자를 입력하면 "소유자 / 프로젝트" 표기에
 * 그 글자(대소문자 무시)가 들어 있는 항목만 남기고, 하나도 없으면 안내 문구를 보인다. 서버 요청은 하지 않는다(decisions.md A-582).
 */
const NotificationProjects = function(){

    const htElement = {};

    /**
     * 요소를 잡고 이벤트를 건다.
     */
    function _init(){
        htElement.welInput = $("#notification-project-filter");
        htElement.welItems = $("#notification-projects > li");
        htElement.welEmpty = $("#notification-project-empty");
        htElement.welTitle = $("#notification-project-title");
        htElement.welInput.on("input", _filter);
        $(document).on("shown.bs.tab", "#notification-projects a", function(){
            htElement.welTitle.text($(this).text().replace(/\s+/g, " ").trim());
        });
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

export default NotificationProjects;

register("yona.user.NotificationProjects", NotificationProjects);
