/**
 * Select2 日本語の文言(4.x 形式).
 *
 * v1.6 の select2 3.4.5 日本語文言を 4.x の言語モジュール(`select2/i18n/ja`)へ移したもの。
 * select2.js の後に読み込むと既定の言語が日本語になる。
 */
(function ($) {
    "use strict";

    $.fn.select2.amd.define("select2/i18n/ja", [], function () {
        return {
            errorLoading: function () { return "結果が読み込まれませんでした"; },
            inputTooLong: function (args) { var n = args.input.length - args.maximum; return "検索文字列が" + n + "文字長すぎます"; },
            inputTooShort: function (args) { var n = args.minimum - args.input.length; return "後" + n + "文字入れてください"; },
            loadingMore: function () { return "読込中･･･"; },
            maximumSelected: function (args) { return "最多で" + args.maximum + "項目までしか選択できません"; },
            noResults: function () { return "該当なし"; },
            searching: function () { return "検索中･･･"; },
            removeAllItems: function () { return "すべてのアイテムを削除"; }
        };
    });
    $.fn.select2.defaults.set("language", "ja");
})(jQuery);
