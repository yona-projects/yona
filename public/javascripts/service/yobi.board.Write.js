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
            _initElement(htOptions || {});
            _initVar(htOptions || {});
            _attachEvent();

            _initFileUploader();
            $("#title").focus();
        }

        /**
         * initialize variable
         */
        function _initVar(htOptions){
            htVar.sMode = htOptions.sMode || "new";
            htVar.sTplFileItem = htOptions.sTplFileItem || (htElement.welTplFileItem ? htElement.welTplFileItem.text() : "");
        }

        /**
         * initialize element variable
         */
        function _initElement(htOptions){
            htElement.welUploader = $(htOptions.elTarget || "#upload");
            htElement.welTextarea = $(htOptions.elTextarea || "#body");
            htElement.welTplFileItem = $('#tplAttachedFile');

            // Validate
            htElement.welForm = $("form");
            htElement.welInputTitle = $("input#title");
        }

        /**
         * attach event handler : for validate form
         */
        function _attachEvent(){
            htElement.welForm.submit(_onSubmitForm);

            htElement.welTextarea.on("focus", function(){
                $(window).on("beforeunload", _onBeforeUnload);
            });
        }

        function _onBeforeUnload(){
            if($yobi.getTrim(htElement.welTextarea.val()).length > 0){
                return Messages("post.error.beforeunload");
            }
        }

        /**
         * Validate form on submit
         */
        function _onSubmitForm(){
            if(htElement.welInputTitle.val() == ""){
                $yobi.showAlert(Messages("post.error.emptyTitle"));
                return false;
            }

            $(window).off("beforeunload", _onBeforeUnload);
            return true;
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            var oUploader = yobi.Files.getUploader(htElement.welUploader, htElement.welTextarea);

            if(oUploader){
                (new yobi.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welTextarea,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : oUploader.attr("data-namespace")
                }));
            }
        }

        _init(htOptions);
    };

})("yobi.board.Write");
