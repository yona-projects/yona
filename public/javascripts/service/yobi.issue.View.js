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
            _setLabelTextColor();
            
            _setTimelineUpdateTimer();
        }

        /**
         * initialize HTML Element variables
         */
        function _initElement(htOptions){
            htElement.welUploader = $("#upload");
            htElement.welTextarea = $("#comment-editor");

            htElement.welLabels = $('.issue-label');
            htElement.welBtnWatch = $('#watch-button');

            htElement.welIssueUpdateForm = htOptions.welIssueUpdateForm;
            htElement.sIssueCheckBoxesSelector = htOptions.sIssueCheckBoxesSelector;

            htElement.welChkIssueOpen = $("#issueOpen");
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

            htVar.oAssignee  = new yobi.ui.Dropdown({"elContainer": htOptions.welAssignee});
            htVar.oMilestone = new yobi.ui.Dropdown({"elContainer": htOptions.welMilestone});

            // for auto-update
            htVar.bTimelineUpdating = false;
            htVar.nTimelineUpdateTimer = null;
            htVar.nTimelineUpdatePeriod = htOptions.nTimelineUpdatePeriod || 60000; // 60000ms = 60s = 1m
            htVar.sTimelineHTML = htElement.welTimelineList.html();
            htVar.nTimelineItems = _countTimelineItems(); // 타임라인 항목 갯수
            htVar.bOnFocusTextarea = false; // 댓글 작성폼에 포커스가 있는지 여부
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            // 지켜보기
            htElement.welBtnWatch.click(_onClickBtnWatch);
            
            // 이슈 정보 업데이트
            htElement.welChkIssueOpen.change(_onChangeIssueOpen);
            htVar.oMilestone.onChange(_onChangeMilestone);
            htVar.oAssignee.onChange(_onChangeAssignee);

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
         * 이슈 해결/미해결 스위치 변경시
         */
        function _onChangeIssueOpen(){
            var welTarget  = $(this);
            var bChecked   = welTarget.prop("checked");
            var sNextState = bChecked ? "OPEN" : "CLOSED";
            
            _requestUpdateIssue({
               "htData" : {"state": sNextState},
               "fOnLoad": function(){
                    welTarget.prop("checked", bChecked);
                    _updateTimeline();
                },
               "fOnError": function(oRes){
                    welTarget.prop("checked", !bChecked);
                    _onErrorRequest(Messages("issue.update.state"), oRes);
               }
            });
        }
        
        /**
         * 담당자 변경시
         * 
         * @param {String} sValue 선택된 항목의 값
         */
        function _onChangeAssignee(sValue){
            _requestUpdateIssue({
               "htData"  : {"assignee.id": sValue},
               "fOnLoad" : function(){
                   $yobi.notify(Messages("issue.update.assignee"), 3000);
                   htVar.oAssignee.selectItem("li[data-id=" + sValue + "]");
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
         * @param {String} sValue 선택된 항목의 값
         */
        function _onChangeMilestone(sValue){
            _requestUpdateIssue({
               "htData"  : {"milestone.id": sValue},
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
         * set Labels foreground color as contrast to background color
         */
        function _setLabelTextColor(){
            var welLabel;
            var sBgColor, sColor;

            htElement.welLabels.each(function(nIndex, elLabel){
                welLabel = $(elLabel);
                sBgColor = welLabel.css("background-color");
                sColor = $yobi.getContrastColor(sBgColor);
                welLabel.css("color", sColor);
            });

            welLabel = null;
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
                if(htVar.bTimelineUpdating !== true){
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

        // initialize
        _init(htOptions || {});
    };
})("yobi.issue.View");
