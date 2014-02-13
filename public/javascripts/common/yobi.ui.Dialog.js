/**
 * @(#)yobi.ui.Dialog.js 2013.04.22
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://yobi.dev.naver.com/license
 */

/**
 * bootstrap-modal.js 에서 제공하는 수동 호출 기능을
 * 사용하기 간편하게 하기 위해 작성한 공통 인터페이스
 * 대화창 레이어 내부에 존재하는 .msg 엘리먼트에
 * 지정한 메시지를 표시하는 기능이 추가되어 있음
 *
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
         * 초기화
         * @param {String} sContainer
         * @param {Hash Table} htOptions
         */
        function _init(sContainer, htOptions){
            _initVar(htOptions);
            _initElement(sContainer);
            _attachEvent();
        }

        /**
         * 변수 초기화
         * @param htOptions
         * @private
         */
        function _initVar(htOptions){
            htVar.sDefaultButton = '<button type="button" class="ybtn ybtn-info" data-dismiss="modal">' + Messages("button.confirm") + '</button>';
            htVar.sTplCustomButton = '<button type="button" class="ybtn ${class}">${text}</button>';
        }

        /**
         * 엘리먼트 초기화
         * @param {String} sContainer
         */
        function _initElement(sContainer){
            htElement.welContainer = $(sContainer).clone();
            htElement.welMessage = htElement.welContainer.find(".msg");
            htElement.welButtons = htElement.welContainer.find(".buttons");
            htElement.welContainer.modal({
                "show": false
            });
        }

        /**
         * 이벤트 설정
         */
        function _attachEvent(){
            htElement.welContainer.on("shown", _onShownDialog);
            htElement.welContainer.on("hidden", _onHiddenDialog);
            htElement.welContainer.on("click", "button.ybtn", _onClickButton);
        }

        /**
         * 메시지 출력
         * @param {String} sMessage
         */
        function showDialog(sMessage, htOptions){
            htVar.fOnAfterShow = htOptions.fOnAfterShow;
            htVar.fOnAfterHide = htOptions.fOnAfterHide;
            htVar.fOnClickButton = htOptions.fOnClickButton;

            // 커스텀 버튼 옵션이 있으면 버튼을 생성하고, 아니면 기본 버튼만 제공한다
            var sButtonHTML = htOptions.aButtonLabels ?
                _getCustomButtons(htOptions) : htVar.sDefaultButton;

            htElement.welButtons.html(sButtonHTML);
            htElement.welMessage.html($yobi.nl2br(sMessage));
            htElement.welContainer.modal("show");
        }

        /**
         * 사용자 옵션에 따른 버튼 HTML 생성
         * @param htOptions
         * @returns {string}
         * @private
         */
        function _getCustomButtons(htOptions){
            var aButtonsHTML = [];
            var aButtonLabels = htOptions.aButtonLabels;
            var aButtonStyles = htOptions.aButtonStyles || [];

            // 1. aButtonStyles 로 지정한 스타일이 있으면 그 스타일을 사용한다.
            // 2. 지정했더라도 라벨수 보다 모자라는 경우 나머지 버튼에는 ybtn-default 스타일을 적용한다.
            // 3. 라벨 버튼 스타일을 지정하지 않았다면 마지막 버튼 스타일을 ybtn-primary 로 적용한다.
            for(var i = 0, nLength = aButtonLabels.length; i < nLength; i++){
                aButtonsHTML.push($yobi.tmpl(htVar.sTplCustomButton, {
                    "text" : aButtonLabels[i],
                    "class": aButtonStyles[i] || (aButtonStyles.length === 0 && i === nLength-1 ? "ybtn-primary" : "ybtn-default")
                }));
            }

            return aButtonsHTML.join("");
        }

        /**
         * 버튼 클릭시 이벤트 핸들러
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

        /**
         * 대화창 닫음
         */
        function hideDialog(){
            htElement.welContainer.modal("hide");
        }

        /**
         * 커스텀 이벤트 핸들러
         */
        function _onShownDialog(){
            if(typeof htVar.fOnAfterShow == "function"){
                htVar.fOnAfterShow();
            }
        }

        /**
         * 대화창 닫고 난 뒤 이벤트 핸들러
         * 콜백함수 지정되어 있으면 실행
         */
        function _onHiddenDialog(){
            htElement.welMessage.html("");

            if(typeof htVar.fOnAfterHide == "function"){
                htVar.fOnAfterHide();
            }
        }

        // 초기화
        _init(sContainer, htOptions || {});

        // 인터페이스
        return {
            "show": showDialog,
            "hide": hideDialog
        };
    };

})("yobi.ui.Dialog");
