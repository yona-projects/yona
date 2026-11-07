/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
yobi.OriginalMessage = (function(htOptions){
    function _setToggle(elem, f1, f2) {
        var a;
        var b;

        a = function() {
            f1();
            elem.click(b);
        }

        b = function() {
            f2();
            elem.click(a);
        }

        elem.click(a);
    }

    /**
     * Hide original message part from the given elements
     *
     * @param {String} sQuery Selector string for targets
     */
    function _hide(targets) {
        $.each(targets, function(_, targetElem) {
            var delimiterLine;
            var originalMessage; // list of jquery elements which construct the original message
            var buttonToHideOriginalMessage; // a button to toggle the original message
            var target = $(targetElem);

            target.find(":contains('---')").each(function() {
                var h = $(this).html()
                // This matches the boudnary which starts the original message like
                // '----Original Mesage---' roughly.
                if (h && !$(this).is(target.children(":first")) &&
                    h.match(/(^|^<[^>]+>)---+[^-]*---+/)) {
                    delimiterLine = $(this);
                    return false;
                }
                return true;
            });

            if (delimiterLine) {
                originalMessage = delimiterLine.add(
                        delimiterLine.nextAll()
                    ).add(
                        delimiterLine.parents().filter(function(idx, elem) {
                            return target.has(elem).length > 0;
                        }).nextAll()
                    ).hide();

                buttonToHideOriginalMessage = $("<button>")
                    .css('border', 0)
                    .css('padding-left', '5px')
                    .css('padding-right', '5px')
                    .attr('type', 'button')
                    .text('...');
                _setToggle(buttonToHideOriginalMessage,
                        function() { originalMessage.show(); },
                        function() { originalMessage.hide(); });
                delimiterLine.before(buttonToHideOriginalMessage);
            }
        });
    }

    // public interface
    return {
	"hide" : _hide
    };
})();
