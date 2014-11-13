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
    var clientErrorStatus = /^4[0-9][0-9]$/
    var serverErrorStatus = /^5[0-9][0-9]$/
    var networkErrorStatus = 0;

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
        htElement.welInputRememberMe = htElement.welDialog.find("input[name='rememberMe']");
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
                "password": htElement.welInputPw.val(),
                "rememberMe": htElement.welInputRememberMe.is(":checked")
            }
        }).done(function(){
            document.location.reload();
        }).fail(function(htResult){
            // If the value of readyState is UNSET(zero), it will be viewed as 'Network Error'
            if(htResult.readyState == networkErrorStatus){
                _showDialogError(Messages("user.login.failed.network"));
            }else if(htResult.responseText && htResult.responseText.length > 0){
                try{
                    var responseObject = JSON.parse(htResult.responseText);
                    _showDialogError(Messages(responseObject.message));
                }catch (err){
                    _getErrorMessageByStatus(htResult.status);
                }
            }else{
                _getErrorMessageByStatus(htResult.status);
            }
        });

        weEvt.preventDefault();
        weEvt.stopPropagation();
        return false;
    }

    function _getErrorMessageByStatus(status) {
        switch(true){
            case clientErrorStatus.test(status):
                _showDialogError(Messages("user.login.failed.client"));
                break;
            case serverErrorStatus.test(status):
                _showDialogError(Messages("user.login.failed.server"));
                break;
            default:
                _showDialogError(Messages("user.login.failed"));
                break;
        }
    }

    function _showDialogError(sMessage){
        htElement.welLoginErrorMsg.html(sMessage);
        htElement.welLoginError.show();
        htElement.welDialog.effect("shake", {"distance": 2}, 200);
        htElement.welInputId.focus();
    }

    _init();
});
