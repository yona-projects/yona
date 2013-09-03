/**
 * @(#)yobi.code.History.js 2013.04.04
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
        }

        /**
         * initialize variables except element
         */
        function _initVar(htOptions) {
            htVar.sAttachmentAction = htOptions.sAttachmentAction;
            htVar.bCommentable = htOptions.bCommentable;
            htVar.sWatchUrl = htOptions.sWatchUrl;
            htVar.sUnwatchUrl = htOptions.sUnwatchUrl;
        }

        /**
         * initialize element
         */
        function _initElement(htOptions){
            var welHidden = $('<input>').attr('type', 'hidden');

            htElement.welDiff = $('#commit');
            htElement.welEmptyCommentForm = $('#comment-form')
                .append(welHidden.clone().attr('name', 'path'))
                .append(welHidden.clone().attr('name', 'line'))
                .append(welHidden.clone().attr('name', 'side'));
            htElement.welComments = $('ul.comments');

            if (htVar.bCommentable) {
                htElement.welIcon = $('#comment-icon-template').tmpl();
            }

            htElement.welEmptyLineNumColumn =
                $('#linenum-column-template').tmpl();
            htElement.welEmptyCommentButton =
                $('#comment-button-template').tmpl();

            htElement.welBtnWatch = $('#watch-button');
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            htElement.welBtnWatch.click(function(weEvt) {
                var welTarget = $(weEvt.target);
                var bWatched = welTarget.hasClass("active");

                $yobi.sendForm({
                    "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                    "fOnLoad": function(){
                        welTarget.toggleClass("active");
                    }
                });
            });
        }

        /**
         * Render diff and comments
         */
        function _render() {
            var sDiff = htElement.welDiff.text();

            htElement.welDiff.text("");
            htElement.welDiff.append(_renderDiff(sDiff));
            htElement.welDiff.show();
            htElement.welComments.show(); // Show the remain comments
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            var oUploader = yobi.Files.getUploader($("#upload"), $("#comment-editor"));
            var sUploaderId = oUploader.attr("data-namespace");
            
            (new yobi.Attachments({
                "elContainer"  : $("#upload"),
                "elTextarea"   : $("#comment-editor"),
                "sTplFileItem" : $('#tplAttachedFile').text(),
                "sUploaderId"  : sUploaderId
            }));
        }

        /**
         * initialize fileDownloader
         */
        function _initFileDownloader(){
            $(".attachments").each(function(i, elContainer){
                (new yobi.Attachments({"elContainer": elContainer}));
            });
        }

        /**
         * initialize toggle comments button
         */
        function _initToggleCommentsButton() {
            $('#toggle-comments').click(function() {
                $('#commit').toggleClass('show-comments');
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

        /**
         * welTable에 새 row를 추가한다.
         *
         * @param {Object} welTable
         * @param {String} sClass
         * @param {Number} nLineA
         * @param {Number} nLineB
         * @param {Object|String} vContent
         */
        function _appendLine(
                welTable, sClass, sPath, nLineA, nLineB, vContent) {
            var welTr = $('<tr>').addClass(sClass);

            _setPropertiesOnLine(welTr, sPath, nLineA, nLineB);
            _prependLineNumberOnLine(welTr, nLineA, nLineB);

            if ((typeof vContent) == 'string') {
                welTr.append($('<td>').append($("<span>").text(vContent)));
            } else {
                welTr.append(vContent);
            }

            welTable.append(welTr);

            _appendCommentThreadOnLine(welTr); // Append comments
        }

        /**
         * diff에서 얻은 특정 파일의 header를 welTable에 새 row로 추가한다.
         *
         * @param {Object} welTable
         * @param {Object} sHunkHeader
         */
        function _appendFileHeader(welTable, sFileHeader) {
            _appendLine(welTable, "file", sFileHeader, "", "", sFileHeader);
        }

        /**
         * diff에서 얻은 특정 hunk의 header를 welTable에 새 row로 추가한다.
         *
         * @param {Object} welTable
         * @param {Object} sHunkHeader
         */
        function _appendHunkHeader(welTable, sPath, sHunkHeader) {
            _appendLine(welTable, "range", sPath, "...", "...", sHunkHeader);
        }

        /**
         * 현재 줄에 다음의 프로퍼티를 설정한다.
         *
         * - path (파일 경로)
         * - line (줄 번호)
         * - side (left, right, base 중 하나)
         *
         * @param {Object} welTr
         * @param {String} sPath
         * @param {Number} nLineA
         * @param {Number} nLineB
         */
        function _setPropertiesOnLine(welTr, sPath, nLineA, nLineB) {
            welTr.data('line', nLineA || nLineB);
            welTr.data('path', sPath);
            if (nLineA && nLineB) {
                welTr.data('side', 'base');
            } else if (nLineA && !nLineB) {
                welTr.data('side', 'left');
            } else if (!nLineA && nLineB) {
                welTr.data('side', 'right');
            }
        }

        /**
         * welTr에 줄 번호를 붙인다.
         *
         * @param {Object} welTr
         * @param {Number} nLineA
         * @param {Number} nLineB
         */
        function _prependLineNumberOnLine(welTr, nLineA, nLineB) {
            var welLineNumA =
                htElement.welEmptyLineNumColumn.clone().text(nLineA);
            var welLineNumB =
                htElement.welEmptyLineNumColumn.clone().text(nLineB);

            welTr.append(welLineNumA);
            welTr.append(welLineNumB);

            if (htVar.bCommentable
                    && (!isNaN(parseInt(nLineA)) || !isNaN(parseInt(nLineB)))) {
                _prependCommentIcon(welLineNumA, welTr);
                welLineNumA.click(_onClickLineNumA);
            }
        }

        /**
         * 현재 줄에 댓글 스레드와 댓글 상자 토글 버튼을 덧붙인다.
         *
         * @param {Object} welTr
         */
        function _appendCommentThreadOnLine(welTr) {
            var welUl = _createCommentThreadOnLine(welTr);
            if (welUl.children().length > 0) {
                _appendCommentToggle(welTr, welUl);
            }
        }

        /**
         * welPrependTo에, welHoverOn에 마우스 호버시 보여질 댓글 아이콘을
         * 붙인다.
         *
         * @param {Object} welPrependTo
         * @param {Object} welHoverOn
         */
        function _prependCommentIcon(welPrependTo, welHoverOn) {
            var welIcon = htElement.welIcon.clone()
            welIcon.prependTo(welPrependTo);

            welHoverOn.hover(function() {
                welIcon.css('visibility', 'visible');
            }, function() {
                welIcon.css('visibility', 'hidden');
            });

            welPrependTo.hover(function() {
                welIcon.css('opacity', '1.0');
            }, function() {
                welIcon.css('opacity', '0.6');
            });
        }

        /**
         * 댓글의 스레드에 댓글 토글 버튼을 덧붙인다.
         *
         * when: 특정 라인에 대한 댓글 스레드를 렌더링하면서 끝에 댓글 토글
         * 버튼을 붙이려 할 때
         *
         * @param {Object} welTr
         * @param {Object} welUl
         */
        function _appendCommentToggle(welTr, welUl) {
            var welTd = $('<td colspan=3>')
                .data("line", welTr.data("line"))
                .data("side", welTr.data("side"))
                .data("path", welTr.data("path"));

            var welCloseButton = htElement.welEmptyCommentButton.clone()
                .text(Messages("code.closeCommentBox"));
            var welOpenButton = htElement.welEmptyCommentButton.clone()
                .text(Messages("code.openCommentBox"));

            var fOnClickAddButton = function(weEvt) {
                _showCommentBox($(weEvt.target).closest("tr"));
                welCloseButton.show();
                $(weEvt.target).hide();
            }

            var fOnClickCloseButton = function(weEvt) {
                _hideCommentBox();
                welOpenButton.show();
                $(weEvt.target).hide();
            }

            welCloseButton.click(fOnClickCloseButton).hide();
            welOpenButton.click(fOnClickAddButton);

            welUl.append(welOpenButton);
            welUl.append(welCloseButton);

            welTr.after($('<tr>')
                    .addClass('comments')
                    .data("path", welTr.data("path"))
                    .data("line", welTr.data("line"))
                    .data("side", welTr.data("side"))
                    .append($('<td colspan=3>')
                        .append(welUl)));
        }

        /**
         * 댓글 상자를 숨긴다.
         *
         * when: 특정 줄의, (댓글 상자가 나타난 상태에서의) 댓글 아이콘이나,
         * 댓글창 닫기 버튼을 눌렀을 때
         */
        function _hideCommentBox() {
            htElement.welCommentTr.remove();
            htElement.welEmptyCommentForm.find('[name=path]').removeAttr('value');
            htElement.welEmptyCommentForm.find('[name=line]').removeAttr('value');
            htElement.welEmptyCommentForm.find('[name=side]').removeAttr('value');
            htElement.welComments.after(htElement.welEmptyCommentForm);
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
                _hideCommentBox();
            } else {
                _showCommentBox($(weEvt.target).closest("tr"));
            }
        }

        /**
         * diff에서 얻은 변경된 라인들을 welTable에 새 row들로 추가한다.
         *
         * 단어 단위 하이라이팅을 적용한다.
         * 삭제된 라인과 추가된 라인이 정확히 1줄씩인 경우임을 가정한다.
         *
         * @param {Object} welTable
         * @param {Object} htDiff
         */
        function _appendChangedLinesWithWordHighlight(welTable, htDiff) {
            var aDiff = JsDiff.diffWords(
                    htDiff.aRemoved[0].substr(1), htDiff.aAdded[0].substr(1));
            var welRemoved = $("<td>");
            var welAdded = $("<td>");

            welRemoved.append($("<span>").text("-"));
            welAdded.append($("<span>").text("+"));

            for (var i = 0; i < aDiff.length; i++) {
                sValue = aDiff[i].value;
                if (aDiff[i].added) {
                    welAdded.append($("<span>").addClass("add").text(sValue));
                } else if (aDiff[i].removed) {
                    welRemoved.append($("<span>").addClass("remove")
                            .text(sValue));
                } else {
                    welAdded.append($("<span>").text(sValue));
                    welRemoved.append($("<span>").text(sValue));
                }
            }

            _appendLine(welTable, "remove", htDiff.sPath, htDiff.nLineA++, "",
                    welRemoved);
            _appendLine(welTable, "add", htDiff.sPath, "", htDiff.nLineB++,
                    welAdded);
        }

        /**
         * diff에서 얻은 변경된 라인들을 welTable에 새 row들로 추가한다.
         *
         * 단어 단위 하이라이팅을 적용하지 않는다.
         *
         * @param {Object} welTable
         * @param {Object} htDiff
         */
        function _appendChangedLinesWithoutWordHighlight(welTable, htDiff) {
            for (var i = 0; i < htDiff.aRemoved.length; i++) {
                _appendLine(welTable, "remove", htDiff.sPath, htDiff.nLineA++,
                        "", htDiff.aRemoved[i]);
            }

            for (var i = 0; i < htDiff.aAdded.length; i++) {
                _appendLine(welTable, "add", htDiff.sPath, "", htDiff.nLineB++,
                        htDiff.aAdded[i]);
            }
        }

        /**
         * 특정 라인에 대한 댓글 스레드를 생성한다.
         *
         * 이 커밋에 대한 모든 댓글을 순회하면서, 현재 라인에 대한 댓글이
         * 존재한다면 현재 라인의 댓글 스레드에 추가한다.
         *
         * @param {Object} welTr
         * @param {Object} welUl
         */
        function _createCommentThreadOnLine(welTr) {
            var waComments = htElement.welComments.children('li.comment');
            var welUl = $('<ul>').addClass("comments");

            for(var i = 0; i < waComments.length; i++) {
               var welComment = $(waComments[i]);
               var linenum = welComment.data('line');
               var side = welComment.data('side');
               var path = welComment.data('path');

               if (welTr.data('path') == welComment.data('path')
                       && welTr.data('line') == welComment.data('line')
                       && welTr.data('side') == welComment.data('side')) {
                    welUl.append(welComment);
               }
            }

            return welUl;
        }

        /**
         * welTr 밑에 댓글 상자를 보여준다.
         *
         * when: 특정 줄의, (댓글 상자가 안 나타난 상태에서의) 댓글 아이콘이나,
         * 댓글창 열기 버튼을 눌렀을 때
         *
         * @param {Object} welTr
         */
        function _showCommentBox(welTr) {
            var welTd = $("<td colspan=3>");
            var welCommentTr;

            if (isNaN(parseInt(welTr.data('line')))) {
                return;
            }

            if (htElement.welCommentTr) {
                htElement.welCommentTr.remove();
            }

            htElement.welCommentTr = $("<tr>")
                .append(welTd.append(htElement.welEmptyCommentForm.width(htElement.welDiff.width())));

            welCommentTr = htElement.welCommentTr;
            welCommentTr.find('[name=path]').attr('value', welTr.data('path'));
            welCommentTr.find('[name=line]').attr('value', welTr.data('line'));
            welCommentTr.find('[name=side]').attr('value', welTr.data('side'));

            welTr.after(htElement.welCommentTr);
        }

        /**
         * Diff 렌더링
         *
         * unified diff 형식의 텍스트인 sDiff를 HTML로 렌더링하며, 특정 라인에
         * 대한 커멘트가 있는 경우 그것도 함께 하께 렌더링한다.
         *
         * @param {String} sDiff
         * @return {Object} 렌더링한 결과로 만들어진 HTML 테이블
         */
        function _renderDiff(sDiff) {
            var aLine = sDiff.split('\n');
            var welTable = $('<table>');
            var htDiff = {
                aRemoved: [],
                aAdded: [],
                nLineA: 0,
                nLineB: 0,
                sPath: ""
            };
            var rxHunkHeader = /@@\s+-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s+@@/;
            var bAddedOrRemoved;
            var aHunkRange;
            var nLastLineA = 0;
            var nLastLineB = 0;
            var bInHunk = false;

            for (var i = 0; i < aLine.length; i++) {
                bAddedOrRemoved = false;

                if (bInHunk) {
                    switch (aLine[i][0]) {
                    case '+':
                        bAddedOrRemoved = true;
                        htDiff.aAdded.push(aLine[i]);
                        break;
                    case '-':
                        bAddedOrRemoved = true;
                        htDiff.aRemoved.push(aLine[i]);
                        break;
                    case ' ':
                        _flushChangedLines(welTable, htDiff);
                        _appendLine(welTable, "", htDiff.sPath, htDiff.nLineA++,
                                htDiff.nLineB++, aLine[i]);
                        break;
                    default:
                        break;
                    }
                } else {
                    switch (aLine[i].substr(0, 2)) {
                    case '--':
                        htDiff.sPath = aLine[i].substr(5);
                        break;
                    case '++':
                        if (aLine[i].substr(5) != 'dev/null') {
                            htDiff.sPath = aLine[i].substr(5);
                        }
                        _flushChangedLines(welTable, htDiff);
                        _appendFileHeader(welTable, htDiff.sPath);
                        break;
                    case '@@':
                        aMatch = aLine[i].match(rxHunkHeader);
                        aHunkRange = aMatch ? jQuery.map(aMatch, function(sVal) {
                            return parseInt(sVal, 10);
                        }) : null;
                        if (aHunkRange == null || aHunkRange.length < 4) {
                            if (console instanceof Object) {
                                console.warn("Failed to parse hunk header");
                            }
                        } else {
                            htDiff.nLineA = aHunkRange[1];
                            if (isNaN(aHunkRange[2])) {
                                nLastLineA = htDiff.nLineA + 1;
                            } else {
                                nLastLineA = htDiff.nLineA + aHunkRange[2];
                            }
                            htDiff.nLineB = aHunkRange[3];
                            if (isNaN(aHunkRange[4])) {
                                nLastLineB = htDiff.nLineB + 1;
                            } else {
                                nLastLineB = htDiff.nLineB + aHunkRange[4];
                            }
                            _flushChangedLines(welTable, htDiff);
                            _appendHunkHeader(welTable, htDiff.sPath, aLine[i]);
                            bInHunk = true;
                        }
                        break;
                    default:
                        break;
                    }
                }

                if (htDiff.nLineA > nLastLineA || htDiff.nLineB > nLastLineB) {
                    if (console instanceof Object) {
                        console.warn("This hunk has incorrect range.");
                    }
                }

                if (htDiff.nLineA + htDiff.aRemoved.length >= nLastLineA
                        && htDiff.nLineB + htDiff.aAdded.length >= nLastLineB) {
                    bInHunk = false;
                    _flushChangedLines(welTable, htDiff);
                }
            }

            _flushChangedLines(welTable, htDiff);

            return welTable;
        }

        _init(htOptions || {});
    };

})("yobi.code.Diff");
