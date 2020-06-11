/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

yobi.Comment = (function(){
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
        htElement.welContainer = $(htOptions.sContainer || '#comments');
        htElement.welDeleteModal = $(htOptions.sDeleteModal || '#comment-delete-modal');
        htElement.welDeleteConfirmBtn = $(htOptions.sDeleteConfirm || '#comment-delete-confirm');
        htElement.commentEditforms = $('[id^=comment-editform-]');
    }

    /**
     * attach event handler
     */
    function _attachEvent() {
        htElement.welContainer.on('click', '[data-toggle="comment-delete"]', _openDeleteModal);
        htElement.welContainer.on('click', '[data-toggle="comment-edit"]', _toggleEditForm);
        htElement.welContainer.on('click', '.ybtn-cancel', _toggleEditForm);

        htElement.commentEditforms.each(function (i, item) {
            temporarySaveHandler($(item).find('textarea'), false);
        });
    }

    function _toggleEditForm(){
        var commentId = $(this).data("commentId");

        $('#comment-editform-' + commentId).toggle();
        $('#comment-body-' + commentId).toggle();
        $("[data-toggle='popover']").popover();
        $(".add-a-comment").hide();
        autosize.update($('textarea'));
    }

    /**
     * open delete modal
     */
    function _openDeleteModal() {
        htElement.welDeleteConfirmBtn
            .data('requestUri', $(this).data('requestUri'))
            .data('requestMethod', 'delete')
            .requestAs();
        htElement.welDeleteModal.modal();
    }

    return {
        "init"  : _init,
    };
})();
