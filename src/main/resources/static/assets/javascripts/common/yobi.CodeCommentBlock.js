/**
 * Yobi, Project Hosting SW
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

yobi = yobi || {};

yobi.CodeCommentBlock = (function(){
    var htVar = {};
    var htElement = {};
    var htBlockInfo = {};

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
        htElement.welContainer = $(htOptions.welContainer);
        htElement.welPopButtonOnBlock = $(htOptions.welPopButtonOnBlock);
    }

    /**
     * Attach event handler
     * @private
     */
    function _attachEvent(){
        htElement.welContainer.on("mouseup", _onMouseUpOnDiff);
        htElement.welContainer.on("mousedown", "td.code pre", _onMouseDownOnDiff);
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
        var welAnchor = $(oSelection.getRangeAt(0).startContainer).closest("tr");
        var welFocus = $(oSelection.getRangeAt(oSelection.rangeCount-1).endContainer).closest("tr");


        // data-line attribute is required on both of anchor and focus
        if(typeof welAnchor.data("line") === "undefined" || typeof welFocus.data("line") === "undefined"){
            return false;
        }

        // Range should be in same TABLE which means same file
        // .data("filePath") could be compared.
        if(welAnchor.closest("table").get(0) != welFocus.closest("table").get(0)){
            return false;
        }

        // detect whether is reversed
        var nAnchorIndex = welAnchor.index();
        var nFocusIndex = welFocus.index();
        var bIsReversed = (nAnchorIndex > nFocusIndex);
        var welStartLine = bIsReversed ? welFocus : welAnchor;
        var welEndLine = bIsReversed ? welAnchor : welFocus;

        // in range ...
        if(nAnchorIndex !== nFocusIndex){
            welStartLine.nextUntil(welEndLine).each(function(){
                var welLine = $(this);

                // tr.comments is tolerable
                if(!welLine.hasClass("comments") &&
                    welLine.find("td.code > pre").length !== 1){
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

        var welAnchor = $(anchor.startContainer).closest("tr");
        var welFocus = $(focus.endContainer).closest("tr");

        var welTable = welAnchor.closest("table");

        // detect whether is reversed
        var nAnchorIndex = welAnchor.index();
        var nFocusIndex = welFocus.index();

        var nAnchorOffset = anchor.startOffset;
        var nFocusOffset = focus.endOffset;

        var startIndex = $(htElement.mouseEventStart).closest("tr").index();

        var bIsReversed = (nAnchorIndex < startIndex) ||
                          (nAnchorIndex === nFocusIndex && nAnchorOffset > nFocusOffset);


        htBlockInfo = {
            "bIsReversed" : bIsReversed,
            "nStartLine"  : welAnchor.data("line"),
            "sStartType"  : welAnchor.data("type"),
            "sStartSide"  : welAnchor.data("type") === 'remove' ? 'A' : 'B',
            "nStartColumn": nAnchorOffset,
            "nEndLine"    : welFocus.data("line"),
            "sEndType"    : welFocus.data("type"),
            "sEndSide"    : welFocus.data("type") === 'remove' ? 'A' : 'B',
            "nEndColumn"  : nFocusOffset,
            "sPathA"      : welTable.data("pathA"),
            "sPathB"      : welTable.data("pathB"),
            "sPrevCommitId": welTable.data("commitA"),
            "sCommitId"    : welTable.data("commitB"),
            "sFilePath"   : welTable.data("filePath"),
            "sPath"   : welTable.data("filePath")
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

        htElement.welPopButtonOnBlock.show();
        htElement.welPopButtonOnBlock.css({
            "top" : htPosition.top  + "px",
            "left": htPosition.left + "px"
        });
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
        var welLine = htBlockInfo.bIsReversed ? htElements.welStartLine : htElements.welEndLine;

        var welCode = welLine.find("td.code");
        var htCodeOffset = welCode.position();
        var nMaxLeft = htElement.welContainer.width() - (htElement.welPopButtonOnBlock.width() * 2);

        return {
            "top" : htCodeOffset.top - (welCode.height() * nLineHeight),
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
                htElement.welPopButtonOnBlock.hide();
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
        htElements.aRows.forEach(function(welRow, nIndex){
            var welRowNode = welRow.find("td.code > pre").get(0).childNodes[0];
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
        var welContainer = $("table.diff-container" + sContainerProp);

        htResult.welStartLine = welContainer.find("tr" + sStartProp);
        htResult.welEndLine = welContainer.find("tr" + sEndProp);
        htResult.elStartLine = htResult.welStartLine.get(0);
        htResult.elEndLine = htResult.welEndLine.get(0);

        /// start of range
        htResult.aRows = [htResult.welStartLine];

        // if 2 or more rows has selected
        if(htResult.elStartLine !== htResult.elEndLine){
            /// in range
            htResult.aRows = htResult.aRows.concat(_getRowsBetween(htResult.elStartLine, htResult.elEndLine));

            /// end of range
            htResult.aRows.push(htResult.welEndLine);
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

        $(elStart).nextUntil(elEnd).each(function(i, elRow){
            var welRow = $(elRow);

            if(welRow.data("line")){
                aRows.push(welRow);
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
        $('[data-toggle="comment-block"]').each(_unwrapCommentBlock);

        _onUnwrapAllCodeCommentBlock();
        removeRanges();
    }

    /**
     * Unwrap each comment block
     * @private
     */
    function _unwrapCommentBlock(){
        var parent = $(this).parents("pre");
        var unwrappedHTML = parent.html().replace(this.outerHTML, this.innerHTML);

        parent.html(unwrappedHTML);
    }

    /**
     * @private
     */
    function _onUnwrapAllCodeCommentBlock(){
        if(htElement.welPopButtonOnBlock){
            htElement.welPopButtonOnBlock.hide();
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
