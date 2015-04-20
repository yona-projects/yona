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
            htVar.sCodeURL = htOptions.sCodeURL;
            htVar.sTplFileURL = htOptions.sTplFileURL;
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
            htElement.welTextarea = $('textarea[data-editor-mode="comment-body"]');

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

            htElement.welBtnWatch = $('#watch-button');

            htElement.welMiniMap = $("#minimap"); // .minimap-outer
            htElement.welMiniMapWrap = htElement.welMiniMap.find(".minimap-wrap");
            htElement.welMiniMapCurr = htElement.welMiniMapWrap.find(".minimap-curr");
            htElement.welMiniMapLinks = htElement.welMiniMapWrap.find(".minimap-links");
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

            $('.diff-wrap').on('click','td.linenum',_onClickLineNumA);

            $('.diff-wrap').on('click','[data-toggle="commentBoxToggle"]',_onClickCommentBoxToggleBtn);
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
        function _initToggleCommentsButton() {
            $('#toggle-comments').on('click',function() {
                $('.diff-container').toggleClass('show-comments');
                $("#minimap").toggle();
            });
        }

        /**
         * @param {Object} welTr
         */
        function _appendCommentThreadOnLine(welTr,sPath) {
            var welUl = _createCommentThreadOnLine(welTr,sPath);

            if (welUl.children().length > 0) {
                return _appendCommentToggle(welTr, welUl);
            }
        }

        /**
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

        function _hideCommentBox() {
            htElement.welCommentTr.remove();
            htElement.welEmptyCommentForm.find('[name=path]').removeAttr('value');
            htElement.welEmptyCommentForm.find('[name=line]').removeAttr('value');
            htElement.welEmptyCommentForm.find('[name=side]').removeAttr('value');
            htElement.welComments.after(htElement.welEmptyCommentForm);
            _updateMiniMap();
        }

        /**
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
                var welDiffCodeWrap = $('<div/>',{class:'diff-partial-code'});
                var welDiffCodeTable = $('<table/>',{class:'diff-container show-comments'});
                var welDiffCodeTableBody = $('<tbody/>');

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

                                    var welCommit = _makeCommitLink(sPath,htVar.sParentCommitId);
                                    welDiffMetaCommit.append(welCommit);

                                } else if(aMatch[1]==='+++') {
                                    sPath = aMatch[2] == "/dev/null" ? sPath : aMatch[2];
                                    welDiffCodeTable.attr('data-path-b',sPath);
                                    welDiffCodeTable.attr('data-file-path',sPath);

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
                }

                welDiffMeta.append(welDiffMetaCommit);
                welDiffMeta.append(welDiffMetaFile);
                welDiffCodeTable.append(welDiffCodeTableBody);
                welDiffCodeWrap.append(welDiffCodeTable);
                welDiffWrapInner.append(welDiffMeta);
                welDiffWrapInner.append(welDiffCodeWrap);
                welDiffWrapOuter.append(welDiffWrapInner);
                $('.diff-body').append(welDiffWrapOuter);
            });
        }

        function _makeCommitLink(sPath,sCommitId) {
            var sURL = htVar.sCodeURL + "/" + sCommitId + "/" + sPath;
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
                welCellLineA.append($('<i/>',{class:'yobicon-comments'}));
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

        _init(htOptions || {});
    };
})("yobi.code.SvnDiff");
