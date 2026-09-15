/**
 * Yona, Project Hosting SW
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

    var oNS = $yona.createNamespace(ns);
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
            _affixIssueInfoWrap();
        }

        /**
         * initialize variables except HTML Element
         */
        function _initVar(htOptions){
            var tplFileItemEl = document.getElementById("tplAttachedFile");
            // jQuery `$('#tplAttachedFile').text()`는 매치가 없어도 빈 문자열을
            // 반환한다(undefined가 아님) - 그 quirk를 그대로 재현.
            htVar.sTplFileItem = tplFileItemEl ? tplFileItemEl.textContent : "";
            htVar.sAction = htOptions.sAction;
            htVar.sWatchUrl = htOptions.sWatchUrl;
            htVar.sUnwatchUrl = htOptions.sUnwatchUrl;
        }

        /**
         * initialize HTML Element variables
         */
        function _initElement(htOptions){
            htElement.welUploader = document.getElementById("upload");
            htElement.welTextarea = document.querySelector('textarea[data-editor-mode="comment-body"]');

            htElement.welAttachments = document.querySelectorAll(".attachments");
            htElement.welBtnWatch = document.getElementById("watch-button");
            htElement.issueInfoWrap = document.querySelector(".issue-info");
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            if(!htElement.welBtnWatch){
                return;
            }

            htElement.welBtnWatch.addEventListener("click", function(weEvt) {
                var welTarget = weEvt.target;
                var bWatched = (welTarget.getAttribute("data-watching") === "true");

                $yona.sendForm({
                    "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                    "fOnLoad": function(){
                        welTarget.setAttribute("data-watching", !bWatched);
                        welTarget.classList.toggle('ybtn-watching');
                        welTarget.innerHTML = Messages(!bWatched ? "post.unwatch" : "post.watch");
                        welTarget.blur();

                        $yona.notify(Messages(bWatched ? "post.unwatch.start" : "post.watch.start"), 3000);
                    }
                });
            });
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            var oUploader = yona.Files.getUploader(htElement.welUploader, htElement.welTextarea);

            if(oUploader){
                // yona.Files.getUploader()는 [elContainer] 형태의 순수 배열을 반환한다
                // (P3-70 라운드10에서 jQuery 래핑 제거) - oUploader[0]로 raw element를 꺼낸다.
                (new yona.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welTextarea,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : oUploader[0].getAttribute("data-namespace")
                }));
            }
        }

        /**
         * initialize fileDownloader
         */
        function _initFileDownloader(){
            htElement.welAttachments.forEach(function(elContainer){
                // 6단계(jQuery 완전 제거): isYonaAttachment는 yona.Attachments.js가 붙이는
                // 순수 expando 프로퍼티다(공개 계약, 중복 초기화 가드).
                if(!elContainer._isYonaAttachment){
                    (new yona.Attachments({"elContainer": elContainer}));
                }
            });
        }

        function _affixIssueInfoWrap(){
            if(htElement.issueInfoWrap){
                htElement.issueInfoWrap.classList.add("sticky-issue-info");
            }
        }

        _init(htOptions);
    };

})("yona.board.View");
