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
            _initVar(htOptions);
            _initElement(htOptions);
            _initDatePicker();
            _attachEvent();
            _initFileUploader();
        }

        /**
         * initialize variables
         */
        function _initVar(htOptions){
            htVar.sDateFormat  = htOptions.sDateFormat  || "YYYY-MM-DD";
            htVar.rxDateFormat = htOptions.rxDateFormat || /\d{4}-\d{2}-\d{2}$/;
            htVar.sTplFileItem = $('#tplAttachedFile').text();
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.welForm = $("form.nm");
            htElement.welDatePicker   = $(htOptions.elDatePicker);
            htElement.welInputDueDate = $(htOptions.elDueDate);
            htElement.welInputTitle   = $('#title');
            htElement.welInputContent = $('textarea[data-editor-mode="content-body"]');
            htElement.welUploader = $(htOptions.elUploader || "#upload");
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welForm.submit(_onSubmitForm);
        }

        /**
         * on submit form
         */
        function _onSubmitForm(weEvt){
            return _validateForm();
        }

        function _validateForm(){
            var sTitle = $.trim(htElement.welInputTitle.val());
            var sContent = $.trim(htElement.welInputContent.val());
            var sDueDate = $.trim(htElement.welInputDueDate.val());

            if(sTitle.length === 0){
                $yobi.showAlert(Messages("milestone.error.title"));
                return false;
            }

            if(sContent.length === 0){
                $yobi.showAlert(Messages("milestone.error.content"));
                return false;
            }

            if(sDueDate.length > 0 && htVar.rxDateFormat.test(sDueDate) === false){
                $yobi.showAlert(Messages("milestone.error.duedateFormat"));
                return false;
            }

            return true;
        }

        /**
         * initialize DatePicker
         * @requires Pikaday
         */
        function _initDatePicker(){
            if(typeof Pikaday != "function"){
                console.log("[Yobi] Pikaday required (https://github.com/dbushell/Pikaday)");
                return false;
            }

            // append Pikaday element to DatePicker
            htVar.oPicker = new Pikaday({
                "format" : htVar.sDateFormat,
                "onSelect" : function(oDate) {
                    htElement.welInputDueDate.val(this.toString());
                }
            });
            htElement.welDatePicker.append(htVar.oPicker.el);

            // fill DatePicker date to InputDueDate if empty
            // or set DatePicker date with InputDueDate
            var sDueDate = htElement.welInputDueDate.val();
            if(sDueDate.length > 0){
                htVar.oPicker.setDate(sDueDate);
            }

            // set relative event between dueDate input and datePicker
            htElement.welInputDueDate.blur(function() {
                htVar.oPicker.setDate(this.value);
            });
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            var oUploader = yobi.Files.getUploader(htElement.welUploader, htElement.welInputContent);

            if(oUploader){
                (new yobi.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welInputContent,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : oUploader.attr("data-namespace")
                }));
            }
        }

        _init(htOptions || {});
    };

})("yobi.milestone.Write");
