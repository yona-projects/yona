/**
 * @(#)yobi.FileDownloader 2013.04.09
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */
yobi.FileDownloader = function(htOptions) {

	var htVar = {};
	var htElement = {};

	/**
	 * 초기화
	 * initialize
	 */
	function _init(htOptions){
		_initElement(htOptions);
		_initVar(htOptions);
		
		_requestList();
	}
	
	/**
	 * 엘리먼트 초기화
	 * initialize element
	 */
	function _initElement(htOptions){
		htElement.welTarget   = $(htOptions.elTarget);
		htElement.welFileList = $('<ul class="attaches wm">');
	}
	
	/**
	 * 변수 초기화
	 * initialize variable
	 */
	function _initVar(htOptions){
		htVar.sAction = htOptions.sAction;
		htVar.sTplItem = '<li class="attach"><a href="${url}"><i class="yobicon-paperclip"></i>${name} (${fileSize})</a></li>';
		
		htVar.sResourceId = htElement.welTarget.attr('resourceId');
		htVar.sResourceType = htElement.welTarget.attr('resourceType');		
	}
	
	/**
	 * 서버에 첨부파일 목록 요청
	 * request attached file list
	 */
	function _requestList(){
		var htData = _getRequestData();

		$yobi.sendForm({
			"sURL"     : htVar.sAction,
			"htData"   : htData,
			"htOptForm": {"method":"get"},
			"fOnLoad"  : _onLoadRequest
		});		
	}
	
	/**
	 * 서버에 요청할 인자 반환
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
	 * 서버에서 수신한 첨부파일 목록 처리함수
	 * @param {Object} oRes
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
	 * 첨부파일 목록에 추가할 항목(LI)을 반환하는 함수
	 * @param {Object} oFile 첨부파일 정보
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
	new yobi.FileDownloader({
		"elTarget": $(welTarget),
		"sAction" : sAction
	});
};