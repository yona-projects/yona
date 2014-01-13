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

            htVar.oSearchAuthor    = new yobi.ui.Dropdown({"elContainer": htOptions.welSearchAuthor});
            htVar.oSearchAssignee  = new yobi.ui.Dropdown({"elContainer": htOptions.welSearchAssignee});
            htVar.oSearchMilestone = new yobi.ui.Dropdown({"elContainer": htOptions.welSearchMilestone});

        }
        
        /**
         * initialize element
         */
        function _initElement(htOptions){
            
            htElement.welSearchForm = htOptions.welSearchForm;
            htElement.welFilter = htOptions.welFilter;

            htElement.welSearchOrder = htOptions.welSearchOrder;
            htElement.welSearchState = htOptions.welSearchState;
            
            htElement.welContainer  = $(".inner");
            htElement.welBtnAdvance = $(".btn-advanced");        
            htElement.welPagination = $(htOptions.elPagination || "#pagination");

            htElement.waLabels      = $("a.issue-label[data-color]"); // 목록 > 라벨

            htElement.welMassUpdateForm = htOptions.welMassUpdateForm;
            htElement.welMassUpdateButtons = htOptions.welMassUpdateButtons;
            htElement.welDeleteButton = htOptions.welDeleteButton;
            htElement.waCheckboxes = $(htVar.sIssueCheckBoxesSelector);

            htElement.welIssueWrap = $('.issue-list-wrap');   
        }

        /**
         * attach event handlers
         */
        function _attachEvent(){
            htVar.oSearchAuthor.onChange(_onChangeSearchField);
            htVar.oSearchAssignee.onChange(_onChangeSearchField);
            htVar.oSearchMilestone.onChange(_onChangeSearchField);

            htElement.welSearchOrder.each(function(i, el) {
                $(el).click(_onChangeSearchOrder);
            });
            
            htElement.welSearchState.each(function(i, el) {
                $(el).click(_onChangeSearchState);
            });
            
            htElement.waLabels.each(function(i, el) {
                $(el).click(_onChangeSearchLabel);
            });
            
            if(htElement.welFilter) htElement.welFilter.each(function(i, el) {
                $(el).click(_onClickSearchFilter);
            });

            htElement.welIssueWrap.on('change','[data-toggle="issue-checkbox"]',_onChangeIssueCheckBox);

        }

        function _onChangeIssueCheckBox() {
            var welItemWrap = $('#issue-item-'+$(this).data('issueId'));
            if($(this).is(':checked')) welItemWrap.addClass('active');
            else welItemWrap.removeClass('active');
        }

        function _onChangeSearchOrder(event) {
            event.preventDefault();
            $("input[name=orderBy]").val($(this).attr("orderBy"));
            $("input[name=orderDir]").val($(this).attr("orderDir"));
            htElement.welSearchForm.submit();
        }
        
        function _onChangeSearchState(event) {
            event.preventDefault();
            $("input[name=state]").val($(this).attr("state"));
            htElement.welSearchForm.submit();
        }
        
        function _onChangeSearchLabel(event) {
            event.preventDefault();
            yobi.Label.resetLabel($(this).attr('data-labelId'));
            htElement.welSearchForm.submit();
        }

        function _onChangeSearchField() {
            htElement.welSearchForm.submit();
        }

        function _onClickSearchFilter(event) {
            event.preventDefault();
            $("#authorId").val($(this).attr("authorId"));
            $("#assigneeId").val($(this).attr("assigneeId"));
            $("#milestoneId").val($(this).attr("milestoneId"));
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
