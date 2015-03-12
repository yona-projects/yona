/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
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

        function _init(htOptions){
            _initVar(htOptions);
            _initElement(htOptions);
            _attachEvent();

            _initFormValidator();
        }

        /**
         * initialize variables
         */
        function _initVar(htOptions){
            htVar.sFormName = htOptions.sFormName || "new-org";
            htVar.rxOrgName = /^[a-zA-Z0-9-]+([_.][a-zA-Z0-9-]+)*$/;
        }

        /**
         * initialize element
         */
        function _initElement(htOptions){
            htElement.welInputOrgName = $("#name");
            htElement.welInputOrgName.focus();
        }

        /**
         * attach event handler
         */
        function _attachEvent(){

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
                var oForm = $(document.forms[htVar.sFormName]);
                var oElement = oForm.find("input[name=name]");
                var sOrgName = oElement.val();
                if(!htVar.rxOrgName.test(sOrgName)){
                    aErrors.push({
                        id: oElement.attr("id"),
                        name: oElement.attr("name"),
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
                $('span.warning').hide();
                $('span.msg').html(aErrors[0].message).show();
            } else {
                NProgress.start();
            }
        }

        _init(htOptions || {});
    };

})("yobi.organization.New");
