/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * P3-70 라운드3: 이 파일은 어느 템플릿에서도 로드되지 않는 죽은 코드다(<script src>도
 * $yona.loadModule("Comment") 호출도 저장소 전체에 0건, grep으로 재확인). 실제 댓글 삭제/수정
 * UI는 common/commentDeleteModal.html의 별도 인라인 스크립트와 common/yona.SubComment.js가
 * 처리한다. 원칙대로 삭제하지 않고 완전 동치 보장 하에 vanilla로 전환했다(라운드10에서
 * _openDeleteModal()의 .requestAs()도 $yona.requestAs로 마저 전환해 완전히 vanilla가 됐다).
 */
yona.Comment = (function(){
    var htElement = {};

    /**
     * initialize
     * @param {Hash Table} htOptions
     */
    function _init(htOptions){
        _initElement(htOptions || {});
        _attachEvent();
    }

    /**
     * initialize element variables
     */
    function _initElement(htOptions) {
        htElement.welContainer = document.querySelector(htOptions.sContainer || '#comments');
        htElement.welDeleteModal = document.querySelector(htOptions.sDeleteModal || '#comment-delete-modal');
        htElement.welDeleteConfirmBtn = document.querySelector(htOptions.sDeleteConfirm || '#comment-delete-confirm');
        htElement.commentEditforms = document.querySelectorAll('[id^=comment-editform-]');
    }

    /**
     * attach event handler
     */
    function _attachEvent() {
        if(htElement.welContainer){
            htElement.welContainer.addEventListener('click', function(weEvt){
                var target;

                target = weEvt.target.closest('[data-toggle="comment-delete"]');
                if(target && htElement.welContainer.contains(target)){
                    _openDeleteModal.call(target);
                    return;
                }

                target = weEvt.target.closest('[data-toggle="comment-edit"]');
                if(target && htElement.welContainer.contains(target)){
                    _toggleEditForm.call(target);
                    return;
                }

                target = weEvt.target.closest('.ybtn-cancel');
                if(target && htElement.welContainer.contains(target)){
                    _toggleEditForm.call(target);
                }
            });
        }

        htElement.commentEditforms.forEach(function (item) {
            temporarySaveHandler(item.querySelector('textarea'), false);
        });
    }

    function _toggleEditForm(){
        var commentId = this.dataset.commentId;

        // .comment-editform-*/.comment-body-*는 별도 CSS 기본값이 없는 일반 컨테이너라
        // (yona.SubComment.js의 .child-comment-input-form/.add-a-comment 케이스와 동일한
        // 관례) getComputedStyle로 현재 표시 여부를 판단해 토글한다.
        var editForm = document.getElementById('comment-editform-' + commentId);
        if(editForm){
            editForm.style.display = (getComputedStyle(editForm).display === 'none') ? '' : 'none';
        }
        var body = document.getElementById('comment-body-' + commentId);
        if(body){
            body.style.display = (getComputedStyle(body).display === 'none') ? '' : 'none';
        }

        $yona.initHoverPopovers("[data-toggle='popover']");

        document.querySelectorAll('.add-a-comment').forEach(function(el){
            el.style.display = 'none';
        });
        autosize.update(document.querySelectorAll('textarea'));
    }

    /**
     * open delete modal
     *
     * P3-70 라운드10: jquery.requestAs.js 플러그인 호출을 $yona.requestAs로 전환했다 - 원본은
     * jQuery `.data()` 내부 캐시에 requestUri/requestMethod를 먼저 심어두고 인자 없이
     * `.requestAs()`를 호출해 그 캐시를 읽게 했지만(welDeleteConfirmBtn 자체엔 원래
     * data-request-* 속성이 없다), $yona.requestAs는 htOptions로 직접 넘기면 동일하게
     * 우선 적용되므로(_getRequestOptions 우선순위: htOptions > data 속성) 그 간접 단계를
     * 생략해도 완전히 동치다. .modal()은 기존 캠페인에서 이미 확립된 네이티브 <dialog>
     * 관례로 전환한다(round2의 organization.Member.js와 동일).
     */
    function _openDeleteModal() {
        $yona.requestAs(htElement.welDeleteConfirmBtn, {
            "sMethod": "delete",
            "sHref"  : this.getAttribute('data-request-uri')
        });

        if(htElement.welDeleteModal && typeof htElement.welDeleteModal.showModal === 'function'){
            htElement.welDeleteModal.showModal();
        }
    }

    return {
        "init"  : _init,
    };
})();
