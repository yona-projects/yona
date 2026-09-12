/**
 * Yona, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Deokhong Kim
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

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        /**
         * initialize
         */
        function _init(htOptions){
            _initVar(htOptions);
            _initElement();
            _attachEvent();

            _initFormValidator();
        }

        /**
         * initialize variables
         */
        function _initVar(htOptions){
            htVar.sFormName = htOptions.sFormName || "update-org";
            htVar.rxOrgName = /^[a-zA-Z0-9-가-힣]+([_.][a-zA-Z0-9-가-힣]+)*$/;
        }

        /**
         * initialize element variables
         */
        function _initElement(){
            htElement.welForm = document.querySelector("form#saveSetting");
            htElement.welInputLogo = document.getElementById("logoPath");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welInputLogo.addEventListener("change", _onChangeLogoPath);
        }

        function _onChangeLogoPath(){
            var welTarget = this;

            if($yona.isImageFile(welTarget) === false){
                $yona.showAlert(Messages("project.logo.alert"));
                welTarget.value = '';
                return;
            }

            htElement.welForm.submit();
        }

        /**
         * initialize formValidator
         * @require validate.js
         */
        function _initFormValidator(){
            // name : name of input element
            // rules: rules to apply to the input element.
            var aRules = [];

            htVar.oValidator = new FormValidator(htVar.sFormName, aRules, function(aErrors){
                var oForm = document.forms[htVar.sFormName];
                var oElement = oForm.querySelector("input[name=name]");
                var sOrgName = oElement.value;
                if(!htVar.rxOrgName.test(sOrgName)){
                    aErrors.push({
                        id: oElement.id,
                        name: oElement.name,
                        message: Messages("organization.name.alert")
                    });
                }
                _onFormValidate(aErrors);
            });
        }

        /**
         * handler for validation errors.
         */
        function _onFormValidate(aErrors){
            if(aErrors.length > 0){
                document.querySelectorAll('span.warning').forEach(function(el){ el.style.display = "none"; });
                document.querySelectorAll('span.msg').forEach(function(el){
                    el.innerHTML = aErrors[0].message;
                    el.style.display = "";
                });
            } else {
                NProgress.start();
            }
        }

        _init(htOptions || {});
    };

})("yona.organization.Setting");
