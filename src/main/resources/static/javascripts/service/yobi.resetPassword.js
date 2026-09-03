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
    oNS.container[oNS.name] = function(){

        var htVar = {};
        var htElement = {};

        /**
         * initialize
         */
        function _init(){
            _initElement();
            _initFormValidator();
            _attachEvent();
        }

        /**
         * initialize elements
         */
        function _initElement(){
            htElement.welInputPassword  = $('#password');
            htElement.welInputPassword2 = $('#retypedPassword');

            htElement.welForm = $("form[name=passwordReset]");
        }

        /**
         * attach event
         */
        function _attachEvent(){
            htElement.welInputPassword.focusout(_onBlurInputPassword);
            htElement.welInputPassword2.focusout(_onBlurInputPassword);
        }


        function _onBlurInputPassword(){
            htVar.oValidator._validateForm();
        }

        /**
         * initialize FormValidator
         * @require validate.js
         */
        function _initFormValidator(){
            var aRules = [
                  {"name": 'password',        "rules": 'required|min_length[4]'},
                  {"name": 'retypedPassword', "rules": 'required|matches[password]'}
              ];

            htVar.oValidator = new FormValidator('passwordReset', aRules, _onFormValidate);

            // set error message
            htVar.oValidator.setMessage('required',    Messages("validation.required"));
            htVar.oValidator.setMessage('min_length',  Messages("validation.tooShortPassword"));
            htVar.oValidator.setMessage('matches',     Messages("validation.passwordMismatch"));
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

        _init();
    };
})("yobi.resetPassword");
