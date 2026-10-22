
(function(ns){

    var oNS = $yobi.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htElement = {};
        var htOrderMap = {"asc": "desc", "desc": "asc"};

        function _initImplicitTitlePrefix() {
            $(".title-prefix").on("click", function(){
                var filterInput = $("input[name*='filter']");
                filterInput.val($(this).text());
                filterInput.closest("form").submit();
            });
        }

        /**
         * initialize
         * @param {Hash Table} htOptions
         */
        function _init(htOptions){
            _initElement(htOptions || {});
            _attachEvent();
            _initPagination(htOptions);
            _initImplicitTitlePrefix();
            _listHoverEffect();
            _initTwoColumnMode();
        }

        function _listHoverEffect(){
            $(".post-list-wrap > .post-item").not(".notice-wrap > .post-item").hover(function () {
                $(this).css("background-color", "#fafafa");
            }, function () {
                $(this).css("background-color", "#fff");
            });
        }

        /**
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.welForm = $(htOptions.sOptionForm || "#option_form");
            htElement.welInputOrderBy = htElement.welForm.find("input[name=orderBy]");
            htElement.welInputOrderDir = htElement.welForm.find("input[name=orderDir]");
            htElement.welInputPageNum = htElement.welForm.find("input[name=pageNum]");
            htElement.welIssueWrap = $(htOptions.welIssueWrap || '.post-list-wrap');

            htElement.welPages = $(htOptions.sQueryPages || "#pagination a");
            htElement.welPagination = $(htOptions.elPagination || '#pagination');
        }

        /**
         * attach event handlers
         */
        function _attachEvent() {
            htElement.welPages.click(_onClickPage);
            htElement.welIssueWrap.on("click", "a[data-label-id][data-category-id]", _onClickLabelOnList);
        }

        /**
         * onClick PageNum
         */
        function _onClickPage(){
            htElement.welInputPageNum.val($(this).attr("pageNum"));
            htElement.welForm.submit();
            return false;
        }

        /**
         * "click" event handler of labels on the list.
         * Add clicked label to search form condition.
         *
         * @param event
         * @private
         */
        function _onClickLabelOnList(weEvt) {
            weEvt.preventDefault();

            var link = $(this);
            var targetQuery = "[data-search=labelIds]";
            var target = htElement.welForm.find(targetQuery);

            var labelId = link.data("labelId");
            var newValue;

            if(target.prop("multiple")){
                newValue = (target.val() || []);
                newValue.push(labelId);
            } else {
                newValue = labelId;
            }

            target.data("select2").val(newValue, true); // triggerChange=true
            console.log("labelId", labelId);
        }

        function _initPagination(htOptions){
            yobi.Pagination.update(htElement.welPagination, htOptions.nTotalPages);
        }

        _init(htOptions);
    };

})("yobi.board.List");
