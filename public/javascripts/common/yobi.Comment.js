/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Jungkook Kim
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
    }

    /**
     * attach event handler
     */
    function _attachEvent() {
        htElement.welContainer.on('click', '[data-toggle="comment-delete"]', _openDeleteModal);
        htElement.welContainer.on('click', '[data-toggle="comment-edit"]', _toggleEditForm);
        htElement.welContainer.on('click', '.ybtn-cancel', _toggleEditForm);
    }

    function _toggleEditForm(){
        var commentId = $(this).data("commentId");

        $('#comment-editform-' + commentId).toggle();
        $('#comment-body-' + commentId).toggle();
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
