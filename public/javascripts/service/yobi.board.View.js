/**
 * @(#)yobi.board.View.js 2013.03.11
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
            _initVar(htOptions || {});
            _initElement(htOptions || {});
            _attachEvent();

            _initFileUploader();
            _initFileDownloader();
        }

        /**
         * initialize variables except HTML Element
         */
        function _initVar(htOptions){
            htVar.sTplFileItem = $('#tplAttachedFile').text();
            htVar.sAction = htOptions.sAction;
            htVar.sWatchUrl = htOptions.sWatchUrl;
            htVar.sUnwatchUrl = htOptions.sUnwatchUrl;
        }

        /**
         * initialize HTML Element variables
         */
        function _initElement(htOptions){
            htElement.welUploader = $("#upload");
            htElement.welTextarea = $("#comment-editor");

            htElement.welAttachments = $(".attachments");
            htElement.welBtnWatch = $('#watch-button');
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            htElement.welBtnWatch.click(function(weEvt) {
                var welTarget = $(weEvt.target);
                var bWatched = (welTarget.attr("data-watching") == "true") ? true : false;
                
                $yobi.sendForm({
                    "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                    "fOnLoad": function(){
                        welTarget.attr("data-watching", !bWatched);
                        welTarget.html(Messages(!bWatched ? "project.unwatch" : "project.watch"));
                        $yobi.notify(Messages(bWatched ? "post.unwatch.start" : "post.watch.start"), 3000);
                    }
                });
            });
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

        /**
         * initialize fileDownloader
         */
        function _initFileDownloader(){
            htElement.welAttachments.each(function(i, elContainer){
                if(!$(elContainer).data("isYobiAttachment")){
                    (new yobi.Attachments({"elContainer": elContainer}));
                }
            });
        }
        
        _init(htOptions);
    };

})("yobi.board.View");
