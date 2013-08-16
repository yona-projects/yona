/**
 * @(#)yobi.milestone.Write.js 2013.03.18
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
			_initVar(htOptions);
			_initElement(htOptions);
			_attachEvent();
		}

		/**
		 * initialize variables
		 */
		function _initVar(htOptions){
		}

		/**
		 * initialize element variables
		 */
		function _initElement(htOptions){
			htElement.welForm = $("form.nm");
			htElement.welInputTitle   = $('#title');
			htElement.welInputBody = $('#body');
            htElement.welInputFromBranch = $('input[name="fromBranch"]');
            htElement.welInputToBranch = $('input[name="toBranch"]');
		}

		/**
		 * attach event handlers
		 */
		function _attachEvent(){
			htElement.welForm.submit(_onSubmitForm);
		}

		/**
		 * on submit form
		 */
		function _onSubmitForm(weEvt){
			return _validateForm();
		}

		function _validateForm(){
			var sTitle = $.trim(htElement.welInputTitle.val());
			var sBody = $.trim(htElement.welInputBody.val());
            // these two fields should be loaded dynamically.
            htElement.welInputFromBranch = $('input[name="fromBranch"]');
            htElement.welInputToBranch = $('input[name="toBranch"]');
			var sFromBranch = $.trim(htElement.welInputFromBranch.val());
            var sToBranch = $.trim(htElement.welInputToBranch.val());

			if(sTitle.length === 0){
				$yobi.showAlert(Messages("pullrequest.title.required"));
				return false;
			}

			if(sBody.length === 0){
				$yobi.showAlert(Messages("pullrequest.body.required"));
				return false;
			}

            if(sFromBranch.length === 0){
                $yobi.showAlert(Messages("pullrequest.fromBranch.required"));
                return false;
            }

            if(sToBranch.length === 0){
                $yobi.showAlert(Messages("pullrequest.toBranch.required"));
                return false;
            }

			return true;
		}

		_init(htOptions || {});
	};

})("yobi.pullRequest.Write");
