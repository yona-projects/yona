/**
 * @(#)hive.board.List.js 2013.03.11
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://hive.dev.naver.com/license
 */

(function(ns){

	var oNS = $hive.createNamespace(ns);
	oNS.container[oNS.name] = function(htOptions){

		var htElement = {};
    	var htOrderMap = {"asc": "desc", "desc": "asc"};

		/**
		 * initialize
		 * @param {Hash Table} htOptions
		 */
		function _init(htOptions){
			_initElement(htOptions || {});
			_attachEvent();

			_initPagination(htOptions);
		}

		/**
		 * initialize element variables
		 */
		function _initElement(htOptions){
			htElement.welForm = $(htOptions.sOptionForm || "#option_form");
			htElement.welInputOrderBy = htElement.welForm.find("input[name=orderBy]");
			htElement.welInputOrderDir = htElement.welForm.find("input[name=orderDir]");
			htElement.welInputPageNum = htElement.welForm.find("input[name=pageNum]");

			htElement.welFilter = $(htOptions.sQueryFilter || "#order a");
			htElement.welPages = $(htOptions.sQueryPages || "#pagination a");
			htElement.welPagination = $(htOptions.elPagination || '#pagination');
		}

		/**
		 * attach event handlers
		 */
        function _attachEvent() {
            htElement.welFilter.click(_onClickFilter);
            htElement.welPages.click(_onClickPage);
        }

        /**
         * onClick filter
         */
        function _onClickFilter(){
            var orderBy = $(this).attr("data-orderBy");
            var orderDir = $(this).attr("data-orderDir");

            htElement.welInputOrderBy.val(orderBy);
            htElement.welInputOrderDir.val(orderDir);

            htElement.welForm.submit();
            return false;
        }

        /**
         * onClick PageNum
         */
        function _onClickPage(){
        	htElement.welInputPageNum.val($(this).attr("pageNum"));
        	htElement.welForm.submit();
            return false;
        }


        function _initPagination(htOptions){
        	hive.Pagination.update(htElement.welPagination, htOptions.nTotalPages);
        }

        _init(htOptions);
	};

})("hive.board.List");

/*
nforge.namespace("board");
nforge.board.list = function() {
    var that = {
        "init" : function() {
            that.setUpEventListener();
        },

        "setUpEventListener" : function() {
            var $headers = $("#order a");
            $headers.click(that.onHeader);

            var $pagination = $("#pagination a");
            $pagination.click(that.onPager);
        },

        "onHeader" : function() {
            var key = $(this).attr("key");
            var $input = $("#option_form input[name=key]");
            if (key !== $input.val()) {
                $input.val(key)
            } else {
                $input = $("#option_form input[name=order]");
                if ($input.val() === "desc"){
                	$input.val("asc");
                } else if ($input.val() === "asc") {
                	$input.val("desc");
                }
            }
            $("#option_form").submit();
            return false;
        },

        "onPager" : function() {
            var $input = $("#option_form input[name=pageNum]");
            $input.val($(this).attr("pageNum"));
            $("#option_form").submit();
            return false;
        }
    };

    return that;
};
*/