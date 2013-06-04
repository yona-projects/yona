/**
 * @(#)hive.milestone.Write.js 2013.03.18
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
			_initVar(htOptions);
			_initElement(htOptions);			
			_initDatePicker();
			_attachEvent();
		}
		
		/**
		 * initialize variables
		 */
		function _initVar(htOptions){
			htVar.sDateFormat  = htOptions.sDateFormat  || "YYYY-MM-DD";
			htVar.rxDateFormat = htOptions.rxDateFormat || /\d{4}-\d{2}-\d{2}/;
		}
		
		/**
		 * initialize element variables
		 */
		function _initElement(htOptions){
			htElement.welForm = $("form.nm");
			htElement.welDatePicker   = $(htOptions.elDatePicker);
			htElement.welInputDueDate = $(htOptions.elDueDate);			
			htElement.welInputTitle   = $('#title');
			htElement.welInputContent = $('#contents');			
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
			var sContent = $.trim(htElement.welInputContent.val());
			var sDueDate = $.trim(htElement.welInputDueDate.val());
			
			if(sTitle.length === 0){
				$hive.showAlert(Messages("milestone.error.title"));
				return false;
			}
			
			if(sContent.length === 0){
				$hive.showAlert(Messages("milestone.error.content"));
				return false;
			}
			
			if(htVar.rxDateFormat.test(sDueDate) === false){
				$hive.showAlert(Messages("milestone.error.duedateFormat"));
				return false;
			}
			
			return true;
		}
		
		/**
		 * initialize DatePicker
		 * @requires Pikaday
		 */
		function _initDatePicker(){
			if(typeof Pikaday != "function"){
				console.log("[HIVE] Pikaday required (https://github.com/dbushell/Pikaday)");
				return false;
			}

			// append Pikaday element to DatePicker
			htVar.oPicker = new Pikaday({
				"format" : htVar.sDateFormat,
				"onSelect" : function(oDate) {
					htElement.welInputDueDate.val(this.toString());
				}
			});
			htElement.welDatePicker.append(htVar.oPicker.el);

			// fill DatePicker date to InputDueDate if empty
			// or set DatePicker date with InputDueDate
			var sDueDate = htElement.welInputDueDate.val();
			if(sDueDate.length > 0){
				htVar.oPicker.setDate(sDueDate);
			}

			// set relative event between dueDate input and datePicker
			htElement.welInputDueDate.blur(function() {
				htVar.oPicker.setDate(this.value);
			});
		}
		
		
		_init(htOptions || {});
	};
	
})("hive.milestone.Write");
