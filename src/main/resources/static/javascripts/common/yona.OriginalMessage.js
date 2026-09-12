/**
 * Yona, Project Hosting SW
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
yona.OriginalMessage = (function(htOptions){
    function _setToggle(elem, f1, f2) {
        var a;
        var b;

        a = function() {
            f1();
            elem.removeEventListener('click', a);
            elem.addEventListener('click', b);
        }

        b = function() {
            f2();
            elem.removeEventListener('click', b);
            elem.addEventListener('click', a);
        }

        elem.addEventListener('click', a);
    }

    function _hideAll(elements) {
        elements.forEach(function(el){ el.style.display = "none"; });
    }

    function _showAll(elements) {
        elements.forEach(function(el){ el.style.display = ""; });
    }

    /**
     * jQuery의 delimiterLine.add(delimiterLine.nextAll()).add(delimiterLine.parents()
     * .filter(target 안쪽만).nextAll())와 동일하게, delimiterLine 자신 + 그 뒤 형제들 +
     * (delimiterLine부터 target 사이 각 조상 레벨의) 뒤 형제들을 모두 모은다.
     */
    function _collectOriginalMessageElements(delimiterLine, target) {
        var elements = [delimiterLine];
        var sib = delimiterLine.nextElementSibling;
        while (sib) {
            elements.push(sib);
            sib = sib.nextElementSibling;
        }

        var ancestor = delimiterLine.parentElement;
        while (ancestor && ancestor !== target && target.contains(ancestor)) {
            var asib = ancestor.nextElementSibling;
            while (asib) {
                elements.push(asib);
                asib = asib.nextElementSibling;
            }
            ancestor = ancestor.parentElement;
        }

        return elements;
    }

    /**
     * Hide original message part from the given elements
     *
     * @param {NodeList|Array} targets
     */
    function _hide(targets) {
        targets.forEach(function(target) {
            var delimiterLine;

            var candidates = Array.prototype.filter.call(target.querySelectorAll('*'), function(el){
                return el.textContent && el.textContent.indexOf('---') !== -1;
            });

            for (var i = 0; i < candidates.length; i++) {
                var el = candidates[i];
                var h = el.innerHTML;
                // This matches the boudnary which starts the original message like
                // '----Original Mesage---' roughly.
                if (h && el !== target.firstElementChild &&
                    h.match(/(^|^<[^>]+>)---+[^-]*---+/)) {
                    delimiterLine = el;
                    break;
                }
            }

            if (delimiterLine) {
                var originalMessage = _collectOriginalMessageElements(delimiterLine, target);
                _hideAll(originalMessage);

                var buttonToHideOriginalMessage = document.createElement('button');
                buttonToHideOriginalMessage.style.border = '0';
                buttonToHideOriginalMessage.style.paddingLeft = '5px';
                buttonToHideOriginalMessage.style.paddingRight = '5px';
                buttonToHideOriginalMessage.type = 'button';
                buttonToHideOriginalMessage.textContent = '...';

                _setToggle(buttonToHideOriginalMessage,
                        function() { _showAll(originalMessage); },
                        function() { _hideAll(originalMessage); });
                delimiterLine.before(buttonToHideOriginalMessage);
            }
        });
    }

    // public interface
    return {
	"hide" : _hide
    };
})();
