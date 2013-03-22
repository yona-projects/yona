/**
 * @(#)hive.issue.View.js 2013.03.13
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
			_setLabelColor();
			
			if(typeof htOptions.htActiveLabels == "object"){
				_initLabels(htOptions.htActiveLabels);
			}			
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
			htElement.welTextarea = $("#comment-editor");
			
			htElement.welAttachments = $(".attachments");
			htElement.welLabels = $('.issue-label');
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
			htElement.welAttachments.each(function(el){
				fileDownloader(el, htVar.sAction);
			});
		}
        
		/**
		 * set Labels foreground color as contrast to background color 
		 */
		function _setLabelColor(){
			var welLabel;
			var sBgColor, sColor;
			
			htElement.welLabels.each(function(nIndex, elLabel){
				welLabel = $(elLabel);
				sBgColor = welLabel.css("background-color");
				sColor = $hive.getContrastColor(sBgColor); 
				welLabel.css("color", sColor);
			});
			
			welLabel = null;
		}
		
		/**
		 * 지정한 라벨들을 활성화 상태로 표시
		 * @param {Hash Table} htActiveLabels
		 * @example
		 * htActiveLabels["labelId"] = "labelColor";
		 */
		function _initLabel(htActiveLabels){
			var sKey;
			
			for(sKey in htActiveLabels){
				hive.Label.setActiveLabel(sKey, htActiveLabels[sKey]);
			}
		}
		
        _init(htOptions);
	};
	
})("hive.issue.View");