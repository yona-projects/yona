/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

document.addEventListener("DOMContentLoaded", function(){

    "use strict";

    var elements = {};
    var bOnSubmit = false;

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
        elements.commentForm = document.getElementById("comment-form");
        elements.textarea = elements.commentForm ? elements.commentForm.querySelector("textarea") : null;
    }

    /**
     * Attach event handlers
     */
    function _attachEvent(){
        if(!elements.commentForm){
            return;
        }
        elements.commentForm.addEventListener("submit", onSubmitCommentForm);
        window.addEventListener("keydown", onKeydownWindow);
        window.addEventListener("beforeunload", onBeforeUnloadWindow);
        temporarySaveHandler(elements.textarea);
    }

    /**
     * Handles submit event of commentForm
     *
     * @returns {boolean}
     */
    function onSubmitCommentForm(event){
        removeCurrentPageTemprarySavedContent();
        clearTimeout(window.draftSavingTimeout);

        event.preventDefault();
        var that = event.target;

        if(isCommentBodyEmpty()){
            $yona.notify(Messages("post.comment.empty"), 3000);
            elements.textarea.focus();
            return false;
        }

        if(isOnSubmit()){
            return false;
        }

        bOnSubmit = true;

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
        return !(elements.textarea.value.trim().length);
    }

    /**
     * Returns true if comment form is marked as "onsubmit"
     *
     * @returns {*}
     */
    function isOnSubmit(){
        return bOnSubmit;
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
            bOnSubmit = false;
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
