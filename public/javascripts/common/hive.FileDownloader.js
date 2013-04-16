/**
 * @(#)hive.FileDownloader 2013.04.09
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */
hive.FileDownloader = function(htOptions) {

	var htVar = {};
	var htElement = {};

	/**
	 * initialize
	 */
	function _init(htOptions){
		_initElement(htOptions);
		_initVar(htOptions);
		
		_requestList();
	}
	
	/**
	 * initialize element
	 */
	function _initElement(htOptions){
		htElement.welTarget   = $(htOptions.elTarget);
		htElement.welFileList = $('<ul class="attaches wm">');
	}
	
	/**
	 * initialize variable
	 */
	function _initVar(htOptions){
		htVar.sAction = htOptions.sAction;
		htVar.sTplItem = '<li class="attach"><a href="${url}"><i class="ico ico-clip"></i>${name} (${fileSize})</a></li>';
		
		htVar.sResourceId = htElement.welTarget.attr('resourceId');
		htVar.sResourceType = htElement.welTarget.attr('resourceType');		
	}
	
	/**
	 * request attached file list
	 */
	function _requestList(){
		var htData = _getRequestData();

		$hive.sendForm({
			"sURL"     : htVar.sAction,
			"htData"   : htData,
			"htOptForm": {"method":"get"},
			"fOnLoad"  : _onLoadRequest
		});		
	}
	
	/**
	 * get request parameters
	 * @return {Hash Table}
	 */
	function _getRequestData(){
		var htData = {};
		
		if(typeof htVar.sResourceType !== "undefined"){
			htData.containerType = htVar.sResourceType;
		}
		
		if(typeof htVar.sResourceId !== "undefined"){
			htData.containerId = htVar.sResourceId;
		}
		
		return htData;
	}
	
	/**
	 * on load file list
	 */
	function _onLoadRequest(oRes){
		var aItems = [];
		var aFiles = oRes.attachments;

		if(aFiles.length === 0){
			return;
		}
		
		htElement.welFileList.css('display', 'block');
		aFiles.forEach(function(oFile){
			aItems.push(_getFileItemHTML(oFile));
		});
		htElement.welFileList.append(aItems);
		htElement.welTarget.append(htElement.welFileList);
	}
	
	/**
	 * @returns {Wrapped Element}
	 */
	function _getFileItemHTML(oFile) {
		var htData = oFile;
			htData.fileSize = humanize.filesize(htData.size);
		var welItem = $.tmpl(htVar.sTplItem, htData);
		
		return welItem;
	}
	
	_init(htOptions || {});
};

// legacy compatibility
var fileDownloader = function(welTarget, sAction){
	new hive.FileDownloader({
		"elTarget": $(welTarget),
		"sAction" : sAction
	});
};