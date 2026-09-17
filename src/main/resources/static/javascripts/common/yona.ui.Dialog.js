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
/**
 * @example
 * var oDialog = new yona.ui.Dialog("#yonaDialog")
 * oDialog.show("메시지");
 *
 * bootstrap-modal.js 대신 네이티브 <dialog> + showModal()/close()를 사용한다. #yonaDialog
 * 자체가 <dialog class="modal yonaDialog">로 바뀌었으므로(site/layout.html 참고) 기존 .modal
 * CSS(위치/테두리/그림자)는 그대로 재사용되고, 백드롭은 ::backdrop 의사 엘리먼트로 대체된다.
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
            _attachEvent();
        }

        /**
         * @param htOptions
         * @private
         */
        function _initVar(htOptions){
            htVar.sDefaultButton = '<button type="button" class="ybtn ybtn-info" data-dismiss="modal">' + Messages("button.confirm") + '</button>';
            htVar.sTplCustomButton = '<button type="button" class="ybtn ${class}">${text}</button>';
            htVar.bAutoFocusOnLastButton = (typeof htOptions.bAutoFocusOnLastButton !== "undefined") ? htOptions.bAutoFocusOnLastButton : true;
        }

        /**
         * @param {String} sContainer
         */
        function _initElement(sContainer){
            var elOriginal = document.querySelector(sContainer);

            // 하이브리드 어댑터(2026-09-17, components/vue-widgets dialog 위젯 적용) -
            // #yonaDialog 자리가 <yona-dialog>(태그명으로 판별)면 그 커스텀 엘리먼트에
            // show/hide를 그대로 위임한다. 원본 vanilla 구현(cloneNode 싱글턴)은 그
            // 태그가 없을 때만 실행된다.
            if(elOriginal.tagName.toLowerCase() === "yona-dialog"){
                htVar.bIsVueDialog = true;
                htElement.elContainer = elOriginal;
                return;
            }

            htElement.elContainer = elOriginal.cloneNode(true);
            document.body.appendChild(htElement.elContainer);
            htElement.elMessage = htElement.elContainer.querySelector(".msg");
            htElement.elDescription = htElement.elContainer.querySelector(".desc");
            htElement.elButtons = htElement.elContainer.querySelector(".buttons");
        }

        function _attachEvent(){
            if(htVar.bIsVueDialog){
                return;
            }
            htElement.elContainer.addEventListener("close", _onHiddenDialog);

            htElement.elContainer.addEventListener("click", function(weEvt){
                // 배경(백드롭) 클릭 시 닫기 - 네이티브 dialog는 backdrop 클릭이 dialog 자신에 대한
                // 클릭으로 버블링되므로(target === 자기 자신) 이것으로 배경 클릭을 판별한다.
                if(weEvt.target === htElement.elContainer){
                    hideDialog();
                    return;
                }

                var elButton = weEvt.target.closest("button.ybtn");
                if(elButton && htElement.elContainer.contains(elButton)){
                    _onClickButton(elButton, weEvt);
                    return;
                }

                // X 닫기 버튼(.btn-dismiss button)처럼 ybtn 클래스가 없는 data-dismiss="modal" 엘리먼트
                var elDismiss = weEvt.target.closest('[data-dismiss="modal"]');
                if(elDismiss && htElement.elContainer.contains(elDismiss)){
                    hideDialog();
                }
            });
        }

        /**
         * @param {String} sMessage
         */
        function showDialog(sMessage, sDescription, htOptions){
            htOptions = htOptions || {};

            if(htVar.bIsVueDialog){
                htElement.elContainer.show(sMessage, sDescription, htOptions);
                return;
            }

            htVar.fOnAfterShow = htOptions.fOnAfterShow;
            htVar.fOnAfterHide = htOptions.fOnAfterHide;
            htVar.fOnClickButton = htOptions.fOnClickButton;

            // 커스텀 버튼 옵션이 있으면 버튼을 생성하고, 아니면 기본 버튼만 제공한다
            var sButtonHTML = htOptions.aButtonLabels ?
                _getCustomButtons(htOptions) : htVar.sDefaultButton;

            htElement.elButtons.innerHTML = sButtonHTML;
            htElement.elMessage.innerHTML = $yona.nl2br(sMessage);
            htElement.elDescription.innerHTML = $yona.nl2br(sDescription || "");
            htElement.elContainer.showModal();
            _onShownDialog();
        }

        /**
         * @param htOptions
         * @returns {string}
         * @private
         */
        function _getCustomButtons(htOptions){
            var aButtonsHTML = [];
            var aButtonLabels = htOptions.aButtonLabels;
            var aButtonStyles = htOptions.aButtonStyles || [];

            for(var i = 0, nLength = aButtonLabels.length; i < nLength; i++){
                aButtonsHTML.push($yona.tmpl(htVar.sTplCustomButton, {
                    "text" : aButtonLabels[i],
                    "class": aButtonStyles[i] || (aButtonStyles.length === 0 && i === nLength-1 ? "ybtn-primary" : "ybtn-default")
                }));
            }

            return aButtonsHTML.join("");
        }

        /**
         * @param {Element} elButton
         * @param weEvt
         * @private
         */
        function _onClickButton(elButton, weEvt){
            if(typeof htVar.fOnClickButton === "function"){
                var bResult = htVar.fOnClickButton({
                    "weEvt"       : weEvt,
                    "nButtonIndex": Array.prototype.indexOf.call(htElement.elButtons.children, elButton)
                });

                // fOnClickButton 이 false 를 반환하는 경우
                if(bResult === false){
                    return false;
                }
            }
            hideDialog();
        }

        function hideDialog(){
            if(htVar.bIsVueDialog){
                htElement.elContainer.hide();
                return;
            }
            htElement.elContainer.close();
        }

        function _onShownDialog(){
            if(typeof htVar.fOnAfterShow == "function"){
                htVar.fOnAfterShow();
            }

            if(htVar.bAutoFocusOnLastButton){
                var aPrimaryButtons = htElement.elButtons.querySelectorAll(".ybtn-primary");
                var elFocusTarget = aPrimaryButtons.length ?
                    aPrimaryButtons[aPrimaryButtons.length - 1] : htElement.elButtons.lastElementChild;
                if(elFocusTarget){
                    elFocusTarget.focus();
                }
            }
        }

        function _onHiddenDialog(){
            htElement.elMessage.innerHTML = "";

            if(typeof htVar.fOnAfterHide == "function"){
                htVar.fOnAfterHide();
            }
        }

        _init(sContainer, htOptions || {});

        return {
            "show": showDialog,
            "hide": hideDialog
        };
    };

})("yona.ui.Dialog");
