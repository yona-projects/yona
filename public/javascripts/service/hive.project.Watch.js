/**
 * @(#)hive.project.New.js 2013.06.13
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://hive.dev.naver.com/license
 */

(function(ns) {
    var oNS = $hive.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions) {
        var htVar = {};
        var htElement = {};

        /**
         * initialize
         */
        function _init(htOptions) {
            _initElement();
            _attachEvent();
        }

        /**
         * initialize element variables
         */
        function _initElement() {
            htElement.watchBtn = $("a.watchBtn");
        }

        /**
         * attach event handlers
         */
        function _attachEvent() {
            htElement.watchBtn.click(function(e) {
                e.preventDefault();
                $('<form action="' + $(this).attr('href') + '" method="post"></form>').submit();
            });
        }

        _init(htOptions || {});
    };
})("hive.project.Watch");