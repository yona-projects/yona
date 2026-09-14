/**
 * Yona, Project Hosting SW
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

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        /**
         * 위임 클릭 바인딩 - 라운드2~4에서 확립한 관례(closest + contains 가드).
         */
        function _delegate(container, sEventType, sSelector, fHandler){
            if(!container){
                return;
            }
            container.addEventListener(sEventType, function(weEvt){
                var matched = weEvt.target.closest(sSelector);
                if(matched && container.contains(matched)){
                    fHandler.call(matched, weEvt);
                }
            });
        }

        /**
         * jQuery `$(tag, {attr: value, ...})` 생성자 근사.
         */
        function _el(sTag, htAttrs){
            var el = document.createElement(sTag);
            if(htAttrs){
                Object.keys(htAttrs).forEach(function(sKey){
                    if(sKey === "class"){
                        el.className = htAttrs[sKey];
                    } else {
                        el.setAttribute(sKey, htAttrs[sKey]);
                    }
                });
            }
            return el;
        }

        /**
         * jQuery `.text(value)` setter는 null/undefined를 빈 문자열로 취급한다
         * (네이티브 `textContent = value`는 문자열 "null"이 돼버려 다르다).
         */
        function _jqText(value){
            return (value === null || typeof value === "undefined") ? "" : String(value);
        }

        /**
         * jQuery `.attr(name, null)`은 속성을 제거한다(네이티브 setAttribute(name, null)은
         * 문자열 "null"을 넣어버려 다르다).
         */
        function _setAttrOrRemove(el, sName, value){
            if(value === null || typeof value === "undefined"){
                el.removeAttribute(sName);
            } else {
                el.setAttribute(sName, value);
            }
        }

        /**
         * jQuery `.offset()`과 동일한 문서 기준 절대좌표.
         */
        function _offset(el){
            var rect = el.getBoundingClientRect();
            return {"top": rect.top + window.scrollY, "left": rect.left + window.scrollX};
        }

        /**
         * jQuery `:visible`(레이아웃 유무로 판단) 근사 - 라운드4에서 확립한 패턴.
         */
        function _isVisible(el){
            return !!(el && (el.offsetWidth || el.offsetHeight || el.getClientRects().length));
        }

        function _createHiddenInput(sName){
            var el = document.createElement("input");
            el.type = "hidden";
            el.name = sName;
            return el;
        }

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

            // yona.Attachments
            // jQuery `$('#tplAttachedFile').text()`는 매치가 없어도 빈 문자열을 반환한다.
            var elTplFileItem = document.getElementById("tplAttachedFile");
            htVar.sTplFileItem = elTplFileItem ? elTplFileItem.textContent : "";
        }

        /**
         * initialize element
         */
        function _initElement(htOptions){
            htElement.welUploader = document.getElementById("upload");
            htElement.welTextarea = document.querySelector('textarea[data-editor-mode="comment-body"]');

            htElement.welDiff = document.getElementById("commit");
            // ReviewViewController.newCommitComment()는 CodeRangeRequest(path/startSide/
            // startLine)로 바인딩한다 - 이 파일이 붙이던 hidden 필드명(line/side)이 서버
            // 계약과 안 맞아 라인별 댓글 작성이 항상 400/무시됐던 것을 #comment-form 마크업
            // 자체가 없어 크래시하는 것을 조사하다 함께 발견했다.
            htElement.welEmptyCommentForm = document.getElementById("comment-form");
            if(htElement.welEmptyCommentForm){
                htElement.welEmptyCommentForm.appendChild(_createHiddenInput("path"));
                htElement.welEmptyCommentForm.appendChild(_createHiddenInput("startLine"));
                htElement.welEmptyCommentForm.appendChild(_createHiddenInput("startSide"));
            }
            htElement.welComments = document.querySelector("ul.comments");

            // #comment-icon-template/#linenum-column-template/#comment-button-template
            // 마크업 자체가 Spring Boot 이식 과정에서 code/svnDiff.html에 누락돼(v1.6
            // code/svnDiff.scala.html:160-167 대응 - 데이터 치환이 전혀 없는 정적 조각이라
            // 별도 템플릿 스크립트 태그 없이 그대로 인라인한다.
            // welIcon/welEmptyLineNumColumn은 원본에서도 생성만 되고 이후 어디서도 쓰이지
            // 않는 완전한 dead 로컬 상태다(전수 조사 완료) - 임의 축소 금지 원칙에 따라
            // 그대로 보존한다.
            if (htVar.bCommentable) {
                htElement.welIcon = _el("i", {"class": "yobicon-comments"});
            }
            htElement.welEmptyLineNumColumn = _el("td", {"class": "linenum"});
            htElement.welEmptyCommentButton = _el("button", {"class": "ybtn medium btn-thread"});

            htElement.welBtnWatch = document.getElementById("watch-button");

            htElement.welMiniMap = document.getElementById("minimap"); // .minimap-outer
            htElement.welMiniMapWrap = htElement.welMiniMap ? htElement.welMiniMap.querySelector(".minimap-wrap") : null;
            htElement.welMiniMapCurr = htElement.welMiniMapWrap ? htElement.welMiniMapWrap.querySelector(".minimap-curr") : null;
            htElement.welMiniMapLinks = htElement.welMiniMapWrap ? htElement.welMiniMapWrap.querySelector(".minimap-links") : null;
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            if(htElement.welBtnWatch){
                htElement.welBtnWatch.addEventListener("click", function(weEvt) {
                    var welTarget = weEvt.target;
                    var bWatched = welTarget.classList.contains("active");

                    $yona.sendForm({
                        "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                        "fOnLoad": function(){
                            welTarget.classList.toggle("active");
                        }
                    });
                });
            }

            window.addEventListener("resize", _initMiniMap);
            window.addEventListener("scroll", _updateMiniMapCurr);

            document.querySelectorAll(".diff-wrap").forEach(function(elDiffWrap){
                _delegate(elDiffWrap, "click", "td.linenum", _onClickLineNumA);
                _delegate(elDiffWrap, "click", '[data-toggle="commentBoxToggle"]', _onClickCommentBoxToggleBtn);
            });
        }

        /**
         * Render diff and comments
         */
        function _render() {
            if(!htElement.welDiff){
                return;
            }

            var sDiff = htElement.welDiff.textContent;

            htElement.welDiff.textContent = "";
            // 원본 `htElement.welDiff.append(_renderDiff(sDiff))`은 _renderDiff가 값을
            // 반환하지 않아(항상 undefined) 사실상 no-op이었다 - 실제 렌더링은 _renderDiff
            // 내부에서 `.diff-body`(= 이 #commit 엘리먼트 자신, class="diff-body")에 직접
            // append하는 부수효과로 일어난다. 네이티브 appendChild(undefined)는 예외를
            // 던지므로 의미 없는 바깥쪽 append 호출은 재현하지 않고, 실제 부수효과만 그대로
            // 유지한다.
            _renderDiff(sDiff);
            htElement.welDiff.style.display = "block";
            if(htElement.welComments){
                htElement.welComments.style.display = "block"; // Show the remain comments
            }

            if(document.location.hash){
                var sTargetId = document.location.hash.substr(1).replace(htVar.rxSlashes, "-");
                var welTargetEl = document.getElementById(sTargetId);

                if(welTargetEl){
                    window.scrollTo(0, _offset(welTargetEl).top);
                }
            }

            document.querySelectorAll('[data-commit-origin="true"]').forEach(function(el){
                el.classList.remove("hide");
            });
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            var oUploader = yona.Files.getUploader(htElement.welUploader, htElement.welTextarea);

            if(oUploader){
                // P3-70 라운드5: yona.Files.getUploader()는 이 라운드 완료 시점까지 반환값을
                // 아직 jQuery로 감싸둔 상태다 - 이미 vanilla인 다른 호출부와 동일하게
                // oUploader[0]로 raw element를 꺼내 네이티브로 읽는다.
                (new yona.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welTextarea,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : oUploader[0].getAttribute("data-namespace")
                }));
            }
        }

        /**
         * initialize fileDownloader
         */
        function _initFileDownloader(){
            document.querySelectorAll(".attachments").forEach(function(elContainer){
                // isYonaAttachment는 다른 파일들(milestone.View.js 등, 이미 vanilla)이 확립한
                // 것과 동일한 공개 계약 - window.jQuery.data() 정적 접근자로 jQuery 내부 데이터
                // 캐시를 직접 읽는다(yona.Attachments.js가 이 키로 기록).
                if(!window.jQuery.data(elContainer, "isYonaAttachment")){
                    (new yona.Attachments({"elContainer": elContainer}));
                }
            });
        }

        /**
         * initialize toggle comments button
         */
        function _initToggleCommentsButton() {
            var elToggle = document.getElementById('toggle-comments');
            if(elToggle){
                elToggle.addEventListener('click', function() {
                    document.querySelectorAll('.diff-container').forEach(function(el){
                        el.classList.toggle('show-comments');
                    });
                    var elMiniMap = document.getElementById("minimap");
                    if(elMiniMap){
                        elMiniMap.style.display = _isVisible(elMiniMap) ? "none" : "block";
                    }
                });
            }
        }

        /**
         * @param {Element} welTr
         */
        function _appendCommentThreadOnLine(welTr, sPath) {
            var welUl = _createCommentThreadOnLine(welTr, sPath);

            if (welUl.children.length > 0) {
                return _appendCommentToggle(welTr, welUl);
            }
        }

        /**
         * @param {Element} welTr
         * @param {Element} welUl
         */
        function _appendCommentToggle(welTr, welUl) {
            // welTd는 원본에서도 생성만 되고 이후 어디에도 append/참조되지 않는 완전한 dead
            // 로컬 변수다(전수 조사 완료) - 임의 축소 금지 원칙에 따라 그대로 보존한다(내부
            // 전용 상태라 jQuery `.data()` 캐시 대신 커스텀 expando로 옮긴다).
            var welTd = document.createElement("td");
            welTd.colSpan = 3;
            welTd.__line = welTr.dataset.line;
            welTd.__side = welTr.dataset.side;
            welTd.__path = welTr.dataset.path;

            if (htVar.bCommentable) {
                var welCommentBoxToggleButton = htElement.welEmptyCommentButton.cloneNode(true);
                welCommentBoxToggleButton.textContent = Messages("code.openCommentBox");
                welCommentBoxToggleButton.setAttribute("data-toggle", "commentBoxToggle");
                welCommentBoxToggleButton.setAttribute("data-type", "open");

                welUl.appendChild(welCommentBoxToggleButton);
            }

            var elTr = document.createElement("tr");
            elTr.className = "comments board-comment-wrap";
            var elTd = document.createElement("td");
            elTd.colSpan = 3;
            elTd.appendChild(welUl);
            elTr.appendChild(elTd);
            return elTr;
        }

        function _onClickCommentBoxToggleBtn(weEvt) {
            var welCommentTr = this.closest("tr");
            var elPrev = welCommentTr ? welCommentTr.previousElementSibling : null;
            var welCodeTr = (elPrev && elPrev.matches("tr")) ? elPrev : null;
            var welPath = welCodeTr ? welCodeTr.closest("table") : null;
            var sType = this.dataset.type;

            if(sType === "open") {
                _showCommentBox(welCommentTr, welPath ? welPath.dataset.filePath : undefined,
                    welCodeTr ? welCodeTr.dataset.line : undefined, welCodeTr ? welCodeTr.dataset.type : undefined);
                this.dataset.type = "close";
                this.textContent = Messages("code.closeCommentBox");
            } else {
                _hideCommentBox();
                this.dataset.type = "open";
                this.textContent = Messages("code.openCommentBox");
            }

        }

        function _hideCommentBox() {
            if(htElement.welCommentTr){
                htElement.welCommentTr.remove();
            }
            if(htElement.welEmptyCommentForm){
                var elPath = htElement.welEmptyCommentForm.querySelector('[name=path]');
                var elStartLine = htElement.welEmptyCommentForm.querySelector('[name=startLine]');
                var elStartSide = htElement.welEmptyCommentForm.querySelector('[name=startSide]');
                if(elPath){ elPath.removeAttribute("value"); }
                if(elStartLine){ elStartLine.removeAttribute("value"); }
                if(elStartSide){ elStartSide.removeAttribute("value"); }
            }
            // #comment-form은 legacy와 동일하게 board-comment-wrap 맨 아래(일반 댓글 작성
            // 위치)로 돌아가며, 그 자리에서 범위 없는 일반 커밋 댓글 폼으로 계속 보인다(숨기지
            // 않는다) - 별도의 write-comment-form이 존재하던 이전 구조에서만 필요했던 hide()
            // 호출이었다(legacy 대조로 단일 폼 겸용 구조로 정리하며 제거).
            if(htElement.welComments && htElement.welEmptyCommentForm){
                htElement.welComments.insertAdjacentElement("afterend", htElement.welEmptyCommentForm);
            }
            _updateMiniMap();
        }

        /**
         * @param {Event} weEvt
         */
        function _onClickLineNumA(weEvt) {
            var elTr = this.closest("tr");
            var elNext = elTr ? elTr.nextElementSibling : null;
            var elCommentForm = elNext ? elNext.querySelector("#comment-form") : null;

            if (elCommentForm) {
                _hideCommentBox();
            } else {
                var welRow = elTr;
                var welPath = welRow ? welRow.closest("table") : null;
                if(welRow && (welRow.dataset.type === 'add' || welRow.dataset.type === 'context' || welRow.dataset.type === 'remove')) {
                    _showCommentBox(welRow, welPath ? welPath.dataset.filePath : undefined, welRow.dataset.line, welRow.dataset.type);
                }
            }
        }

        /**
         * @param {Element} welTr
         * @param {Element} welUl
         */
        function _createCommentThreadOnLine(welTr, sPath) {
            var waComments = htElement.welComments ? htElement.welComments.querySelectorAll(":scope > li.comment") : [];
            var welUl = _el("ul", {"class": "comments"});

            var nLinenum = welTr.dataset.line;
            var sSide = (welTr.dataset.type === "remove") ? "A" : "B";

            waComments.forEach(function(welComment){
                if (sPath == welComment.dataset.path
                        && nLinenum == welComment.dataset.line
                        && sSide == welComment.dataset.side) {
                     welUl.appendChild(welComment);
                }
            });

            return welUl;
        }

        /**
         * @param {Element} welTr
         */
        function _showCommentBox(welTr, sPath, nLine, sType) {
            var sSide = (sType === "remove") ? "A" : "B";

            if (htElement.welCommentTr) {
                htElement.welCommentTr.remove();
            }

            var welTd = _el("td", {"class": "diff-comment-box", "colspan": 3});
            if(htElement.welEmptyCommentForm){
                welTd.appendChild(htElement.welEmptyCommentForm);
            }

            htElement.welCommentTr = _el("tr");
            htElement.welCommentTr.appendChild(welTd);

            if(htElement.welEmptyCommentForm){
                var elPath = htElement.welEmptyCommentForm.querySelector('[name="path"]');
                var elStartLine = htElement.welEmptyCommentForm.querySelector('[name="startLine"]');
                var elStartSide = htElement.welEmptyCommentForm.querySelector('[name="startSide"]');
                if(elPath){ elPath.value = sPath; }
                if(elStartLine){ elStartLine.value = nLine; }
                if(elStartSide){ elStartSide.value = sSide; }
            }

            welTr.insertAdjacentElement("afterend", htElement.welCommentTr);
            _updateMiniMap();
        }

        /**
         * @param {String} sDiff
         * @return {Object} 렌더링한 결과로 만들어진 HTML 테이블 (부수효과로 .diff-body에 직접 append됨)
         */
        function _renderDiff(sDiff) {
            var rxDiff = /^Index: [\S]+\n[=]+\n/igm;
            var aMatchDiff = sDiff.match(rxDiff);
            var aDiffPath = sDiff.split(rxDiff).slice(1);
            var rxHunkHeader = /@@\s+-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s+@@/;
            var rxFileHeader = /^(---|\+\+\+) (.+)\t[^\t]+$/; // http://en.wikipedia.org/wiki/Diff#Unified_format
            var sPath;

            aDiffPath.forEach(function(sDiffRow, nIndex){
                var welDiffWrapOuter = _el("div", {"class": "diff-partial-outer"});
                var welDiffWrapInner = _el("div", {"class": "diff-partial-inner"});
                var welDiffMeta = _el("div", {"class": "diff-partial-meta"});
                var welDiffMetaCommit = _el("div", {"class": "diff-partial-commit"});
                var welDiffMetaFile = _el("div", {"class": "diff-partial-file"});
                var welDiffCodeWrap = _el("div", {"class": "diff-partial-code"});
                var welDiffCodeTable = _el("table", {"class": "diff-container show-comments"});
                var welDiffCodeTableBody = _el("tbody");

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
                    var welLineA = _el("td", {"class": "linenum"});
                    welLineA.appendChild(_el("div", {"class": "line-number"}));
                    var welLineB = welLineA.cloneNode(true);

                    sPath = sDiffIndex.substr(7);

                    var welCommitIdPlaceholder = _el("div", {"class": "diff-partial-commit-id"});
                    welCommitIdPlaceholder.innerHTML = "&nbsp;";
                    welDiffMetaCommit.appendChild(welCommitIdPlaceholder);
                    welDiffMetaCommit.appendChild(_makeCommitLink(sPath,htVar.sCommitId));
                    var welFilenameSpanBin = _el("span", {"class": "filename"});
                    welFilenameSpanBin.textContent = sPath;
                    welDiffMetaFile.appendChild(welFilenameSpanBin);
                    welDiffCodeTableBody.appendChild(_makeCodeLine(null,null,'binary',Messages('code.isBinary')));

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
                                    welDiffCodeTable.setAttribute('data-path-a', sPath);

                                    var welCommit = _makeCommitLink(sPath,htVar.sParentCommitId);
                                    welDiffMetaCommit.appendChild(welCommit);

                                } else if(aMatch[1]==='+++') {
                                    sPath = aMatch[2] == "/dev/null" ? sPath : aMatch[2];
                                    welDiffCodeTable.setAttribute('data-path-b', sPath);
                                    welDiffCodeTable.setAttribute('data-file-path', sPath);

                                    var welCommit = _makeCommitLink(sPath,htVar.sCommitId);
                                    welDiffMetaCommit.appendChild(welCommit);
                                    var welFilenameSpan = _el("span", {"class": "filename"});
                                    welFilenameSpan.textContent = sPath;
                                    welDiffMetaFile.appendChild(welFilenameSpan);
                                }

                                break;
                            case '@@' :
                                var aMatch = sLine.match(rxHunkHeader);
                                var aHunkRange = aMatch ? aMatch.map(function(sVal) {
                                    return parseInt(sVal, 10);
                                }) : null;

                                if (aHunkRange == null || aHunkRange.length < 4) {
                                    if (console instanceof Object) {
                                        console.warn("Failed to parse hunk header");
                                    }
                                } else {
                                    welDiffCodeTableBody.appendChild(_makeCodeLine('...','...','range',sLine));
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
                                welDiffCodeTableBody.appendChild(welCodeRow);

                                var welCodeReview = _appendCommentThreadOnLine(welCodeRow,sPath);

                                if(typeof welCodeReview != 'undefined') {
                                    welDiffCodeTableBody.appendChild(welCodeReview);
                                }
                                break;
                        }
                    });
                }

                welDiffMeta.appendChild(welDiffMetaCommit);
                welDiffMeta.appendChild(welDiffMetaFile);
                welDiffCodeTable.appendChild(welDiffCodeTableBody);
                welDiffCodeWrap.appendChild(welDiffCodeTable);
                welDiffWrapInner.appendChild(welDiffMeta);
                welDiffWrapInner.appendChild(welDiffCodeWrap);
                welDiffWrapOuter.appendChild(welDiffWrapInner);
                var elDiffBody = document.querySelector('.diff-body');
                if(elDiffBody){
                    elDiffBody.appendChild(welDiffWrapOuter);
                }
            });
        }

        function _makeCommitLink(sPath,sCommitId) {
            var sURL = htVar.sCodeURL + "/" + sCommitId + "/" + sPath;
            var welCommit = _el("div", {"class": "diff-partial-commit-id"});
            var welCommitLink = _el("a", {"href": sURL, "target": "_blink"});
            welCommitLink.textContent = sCommitId;
            welCommit.appendChild(welCommitLink);

            return welCommit;
        }

        function _makeCodeLine(nLineA,nLineB,sRowType,sCode) {
            var welRow = _el("tr", {"class": sRowType});
            var welCellLineA = _el("td", {"class": "linenum"});
            var welCellLineB = _el("td", {"class": "linenum"});
            var welCellCode = _el("td");

            if(sRowType=='range') {
                welCellCode.classList.add("hunk");
                welCellCode.textContent = sCode;
            } else if(sRowType=='binary') {
                welCellCode.classList.add("binary");
                welCellCode.textContent = sCode;
            } else {
                var welCode = _el("pre", {"class": "diff-partial-codeline"});
                welCode.textContent = sCode;
                var nLine = (nLineB==null) ? nLineA : nLineB;
                welCellCode.classList.add("code");
                welRow.setAttribute('data-line', nLine);
                welRow.setAttribute('data-type', sRowType);
                welCellLineA.appendChild(_el("i", {"class": "yobicon-comments"}));
                welCellCode.appendChild(welCode);
            }

            var welLineNumDivA = _el("div", {"class": "line-number"});
            _setAttrOrRemove(welLineNumDivA, "data-line-num", nLineA);
            welCellLineA.appendChild(welLineNumDivA);
            var welHiddenSpanA = _el("span", {"class": "hidden"});
            welHiddenSpanA.textContent = _jqText(nLineA);
            welCellLineA.appendChild(welHiddenSpanA);

            var welLineNumDivB = _el("div", {"class": "line-number"});
            _setAttrOrRemove(welLineNumDivB, "data-line-num", nLineB);
            welCellLineB.appendChild(welLineNumDivB);
            var welHiddenSpanB = _el("span", {"class": "hidden"});
            welHiddenSpanB.textContent = _jqText(nLineB);
            welCellLineB.appendChild(welHiddenSpanB);

            welRow.appendChild(welCellLineA);
            welRow.appendChild(welCellLineB);
            welRow.appendChild(welCellCode);
            return welRow;
        }

        function _initMiniMap(){
            _setMiniMapRatio();
            _updateMiniMap();
            _resizeMiniMapCurr();
        }

        function _setMiniMapRatio(){
            var nDocumentHeight = document.documentElement.scrollHeight;
            var nMapHeight = htElement.welMiniMapWrap ? htElement.welMiniMapWrap.offsetHeight : 0;

            htVar.nMiniMapRatio = nMapHeight / nDocumentHeight;
        }

        function _updateMiniMapCurr(){
            if(htElement.welMiniMapCurr){
                htElement.welMiniMapCurr.style.top = Math.ceil(document.body.scrollTop * htVar.nMiniMapRatio) + "px";
            }
        }

        function _resizeMiniMapCurr(){
            if(htElement.welMiniMapCurr){
                htElement.welMiniMapCurr.style.height = Math.ceil(window.innerHeight * htVar.nMiniMapRatio) + "px";
            }
        }

        function _updateMiniMap(){
            var aLinks = [];
            var waTargets = document.querySelectorAll(htVar.sQueryMiniMap);

            if(waTargets.length > 0){
                waTargets.forEach(function(el){
                    aLinks.push($yona.tmpl(htVar.sTplMiniMapLink, {
                        "id"    : el.getAttribute("id"),
                        "top"   : Math.ceil(_offset(el).top * htVar.nMiniMapRatio),
                        "height": Math.ceil(el.offsetHeight * htVar.nMiniMapRatio)
                    }));
                });
                if(htElement.welMiniMapLinks){
                    htElement.welMiniMapLinks.innerHTML = aLinks.join("");
                }
                if(htElement.welMiniMap){
                    htElement.welMiniMap.style.display = "block";
                }
            } else if(htElement.welMiniMap){
                htElement.welMiniMap.style.display = "none";
            }
        }

        _init(htOptions || {});
    };
})("yona.code.SvnDiff");
