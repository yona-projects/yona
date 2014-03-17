/**
 * @(#)yobi.code.Diff.js 2013.04.04
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
         */
        function _init(htOptions){
            _initVar(htOptions);
            _initElement();
            _attachEvent();

            _initFileUploader();
            _initFileDownloader();
            _initToggleCommentsButton();
            _initCodeComment();
            _initReviewWrapAffixed();
            _initMiniMap();

            _scrollToHash();
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
            htVar.rxSlashes = /\//g;

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
            htElement.welReviewWrap = htElement.welContainer.find("div.review-wrap");
            htElement.welReviewList = htElement.welReviewWrap.find("div.review-wrap-scroll");
            htElement.waBtnToggleReviewWrap = htElement.welContainer.find("button.btn-show-reviewcards,button.btn-hide-reviewcards");

            // 전체 댓글 (Non-Ranged comment thread)
            htElement.welUploader = $("#upload");
            htElement.welTextarea = $("#comment-editor");

            // 지켜보기
            htElement.welBtnWatch = $('#watch-button');

            // 미니맵
            htElement.welMiniMap = $("#minimap"); // .minimap-outer
            htElement.welMiniMapWrap = htElement.welMiniMap.find(".minimap-wrap");
            htElement.welMiniMapCurr = htElement.welMiniMapWrap.find(".minimap-curr");
            htElement.welMiniMapLinks = htElement.welMiniMapWrap.find(".minimap-links");
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            // 지켜보기
            htElement.welBtnWatch.on("click", _onClickBtnWatchToggle);

            // 미니맵
            $(window).on({
                "resize": _initMiniMap,
                "scroll": _updateMiniMapCurr
            });

            // 리뷰목록 토글
            htElement.waBtnToggleReviewWrap.on("click", function(){
                htElement.welContainer.toggleClass("diffs-only");
            });
        }

        /**
         * 지켜보기 버튼 클릭시 이벤트 핸들러
         *
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
         * Diff 중에서 특정 파일을 #path 로 지정한 경우
         * Diff render 완료 후 해당 파일 위치로 스크롤 이동
         * @private
         */
        function _scrollToHash(){
            if(document.location.hash){
                var sTargetId = document.location.hash.substr(1).replace(htVar.rxSlashes, "-");
                var welTarget = $(document.getElementById(sTargetId));

                if(welTarget.length > 0){
                    window.scrollTo(0, welTarget.offset().top);
                }
            }
        }

        /**
         * 리뷰 목록이 존재하면 스크롤에 따라 일정한 위치에 고정되도록 설정
         * @private
         */
        function _initReviewWrapAffixed(){
            if(!htElement.welReviewWrap.length){
                return;
            }

            htVar.nAffixTop = htElement.welReviewWrap.offset().top - 25;
            _updateReviewWrapAffixed();
            $(window).on("scroll", _updateReviewWrapAffixed);
        }

        /**
         * 스크롤에 따라 리뷰 목록 위치 고정 여부 처리
         * @private
         */
        function _updateReviewWrapAffixed(){
            if($(window).scrollTop() > htVar.nAffixTop){
                htElement.welReviewWrap.addClass("fixed");
            } else {
                htElement.welReviewWrap.removeClass("fixed");
            }
        }

        /**
         * 댓글 폼 파일 업로더 초기화
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
         * 첨부파일 표시
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
         * 댓글 표시하기 토글
         * initialize toggle comments button
         */
        function _initToggleCommentsButton(){
            $('#toggle-comments').on('click', function(){
                htElement.waDiffContainers.toggleClass('show-comments');
                htElement.welMiniMap.toggle();
            });
        }

        /**
         * 코드 댓글 관련 초기화 함수
         * 작성 권한이 있다면 댓글 폼을 활성화 시키고
         * 작성 권한이 없으면 사용하지 않을 화면 요소를 감춘다
         *
         * @private
         */
        function _initCodeComment(){
            // 댓글 작성 권한 유무에 따른 처리
            if(htVar.bCommentable){
                // 블록 댓글 기능 초기화
                _initCodeCommentBox();
                _initCodeCommentBlock();

                // 줄번호 클릭으로 댓글 작성 (예전 댓글 기능)
                $('div.diff-body[data-outdated!="true"] tr .linenum:first-child').on("click", _onClickLineNumA);

                // 스레드에 댓글 추가 버튼
                htElement.welDiffWrap.on("click", "button.btn-thread", _onClickBtnReplyOnThread);
            } else {
                htElement.welDiffBody.find(".linenum > .yobicon-comments").hide();
            }

            // 스레드 접기 토글 버튼
            htElement.welDiffBody.on("click", ".btn-thread-minimize", _onClickBtnFoldThread);

            // block/unblock with thread range with mouseenter/leave event
            $('div[data-toggle="CodeCommentThread"]').on({
                "mouseenter": _onMouseOverCodeCommentThread,
                "mouseleave": _onMouseLeaveCodeCommentThread
            });
        }

        /**
         * 댓글 상자 초기화
         *
         * @private
         */
        function _initCodeCommentBox() {
            yobi.CodeCommentBox.init({
                "fOnAfterShow": _updateMiniMap,
                "fOnAfterHide": function(){
                    _updateMiniMap();
                    yobi.CodeCommentBlock.unblock();
                },
                "sTplFileItem": htVar.sTplFileItem
            });
        }

        /**
         * 스레드의 [댓글 입력] 버튼을 클릭했을 때의 이벤트 핸들러
         *
         * @param weEvt
         * @private
         */
        function _onClickBtnReplyOnThread(weEvt){
            // 댓글 박스가 [댓글입력] 버튼을 덮는 위치에 오도록
            // 그 버튼 높이+여백만큼 top 값을 보정한다
            var welButton = $(weEvt.currentTarget);
            var nGap = (htElement.welDiffBody.has(welButton).length > 0) ? htElement.welDiffBody.position().top : 0;
            var nAdjustmentTop = (-1 * welButton.outerHeight()) + nGap - 4;

            yobi.CodeCommentBox.show(welButton, {
                "nAdjustmentTop": nAdjustmentTop
            });
        }

        /**
         * 블록댓글 기능 초기화
         * 변경내역 영역에서 블록을 지정하면 댓글 버튼을 표시하기 위해서 사용한다.
         * @private
         */
        function _initCodeCommentBlock(){
            yobi.CodeCommentBlock.init({
                "welContainer"    : htElement.welDiffBody,
                "welButtonOnBlock": htElement.welDiffBody.find(".btnPop")
            });

            htElement.welDiffBody.on("click", ".btnPop", _onClickBtnAddBlockComment);
            htElement.welDiffBody.on("mousedown", ":not(.btnPop)", function(){
                if(document.getSelection().toString().length === 0){
                    yobi.CodeCommentBox.hide();
                }
            });
        }

        /**
         * 변경내역 영역에서 블록을 지정하고 댓글작성 버튼을 클릭했을 때 이벤트 핸들러.
         * 선택한 블록의 영역을 yobi.CodeCommentBlock 를 이용해 표시하고, 정보를 얻어서
         * 적절한 위치에 yobi.CodeCommentBox 를 이용해 댓글 작성 폼을 표시해준다
         *
         * @private
         */
        function _onClickBtnAddBlockComment(){
            // 블록 정보를 얻어서 블록 표시
            var htBlockInfo = yobi.CodeCommentBlock.getData();
            yobi.CodeCommentBlock.block(htBlockInfo);

            // 댓글을 표시할 줄을 찾아 CodeCommentBox 호출
            var sLineNum = htBlockInfo.bIsReversed ? htBlockInfo.nStartLine : htBlockInfo.nEndLine;
            var sLineType = htBlockInfo.bIsReversed ? htBlockInfo.sStartType : htBlockInfo.sEndType;
            var welContainer = $('.diff-container[data-file-path="' + htBlockInfo.sFilePath + '"]');
            var welTR = welContainer.find('tr[data-line="' + sLineNum + '"][data-type="' + sLineType + '"]');
            welTR.data("blockInfo", htBlockInfo); // 블록정보

            yobi.CodeCommentBox.show(welTR, {
                "sPlacement": htBlockInfo.bIsReversed ? "top" : "bottom",
                "nAdjustmentTop": htElement.welDiffBody.position().top
            });

            if(!htBlockInfo.bIsReversed){
                window.scrollTo(0, welTR.offset().top - 50);
            }
        }

        /**
         * 특정 줄의 줄번호 컬럼(왼쪽 것)을 클릭했을 때의 이벤트 핸들러
         *
         * 예를 들면 아래와 같은 줄에서 줄번호 "240"이 있는 컬럼을 클릭했을 때
         * |  240 |  244 |  $(window).click(function(){ // for IE |
         *
         * 다음과 같이 조건에 따라 댓글 창이 토글된다.
         * 1) 다음 줄에 댓글 창이 없다면 댓글 창이 나타난다.
         * 2) 다음 줄에 댓글 창이 있다면 그 댓글 창이 사라진다.
         *
         * ps. 원래 댓글 아이콘을 클릭하면 댓글 창이 나타나게 하려고
         * 했었는데, 아이콘이 너무 작아서 누르기 힘들길래 이렇게 고쳤다.
         *
         * @param {Event} weEvt
         */
        function _onClickLineNumA(weEvt){
            // 기존의 Selection 제거하고
            window.getSelection().removeAllRanges();

            var welTarget = $(weEvt.target).closest("tr").find("td.code pre");
            var oNode = welTarget.get(0).childNodes[0];
            var oRange = document.createRange();

            // 클릭한 줄을 전부 선택한 것으로 취급해서
            oRange.setStart(oNode, 0);
            oRange.setEnd(oNode, oNode.length);
            window.getSelection().addRange(oRange);

            // 블록 댓글 작성 폼을 띄운다
            welTarget.trigger("mouseup");
            _onClickBtnAddBlockComment();
        }

        /**
         * On Click fold/unfold thread toggle button
         * @param weEvt
         * @private
         */
        function _onClickBtnFoldThread(weEvt){
            var welThread = $(weEvt.currentTarget).closest(".comment-thread-wrap");
            var welButton = welThread.find(".btn-thread-here");
            var nMarginWidth = welButton.width() + 7;
            var nMarginHeight = welButton.height() - 7;
            var nPaddingRight = 10;
            welThread.toggleClass("fold");

            // set unfold button right
            welButton.css("right", ((welThread.index() * nMarginWidth) + nPaddingRight) + "px");

            // set unfold button top
            // find target line with thread
            var sEndLineQuery = 'tr[data-line="' + welThread.data("range-endline") + '"]' +
                '[data-side="' + welThread.data("range-endside") + '"]';
            var welEndLine = welThread.closest("tr").prev(sEndLineQuery);

            if(welEndLine.length > 0){
                welButton.css("top", welEndLine.position().top + nMarginHeight + "px");
            }
        }

        /**
         * On MouseEnter event fired from CodeCommentThread
         * @param weEvt
         * @private
         */
        function _onMouseOverCodeCommentThread(weEvt){
            // only no mouse button clicked
            if(weEvt.which !== 0){
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
         * On MouseLeave event fired from CodeCommentThread
         * @private
         */
        function _onMouseLeaveCodeCommentThread(){
            yobi.CodeCommentBlock.unblock();
        }

        /**
         * 댓글 미니맵 초기화
         * 모듈 로딩시(_init)와 창 크기 변경시(_attachEvent:window.resize) 호출됨
         */
        function _initMiniMap(){
            _setMiniMapRatio();
            _updateMiniMap();
            _resizeMiniMapCurr();
        }

        /**
         * 미니맵 비율을 설정한다
         * 비율 = 미니맵 높이 / 문서 전체 높이
         */
        function _setMiniMapRatio(){
            var nDocumentHeight = $(document).height();
            var nMapHeight = htElement.welMiniMapWrap.height();

            htVar.nMiniMapRatio = nMapHeight / nDocumentHeight;
        }

        /**
         * 현재 스크롤 위치에 맞추어 minimap-curr 의 위치도 움직인다
         */
        function _updateMiniMapCurr(){
            htElement.welMiniMapCurr.css("top", Math.ceil($(document.body).scrollTop() * htVar.nMiniMapRatio) + "px");
        }

        /**
         * 미니맵 스크롤 위치 표시기(minimap-curr)의 높이를
         * 비율에 맞추어 조정한다
         */
        function _resizeMiniMapCurr(){
            htElement.welMiniMapCurr.css("height", Math.ceil(window.innerHeight * htVar.nMiniMapRatio) + "px");
        }

        /**
         * tr.comments 의 위치, 높이를 기준으로 미니맵을 표시한다
         *
         * 화면 크기 변경(window.resize)이나 화면 내용 변동시(_initMiniMap)
         * 이미 생성한 DOM을 일일히 제어하는 것 보다 HTML을 새로 그리는 것이 빠르다
         *
         * 표시할 항목이 없다면 미니맵은 감춤
         */
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

        _init(htOptions || {});
    };
})("yobi.code.Diff");
