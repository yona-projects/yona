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
    var nLastMouseUp = 0;
    var htBlockInfo = {};
    var htVar = {};
    var htElement = {};

    /**
     * 초기화
     *
     * @param sQuery
     * @private
     */
    function _init(htOptions){
        _initElement(htOptions);
        _attachEvent();

        htVar.bPopButtonOnBlock = (typeof htOptions.bPopButtonOnBlock !== "undefined") ? htOptions.bPopButtonOnBlock : true;
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
        htElement.welContainer.on("mouseup",     ".diff-container", _onMouseUpOnDiff);
        htElement.welContainer.on("selectstart", ".diff-container", _onSelectStartOnDiff);
    }

    /**
     * DiffBody 영역에서 selectstart 이벤트 발생시 핸들러
     *
     * @private
     */
    function _onSelectStartOnDiff(){
        _unwrapAll();
    }

    /**
     * DiffBody 영역에서 mouseup 이벤트 발생시 핸들러
     *
     * @param weEvt
     * @private
     */
    function _onMouseUpOnDiff(weEvt){
        // prevent doubled mouseUp event
        if((weEvt.timeStamp - nLastMouseUp) < 10){
            return;
        }
        nLastMouseUp = weEvt.timeStamp;

        // if something selected
        if(_getSelectionHtml() === ''){
            return;
        }

        _setBlockDataBySelection();
    }

    /**
     * 선택한 영역의 HTML 내용을 반환한다
     *
     * @returns {string}
     */
    function _getSelectionHtml() {
        var oSelection, sHTML="";

        if(window.getSelection !== undefined){
            oSelection = window.getSelection();
            if(oSelection.rangeCount){
                var elContainer = document.createElement('div');
                for(var i = 0, nLength = oSelection.rangeCount; i < nLength; i++){
                    elContainer.appendChild(oSelection.getRangeAt(i).cloneContents());
                }
                sHTML = elContainer.innerHTML;
            }
        } else if(document.selection !== undefined){
            if(document.selection.type === 'Text'){
                sHTML = document.selection.createRange().htmlText;
            }
        }
        return sHTML;
    }

    /**
     * DiffBody 에서 선택한 영역 정보를 찾아 저장(= _setBlockData 를 호출)한다
     * @private
     */
    function _setBlockDataBySelection(){
        var htInfo = _getSelectionInfo();

        if(htInfo){
            _setBlockData(htInfo);
        }
    }

    /**
     * 선택한 영역에 관한 정보를 반환한다
     *
     * @returns {Hash Table}
     */
    function _getSelectionInfo(){
        var oSelection = document.getSelection();
        var elStart = _findClosestAncestorElementFrom(oSelection.anchorNode, "tr");
        var elEnd = _findClosestAncestorElementFrom(oSelection.focusNode, "tr");
        var welStart = $(elStart);
        var welEnd = $(elEnd);

        if(elStart === false || elEnd === false){ // 적당한 TR 엘리먼트를 찾지 못하는 경우
            return false;
        }
        if(!welStart.data("line") || !welEnd.data("line")){ // 줄 정보를 확인할 수 없으면
            return false;
        }
        if(elStart.parentElement !== elEnd.parentElement){  // 같은 파일 Diff 내에서만
            return false;
        }

        // 아래쪽에서 위로 선택영역을 잡은 경우 시작, 끝이 반대로 잡힌다
        // 혹은 같은 줄에서 오른쪽에서 왼쪽으로 선택 영역을 잡은 경우도
        var bReversed = (welStart.index() > welEnd.index())
                     || (elStart === elEnd && oSelection.anchorOffset > oSelection.focusOffset);
        var elStartLine = bReversed ? elEnd : elStart;
        var elEndLine = bReversed ? elStart : elEnd;

        /// start of range
        var aRows = [elStartLine];

        // if 2 or more rows has selected
        if(elStartLine !== elEndLine){
            /// in range
            aRows = aRows.concat(_getRowsBetween(elStartLine, elEndLine));

            /// end of range
            aRows.push(elEndLine);
        }

        // return information
        return {
            "aRows": aRows,
            "bReversed"    : bReversed,
            "nAnchorOffset": bReversed ? oSelection.focusOffset  : oSelection.anchorOffset,
            "nFocusOffset" : bReversed ? oSelection.anchorOffset : oSelection.focusOffset,
            "oAnchorNode"  : bReversed ? oSelection.focusNode    : oSelection.anchorNode, // Note: node not equals row element
            "oFocusNode"   : bReversed ? oSelection.anchorNode   : oSelection.focusNode,
            "welStartLine" : $(elStartLine),
            "welEndLine"   : $(elEndLine)
        };
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
            if($(elRow).data("line")){
                aRows.push(elRow);
            }
        });

        return aRows;
    }

    /**
     * 지정한 노드에서 가장 가까운 상위 엘리먼트 중 지정한 태그명을 갖는 엘리먼트를 반환한다
     *
     * @param {Object} oNode 기준 노드
     * @param {String} sTagName 태그명
     * @returns {*}
     */
    function _findClosestAncestorElementFrom(oNode, sTagName){
        return (oNode.nodeType === 1 && oNode.tagName.toLowerCase() === sTagName) ? oNode : (oNode.parentNode ? _findClosestAncestorElementFrom(oNode.parentNode, sTagName) : false);
    }

    /**
     * line:offset ~ line:offset 을 Wrap 하는 함수
     *
     * @param htOffset
     * @param htOffset.sPathA
     * @param htOffset.sPathB
     * @param htOffset.nStartLine
     * @param htOffset.nStartOffset (optional)
     * @param htOffset.sStartLineType (optional)
     * @param htOffset.nEndLine
     * @param htOffset.nEndOffset (optional)
     * @param htOffset.sEndLineType (optional)
     * @private
     * @example
     * _wrapByOffset({"nStartLine": 117, "nStartOffset":0, "nEndLine":120, "nEndOffset":3});
     */
    function _wrapByOffset(htOffset){
        var htInfo = _getOffsetInfo(htOffset);

        if(htInfo !== false){
            _unwrapAll();
            _wrapOnDiff(htInfo);
        }
    }

    /**
     * 지정한 오프셋 영역에 대한 정보를 반환한다
     *
     * @param htOffset
     * @returns {*}
     * @private
     */
    function _getOffsetInfo(htOffset){
        var htElements = _getElementsByOffsetOptions(htOffset);
        if(!htElements.elStartLine || !htElements.elEndLine){
            return false;
        }

        var oAnchorNode = htElements.welStartLine.find("pre").get(0).childNodes[0];
        var oFocusNode = htElements.welEndLine.find("pre").get(0).childNodes[0];
        var aRows = [htElements.elStartLine];

        if(htElements.elStartLine !== htElements.elEndLine){
            aRows = aRows.concat(_getRowsBetween(htElements.elStartLine, htElements.elEndLine));
            aRows.push(htElements.elEndLine);
        }

        var htInfo = {
            "aRows": aRows,
            "nAnchorOffset": htOffset.nStartOffset || 0,
            "nFocusOffset" : htOffset.nEndOffset || oFocusNode.length,
            "oAnchorNode"  : oAnchorNode,
            "oFocusNode"   : oFocusNode,
            "welStartLine" : htElements.welStartLine,
            "welEndLine"   : htElements.welEndLine
        };

        return htInfo;
    }

    /**
     * 선택한 영역을 Wrap
     *
     * @param {Hash Table} htInfo
     * @private
     */
    function _wrapOnDiff(htInfo){
        htInfo.aRows.forEach(function(elRow){
            var welRow = $(elRow);
            var oRange = document.createRange();
            var elWrapper = _getCommentLineWrapper(htInfo);

            // wrap selected lines
            if(welRow.has(htInfo.oAnchorNode).length > 0){ // first line
                oRange.setStart(htInfo.oAnchorNode, htInfo.nAnchorOffset);
                oRange.setEnd(htInfo.oAnchorNode, htInfo.oAnchorNode === htInfo.oFocusNode ? htInfo.nFocusOffset : htInfo.oAnchorNode.length);
            } else if((htInfo.oAnchorNode != htInfo.oFocusNode) && (welRow.has(htInfo.oFocusNode).length > 0)){ // last line
                oRange.setStart(htInfo.oFocusNode, 0);
                oRange.setEnd(htInfo.oFocusNode, htInfo.nFocusOffset);
            } else { // other rows
                var elCode = welRow.find("pre")[0];
                oRange.setStart(elCode, 0);
                oRange.setEnd(elCode, elCode.childNodes.length);
            }

            oRange.surroundContents(elWrapper);
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
        var sContainerProp = htOffset.sPathA ? "[data-path-a='" + htOffset.sPathA + "']" :
            (htOffset.sPathB ? "[data-path-b='" + htOffset.sPathB + "']" : "");
        var sStartProp = [htOffset.nStartLine ? "[data-line=" + htOffset.nStartLine + "]": "",
            htOffset.sStartLineType ? "[data-type=" + htOffset.sStartLineType + "]" : ""].join("");
        var sEndProp = [htOffset.nEndLine ? "[data-line=" + htOffset.nEndLine + "]": "",
            htOffset.sEndLineType ? "[data-type=" + htOffset.sEndLineType + "]": ""].join("");
        var welContainer = $("table.diff-container" + sContainerProp);

        htResult.welStartLine = welContainer.find("tr" + sStartProp);
        htResult.welEndLine = welContainer.find("tr" + sEndProp);
        htResult.elStartLine = htResult.welStartLine.get(0);
        htResult.elEndLine = htResult.welEndLine.get(0);

        return htResult;
    }

    /**
     * Returns comment line wrapper HTMLElement
     * @param htInfo
     * @returns {HTMLElement}
     * @private
     */
    function _getCommentLineWrapper(htInfo){
        var sWrapperId = _getCommentLineWrapperId(htInfo);
        var elWrapper =  document.createElement("SPAN");

        // 여러 줄에 사용되는 line wrapper 는 모두 다른 엘리먼트
        // TODO: Style 속성은 CSS ClassName 으로 대체할 것
        elWrapper.setAttribute("data-comment-wrapperId", sWrapperId);
        elWrapper.style.backgroundColor = "rgba(0,133,255,0.25)";//"rgba(133,246,150,0.25)";
        elWrapper.style.padding = "4px 0";

        return elWrapper;
    }

    /**
     * Returns comment-wrapperId
     * @param htInfo
     * @returns {string}
     * @private
     */
    function _getCommentLineWrapperId(htInfo){
        return "__wrapper-" + [htInfo.welStartLine.data("line"), htInfo.nAnchorOffset, htInfo.welEndLine.data("line"), htInfo.nFocusOffset].join("-");
    }

    /**
     * Unwrap all comment wrappers
     * @private
     */
    function _unwrapAll(){
        $("[data-comment-wrapperId]").each(function(i, el){
            el.outerHTML = el.innerHTML;
        });

        _onUnwrapAllCodeCommentBlock();
    }

    /**
     * CodeCommentBlock 에서 wrap 이벤트 발생시
     * 사용자가 어떤 영역을 선택하면 그 근처에 댓글작 버튼을 표시
     * @param htInfo
     * @private
     */
    function _onWrapCodeCommentBlock(htInfo){
        if(htVar.bPopButtonOnBlock && htElement.welButtonOnBlock){
            var welLine = (htInfo.bReversedRange ? htInfo.welStartLine : htInfo.welEndLine);
            var welCode = welLine.find("td.code");
            var nBlockOffset = (htInfo.bReversedRange ? htInfo.nStartOffset : htInfo.nEndOffset);
            var htCodeOffset = welCode.offset();
            var nTop = htCodeOffset.top + (htInfo.bReversedRange ? -20 : welCode.height());
            var nLeft = htCodeOffset.left + (nBlockOffset * 7);

            htElement.welButtonOnBlock.show();
            htElement.welButtonOnBlock.css({
                "top" : nTop +"px",
                "left": nLeft+"px"
            });
        }
    }

    /**
     * CodeCommentBlock 에서 unwrapAll 이벤트 발생시
     * 댓글작성 버튼 감춤
     * @private
     */
    function _onUnwrapAllCodeCommentBlock(){
        htElement.welButtonOnBlock.hide();
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

    /**
     * Setter for block data
     * @param htInfo
     * @returns {{sStartLineType: *, nStartLine: *, nStartOffset: (*|htInfo.nAnchorOffset), sEndLineType: *, nEndLine: *, nEndOffset: (*|htInfo.nFocusOffset), welStartLine: (*|htInfo.welStartLine), welEndLine: (*|htInfo.welEndLine), bReversedRange: (*|yobi.CodeCommentBlock._getSelectionInfo.bReversed), sPathA: *, sPathB: *}}
     * @private
     */
    function _setBlockData(htInfo){
        var welContainer = htInfo.welStartLine.parents("table");
        var sPathA = welContainer.data("path-a");
        var sPathB = welContainer.data("path-b");

        htBlockInfo = {
            "sStartLineType": htInfo.welStartLine.data("type"),
            "nStartLine"    : htInfo.welStartLine.data("line"),
            "nStartOffset"  : htInfo.nAnchorOffset,
            "sEndLineType"  : htInfo.welEndLine.data("type"),
            "nEndLine"      : htInfo.welEndLine.data("line"),
            "nEndOffset"    : htInfo.nFocusOffset,
            "welStartLine"  : htInfo.welStartLine,
            "welEndLine"    : htInfo.welEndLine,
            "bReversedRange": htInfo.bReversed,
            "sPathA"        : sPathA,
            "sPathB"        : sPathB
        };

        _onWrapCodeCommentBlock(htBlockInfo);
    }

    // public interface
    return {
        "init"        : _init,
        "block"       : _wrapByOffset,
        "unblock"     : _unwrapAll,
        "getData"     : _getBlockData
    };
})();
