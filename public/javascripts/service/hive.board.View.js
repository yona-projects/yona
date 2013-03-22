/**
 * @(#)hive.board.View.js 2013.03.11
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
		 * @param {Hash Table} htOptions
		 */
		function _init(htOptions){
			_initVar(htOptions || {});
			_initElement(htOptions || {});

			_initFileUploader();
			_initFileDownloader();
		}

		/**
		 * initialize variables except HTML Element
		 */
		function _initVar(htOptions){
			htVar.sTplFileItem = $('#tplAttachedFile').text();
			htVar.sAction = htOptions.sAction;
		}
		
		/**
		 * initialize HTML Element variables
		 */
		function _initElement(htOptions){
			htElement.welTarget = $("#upload");
			htElement.welTextarea = $("#contents");
			
			htElement.welAttachments = $(".attachments");			
		}

		/**
		 * initialize fileUploader
		 */
		function _initFileUploader(){
			hive.FileUploader.init({
				"elTarget": htElement.welTarget,
				"elTextarea": htElement.welTextarea,
				"sTplFileItem": htVar.sTplFileItem,
				"sAction": htVar.sAction
			});
		}
		
		/**
		 * initialize fileDownloader
		 */
		function _initFileDownloader(){
			htElement.welAttachments.each(function(n, el){
				fileDownloader($(el), htVar.sAction);
			});
		}
        
        _init(htOptions);
	};
	
})("hive.board.View");
