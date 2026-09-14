/**
 * yona.CodeCommentBox - legacy yobi.CodeCommentBox.js(302줄)의 vanilla 포팅
 * (code.Diff.js 복원 계획 4단계). 페이지당 단 하나 존재하는 "#review-form" DOM을
 * 필요한 위치(새 라인/범위 댓글 자리, 또는 기존 스레드)로 옮겨 재사용하는 플로팅
 * 코드리뷰 댓글 상자다 - 매번 새 폼을 만들어 삽입하지 않는다.
 */
yona = yona || {};

yona.CodeCommentBox = (function(){
    "use strict";

    var htVar = {};
    var htElement = {};

    function _init(){
        _initElement();
        _attachEvent();
    }

    function _initElement(){
        htElement.welCommentWrap = document.getElementById("review-form");
        htElement.welCommentForm = htElement.welCommentWrap.querySelector("form");
        htElement.welCommentTextarea = htElement.welCommentForm.querySelector('[data-toggle="markdown-editor"] textarea');
        htElement.welInitialParent = htElement.welCommentWrap.parentElement;
    }

    function _attachEvent(){
        htElement.welCommentForm.addEventListener("click", function(weEvt){
            if(weEvt.target.closest('[data-toggle="close"]')){
                _hide();
            }
        });
    }

    /**
     * @param welTarget 댓글을 달 기준 요소 - 새 댓글이면 diff의 tr(data-line 보유), 답글이면
     *                  data-thread-id를 가진 버튼/요소
     * @param htOptions.sPlacement "top" 또는 "bottom" - welTarget 기준 어느 방향에 화살표를 둘지
     * @param htOptions.htBlockInfo 새 댓글일 때 CodeRangeRequest 필드로 변환될 blockInfo
     */
    function _show(welTarget, htOptions){
        htOptions = htOptions || {};

        var sPlacement = (htOptions.sPlacement || "bottom").toLowerCase();
        _setArrowPlacement(sPlacement);
        _placeReviewForm(welTarget, sPlacement, htOptions.htBlockInfo);
        htElement.welCommentWrap.style.display = "block";
        if(htElement.welCommentTextarea){
            htElement.welCommentTextarea.focus();
        }

        window.dispatchEvent(new CustomEvent("CodeCommentBox:aftershow"));
    }

    /**
     * arrow는 welTarget과 반대쪽에 놓인다(bottom 배치면 위쪽에 화살표)
     */
    function _setArrowPlacement(sPlacement){
        htVar.htArrowPlacement = htVar.htArrowPlacement || {
            "top": "bottom",
            "bottom": "top"
        };

        htElement.welCommentWrap.classList.remove("arrow-top", "arrow-bottom");
        htElement.welCommentWrap.classList.add("arrow-" + htVar.htArrowPlacement[sPlacement]);
    }

    function _placeReviewForm(welTarget, sPlacement, htBlockInfo){
        var welFormTarget = _getReviewFormTarget(welTarget);
        var welFormPlace = _getReviewFormPlace(welFormTarget, sPlacement);

        _setReviewFormFields(_getReviewFormFieldData(welFormTarget, htBlockInfo));

        var welWriteCommentForm = welFormPlace.querySelector(".write-comment-form");
        // P3-73: Node.appendChild()는 대상이 이미 같은 부모의 자식이어도(마지막 자식이어도)
        // 내부적으로 disconnect 후 reconnect를 일으킨다 - 불필요한 재배치는 이 가드로 피한다
        // (P3-55 1차 시도에서 한 번 발견·수정됐다가 4단계 복원 때 유실됐던 것과 동일한 가드).
        // 다만 새 댓글(스레드 답글이 아닌 경우)은 _getReviewFormPlace()가 매번 새 <tr>을
        // 만들어 반환하므로 이 가드로도 실제 이동 자체는 피할 수 없다 - 그 경우를 위해
        // _remountEditor()로 별도 대응한다(바로 아래).
        if(htElement.welCommentWrap.parentElement !== welWriteCommentForm){
            welWriteCommentForm.appendChild(htElement.welCommentWrap);
            _remountEditor();
        }
    }

    /**
     * P3-73: <yona-markdown-editor>(lib/yona-markdown-editor, 미수정)의 connectedCallback은
     * 이미 shadowRoot가 있으면 재초기화를 건너뛴다(중복 초기화 방지용 가드) - 그런데 위
     * appendChild로 review-form을 다른 위치로 옮기면 그 안에 있는 이 커스텀 엘리먼트도
     * disconnectedCallback → connectedCallback을 다시 타면서 기존 CodeMirror 뷰가
     * destroy된 뒤 그 가드 때문에 다시 만들어지지 않는다(실측 확인 - 이동 후
     * `.editor-wrapper`가 완전히 빈 채로 남아 타이핑할 수 있는 입력란 자체가 사라짐).
     * 벤더 파일을 수정할 수 없으므로, 이동 직후 완전히 새 인스턴스로 교체해 우회한다 -
     * `cloneNode(true)`는 light DOM 자식(`<textarea>`와 그 속성)은 그대로 복사하고 imperative
     * shadow DOM(생성자/connectedCallback에서 attachShadow로 만든 것)은 복사하지 않으므로,
     * 새 인스턴스가 연결될 때 connectedCallback이 빈 shadowRoot를 보고 정상적으로 CM6를
     * 새로 만든다(`common/yona.ui.Toast.js`/`yona.ui.Dialog.js`가 이미 쓰는 cloneNode 기반
     * 재사용 관례와 동일한 기법).
     */
    function _remountEditor(){
        var elOldEditor = htElement.welCommentForm.querySelector("yona-markdown-editor");
        if(!elOldEditor){
            return;
        }
        var elNewEditor = elOldEditor.cloneNode(true);
        elOldEditor.replaceWith(elNewEditor);
        htElement.welCommentTextarea = htElement.welCommentForm.querySelector('[data-toggle="markdown-editor"] textarea');
    }

    /**
     * welTarget에 thread-id도 data-line도 없으면(=드래그 다중행 범위선택으로 만들어진
     * 컨텍스트 없는 임시 대상), data-line을 가진 실제 diff 행을 거슬러 올라가 찾는다.
     */
    function _getReviewFormTarget(welTarget){
        if(!welTarget.dataset.threadId && !welTarget.dataset.line){
            return _prevUntilDataLine(welTarget);
        }

        return welTarget;
    }

    function _prevUntilDataLine(welTarget){
        var el = welTarget.previousElementSibling;
        while(el && !el.matches("tr[data-line]")){
            el = el.previousElementSibling;
        }
        return el || welTarget;
    }

    function _getReviewFormPlace(welTarget, sPlacement){
        if(welTarget.dataset.threadId){
            return welTarget.closest(".comment-thread-wrap");
        }

        var elPlace = document.createElement("tr");
        elPlace.className = "comment-form";
        elPlace.innerHTML = '<td colspan="3" class="write-comment-form"></td>';

        if(sPlacement === "top"){
            welTarget.before(elPlace);
        } else {
            welTarget.after(elPlace);
        }

        return elPlace;
    }

    function _getReviewFormFieldData(welTarget, htBlockInfo){
        return !welTarget.dataset.threadId ?
                _getFormFieldsFromBlockInfo(htBlockInfo) :
                {"thread.id": welTarget.dataset.threadId};
    }

    /**
     * CodeCommentBlock의 htBlockInfo(nStartLine/sStartSide/...) 필드명을 CodeRangeRequest가
     * 기대하는 폼 필드명(startLine/startSide/...)으로 변환한다 - 헝가리안 접두문자(n/s/b) 한
     * 글자를 떼고 그다음 글자를 소문자로 바꾼다(legacy와 동일한 규칙).
     */
    function _getFormFieldsFromBlockInfo(htBlockInfo){
        var sNewKey;
        var htData = {};
        var aBlockWords = ["bIsReversed", "sStartType", "sEndType", "sPathA", "sPathB",
            "sPrevCommitId", "sCommitId", "sFilePath"];

        for(var sKey in htBlockInfo){
            if(aBlockWords.indexOf(sKey) > -1){
                continue;
            }

            sNewKey = sKey.substr(1,1).toLowerCase() + sKey.substring(2);
            htData[sNewKey] = htBlockInfo[sKey];
        }

        return htData;
    }

    function _setReviewFormFields(htData){
        var aInput = [];
        var welField, elField, sFieldName;
        var welForm = htElement.welCommentForm;

        for(sFieldName in htData){
            welField = welForm.querySelector('input[type="hidden"][name="' + sFieldName + '"]');

            if(!welField){
                elField = _getHiddenField(sFieldName, htData[sFieldName]);
                aInput.push(elField);
            } else {
                welField.value = htData[sFieldName];
            }
        }

        aInput.forEach(function(elInput){
            welForm.prepend(elInput);
        });
    }

    function _getHiddenField(sFieldName, sFieldValue){
        var elInput = document.createElement("INPUT");
        elInput.setAttribute("name", sFieldName);
        elInput.setAttribute("type", "hidden");
        elInput.value = sFieldValue;
        return elInput;
    }

    function _hide(){
        htElement.welCommentWrap.style.display = "none";
        var welFormWrap = htElement.welCommentWrap.closest("tr.comment-form");
        // P3-73: 위 _placeReviewForm()과 동일한 이유로 실제 이동이 필요할 때만 appendChild한다.
        if(htElement.welCommentWrap.parentElement !== htElement.welInitialParent){
            htElement.welInitialParent.appendChild(htElement.welCommentWrap);
        }
        if(welFormWrap){
            welFormWrap.remove();
        }

        window.dispatchEvent(new CustomEvent("CodeCommentBox:afterhide"));
    }

    function _toggleVisibility(welTarget, htOptions){
        if(_isVisible()){
            _hide();
        } else {
            _show(welTarget, htOptions);
        }
    }

    function _isVisible(){
        return !!(htElement.welCommentWrap &&
                htElement.welCommentWrap.style.display === "block");
    }

    function _getWrapHeight(){
        return htElement.welCommentWrap.offsetHeight;
    }

    function _getWrapOffset(){
        var rect = htElement.welCommentWrap.getBoundingClientRect();
        return {
            "top": rect.top + window.scrollY,
            "left": rect.left + window.scrollX
        };
    }

    // public interface
    return {
        "init"  : _init,
        "show"  : _show,
        "hide"  : _hide,
        "toggle": _toggleVisibility,
        "isVisible": _isVisible,
        "height": _getWrapHeight,
        "offset": _getWrapOffset
    };
})();
