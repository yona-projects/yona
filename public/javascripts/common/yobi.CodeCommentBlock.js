/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Jihan Kim
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
     * 초기화
     *
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
        htElement.welButtonOnBlock = $(htOptions.welButtonOnBlock);
    }

    /**
     * Attach event handler
     * @private
     */
    function _attachEvent(){
        htElement.welContainer.on("mouseup", _onMouseUpOnDiff);
        htElement.welContainer.on("mousedown", "td.code pre", _onMouseDownOnDiff);
    }

    /**
     * DiffBody 영역에서 mousedown 이벤트 발생시 핸들러
     * selectstart 이벤트는 FireFox 브라우저에서 지원되지 않기 때문에 mousedown 이벤트 사용
     *
     * @private
     */
    function _onMouseDownOnDiff(weEvt){
        if(!_isMouseLeftButtonPressed(weEvt)){
            return;
        }

        _unwrapAll();
        window.getSelection().removeAllRanges(); // 기존의 Selection 정보를 지워야 함
    }

    /**
     * 주어진 마우스 이벤트가 왼쪽 버튼을 누른 것인지 여부를 반환
     *
     * @param weEvt
     * @returns {boolean}
     * @private
     */
    function _isMouseLeftButtonPressed(weEvt){
        return (weEvt.which === 1);
    }

    /**
     * DiffBody 영역에서 mouseup 이벤트 발생시 핸들러
     *
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
     * 코멘트 달 수 있는 블럭 영역인지 여부를 불리언 값으로 반환
     *
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
        var welAnchor = $(oSelection.anchorNode.parentElement).closest("tr");
        var welFocus = $(oSelection.focusNode.parentElement).closest("tr");

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
     * DiffBody 에서 선택한 영역 정보를 찾아 저장한다
     * @private
     */
    function _setBlockDataBySelection(){
        // get anchor, focus row (TR) from selected text node
        var oSelection = document.getSelection();
        var welAnchor = $(oSelection.anchorNode.parentElement).closest("tr");
        var welFocus = $(oSelection.focusNode.parentElement).closest("tr");
        var welTable = welAnchor.closest("table");

        // detect whether is reversed
        var nAnchorIndex = welAnchor.index();
        var nFocusIndex = welFocus.index();
        var nAnchorOffset = oSelection.anchorOffset;
        var nFocusOffset = oSelection.focusOffset;
        var bIsReversed = (nAnchorIndex > nFocusIndex) ||
                          (nAnchorIndex === nFocusIndex && nAnchorOffset > nFocusOffset);
        var welStartLine = bIsReversed ? welFocus : welAnchor;
        var welEndLine = bIsReversed ? welAnchor : welFocus;

        htBlockInfo = {
            "bIsReversed" : bIsReversed,
            "nStartLine"  : welStartLine.data("line"),
            "sStartType"  : welStartLine.data("type"),
            "sStartSide"  : welStartLine.data("type") === 'remove' ? 'A' : 'B',
            "nStartColumn": bIsReversed ? oSelection.focusOffset  : oSelection.anchorOffset,
            "nEndLine"    : welEndLine.data("line"),
            "sEndType"    : welEndLine.data("type"),
            "sEndSide"    : welEndLine.data("type") === 'remove' ? 'A' : 'B',
            "nEndColumn"  : bIsReversed ? oSelection.anchorOffset : oSelection.focusOffset,
            "sPathA"      : welTable.data("pathA"),
            "sPathB"      : welTable.data("pathB"),
            "sPrevCommitId": welTable.data("commitA"),
            "sCommitId"    : welTable.data("commitB"),
            "sFilePath"   : welTable.data("filePath"),
            "sPath"   : welTable.data("filePath")
        };
    }

    /**
     * CodeCommentBlock 에서 wrap 이벤트 발생시
     * 사용자가 어떤 영역을 선택하면 그 근처에 댓글작성 버튼을 표시
     *
     * @private
     */
    function _onWrapCodeCommentBlock(){
        if(htVar.bPopButtonOnBlock && htElement.welButtonOnBlock){
            var htBlockInfo = _getBlockData();
            var htElements = _getElementsByOffsetOptions(htBlockInfo);

            var welLine = (htBlockInfo.bIsReversed ? htElements.welStartLine : htElements.welEndLine);
            var welCode = welLine.find("td.code");
            var nBlockOffset = (htBlockInfo.bIsReversed ? htBlockInfo.nStartColumn : htBlockInfo.nEndColumn);
            var htCodeOffset = welCode.position();
            var nTop = htCodeOffset.top + (htBlockInfo.bIsReversed ? -20 : welCode.height());
            var nLeft = htCodeOffset.left + (nBlockOffset * 7);

            // 블럭 영역이 diff-container 테이블 밖에 잡힐때를 대비해서
            if(nLeft > (htCodeOffset.left + welLine.width() - 40)){
                nLeft = htCodeOffset.left + welLine.width() - 80;
            }

            htElement.welButtonOnBlock.show();
            htElement.welButtonOnBlock.css({
                "top" : nTop +"px",
                "left": nLeft+"px"
            });

            _setSelectionWatcher();
        }
    }

    /**
     * 사용자가 어떤 영역을 선택해서 welButtonOnBlock 이 표시된 이후에
     * 계속 Selection 이 존재하는지를 확인해서 없어지면 welButtonOnBlock 을 감추고
     * 더 이상 감시하지 않는다
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
                htElement.welButtonOnBlock.hide();
                clearInterval(htVar.nSelectionWatcher);
                htVar.nSelectionWatcher = null;
            }
        }, 50);
    }

    /**
     * line:offset ~ line:offset 을 Wrap 하는 함수
     *
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
    }

    /**
     * 선택한 영역을 Wrap
     *
     * @param {Hash Table} htOffset _setBlockDataBySelection 에서 반환하는 값과 같은 형식
     * @private
     */
    function _wrapOnDiff(htOffset){
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

        document.getSelection().removeAllRanges();
    }

    /**
     * _wrapByOffset() 함수의 인자로부터 필요한 엘리먼트들을 찾아서 반환하는 함수
     *
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
     * elStart 와 elEnd 사이의 TableRow(TR) 배열을 반환한다
     *
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
     * CodeCommentBlock 에서 unwrapAll 이벤트 발생시
     * 댓글작성 버튼 감춤
     * @private
     */
    function _onUnwrapAllCodeCommentBlock(){
        if(htElement.welButtonOnBlock){
            htElement.welButtonOnBlock.hide();
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

    // public interface
    return {
        "init"        : _init,
        "block"       : _wrapByOffset,
        "unblock"     : _unwrapAll,
        "getData"     : _getBlockData
    };
})();
