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
			htVar.oTypeahead = new hive.ui.FindUser("input[name=authorLoginId]");
		}
		
		/**
		 * initialize element
		 */
		function _initElement(htOptions){
			htElement.welForm = $("form.form-search");
			htElement.welFieldset = $("fieldset.properties");
			htElement.welAssigneeId  = htElement.welFieldset.find("button.active[data-assigneeId]");
			htElement.welMilestoneId = htElement.welFieldset.find("button.active[data-milestoneId]");
			htElement.welBtnOptions = htElement.welFieldset.find("div.controls button");
			htElement.welBtnAdvance = $(".btn-advanced");
			
			htElement.welPagination = $(htOptions.elPagination || "#pagination");
			
			htElement.welLabels = $("button.issue-label[data-color]");
		}
		
		/**
		 * attach event handlers
		 */
		function _attachEvent(){
			htElement.welForm.submit(_onSubmitForm);
			htElement.welBtnOptions.click(_onClickBtnOptions);
			htElement.welBtnAdvance.click(_onClickBtnAdvance);
		}
		
		/**
		 * on submit search form
		 */
		function _onSubmitForm(){
			// AssigneeId
			var sAssigneeId = htElement.welAssigneeId.attr("data-assigneeId")
			if(typeof sAssigneeId != "undefined"){
				htElement.welForm.append(_getHiddenInput("assigneeId", sAssigneeId));
			}
			
			// Milestone Id
			var sMilestoneId = htElement.welMilestoneId.attr("data-milestoneId");
			if(typeof sMilestoneId != "undefined"){
				htElement.welForm.append(_getHiddenInput("milesone", sMilestoneId));
			}
		}
		
		/**
		 * get HTMLInputElement <input type="hidden">
		 * @param {String} sName
		 * @param {String} sValue
		 * @returns {HTMLInputElement}
		 */
		function _getHiddenInput(sName, sValue){
			return $('<input type="hidden" name="' + sName + '" value="' + sValue + '">')
		}
		
		/**
		 * on click button
		 */
		function _onClickBtnOptions(e){
	        var welTarget = $(e.target || e.srcElement || e.originalTarget);
	        if (welTarget.hasClass("active")) {
	        	welTarget.removeClass("active");
	        	return false;
	        }
		}

		function _onClickBtnAdvance(){
			$(".inner").toggleClass("advanced");
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
			
			htElement.welLabels.each(function(){
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
