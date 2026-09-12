/**
 * Yona, Project Hosting SW
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
(function(ns){

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){
        var htVar = {};
        var htElement = {};

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
            htElement.welPagination = htVar.elPagination || document.querySelector("#pagination");
            htElement.welIssueListWrap = document.querySelector('.issue-list-wrap');
            htElement.welSearchForm = htVar.welSearchForm;
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welIssueListWrap.addEventListener('click', function(e){
                var match = e.target.closest('[data-toggle="filter"]');
                if(match && htElement.welIssueListWrap.contains(match)){
                    _onChangeFilter.call(match, e);
                }
            });
            htElement.welIssueListWrap.addEventListener('click', function(e){
                var match = e.target.closest('[data-toggle="order"]');
                if(match && htElement.welIssueListWrap.contains(match)){
                    _onChangeOrder.call(match, e);
                }
            });
        }

        function _onChangeFilter(weEvent) {
            weEvent.preventDefault();

            var welElement = this;
            if(welElement.dataset.type === 'state') {
                document.querySelectorAll("input[name='state']").forEach(function(el){ el.value = welElement.dataset.value; });
            } else {
                var sAuthorId = (welElement.dataset.type === 'authorId') ? welElement.dataset.value : '';
                var sParticipantId = (welElement.dataset.type === 'participantId') ? welElement.dataset.value : '';

                document.querySelectorAll("input[name='authorId']").forEach(function(el){ el.value = sAuthorId; });
                document.querySelectorAll("input[name='participantId']").forEach(function(el){ el.value = sParticipantId; });
            }

            htElement.welSearchForm.submit();
        }

        function _onChangeOrder(weEvent) {
            weEvent.preventDefault();

            var welElement = this;
            var sOrderField = welElement.dataset.field;
            var sOrderValue = welElement.dataset.value;

            document.querySelectorAll("input[name='orderBy']").forEach(function(el){ el.value = sOrderField; });
            document.querySelectorAll("input[name='orderDir']").forEach(function(el){ el.value = sOrderValue; });
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
    }
})("yona.review.List");

