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

    var htElement = {};

    function _init(){
        // do this except in loginForm, signUpForm
        if(location.pathname.substr(1).split('/')[0] === "users"){
            return;
        }

        _initElement();
        _attachEvent();
    }

    function _initElement(){
        htElement.welDialog = $("#loginDialog");
        htElement.welForm = htElement.welDialog.find("form");
        htElement.welInputId = htElement.welDialog.find("input[name='loginIdOrEmail']");
        htElement.welInputPw = htElement.welDialog.find("input[name='password']");
        htElement.welLoginError = htElement.welDialog.find(".error");
        htElement.welLoginErrorMsg = htElement.welLoginError.find(".error-message");
    }

    function _attachEvent(){
        $(document.body).on('click', '[data-login="required"]', _showDialog);
        htElement.welForm.on('submit', _onSubmitForm);
    }

    function _showDialog(weEvt){
        if(_isInputElement(weEvt.target)){
            $(weEvt.target).blur();
        }

        htElement.welLoginError.hide();
        htElement.welInputPw.val("");
        htElement.welInputId.val("");

        htElement.welDialog.modal("show");
        htElement.welInputId.focus();

        weEvt.preventDefault();
        weEvt.stopPropagation();
        return false;
    }

    function _isInputElement(el){
        return (["INPUT", "TEXTAREA"].indexOf(el.tagName.toUpperCase()) > -1);
    }

    function _onSubmitForm(weEvt){
        $.ajax(htElement.welForm.attr("action"), {
            "type": "post",
            "dataType": "json",
            "data": {
                "loginIdOrEmail" : htElement.welInputId.val(),
                "password": htElement.welInputPw.val()
            }
        }).done(function(){
            document.location.reload();
        }).fail(function(htResult){
            _showDialogError(Messages(htResult.message));
        });

        weEvt.preventDefault();
        weEvt.stopPropagation();
        return false;
    }

    function _showDialogError(sMessage){
        htElement.welLoginErrorMsg.html(sMessage);
        htElement.welLoginError.show();
        htElement.welDialog.effect("shake", {"distance": 2}, 200);
        htElement.welInputId.focus();
    }

    _init();
});
