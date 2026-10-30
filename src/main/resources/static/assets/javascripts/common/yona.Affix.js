/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * 스크롤이 지정한 위치를 지나면 요소를 화면에 고정하는 기능이다.
 *
 * Bootstrap 2/3의 `affix` 플러그인이 하던 일 가운데 이 사이트가 쓰는 부분만 자체 코드로 옮겼다(X-38c, `decisions.md` A-291).
 * 고정 여부는 클래스로 나타낸다. 스크롤이 기준 위치 이하이면 `affix-top`, 지나면 `affix`이다.
 * 고정된 모양(`position: fixed`)은 스타일시트의 `.affix`가 정한다.
 *
 * 사용: `yona.affix(요소, 기준위치)`. 기준위치는 문서 맨 위에서 떨어진 거리(px)이다.
 */
(function (window, $) {
    "use strict";

    var yona = window.yona = window.yona || {};

    /**
     * 요소가 고정되는 스크롤 위치를 정하고, 스크롤과 창 크기 변경에 맞춰 클래스를 갱신한다.
     *
     * @param {jQuery|Element|string} target 고정할 요소
     * @param {number} offsetTop 이 값보다 많이 스크롤되면 요소가 고정된다
     * @returns {jQuery} 대상 요소
     */
    yona.affix = function (target, offsetTop) {
        var $target = $(target);
        if ($target.length === 0) {
            return $target;
        }

        var $window = $(window);
        var check = function () {
            if (!$target.is(":visible")) {
                return;
            }
            var affixed = $window.scrollTop() > offsetTop;
            $target.toggleClass("affix", affixed).toggleClass("affix-top", !affixed);
        };

        $window.on("scroll resize", check);
        check();
        return $target;
    };

    // 관리자 로그인 막대처럼 `data-affix-top`으로 기준 위치를 적은 요소는 페이지가 열릴 때 자동으로 고정 대상이 된다.
    $(function () {
        $("[data-affix-top]").each(function () {
            yona.affix(this, Number($(this).attr("data-affix-top")));
        });
    });
})(window, jQuery);
