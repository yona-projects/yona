/**
 * @(#)hive.project.New.js 2013.03.18
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
	
		function _init(htOptions){
			_initVar(htOptions);
			_initElement(htOptions);
			_attachEvent();
			
			_initFormValidator();
		}
		
		/**
		 * initialize variables
		 */
		function _initVar(htOptions){
			htVar.sFormName = htOptions.sFormName || "newproject";

		}
		
		/**
		 * initialize element
		 */
		function _initElement(htOptions){
			htElement.welInputVCS = $("#vcs"); // input type="hidden"
			htElement.welBtnVCSSelected = $("#vcs_msg"); // <button data-toggle="dropdown">
			htElement.aVCSItems = $("#vcs_dropdown li a");
		}
		
		/**
		 * attach event handler
		 */
		function _attachEvent(){
			htElement.aVCSItems.click(_onSelectVCSItem);
		}
		
		function _onSelectVCSItem(){
			var sText = $(this).text();
			var sValue = $(this).attr("data-value");
			
			htElement.welInputVCS.val(sValue);
			htElement.welBtnVCSSelected.text(sText);
		}
		
		/**
		 * initialize formValidator
		 * @require validate.js
		 */
		function _initFormValidator(){
			// name : name of input element
			// rules: rules to apply to the input element.
			var htRuleName   = {"name":"name",   "rules":"required|alpha_dash"}; // project name
			var aRules = [htRuleName];
			
			htVar.oValidator = new FormValidator(htVar.sFormName, aRules, _onFormValidate);
		}

		/**
		 * handler for validation errors.
		 * callback should return an appropriate message for the given error
		 */
		function _onFormValidate(aErrors){
			if(aErrors.length > 0){
			    $('span.warning').hide();
			    $('span.msg').show();
			}
		}
		
		_init(htOptions || {});
	};
	
})("hive.project.New");
