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
			
			/*
			htVar.htErrMsgs = {
				"name"  : Messages("project.name.alert"),
				"accept": Messages("project.new.agreement.alert")					
			};
			*/
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
			var sValue = $(this).text();
			htElement.welInputVCS.val(sValue.toUpperCase());
			htElement.welBtnVCSSelected.text(sValue);
		}
		
		/**
		 * initialize formValidator
		 * @require validate.js
		 */
		function _initFormValidator(){
			// name : name of input element
			// rules: rules to apply to the input element.
			var htRuleName   = {"name":"name",   "rules":"required|alpha_dash"}; // project name
			var htRuleAccept = {"name":"accept", "rules":"required"}; // accept agreement
			var aRules = [htRuleName, htRuleAccept];
			
			htVar.oValidator = new FormValidator(htVar.sFormName, aRules, _onErrorValidate);
		}

		/**
		 * handler for validation errors.
		 * callback should return an appropriate message for the given error
		 */
		function _onErrorValidate(aErrors){
			var htError = aErrors[0];
			var welTarget = $('div.n-alert[data-errType="' + htError.name + '"]');
			welTarget.show();
			
			try {
				return htError.message;
			} finally {
				welTarget = htError = null;
			}
		}
		
		_init(htOptions || {});
	};
	
})("hive.project.New");
