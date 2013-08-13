/**
 * @(#)yobi.git.View.js 2013.08.12
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */

(function(ns){
    
    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){
        
        var htVar = {};
        var htElement = {};

        /**
         * initialize
         * @param {Hash Table} htOptions
         */
        function _init(htOptions){
            _initVar(htOptions);
            _initElement(htOptions);
            _attachEvent();

            _initFileUploader();
            _initFileDownloader();
        }

        /**
         * initialize variables except HTML Element
         */
        function _initVar(htOptions){
            htVar.sFilesURL = htOptions.sFilesURL;
            htVar.sUploadURL = htOptions.sUploadURL;
            htVar.sTplFileItem = $('#tplAttachedFile').text();
        }
        
        /**
         * initialize HTML Element variables
         */
        function _initElement(htOptions){
            htElement.welUploader = $("#upload");
            htElement.welTextarea = $("#comment-editor");
            htElement.welAttachments = $("#attachments");

            htElement.welBtnHelp = $('#helpBtn');
            htElement.welMsgHelp = $('#helpMessage');
            
            // tooltip
            $('span[data-toggle="tooltip"]').tooltip({
                placement : "bottom"
            });
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welBtnHelp.click(function(e){
                e.preventDefault();
                htElement.welMsgHelp.toggle();
            });
        }
        
        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            yobi.FileUploader.init({
                "elContainer" : htElement.welUploader,
                "elTextarea"  : htElement.welTextarea,
                "sTplFileItem": htVar.sTplFileItem,
                "sAction"     : htVar.sUploadURL
            });
        }
        
        /**
         * initialize fileDownloader
         */
        function _initFileDownloader(){
            htElement.welAttachments.each(function(n, el){
                (new yobi.FileDownloader({
                    "elTarget": htElement.welAttachments,
                    "sAction" : htVar.sFilesURL
                }));
            });
        }

        _init(htOptions || {});
    };
    
})("yobi.git.View");