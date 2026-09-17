/**
 * 페이지당 단 하나 존재하는 "#review-form" DOM을 필요한 위치(새 라인/범위 댓글 자리,
 * 또는 기존 스레드)로 옮겨 재사용하는 플로팅 코드리뷰 댓글 상자다 - 매번 새 폼을
 * 만들어 삽입하지 않는다.
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

        // #review-form 자리가 <yona-review-form>이면 show/hide/toggle/isVisible/height/offset을
        // 전부 그 커스텀 엘리먼트에 위임한다 - 언제/어디에 뜰지 결정하는 트리거 로직
        // (yona.code.Diff.js 소유)은 그대로 두고 공개 계약만 유지한다.
        if(htElement.welCommentWrap && htElement.welCommentWrap.tagName.toLowerCase() === "yona-review-form"){
            htVar.bIsVueReviewForm = true;
            return;
        }

        htElement.welCommentForm = htElement.welCommentWrap.querySelector("form");
        htElement.welCommentTextarea = htElement.welCommentForm.querySelector('[data-toggle="markdown-editor"] textarea');
        htElement.welInitialParent = htElement.welCommentWrap.parentElement;
    }

    function _attachEvent(){
        if(htVar.bIsVueReviewForm){
            return;
        }

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

        if(htVar.bIsVueReviewForm){
            htElement.welCommentWrap.show(welTarget, htOptions);
            return;
        }

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
        // appendChild()는 대상이 이미 같은 부모의 마지막 자식이어도 내부적으로 disconnect
        // 후 reconnect를 일으키므로, 실제로 위치가 바뀔 때만 호출한다. 다만 새 댓글(스레드
        // 답글이 아닌 경우)은 _getReviewFormPlace()가 매번 새 <tr>을 만들어 이 가드로도
        // 이동 자체는 피할 수 없다 - 그 경우를 위해 _remountEditor()로 별도 대응한다.
        if(htElement.welCommentWrap.parentElement !== welWriteCommentForm){
            welWriteCommentForm.appendChild(htElement.welCommentWrap);
            _remountEditor();
        }
    }

    /**
     * <yona-markdown-editor>(벤더, 수정 불가)의 connectedCallback은 이미 shadowRoot가
     * 있으면 재초기화를 건너뛴다 - review-form을 appendChild로 옮기면 이 커스텀 엘리먼트도
     * disconnect/reconnect를 다시 타면서 기존 CodeMirror 뷰가 destroy된 뒤 그 가드 때문에
     * 다시 만들어지지 않는다(실측 확인 - 이동 후 편집 영역이 완전히 빈 채로 남음).
     * cloneNode(true)는 light DOM 자식(textarea)만 복사하고 imperative shadow DOM은
     * 복사하지 않으므로, 이동 직후 새 인스턴스로 교체하면 connectedCallback이 빈 shadowRoot를
     * 보고 CM6를 정상적으로 다시 만든다(yona.ui.Toast.js/yona.ui.Dialog.js와 동일한 기법).
     */
    function _remountEditor(){
        var elOldEditor = htElement.welCommentForm.querySelector("yona-markdown-editor");
        if(!elOldEditor){
            return;
        }
        var elNewEditor = elOldEditor.cloneNode(true);
        elOldEditor.replaceWith(elNewEditor);
        htElement.welCommentTextarea = htElement.welCommentForm.querySelector('[data-toggle="markdown-editor"] textarea');
        _makeEditorResizable(elNewEditor);
    }

    /**
     * CSS(`::part(editor)`)로 `.editor-wrapper`에 `resize:vertical` 핸들을 노출했지만,
     * 그 안의 실제 CodeMirror 뷰(`.cm-editor`)는 고정 `min-height`에만 반응할 뿐 커진
     * 부모 크기를 자동으로 따라가지 않는다(CM6는 명시적 height가 없으면 내용 기준
     * auto-grow라 부모 리사이즈를 알 방법이 없음 - 실측 확인). `.cm-editor`는 `::part()`로
     * 직접 겨냥할 수 없어, ResizeObserver로 `.editor-wrapper` 크기 변화를 감지해
     * `.cm-editor`의 min-height를 JS로 동기화한다.
     *
     * ResizeObserver는 observe() 호출 시 "현재" 크기로 콜백을 즉시 한 번 실행하는데
     * (스펙 규정 동작), 이를 그대로 반영하면 사용자가 리사이즈하지도 않았는데 초기 높이가
     * 인라인 min-height로 고정돼 CSS가 의도한 축소된 기본 높이가 무력화된다(실측 확인).
     * 최초 1회는 건너뛰고 실제로 크기가 변한 이후부터만 동기화한다.
     */
    function _makeEditorResizable(elEditor){
        if(typeof ResizeObserver === "undefined" || !elEditor.shadowRoot){
            return;
        }
        var elWrapper = elEditor.shadowRoot.querySelector(".editor-wrapper");
        var elCm = elEditor.shadowRoot.querySelector(".cm-editor");
        if(!elWrapper || !elCm){
            return;
        }
        var bSkipFirstCallback = true;
        new ResizeObserver(function(){
            if(bSkipFirstCallback){
                bSkipFirstCallback = false;
                return;
            }
            elCm.style.minHeight = elWrapper.getBoundingClientRect().height + "px";
        }).observe(elWrapper);
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
        if(htVar.bIsVueReviewForm){
            htElement.welCommentWrap.hide();
            return;
        }

        htElement.welCommentWrap.style.display = "none";
        var welFormWrap = htElement.welCommentWrap.closest("tr.comment-form");
        // 위 _placeReviewForm()과 동일한 이유로 실제 이동이 필요할 때만 appendChild한다.
        if(htElement.welCommentWrap.parentElement !== htElement.welInitialParent){
            htElement.welInitialParent.appendChild(htElement.welCommentWrap);
        }
        if(welFormWrap){
            welFormWrap.remove();
        }

        // 닫힐 때마다 에디터를 새로 만들어 초안을 버린다 - 다음 show()가 매번 새 <tr>을
        // 만드는 것에 기대지 않고, 닫히는 시점에 명시적으로 보장한다.
        _remountEditor();

        window.dispatchEvent(new CustomEvent("CodeCommentBox:afterhide"));
    }

    function _toggleVisibility(welTarget, htOptions){
        if(htVar.bIsVueReviewForm){
            htElement.welCommentWrap.toggle(welTarget, htOptions);
            return;
        }

        if(_isVisible()){
            _hide();
        } else {
            _show(welTarget, htOptions);
        }
    }

    function _isVisible(){
        if(htVar.bIsVueReviewForm){
            return htElement.welCommentWrap.isVisible();
        }
        return !!(htElement.welCommentWrap &&
                htElement.welCommentWrap.style.display === "block");
    }

    function _getWrapHeight(){
        if(htVar.bIsVueReviewForm){
            return htElement.welCommentWrap.height();
        }
        return htElement.welCommentWrap.offsetHeight;
    }

    function _getWrapOffset(){
        if(htVar.bIsVueReviewForm){
            return htElement.welCommentWrap.offset();
        }
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
