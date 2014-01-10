/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
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
            htVar.htOptions = {  // -- Default Options
                "lines"    : 11, // The number of lines to draw
                "length"   : 26, // The length of each line
                "width"    : 7,  // The line thickness
                "radius"   : 19, // The radius of the inner circle
                "corners"  : 1,  // Corner roundness (0..1)
                "rotate"   : 0,  // The rotation offset
                "direction": -1, // 1: clockwise, -1: counterclockwise
                "color"    : "#fff", // #rgb or #rrggbb or array of colors
                "speed"    : 1.5,    // Rounds per second
                "trail"    : 66,     // Afterglow percentage
                "shadow"   : false,  // Whether to render a shadow
                "hwaccel"  : true,   // Whether to use hardware acceleration
                "className": "spinner", // The CSS class to assign to the spinner
                "zIndex"   : 2e9,    // The z-index (defaults to 2000000000)
                "top"      : "auto", // Top position relative to parent in px
                "left"     : "auto"  // Left position relative to parent in px
            };

            _setOption(htOptions);

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
         * Set user defined options
         * @param htOpt
         * @private
         */
        function _setOption(htOpt){
            for(var sKey in htOpt){
                htVar.htOptions[sKey] = htOpt[sKey];
            }
        }

        /**
         * Show spinner
         * @private
         */
        function _showSpinner(){
            htVar.oSpinner.spin();
            htElement.welContainer.append(htVar.oSpinner.el);

            htElement.welWrapper.fadeIn();
        }

        /**
         * Hide spinner
         * @private
         */
        function _hideSpinner(){
            htElement.welWrapper.fadeOut(400, function(){
                htVar.oSpinner.stop();
            });
        }

        _init(htOptions || {});

        // public interfaces
        return {
            "show": _showSpinner,
            "hide": _hideSpinner,
            "setOption": _setOption
        };
    })();

})("yobi.ui.Spinner");

/**
 * 페이지 내에 존재하는 form 의 submit 이벤트와
 * jQuery.requestAs 의 beforeRequest 이벤트 발생시
 * 자동으로 ui.Spinner.show() 를 실행한다
 */
$(document).ready(function(){
    // <form>
    window.bFormSubmitted = false;

    $("form").on("submit", function(){
        yobi.ui.Spinner.show();
        window.bFormSubmitted = true;
    });

    // -- ESC 키를 눌러 폼 전송을 중단하는 경우
    $(window).on("keydown", function(weEvt){
        if(weEvt.keyCode === 27 && window.bFormSubmitted){
            yobi.ui.Spinner.hide();
            window.bFormSubmitted = false;
        }
    });
    // --- //

    // [data-request-method]
    $("[data-request-method]").each(function(i, el){
        $(el).data("requestAs").on("beforeRequest", function(){
            yobi.ui.Spinner.show();
        });
    });
    // --- //

    // for OSX Safari
    if(navigator.userAgent.indexOf("Safari") > -1){
        $(window).on("beforeunload", function(){
            yobi.ui.Spinner.hide();
        });
    }
});
