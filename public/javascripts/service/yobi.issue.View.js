/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Jihan Kim
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
            htElement.welTextarea = $('textarea[data-editor-mode="comment-body"]');

            htElement.welBtnWatch = $('#watch-button');

            htElement.welIssueLabels = $("#issueLabels");
            htElement.welAssignee = htOptions.welAssignee || $("#assignee");
            htElement.welMilestone = htOptions.welMilestone || $("#milestone");
            htElement.welIssueUpdateForm = htOptions.welIssueUpdateForm;

            htElement.welTimelineWrap = $("#timeline");
            htElement.welTimelineList = htElement.welTimelineWrap.find(".timeline-list");
            htElement.welDueDate = htOptions.welDueDate;
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
            htVar.nTimelineItems = _countTimelineItems();
            htVar.bOnFocusTextarea = false;

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
            htElement.welDueDate.on("change", _onChangeDueDate);

            // 타임라인 자동업데이트를 위한 정보
            if(htElement.welTextarea.length > 0){
                htElement.welTextarea.on({
                   "focus": _onFocusCommentTextarea,
                   "blur" : _onBlurCommentTextarea
                });
            }

            $(".labels-wrap").on("click", ".edit-button", function(){
                $("#issueLabels").data("select2").open();
            });
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
         * @param {Wrapped Event} weEvt
         */
        function _onClickBtnWatch(weEvt){
            var welTarget = $(weEvt.target);
            var bWatched = (welTarget.attr("data-watching") === "true");

            $yobi.sendForm({
                "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                "fOnLoad": function(){
                    welTarget
                        .attr("data-watching", !bWatched)
                        .toggleClass('ybtn-watching')
                        .html(Messages(!bWatched ? "project.unwatch" : "project.watch")).blur();
                        
                    $yobi.notify(Messages(bWatched ? "issue.unwatch.start" : "issue.watch.start"), 3000);
                }
            });
        }

        /**
         * @param weEvt
         * @private
         */
        function _onChangeIssueLabels(weEvt){
            var htReqData = _getRequestDataForUpdateIssueLabel(weEvt);

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
         * @param weEvt
         * @returns {Hash Table}
         * @private
         */
        function _getRequestDataForUpdateIssueLabel(weEvt){
            var htReqData = {};

            htReqData["detachingLabel[0].id"] = _getIdPropFromObject(weEvt.removed);

            htReqData["attachingLabel[0].id"] = _getIdPropFromObject(weEvt.added);

            if(htReqData["attachingLabel[0].id"]){
                var htRemove = _getLabelsToRemovedByAdding(weEvt.added);
                htReqData = $.extend(htReqData, htRemove);
            }

            return htReqData;
        }

        /**
         * @param htItem
         * @returns {*}
         * @private
         */
        function _getIdPropFromObject(htItem){
            return (htItem && htItem.id) ? htItem.id : undefined;
        }

        /**
         * @param htLabel
         * @private
         * @return {Hash Table}
         */
        function _getLabelsToRemovedByAdding(htLabel){
            var htRemove = {};
            var oIssueLabels = htElement.welIssueLabels.data("select2");
            var aIssueLabelValues = oIssueLabels.val();
            var aRemoveLabelIds = _getLabelInSameCategoryWith(oIssueLabels.data(), htLabel);

            aRemoveLabelIds.forEach(function(nValue, nIndex){
                htRemove["detachingLabel[" + (nIndex + 1) + "].id"] = nValue;
                aIssueLabelValues.splice(aIssueLabelValues.indexOf(nValue), 1);
            });

            oIssueLabels.val(aIssueLabelValues);

            return htRemove;
        }

        /**
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
         * @param {Wrapped Event} weEvt
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
         * @param {Wrapped Event} weEvt
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

        function _onChangeDueDate(weEvt) {
            if (htElement.welDueDate.val() != htElement.welDueDate.data("oDueDate")) {
                _requestUpdateIssue({
                    "htData": {
                        "dueDate": htElement.welDueDate.val(),
                        "isDueDateChanged": true
                    },
                    "fOnLoad": function () {
                        htElement.welDueDate.data("oDueDate", htElement.welDueDate.val());
                        $yobi.notify(Messages("issue.update.duedate"), 3000);
                    },
                    "fOnError": function (oRes) {
                        htElement.welDueDate.val(htElement.welDueDate.date("oDueDate"));
                        _onErrorRequest(Messages("issue.update.duedate"), oRes);
                    }
                });
            }
        }

        /**
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

            var welTimelineList = _getRenderedTimeline(sResult);

            setTimeout(function(){
                htElement.welTimelineList.replaceWith(welTimelineList);
                htElement.welTimelineList = welTimelineList;
                htVar.sTimelineHTML = sResult;

                var bChanged= (htVar.nTimelineItems !== _countTimelineItems());
                var bTimelineChangedOnTyping = htVar.bOnFocusTextarea && bChanged;

                var nScrollGap = bTimelineChangedOnTyping ?
                    (htElement.welTextarea.offset().top - $(document).scrollTop()) : 0;

                _unfixTimelineHeight();

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
            var welEditor = $('textarea[data-editor-mode="comment-body"]');
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
