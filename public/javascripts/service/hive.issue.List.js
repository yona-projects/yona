/**
 * @(#)hive.issue.List.js 2013.03.13
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */

(function(ns){
	
	var oNS = $hive.createNamespace(ns);
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
			
			_initLabel(htOptions.htOptLabel);
			
			_initPagination();
			_setLabelColor();
		}
		
		/**
		 * initialize variables except element
		 */
		function _initVar(htOptions){
			htVar.nTotalPages = htOptions.nTotalPages || 1;
			htVar.oTypeahead = new hive.ui.Typeahead("input[name=authorLoginId]", {
				"sActionURL": "/users"
			});
		}
		
		/**
		 * initialize element
		 */
		function _initElement(htOptions){
			htElement.welContainer  = $(".inner");
			htElement.welBtnAdvance = $(".btn-advanced");		
			htElement.welPagination = $(htOptions.elPagination || "#pagination");

			htElement.waLabels    = $("button.issue-label[data-color]"); // 목록 > 라벨
		}
		
		/**
		 * attach event handlers
		 */
		function _attachEvent(){
			htElement.welBtnAdvance.click(_onClickBtnAdvance);
		}
		
		/**
		 * 상세검색 영역 토글
		 */
		function _onClickBtnAdvance(){
			htElement.welContainer.toggleClass("advanced");
	   	}

		/**
		 * initialize hive.Label
		 * @param {Hash Table} htOptions
		 */
		function _initLabel(htOptions){		
			hive.Label.init(htOptions);
		}
		
		/**
		 * update Pagination
		 * @requires hive.Pagination
		 */
		function _initPagination(){
			hive.Pagination.update(htElement.welPagination, htVar.nTotalPages);
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
				welLabel.css("color", $hive.getContrastColor(sColor));
		    });
			
			welLabel = sColor = null;
		}

		_init(htOptions);
	};
	
})("hive.issue.List");
