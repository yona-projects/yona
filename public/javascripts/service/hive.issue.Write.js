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
			fileUploader.init({
			  	"elTarget"    : htElement.welTarget,
			  	"elTextarea"  : htElement.welTextarea,
			  	"sTplFileItem": htVar.sTplFileItem,
			  	"sAction"     : htVar.sUploaderAction
			});
		}
		
		_init(htOptions);
	};
	
})("hive.issue.Write");

/*
nforge.namespace('issue');
nforge.issue.new = function() {
  var that;

  that = {
    init: function(filesUrl) {
      //fileUploader($('#upload'), $('#body'), filesUrl);
      fileUploader.init({
      	"elTarget"    : $('#upload'),   // upload area
      	"elTextarea"  : $('#body'), // textarea
      	"sTplFileItem": $('#tplAttachedFile').text(),
      	"sAction"     : filesUrl
      });
    }
  }

  return that;
};

nforge.issue.edit = nforge.issue.new;
*/