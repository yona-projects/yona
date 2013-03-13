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
	oNS.container[oNS.name] = function(){
		
	};
	
})("hive.Issue.Write");

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