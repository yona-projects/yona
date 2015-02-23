/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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
        elements.commentForm.on("submit", onSubmitCommentForm);
        $(window).on("keydown",  onKeydownWindow);
        $(window).on("beforeunload", onBeforeUnloadWindow);
    }

    /**
     * Handles submit event of commentForm
     *
     * @returns {boolean}
     */
    function onSubmitCommentForm(){
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
        return true;
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
