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

import { register } from "../namespace.js";

const SvnDiff = function(htOptions){

    const htVar = {};
    const htElement = {};

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
        htVar.sTplMiniMapLink = '<a href="#{{id}}" style="top:{{top}}px; height:{{height}}px;"></a>';

        // yona.Attachments
        htVar.sTplFileItem = $('#tplAttachedFile').text();
    }

    /**
     * initialize element
     */
    function _initElement(htOptions){
        htElement.welUploader = $("#upload");
        htElement.welTextarea = $('textarea[data-editor-mode="comment-body"]');

        const welHidden = $('<input>').attr('type', 'hidden');

        htElement.welDiff = $('#commit');
        htElement.welEmptyCommentForm = $('#comment-form')
            .append(welHidden.clone().attr('name', 'path'))
            .append(welHidden.clone().attr('name', 'line'))
            .append(welHidden.clone().attr('name', 'side'));
        htElement.welComments = $('ul.comments');

        if (htVar.bCommentable) {
            htElement.welIcon = $yona.renderElement('#comment-icon-template');
        }
        htElement.welEmptyLineNumColumn = $yona.renderElement('#linenum-column-template');
        htElement.welEmptyCommentButton = $yona.renderElement('#comment-button-template');

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
        htElement.welBtnWatch.click((weEvt) => {
            const welTarget = $(weEvt.target);
            const bWatched = welTarget.hasClass("active");

            $yona.sendForm({
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
        const sDiff = htElement.welDiff.text();

        htElement.welDiff.text("");
        htElement.welDiff.append(_renderDiff(sDiff));
        htElement.welDiff.show();
        htElement.welComments.show(); // Show the remain comments

        if(document.location.hash){
            const sTargetId = document.location.hash.substr(1).replace(htVar.rxSlashes, "-");
            const welTarget = $(document.getElementById(sTargetId));

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
        const oUploader = yona.Files.getUploader(htElement.welUploader, htElement.welTextarea);

        if(oUploader){
            (new yona.Attachments({
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
        $(".attachments").each((i, elContainer) =>{
            if(!$(elContainer).data("isYobiAttachment")){
                (new yona.Attachments({"elContainer": elContainer}));
            }
        });
    }

    /**
     * initialize toggle comments button
     */
    function _initToggleCommentsButton() {
        $('#toggle-comments').on('click',() => {
            $('.diff-container').toggleClass('show-comments');
            $("#minimap").toggle();
        });
    }

    /**
     * @param {Object} welTr
     */
    function _appendCommentThreadOnLine(welTr,sPath) {
        const welUl = _createCommentThreadOnLine(welTr,sPath);

        if (welUl.children().length > 0) {
            return _appendCommentToggle(welTr, welUl);
        }
    }

    /**
     * @param {Object} welTr
     * @param {Object} welUl
     */
    function _appendCommentToggle(welTr, welUl) {
        const welTd = $('<td colspan=3>')
            .data("line", welTr.data("line"))
            .data("side", welTr.data("side"))
            .data("path", welTr.data("path"));

        if (htVar.bCommentable) {
            const welCommentBoxToggleButton = htElement.welEmptyCommentButton.clone()
                .text(Messages("code.openCommentBox"))
                .attr('data-toggle','commentBoxToggle')
                .attr('data-type','open');

            welUl.append(welCommentBoxToggleButton);
        }

        return $('<tr/>',{class:'comments board-comment-wrap'})
                .append($('<td colspan="3">').append(welUl));
    }

    function _onClickCommentBoxToggleBtn(weEvt) {
        const welCommentTr = $(this).closest('tr');
        const welCodeTr = welCommentTr.prev('tr');
        const welPath = welCodeTr.closest('table');
        const sType = $(this).data('type');

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

        const commentForm =
            $(weEvt.target).closest('tr').next().find('#comment-form');

        if (commentForm.length > 0) {
            _hideCommentBox();
        } else {
            const welRow = $(this).closest('tr');
            const welPath = welRow.closest('table');
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
        const waComments = htElement.welComments.children('li.comment');
        const welUl = $('<ul>',{class:'comments'});

        const nLinenum = welTr.data('line');
        const sSide = (welTr.data('type') == 'remove') ? 'A' : 'B';

        for(let i = 0; i < waComments.length; i++) {
           const welComment = $(waComments[i]);

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
        const welTd = $("<td/>",{class:'diff-comment-box',colspan:3});
        let welCommentTr;
        const sSide = (sType == 'remove') ? 'A' : 'B';

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
        const rxDiff = /^Index: [\S]+\n[=]+\n/igm;
        const aMatchDiff = sDiff.match(rxDiff);
        const aDiffPath = sDiff.split(rxDiff).slice(1);
        const rxHunkHeader = /@@\s+-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s+@@/;
        const rxFileHeader = /^(---|\+\+\+) (.+)\t[^\t]+$/; // http://en.wikipedia.org/wiki/Diff#Unified_format
        let sPath;

        aDiffPath.forEach((sDiffRow,nIndex) =>{
            const welDiffWrapOuter = $('<div/>',{class:'diff-partial-outer'});
            const welDiffWrapInner = $('<div/>',{class:'diff-partial-inner'});
            const welDiffMeta = $('<div/>',{class:'diff-partial-meta'});
            const welDiffMetaCommit = $('<div/>',{class:'diff-partial-commit'});
            const welDiffMetaFile = $('<div/>',{class:'diff-partial-file'});
            const welDiffCodeWrap = $('<div/>',{class:'diff-partial-code'});
            const welDiffCodeTable = $('<table/>',{class:'diff-container show-comments'});
            const welDiffCodeTableBody = $('<tbody/>');

            const aLine = sDiffRow.split('\n').slice(0,-1);
            let sPath;
            let nLineA=1;
            let nLineB=1;
            let nLastLineA=1;
            let nLastLineB=1;
            let nCodeLineA;
            let nCodeLineB;
            let bInHunk = false;

            if(aLine[0].indexOf('file marked as a binary type') !==-1) {
                const sDiffIndex = aMatchDiff[nIndex].split('\n')[0];
                const welLineA = $('<td/>',{class:'linenum'}).append($('<div/>',{class:'line-number'}));
                const welLineB = welLineA.clone();

                sPath = sDiffIndex.substr(7);

                welDiffMetaCommit.append($('<div/>',{class:'diff-partial-commit-id'}).html("&nbsp;"));
                welDiffMetaCommit.append(_makeCommitLink(sPath,htVar.sCommitId));
                welDiffMetaFile.append($('<span/>',{class:'filename'}).text(sPath));
                welDiffCodeTableBody.append(_makeCodeLine(null,null,'binary',Messages('code.isBinary')));

            } else {
                aLine.forEach((sLine) =>{
                    // v1.6은 내용이 `--`나 `++`로 시작하는 줄을 파일 머리로 보내 그리지 않았고 줄 번호도 어긋났다(C042).
                    // 첫 헝크가 시작된 뒤의 줄은 모두 내용 줄이다
                    const sLineHead = bInHunk ? '' : sLine.substr(0,2);
                    switch(sLineHead) {
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
                            var aHunkRange = aMatch ? aMatch.map((sVal) => {
                                return parseInt(sVal, 10);
                            }) : null;

                            if (aHunkRange == null || aHunkRange.length < 4) {
                                if (console instanceof Object) {
                                    console.warn("Failed to parse hunk header");
                                }
                                // v1.6은 경고만 하고 `aHunkRange[1]`을 읽어 TypeError가 났다(C043, C113-4)
                                break;
                            }
                            bInHunk = true;
                            welDiffCodeTableBody.append(_makeCodeLine('...','...','range',sLine));

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
        const sURL = htVar.sCodeURL + "/" + sCommitId + "/" + sPath;
        const welCommit = $('<div/>',{class:'diff-partial-commit-id'});
        const welCommitLink = $('<a/>',{href:sURL,target:'_blink'}).text(sCommitId);
        welCommit.append(welCommitLink);

        return welCommit;
    }

    function _makeCodeLine(nLineA,nLineB,sRowType,sCode) {
        const welRow = $('<tr/>',{class:sRowType});
        const welCellLineA = $('<td/>',{class:'linenum'});
        const welCellLineB = $('<td/>',{class:'linenum'});
        const welCellCode = $('<td/>');

        if(sRowType=='range') {
            welCellCode.addClass('hunk');
            welCellCode.text(sCode);
        } else if(sRowType=='binary') {
            welCellCode.addClass('binary');
            welCellCode.text(sCode);
        } else {
            const welCode = $('<pre/>',{class:'diff-partial-codeline'}).text(sCode);
            const nLine = (nLineB==null) ? nLineA : nLineB;
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
        const nDocumentHeight = $(document).height();
        const nMapHeight = htElement.welMiniMapWrap.height();

        htVar.nMiniMapRatio = nMapHeight / nDocumentHeight;
    }

    function _updateMiniMapCurr(){
        htElement.welMiniMapCurr.css("top", Math.ceil($(window).scrollTop() * htVar.nMiniMapRatio) + "px");
    }

    function _resizeMiniMapCurr(){
        htElement.welMiniMapCurr.css("height", Math.ceil(window.innerHeight * htVar.nMiniMapRatio) + "px");
    }

    function _updateMiniMap(){
        const aLinks = [];
        let welTarget, nTop;
        const waTargets = $(htVar.sQueryMiniMap);

        if(waTargets.length > 0){
            waTargets.each((i, el) =>{
                welTarget = $(el);

                aLinks.push($yona.render(htVar.sTplMiniMapLink, {
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

export default SvnDiff;

register("yona.code.SvnDiff", SvnDiff);
