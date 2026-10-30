/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * `.nav` 밖에 있는 탭 목록의 활성 표시를 옮긴다.
 *
 * Bootstrap 4의 탭 플러그인은 `.nav`나 `.list-group` 안의 링크(`li > a.active`)에서만 이전 탭의 활성 표시를 지운다.
 * 이 사이트에는 자체 목록(`ul.nav-subtab`, `ul.lst-stacked`)의 `li`에 `active`를 두는 탭이 있어서,
 * 탭이 바뀌면(`shown.bs.tab`) 눌린 링크의 `li`에만 `active`를 남긴다(X-38c, `decisions.md` A-292).
 * 패널 전환은 플러그인이 그대로 한다.
 */
(function ($) {
    "use strict";

    $(document).on("shown.bs.tab", 'ul.nav-subtab a[data-bs-toggle="tab"], ul.lst-stacked a[data-bs-toggle="tab"]', function () {
        $(this).parent("li").addClass("active").siblings("li").removeClass("active");
    });
})(jQuery);
