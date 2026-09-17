/**
 * Yona, Project Hosting SW
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

    var oNS = $yona.createNamespace(ns);
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
            htElement.elContainer = document.querySelector(sContainer);

            // 하이브리드 어댑터(2026-09-17, components/vue-widgets toast 위젯 적용) -
            // #yonaToasts 자리가 <yona-toast>(태그명으로 판별)면 그 커스텀 엘리먼트의
            // push/clear에 그대로 위임한다. 원본 vanilla 구현(cloneNode 템플릿)은 그
            // 태그가 없을 때만 실행된다.
            if(htElement.elContainer.tagName.toLowerCase() === "yona-toast"){
                htVar.bIsVueToast = true;
                return;
            }

            var elTemplate = document.createElement("div");
            elTemplate.innerHTML = htVar.sTplToast;
            htElement.elToast = elTemplate.firstElementChild;
        }

        /**
         * @param {String} sMessage
         * @param {Number} nDuration
         */
        function pushToast(sMessage, nDuration){
            if(htVar.bIsVueToast){
                htElement.elContainer.push(sMessage, nDuration);
                return;
            }

            var elToast = _getToast(sMessage);
            htElement.elContainer.prepend(elToast);
            elToast.style.opacity = "1";

            if(nDuration && nDuration > 0){
                _fadeOutTimer(elToast, nDuration);
            }
        }

        /**
         * @param {String} sMessage
         * @return {HTMLElement}
         */
        function _getToast(sMessage){
            var elToast = htElement.elToast.cloneNode(true);
            var elMessage = elToast.querySelector(".msg");

            elToast.style.opacity = "0";
            elToast.addEventListener("click", _onClickClose);
            elMessage.innerHTML = $yona.nl2br(sMessage);

            return elToast;
        }

        function _onClickClose(weEvt){
            this.remove();
        }

        /**
         * @param {HTMLElement} elToast
         * @param {Number} nDuration
         */
        function _fadeOutTimer(elToast, nDuration){
            elToast.addEventListener("webkitTransitionEnd", function(){
                elToast.remove();
            });
            setTimeout(function(){
                elToast.style.opacity = 0;
            }, nDuration);
        }

        function clearToasts(){
            if(htVar.bIsVueToast){
                htElement.elContainer.clear();
                return;
            }
            htElement.elContainer.innerHTML = "";
        }

        _init(sContainer, htOptions || {});

        return {
            "push" : pushToast,
            "clear": clearToasts
        };
    };

})("yona.ui.Toast");
