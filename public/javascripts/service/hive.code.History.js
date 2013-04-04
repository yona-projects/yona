/**
 * @(#)hive.code.History.js 2013.04.04
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
		
		/**
		 * initialize
		 */
		function _init(htOptions){
			_initElement(htOptions);
			_initCopyURL();
		}
	
		/**
		 * initialize element
		 */
		function _initElement(htOptions){
			// copy repository URL
			htElement.welInputRepoURL = $("#repositoryURL");
			htElement.welBtnCopyURL = $("#copyURL");
		}
		
		/**
		 * Copy repository URL to clipBoard
		 * 
		 * @require ZeroClipboard
		 */
		function _initCopyURL(){
			htElement.welBtnCopyURL.zclip({
				"path": "/assets/javascripts/lib/jquery/ZeroClipboard.swf",
				"copy": function(){
					return htElement.welInputRepoURL.val();
				}
			});
		}
		
		_init(htOptions || {});
	};
	
})("hive.code.History");