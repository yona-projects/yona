/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
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

/**
 * 기존의 댓글 상자를 코드 댓글 상자로 만들어준다.
 *
 * 코드 주고받기 메뉴의 개요 탭에서도 코드에 댓글을 달 수
 * 있도록 하기 위해 yobi.Code.Diff.js의 일부를 뽑아내어
 * 구현하였다. 그러나 view에 의존성이 매우 크기 때문에
 * yobi.Code.Diff.js와 yobi.git.View.js이외에서 사용하려면 많은
 * 수정이 필요할 것이다.
 */

yobi = yobi || {};

yobi.CodeCommentBox = (function(){
    "use strict";

    var htVar = {};
    var htElement = {};

    /**
     * 초기화
     *
     * @param htOptions
     * @private
     */
    function _init(htOptions){
        _initVar(htOptions);
        _initElement();
        _attachEvent();

        _initFileUploader();
    }

    /**
     * Initialize Variables
     * @param htOptions
     * @private
     */
    function _initVar(htOptions){
        htVar.fOnAfterShow = htOptions.fOnAfterShow;
        htVar.fOnAfterHide = htOptions.fOnAfterHide;
        htVar.sTplFileItem = htOptions.sTplFileItem || $('#tplAttachedFile').text();
        htVar.htArrowPlacement = {
            "top": "bottom",
            "bottom": "top"
        };
    }

    /**
     * Initialize Elements
     * @private
     */
    function _initElement(){
        htElement.welCommentWrap = $("#review-form");
        htElement.welCommentForm = htElement.welCommentWrap.find("form");
        htElement.welCommentTextarea = htElement.welCommentForm.find("textarea.comment");
        htElement.welCommentUploader = htElement.welCommentForm.find(".upload-wrap");
    }

    /**
     * Attach event handlers
     * @private
     */
    function _attachEvent(){
        htElement.welCommentForm.on("click", '[data-toggle="close"]', function(){
            _hide();
        });
    }

    /**
     * welTr 을 기준으로 리뷰 작성 폼을 표시한다
     *
     * @param {Object} welTr
     */
    function _show(welTr, htOptions) {
        htOptions = htOptions || {};

        var welTarget = welTr;
        var sThreadId = welTarget.data("thread-id");

        // 기존 스레드에 댓글을 추가하는 버튼이면
        if(typeof sThreadId !== "undefined"){
            _setReviewFormFields({
                "thread.id": sThreadId
            });
        } else {
            if(typeof welTarget.data("line") === "undefined"){
                welTarget = welTr.prevUntil("tr[data-line]");
            }

            // set form field values
            var htBlockInfo = welTarget.data("blockInfo");
            var htData = _getFormFieldsFromBlockInfo(htBlockInfo);
            _setReviewFormFields(htData);
        }

        // show comment form
        // sPlacement means where to show commentBox from welTr (top or bottom)
        // sArrowPlacement means where to show arrow on commentBox (opposite side to sPlacement)
        var sPlacement = (htOptions.sPlacement || "bottom").toLowerCase();
        var sArrowPlacement = htVar.htArrowPlacement[sPlacement];
        var nAdjustmentTop = (sPlacement === "bottom") ? (welTr.height() + 10)
                               : -1 * (htElement.welCommentWrap.height() + 30);
        nAdjustmentTop += (htOptions.nAdjustmentTop || 0);

        var nTop = welTr.position().top + nAdjustmentTop;

        htElement.welCommentWrap.removeClass("arrow-top arrow-bottom")
                                .addClass("arrow-" + sArrowPlacement);
        htElement.welCommentWrap.css("top", nTop + "px");
        htElement.welCommentWrap.show();
        htElement.welCommentTextarea.focus();

        // run callback function
        if(typeof htOptions.fCallback === "function"){
            htOptions.fCallback();
        }

        if(typeof htVar.fOnAfterShow === "function"){
            htVar.fOnAfterShow();
        }
    }

    /**
     * 블록 정보를 Form 전송을 위한 데이터로 만든다.
     * 불필요한 항목은 제거하고, 필드명도 다듬어서 반환
     *
     * @param htBlockInfo
     * @returns {{}}
     * @private
     */
    function _getFormFieldsFromBlockInfo(htBlockInfo){
        var sNewKey;
        var htData = {};
        var aBlockWords = ["bIsReversed"];

        for(var sKey in htBlockInfo){
            // 특정한 항목은 폼 데이터에 넣지 않는다
            if(aBlockWords.indexOf(sKey) > -1){
                continue;
            }

            sNewKey = sKey.substr(1,1).toLowerCase() + sKey.substring(2);
            htData[sNewKey] = htBlockInfo[sKey];
        }

        return htData;
    }

    /**
     * welForm 을 htData 를 기준으로 폼 데이터를 채운다
     * input(type="hidden")이 존재하면 값을 지정하고
     * 존재하지 않으면 새롭게 만들어서 폼에 추가한다
     *
     * @param welForm
     * @param htData
     * @private
     */
    function _setReviewFormFields(htData){
        var aInput = [];
        var welField, elField, sFieldName;
        var welForm = htElement.welCommentForm;

        // 필드가 없으면 만들고, 있으면 값 지정
        for(sFieldName in htData){
            welField = welForm.find('input[type="hidden"][name="' + sFieldName + '"]');

            if(welField.length === 0){
                elField = _getHiddenField(sFieldName, htData[sFieldName]);
                aInput.push(elField); // append new field
            } else {
                welField.val(htData[sFieldName]);
            }
        }

        // prepend new INPUT elements to welForm
        if(aInput.length > 0){
            welForm.prepend(aInput);
        }
    }

    /**
     * name=sFieldName,value=sFieldValue 인
     * hidden type input 엘리먼트를 반환한다
     *
     * @param sFieldName
     * @param sFieldValue
     * @returns {HTMLElement}
     * @private
     */
    function _getHiddenField(sFieldName, sFieldValue){
        var elInput = document.createElement("INPUT");
        elInput.setAttribute("name", sFieldName);
        elInput.setAttribute("type", "hidden");
        elInput.value = sFieldValue;
        return elInput;
    }

    /**
     * 댓글 상자를 숨긴다.
     *
     * @param htOptions.fCallback
     * @private
     */
    function _hide(htOptions){
        htOptions = htOptions || {};
        htElement.welCommentWrap.hide();

        // run callback function
        if(typeof htOptions.fCallback === "function"){
            htOptions.fCallback();
        }

        if(typeof htVar.fOnAfterHide === "function"){
            htVar.fOnAfterHide();
        }
    }

    /**
     * 댓글 상자 표시 토글
     *
     * @param welTr
     * @param fCallback
     * @private
     */
    function _toggleVisibility(welTr, htOptions){
       if(htElement.welCommentWrap.css("display") === "block"){
           _hide(htOptions);
       } else {
           _show(welTr, htOptions);
       }
    }

    /**
     * 댓글 상자가 표시되고 있는지를 반환
     *
     * @returns {boolean}
     * @private
     */
    function _isVisible(){
        return (htElement.welCommentWrap &&
                htElement.welCommentWrap.css("display") === "block");
    }

    /**
     * 댓글 상자에 파일 업로더를 설정한다
     *
     * @private
     */
    function _initFileUploader(){
        var oUploader = yobi.Files.getUploader(htElement.welCommentUploader, htElement.welCommentTextarea);

        if(oUploader){
            (new yobi.Attachments({
                "elContainer"  : htElement.welCommentUploader,
                "elTextarea"   : htElement.welCommentTextarea,
                "sTplFileItem" : htVar.sTplFileItem,
                "sUploaderId"  : oUploader.attr("data-namespace")
            }));
        }
    }

    // public interface
    return {
        "init"  : _init,
        "show"  : _show,
        "hide"  : _hide,
        "toggle": _toggleVisibility,
        "isVisible": _isVisible
    };
})();
