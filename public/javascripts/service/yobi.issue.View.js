/**
 * Yobi, Project Hosting SW
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

    "use strict";

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(options){

        var vars = {};
        var elements = {};

        /**
         * Initialize
         * @param {Hash Table} options
         */
        function _init(options){
            _initElement(options || {});
            _initVar(options || {});
            _attachEvent();

            _initFileUploader();
            _initFileDownloader();
            _initCommentAndCloseButton();

            //_setTimelineUpdateTimer();
            _affixIssueInfoWrap();
        }

        /**
         * Initialize HTML Element variables
         *
         * @private
         */
        function _initElement(options){
            elements.uploader = $("#upload");
            elements.textarea = $('textarea[data-editor-mode="comment-body"]');

            elements.btnWatch = $('#watch-button');
            elements.issueInfoWrap = $(".issue-info");
            elements.dueDateStatus = elements.issueInfoWrap.find(".duedate-status");

            elements.timelineWrap = $("#timeline");
            elements.timelineList = elements.timelineWrap.find(".timeline-list");

            elements.dueDate = $("#issueDueDate");

            elements.btnVoteComment = $(options.btnVoteComment || '[data-request-type="comment-vote"]');
        }

        /**
         * Initialize variables
         *
         * @param options
         * @private
         */
        function _initVar(options){
            vars.issueId = options.issueId;
            vars.urls = options.urls;
            vars.nextState = options.nextState;
            vars.tplFileItem = $('#tplAttachedFile').text();

            // for auto-update
            vars.isTimelineUpdating = false;
            vars.isTextareaOnFocused = false;
            vars.timelineUpdateTimer = null;
            vars.timelineUpdatePeriod = options.timelineUpdatePeriod || 60000; // 60000ms = 60s = 1m
            vars.timelineHTML = elements.timelineList.html();
            vars.timelineItems = _countTimelineItems();

            // for comment-and-close
            vars.nextState = options.nextState;
        }

        /**
         * Attach event handler
         */
        function _attachEvent(){
            // Watch button
            elements.btnWatch.on("click", _onClickBtnWatch);

            // Vote button on comment
            elements.timelineWrap.on("click", '[data-request-type="comment-vote"]', _onClickCommentVote);

            // Update issue info
            elements.issueInfoWrap.on("change", "[data-toggle=select2]", _onChangeIssueInfo);
            elements.issueInfoWrap.on("change", "[data-toggle=calendar]", _onChangeDueDate);
            elements.issueInfoWrap.on("select2-selecting", '[name="assignee.user.id"]', _onSelectingAssignee);

            // Detect textarea events for autoUpdate timeline
            elements.textarea.on({
               "focus": _onFocusCommentTextarea,
               "blur" : _onBlurCommentTextarea
            });
        }

        function _onClickCommentVote(){
            $.ajax($(this).data("requestUri"), {
                "method"  : "post",
                "success" : function(){
                    location.reload();
                },
                "error" : function(res){
                    $yobi.notify(Messages(res.responseText), 3000);
                }
            });
        }

        function _onSelectingAssignee(evt){
            var targetElement = $(this);
            var selectedElement = targetElement.find(':selected');
            var isValueNotChanged = (targetElement.val() === evt.val);
            var isForceChange = $(evt.object.element).data("forceChange");
            var isNonMember = selectedElement.data("nonMember");

            if (isNonMember && !isValueNotChanged) {
                selectedElement.remove();
            }

            if(isForceChange && isValueNotChanged){
                targetElement.trigger("change");
            }
        }

        /**
         * on change dueDate input field
         *
         * @param evt
         * @private
         */
        function _onChangeDueDate(evt){
            var element = $(this);
            var dueDate = $.trim(element.val());

            // if dueDate is not empty and invalid
            if(dueDate && !moment(dueDate).isValid()){
                $yobi.notify(Messages("issue.error.invalid.duedate"), 3000);
                element.focus();
                return;
            }

            if(element.data("oval") !== element.val()){
                element.data("oval", element.val());
            }

            _requestUpdateIssue(evt, function(res){
                elements.dueDateStatus.html("(" + res.dueDateMsg + ")");
                if (res.isOverDue) {
                    elements.dueDateStatus.addClass("overdue");
                } else {
                    elements.dueDateStatus.removeClass("overdue");
                }
            });
        }

        /**
         * "change" event handler of issue info select2 fields.
         *
         * @param evt
         * @private
         */
        function _onChangeIssueInfo(evt){
            _requestUpdateIssue(evt);
        }

        /**
         * Send request to update issue info
         * like as assignee.id, milestone.id and labelIds.
         *
         * @param evt
         * @param callback
         * @private
         */
        function _requestUpdateIssue(evt, callback){
            var field = $(evt.target);
            var fieldName = field.data("fieldName") || field.prop("name");
            var fieldValue = field.data("select2") ? field.data("select2").val() : field.val();

            // Send request to update issueInfo
            $.ajax(vars.urls.massUpdate, {
                "method"  : "post",
                "dataType": "json",
                "data"    : _getUpdateIssueRequestData(fieldName, fieldValue, evt)
            })
            .done(function(res){
                _updateTimeline();

                $yobi.notify(Messages("issue.update." + fieldName), 3000);

                if(field.data("select2")){
                    field.data("select2").val(fieldValue);
                }

                if(typeof callback === "function"){
                    callback(res, fieldName, fieldValue, evt);
                }
            })
            .fail(function(res){
                $yobi.notify(Messages("error.failedTo",
                    Messages("issue.update." + fieldName),
                    res.status, res.statusText));
            });
        }

        /**
         * Returns request data to update issue info.
         *
         * @param fieldName
         * @param fieldValue
         * @param evt
         * @returns {Hash Table}
         * @private
         */
        function _getUpdateIssueRequestData(fieldName, fieldValue, evt){
            var requestData = {"issues[0].id": vars.issueId};

            if(fieldName === "labelIds"){
                requestData["attachingLabelIds"] = _getIdProps(evt.added);
                requestData["detachingLabelIds"] = _getIdProps(evt.removed);
            } else {
                requestData[fieldName] = fieldValue;
            }

            if(fieldName === "dueDate"){
                requestData["isDueDateChanged"] = true;
            }

            return requestData;
        }

        /**
         * Returns "id" properties of given object.
         * If {@code source} is array, extract "id" property of each object in the array.
         *
         * @param source
         * @returns {Array}
         * @private
         */
        function _getIdProps(source){
            var result = [];

            if(source instanceof Array){
                source.forEach(function(obj){
                    if(obj && obj.id){
                        result.push(obj.id);
                    }
                });
            } else if(source && source.id){
                result.push(source.id);
            }

            return (result.length > 0) ? result : undefined;
        }

        /**
         * "focus" event handler of textarea
         * _onLoadTimeline references {@code vars.isTextareaOnFocus}
         * to hold steady scroll position from textarea
         *
         * @private
         */
        function _onFocusCommentTextarea(){
            vars.isTextareaOnFocused = true;
        }

        /**
         * "blur" event handler of textarea
         *
         * @private
         */
        function _onBlurCommentTextarea(){
            vars.isTextareaOnFocused = false;
        }

        /**
         * "click" event handler of watch/unwatch button.
         * Toggles watch/unwatch issue.
         *
         * @param evt
         * @private
         */
        function _onClickBtnWatch(evt){
            var button = $(evt.target);
            var watching = button.data("watching");
            var url = watching ? vars.urls.unwatch : vars.urls.watch;

            $.post(url, function(){
                button.data("watching", !watching)
                    .toggleClass('ybtn-watching')
                    .html(Messages(!watching ? "project.unwatch" : "project.watch"))
                    .blur();

                $yobi.notify(Messages(watching ? "issue.unwatch.start" : "issue.watch.start"), 3000);
            });
        }

        /**
         * Initialize fileUploader
         *
         * @private
         */
        function _initFileUploader(){
            var oUploader = yobi.Files.getUploader(elements.uploader, elements.textarea);

            if(oUploader){
                (new yobi.Attachments({
                    "elContainer"  : elements.uploader,
                    "elTextarea"   : elements.textarea,
                    "sTplFileItem" : vars.tplFileItem,
                    "sUploaderId"  : oUploader.attr("data-namespace")
                }));
            }
        }

        /**
         * Initialize fileDownloader
         *
         * @param target
         * @private
         */
        function _initFileDownloader(target){
            (target || $(".attachments")).each(function(i, container){
                if(!$(container).data("isYobiAttachment")){
                    (new yobi.Attachments({"elContainer": container}));
                }
            });
        }

        /**
         * Update issue timeline
         *
         * @private
         */
        function _updateTimeline(){
            if(vars.isTimelineUpdating){
                return;
            }

            vars.isTimelineUpdating = true;

            $.get(vars.urls.timeline, _onLoadTimeline)
             .always(function(){
                 vars.isTimelineUpdating = false;
             });
        }

        /**
         * Render issue timeline on load HTML
         *
         * @param resultHTML
         * @private
         */
        function _onLoadTimeline(resultHTML){
            if(resultHTML === vars.timelineHTML){ // update only HTML has changed
                return;
            }

            _fixTimelineHeight();

            var timelineList = _getRenderedTimeline(resultHTML);

            setTimeout(function(){
                elements.timelineList.replaceWith(timelineList);
                elements.timelineList = timelineList;
                vars.timelineHTML = resultHTML;

                var isChanged = (vars.timelineItems !== _countTimelineItems());
                var isTimelineChangedOnTyping = vars.isTextareaOnFocused && isChanged;

                var scrollGap = isTimelineChangedOnTyping ?
                    (elements.textarea.offset().top - $(document).scrollTop()) : 0;

                _unfixTimelineHeight();

                if(isTimelineChangedOnTyping){
                    $(document).scrollTop(elements.textarea.offset().top - scrollGap);
                }
            }, 500);
        }

        /**
         * fix timeline height with current height
         *
         * @private
         */
        function _fixTimelineHeight(){
            elements.timelineWrap.height(elements.timelineWrap.height());
        }

        /**
         * unfix timeline height
         *
         * @private
         */
        function _unfixTimelineHeight(){
            elements.timelineWrap.height("");
            vars.timelineItems = _countTimelineItems();
        }

        /**
         * Get issue timeline element which filled with specified HTML String
         *
         * @param sHTML
         * @returns {*}
         * @private
         */
        function _getRenderedTimeline(timelineHTML){
            var timelineList = elements.timelineList.clone();
            timelineList.html(timelineHTML);

            _initFileDownloader(timelineList.find(".attachments"));
            yobi.Markdown.enableMarkdown(timelineList.find("[markdown]"));
            timelineList.find("[data-request-method]").requestAs(); // delete button

            return timelineList;
        }

        /**
         * Update timeline automatically with interval timer.
         * Don't update if visible .comment-update-form exists
         * or docked inspector is opened.
         *
         * @private
         */
        function _setTimelineUpdateTimer(){
            _unsetTimelineUpdateTimer();

            vars.timelineItems = _countTimelineItems();
            vars.timelineUpdateTimer = setInterval(function(){
                var isEditing = (elements.timelineWrap.find(".comment-update-form:visible").length > 0)
                                || _isDockedInspectorOpened();

                if(vars.isTimelineUpdating !== true && !isEditing){
                    _updateTimeline();
                }
            }, vars.timelineUpdatePeriod);
        }

        function _isDockedInspectorOpened(){
            return (window.outerHeight - window.innerHeight > 100);
        }

        /**
         * Unset IssueTimeline update timer
         *
         * @private
         */
        function _unsetTimelineUpdateTimer(){
            if(vars.timelineUpdateTimer != null){
                clearInterval(vars.timelineUpdateTimer);
            }

            vars.timelineUpdateTimer = null;
        }

        /**
         * Count items in timeline
         * for detect timeline has updated
         *
         * @returns {*}
         * @private
         */
        function _countTimelineItems(){
            return elements.timelineList.find("ul.comments > li").length;
        }

        /**
         * Add "comment & close" like button at comment form
         *
         * @private
         */
        function _initCommentAndCloseButton(){
            var commentForm = $("#comment-form");
            var dynamicCommentBtn = $("#dynamic-comment-btn");
            var withStateTransitionInput = $("<input type='hidden' name='withStateTransition'>");

            commentForm.prepend(withStateTransitionInput);

            dynamicCommentBtn.removeClass("hidden");
            dynamicCommentBtn.html(Messages("button.nextState." + vars.nextState));
            dynamicCommentBtn.on("click", function(){
                if(elements.textarea.val().length > 0){
                    withStateTransitionInput.val("true");
                    commentForm.submit();
                } else {
                    withStateTransitionInput.val("");
                    location.href = vars.urls.nextState;
                }
            });

            elements.textarea.on("keyup", function(){
                if(elements.textarea.val().length > 0){
                    dynamicCommentBtn.html(Messages("button.commentAndNextState." + vars.nextState));
                } else {
                    dynamicCommentBtn.html(Messages("button.nextState." + vars.nextState));
                }
            });

            // if yobi.ShortcutKey exists
            if(yobi.ShortcutKey){
                yobi.ShortcutKey.attach("CTRL+SHIFT+ENTER", function(htInfo){
                    if(htInfo.welTarget.is(elements.textarea)){
                        dynamicCommentBtn.click();
                    }
                });
            }
        }

        function _affixIssueInfoWrap(){
            elements.issueInfoWrap.affix({
                "offset": {
                    "top": elements.issueInfoWrap.offset().top - 10
                }
            });
        }

        // initialize
        _init(options || {});
    };
})("yobi.issue.View");
