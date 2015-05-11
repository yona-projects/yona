/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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
         */
        function _init(htOptions){
            _initVar(htOptions);
            _initElement();
            _attachEvent();

            _initFileUploader();
            _initFileDownloader();
            _initToggleCommentsButton();
            _initCodeComment();
            _initMiniMap();
            _scrollToHash();
            _setReviewListHeight();
            _setAllBtnThreadHerePosition();
        }

        /**
         * initialize variables except element
         */
        function _initVar(htOptions) {
            htVar.bCommentable = htOptions.bCommentable;
            htVar.sWatchUrl = htOptions.sWatchUrl;
            htVar.sUnwatchUrl = htOptions.sUnwatchUrl;
            htVar.sParentCommitId = htOptions.sParentCommitId;
            htVar.sCommitId = htOptions.sCommitId;
            htVar.htThreadWrap = {};

            // 미니맵
            htVar.sQueryMiniMap = htOptions.sQueryMiniMap || "li.comment";
            htVar.sTplMiniMapLink = '<a href="#${id}" style="top:${top}px; height:${height}px;"></a>';

            // yobi.Attachments
            htVar.sTplFileItem = $('#tplAttachedFile').text();
        }

        /**
         * initialize element
         */
        function _initElement(){
            htElement.welContainer = $(".codediff-wrap");

            // 변경내역
            htElement.welDiffWrap = htElement.welContainer.find("div.diffs-wrap");
            htElement.welDiffBody = htElement.welDiffWrap.find(".diff-body");
            htElement.waDiffContainers = htElement.welDiffWrap.find(".diff-container");

            // 리뷰영역
            htElement.welCommentWrap = htElement.welContainer.find("div.board-comment-wrap");
            htElement.welReviewWrap = htElement.welContainer.find("div.review-wrap");
            htElement.welReviewContainer = htElement.welReviewWrap.find("div.review-container");
            htElement.waBtnToggleReviewWrap = htElement.welContainer.find("button.btn-show-reviewcards,button.btn-hide-reviewcards");
            htElement.welReviewList = htElement.welContainer.find("div.review-list");
            // 전체 댓글 (Non-Ranged comment thread)
            htElement.welUploader = $("#upload");
            htElement.welTextarea = $('textarea[data-editor-mode="comment-body"]');

            // 지켜보기
            htElement.welBtnWatch = $('#watch-button');

            // 미니맵
            htElement.welMiniMap = $("#minimap"); // .minimap-outer
            htElement.welMiniMapWrap = htElement.welMiniMap.find(".minimap-wrap");
            htElement.welMiniMapCurr = htElement.welMiniMapWrap.find(".minimap-curr");
            htElement.welMiniMapLinks = htElement.welMiniMapWrap.find(".minimap-links");

            // 코드받기
            htElement.welBtnAccept = $("#btnAccept");
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            htElement.welBtnWatch.on("click", _onClickBtnWatchToggle);

            $(window).on({
                "resize": _initMiniMap,
                "scroll": _updateMiniMapCurr
            });

            htElement.waBtnToggleReviewWrap.on("click", function(){
                htElement.welContainer.toggleClass("diffs-only");

                var positionTop =  10;
                if(htElement.welReviewContainer.offset().top - positionTop < $(document).scrollTop()){
                    htElement.welReviewContainer
                        .addClass('affix-top')
                        .removeClass('affix');
                }

            });

            // 리뷰카드 링크 클릭시
            htElement.welReviewWrap.on("click", "a.review-card", _onClickReviewCardLink);

            // Diff 영역에서 스크롤시
            // .comment-thread-wrap 의 좌우 위치를 맞춰준다
            $(".diff-partial-code").on("scroll", function(){
                var welPartial = $(this);
                var sHashCode = $(this).data("hashcode");
                htVar.htThreadWrap[sHashCode] = htVar.htThreadWrap[sHashCode] || welPartial.find(".comment-thread-wrap");
                htVar.htThreadWrap[sHashCode].css("margin-left", welPartial.scrollLeft() + "px");
            });

            $(window).on("hashchange", _onHashChange);

            if(htElement.welBtnAccept.length > 0 && htElement.welBtnAccept.data("requestAs")){
                htElement.welBtnAccept.data("requestAs").on("beforeRequest", function(){
                    htElement.welBtnAccept.attr('disabled','disabled');
                    NProgress.start();
                });
            }

            _setReviewWrapAffixed();

            $(window).on('resize scroll', _setReviewListHeight);

            $("#branches").on("change", function(weEvt){
                location.href = weEvt.val;
            });
        }

        /**
         * @param weEvt
         * @returns {boolean}
         * @private
         */
        function _onClickReviewCardLink(weEvt){
            var sThreadId = _getHashFromLinkString($(weEvt.currentTarget).attr("href"));

            if(!_isThreadExistOnCurrentPage(sThreadId)){
                return;
            }

            var sPreviousHash = location.hash;
            location.hash = sThreadId;

            if(sPreviousHash === location.hash) {
                $(window).trigger("hashchange");
            }

            weEvt.preventDefault();
            return false;
        }

        /**
         * @param sLink
         * @returns {*}
         * @private
         */
        function _getHashFromLinkString(sLink){
            return sLink.split("#").pop();
        }

        /**
         * @param sHash
         * @returns {boolean}
         * @private
         */
        function _isThreadExistOnCurrentPage(sHash){
            return ($("#" + sHash).length > 0);
        }

        /**
         * @private
         */
        function _onHashChange(){
            if(location.hash) {
                _scrollToAndHighlight($(location.hash));
            }
        }

        /**
         * @param welTarget
         * @private
         */
        function _scrollToAndHighlight(welTarget){
            var welThread = _getThread(welTarget);

            if(!welTarget || welTarget.length === 0 || !welThread || welThread.length === 0){
                return;
            }

            if(_isFoldedThread(welThread)){
                welThread.removeClass("fold");
            }

            _showReviewCardsTabByState(welThread.data("state"));
            window.scrollTo(0, welTarget.offset().top - 50);
            welTarget.effect("highlight");
        }

        function _getThread(welTarget){
            return (welTarget.hasClass("comment-thread-wrap")) ? welTarget : welTarget.parents(".comment-thread-wrap");
        }

        /**
         * Show review-card list tab of {@code state}
         *
         * @param state
         * @private
         */
        function _showReviewCardsTabByState(state){
            if(["open", "closed"].indexOf(state) > -1) {
                $("a[href=#reviewcards-" + state + "]").tab("show");
            }
        }

        /**
         * @param welTarget
         * @returns {boolean}
         * @private
         */
        function _isFoldedThread(welTarget){
            return welTarget.hasClass("fold") && (welTarget.find(".btn-thread-here").length > 0);
        }

        /**
         * @private
         */
        function _scrollToHash(){
            if(location.hash){
                $(window).trigger("hashchange");
            }
        }

        /**
         * @param weEvt
         * @private
         */
        function _onClickBtnWatchToggle(weEvt){
            var welTarget = $(weEvt.target);
            var bWatched = welTarget.hasClass("active");

            $yobi.sendForm({
                "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                "fOnLoad": function(){
                    welTarget.toggleClass("active ybtn-watching");
                }
            });
        }

        /**
         * @private
         */
        function _setReviewWrapAffixed(){
            if(htElement.welReviewContainer.length === 0){
                return;
            }

            htElement.welReviewContainer.affix({
                offset : {top : htElement.welReviewContainer.offset().top-10}
            });
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

            $("form.review-form").each(function(i, el){
                var form = $(el);
                var container = form.find(".upload-wrap");
                var textarea = form.find("textarea");
                var uploader = yobi.Files.getUploader(container, textarea);

                if(uploader){
                    (new yobi.Attachments({
                        "elTextarea"   : textarea,
                        "elContainer"  : container,
                        "sTplFileItem" : htVar.sTplFileItem,
                        "sUploaderId"  : uploader.attr("data-namespace")
                    }));

                }
            });
        }

        /**
         * initialize fileDownloader
         */
        function _initFileDownloader(){
            $(".attachments").each(function(i, elContainer){
                if(!$(elContainer).data("isYobiAttachment")){
                    (new yobi.Attachments({"elContainer": elContainer}));
                }
            });
        }

        /**
         * initialize toggle comments button
         */
        function _initToggleCommentsButton(){
            $('#toggle-comments').on('click', function(){
                htElement.waDiffContainers.toggleClass('show-comments');
                htElement.welMiniMap.toggle();
            });
        }

        /**
         * @private
         */
        function _initCodeComment(){
            if(htVar.bCommentable){
                _initCodeCommentBox();
                _initCodeCommentBlock();

                $('div.diff-body[data-outdated!="true"]').on("click", "tr[data-line] .linenum", _onClickLineNumA);

                htElement.welDiffWrap.on("click", "button.btn-thread", _onClickBtnReplyOnThread);
            } else {
                htElement.welDiffBody.find(".linenum > .yobicon-comments").hide();
            }

            htElement.welDiffBody.on("click", ".btn-thread-minimize", _onClickBtnFoldThread);

            // block/unblock with thread range with mouseenter/leave event
            $('div[data-toggle="CodeCommentThread"]').on({
                "mouseenter": _onMouseOverCodeCommentThread,
                "mouseleave": _onMouseLeaveCodeCommentThread
            });
        }

        /**
         * @private
         */
        function _initCodeCommentBox() {
            yobi.CodeCommentBox.init({
                "sTplFileItem": htVar.sTplFileItem
            });

            $(window).on({
                "CodeCommentBox:aftershow": _updateMiniMap,
                "CodeCommentBox:afterhide": function(){
                    _updateMiniMap();
                    yobi.CodeCommentBlock.unblock();
                    htVar.htBlockInfo = null;
                }
            });
        }

        /**
         * @param weEvt
         * @private
         */
        function _onClickBtnReplyOnThread(weEvt){
            var welButton = $(weEvt.currentTarget);
            var welActions = welButton.closest(".thread-actrow");

            $(window).on("CodeCommentBox:afterhide", function(){
                welActions.show();
                $(window).off("CodeCommentBox:afterhide", arguments.callee);
            });
            yobi.CodeCommentBox.show(welButton);

            welActions.hide();
        }

        /**
         * @private
         */
        function _initCodeCommentBlock(){
            yobi.CodeCommentBlock.init({
                "welContainer"       : htElement.welDiffBody,
                "welPopButtonOnBlock": htElement.welDiffBody.find(".btnPop")
            });

            htElement.welDiffBody.on("click", ".btnPop", _onClickBtnAddBlockComment);
            htElement.welDiffBody.on("mousedown", ":not(.btnPop)", _onMouseDownDiffBody);
        }

        /**
         * @param weEvt
         * @private
         */
        function _onMouseDownDiffBody(weEvt){
            if(!_isMouseLeftButtonPressed(weEvt)){
                return;
            }

            if(_isTargetBelongs(weEvt.target, ".comment-thread-wrap,.review-form")){
                return;
            }

            if(!_isSelectionExists()){
                yobi.CodeCommentBox.hide();
                htVar.htBlockInfo = null;
            }
        }

        /**
         * @param weEvt
         * @returns {boolean}
         * @private
         */
        function _isMouseLeftButtonPressed(weEvt){
            return (weEvt.which === 1);
        }

        /**
         * @param elTarget
         * @param sQuery
         * @returns {boolean}
         * @private
         */
        function _isTargetBelongs(elTarget, sQuery){
            return ($(elTarget).parents(sQuery).length > 0);
        }

        /**
         * @returns {boolean}
         * @private
         */
        function _isSelectionExists(){
            return (document.getSelection().toString().length > 0);
        }

        /**
         * @private
         */
        function _onClickBtnAddBlockComment(){
            var htBlockInfo = yobi.CodeCommentBlock.getData();
            yobi.CodeCommentBlock.block(htBlockInfo);

            var sLineNum = htBlockInfo.bIsReversed ? htBlockInfo.nStartLine : htBlockInfo.nEndLine;
            var sLineType = htBlockInfo.bIsReversed ? htBlockInfo.sStartType : htBlockInfo.sEndType;
            var welContainer = $('.diff-container[data-file-path="' + htBlockInfo.sFilePath + '"]');
            var welTR = welContainer.find('tr[data-line="' + sLineNum + '"][data-type="' + sLineType + '"]');
            welTR.data("blockInfo", htBlockInfo);

            yobi.CodeCommentBox.show(welTR, {
                "sPlacement": htBlockInfo.bIsReversed ? "top" : "bottom",
                "nAdjustmentTop": htElement.welDiffBody.position().top
            });

            var nMarginFromBorder = 20;

            if(!htBlockInfo.bIsReversed && _doesCommentBoxOutOfWindow()){
                window.scrollTo(0, yobi.CodeCommentBox.offset().top + yobi.CodeCommentBox.height() - window.innerHeight + nMarginFromBorder);
            }

            if(htBlockInfo.bIsReversed && _doesCommentBoxOutOfWindow()){
                window.scrollTo(0, yobi.CodeCommentBox.offset().top - nMarginFromBorder);
            }

            htVar.htBlockInfo = htBlockInfo;
        }

        /**
         * @returns {boolean}
         * @private
         */
        function _doesCommentBoxOutOfWindow(){
            var nScrollTop = $(document.body).scrollTop();
            var nOffsetTop = yobi.CodeCommentBox.offset().top;

            return (nScrollTop + window.innerHeight < nOffsetTop) ||
                   (nOffsetTop + yobi.CodeCommentBox.height() > nScrollTop + window.innerHeight);
        }

        /**
         * @param {Event} weEvt
         */
        function _onClickLineNumA(weEvt){
            window.getSelection().removeAllRanges();

            var welTarget = $(weEvt.target).closest("tr").find("td.code pre");
            var oNode = welTarget.get(0).childNodes[0];
            var oRange = document.createRange();

            oRange.setStart(oNode, 0);
            oRange.setEnd(oNode, oNode.length);
            window.getSelection().addRange(oRange);

            welTarget.trigger("mouseup");
            _onClickBtnAddBlockComment();
        }

        /**
         * On Click fold/unfold thread toggle button
         *
         * @param weEvt
         * @private
         */
        function _onClickBtnFoldThread(weEvt){
            $(weEvt.currentTarget).closest(".comment-thread-wrap").toggleClass("fold");
            _setAllBtnThreadHerePosition();
        }

        /**
         * Set positions of all .btn-thread-here button
         *
         * @private
         */
        function _setAllBtnThreadHerePosition(){
            htElement.welDiffBody.find(".btn-thread-here").each(function(i, el){
                _setBtnThreadHerePosition($(el));
            });
        }

        /**
         * Set position of .btn-thread-here , which marks folded thread
         *
         * @param welButton
         * @private
         */
        function _setBtnThreadHerePosition(welButton){
            var welThread = welButton.closest(".comment-thread-wrap");
            var nPadding = 10;

            // set unfold button right
            welButton.css("right", ((welThread.index() * welButton.width()) + nPadding) + "px");

            // set unfold button top
            // find target line with thread
            var welEndLine = _getTargetLineByThread(welThread);
            if(welEndLine.length > 0){
                welButton.css("top", welEndLine.position().top - nPadding + "px");
            }
        }

        /**
         * Get last line element in target range of comment-thread
         *
         * @param welThread
         * @returns {*}
         * @private
         */
        function _getTargetLineByThread(welThread){
            var sEndLineQuery = 'tr[data-line="' + welThread.data("range-endline") + '"]' +
                '[data-side="' + welThread.data("range-endside") + '"]';
            var welEndLine = welThread.closest("tr").prev(sEndLineQuery);

            return welEndLine;
        }

        /**
         * On MouseEnter event fired from CodeCommentThread
         * @param weEvt
         * @private
         */
        function _onMouseOverCodeCommentThread(weEvt){
            // only no mouse button clicked
            if(_doesMouseButtonPressed(weEvt)){
                return;
            }

            var welThread = $(weEvt.currentTarget);
            var htBlockInfo = {
                "sPath"       : welThread.data("range-path"),
                "sStartSide"  : welThread.data("range-startside"),
                "nStartLine"  : parseInt(welThread.data("range-startline"), 10),
                "nStartColumn": parseInt(welThread.data("range-startcolumn"), 10),
                "sEndSide"    : welThread.data("range-endside"),
                "nEndLine"    : parseInt(welThread.data("range-endline"), 10),
                "nEndColumn"  : parseInt(welThread.data("range-endcolumn"), 10)
            };
            yobi.CodeCommentBlock.block(htBlockInfo);
        }

        /**
         * Returns whether any mouse button has been pressed.
         *
         * @param weEvt
         * @returns {boolean}
         * @private
         */
        function _doesMouseButtonPressed(weEvt){
            return (typeof weEvt.buttons !== "undefined") ? (weEvt.buttons !== 0) : (weEvt.which !== 0);
        }

        /**
         * On MouseLeave event fired from CodeCommentThread
         * @private
         */
        function _onMouseLeaveCodeCommentThread(){
            yobi.CodeCommentBlock.unblock();

            if(yobi.CodeCommentBox.isVisible() && htVar.htBlockInfo){
                yobi.CodeCommentBlock.block(htVar.htBlockInfo);
            }
        }

        function _initMiniMap(){
            _setMiniMapRatio();
            _updateMiniMap();
            _resizeMiniMapCurr();
        }

        function _setMiniMapRatio(){
            var nDocumentHeight = $(document).height();
            var nMapHeight = htElement.welMiniMapWrap.height();

            htVar.nMiniMapRatio = nMapHeight / nDocumentHeight;
        }

        function _updateMiniMapCurr(){
            htElement.welMiniMapCurr.css("top", Math.ceil($(document.body).scrollTop() * htVar.nMiniMapRatio) + "px");
        }

        function _resizeMiniMapCurr(){
            htElement.welMiniMapCurr.css("height", Math.ceil(window.innerHeight * htVar.nMiniMapRatio) + "px");
        }

        function _updateMiniMap(){
            var aLinks = [];
            var welTarget, nTop;
            var waTargets = $(htVar.sQueryMiniMap);

            if(waTargets.length > 0){
                waTargets.each(function(i, el){
                    welTarget = $(el);

                    aLinks.push($yobi.tmpl(htVar.sTplMiniMapLink, {
                        "id"    : welTarget.attr("id"),
                        "top"   : Math.ceil(welTarget.offset().top * htVar.nMiniMapRatio),
                        "height": Math.ceil(welTarget.height() * htVar.nMiniMapRatio)
                    }));
                });
                htElement.welMiniMapLinks.html(aLinks.join(""));
                htElement.welMiniMap.show();
            } else {
                htElement.welMiniMap.hide();
            }
        }

        function _setReviewListHeight() {
            var nMaxHeight;
            
            if(htElement.welReviewContainer.hasClass('affix')) {
                var nCodeDiffWrapOffsetBottom = htElement.welContainer.position().top + htElement.welContainer.height();
                var nReviewListOffsetBottom = htElement.welReviewList.offset().top + htElement.welReviewList.height();
                var nReviewListDefaultMarginBottom = 15;
                var nDiffWrapBottomPadding = 90;

                if(nCodeDiffWrapOffsetBottom <= nReviewListOffsetBottom + nReviewListDefaultMarginBottom) {
                    nMaxHeight = nCodeDiffWrapOffsetBottom  - $(document).scrollTop() + nDiffWrapBottomPadding;
                } else {
                    nMaxHeight = $(window).height() - htElement.welReviewList.position().top - nReviewListDefaultMarginBottom;
                }
            } else {
                nMaxHeight = htElement.welContainer.height() - htElement.welReviewList.position().top;
            }

            htElement.welReviewList.css({'max-height': nMaxHeight +'px'});
        }

        _init(htOptions || {});
    };
})("yobi.code.Diff");
