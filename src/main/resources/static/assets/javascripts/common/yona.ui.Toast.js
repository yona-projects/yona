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

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(sContainer, htOptions){

        var htVar = {};
        var htElement = {};

        /** 템플릿을 주지 않았을 때 쓰는 알림 상자 */
        var DEFAULT_TEMPLATE = '<div class="toast" tabindex="-1">' +
            '<div class="btn-dismiss"><button type="button" class="btn-transparent">&times;</button></div>' +
            '<div class="center-text msg"></div></div>';

        /**
         * 구석에 잠깐 떴다 사라지는 알림 상자를 쌓는다. 새 상자가 맨 위에 놓인다.
         * 표준 DOM과 `transitionend`를 쓴다(S-08). 투명해지는 전환은 CSS(`.yobiToasts .toast`의 transition)가 한다.
         *
         * @param {String} sContainer 상자를 쌓을 요소의 선택자
         * @param {Hash Table} htOptions
         * @param {String} htOptions.sTplToast 알림 상자 하나의 HTML 템플릿. 메시지가 들어갈 `.msg`가 있어야 한다
         */
        function _init(sContainer, htOptions){
            htVar.sTplToast = htOptions.sTplToast || DEFAULT_TEMPLATE;
            htElement.elContainer = document.querySelector(sContainer);
        }

        /**
         * 알림 상자 하나를 만든다. 템플릿 앞뒤의 공백과 줄바꿈은 무시한다.
         *
         * @param {String} sMessage
         * @return {HTMLElement}
         */
        function _createToast(sMessage){
            var elTemplate = document.createElement("template");
            elTemplate.innerHTML = htVar.sTplToast.trim();
            var elToast = elTemplate.content.firstElementChild;

            elToast.style.opacity = "0";
            elToast.addEventListener("click", function(){
                elToast.remove();
            });
            elToast.querySelector(".msg").innerHTML = $yona.nl2br(sMessage);

            return elToast;
        }

        /**
         * 알림을 보인다.
         *
         * @param {String} sMessage HTML을 쓸 수 있다. 줄바꿈은 `<br>`로 바뀐다
         * @param {Number} nDuration 밀리초. 이 시간 뒤 투명해졌다가 사라진다. 없거나 0이면 눌러서 닫을 때까지 남는다
         */
        function pushToast(sMessage, nDuration){
            var elToast = _createToast(sMessage);
            htElement.elContainer.prepend(elToast);
            elToast.style.opacity = "1";

            if(nDuration && nDuration > 0){
                _fadeOutTimer(elToast, nDuration);
            }
        }

        /**
         * [nDuration] 뒤 투명하게 만들고, 전환이 끝나면 지운다.
         *
         * @param {HTMLElement} elToast
         * @param {Number} nDuration
         */
        function _fadeOutTimer(elToast, nDuration){
            setTimeout(function(){
                elToast.addEventListener("transitionend", function(weEvt){
                    if(weEvt.target === elToast){
                        elToast.remove();
                    }
                }, {"once": false});
                elToast.style.opacity = "0";
            }, nDuration);
        }

        /**
         * 모든 알림을 지운다.
         */
        function clearToasts(){
            htElement.elContainer.replaceChildren();
        }

        _init(sContainer, htOptions || {});

        return {
            "push" : pushToast,
            "clear": clearToasts
        };
    };

})("yona.ui.Toast");
