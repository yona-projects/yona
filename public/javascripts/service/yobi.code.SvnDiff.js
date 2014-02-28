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
            _initMergely();
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
            htVar.sTplFileURL = htOptions.sTplFileURL;
            htVar.sTplRawURL = htOptions.sTplRawURL;
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

            htElement.welDiff = $('#commit');
            htElement.welEmptyCommentForm = $('#comment-form')
                .append(welHidden.clone().attr('name', 'path'))
                .append(welHidden.clone().attr('name', 'line'))
                .append(welHidden.clone().attr('name', 'side'));
            htElement.welComments = $('ul.comments');

            if (htVar.bCommentable) {
                htElement.welIcon = $('#comment-icon-template').tmpl();
            }
            htElement.welEmptyLineNumColumn = $('#linenum-column-template').tmpl();
            htElement.welEmptyCommentButton = $('#comment-button-template').tmpl();

            // 지켜보기
            htElement.welBtnWatch = $('#watch-button');

            // 미니맵
            htElement.welMiniMap = $("#minimap"); // .minimap-outer
            htElement.welMiniMapWrap = htElement.welMiniMap.find(".minimap-wrap");
            htElement.welMiniMapCurr = htElement.welMiniMapWrap.find(".minimap-curr");
            htElement.welMiniMapLinks = htElement.welMiniMapWrap.find(".minimap-links");

            // FullDiff (Mergely)
            htElement.welMergelyWrap = $("#compare");
            htElement.welMergely = $("#mergely");
            htElement.welMergelyPathTitle = htElement.welMergelyWrap.find(".path > span");
            htElement.welMergelyCommitFrom = htElement.welMergelyWrap.find(".compare-from");
            htElement.welMergelyCommitTo = htElement.welMergelyWrap.find(".compare-to");
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

            $(window).on("resize", _initMiniMap);
            $(window).on("scroll", _updateMiniMapCurr);
            $(window).on("resize", _resizeMergely);

            $('.diff-wrap').on('click','td.linenum',_onClickLineNumA);

            $('.diff-wrap').on('click','[data-toggle="commentBoxToggle"]',_onClickCommentBoxToggleBtn);
            
            $('.diff-wrap').on('click','[data-toggle="mergely"]',_onClickBtnFullDiff);
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

            // Diff 중에서 특정 파일을 #path 로 지정한 경우
            // Diff render 완료 후 해당 파일 위치로 스크롤 이동
            if(document.location.hash){
                var sTargetId = document.location.hash.substr(1).replace(htVar.rxSlashes, "-");
                var welTarget = $(document.getElementById(sTargetId));

                if(welTarget.length > 0){
                    window.scrollTo(0, welTarget.offset().top);
                }
            }

            $('[data-commit-origin="true"]').removeClass("hide");
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
            $('#toggle-comments').on('click',function() {
                $('.diff-container').toggleClass('show-comments');
                $("#minimap").toggle();
            });
        }

        /**
         * 현재 줄에 댓글 스레드와 댓글 상자 토글 버튼을 덧붙인다.
         *
         * @param {Object} welTr
         */
        function _appendCommentThreadOnLine(welTr,sPath) {
            var welUl = _createCommentThreadOnLine(welTr,sPath);

            if (welUl.children().length > 0) {
                return _appendCommentToggle(welTr, welUl);
            }
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

            if (htVar.bCommentable) {
                var welCommentBoxToggleButton = htElement.welEmptyCommentButton.clone()
                    .text(Messages("code.openCommentBox"))
                    .attr('data-toggle','commentBoxToggle')
                    .attr('data-type','open');
                
                welUl.append(welCommentBoxToggleButton);
            }

            return $('<tr/>',{class:'comments board-comment-wrap'})                   
                    .append($('<td colspan="3">').append(welUl));
        }

        function _onClickCommentBoxToggleBtn(weEvt) {
            var welCommentTr = $(this).closest('tr');
            var welCodeTr = welCommentTr.prev('tr');
            var welPath = welCodeTr.closest('table');
            var sType = $(this).data('type');

            if(sType=='open') {
                _showCommentBox(welCommentTr,welPath.data('filePath'),welCodeTr.data('line'),welCodeTr.data('type'));
                $(this).data('type','close').text(Messages("code.closeCommentBox"));
            } else {
                _hideCommentBox();
                $(this).data('type','open').text(Messages("code.openCommentBox"));
            }

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
            _updateMiniMap();
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
                var welRow = $(this).closest('tr');
                var welPath = welRow.closest('table');
                if(welRow.data('type')=='add' || welRow.data('type')=='context' || welRow.data('type')=='remove') {
                    _showCommentBox(welRow, welPath.data('filePath'), welRow.data('line'),welRow.data('type'));
                }
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
        function _createCommentThreadOnLine(welTr,sPath) {
            var waComments = htElement.welComments.children('li.comment');
            var welUl = $('<ul>',{class:'comments'});
            
            var nLinenum = welTr.data('line');
            var sSide = (welTr.data('type') == 'remove') ? 'A' : 'B';
            
            for(var i = 0; i < waComments.length; i++) {
               var welComment = $(waComments[i]);
               
               if (sPath == welComment.data('path')
                       && nLinenum == welComment.data('line')
                       && sSide == welComment.data('side')) {
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
        function _showCommentBox(welTr,sPath,nLine,sType) {
            var welTd = $("<td/>",{class:'diff-comment-box',colspan:3});
            var welCommentTr;
            var sSide = (sType == 'remove') ? 'A' : 'B';

            if (htElement.welCommentTr) {
                htElement.welCommentTr.remove();
            }

            htElement.welCommentTr = $("<tr>")
                .append(welTd.append(htElement.welEmptyCommentForm));

            welCommentTr = htElement.welCommentTr;
            welCommentTr.find('[name="path"]').val(sPath);
            welCommentTr.find('[name="line"]').val(nLine);
            welCommentTr.find('[name="side"]').val(sSide);

            welTr.after(htElement.welCommentTr);
            _updateMiniMap();
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
            var rxDiff = /^Index: [\S]+\n[=]+\n/igm;
            var aMatchDiff = sDiff.match(rxDiff);
            var aDiffPath = sDiff.split(rxDiff).slice(1);
            var rxHunkHeader = /@@\s+-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s+@@/;
            var rxFileHeader = /^(---|\+\+\+) (.+)\t[^\t]+$/; // http://en.wikipedia.org/wiki/Diff#Unified_format
            var sPath;
            
            aDiffPath.forEach(function(sDiffRow,nIndex){
                var welDiffWrapOuter = $('<div/>',{class:'diff-partial-outer'});
                var welDiffWrapInner = $('<div/>',{class:'diff-partial-inner'});
                var welDiffMeta = $('<div/>',{class:'diff-partial-meta'});
                var welDiffMetaCommit = $('<div/>',{class:'diff-partial-commit'});
                var welDiffMetaFile = $('<div/>',{class:'diff-partial-file'});
                var welDiffMetaUtility = $('<div/>',{class:'diff-partial-utility'});
                var welDiffCodeWrap = $('<div/>',{class:'diff-partial-code'});
                var welDiffCodeTable = $('<table/>',{class:'diff-container show-comments'});
                var welDiffCodeTableBody = $('<tbody/>');
                var welFullDiff = $('<button/>',{class:'ybtn ybtn-small',type:'button'})
                                    .attr('data-toggle','mergely')
                                    .text(Messages("code.fullDiff"));

                var aLine = sDiffRow.split('\n').slice(0,-1);
                var sPath;
                var nLineA=1;
                var nLineB=1;
                var nLastLineA=1;
                var nLastLineB=1;
                var nCodeLineA;
                var nCodeLineB;
               
                if(aLine[0].indexOf('file marked as a binary type') !==-1) {
                    var sDiffIndex = aMatchDiff[nIndex].split('\n')[0];
                    var welLineA = $('<td/>',{class:'linenum'}).append($('<div/>',{class:'line-number'}));
                    var welLineB = welLineA.clone();

                    sPath = sDiffIndex.substr(7);
                    
                    welDiffMetaCommit.append($('<div/>',{class:'diff-partial-commit-id'}).html("&nbsp;"));
                    welDiffMetaCommit.append(_makeCommitLink(sPath,htVar.sCommitId));
                    welDiffMetaFile.append($('<span/>',{class:'filename'}).text(sPath));
                    welDiffCodeTableBody.append(_makeCodeLine(null,null,'binary',Messages('code.isBinary')));

                } else {
                    aLine.forEach(function(sLine){
                        switch(sLine.substr(0,2)) {
                            case '--':
                            case '++':
                                var aMatch = sLine.match(rxFileHeader);   
                                
                                if(aMatch === null) {
                                    if (sLine.indexOf("---") === 0 || sLine.indexOf("+++") === 0) {
                                        aMatch = ['', sLine.substring(0, 3), sLine.substr(4)];
                                    } else {
                                        return ;
                                    }
                                } 

                                if(aMatch[1]==='---') {
                                    sPath = aMatch[2];
                                    welDiffCodeTable.attr('data-path-a',sPath);
                                    welFullDiff.attr('data-path-a',sPath);
                                    welFullDiff.attr('data-commit-a',htVar.sParentCommitId);

                                    var welCommit = _makeCommitLink(sPath,htVar.sParentCommitId);
                                    welDiffMetaCommit.append(welCommit);

                                } else if(aMatch[1]==='+++') {
                                    sPath = aMatch[2] == "/dev/null" ? sPath : aMatch[2];
                                    welDiffCodeTable.attr('data-path-b',sPath);
                                    welDiffCodeTable.attr('data-file-path',sPath);

                                    welFullDiff.attr('data-path-b',sPath);
                                    welFullDiff.attr('data-path',sPath);
                                    welFullDiff.attr('data-commit-b',htVar.sCommitId);

                                    var welCommit = _makeCommitLink(sPath,htVar.sCommitId);
                                    welDiffMetaCommit.append(welCommit);
                                    welDiffMetaFile.append($('<span>',{class:'filename'}).text(sPath));
                                }

                                break;
                            case '@@' : 
                                var aMatch = sLine.match(rxHunkHeader);
                                var aHunkRange = aMatch ? jQuery.map(aMatch, function(sVal) {
                                    return parseInt(sVal, 10);
                                }) : null;

                                if (aHunkRange == null || aHunkRange.length < 4) {
                                    if (console instanceof Object) {
                                        console.warn("Failed to parse hunk header");
                                    }
                                } else {
                                    welDiffCodeTableBody.append(_makeCodeLine('...','...','range',sLine));
                                }    
                                
                                nLineA = aHunkRange[1];
                                if (isNaN(aHunkRange[2])) {
                                    nLastLineA = nLineA + 1;
                                } else {
                                    nLastLineA = nLineA + aHunkRange[2];
                                }
                                nLineB = aHunkRange[3];
                                if (isNaN(aHunkRange[4])) {
                                    nLastLineB = nLineB + 1;
                                } else {
                                    nLastLineB = nLineB + aHunkRange[4];
                                }                       
                                break;
                            default:
                                var sLineType = (sLine[0]=='+') 
                                                ? 'add' : (sLine[0]=='-') 
                                                ? 'remove' : 'context';

                                if(sLineType=='add') {
                                    nCodeLineB= nLineB++;
                                    nCodeLineA=null;
                                } else if(sLineType=='remove') {
                                    nCodeLineB=null;
                                    nCodeLineA = nLineA++;
                                } else {
                                    nCodeLineA=nLineA++;
                                    nCodeLineB=nLineB++;
                                }   
                                var welCodeRow = _makeCodeLine(nCodeLineA,nCodeLineB,sLineType,sLine);            
                                welDiffCodeTableBody.append(welCodeRow);
                                
                                var welCodeReview = _appendCommentThreadOnLine(welCodeRow,sPath);
                                
                                if(typeof welCodeReview != 'undefined') {
                                    welDiffCodeTableBody.append(welCodeReview);
                                }
                                break;
                        }
                    });
                    
                    welDiffMetaUtility.append(welFullDiff);
                }
               
                welDiffMeta.append(welDiffMetaCommit);
                welDiffMeta.append(welDiffMetaFile);
                welDiffMeta.append(welDiffMetaUtility);
                welDiffCodeTable.append(welDiffCodeTableBody);
                welDiffCodeWrap.append(welDiffCodeTable);
                welDiffWrapInner.append(welDiffMeta);
                welDiffWrapInner.append(welDiffCodeWrap);
                welDiffWrapOuter.append(welDiffWrapInner);
                $('.diff-body').append(welDiffWrapOuter);
            });
        }

        function _makeCommitLink(sPath,sCommitId) {
            var sURL = $yobi.tmpl(htVar.sTplFileURL, {"commitId":sCommitId, "path":sPath});
            var welCommit = $('<div/>',{class:'diff-partial-commit-id'});
            var welCommitLink = $('<a/>',{href:sURL,target:'_blink'}).text(sCommitId);
            welCommit.append(welCommitLink);
            
            return welCommit;
        }

        function _makeCodeLine(nLineA,nLineB,sRowType,sCode) {
            var welRow = $('<tr/>',{class:sRowType});
            var welCellLineA = $('<td/>',{class:'linenum'});
            var welCellLineB = $('<td/>',{class:'linenum'});
            var welCellCode = $('<td/>');

            if(sRowType=='range') {
                welCellCode.addClass('hunk');
                welCellCode.text(sCode);
            } else if(sRowType=='binary') {
                welCellCode.addClass('binary');
                welCellCode.text(sCode);
            } else {
                var welCode = $('<pre/>',{class:'diff-partial-codeline'}).text(sCode);
                var nLine = (nLineB==null) ? nLineA : nLineB;
                welCellCode.addClass('code');
                welRow.attr('data-line',nLine).attr('data-type',sRowType);    
                welCellLineA.append($('<i/>',{class:'icon-comment'}));
                welCellCode.append(welCode);
            }

            welCellLineA.append($('<div/>',{class:'line-number'}).attr('data-line-num',nLineA));
            welCellLineA.append($('<span/>',{class:'hidden'}).text(nLineA));
            welCellLineB.append($('<div/>',{class:'line-number'}).attr('data-line-num',nLineB));
            welCellLineB.append($('<span/>',{class:'hidden'}).text(nLineB));

            welRow.append(welCellLineA);
            welRow.append(welCellLineB);
            welRow.append(welCellCode);
            return welRow;
        }

        /**
         * Mergely 초기화
         */
        function _initMergely(){
            var htWrapSize = _getMergelyWrapSize();

            htElement.welMergely.mergely({
                "width" : "auto",
                // "height": "auto",
                "height": (htWrapSize.nWrapHeight - 100) + "px",
                "editor_width": ((htWrapSize.nWrapWidth - 92) / 2) + "px",
                "editor_height": (htWrapSize.nWrapHeight - 100) + "px",
                "cmsettings":{"readOnly": true, "lineNumbers": true}
            });
        }

        /**
         * Mergely wrapper 크기 반환
         */
        function _getMergelyWrapSize(){
            return {
                "nWrapWidth" : window.innerWidth - 100,
                "nWrapHeight": window.innerHeight - (window.innerHeight * 0.2)
            };
        }

        /**
         * fullDiff 버튼 클릭시 이벤트 핸들러
         *
         * @param {Wrapped Event} weEvt
         */
        function _onClickBtnFullDiff(weEvt){
            var welTarget = $(weEvt.target);
            var sToId   = welTarget.data("commitA");
            var sFromId = welTarget.data("commitB");
            var sPath   = welTarget.data("path");
            sPath = sPath.indexOf("/") === 0 ? sPath.substr(1) : sPath;
            var sRawURLFrom = $yobi.tmpl(htVar.sTplRawURL, {"commitId": sToId, "path": sPath});
            var sRawURLTo = $yobi.tmpl(htVar.sTplRawURL, {"commitId": sFromId, "path": sPath});

            // UpdateText
            htElement.welMergelyPathTitle.text(sPath);
            htElement.welMergelyCommitFrom.text(sFromId);
            htElement.welMergelyCommitTo.text(sToId);
            htElement.welMergelyWrap.modal();

            _resizeMergely();
            _updateMergely(sRawURLFrom, sRawURLTo);
        }

        /**
         * 두 코드를 가져다 fullDiff 에 표시하는 함수
         *
         * @param {String} sRawURLFrom
         * @param {String} sRawURLTo
         */
        function _updateMergely(sRawURLFrom, sRawURLTo){
            // lhs = from
            $.get(sRawURLFrom).done(function(sData){
                htElement.welMergely.mergely("lhs", sData);
                htElement.welMergely.mergely("resize");
                htElement.welMergely.mergely("update");
            });

            // rhs = to
            $.get(sRawURLTo).done(function(sData){
                htElement.welMergely.mergely("rhs", sData);
                htElement.welMergely.mergely("resize");
                htElement.welMergely.mergely("update");
            });
        }

        /**
         * Mergely 영역 크기 조절
         */
        function _resizeMergely(){
            var htWrapSize = _getMergelyWrapSize();
            var nWidth = ((htWrapSize.nWrapWidth - 92) / 2);
            var nHeight = (htWrapSize.nWrapHeight - 100);

            htElement.welMergelyWrap.css({
                "width" : htWrapSize.nWrapWidth + "px",
                "height": htWrapSize.nWrapHeight + "px",
                "margin-left": -(htWrapSize.nWrapWidth / 2) + "px"
            });
            htElement.welMergely.mergely("cm", "rhs").setSize(nWidth + "px", nHeight + "px");
            htElement.welMergely.mergely("cm", "lhs").setSize(nWidth + "px", nHeight + "px");

            $(".mergely-column").width(nWidth).height(nHeight);
            $(".CodeMirror").height(nHeight);
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
})("yobi.code.SvnDiff");
