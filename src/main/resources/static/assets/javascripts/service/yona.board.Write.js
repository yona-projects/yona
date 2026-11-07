/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

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
            htElement.welInputTitle.focus();
            htElement.welInputTitle.on('keydown', function (e) {
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

            temporarySaveHandler(htElement.welTextarea);

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
                $yobi.showAlert(Messages("post.error.emptyTitle"), function() {
                    $("#title").focus();
                });
                return false;
            }

            $(window).off("beforeunload", _onBeforeUnload);

            removeCurrentPageTemprarySavedContent();

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
