/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * 이 파일은 어느 템플릿에서도 로드되지 않는 죽은 코드다(<script src>도
 * $yona.loadModule("Comment") 호출도 없음) - 실제 댓글 삭제/수정 UI는
 * commentDeleteModal.html의 인라인 스크립트와 yona.SubComment.js가 처리한다.
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

        // 별도 CSS 기본값이 없는 일반 컨테이너라 getComputedStyle로 현재 표시 여부를 판단해 토글한다.
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
     * 원본은 jQuery .data() 캐시에 requestUri/requestMethod를 심어두고 인자 없이
     * .requestAs()를 호출해 그 캐시를 읽었다. $yona.requestAs는 htOptions로 직접 넘기면
     * data 속성보다 우선 적용되므로 그 간접 단계 없이도 동치다.
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
