/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Jihan Kim
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

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

        /**
         * initialize
         */
        function _init(htOptions){
            _initVar(htOptions || {})
            _initElement(htOptions || {});
            _attachEvent();
            _initPagination();
            _setLabelColor();
        }

        /**
         * initialize variables except element
         */
        function _initVar(htOptions){
            htVar.nTotalPages = htOptions.nTotalPages || 1;
        }

        /**
         * initialize element
         */
        function _initElement(htOptions){
            htElement.welFilter = htOptions.welFilter;
            htElement.welSearchForm = htOptions.welSearchForm;
            htElement.welSearchOrder = htOptions.welSearchOrder;
            htElement.welSearchState = htOptions.welSearchState;

            htElement.welContainer  = $(".inner");
            htElement.welBtnAdvance = $(".btn-advanced");
            htElement.welPagination = $(htOptions.elPagination || "#pagination");

            htElement.waLabels = $("a.issue-label[data-color]"); // 목록 > 라벨

            htElement.welMassUpdateForm = htOptions.welMassUpdateForm;
            htElement.welMassUpdateButtons = htOptions.welMassUpdateButtons;
            htElement.welDeleteButton = htOptions.welDeleteButton;
            htElement.waCheckboxes = $(htVar.sIssueCheckBoxesSelector);

            htElement.welIssueWrap = $('.issue-list-wrap');

            htElement.welSearchAuthorId = $("#authorId");
            htElement.welSearchAssigneeId = $("#assigneeId");
            htElement.welSearchMilestoneId = $("#milestoneId");

            // ui.Select2.js required
            if(typeof yobi.ui.Select2 === "function"){
                yobi.ui.Select2(htElement.welSearchAuthorId);
                yobi.ui.Select2(htElement.welSearchAssigneeId);
                yobi.ui.Select2(htElement.welSearchMilestoneId);
            }

            if(htOptions.welIssueDueDate){
                htElement.welIssueDueDate = htOptions.welIssueDueDate;
            }

            if(typeof yobi.ui.Calendar === "function"){
                yobi.ui.Calendar(htElement.welIssueDueDate);
            }
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welSearchAuthorId.on("change", _onChangeSearchField);
            htElement.welSearchAssigneeId.on("change", _onChangeSearchField);
            htElement.welSearchMilestoneId.on("change", _onChangeSearchField);
            if(htOptions.welIssueDueDate){
                htElement.welIssueDueDate.on("change", _onChangeSearchField);
            }

            htElement.welSearchOrder.on("click", _onChangeSearchOrder);
            htElement.welSearchState.on("click", _onChangeSearchState);
            htElement.welFilter.on("click", _onClickSearchFilter);
            htElement.waLabels.on("click", _onChangeSearchLabel);

            htElement.welIssueWrap.on('change','[data-toggle="issue-checkbox"]',_onChangeIssueCheckBox);
        }

        /**
         * @private
         */
        function _onChangeIssueCheckBox() {
            var welCheckBox = $(this)
            var welItemWrap = $('#issue-item-' + welCheckBox.data('issueId'));

            if(welCheckBox.is(':checked')){
                welItemWrap.addClass('active');
            } else {
                welItemWrap.removeClass('active');
            }
        }

        /**
         * @param weEvt
         * @private
         */
        function _onChangeSearchOrder(weEvt) {
            weEvt.preventDefault();

            $("input[name=orderBy]").val($(this).attr("orderBy"));
            $("input[name=orderDir]").val($(this).attr("orderDir"));

            htElement.welSearchForm.submit();
        }

        /**
         * @param weEvt
         * @private
         */
        function _onChangeSearchState(weEvt) {
            weEvt.preventDefault();

            $("input[name=state]").val($(this).attr("state"));

            htElement.welSearchForm.submit();
        }

        /**
         * @param event
         * @private
         */
        function _onChangeSearchLabel(weEvt) {
            weEvt.preventDefault();

            yobi.Label.resetLabel($(this).attr('data-labelId'));

            htElement.welSearchForm.submit();
        }

        /**
         * @private
         */
        function _onChangeSearchField() {
            htElement.welSearchForm.submit();
        }

        /**
         * @param weEvt
         * @private
         */
        function _onClickSearchFilter(weEvt) {
            weEvt.preventDefault();

            htElement.welSearchAuthorId.val($(this).attr("authorId"));
            htElement.welSearchAssigneeId.val($(this).attr("assigneeId"));
            htElement.welSearchMilestoneId.val($(this).attr("milestoneId"));

            htElement.welSearchForm.submit();
        }

        /**
         * update Pagination
         * @requires yobi.Pagination
         */
        function _initPagination(){
            yobi.Pagination.update(htElement.welPagination, htVar.nTotalPages);
        }

        /**
         * update Label color
         */
        function _setLabelColor(){
            var welLabel, sColor;

            htElement.waLabels.each(function(){
                welLabel = $(this);
                sColor = welLabel.data("color");
                welLabel.css("background-color", sColor);
                welLabel.css("color", $yobi.getContrastColor(sColor));
            });

            welLabel = sColor = null;
        }

        _init(htOptions);
    };

})("yobi.issue.List");
