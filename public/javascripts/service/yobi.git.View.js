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
            _setStateUpdateTimer();
        }

        /**
         * initialize variables except HTML Element
         */
        function _initVar(htOptions){
            htVar.sTplFileItem = $('#tplAttachedFile').text();
            htVar.bCommentable = htOptions.bCommentable;
            htVar.sWatchUrl = htOptions.sWatchUrl;
            htVar.sUnwatchUrl = htOptions.sUnwatchUrl;

            htVar.sStateUrl = htOptions.sStateUrl;
            htVar.bStateUpdating = false;
            htVar.nStateUpdateTimer = null;
            htVar.nStateUpdateInterval = htOptions.nStateUpdateInterval || 10000; // 10sec
            htVar.sStateHTML = "";
        }

        /**
         * initialize HTML Element variables
         */
        function _initElement(htOptions){
            htElement.welUploader = $("#upload");
            htElement.welTextarea = $("#comment-editor");
            htElement.welAttachments = $(".attachments");
            htElement.welBtnWatch = $('#watch-button');
            htElement.welComment = $('#comments');
            htElement.welBtnHelp = $('#helpBtn');
            htElement.welMsgHelp = $('#helpMessage');
            htElement.welState = $("#state");
            htElement.welActOnOpen = $("#actOnOpen");
            htElement.welActOnClosed = $("#actOnClosed");
            htElement.welBtnAccept = $("#btnAccept");
            
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

            htElement.welBtnWatch.click(function(weEvt) {
                var welTarget = $(weEvt.target);
                var bWatched = welTarget.hasClass("active");

                $yobi.sendForm({
                    "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                    "fOnLoad": function(){
                        welTarget.toggleClass("active");
                        welTarget.html(Messages(welTarget.hasClass("active") ? "project.unwatch" : "project.watch"));
                    }
                });
            });
            
            $("button.more").click(function(){
                $(this).next("pre").toggleClass("hidden");
            });

            $("a#toggle").click(function(weEvt){
                weEvt.preventDefault();
                $("#outdatedCommits").toggle();
            });

            htElement.welComment.on('click','[data-toggle="more"]', function(){
              $(this).parent().next('p.desc').toggleClass('hide');
            });

            if (htVar.bCommentable) {
                yobi.CodeCommentBox.init({
                    fCallbackAfterHideCommentBox: function(welCommentBox) {
                        $('ul#comments').after(welCommentBox);
                    },
                    welDiff: htOptions.htDiff
                });
            }

            htElement.welBtnAccept.data("requestAs").on("beforeRequest", function(){
                yobi.ui.Spinner.show({"bUseDimmer": true});
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
            htElement.welAttachments.each(function(n, elContainer){
                if(!$(elContainer).data("isYobiAttachment")){
                    if(!$(elContainer).data("isYobiAttachment")){
                        (new yobi.Attachments({"elContainer": elContainer}));
                    }
                }
            });
        }
        
        /**
         * update pullRequest state
         */
        function _updateState(){
            if(htVar.bStateUpdating){
                return;
            }
            
            htVar.bStateUpdating = true;

            $.get(htVar.sStateUrl, function(oRes){
                var sResult = oRes.html;
                
                // update state only HTML has changed
                if(sResult != htVar.sStateHTML){ 
                    htVar.sStateHTML = sResult;
                    htElement.welState.html(sResult);
                }
                
                // update visiblitity of actrow buttons
                htElement.welActOnOpen.css("display", !oRes.isMerging && oRes.isOpen ? "block" : "none");
                htElement.welActOnClosed.css("display", !oRes.isMerging && oRes.isClosed ? "block" : "none");
                htElement.welBtnAccept.css("display", oRes.isConflict ? "none" : "inline-block");
            }).always(function(){
                htVar.bStateUpdating = false;
            });
        }
        
        /**
         * update current state of pullRequest automatically 
         * with interval timer
         */
        function _setStateUpdateTimer(){
            if(htVar.nStateUpdateTimer != null){
                yobi.Interval.clear(htVar.nStateUpdateTimer);
                htVar.nStateUpdateTimer = null;
            }
            
            htVar.nStateUpdateTimer = yobi.Interval.set(function(){
                if(htVar.bStateUpdating !== true){
                    _updateState();
                }
            }, htVar.nStateUpdateInterval);
        }

        _init(htOptions || {});
    };

})("yobi.git.View");
