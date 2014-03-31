/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author kjkmadness
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
        htElement.welContainer.on('click', '[data-toggle="comment-edit"]', function _editFormShowToggle(){
            var editformId = $(this).data('comment-editform-id');
            $('#' + editformId).toggle();
            $(this).parents('.media-body').toggle();
        });
        htElement.welContainer.on('click', '.ybtn-cancel', function _hideEditForm(){
            $(this).parents('.comment-update-form').toggle();
            var commentBodyId = $(this).data('comment-body-id');
            $('#' + commentBodyId).toggle();
        });
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
