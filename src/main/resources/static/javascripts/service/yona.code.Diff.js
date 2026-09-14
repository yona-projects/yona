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
         * P3-70 라운드5: 이 파일은 "죽은 코드" 감사에서 어느 템플릿도
         * `$yona.loadModule("code.Diff", ...)`를 실제로 호출하지 않는 것으로 확인됐다
         * (code/diff.html 등이 자체 vanilla 재구현으로 이미 대체). 실행되는 화면이 없어
         * Playwright 실측 검증은 불가능하지만, "완전 동치 보장 하에 vanilla로 전환"
         * 원칙(라운드2/3의 organization.Member.js/ui.Mergely.js와 동일한 판단)에 따라
         * 삭제하지 않고 1:1로 전환한다.
         */

        /**
         * jQuery의 인자 없는 `.parents(sQuery)`(자기 자신 제외, 매칭 조상 전체) 근사 -
         * 이 파일에서는 항상 "가장 가까운 매칭 조상 1개"만 실제로 쓰이므로 자신을 제외한
         * closest로 충분하다.
         */
        function _closestAncestor(el, sQuery){
            return (el && el.parentElement) ? el.parentElement.closest(sQuery) : null;
        }

        function _hasMatchingAncestor(el, sQuery){
            return !!_closestAncestor(el, sQuery);
        }

        /**
         * jQuery `.offset()`과 동일한 문서 기준 절대좌표.
         */
        function _offset(el){
            var rect = el.getBoundingClientRect();
            return {"top": rect.top + window.scrollY, "left": rect.left + window.scrollX};
        }

        /**
         * jQuery `.position()`과 동일한 offsetParent 기준 상대좌표.
         */
        function _position(el){
            var rect = el.getBoundingClientRect();
            var elOffsetParent = el.offsetParent || document.documentElement;
            var parentRect = elOffsetParent.getBoundingClientRect();
            var style = window.getComputedStyle(el);
            var parentStyle = window.getComputedStyle(elOffsetParent);
            var nMarginTop = parseFloat(style.marginTop) || 0;
            var nMarginLeft = parseFloat(style.marginLeft) || 0;
            var nBorderTop = parseFloat(parentStyle.borderTopWidth) || 0;
            var nBorderLeft = parseFloat(parentStyle.borderLeftWidth) || 0;
            return {
                "top": rect.top - parentRect.top - nMarginTop + nBorderTop,
                "left": rect.left - parentRect.left - nMarginLeft + nBorderLeft
            };
        }

        /**
         * jQuery `.width()`/`.height()`(padding/border 제외한 content box 크기)와 동일.
         */
        function _contentWidth(el){
            if(!el){ return 0; }
            var style = window.getComputedStyle(el);
            return el.clientWidth - (parseFloat(style.paddingLeft) || 0) - (parseFloat(style.paddingRight) || 0);
        }

        function _contentHeight(el){
            if(!el){ return 0; }
            var style = window.getComputedStyle(el);
            return el.clientHeight - (parseFloat(style.paddingTop) || 0) - (parseFloat(style.paddingBottom) || 0);
        }

        /**
         * jQuery `:visible`(레이아웃 유무로 판단) 근사 - 라운드4에서 확립한 패턴.
         */
        function _isVisible(el){
            return !!(el && (el.offsetWidth || el.offsetHeight || el.getClientRects().length));
        }

        /**
         * jQuery UI `.effect("highlight")`(노란색으로 반짝였다 원래 배경색으로 서서히
         * 복귀)와 동일한 시각 효과의 최소 vanilla 재현.
         */
        function _highlightElement(el){
            if(!el){ return; }
            var sOriginalTransition = el.style.transition;
            var sOriginalBackground = el.style.backgroundColor;
            el.style.transition = "none";
            el.style.backgroundColor = "#ffff99";
            void el.offsetWidth; // 강제 리플로우 - transition:none이 실제 적용된 뒤 되돌려야 애니메이션이 보인다.
            el.style.transition = "background-color 1.6s ease";
            el.style.backgroundColor = sOriginalBackground;
            setTimeout(function(){
                el.style.transition = sOriginalTransition;
            }, 1600);
        }

        /**
         * 위임 클릭/이벤트 바인딩 - 라운드2~4에서 확립한 관례(closest + contains 가드).
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

            // yona.Attachments
            // jQuery `$('#tplAttachedFile').text()`는 매치가 없어도 빈 문자열을 반환한다.
            var elTplFileItem = document.getElementById("tplAttachedFile");
            htVar.sTplFileItem = elTplFileItem ? elTplFileItem.textContent : "";
        }

        /**
         * initialize element
         */
        function _initElement(){
            htElement.welContainer = document.querySelector(".codediff-wrap");

            // 변경내역
            htElement.welDiffWrap = htElement.welContainer ? htElement.welContainer.querySelector("div.diffs-wrap") : null;
            htElement.welDiffBody = htElement.welDiffWrap ? htElement.welDiffWrap.querySelector(".diff-body") : null;
            htElement.waDiffContainers = htElement.welDiffWrap ? htElement.welDiffWrap.querySelectorAll(".diff-container") : [];

            // 리뷰영역
            htElement.welCommentWrap = htElement.welContainer ? htElement.welContainer.querySelector("div.board-comment-wrap") : null;
            htElement.welReviewWrap = htElement.welContainer ? htElement.welContainer.querySelector("div.review-wrap") : null;
            htElement.welReviewContainer = htElement.welReviewWrap ? htElement.welReviewWrap.querySelector("div.review-container") : null;
            htElement.waBtnToggleReviewWrap = htElement.welContainer ? htElement.welContainer.querySelectorAll("button.btn-show-reviewcards,button.btn-hide-reviewcards") : [];
            htElement.welReviewList = htElement.welContainer ? htElement.welContainer.querySelector("div.review-list") : null;
            // 전체 댓글 (Non-Ranged comment thread)
            htElement.welUploader = document.getElementById("upload");
            htElement.welTextarea = document.querySelector('textarea[data-editor-mode="comment-body"]');

            // 지켜보기
            htElement.welBtnWatch = document.getElementById("watch-button");

            // 미니맵
            htElement.welMiniMap = document.getElementById("minimap"); // .minimap-outer
            htElement.welMiniMapWrap = htElement.welMiniMap ? htElement.welMiniMap.querySelector(".minimap-wrap") : null;
            htElement.welMiniMapCurr = htElement.welMiniMapWrap ? htElement.welMiniMapWrap.querySelector(".minimap-curr") : null;
            htElement.welMiniMapLinks = htElement.welMiniMapWrap ? htElement.welMiniMapWrap.querySelector(".minimap-links") : null;

            // 코드받기 - #btnAccept는 [data-request-method]라 Common.js의 전역 auto-init이
            // 페이지 로드시 이미 $yona.requestAs()로 초기화해둔다(P3-70 라운드10).
            htElement.welBtnAccept = document.getElementById("btnAccept");
        }

        /**
         * attach event handler
         */
        function _attachEvent(){
            if(htElement.welBtnWatch){
                htElement.welBtnWatch.addEventListener("click", _onClickBtnWatchToggle);
            }

            window.addEventListener("resize", _initMiniMap);
            window.addEventListener("scroll", _updateMiniMapCurr);

            htElement.waBtnToggleReviewWrap.forEach(function(el){
                el.addEventListener("click", function(){
                    if(htElement.welContainer){
                        htElement.welContainer.classList.toggle("diffs-only");
                    }
                    _setReviewListHeight();
                });
            });

            // 리뷰카드 링크 클릭시
            _delegate(htElement.welReviewWrap, "click", "a.review-card", _onClickReviewCardLink);

            // Diff 영역에서 스크롤시
            // .comment-thread-wrap 의 좌우 위치를 맞춰준다
            document.querySelectorAll(".diff-partial-code").forEach(function(elPartial){
                elPartial.addEventListener("scroll", function(){
                    var sHashCode = elPartial.dataset.hashcode;
                    htVar.htThreadWrap[sHashCode] = htVar.htThreadWrap[sHashCode] || elPartial.querySelector(".comment-thread-wrap");
                    if(htVar.htThreadWrap[sHashCode]){
                        htVar.htThreadWrap[sHashCode].style.marginLeft = elPartial.scrollLeft + "px";
                    }
                });
            });

            window.addEventListener("hashchange", _onHashChange);

            // P3-70 라운드10: el._yonaRequestAs는 Common.js의 전역 auto-init이 페이지 로드시
            // 이미 채워뒀을 것이다(원본 jQuery `.data("requestAs")`와 동일한 존재 확인 가드).
            if(htElement.welBtnAccept && htElement.welBtnAccept._yonaRequestAs){
                htElement.welBtnAccept._yonaRequestAs.on("beforeRequest", function(){
                    htElement.welBtnAccept.setAttribute('disabled', 'disabled');
                    NProgress.start();
                });
            }

            _setReviewWrapAffixed();

            window.addEventListener("resize", _setReviewListHeight);
            window.addEventListener("scroll", _setReviewListHeight);

            var elBranches = document.getElementById("branches");
            if(elBranches){
                elBranches.addEventListener("change", function(weEvt){
                    location.href = weEvt.val;
                });
            }
        }

        /**
         * @param weEvt
         * @returns {boolean}
         * @private
         */
        function _onClickReviewCardLink(weEvt){
            var sThreadId = _getHashFromLinkString(this.getAttribute("href"));

            if(!_isThreadExistOnCurrentPage(sThreadId)){
                return;
            }

            var sPreviousHash = location.hash;
            location.hash = sThreadId;

            if(sPreviousHash === location.hash) {
                window.dispatchEvent(new Event("hashchange"));
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
            return !!document.getElementById(sHash);
        }

        /**
         * @private
         */
        function _onHashChange(){
            if(location.hash) {
                _scrollToAndHighlight(document.querySelector(location.hash));
            }
        }

        /**
         * @param welTarget
         * @private
         */
        function _scrollToAndHighlight(welTarget){
            var welThread = welTarget ? _getThread(welTarget) : null;

            if(!welTarget || !welThread){
                return;
            }

            if(_isFoldedThread(welThread)){
                welThread.classList.remove("fold");
            }

            _showReviewCardsTabByState(welThread.dataset.state);
            window.scrollTo(0, _offset(welTarget).top - 50);
            _highlightElement(welTarget);
        }

        function _getThread(welTarget){
            return welTarget.classList.contains("comment-thread-wrap") ? welTarget : _closestAncestor(welTarget, ".comment-thread-wrap");
        }

        /**
         * Show review-card list tab of {@code state}
         *
         * @param state
         * @private
         */
        function _showReviewCardsTabByState(state){
            if(["open", "closed"].indexOf(state) > -1) {
                var elTab = document.querySelector('a[href="#reviewcards-' + state + '"]');
                if(elTab){
                    $yona.tabShow(elTab);
                }
            }
        }

        /**
         * @param welTarget
         * @returns {boolean}
         * @private
         */
        function _isFoldedThread(welTarget){
            return welTarget.classList.contains("fold") && welTarget.querySelectorAll(".btn-thread-here").length > 0;
        }

        /**
         * @private
         */
        function _scrollToHash(){
            if(location.hash){
                window.dispatchEvent(new Event("hashchange"));
            }
        }

        /**
         * @param weEvt
         * @private
         */
        function _onClickBtnWatchToggle(weEvt){
            var welTarget = weEvt.target;
            var bWatched = welTarget.classList.contains("active");

            $yona.sendForm({
                "sURL": bWatched ? htVar.sUnwatchUrl : htVar.sWatchUrl,
                "fOnLoad": function(){
                    // jQuery `.toggleClass("active ybtn-watching")`는 두 클래스를 각각 독립적으로
                    // (자기 자신의 현재 상태 기준으로) 토글한다 - 네이티브 classList.toggle을
                    // 클래스별로 따로 호출해 동일하게 재현.
                    welTarget.classList.toggle("active");
                    welTarget.classList.toggle("ybtn-watching");
                }
            });
        }

        /**
         * @private
         */
        function _setReviewWrapAffixed(){
            if(!htElement.welReviewContainer){
                return;
            }

            htElement.welReviewContainer.classList.add('sticky-review-container');
        }

        /**
         * initialize fileUploader
         */
        function _initFileUploader(){
            var oUploader = yona.Files.getUploader(htElement.welUploader, htElement.welTextarea);

            if(oUploader){
                // yona.Files.getUploader()는 [elContainer] 형태의 순수 배열을 반환한다
                // (P3-70 라운드10에서 jQuery 래핑 제거) - oUploader[0]로 raw element를 꺼낸다.
                (new yona.Attachments({
                    "elContainer"  : htElement.welUploader,
                    "elTextarea"   : htElement.welTextarea,
                    "sTplFileItem" : htVar.sTplFileItem,
                    "sUploaderId"  : oUploader[0].getAttribute("data-namespace")
                }));
            }

            document.querySelectorAll("form.review-form").forEach(function(form){
                var container = form.querySelector(".upload-wrap");
                var textarea = form.querySelector("textarea");
                var uploader = yona.Files.getUploader(container, textarea);

                if(uploader){
                    (new yona.Attachments({
                        "elTextarea"   : textarea,
                        "elContainer"  : container,
                        "sTplFileItem" : htVar.sTplFileItem,
                        "sUploaderId"  : uploader[0].getAttribute("data-namespace")
                    }));

                }
            });
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
        function _initToggleCommentsButton(){
            var elToggle = document.getElementById('toggle-comments');
            if(elToggle){
                elToggle.addEventListener('click', function(){
                    htElement.waDiffContainers.forEach(function(el){
                        el.classList.toggle('show-comments');
                    });
                    if(htElement.welMiniMap){
                        htElement.welMiniMap.style.display = _isVisible(htElement.welMiniMap) ? "none" : "block";
                    }
                });
            }
        }

        /**
         * @private
         */
        function _initCodeComment(){
            if(htVar.bCommentable){
                _initCodeCommentBox();
                _initCodeCommentBlock();

                // Sizzle의 `[data-outdated!="true"]`(네이티브 CSS엔 없는 확장) 대신 표준
                // `:not([data-outdated="true"])`로 대체 - 속성이 아예 없는 경우까지 포함해
                // 동일하게 매칭한다.
                document.querySelectorAll('div.diff-body:not([data-outdated="true"])').forEach(function(elDiffBody){
                    _delegate(elDiffBody, "click", "tr[data-line] .linenum", _onClickLineNumA);
                });

                _delegate(htElement.welDiffWrap, "click", "button.btn-thread", _onClickBtnReplyOnThread);
            } else {
                if(htElement.welDiffBody){
                    htElement.welDiffBody.querySelectorAll(".linenum > .yobicon-comments").forEach(function(el){
                        el.style.display = "none";
                    });
                }
            }

            _delegate(htElement.welDiffBody, "click", ".btn-thread-minimize", _onClickBtnFoldThread);

            // block/unblock with thread range with mouseenter/leave event
            document.querySelectorAll('div[data-toggle="CodeCommentThread"]').forEach(function(el){
                el.addEventListener("mouseenter", _onMouseOverCodeCommentThread);
                el.addEventListener("mouseleave", _onMouseLeaveCodeCommentThread);
            });
        }

        /**
         * @private
         */
        function _initCodeCommentBox() {
            yona.CodeCommentBox.init({
                "sTplFileItem": htVar.sTplFileItem
            });

            window.addEventListener("CodeCommentBox:aftershow", _updateMiniMap);
            window.addEventListener("CodeCommentBox:afterhide", function(){
                _updateMiniMap();
                yona.CodeCommentBlock.unblock();
                htVar.htBlockInfo = null;
            });
        }

        /**
         * @param weEvt
         * @private
         */
        function _onClickBtnReplyOnThread(weEvt){
            var elButton = this;
            var elActions = elButton.closest(".thread-actrow");

            function _onAfterHide(){
                if(elActions){
                    elActions.style.display = "";
                }
                window.removeEventListener("CodeCommentBox:afterhide", _onAfterHide);
            }
            window.addEventListener("CodeCommentBox:afterhide", _onAfterHide);

            yona.CodeCommentBox.show(elButton);

            if(elActions){
                elActions.style.display = "none";
            }
        }

        /**
         * @private
         */
        function _initCodeCommentBlock(){
            yona.CodeCommentBlock.init({
                "welContainer"       : htElement.welDiffBody,
                "welPopButtonOnBlock": htElement.welDiffBody ? htElement.welDiffBody.querySelector(".btnPop") : null
            });

            _delegate(htElement.welDiffBody, "click", ".btnPop", _onClickBtnAddBlockComment);
            _delegate(htElement.welDiffBody, "mousedown", ":not(.btnPop)", _onMouseDownDiffBody);
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
                yona.CodeCommentBox.hide();
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
            return _hasMatchingAncestor(elTarget, sQuery);
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
            var htBlockInfo = yona.CodeCommentBlock.getData();
            yona.CodeCommentBlock.block(htBlockInfo);

            var sLineNum = htBlockInfo.bIsReversed ? htBlockInfo.nStartLine : htBlockInfo.nEndLine;
            var sLineType = htBlockInfo.bIsReversed ? htBlockInfo.sStartType : htBlockInfo.sEndType;
            var welContainer = document.querySelector('.diff-container[data-file-path="' + htBlockInfo.sFilePath + '"]');
            var welTR = welContainer ? welContainer.querySelector('tr[data-line="' + sLineNum + '"][data-type="' + sLineType + '"]') : null;
            if(welTR){
                // 이 값은 원본에서도 이후 어디서도 다시 읽히지 않는 내부 전용 기록이다(전수
                // 조사 완료) - jQuery `.data()` 캐시 대신 커스텀 expando로 그대로 보존한다.
                welTR.__blockInfo = htBlockInfo;
            }

            yona.CodeCommentBox.show(welTR, {
                "sPlacement": htBlockInfo.bIsReversed ? "top" : "bottom",
                "nAdjustmentTop": htElement.welDiffBody ? _position(htElement.welDiffBody).top : 0
            });

            var nMarginFromBorder = 20;

            if(!htBlockInfo.bIsReversed && _doesCommentBoxOutOfWindow()){
                window.scrollTo(0, yona.CodeCommentBox.offset().top + yona.CodeCommentBox.height() - window.innerHeight + nMarginFromBorder);
            }

            if(htBlockInfo.bIsReversed && _doesCommentBoxOutOfWindow()){
                window.scrollTo(0, yona.CodeCommentBox.offset().top - nMarginFromBorder);
            }

            htVar.htBlockInfo = htBlockInfo;
        }

        /**
         * @returns {boolean}
         * @private
         */
        function _doesCommentBoxOutOfWindow(){
            var nScrollTop = document.body.scrollTop;
            var nOffsetTop = yona.CodeCommentBox.offset().top;

            return (nScrollTop + window.innerHeight < nOffsetTop) ||
                   (nOffsetTop + yona.CodeCommentBox.height() > nScrollTop + window.innerHeight);
        }

        /**
         * @param {Event} weEvt
         */
        function _onClickLineNumA(weEvt){
            window.getSelection().removeAllRanges();

            var elTr = this.closest("tr");
            var elPre = elTr ? elTr.querySelector("td.code pre") : null;
            if(!elPre){
                return;
            }
            var oNode = elPre.childNodes[0];
            var oRange = document.createRange();

            oRange.setStart(oNode, 0);
            oRange.setEnd(oNode, oNode.length);
            window.getSelection().addRange(oRange);

            elPre.dispatchEvent(new Event("mouseup", {"bubbles": true}));
            _onClickBtnAddBlockComment();
        }

        /**
         * On Click fold/unfold thread toggle button
         *
         * @param weEvt
         * @private
         */
        function _onClickBtnFoldThread(weEvt){
            var elThread = this.closest(".comment-thread-wrap");
            if(elThread){
                elThread.classList.toggle("fold");
            }
            _setAllBtnThreadHerePosition();
        }

        /**
         * Set positions of all .btn-thread-here button
         *
         * @private
         */
        function _setAllBtnThreadHerePosition(){
            if(!htElement.welDiffBody){
                return;
            }
            htElement.welDiffBody.querySelectorAll(".btn-thread-here").forEach(function(el){
                _setBtnThreadHerePosition(el);
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
            var nThreadIndex = welThread && welThread.parentElement ?
                Array.prototype.indexOf.call(welThread.parentElement.children, welThread) : 0;
            welButton.style.right = ((nThreadIndex * _contentWidth(welButton)) + nPadding) + "px";

            // set unfold button top
            // find target line with thread
            var welEndLine = welThread ? _getTargetLineByThread(welThread) : null;
            if(welEndLine){
                welButton.style.top = (_position(welEndLine).top - nPadding) + "px";
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
            var sEndLineQuery = 'tr[data-line="' + welThread.dataset.rangeEndline + '"]' +
                '[data-side="' + welThread.dataset.rangeEndside + '"]';
            var elTr = welThread.closest("tr");
            var elPrev = elTr ? elTr.previousElementSibling : null;

            return (elPrev && elPrev.matches(sEndLineQuery)) ? elPrev : null;
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

            var welThread = weEvt.currentTarget;
            var htBlockInfo = {
                "sPath"       : welThread.dataset.rangePath,
                "sStartSide"  : welThread.dataset.rangeStartside,
                "nStartLine"  : parseInt(welThread.dataset.rangeStartline, 10),
                "nStartColumn": parseInt(welThread.dataset.rangeStartcolumn, 10),
                "sEndSide"    : welThread.dataset.rangeEndside,
                "nEndLine"    : parseInt(welThread.dataset.rangeEndline, 10),
                "nEndColumn"  : parseInt(welThread.dataset.rangeEndcolumn, 10)
            };
            yona.CodeCommentBlock.block(htBlockInfo);
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
            yona.CodeCommentBlock.unblock();

            if(yona.CodeCommentBox.isVisible() && htVar.htBlockInfo){
                yona.CodeCommentBlock.block(htVar.htBlockInfo);
            }
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

        function _setReviewListHeight() {
            var nMaxHeight;

            // position: sticky는 fixed와 달리 상태 변화 이벤트가 없어, top:10px에
            // 실제로 붙었는지 여부를 getBoundingClientRect()로 직접 판별한다(Bootstrap
            // affix.js의 .affix 클래스 판별을 대체).
            var bIsStuck = !!htElement.welReviewContainer &&
                htElement.welReviewContainer.getBoundingClientRect().top <= 10;

            if(bIsStuck && htElement.welContainer && htElement.welReviewList) {
                var nCodeDiffWrapOffsetBottom = _position(htElement.welContainer).top + _contentHeight(htElement.welContainer);
                var nReviewListOffsetBottom = _offset(htElement.welReviewList).top + _contentHeight(htElement.welReviewList);
                var nReviewListDefaultMarginBottom = 15;
                var nDiffWrapBottomPadding = 90;

                if(nCodeDiffWrapOffsetBottom <= nReviewListOffsetBottom + nReviewListDefaultMarginBottom) {
                    nMaxHeight = nCodeDiffWrapOffsetBottom - window.scrollY + nDiffWrapBottomPadding;
                } else {
                    nMaxHeight = window.innerHeight - _position(htElement.welReviewList).top - nReviewListDefaultMarginBottom;
                }
            } else if(htElement.welContainer && htElement.welReviewList) {
                nMaxHeight = _contentHeight(htElement.welContainer) - _position(htElement.welReviewList).top;
            }

            if(htElement.welReviewList && typeof nMaxHeight !== "undefined"){
                htElement.welReviewList.style.maxHeight = nMaxHeight + 'px';
            }
        }

        _init(htOptions || {});
    };
})("yona.code.Diff");
