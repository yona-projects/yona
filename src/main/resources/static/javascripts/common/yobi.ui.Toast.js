/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Jihan Kim
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
(function(ns){

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(sContainer, htOptions){

        var htVar = {};
        var htElement = {};

        /**
         * @param {String} sContainer
         * @param {Hash Table} htOptions
         */
        function _init(sContainer, htOptions){
            _initVar(htOptions);
            _initElement(sContainer);
        }

        /**
         * @param {Hash Table} htOptions
         */
        function _initVar(htOptions){
            htOptions.sTplToast = htOptions.sTplToast.replace("\n", "");
            htVar.sTplToast = htOptions.sTplToast || '<div class="toast" tabindex="-1">\
            <div class="btn-dismiss"><button type="button" class="btn-transparent">&times;</button></div>\
            <div class="center-text msg"></div></div>';
        }

        /**
         * @param {String} sContainer
         */
        function _initElement(sContainer){
            htElement.welContainer = $(sContainer);
            htElement.welToast = $(htVar.sTplToast);
        }

        /**
         * @param {String} sMessage
         * @param {Number} nDuration
         */
        function pushToast(sMessage, nDuration){
            var welToast = _getToast(sMessage);
            htElement.welContainer.prepend(welToast);
            welToast.css("opacity", "1");

            if(nDuration && nDuration > 0){
                _fadeOutTimer(welToast, nDuration);
            }
        }

        /**
         * @param {String} sMessage
         * @return {Wrapped Element}
         */
        function _getToast(sMessage){
            var welToast = htElement.welToast.clone();
            var welMessage = welToast.find(".msg");

            welToast.css("opacity", "0");
            welToast.click(_onClickClose);
            welMessage.html($yobi.nl2br(sMessage));

            return welToast;
        }

        function _onClickClose(weEvt){
            $(this).remove();
        }

        /**
         * @param {Wrapped Element} welToast
         * @param {Number} nDuration
         */
        function _fadeOutTimer(welToast, nDuration){
            welToast.bind("webkitTransitionEnd", function(){
                welToast.remove();
            });
            setTimeout(function(){
                welToast.css("opacity", 0);
            }, nDuration);
        }

        function clearToasts(){
            htElement.welContainer.empty();
        }

        _init(sContainer, htOptions || {});

        return {
            "push" : pushToast,
            "clear": clearToasts
        };
    };

})("yobi.ui.Toast");
