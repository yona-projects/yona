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

    var oNS = $yona.createNamespace(ns);
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

                $yona.sendForm({
                    "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                    "fOnLoad": function(){
                        welTarget
                            .attr("data-watching", !bWatched)
                            .toggleClass('ybtn-watching')
                            .html(Messages(!bWatched ? "project.unwatch" : "project.watch")).blur();
                            
                        $yona.notify(Messages(bWatched ? "pullRequest.unwatch.start" : "pullRequest.watch.start"), 3000);
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
                yona.CodeCommentBox.init({
                    fCallbackAfterHideCommentBox: function(welCommentBox) {
                        $('ul#comments').after(welCommentBox);
                    },
                    welDiff: htOptions.htDiff
                });
            }

            if(htElement.welBtnAccept.length > 0){
                htElement.welBtnAccept[0].addEventListener("request:before", function(){
                    htElement.welBtnAccept.attr('disabled','disabled');
                    NProgress.start();
                });
            }
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
                        (new yona.Attachments({"elContainer": elContainer}));
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
         * 풀 리퀘스트 상태를 일정 간격으로 자동 갱신한다.
         *
         * v1.6의 `yona.Interval`이 하던 "사용자 입력이 60초 동안 없으면 타이머를 멈추고,
         * 입력이 다시 오면 재개한다"는 규칙을 이 화면 안으로 옮긴 것이다(S-02, A-416).
         * 타이머는 계속 돌되 마지막 입력 후 60초가 지났으면 요청을 보내지 않고 건너뛴다.
         * 입력(scroll, mousedown, mousemove, keypress)이 오면 다음 간격 시각부터 다시 요청한다.
         */
        function _setStateUpdateTimer(){
            var nIdleLimit = 60000; // 60sec
            var nLastInput = Date.now();

            function onUserInput(){
                nLastInput = Date.now();
            }
            ["scroll", "mousedown", "mousemove", "keypress"].forEach(function(sType){
                window.addEventListener(sType, onUserInput, {"passive": true});
            });

            if(htVar.nStateUpdateTimer != null){
                clearInterval(htVar.nStateUpdateTimer);
            }

            htVar.nStateUpdateTimer = setInterval(function(){
                if(Date.now() - nLastInput >= nIdleLimit){
                    return;
                }
                if(htVar.bStateUpdating !== true){
                    _updateState();
                }
            }, htVar.nStateUpdateInterval);
        }

        _init(htOptions || {});
    };

})("yona.git.View");
