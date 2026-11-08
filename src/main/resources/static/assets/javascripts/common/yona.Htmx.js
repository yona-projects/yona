/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/

/**
 * htmx(`lib/htmx.js`)를 Yona에 맞게 설정한다. jquery-pjax를 대신한다(`decisions.md` D-96).
 *
 * 목록 화면(이슈, 내 이슈, 풀 리퀘스트, 조직 목록)은 `div[pjax-container]` 영역만 바꾼다. 어떤 요소가 어떤 영역을 어떻게 바꿀지는
 * 템플릿의 `hx-*` 속성이 선언하고, 이 파일은 모든 htmx 요청에 공통인 동작만 정한다.
 *
 * <ul>
 *   <li>서버 계약: 요청에 `X-PJAX: true`와 `X-Requested-With: XMLHttpRequest`를 붙인다. 서버는 이 두 헤더로 목록 조각 응답을
 *       구분한다(`AjaxRequests.isPjax`, v1.6 `HttpUtil.isPJAXRequest`와 같다).</li>
 *   <li>속성 정착 끔: 교체한 요소의 속성이 20ms 뒤에 되돌려지지 않는다(초기화가 붙인 class와 style을 지키려고).</li>
 *   <li>이동 이력: 화면 조각을 저장하지 않는다. 뒤로 가기와 앞으로 가기는 그 주소를 서버에서 새로 불러온다
 *       (`historyCacheSize`가 0이고 `refreshOnHistoryMiss`가 true이다).</li>
 *   <li>대체 동작: 서버 오류, 연결 실패, 시간 초과, 목록 영역이 없는 응답(로그인 화면 등)은 일반 이동으로 같은 주소를 다시 연다.
 *       jquery-pjax가 하던 동작과 같다.</li>
 *   <li>진행 막대: 요청이 시작되면 `NProgress`를 시작하고, 끝나면(성공과 실패 모두) 끝낸다.</li>
 * </ul>
 */
yona.Htmx = (function(window, document) {

    "use strict";

    /** 응답에 이 표지가 없으면 목록 조각이 아니라고 보고 일반 이동으로 처리한다. */
    var CONTAINER_MARK = "pjax-container";

    var htmx = window.htmx;

    if (typeof htmx !== "undefined") {
        _configure();
    }

    /** 이동 이력, 표시기 스타일, 속성 안의 JavaScript 식 평가를 정하고 공통 이벤트 처리를 단다. */
    function _configure() {
        htmx.config.historyCacheSize = 0;
        htmx.config.refreshOnHistoryMiss = true;
        // 쓰지 않는 기능을 꺼서 공격 면을 줄인다: 표시기 스타일 삽입, 속성 안의 JavaScript 식 평가
        htmx.config.includeIndicatorStyles = false;
        htmx.config.allowEval = false;
        // 속성 정착(settling)을 끈다. htmx는 id가 같은 요소가 이미 있으면 새 요소가 옛 요소의 class, style, width, height를 먼저 물려받았다가
        // 20ms 뒤에 서버가 준 값으로 되돌린다(CSS 전환용). 그 사이에 select2 같은 초기화가 붙인 속성(`select2-hidden-accessible` 등)이
        // 함께 지워져 원본 select가 화면에 드러난다. pjax는 노드를 그대로 바꿔 끼웠으므로 이 일이 없었다.
        htmx.config.attributesToSettle = [];

        document.addEventListener("htmx:configRequest", function(evt) {
            var detail = evt.detail;
            detail.headers["X-PJAX"] = "true";
            detail.headers["X-Requested-With"] = "XMLHttpRequest";

            // htmx는 `hx-get`의 값을 요소를 처리할 때 한 번만 읽는다. 풀 리퀘스트 목록의 탭은 클릭할 때 폼의 `action`을
            // 열림, 닫힘, 보냄 주소로 바꾸고 제출하므로(v1.6과 같다), 폼은 요청 시점의 `action`을 따른다.
            var action = detail.elt && detail.elt.tagName === "FORM" && detail.elt.getAttribute("action");
            if (action) {
                detail.path = action;
            }
        });

        document.addEventListener("htmx:beforeRequest", _startProgress);
        document.addEventListener("htmx:afterRequest", _doneProgress);

        document.addEventListener("htmx:beforeSwap", function(evt) {
            var detail = evt.detail;
            if (detail.isError) {
                return;
            }
            var response = detail.serverResponse;
            if (typeof response === "string" && response.indexOf(CONTAINER_MARK) < 0) {
                detail.shouldSwap = false;
                _loadWholePage(evt);
            }
        });

        document.addEventListener("htmx:responseError", _loadWholePage);
        document.addEventListener("htmx:sendError", _loadWholePage);
        document.addEventListener("htmx:timeout", _loadWholePage);
    }

    /**
     * 같은 주소를 일반 이동으로 다시 연다.
     *
     * @param {CustomEvent} evt htmx 이벤트. `detail.pathInfo`에 요청 주소가 있다.
     * @private
     */
    function _loadWholePage(evt) {
        var pathInfo = evt.detail && evt.detail.pathInfo;
        var path = pathInfo && (pathInfo.finalRequestPath || pathInfo.requestPath);
        if (path) {
            window.location.assign(path);
        }
    }

    function _startProgress(evt) {
        if (!evt.defaultPrevented && typeof window.NProgress !== "undefined") {
            window.NProgress.start();
        }
    }

    function _doneProgress() {
        if (typeof window.NProgress !== "undefined") {
            window.NProgress.done();
        }
    }

    return {
        /**
         * 폼을 제출한다. 폼에 `hx-get`이 있으면 htmx가 가로채 영역만 바꾸고, 없으면 일반 제출이다.
         * jQuery의 `.submit()`은 제출 이벤트를 만들지 않고 폼을 직접 제출하므로 htmx가 가로채지 못한다. 그래서 `requestSubmit()`을 쓴다.
         * `requestSubmit()`이 없는 옛 브라우저는 일반 제출로 처리한다.
         *
         * @param {HTMLFormElement|jQuery|String} form 폼 요소, jQuery 객체 또는 선택자
         */
        "submit": function(form) {
            var el = window.jQuery(form).get(0);
            if (!el) {
                return;
            }
            if (typeof el.requestSubmit === "function") {
                el.requestSubmit();
            } else {
                window.jQuery(el).submit();
            }
        }
    };

})(window, document);
