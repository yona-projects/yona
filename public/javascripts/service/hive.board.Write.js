/**
 * @(#)hive.board.Write.js 2013.03.12
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
			_initElement(htOptions || {});
			_initVar(htOptions || {});
			_attachEvent();
			
			_initFileUploader();
		}
		
		/**
		 * initialize variable
		 */
		function _initVar(htOptions){
			htVar.sMode = htOptions.sMode || "new";
			htVar.sUploaderAction = htOptions.sUploaderAction;
			htVar.sTplFileItem = htOptions.sTplFileItem || (htElement.welTplFileItem ? htElement.welTplFileItem.text() : "");
		}
		
		/**
		 * initialize element variable
		 */
		function _initElement(htOptions){
			htElement.welTarget = $(htOptions.elTarget || "#upload");
			htElement.welTextarea = $(htOptions.elTextarea || "#contents");

			htElement.welTplFileItem = $('#tplAttachedFile');
			
			// Validate
			htElement.welForm = $("form");
			htElement.welInputTitle = $("input#title");
			htElement.welInputBody = $("textarea#contents");
			htElement.welWarning = $("#warning");
			htElement.welWarningBtn = htElement.welWarning.find("button"); 			
		}
		
		/**
		 * attach event handler : for validate form
		 */
		function _attachEvent(){
			htElement.welForm.submit(_onSubmitForm);
		}
		
		/**
		 * Validate form on submit
		 */
		function _onSubmitForm(){
			if(htElement.welInputTitle.val() == "" || htElement.welInputBody.val() == ""){
				htElement.welWarningBtn.click(function(){
					htElement.welWarning.hide();
				});
				htElement.welWarning.show();
				return false;
			}
			
			return true;
		}
		
		/**
		 * initialize fileUploader
		 */
		function _initFileUploader(){
			hive.FileUploader.init({
			  	"elTarget"    : htElement.welTarget,
			  	"elTextarea"  : htElement.welTextarea,
			  	"sTplFileItem": htVar.sTplFileItem,
			  	"sAction"     : htVar.sUploaderAction
			});
		}
		
		_init(htOptions);
	};
	
})("hive.board.Write");