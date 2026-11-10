/**
 *  Yona, 21st Century Project Hosting SW
 *  <p>
 *  Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 *  https://yona.io
 **/
(function(ns){

    "use strict";

    const oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(options){

        const vars = {};
        const elements = {};

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
                    $yona.notify(Messages(res.responseText), 3000);
                }
            });
        }

        /**
         * on change dueDate input field
         *
         * @param evt
         * @private
         */
        function _onChangeDueDate(evt){
            const element = $(this);
            const dueDate = (element.val() || "").trim();

            // if dueDate is not empty and invalid
            if(dueDate && !moment(dueDate).isValid()){
                $yona.notify(Messages("issue.error.invalid.duedate"), 3000);
                element.focus();
                return;
            }

            if(element.data("oval") !== element.val()){
                element.data("oval", element.val());
            }

            _requestUpdateIssue(evt, (res) =>{
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
            const field = $(evt.target);
            const fieldName = field.data("fieldName") || field.prop("name");
            const fieldValue = field.val();

            // Send request to update issueInfo
            $.ajax(vars.urls.massUpdate, {
                "method"  : "post",
                "dataType": "json",
                "data"    : _getUpdateIssueRequestData(fieldName, fieldValue, evt)
            })
            .done((res) =>{
                _updateTimeline();

                $yona.notify(Messages("issue.update." + fieldName), 3000);

                if(typeof callback === "function"){
                    callback(res, fieldName, fieldValue, evt);
                }
            })
            .fail((res) =>{
                $yona.notify(Messages("error.failedTo",
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
            const requestData = {"issues[0].id": vars.issueId};

            if(fieldName === "labelIds"){
                const changes = yona.ui.Select2.selectionChanges(evt.target);
                requestData["attachingLabelIds"] = _getIdProps(changes.added);
                requestData["detachingLabelIds"] = _getIdProps(changes.removed);
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
            const result = [];

            if(source instanceof Array){
                source.forEach((obj) =>{
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
            const button = $(evt.target);
            const watching = button.data("watching");
            const url = watching ? vars.urls.unwatch : vars.urls.watch;

            $.post(url, () =>{
                button.data("watching", !watching)
                    .toggleClass('ybtn-watching')
                    .html(Messages(!watching ? "issue.unwatch" : "issue.watch"))
                    .blur();

                $yona.notify(Messages(watching ? "issue.unwatch.start" : "issue.watch.start"), 3000);
            });
        }

        /**
         * Initialize fileUploader
         *
         * @private
         */
        function _initFileUploader(){
            const oUploader = yona.Files.getUploader(elements.uploader, elements.textarea);

            if(oUploader){
                (new yona.Attachments({
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
            (target || $(".attachments")).each((i, container) =>{
                if(!$(container).data("isYobiAttachment")){
                    (new yona.Attachments({"elContainer": container}));
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
             .always(() =>{
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

            const timelineList = _getRenderedTimeline(resultHTML);

            setTimeout(() =>{
                elements.timelineList.replaceWith(timelineList);
                elements.timelineList = timelineList;
                vars.timelineHTML = resultHTML;

                const isChanged = (vars.timelineItems !== _countTimelineItems());
                const isTimelineChangedOnTyping = vars.isTextareaOnFocused && isChanged;

                const scrollGap = isTimelineChangedOnTyping ?
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
            const timelineList = elements.timelineList.clone();
            timelineList.html(timelineHTML);

            _initFileDownloader(timelineList.find(".attachments"));
            yona.Markdown.enableMarkdown(timelineList.find("[markdown]"));
            yona.Request.bind(timelineList[0]); // delete button

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
            vars.timelineUpdateTimer = setInterval(() =>{
                const isEditing = (elements.timelineWrap.find(".comment-update-form:visible").length > 0)
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
            const commentForm = $("#comment-form");
            const dynamicCommentBtn = $("#dynamic-comment-btn");
            const withStateTransitionInput = $("<input type='hidden' name='withStateTransition'>");

            commentForm.prepend(withStateTransitionInput);

            dynamicCommentBtn.removeClass("hidden");
            dynamicCommentBtn.html(Messages("button.nextState." + vars.nextState));
            dynamicCommentBtn.on("click", () =>{
                if(elements.textarea.val().length > 0){
                    withStateTransitionInput.val("true");
                    commentForm.submit();
                } else {
                    withStateTransitionInput.val("");
                    location.href = vars.urls.nextState;
                }
            });

            elements.textarea.on("keyup", () =>{
                if(elements.textarea.val().length > 0){
                    dynamicCommentBtn.html(Messages("button.commentAndNextState." + vars.nextState));
                } else {
                    dynamicCommentBtn.html(Messages("button.nextState." + vars.nextState));
                }
            });

            // if yona.ShortcutKey exists
            if(yona.ShortcutKey){
                yona.ShortcutKey.attach("CTRL+SHIFT+ENTER", (htInfo) =>{
                    if(htInfo.welTarget.is(elements.textarea)){
                        dynamicCommentBtn.click();
                    }
                });
            }
        }


        // initialize
        _init(options || {});
    };
})("yona.issue.View");
