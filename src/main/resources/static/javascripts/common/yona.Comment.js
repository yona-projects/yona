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
 * 처리한다. 원칙대로 삭제하지 않고 완전 동치 보장 하에 vanilla로 전환했다 - 단,
 * _openDeleteModal()의 .requestAs()는 라운드1에서 yona.project.Delete.js에 대해 내린 판단과
 * 동일하게 미룬다(jquery.requestAs.js 플러그인 자체가 라운드10 "코어 라이브러리 제거" 대상).
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
     * P3-70 라운드3: .requestAs()는 jquery.requestAs.js 플러그인 호출이라 라운드10(코어
     * 라이브러리 제거)까지 전환을 미룬다(라운드1의 yona.project.Delete.js와 동일 판단) - 이
     * 함수 전체가 그 호출에 강하게 결합돼 있어 그대로 둔다. .modal()만 기존 캠페인에서 이미
     * 확립된 네이티브 <dialog> 관례로 전환한다(round2의 organization.Member.js와 동일).
     */
    function _openDeleteModal() {
        window.jQuery(htElement.welDeleteConfirmBtn)
            .data('requestUri', this.getAttribute('data-request-uri'))
            .data('requestMethod', 'delete')
            .requestAs();

        if(htElement.welDeleteModal && typeof htElement.welDeleteModal.showModal === 'function'){
            htElement.welDeleteModal.showModal();
        }
    }

    return {
        "init"  : _init,
    };
})();
