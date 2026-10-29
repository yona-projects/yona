/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

$(function(){

    "use strict";

    var elements = {};

    /**
     * Initialize
     */
    function init(){
        _initElement();
        _attachEvent();
    }

    /**
     * Initialize element variables
     * @private
     */
    function _initElement(){
        elements.commentForm = $("#comment-form");
        elements.textarea = elements.commentForm.find("textarea");
    }

    /**
     * Attach event handlers
     */
    function _attachEvent(){
        elements.commentForm.submit(onSubmitCommentForm);
        // 임시 저장 리뷰(X-26, decisions.md D-38 9번)의 "리뷰 시작/리뷰에 추가" 단추: 이 스크립트는 폼을 `submit()`으로 다시 보내 눌린 단추의 값이 빠지므로
        // 단추를 누를 때 숨은 입력(`pending=true`)으로 옮겨 둔다. 다른 제출 단추를 누르면 지운다
        elements.commentForm.on("click", "button[type=submit]", function(){
            elements.commentForm.find("input.pending-flag").remove();
            if(this.name === "pending"){
                $('<input type="hidden" class="pending-flag" name="pending">').val(this.value).appendTo(elements.commentForm);
            }
        });
        $(window).on("keydown",  onKeydownWindow);
        $(window).on("beforeunload", onBeforeUnloadWindow);
        temporarySaveHandler(elements.textarea);
    }

    /**
     * Handles submit event of commentForm
     *
     * @returns {boolean}
     */
    function onSubmitCommentForm(event){
        removeCurrentPageTemprarySavedContent();
        elements.textarea.off();
        clearTimeout(window.draftSavingTimeout);

        event.preventDefault();
        var that = this;

        if(isCommentBodyEmpty()){
            $yobi.notify(Messages("post.comment.empty"), 3000);
            elements.textarea.focus();
            return false;
        }

        if(isOnSubmit()){
            return false;
        }

        elements.commentForm.data("onsubmit", true);

        NProgress.start();

        setTimeout(function () {
            that.submit();
        }, 300);
    }

    /**
     * Returns true if comment body is empty
     * Space characters(\s) will be excluded from count string length.
     *
     * @returns {boolean}
     */
    function isCommentBodyEmpty(){
        return !(elements.textarea.val().trim().length);
    }

    /**
     * Returns true if comment form is marked as "onsubmit"
     *
     * @returns {*}
     */
    function isOnSubmit(){
        return elements.commentForm.data("onsubmit");
    }

    /**
     * Handles keydown event of window
     * Hide spinner when form submit has cancelled with ESC key in WebBrowser.
     *
     * @param evt
     */
    function onKeydownWindow(evt){
        if (isEscapeKeyPressed(evt) && isOnSubmit()){
            NProgress.done();
            elements.commentForm.data("onsubmit", false);
        }
    }

    /**
     * Handles beforeunload event of window
     * In case of commentBody is not empty, show confirm to exit page.
     * Browser will shows confirm dialog with returned message string.
     */
    function onBeforeUnloadWindow(){
        if(!isCommentBodyEmpty() && !isOnSubmit()){
            return Messages("common.comment.beforeunload.confirm");
        }
    }

    /**
     * Returns whether ESC(keyCode: 27) has pressed from event
     *
     * @param weEvt
     * @returns {boolean}
     */
    function isEscapeKeyPressed(evt){
        return (evt.keyCode && evt.keyCode === 27);
    }

    init();
});
