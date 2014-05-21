/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @Author Jihan Kim
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
/**
 * yobi.ui.Spinner 는 일관된 Spinner UI를 위한 공통 인터페이스다.
 *
 * Method:
 * yobi.ui.Spinner.show()
 * yobi.ui.Spinner.hide()
 *
 * @requires spin.js (http://fgnass.github.io/spin.js)
 */
(function(ns){

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = (function(htOptions){

        var htVar = {};
        var htElement = {};

        /**
         * Initialize
         * @param htOptions
         * @private
         */
        function _init(htOptions){
            _initVar(htOptions);
            _initElement();
        }

        /**
         * Initialize Variables
         * @param htOptions
         * @private
         */
        function _initVar(htOptions){
            htVar.htOptions = $.extend({  // -- Default Options
                "lines"    : 11, // The number of lines to draw
                "length"   : 26, // The length of each line
                "width"    : 7,  // The line thickness
                "radius"   : 19, // The radius of the inner circle
                "corners"  : 1,  // Corner roundness (0..1)
                "rotate"   : 0,  // The rotation offset
                "direction": -1, // 1: clockwise, -1: counterclockwise
                "color"    : "#bbb", // #rgb or #rrggbb or array of colors
                "speed"    : 1.5,    // Rounds per second
                "trail"    : 66,     // Afterglow percentage
                "shadow"   : false,  // Whether to render a shadow
                "hwaccel"  : true,   // Whether to use hardware acceleration
                "className": "spinner", // The CSS class to assign to the spinner
                "zIndex"   : 2e9,    // The z-index (defaults to 2000000000)
                "top"      : "auto", // Top position relative to parent in px
                "left"     : "auto"  // Left position relative to parent in px
            }, htOptions);

            htVar.oSpinner = new Spinner(htVar.htOptions);
        }

        /**
         * Initialize Elements
         * @private
         */
        function _initElement(){
            htElement.welWrapper = $("#yobiSpinner");
            htElement.welContainer = htElement.welWrapper.find(".spinContainer");
        }

        /**
         * Show spinner
         * @private
         */
        function _showSpinner(htOptions){
            var bUseDimmer = (htOptions && typeof htOptions.bUseDimmer !== "undefined") ?
                              htOptions.bUseDimmer : false;
            var sBackground = bUseDimmer ? "rgba(0,0,0,0.4)": "transparent";

            htVar.oSpinner.spin();
            htElement.welContainer.append(htVar.oSpinner.el);

            htElement.welWrapper.css("background", sBackground);
            htElement.welWrapper.show();
        }

        /**
         * Hide spinner
         * @private
         */
        function _hideSpinner(){
            htElement.welWrapper.hide();
            htVar.oSpinner.stop();
        }

        _init(htOptions || {});

        // public interfaces
        return {
            "show": _showSpinner,
            "hide": _hideSpinner
        };
    })();

})("yobi.ui.Spinner");

/**
 * Safari 브라우저의 캐시로 인해
 * Spinner 가 표시되고 있는 상태로 패이지를 이동했다가
 * "뒤로가기" 버튼을 눌러 돌아오면 여전히 표시중인 문제를 해결하기 위해
 * 페이지를 벗어나기 직전 없애고 Spinner 를 감춤 처리하도록 한다
 */
$(document).ready(function(){
    if(navigator.userAgent.indexOf("Safari") > -1){
        $(window).on("beforeunload", function(){
            yobi.ui.Spinner.hide();
        });
    }
});
