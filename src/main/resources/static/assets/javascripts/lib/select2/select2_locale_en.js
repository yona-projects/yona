/**
 * Select2 English messages (4.x format).
 *
 * Keeps the wording of select2 3.4.5 used by v1.6 ("No matches found", "Searching...") instead of the 4.1.0 wording
 * ("No results found", "Searching…"), so the screens read the same as in v1.6. Load it after select2.js.
 */
(function ($) {
    "use strict";

    $.fn.select2.amd.define("select2/i18n/en", [], function () {
        return {
            errorLoading: function () { return "The results could not be loaded."; },
            inputTooLong: function (args) { var n = args.input.length - args.maximum; return "Please delete " + n + " character" + (n == 1 ? "" : "s"); },
            inputTooShort: function (args) { var n = args.minimum - args.input.length; return "Please enter " + n + " more character" + (n == 1 ? "" : "s"); },
            loadingMore: function () { return "Loading more results..."; },
            maximumSelected: function (args) { return "You can only select " + args.maximum + " item" + (args.maximum == 1 ? "" : "s"); },
            noResults: function () { return "No matches found"; },
            searching: function () { return "Searching..."; },
            removeAllItems: function () { return "Remove all items"; },
            removeItem: function () { return "Remove item"; },
            search: function () { return "Search"; }
        };
    });
    $.fn.select2.defaults.set("language", "en");
})(jQuery);
