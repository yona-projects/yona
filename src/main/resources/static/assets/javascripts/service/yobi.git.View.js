/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author JiHan Kim
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
            htElement.welTextarea = $('textarea[data-editor-mode="content-body"]');
            htElement.welAttachments = $(".attachments");
            htElement.welBtnWatch = $('#watch-button');
            htElement.welComment = $('#comments');
            htElement.welBtnHelp = $('#helpBtn');
            htElement.welMsgHelp = $('#helpMessage');
            htElement.welState = $("#state");
            htElement.welBtnAccept = $("#btnAccept");
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
                var bWatched = (welTarget.attr("data-watching") === "true");

                $yobi.sendForm({
                    "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                    "fOnLoad": function(){
                        welTarget
                            .attr("data-watching", !bWatched)
                            .toggleClass('ybtn-watching')
                            .html(Messages(!bWatched ? "project.unwatch" : "project.watch")).blur();
                            
                        $yobi.notify(Messages(bWatched ? "pullRequest.unwatch.start" : "pullRequest.watch.start"), 3000);
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

            if(htElement.welBtnAccept.length > 0 && htElement.welBtnAccept.data("requestAs")){
                htElement.welBtnAccept.data("requestAs").on("beforeRequest", function(){
                    htElement.welBtnAccept.attr('disabled','disabled');
                    NProgress.start();
                });
            }
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
