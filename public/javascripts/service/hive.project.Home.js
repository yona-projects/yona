/**
 * @(#)hive.project.Home.js 2013.04.25
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
			_initElement(htOptions);
			_attachEvent();			
		}
		
		/**
		 * initialize element
		 */
		function _initElement(htOptions){
			htElement.welRepoURL = $("#repositoryURL");
		}
		
		/**
		 * attach event handler
		 */
		function _attachEvent(){
			htElement.welRepoURL.click(_onClickRepoURL);
		}
		
		function _onClickRepoURL(){
			htElement.welRepoURL.select();
		}
		
		_init(htOptions || {});
	};
	
})("hive.project.Home");
