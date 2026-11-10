/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Deokhong Kim
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

import { register } from "../namespace.js";

const List = function(htOptions){
    let htVar = {};
    const htElement = {};

    /**
     * initialize
     */
    function _init(htOptions){
        _initVar(htOptions || {})
        _initElement();
        _attachEvent();
        _initPagination();
    }

    function _initVar(htOptions) {
        htVar = htOptions;
        htVar.nTotalPages = htOptions.nTotalPages || 1;
    }

     /**
     * initialize element
     */
    function _initElement(){
        htElement.welPagination = $(htVar.elPagination || "#pagination");
        htElement.welIssueListWrap = $('.issue-list-wrap');
        htElement.welSearchForm = htVar.welSearchForm;
    }

    /**
     * attach event handlers
     */
    function _attachEvent(){
        htElement.welIssueListWrap.on('click','[data-toggle="filter"]', _onChangeFilter);
        htElement.welIssueListWrap.on('click','[data-toggle="order"]', _onChangeOrder);
    }

    function _onChangeFilter(weEvent) {
        weEvent.preventDefault();

        const welElement = $(this);
        if(welElement.data('type') === 'state') {
            $("input[name='state']").val(welElement.data('value'));
        } else {
            const sAuthorId = (welElement.data('type') === 'authorId') ? welElement.data('value') : '';
            const sParticipantId = (welElement.data('type') ==='participantId') ? welElement.data('value') : '';

            $("input[name='authorId']").val(sAuthorId);
            $("input[name='participantId']").val(sParticipantId);
        }

        htElement.welSearchForm.submit();
    }

    function _onChangeOrder(weEvent) {
        weEvent.preventDefault();

        const welElement = $(this);
        const sOrderField = welElement.data('field');
        const sOrderValue = welElement.data('value');

        $("input[name='orderBy']").val(sOrderField);
        $("input[name='orderDir']").val(sOrderValue);
        htElement.welSearchForm.submit();
    }

    /**
     * update Pagination
     * @requires yona.Pagination
     */
    function _initPagination(){
        yona.Pagination.update(htElement.welPagination, htVar.nTotalPages);
    }

    _init(htOptions);
};

export default List;

register("yona.review.List", List);
