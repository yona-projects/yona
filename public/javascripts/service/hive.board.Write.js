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
			htElement.welTextarea = $(htOptions.elTextarea || "#body");

			htElement.welTplFileItem = $('#tplAttachedFile');
			
			// Validate
			htElement.welForm = $("form");
			htElement.welInputTitle = $("input#title");
			htElement.welInputBody = $("textarea#body");
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
			if(htElement.welInputTitle.val() == ""){
				$hive.showAlert(Messages("post.error.emptyTitle"));
				return false;
			}

			if(htElement.welInputBody.val() == ""){
				$hive.showAlert(Messages("post.error.emptyBody"));
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
			  	"sAction"     : htVar.sUploaderAction,
			  	"sMode"       : htVar.sMode
			});
		}
		
		_init(htOptions);
	};
	
})("hive.board.Write");