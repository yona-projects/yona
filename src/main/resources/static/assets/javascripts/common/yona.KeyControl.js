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

    /**
     * 입력란의 [from, to) 범위를 [text]로 바꾼다.
     *
     * 브라우저의 입력 명령(`execCommand('insertText')`)으로 바꾸면 되돌리기(Ctrl+Z) 기록에 한 번의 편집으로 남고
     * input 이벤트도 온다(GitHub의 서식 단추 모듈도 같은 방식이다, S-03). 값을 직접 바꾸면 되돌리기 기록이 끊긴다.
     * 명령이 없거나 실패하거나 결과가 기대와 다르면 v1.6처럼 값을 직접 바꾼다.
     *
     * @param {HTMLTextAreaElement} target
     * @param {Number} from 바꿀 범위의 시작
     * @param {Number} to 바꿀 범위의 끝(포함하지 않음)
     * @param {String} text 바꿔 넣을 글자
     */
    var replaceRange = function (target, from, to, text) {
        var value = target.value,
            expected = value.substring(0, from) + text + value.substring(to),
            done = false;

        target.focus();
        target.setSelectionRange(from, to);
        try {
            done = (text === '') ? document.execCommand('delete') : document.execCommand('insertText', false, text);
        } catch (e) {
            done = false;
        }
        if (!done || target.value !== expected) {
            target.value = expected;
        }
    };

    window._tab = function (target) {

        var start = target.selectionStart,
            end = target.selectionEnd,
            value = target.value,
            selections = value.substring(start, end).split('\n');

        for (var i = 0, len = selections.length; i < len; ++i) {
            selections[i] = tabCharacter + selections[i];
        }

        replaceRange(target, start, end, selections.join('\n'));

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
                replaceRange(target, start, end, portion.replace(pattern, ""));
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

            replaceRange(target, start, end, selections.join('\n'));

            // re-select text after tabbing
            select(start, (edits > 0 ? end - (tabCharacter.length * edits) : end), target);
        }

        target.focus()
    };
})();