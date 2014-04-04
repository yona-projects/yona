/**
 * @(#)yobi.issue.View.js 2013.03.13
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
            _initElement(htOptions || {});
            _initVar(htOptions || {});
            _attachEvent();

            _initFileUploader();
            _initFileDownloader();

            _setTimelineUpdateTimer();

            _setBtnCommentAndClose();
        }

        /**
         * initialize HTML Element variables
         */
        function _initElement(htOptions){
            htElement.welUploader = $("#upload");
            htElement.welTextarea = $("#comment-editor");

            htElement.welBtnWatch = $('#watch-button');

            htElement.welIssueLabels = $("#issueLabels");
            htElement.welAssignee = htOptions.welAssignee || $("#assignee");
            htElement.welMilestone = htOptions.welMilestone || $("#milestone");
            htElement.welIssueUpdateForm = htOptions.welIssueUpdateForm;

            htElement.welTimelineWrap = $("#timeline");
            htElement.welTimelineList = htElement.welTimelineWrap.find(".timeline-list");
        }

        /**
         * initialize variables except HTML Element
         */
        function _initVar(htOptions){
            htVar.sTplFileItem = $('#tplAttachedFile').text();

            htVar.sIssueId = htOptions.sIssueId;
            htVar.sIssuesUrl = htOptions.sIssuesUrl;

            htVar.sWatchUrl   = htOptions.sWatchUrl;
            htVar.sUnwatchUrl = htOptions.sUnwatchUrl;
            htVar.sTimelineUrl = htOptions.sTimelineUrl;

            // for auto-update
            htVar.bTimelineUpdating = false;
            htVar.nTimelineUpdateTimer = null;
            htVar.nTimelineUpdatePeriod = htOptions.nTimelineUpdatePeriod || 60000; // 60000ms = 60s = 1m
            htVar.sTimelineHTML = htElement.welTimelineList.html();
            htVar.nTimelineItems = _countTimelineItems(); // 타임라인 항목 갯수
            htVar.bOnFocusTextarea = false; // 댓글 작성폼에 포커스가 있는지 여부

            // for comment-and-close
            htVar.sNextState = htOptions.sNextState;
            htVar.sNextStateUrl = htOptions.sNextStateUrl;
            htVar.sCommentWithStateUrl = htOptions.sCommentWithStateUrl;

            // for label update
            htVar.aLatestLabelIds = htElement.welIssueLabels.val();
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            // 지켜보기
            htElement.welBtnWatch.click(_onClickBtnWatch);

            // 이슈 정보 업데이트
            htElement.welAssignee.on("change", _onChangeAssignee);
            htElement.welMilestone.on("change", _onChangeMilestone);
            htElement.welIssueLabels.on("change", _onChangeIssueLabels);

            // 타임라인 자동업데이트를 위한 정보
            if(htElement.welTextarea.length > 0){
                htElement.welTextarea.on({
                   "focus": _onFocusCommentTextarea,
                   "blur" : _onBlurCommentTextarea
                });
            }
        }

        /**
         * on focus textarea
         * @private
         */
        function _onFocusCommentTextarea(){
            htVar.bOnFocusTextarea = true;
        }

        /**
         * on blur textarea
         * @private
         */
        function _onBlurCommentTextarea(){
            htVar.bOnFocusTextarea = false;
        }

        /**
         * 지켜보기 버튼 클릭시 이벤트 핸들러
         *
         * @param {Wrapped Event} weEvt
         */
        function _onClickBtnWatch(weEvt){
            var welTarget = $(weEvt.target);
            var bWatched = (welTarget.attr("data-watching") == "true") ? true : false;

            $yobi.sendForm({
                "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                "fOnLoad": function(){
                    welTarget.attr("data-watching", !bWatched);
                    welTarget.html(Messages(!bWatched ? "project.unwatch" : "project.watch"));
                    $yobi.notify(Messages(bWatched ? "issue.unwatch.start" : "issue.watch.start"), 3000);
                }
            });
        }

        /**
         * 이슈 라벨 변경시
         * change 이벤트 핸들러
         *
         * @param weEvt
         * @private
         */
        function _onChangeIssueLabels(weEvt){
            var htReqData = _getRequestDataForUpdateIssueLabel(weEvt);

            // 업데이트 요청 전송
            _requestUpdateIssue({
               "htData"  : htReqData,
               "fOnLoad" : function(){
                   $yobi.notify(Messages("issue.update.label"), 3000);
               },
               "fOnError": function(oRes){
                   _onErrorRequest(Messages("issue.update.label"), oRes);
               }
            });
        }

        /**
         * 이슈 라벨 변경 요청 데이터를 반환
         *
         * @param weEvt
         * @returns {Hash Table}
         * @private
         */
        function _getRequestDataForUpdateIssueLabel(weEvt){
            var htReqData = {};

            // 삭제할 라벨
            htReqData["detachingLabel[0].id"] = _getIdPropFromObject(weEvt.removed);

            // 추가할 라벨
            htReqData["attachingLabel[0].id"] = _getIdPropFromObject(weEvt.added);

            // 추가하는 라벨이 있는 경우
            // 해당 라벨을 추가함으로 인해 삭제해야 하는 라벨을 찾아 htReqData 에 넣는다
            if(htReqData["attachingLabel[0].id"]){
                var htRemove = _getLabelsToRemovedByAdding(weEvt.added);
                htReqData = $.extend(htReqData, htRemove);
            }

            return htReqData;
        }

        /**
         * {@code htItem}의 id 속성을 반환한다
         *
         * @param htItem
         * @returns {*}
         * @private
         */
        function _getIdPropFromObject(htItem){
            return (htItem && htItem.id) ? htItem.id : undefined;
        }

        /**
         * {@code htLabel} 을 추가함으로 인해 삭제해야 하는 라벨을 찾아 그 정보를 반환한다
         *
         * @param htLabel
         * @private
         * @return {Hash Table}
         */
        function _getLabelsToRemovedByAdding(htLabel){
            var htRemove = {};
            var oIssueLabels = htElement.welIssueLabels.data("select2");
            var aIssueLabelValues = oIssueLabels.val();
            var aRemoveLabelIds = _getLabelInSameCategoryWith(oIssueLabels.data(), htLabel);

            // 삭제할 항목으로 추가하고
            aRemoveLabelIds.forEach(function(nValue, nIndex){
                htRemove["detachingLabel[" + (nIndex + 1) + "].id"] = nValue;
                aIssueLabelValues.splice(aIssueLabelValues.indexOf(nValue), 1);
            });

            // 해당 항목이 제거된 상태로 Select2 값 설정
            oIssueLabels.val(aIssueLabelValues);

            return htRemove;
        }

        /**
         * {@code aData} 를 기준으로 {@code htAddedLabel}과 같은 카테고리의 항목을 반환한다
         *
         * @param aData
         * @param htAddedLabel
         * @private
         * @returns {Array}
         */
        function _getLabelInSameCategoryWith(aData, htAddedLabel){
            var aLabelIds = [];
            var sAddedCategory = $(htAddedLabel.element).data("category");

            aData.forEach(function(htData){
                var sCategory = $(htData.element).data("category");

                if(htData.id !== htAddedLabel.id && sCategory === sAddedCategory){
                    aLabelIds.push(htData.id);
                }
            });

            return aLabelIds;
        }

        /**
         * 담당자 변경시
         *
         * @param {Wrapped Event} weEvt "change" 이벤트
         */
        function _onChangeAssignee(weEvt){
            _requestUpdateIssue({
               "htData"  : {"assignee.id": weEvt.val},
               "fOnLoad" : function(){
                   $yobi.notify(Messages("issue.update.assignee"), 3000);
                   htElement.welAssignee.select2("val", weEvt.val);
                   _updateTimeline();
               },
               "fOnError": function(oRes){
                   _onErrorRequest(Messages("issue.update.assignee"), oRes);
               }
            });
        }

        /**
         * 마일스톤 변경시
         *
         * @param {Wrapped Event} weEvt "change" 이벤트
         */
        function _onChangeMilestone(weEvt){
            _requestUpdateIssue({
               "htData"  : {"milestone.id": weEvt.val},
               "fOnLoad" : function(){
                   $yobi.notify(Messages("issue.update.milestone"), 3000);
               },
               "fOnError": function(oRes){
                   _onErrorRequest(Messages("issue.update.milestone"), oRes);
               }
            });
        }

        /**
         * 이슈 정보 업데이트 AJAX 호출
         *
         * @param {Hash Table} htOptions
         */
        function _requestUpdateIssue(htOptions){
            var htReqData = {"issues[0].id": htVar.sIssueId};
            for(var sKey in htOptions.htData){
                htReqData[sKey] = htOptions.htData[sKey];
            }

            $.ajax(htVar.sIssuesUrl, {
                "method"  : "post",
                "dataType": "json",
                "data"    : htReqData,
                "success" : htOptions.fOnLoad,
                "error"   : htOptions.fOnError
            });
        }

        /**
         * 이슈 정보 업데이트 호출 실패시
         *
         * @param sMessage
         * @param oRes
         * @private
         */
        function _onErrorRequest(sMessage, oRes){
            $yobi.notify(Messages("error.failedTo", sMessage, oRes.status, oRes.statusText));
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
         *
         * @param {Wrapped Array} waTarget (optional)
         */
        function _initFileDownloader(waTarget){
            (waTarget || $(".attachments")).each(function(i, elContainer){
                if(!$(elContainer).data("isYobiAttachment")){
                    (new yobi.Attachments({"elContainer": elContainer}));
                }
            });
        }

        /**
         * update IssueTimeline
         */
        function _updateTimeline(){
            if(htVar.bTimelineUpdating){
                return;
            }

            htVar.bTimelineUpdating = true;

            $.get(htVar.sTimelineUrl, _onLoadTimeline).always(function(){
                htVar.bTimelineUpdating = false;
            });
        }

        /**
         * On load IssueTimeline
         * @param sResult HTML String
         * @private
         */
        function _onLoadTimeline(sResult){
            if(sResult === htVar.sTimelineHTML){ // update only HTML has changed
                return;
            }

            _fixTimelineHeight();

            // 미리 그려서
            var welTimelineList = _getRenderedTimeline(sResult);

            // 첨부파일, 마크다운 렌더링 등에 시간이 걸리므로 약간의 시간차를 두고
            setTimeout(function(){
                // 한 번에 DOM Element 갈아끼우기
                htElement.welTimelineList.replaceWith(welTimelineList);
                htElement.welTimelineList = welTimelineList;
                htVar.sTimelineHTML = sResult;

                // 렌더링 이후 타임라인 항목 갯수가 변했나? = 영역의 높이가 달라지나?
                var bChanged= (htVar.nTimelineItems !== _countTimelineItems());
                var bTimelineChangedOnTyping = htVar.bOnFocusTextarea && bChanged;

                // 댓글 입력 도중 타임라인 높이가 변경되었으면
                // 댓글 입력폼과 화면 스크롤 간의 차이를 기억해둔다
                var nScrollGap = bTimelineChangedOnTyping ?
                    (htElement.welTextarea.offset().top - $(document).scrollTop()) : 0;

                _unfixTimelineHeight();

                // 입력 중인 댓글 폼이 계속 원래 보던 위치에 보이도록 화면 스크롤
                if(bTimelineChangedOnTyping){
                    $(document).scrollTop(htElement.welTextarea.offset().top - nScrollGap);
                }
            }, 500);
        }

        /**
         * fix timeline height with current height
         * @private
         */
        function _fixTimelineHeight(){
            htElement.welTimelineWrap.height(htElement.welTimelineWrap.height());
        }

        /**
         * unfix timeline height
         * @private
         */
        function _unfixTimelineHeight(){
            htElement.welTimelineWrap.height("");
            htVar.nTimelineItems = _countTimelineItems();
        }

        /**
         * Get issue timeline element which filled with specified HTML String
         * @param sHTML
         * @returns {*}
         * @private
         */
        function _getRenderedTimeline(sHTML){
            var welTimelineList = htElement.welTimelineList.clone();
            welTimelineList.html(sHTML);

            _initFileDownloader(welTimelineList.find(".attachments"));
            yobi.Markdown.enableMarkdown(welTimelineList.find("[markdown]"));
            welTimelineList.find("[data-request-method]").requestAs(); // delete button
            welTimelineList.find("[data-toggle=tooltip]").tooltip(); // bootstrap tooltip

            return welTimelineList;
        }

        /**
         * update IssueTimeline automatically
         * with interval timer
         */
        function _setTimelineUpdateTimer(){
            _unsetTimelineUpdateTimer();

            htVar.nTimelineItems = _countTimelineItems();
            htVar.nTimelineUpdateTimer = setInterval(function(){
                var bEditing = (htElement.welTimelineWrap.find(".comment-update-form:visible").length > 0);

                if(htVar.bTimelineUpdating !== true && !bEditing){
                    _updateTimeline();
                }
            }, htVar.nTimelineUpdatePeriod);
        }

        /**
         * Unset IssueTimeline update timer
         * @private
         */
        function _unsetTimelineUpdateTimer(){
            if(htVar.nTimelineUpdateTimer != null){
                clearInterval(htVar.nTimelineUpdateTimer);
            }

            htVar.nTimelineUpdateTimer = null;
        }

        /**
         * Count items in timeline
         * for detect timeline has updated
         * @returns {*}
         * @private
         */
        function _countTimelineItems(){
            return htElement.welTimelineList.find("ul.comments > li").length;
        }

        /**
         * Add "comment & close" like button at comment form
         * @private
         */
        function _setBtnCommentAndClose(){
            var welEditor = $("#comment-editor");
            var welDynamicCommentBtn = $("#dynamic-comment-btn");
            var welCommentForm = $("#comment-form");
            var welWithStateTransition = $("<input type='hidden' name='withStateTransition'>");

            var sNextState = Messages("button.nextState." + htVar.sNextState);
            var sCommentAndNextState = Messages("button.commentAndNextState." + htVar.sNextState);

            welCommentForm.prepend(welWithStateTransition);
            welDynamicCommentBtn.removeClass('hidden');
            welDynamicCommentBtn.html(Messages("button.nextState." + htVar.sNextState));
            welDynamicCommentBtn.on("click", function(){
                if(welEditor.val().length > 0){
                    welWithStateTransition.val("true");
                    welCommentForm.attr("action", htVar.sCommentWithStateUrl);
                    welCommentForm.submit();
                } else {
                    welWithStateTransition.val("");
                    location.href = htVar.sNextStateUrl;
                }
            });

            welEditor.on("keyup", function(){
                if(welEditor.val().length > 0){
                    welDynamicCommentBtn.html(sCommentAndNextState);
                } else {
                    welDynamicCommentBtn.html(sNextState);
                }
            });

            // if yobi.ShortcutKey exists
            if(yobi.ShortcutKey){
                yobi.ShortcutKey.attach("CTRL+SHIFT+ENTER", function(htInfo){
                    if(htInfo.welTarget.is(welEditor)){
                        welDynamicCommentBtn.click();
                    }
                });
            }
        }

        // initialize
        _init(htOptions || {});
    };
})("yobi.issue.View");
