/**
 * @(#)hive.Issue.Write.js 2013.03.13
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
			
			_initFileUploader();
			
			if(typeof htOptions.htOptLabel == "object"){
				_initLabel(htOptions.htOptLabel);
			}
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
		
		/**
		 * 지정한 라벨들을 활성화 상태로 표시
		 * @param {Hash Table} htActiveLabels
		 * @example
		 * htActiveLabels["labelId"] = "labelColor";
		 */
		function _initLabel(htOptions){
			htOptions.fOnLoad = function(){
				var sKey;
				for(sKey in htOptions.htActive){
					hive.Label.setActiveLabel(sKey, htOptions.htActive[sKey]);
				}
			};
			
			hive.Label.init(htOptions);
		}
		
		_init(htOptions);
	};
	
})("hive.issue.Write");