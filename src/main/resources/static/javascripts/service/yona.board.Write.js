/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

(function(ns){

    var oNS = $yona.createNamespace(ns);
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
            htElement.welInputTitle.focus();
            htElement.welInputTitle.addEventListener('keydown', function (e) {
                if((e.keyCode || e.which) === 13) {
                    e.preventDefault();
                    htElement.welTextarea.focus();
                }
            });
        }

        /**
         * initialize variable
         */
        function _initVar(htOptions){
            htVar.sMode = htOptions.sMode || "new";
            htVar.sTplFileItem = htOptions.sTplFileItem || (htElement.welTplFileItem ? htElement.welTplFileItem.textContent : "");
        }

        /**
         * initialize element variable
         */
        function _initElement(htOptions){
            htElement.welUploader = document.querySelector(htOptions.elTarget || "#upload");
            htElement.welTextarea = document.querySelector(htOptions.elTextarea || "#body");
            htElement.welTplFileItem = document.getElementById('tplAttachedFile');

            // Validate
            htElement.welForms = document.querySelectorAll("form");
            htElement.welInputTitle = document.querySelector("input#title");
        }

        /**
         * attach event handler : for validate form
         */
        function _attachEvent(){
            htElement.welForms.forEach(function(form){ form.addEventListener("submit", _onSubmitForm); });

            temporarySaveHandler(htElement.welTextarea);

            htElement.welTextarea.addEventListener("focus", function(){
                window.addEventListener("beforeunload", _onBeforeUnload);
            });
        }

        function _onBeforeUnload(){
            if($yona.getTrim(htElement.welTextarea.value).length > 0){
                return Messages("post.error.beforeunload");
            }
        }

        /**
         * Validate form on submit
         */
        function _onSubmitForm(event){
            if(htElement.welInputTitle.value == ""){
                event.preventDefault();
                $yona.showAlert(Messages("post.error.emptyTitle"), function() {
                    document.getElementById("title").focus();
                });
                return false;
            }

            window.removeEventListener("beforeunload", _onBeforeUnload);

            removeCurrentPageTemprarySavedContent();

            return true;
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            var oUploader = yona.Files.getUploader(htElement.welUploader, htElement.welTextarea);

            if(oUploader){
                (new yona.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welTextarea,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : oUploader[0].getAttribute("data-namespace")
                }));
            }
        }

        _init(htOptions);
    };

})("yona.board.Write");
