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
(function(ns){

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        /**
         * initialize
         */
        function _init(htOptions){
            _initElement();
            _initVar(htOptions);

            _initFormValidator();
            _attachEvent();
        }

        /**
         * initialize elements
         */
        function _initElement(){
            htElement.welInputPassword  = $('#password');
            htElement.welInputPassword2 = $('#retypedPassword');
            htElement.welInputEmail     = $('#email');
            htElement.welInputLoginId   = $('#loginId');
            htElement.welInputLoginId.focus();

            htElement.welForm = $("form[name=signup]");

        }

        /**
         * initialize variables
         */
        function _initVar(htOptions){
            htVar.rxLoginId = /^[a-zA-Z0-9-]+([_.][a-zA-Z0-9-]+)*$/;
            htVar.sLogindIdCheckUrl = htOptions.sLogindIdCheckUrl;
            htVar.sEmailCheckUrl = htOptions.sEmailCheckUrl;
        }

        /**
         * attach event
         */
        function _attachEvent(){
            htElement.welInputLoginId.on('focusout', _onBlurInputLoginId);
            htElement.welInputEmail.on('focusout', _onBlurInputEmail);
            htElement.welInputPassword.on('keyup', _onValidInputPassword);
            htElement.welInputPassword2.on('keyup', _onValidInputPasswordCheck);
        }

        function _onBlurInputLoginId(){
            var welInput = $(this);
            var sLoginId = $yobi.getTrim(welInput.val()).toLowerCase();
            welInput.val(sLoginId);

            if(_onValidateLoginId(sLoginId) === false){
                showErrorMessage(welInput, Messages("validation.allowedCharsForLoginId"));
                return false;
            }

            if(sLoginId != ""){
                doesExists($(this), htVar.sLogindIdCheckUrl);
            }
        }

        function _onBlurInputEmail(){

            var welInput = $(this);

            if($.trim(welInput.val()) !== ""){
                doesExists(welInput, htVar.sEmailCheckUrl);
            }
        }

        function _onValidInputPassword(){

            clearTimeout(htVar.oDelay);

            htVar.oDelay = setTimeout(checkPassword, 300);
        }

        function checkPassword() {
          hideErrorMessage(htElement.welInputPassword);

          if($.trim(htElement.welInputPassword.val()).length < 4) {
            showErrorMessage(htElement.welInputPassword, Messages("validation.tooShortPassword"));
          }

          checkPasswordConfirm();
        }

        function _onValidInputPasswordCheck() {
          clearTimeout(htVar.oDelay);

          htVar.oDelay = setTimeout(checkPasswordConfirm, 200);
        }

        function checkPasswordConfirm() {
          hideErrorMessage(htElement.welInputPassword2);

          if (htElement.welInputPassword2.val() !== htElement.welInputPassword.val()) {
            showErrorMessage(htElement.welInputPassword2, Messages("validation.passwordMismatch"));
          }
        }

        /**
         * @param {Wrapped Element} welInput
         * @param {String} sURL
         */
        function doesExists(welInput, sURL){

            $.ajax({
                "url": sURL + welInput.val()
            }).done(function(htData){
                if(htData.isExist === true){
                    showErrorMessage(welInput, Messages("validation.duplicated"));
                } else if (htData.isReserved == true) {
                    showErrorMessage(welInput, Messages("validation.reservedWord"));
                } else {
                    hideErrorMessage(welInput);
                }
            });
        }

        /**
         * initialize FormValidator
         * @require validate.js
         */
        function _initFormValidator(){
            var aRules = [
                {"name": 'loginId',         "rules": 'required|callback_check_loginId'},
                {"name": 'email',           "rules": 'required|valid_email'},
                {"name": 'password',        "rules": 'required|min_length[4]'},
                {"name": 'retypedPassword', "rules": 'required|matches[password]'}
            ];

            htVar.oValidator = new FormValidator('signup', aRules, _onFormValidate);
            htVar.oValidator.registerCallback('check_loginId', _onValidateLoginId);

            // set error message
            htVar.oValidator.setMessage('check_loginId', Messages("validation.allowedCharsForLoginId"));
            htVar.oValidator.setMessage('required',      Messages("validation.required"));
            htVar.oValidator.setMessage('min_length',    Messages("validation.tooShortPassword"));
            htVar.oValidator.setMessage('matches',       Messages("validation.passwordMismatch"));
            htVar.oValidator.setMessage('valid_email',   Messages("validation.invalidEmail"));
        }

        /**
         * login id validation
         * @param {String} sLoginId
         * @return {Boolean}
         */
        function _onValidateLoginId(sLoginId){
            return htVar.rxLoginId.test(sLoginId);
        }

        /**
         * on validate form
         * @param {Array} aErrors
         */
        function _onFormValidate(aErrors){
            var welTarget;

            aErrors.forEach(function(htError){
                welTarget = htElement.welForm.find("input[name=" + htError.name + "]");
                if(welTarget){
                    showErrorMessage(welTarget, htError.message);
                }
            });
        }

        function showErrorMessage(welInput, sMessage){
            welInput.popover({
                "trigger": "manual",
                "placement": "left",
                "content": sMessage
            }).popover("show");
        }

        function hideErrorMessage(welInput){
            welInput.popover("hide");

            try{
                welInput.popover("destroy");
            } catch(e){} // to avoid bootstrap bug
        }

        _init(htOptions || {});
    };
})("yobi.user.SignUp");
