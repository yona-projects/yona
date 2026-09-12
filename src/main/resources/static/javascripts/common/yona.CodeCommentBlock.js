/**
 * Yona, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Jihan Kim
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

yona = yona || {};

yona.CodeCommentBlock = (function(){
    var htVar = {};
    var htElement = {};
    var htBlockInfo = {};

    // 호출부(yona.code.Diff.js)가 welContainer/welPopButtonOnBlock을 jQuery 객체로
    // 넘기므로 raw DOM으로 정규화한다.
    function _toElement(el){
        if(!el){
            return null;
        }
        if(el.jquery){
            return el[0] || null;
        }
        return el;
    }

    // node(텍스트 노드일 수 있음)에서 가장 가까운 selector 매칭 조상을 찾는다.
    // Element.prototype.closest()는 텍스트 노드에는 없으므로 parentElement로 보정한다.
    function _closestFrom(node, selector){
        if(!node){
            return null;
        }
        var el = node.nodeType === 1 ? node : node.parentElement;
        return el ? el.closest(selector) : null;
    }

    // jQuery .index()(인자 없음)와 동일: 같은 부모 아래 형제 엘리먼트들 중 순번.
    function _elementIndex(el){
        if(!el || !el.parentElement){
            return -1;
        }
        return Array.prototype.indexOf.call(el.parentElement.children, el);
    }

    // jQuery .nextUntil(until)과 동일: 다음 형제들을 until(제외) 전까지 수집.
    function _nextUntil(el, until){
        var result = [];
        var sib = el.nextElementSibling;
        while(sib && sib !== until){
            result.push(sib);
            sib = sib.nextElementSibling;
        }
        return result;
    }

    // jQuery .width()는 display:none인 엘리먼트도 임시로 보이게 만들어 측정한다
    // (popup 버튼이 이전 선택 종료로 숨겨진 채로 다음 위치 계산이 이뤄질 수 있어 필요).
    function _measureWidthEvenIfHidden(el){
        if(getComputedStyle(el).display !== "none"){
            return el.offsetWidth;
        }
        var prevDisplay = el.style.display;
        var prevVisibility = el.style.visibility;
        el.style.visibility = "hidden";
        el.style.display = "";
        var width = el.offsetWidth;
        el.style.display = prevDisplay;
        el.style.visibility = prevVisibility;
        return width;
    }

    /**
     * @param sQuery
     * @private
     */
    function _init(htOptions){
        _initElement(htOptions);
        _attachEvent();

        htVar.bPopButtonOnBlock = (typeof htOptions.bPopButtonOnBlock !== "undefined")
                                    ? htOptions.bPopButtonOnBlock : true;
    }

    /**
     * Initialize element variables
     * @param htOptions
     * @private
     */
    function _initElement(htOptions){
        htElement.welContainer = _toElement(htOptions.welContainer);
        htElement.welPopButtonOnBlock = _toElement(htOptions.welPopButtonOnBlock);
    }

    /**
     * Attach event handler
     * @private
     */
    function _attachEvent(){
        htElement.welContainer.addEventListener("mouseup", _onMouseUpOnDiff);
        htElement.welContainer.addEventListener("mousedown", function(weEvt){
            var match = weEvt.target.closest("td.code pre");
            if(match && htElement.welContainer.contains(match)){
                _onMouseDownOnDiff.call(match, weEvt);
            }
        });
        htElement.mouseEventStart = {};
    }

    /**
     * @private
     */
    function _onMouseDownOnDiff(weEvt){
        if(!_isMouseLeftButtonPressed(weEvt)){
            return;
        }

        htElement.mouseEventStart = this;
        _unwrapAll();

        removeRanges();

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
     * @param weEvt
     * @private
     */
    function _onMouseUpOnDiff(){
        if(_doesCommentableRangeExists()){
            _setBlockDataBySelection();
            _onWrapCodeCommentBlock();
        }
    }

    /**
     * @returns {boolean}
     * @private
     */
    function _doesCommentableRangeExists(){
        var bHasRange = false;
        var oSelection = document.getSelection();

        // check empty seletction string
        if(oSelection.toString().length == 0){
            return false;
        }

        // get anchor, focus row (TR) from selected text node
        var elAnchor = _closestFrom(oSelection.getRangeAt(0).startContainer, "tr");
        var elFocus = _closestFrom(oSelection.getRangeAt(oSelection.rangeCount-1).endContainer, "tr");

        // data-line attribute is required on both of anchor and focus
        if(!elAnchor || !elFocus ||
            typeof elAnchor.dataset.line === "undefined" || typeof elFocus.dataset.line === "undefined"){
            return false;
        }

        // Range should be in same TABLE which means same file
        // .data("filePath") could be compared.
        if(elAnchor.closest("table") != elFocus.closest("table")){
            return false;
        }

        // detect whether is reversed
        var nAnchorIndex = _elementIndex(elAnchor);
        var nFocusIndex = _elementIndex(elFocus);
        var bIsReversed = (nAnchorIndex > nFocusIndex);
        var elStartLine = bIsReversed ? elFocus : elAnchor;
        var elEndLine = bIsReversed ? elAnchor : elFocus;

        // in range ...
        if(nAnchorIndex !== nFocusIndex){
            _nextUntil(elStartLine, elEndLine).forEach(function(elLine){
                // tr.comments is tolerable
                if(!elLine.classList.contains("comments") &&
                    elLine.querySelectorAll("td.code > pre").length !== 1){
                    bHasRange = true;
                }
            });
        }

        return !bHasRange;
    }

    /**
     * @private
     */
    function _setBlockDataBySelection(){
        // get anchor, focus row (TR) from selected text node
        var oSelection = document.getSelection();

        var anchor = oSelection.getRangeAt(0);
        var focus = (oSelection.rangeCount ===1) ? anchor : oSelection.getRangeAt(oSelection.rangeCount-1);

        var elAnchor = _closestFrom(anchor.startContainer, "tr");
        var elFocus = _closestFrom(focus.endContainer, "tr");

        var elTable = elAnchor.closest("table");

        // detect whether is reversed
        var nAnchorIndex = _elementIndex(elAnchor);
        var nFocusIndex = _elementIndex(elFocus);

        var nAnchorOffset = anchor.startOffset;
        var nFocusOffset = focus.endOffset;

        var startIndex = _elementIndex(_closestFrom(htElement.mouseEventStart, "tr"));

        var bIsReversed = (nAnchorIndex < startIndex) ||
                          (nAnchorIndex === nFocusIndex && nAnchorOffset > nFocusOffset);


        htBlockInfo = {
            "bIsReversed" : bIsReversed,
            "nStartLine"  : parseInt(elAnchor.dataset.line, 10),
            "sStartType"  : elAnchor.dataset.type,
            "sStartSide"  : elAnchor.dataset.type === 'remove' ? 'A' : 'B',
            "nStartColumn": nAnchorOffset,
            "nEndLine"    : parseInt(elFocus.dataset.line, 10),
            "sEndType"    : elFocus.dataset.type,
            "sEndSide"    : elFocus.dataset.type === 'remove' ? 'A' : 'B',
            "nEndColumn"  : nFocusOffset,
            "sPathA"      : elTable.dataset.pathA,
            "sPathB"      : elTable.dataset.pathB,
            "sPrevCommitId": elTable.dataset.commitA,
            "sCommitId"    : elTable.dataset.commitB,
            "sFilePath"   : elTable.dataset.filePath,
            "sPath"   : elTable.dataset.filePath
        };
    }

    /**
     * @private
     */
    function _onWrapCodeCommentBlock(){
        if(htVar.bPopButtonOnBlock && htElement.welPopButtonOnBlock){
            _showPopButtonOnBlock();
            _setSelectionWatcher();
        }
    }

    /**
     * Show pop button on block(welPopButtonOnBlock)
     * which is for create new comment thread
     * near to selected block
     *
     * @private
     */
    function _showPopButtonOnBlock(){
        var htPosition = _getPopButtonPosition();

        htElement.welPopButtonOnBlock.style.display = "";
        htElement.welPopButtonOnBlock.style.top = htPosition.top + "px";
        htElement.welPopButtonOnBlock.style.left = htPosition.left + "px";
    }

    /**
     * Returns proper position for welPopButtonOnBlock.
     * Calculate top, left offset position by finding last line of selection block.
     *
     * @returns {Hash Table} {top: number, left: number}
     * @private
     */
    function _getPopButtonPosition(){
        var htBlockInfo = _getBlockData();
        var htElements = _getElementsByOffsetOptions(htBlockInfo);

        var nColumnWidth = 7;
        var nLineHeight = 1.5;
        var nColumn = htBlockInfo.bIsReversed ? htBlockInfo.nStartColumn : htBlockInfo.nEndColumn;
        var elLine = htBlockInfo.bIsReversed ? htElements.elStartLine : htElements.elEndLine;

        var elCode = elLine.querySelector("td.code");
        var htCodeOffset = {"top": elCode.offsetTop, "left": elCode.offsetLeft};
        var nMaxLeft = htElement.welContainer.clientWidth - (_measureWidthEvenIfHidden(htElement.welPopButtonOnBlock) * 2);

        return {
            "top" : htCodeOffset.top - (elCode.offsetHeight * nLineHeight),
            "left": Math.min(htCodeOffset.left + (nColumn * nColumnWidth), nMaxLeft)
        };
    }

    /**
     * Watch whether selection exists after welPopButtonOnBlock has shown.
     * If no more selection exists, Hide welPopButtonOnBlock and stop to watching.
     *
     * @private
     */
    function _setSelectionWatcher(){
        if(htVar.nSelectionWatcher){
            clearInterval(htVar.nSelectionWatcher);
            htVar.nSelectionWatcher = null;
        }

        htVar.nSelectionWatcher = setInterval(function(){
            if(document.getSelection().toString().length === 0){
                htElement.welPopButtonOnBlock.style.display = "none";
                clearInterval(htVar.nSelectionWatcher);
                htVar.nSelectionWatcher = null;
            }
        }, 50);
    }

    /**
     * @param htOffset
     * @param htOffset.sPathA
     * @param htOffset.sPathB
     * @param htOffset.nStartLine
     * @param htOffset.nStartColumn (optional)
     * @param htOffset.sStartLineType (optional)
     * @param htOffset.nEndLine
     * @param htOffset.nEndColumn (optional)
     * @param htOffset.sEndLineType (optional)
     * @private
     * @example
     * _wrapByOffset({"nStartLine": 117, "nStartColumn":0, "nEndLine":120, "nEndColumn":3});
     */
    function _wrapByOffset(htOffset){
        _unwrapAll();
        _wrapOnDiff(htOffset);
        removeRanges();
    }

    /**
     * @param {Hash Table} htOffset
     * @private
     */
    function _wrapOnDiff(htOffset){
        removeRanges();

        var htElements = _getElementsByOffsetOptions(htOffset);
        if(!htElements.elStartLine || !htElements.elEndLine){
            return false;
        }

        var nRows = htElements.aRows.length;
        htElements.aRows.forEach(function(elRow, nIndex){
            var welRowNode = elRow.querySelector("td.code > pre").childNodes[0];
            var oRange = document.createRange();
            var elBlock = _getCommentLineWrapper();
            var nStartColumn = 0;
            var nEndColumn = 0;
            var nNodeLength = welRowNode.length;

            if(nRows === 1){               // in one line
                nStartColumn = htOffset.nStartColumn;
                nEndColumn = htOffset.nEndColumn;
            } else if(nIndex === 0){       // first line
                nStartColumn = htOffset.nStartColumn;
                nEndColumn = nNodeLength;
            } else if(nIndex === nRows-1){ // last line
                nStartColumn = 0;
                nEndColumn = htOffset.nEndColumn;
            } else {                       // and the others
                nStartColumn = 0;
                nEndColumn = nNodeLength;
            }

            oRange.setStart(welRowNode, nStartColumn);
            oRange.setEnd(welRowNode, Math.min(nEndColumn, nNodeLength));
            oRange.surroundContents(elBlock);
        });


    }

    /**
     * @param htOffset
     * @returns {Hash Table}
     * @private
     */
    function _getElementsByOffsetOptions(htOffset){
        var htResult = {};
        var sContainerProp = htOffset.sPath ? "[data-file-path='" + htOffset.sPath + "']": "";
        var sStartProp = [htOffset.nStartLine ? "[data-line=" + htOffset.nStartLine + "]": "",
            htOffset.sStartSide ? "[data-side=" + htOffset.sStartSide + "]" : ""].join("");
        var sEndProp = [htOffset.nEndLine ? "[data-line=" + htOffset.nEndLine + "]": "",
            htOffset.sEndSide ? "[data-side=" + htOffset.sEndSide + "]": ""].join("");

        // sContainerProp이 없으면(sPath 미지정) jQuery는 "table.diff-container" 전체를
        // 대상으로 검색했다 - 페이지에 diff 테이블이 여러 개 있을 수 있어 컬렉션으로 다룬다.
        var startLines = [];
        var endLines = [];
        document.querySelectorAll("table.diff-container" + sContainerProp).forEach(function(table){
            table.querySelectorAll("tr" + sStartProp).forEach(function(tr){ startLines.push(tr); });
            table.querySelectorAll("tr" + sEndProp).forEach(function(tr){ endLines.push(tr); });
        });

        htResult.elStartLine = startLines[0] || null;
        htResult.elEndLine = endLines[0] || null;

        /// start of range
        htResult.aRows = htResult.elStartLine ? [htResult.elStartLine] : [];

        // if 2 or more rows has selected
        if(htResult.elStartLine !== htResult.elEndLine){
            /// in range
            htResult.aRows = htResult.aRows.concat(_getRowsBetween(htResult.elStartLine, htResult.elEndLine));

            /// end of range
            if(htResult.elEndLine){
                htResult.aRows.push(htResult.elEndLine);
            }
        }

        return htResult;
    }

    /**
     * @param elStart
     * @param elEnd
     * @returns {Array}
     * @private
     */
    function _getRowsBetween(elStart, elEnd){
        var aRows = [];

        if(!elStart){
            return aRows;
        }

        _nextUntil(elStart, elEnd).forEach(function(elRow){
            if(elRow.dataset.line){
                aRows.push(elRow);
            }
        });

        return aRows;
    }

    /**
     * Returns comment line wrapper HTMLElement
     * @returns {HTMLElement}
     * @private
     */
    function _getCommentLineWrapper(){
        var elWrapper =  document.createElement("SPAN");
        elWrapper.setAttribute("data-toggle", "comment-block");
        elWrapper.className = "review-block";

        return elWrapper;
    }

    /**
     * Unwrap all comment wrappers
     * @private
     */
    function _unwrapAll(){
        document.querySelectorAll('[data-toggle="comment-block"]').forEach(function(el){
            _unwrapCommentBlock.call(el);
        });

        _onUnwrapAllCodeCommentBlock();
        removeRanges();
    }

    /**
     * Unwrap each comment block
     * @private
     */
    function _unwrapCommentBlock(){
        var parent = this.closest("pre");
        parent.innerHTML = parent.innerHTML.replace(this.outerHTML, this.innerHTML);
    }

    /**
     * @private
     */
    function _onUnwrapAllCodeCommentBlock(){
        if(htElement.welPopButtonOnBlock){
            htElement.welPopButtonOnBlock.style.display = "none";
        }
    }

    /**
     * Getter for latest block data
     *
     * @returns {Hash Table}
     * @private
     */
    function _getBlockData(){
        return htBlockInfo;
    }

    function removeRanges() {
      if (window.getSelection) {  // all browsers, except IE before version 9
        window.getSelection().removeAllRanges();
      } else {
        document.selection.empty();
      }
    }

    // public interface
    return {
        "init"        : _init,
        "block"       : _wrapByOffset,
        "unblock"     : _unwrapAll,
        "getData"     : _getBlockData
    };
})();
