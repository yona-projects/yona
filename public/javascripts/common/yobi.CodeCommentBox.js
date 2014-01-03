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

    function _initVar(htOptions){
        htVar.fOnAfterShow = htOptions.fOnAfterShow;
        htVar.fOnAfterHide = htOptions.fOnAfterHide;
        htVar.sTplFileItem = htOptions.sTplFileItem || $('#tplAttachedFile').text();
        htVar.htArrowPlacement = {
            "top": "bottom",
            "bottom": "top"
        };
    }

    function _initElement(){
        htElement.welCommentWrap = $("#review-form");
        htElement.welCommentForm = _getEmptyCommentForm(htElement.welCommentWrap.find("form"));
        htElement.welCommentTextarea = htElement.welCommentForm.find("textarea.comment");
        htElement.welCommentUploader = htElement.welCommentForm.find(".upload-wrap");
    }

    function _attachEvent(){
        htElement.welCommentForm.on("click", '[data-toggle="close"]', function(){
            _hide();
        });
    }

    /**
     * 필요한 INPUT(type=hidden) 필드가 존재하는 댓글 폼을 반환한다
     *
     * @returns {*|jQuery|HTMLElement}
     * @private
     */
    function _getEmptyCommentForm(welForm){
        var aInput = [];
        var aFields = ["path", "line", "side", "commitA", "commitB", "blockInfo"];
        var welHidden = $('<input type="hidden">');

        aFields.forEach(function(sFieldName){
            var welField = welForm.find('input[name="' + sFieldName + '"]');

            if(welField.length === 0){
                aInput.push(welHidden.clone().attr("name", sFieldName)); // append new field
            } else {
                welField.val(""); // empty previous value
            }
        });

        welForm.append(aInput);
        return welForm;
    }

    /**
     * welTr 을 기준으로 리뷰 작성 폼을 표시한다
     *
     * @param {Object} welTr
     */
    function _show(welTr, htOptions) {
        htOptions = htOptions || {};

        var welTarget = welTr;

        if(typeof welTarget.data("line") === "undefined"){
            welTarget = welTr.prevUntil("tr[data-line]");
        }

        var sType = welTarget.data("type");
        var welDiffContainer = welTarget.closest(".diff-container"); // = table
        var htData = {
            "line"     : welTarget.data("line"),
            "side"     : (sType === 'remove') ? 'A' : 'B',
            "blockInfo": welTarget.data("blockInfo"),
            "commitA"  : welDiffContainer.data("commitA"),
            "commitB"  : welDiffContainer.data("commitB"),
            "path"     : welDiffContainer.data(sType === "remove" ? "path-a" : "path-b")
        };
        // TODO: 서버 전송할 필드에 따라 불필요한 정보는 제거할 필요 있음 (대부분의 정보는 blockInfo 에 포함하고 있어서)

        // show form and fill fields
        // sPlacement means where to show commentBox from welTr (top or bottom)
        // sArrowPlacement means where to show arrow on commentBox (opposite side to sPlacement)
        var sPlacement = (htOptions.sPlacement || "bottom").toLowerCase();
        var sArrowPlacement = htVar.htArrowPlacement[sPlacement];
        var nAdjustmentTop = (sPlacement === "bottom") ? (welTr.height() + 10)
                               : -1 * (htElement.welCommentWrap.height() + 30);
        var nTop = welTr.position().top + nAdjustmentTop;

        htElement.welCommentWrap.removeClass("arrow-top arrow-bottom")
                                .addClass("arrow-" + sArrowPlacement);
        htElement.welCommentWrap.css("top", nTop + "px");
        htElement.welCommentWrap.show();
        htElement.welCommentTextarea.focus();
        _setReviewFormFields(htElement.welCommentForm, htData);

        // run callback function
        if(typeof htOptions.fCallback === "function"){
            htOptions.fCallback();
        }

        if(typeof htVar.fOnAfterShow === "function"){
            htVar.fOnAfterShow();
        }
    }

    /**
     * welForm 을 htData 를 기준으로 폼 데이터를 채운다
     *
     * @param welForm
     * @param htData
     * @private
     */
    function _setReviewFormFields(welForm, htData){
        var welInput;

        for(var sFieldName in htData){
            welInput = welForm.find('input[name="' + sFieldName + '"]');

            if(welInput.length === 1){
                welInput.val(htData[sFieldName]);
            }
        }
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
        "toggle": _toggleVisibility
    };
})();
