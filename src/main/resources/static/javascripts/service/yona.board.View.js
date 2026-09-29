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
        var elRoot = document;
        var oUploaderAttachment = null;
        var sUploaderId = null;
        var aDownloaders = [];
        // dispose 시 이 모듈이 붙인 리스너를 한 번에 해제한다(상세가 재마운트돼도 중복 등록되지 않도록).
        var oLifecycle = new AbortController();

        /**
         * initialize
         * @param {Hash Table} htOptions
         */
        function _init(htOptions){
            // 2단 보기(Turbo Frame)에서는 상세 루트 안으로 범위를 한정하고, 이 모듈이 만든 것을 되돌릴 수
            // 있도록 dispose 함수를 반환한다. root가 없으면 기존처럼 document 전체를 대상으로 한다.
            elRoot = (htOptions && htOptions.root) || document;
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
            var tplFileItemEl = elRoot.querySelector("#tplAttachedFile");
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
            htElement.welUploader = elRoot.querySelector("#upload");
            htElement.welTextarea = elRoot.querySelector('textarea[data-editor-mode="comment-body"]');

            htElement.welAttachments = elRoot.querySelectorAll(".attachments");
            htElement.welBtnWatch = elRoot.querySelector("#watch-button");
            htElement.issueInfoWrap = elRoot.querySelector(".issue-info");
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
                }, {signal: oLifecycle.signal});
            }

            // Wire the label <select data-toggle="tomselect" id="labelIds"> (issue/
            // partial_select_label.html, shared with issue/view.html) into a PUT to
            // urls.labels (BoardController#updatePostLabels) on every "change" - mirrors
            // yona.issue.View.js's `_delegate(elements.issueInfoWrap, "change",
            // "[data-toggle=tomselect]", ...)` wiring, which board/view.html's markup never
            // had an equivalent of (the <select> and its REST endpoint both already existed;
            // nothing on the client ever called it).
            if(htElement.issueInfoWrap && htVar.sLabelsUrl){
                htElement.issueInfoWrap.addEventListener("change", _onChangeLabelIds, {signal: oLifecycle.signal});
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
            // 비로그인 화면에는 업로더(#upload)가 없다 - 가드가 없으면 아래에서 null.getAttribute로 죽어
            // 이후 초기화(다운로더 등)와, 이 모듈을 부르는 호출부의 나머지 배선이 모두 중단된다.
            if(!htElement.welUploader){ return; }
            var oUploader = yona.Files.getUploader(htElement.welUploader, htElement.welTextarea);

            if(oUploader){
                // yona.Files.getUploader()는 [elContainer] 형태의 순수 배열을 반환한다 -
                // oUploader[0]로 raw element를 꺼낸다.
                sUploaderId = oUploader[0].getAttribute("data-namespace");
                oUploaderAttachment = new yona.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welTextarea,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : sUploaderId
                });
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
                    aDownloaders.push({container: elContainer, attachment: new yona.Attachments({"elContainer": elContainer})});
                }
            });
        }

        function _affixIssueInfoWrap(){
            if(htElement.issueInfoWrap){
                htElement.issueInfoWrap.classList.add("sticky-issue-info");
            }
        }

        _init(htOptions);

        return function(){
            oLifecycle.abort();
            if(oUploaderAttachment){ oUploaderAttachment.destroy(); }
            if(sUploaderId){ yona.Files.destroyUploader(sUploaderId); }
            aDownloaders.forEach(function(oItem){
                oItem.attachment.destroy();
                oItem.container.replaceChildren();
                delete oItem.container._isYonaAttachment;
            });
            oUploaderAttachment = null;
            sUploaderId = null;
            aDownloaders = [];
        };
    };

})("yona.board.View");
