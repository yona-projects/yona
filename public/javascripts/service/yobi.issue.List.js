/**
 * @(#)yobi.issue.List.js 2013.03.13
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://yobi.dev.naver.com/license
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
            yobi.ui.Select2(htElement.welSearchAuthorId);
            yobi.ui.Select2(htElement.welSearchAssigneeId);
            yobi.ui.Select2(htElement.welSearchMilestoneId);
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htElement.welSearchAuthorId.on("change", _onChangeSearchField);
            htElement.welSearchAssigneeId.on("change", _onChangeSearchField);
            htElement.welSearchMilestoneId.on("change", _onChangeSearchField);

            htElement.welSearchOrder.on("click", _onChangeSearchOrder);
            htElement.welSearchState.on("click", _onChangeSearchState);
            htElement.welFilter.on("click", _onClickSearchFilter);
            htElement.waLabels.on("click", _onChangeSearchLabel);

            htElement.welIssueWrap.on('change','[data-toggle="issue-checkbox"]',_onChangeIssueCheckBox);
        }

        /**
         * 이슈 목록에서 체크박스 선택시
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
         * 이슈 목록 정렬 기준 클릭시
         * 변경순, 날짜순, 댓글순... 등
         *
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
         * 이슈 목록 상단의 열림/닫힘 탭 클릭시
         * 선택한 상태값으로 이슈 검색
         *
         * @param weEvt
         * @private
         */
        function _onChangeSearchState(weEvt) {
            weEvt.preventDefault();

            $("input[name=state]").val($(this).attr("state"));

            htElement.welSearchForm.submit();
        }

        /**
         * 이슈 검색 폼에서 라벨 선택시 선택한 라벨로 검색
         * @param event
         * @private
         */
        function _onChangeSearchLabel(weEvt) {
            weEvt.preventDefault();

            yobi.Label.resetLabel($(this).attr('data-labelId'));

            htElement.welSearchForm.submit();
        }

        /**
         * 이슈 검색 항목 변경시 이벤트 핸들러
         * 등록자, 담당자, 마일스톤 select 의 change 이벤트 발생시
         *
         * @private
         */
        function _onChangeSearchField() {
            htElement.welSearchForm.submit();
        }

        /**
         * 검색필터 링크 클릭시 이벤트 핸들러
         * 이슈 목록 좌측 상단의 전체이슈, 나에게 할당된 이슈, 내가 작성한 이슈 링크
         *
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
