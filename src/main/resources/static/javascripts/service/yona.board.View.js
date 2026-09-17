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
            // urls.labels: PUT /api/projects/{projectId}/posts/{number}/labels (BoardController.
            // updatePostLabels) - accepts a raw JSON array of label ids as the request body.
            htVar.sLabelsUrl = htOptions.urls ? htOptions.urls.labels : undefined;
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
            if(htElement.welBtnWatch){
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

            // Wire the label <select data-toggle="tomselect" id="labelIds"> (issue/
            // partial_select_label.html, shared with issue/view.html) into a PUT to
            // urls.labels (BoardController#updatePostLabels) on every "change" - mirrors
            // yona.issue.View.js's `_delegate(elements.issueInfoWrap, "change",
            // "[data-toggle=tomselect]", ...)` wiring, which board/view.html's markup never
            // had an equivalent of (the <select> and its REST endpoint both already existed;
            // nothing on the client ever called it).
            if(htElement.issueInfoWrap && htVar.sLabelsUrl){
                htElement.issueInfoWrap.addEventListener("change", _onChangeLabelIds);
            }
        }

        /**
         * "change" handler of the post's label <select data-toggle="tomselect">.
         * Sends the full current selection as a JSON array of label ids to urls.labels.
         *
         * @param weEvt
         * @private
         */
        function _onChangeLabelIds(weEvt){
            var elField = weEvt.target.closest("[data-toggle=tomselect]");
            if(!elField || elField.id !== "labelIds"){
                return;
            }

            var oTomSelect = elField.tomselect;
            var aRawValues = oTomSelect ? oTomSelect.getValue() : Array.prototype.map.call(elField.selectedOptions, function(elOption){ return elOption.value; });
            var aLabelIds = (aRawValues || []).map(Number).filter(function(nId){ return !isNaN(nId); });

            fetch(htVar.sLabelsUrl, {
                "method": "PUT",
                "headers": {"Content-Type": "application/json"},
                "credentials": "same-origin",
                "body": JSON.stringify(aLabelIds)
            }).then(function(response){
                if(!response.ok){
                    return Promise.reject(response);
                }
                $yona.notify(Messages("issue.update.labelIds"), 3000);
            }).catch(function(oErr){
                $yona.notify(Messages("error.failedTo", Messages("issue.update.labelIds"),
                    oErr && oErr.status || "", oErr && oErr.statusText || ""), 3000);
            });
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            var oUploader = yona.Files.getUploader(htElement.welUploader, htElement.welTextarea);

            if(oUploader){
                // yona.Files.getUploader()는 [elContainer] 형태의 순수 배열을 반환한다 -
                // oUploader[0]로 raw element를 꺼낸다.
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
                // isYonaAttachment는 yona.Attachments.js가 붙이는 expando 프로퍼티다
                // (공개 계약, 중복 초기화 가드).
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
