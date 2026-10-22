/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

// Borrowed and modified from https://jsfiddle.net/tovic/rKzs5/
(function () {

    var tabCharacter = '    '; // Use `\t` or multiple space character

    var select = function (start, end, target) {
        target.focus();
        target.setSelectionRange(start, end);
    };

    window._tab = function (target) {

        var start = target.selectionStart,
            end = target.selectionEnd,
            value = target.value,
            selections = value.substring(start, end).split('\n');

        for (var i = 0, len = selections.length; i < len; ++i) {
            selections[i] = tabCharacter + selections[i];
        }

        target.value = value.substring(0, start) + selections.join('\n') + value.substring(end);

        // re-select text after tabbing
        var selectEnd = (end + (tabCharacter.length * selections.length));
        if (start === end) {
            select(selectEnd, selectEnd, target);
        } else {
            select(start, selectEnd, target);
        }
        target.focus();
    };

    window._untab = function (target) {
        var start = target.selectionStart,
            end = target.selectionEnd,
            value = target.value,
            pattern = new RegExp(tabCharacter),
            edits = 0;

        if (start === end) { // single line

            while (start > 0) {
                if (value.charAt(start - 1) === '\n' || value.charAt(start - 1) === '\r') {
                    break;
                }
                start--;
            }

            var portion = value.substring(start, end),
                matches = portion.match(pattern);

            if (matches) {
                target.value = value.substring(0, start) + portion.replace(pattern, "") + value.substring(end);
                end--;
            }

            // set caret position after tabbing
            var selectEnd = end <= start ? end : end - tabCharacter.length + 1;
            select(selectEnd, selectEnd, target);

        } else { // multiline

            var selections = value.substring(start, end).split('\n');

            for (var i = 0, len = selections.length; i < len; ++i) {
                if (selections[i].match(pattern)) {
                    edits++;
                    selections[i] = selections[i].replace(pattern, "");
                }
            }

            target.value = value.substring(0, start) + selections.join('\n') + value.substring(end);

            // re-select text after tabbing
            select(start, (edits > 0 ? end - (tabCharacter.length * edits) : end), target);
        }

        target.focus()
    };
})();