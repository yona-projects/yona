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
            _initElement(htOptions);
            _attachEvent();
            _render();

            _initFileUploader();
            _initFileDownloader();
            _initToggleCommentsButton();
            _initMiniMap();
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
        function _initElement(htOptions){
            htElement.welUploader = $("#upload");
            htElement.welTextarea = $("#comment-editor");

            var welHidden = $('<input>').attr('type', 'hidden');

            htElement.welDiff = htOptions.welDiff || $('#commit');
            htElement.welEmptyCommentForm = $('#comment-form')
                .append(welHidden.clone().attr('name', 'path'))
                .append(welHidden.clone().attr('name', 'line'))
                .append(welHidden.clone().attr('name', 'side'))
                .append(welHidden.clone().attr('name', 'commitA'))
                .append(welHidden.clone().attr('name', 'commitB'))
                .append(welHidden.clone().attr('name', 'commitId'));
            htElement.welComments = $('ul.comments');

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
            if (htElement.welBtnWatch == null) {
                return;
            }

            htElement.welBtnWatch.click(function(weEvt) {
                var welTarget = $(weEvt.target);
                var bWatched = welTarget.hasClass("active");

                $yobi.sendForm({
                    "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                    "fOnLoad": function(){
                        welTarget.toggleClass("active ybtn-watching");
                    }
                });
            });

            $(window).on("resize", _initMiniMap);
            $(window).on("scroll", _updateMiniMapCurr);
            $('div.diff-body[data-outdated!="true"] tr .linenum:first-child').click(_onClickLineNumA);

            _attachCommentBoxToggleEvent();
        }

        /**
         * Render diff and comments
         */
        function _render() {

            if(!htVar.bCommentable) {
                $(".diff-body .icon-comment").css("display", "none");
            }

            var waComments = htElement.welComments.children('li.comment');
            var aComment = [];

            for(var i = 0; i < waComments.length; i++) {
                var welComment = $(waComments[i]);
                var linenum = welComment.data('line');
                var side = welComment.data('side');
                var path = welComment.data('path');
                var sSelector;
                var welCommentList;

                if (welComment.data('outdated') == false && welComment.data('path')) {
                    sSelector = 'table[data-path="' + welComment.data('path') + '"] tr[data-line="' + welComment.data('line') + '"][data-type="' + welComment.data('side') + '"]'
                    if (aComment[sSelector] == undefined) {
                        aComment[sSelector] = [];
                    }
                    aComment[sSelector].push(welComment);
                }
            }

            for (var sSelector in aComment) {
                welCommentList = $('<ul>').addClass("comments");
                for (var j = 0; j < aComment[sSelector].length; j++) {
                    welCommentList.append(aComment[sSelector][j]);
                }
            }

            htElement.welComments.show(); // Show the remain comments

            // Diff 중에서 특정 파일을 #path 로 지정한 경우
            // Diff render 완료 후 해당 파일 위치로 스크롤 이동
            if(document.location.hash){
                var sTargetId = document.location.hash.substr(1).replace(htVar.rxSlashes, "-");
                var welTarget = $(document.getElementById(sTargetId));

                if(welTarget.length > 0){
                    window.scrollTo(0, welTarget.offset().top);
                }
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
        function _initToggleCommentsButton() {
            $('#toggle-comments').click(function() {
                $('.diff-container').toggleClass('show-comments');
                $("#minimap").toggle();
            });
        }

        /**
         * diff에서 얻은 변경된 라인들을 welTable에 새 row들로 추가한다.
         *
         * 만약 변경된 라인들이 정확하게 삭제된 라인 1줄, 추가된 라인
         * 1줄이라면 단어 단위 하이라이팅을 적용한다.
         *
         * @param {Object} welTable
         * @param {Object} htDiff
         */
        function _flushChangedLines(welTable, htDiff) {
            if (htDiff.aRemoved.length == 1 && htDiff.aAdded.length == 1) {
                _appendChangedLinesWithWordHighlight(welTable, htDiff);
            } else {
                _appendChangedLinesWithoutWordHighlight(welTable, htDiff);
            }

            htDiff.aRemoved = [];
            htDiff.aAdded = [];
        }

        function _attachCommentBoxToggleEvent() {
            if (htVar.bCommentable) {
                yobi.CodeCommentBox.init({
                    fCallbackAfterShowCommentBox: _updateMiniMap,
                    fCallbackAfterHideCommentbox: _updateMiniMap,
                    welDiff: htOptions.htDiff
                });
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
        function _onClickLineNumA(weEvt) {
            var commentForm =
                $(weEvt.target).closest('tr').next().find('#comment-form');

            if (commentForm.length > 0) {
                yobi.CodeCommentBox.hide();
            } else {
                yobi.CodeCommentBox.show($(weEvt.target).closest("tr"));
            }
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
