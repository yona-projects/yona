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
/**
 * @example
 * var oDialog = new yobi.ui.Dialog("#yobiDialog")
 * oDialog.show("메시지");
 *
 * @require bootstrap-modal.js
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
            htElement.welContainer = $(sContainer).clone();
            htElement.welMessage = htElement.welContainer.find(".msg");
            htElement.welDescription = htElement.welContainer.find(".desc");
            htElement.welButtons = htElement.welContainer.find(".buttons");
            htElement.welContainer.modal({
                "show": false
            });
        }

        function _attachEvent(){
            htElement.welContainer.on("shown", _onShownDialog);
            htElement.welContainer.on("hidden", _onHiddenDialog);
            htElement.welContainer.on("click", "button.ybtn", _onClickButton);
        }

        /**
         * @param {String} sMessage
         */
        function showDialog(sMessage, sDescription, htOptions){
            htVar.fOnAfterShow = htOptions.fOnAfterShow;
            htVar.fOnAfterHide = htOptions.fOnAfterHide;
            htVar.fOnClickButton = htOptions.fOnClickButton;

            // 커스텀 버튼 옵션이 있으면 버튼을 생성하고, 아니면 기본 버튼만 제공한다
            var sButtonHTML = htOptions.aButtonLabels ?
                _getCustomButtons(htOptions) : htVar.sDefaultButton;

            htElement.welButtons.html(sButtonHTML);
            htElement.welMessage.html($yobi.nl2br(sMessage));
            htElement.welDescription.html($yobi.nl2br(sDescription || ""));
            htElement.welContainer.modal("show");
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
                aButtonsHTML.push($yobi.tmpl(htVar.sTplCustomButton, {
                    "text" : aButtonLabels[i],
                    "class": aButtonStyles[i] || (aButtonStyles.length === 0 && i === nLength-1 ? "ybtn-primary" : "ybtn-default")
                }));
            }

            return aButtonsHTML.join("");
        }

        /**
         * @param weEvt
         * @private
         */
        function _onClickButton(weEvt){
            if(typeof htVar.fOnClickButton === "function"){
                var bResult = htVar.fOnClickButton({
                    "weEvt"       : weEvt,
                    "nButtonIndex": $(this).index()
                });

                // fOnClickButton 이 false 를 반환하는 경우
                if(bResult === false){
                    return false;
                }
            }
            hideDialog();
        }

        function hideDialog(){
            htElement.welContainer.modal("hide");
        }

        function _onShownDialog(){
            if(typeof htVar.fOnAfterShow == "function"){
                htVar.fOnAfterShow();
            }

            if(htVar.bAutoFocusOnLastButton){
                htElement.welButtons.find(".ybtn-primary:last,button:last").focus();
            }
        }

        function _onHiddenDialog(){
            htElement.welMessage.html("");

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

})("yobi.ui.Dialog");
