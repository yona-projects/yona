/**
 * Select2 한국어 문구(4.x 형식).
 *
 * v1.6의 select2 3.4.5 한국어 문구(Swen Mun 번역)를 4.x의 언어 모듈(`select2/i18n/ko`)로 옮긴 것이다.
 * 공식 4.1.0의 `i18n/ko.js`와 문구가 달라서(예: "결과 없음"과 "결과가 없습니다.") v1.6 화면 문구를 그대로 지키려고 직접 둔다.
 * select2.js 다음에 불러오면 기본 언어가 한국어로 바뀐다.
 */
(function ($) {
    "use strict";

    $.fn.select2.amd.define("select2/i18n/ko", [], function () {
        return {
            errorLoading: function () { return "결과를 불러올 수 없습니다."; },
            inputTooLong: function (args) { var n = args.input.length - args.maximum; return "너무 깁니다. " + n + "글자 지워주세요."; },
            inputTooShort: function (args) { var n = args.minimum - args.input.length; return "너무 짧습니다. " + n + "글자 더 입력해주세요."; },
            loadingMore: function () { return "불러오는 중…"; },
            maximumSelected: function (args) { return "최대 " + args.maximum + "개까지만 선택하실 수 있습니다."; },
            noResults: function () { return "결과 없음"; },
            searching: function () { return "검색 중…"; },
            removeAllItems: function () { return "모든 항목 삭제"; }
        };
    });
    $.fn.select2.defaults.set("language", "ko");
})(jQuery);
