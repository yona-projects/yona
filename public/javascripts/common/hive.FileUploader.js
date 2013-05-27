/**
 * @(#)hive.FileUploader 2013.03.21
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */
hive.FileUploader = (function() {
	
	var htVar = {};
	var htElements = {};
	
	/**
	 * initialize fileUploader
	 * 파일 업로더 초기화 함수. fileUploader.init(htOptions) 으로 사용한다.
	 * @param {Hash Table} htOptions
	 * @param {Variant} htOptions.elTarget     첨부파일 
	 * @param {Variant} htOptions.elTextarea   이미지 첨부파일의 경우 클릭시 이 영역에 태그를 삽입한다   
	 * @param {String}  htOptions.sTplFileItem 첨부한 파일명을 표시할 HTML 템플릿   
	 */
	function _init(htOptions){
		htOptions = htOptions || {};
		
		_initElement(htOptions);
		_initVar(htOptions);
		_attachEvent();
		
        _requestList();
	}
	
	/**
	 * 변수 초기화
	 * initialize variables
	 * @param {Hash Table} htOptions 초기화 옵션
	 */
	function _initVar(htOptions){
		htVar.nTotalSize   = 0;
		htVar.sAction      = htOptions.sAction;
		htVar.sTplFileItem = htOptions.sTplFileItem;
		
		htVar.htUploadOpts = {
			"dataType"      : "json",
			"error"         : _onErrorSubmitForm,
			"success"       : _onSuccessSubmitForm,
			"beforeSubmit"  : _onBeforeSubmitForm,
			"uploadProgress": _onUploadProgressForm
		};
		
		htVar.sMode = htOptions.sMode;
		htVar.sResourceId = htElements.welTarget.attr('resourceId');
		htVar.sResourceType = htElements.welTarget.attr('resourceType');		
	}

	/**
	 * 엘리먼트 초기화
	 * initialize elements
	 * @param {Hash Table} htOptions 초기화 옵션
	 */
	function _initElement(htOptions){
		htElements.welTarget      = $(htOptions.elTarget);
		htElements.welTextarea    = $(htOptions.elTextarea);
		
		htElements.welInputFile   = $(htOptions.elInputFile   || ".file");
		htElements.welTotalNum    = $(htOptions.elTotalNum    || ".total-num");
		htElements.welProgressNum = $(htOptions.elProgressNum || ".progress-num");
		htElements.welProgressBar = $(htOptions.elProgressBar || ".progress > .bar");
		htElements.welFileList    = $(htOptions.elFileList    || "ul.attached-files");
	}
	
	/**
	 * 이벤트 핸들러 설정
	 * attach event handlers
	 */
	function _attachEvent(){
		htElements.welInputFile.change(_onChangeFile);
		htElements.welInputFile.click(function(){
			_setProgressBar(0);
		});
	}
	
	/**
	 * 서버에 첨부파일 목록 요청
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
	function _onLoadRequest(oRes) {
        var nTotalFileSize = 0;
        var sNotice = " (" + Messages("attach.attachIfYouSave") + ")";

        nTotalFileSize += _addFilesToList(oRes.attachments); // 이미 첨부되어 있는 파일
        nTotalFileSize += _addFilesToList(oRes.tempFiles, sNotice); // 임시 파일 (저장하면 첨부됨)
		
		_setProgressBar(100);
		_updateTotalFilesize(nTotalFileSize);
	}

	/**
	 * 첨부파일 정보를 HTML 목록에 추가하는 함수
	 * @param {Array} aFiles 첨부파일 절보
	 * @param {String} sNotice 
	 */
	function _addFilesToList(aFiles, sNotice){
	    if(!(aFiles instanceof Array) || aFiles.length === 0){
	        return 0;
	    }
	    
        var welItem;
        var nTotalSize = 0;
        sNotice = sNotice || "";

        aFiles.forEach(function(oFile) {
            welItem = _createFileItem(oFile, sNotice);
            welItem.click(_onClickListItem);
            htElements.welFileList.append(welItem);
            nTotalSize += parseInt(oFile.size, 10);
        });

        return nTotalSize;
	}
	
	/**
	 * 파일 선택시 이벤트 핸들러
	 * change event handler on <input type="file">
	 */
	function _onChangeFile(){
		// Validation
		var sFileName = _getBasename(htElements.welInputFile.val());
		if(!sFileName || sFileName === ""){
			return;
		}

		// Submit
		var welForm = $('<form method="post" enctype="multipart/form-data" style="display:none">');
		var welInputFile = htElements.welInputFile.clone();
		welInputFile[0].files = htElements.welInputFile[0].files;
		welForm.attr('action', htVar.sAction);
		welForm.append(welInputFile).appendTo(document.body);
		welForm.ajaxForm(htVar.htUploadOpts);
		
		try {
			welForm.submit();
		} finally {
			welInputFile.remove();
			welForm.remove();
			welForm = welInputFile = null;
		}
	}
	
	/**
	 * 문자열에서 경로를 제거하고 파일명만 반환
	 * return trailing name component of path
	 * @param {String} sPath
	 * @return {String}  
	 */
	function _getBasename(sPath){
		var sSeparator = 'fakepath';
		var nPos = sPath.indexOf(sSeparator);		
		return (nPos > -1) ? sPath.substring(nPos + sSeparator.length + 1) : sPath;
	}
	
	/**
	 * 파일 전송하기 전에 실행되는 함수
	 * 선택된 파일이 없으면 false 반환
	 * @return {Boolean}
	 */
	function _onBeforeSubmitForm(){
		var sFileName = _getBasename(htElements.welInputFile.val());
		return !!sFileName;
	}

	/**
	 * 첨부 파일 전송에 성공시 이벤트 핸들러
	 * On success to submit temporary form created in onChangeFile()
	 * @param {Hash Table} htData
	 * @return
	 */
	function _onSuccessSubmitForm(oRes){
		htElements.welInputFile.val("");
		
		// Validation
		if(!(oRes instanceof Object) || !oRes.name || !oRes.url){
			//console.log("Failed to upload - Server Error");
			_setProgressBar(0);
			return;
		}

		// create list item
        var welItem = _createFileItem(oRes, _attachIfYouSaveNotice());
		welItem.click(_onClickListItem);
		htElements.welFileList.append(welItem);
		
		_setProgressBar(100);
		_updateTotalFilesize(oRes.size);
	}
	
	/**
	 * 첨부 파일 크기 합계 표시
	 * @param {Number} nValue 첨부 파일 크기 변화할 값
	 */
	function _updateTotalFilesize(nValue){
		nValue = (nValue || 0) * 1;
		htVar.nTotalSize += nValue;
		htElements.welTotalNum.text(humanize.filesize(htVar.nTotalSize));
	}
	
	/**
	 * 파일 목록에 추가할 수 있는 LI 엘리먼트를 반환하는 함수
	 * Create uploaded file item HTML element using template string
	 * @param {Hash Table} htFile 파일 정보
	 * @return {HTMLElement} 
	 */
	function _createFileItem(htFile, sNotice) {
		var oItem = $.tmpl(htVar.sTplFileItem, {
			"mimeType": htFile.mimeType,
			"fileName": htFile.name,
			"fileHref": htFile.url,
			"fileSize": htFile.size,
			"fileSizeReadable": humanize.filesize(htFile.size),
            "notice": sNotice
		});

		return oItem;
	}
	
	/**
	 * 파일 전송에 실패한 경우
	 * On error to submit temporary form created in onChangeFile()
	 * @param {Object} oRes
	 */
	function _onErrorSubmitForm(oRes){
		_setProgressBar(0);
	}
	
	/**
	 * 파일 업로드 진행상태 표시 함수
	 * uploadProgress event handler 
	 * @param {Object} oEvent
	 * @param {Number} nPos
	 * @param {Number} nTotal
	 * @param {Number} nPercentComplete
	 */
	function _onUploadProgressForm(oEvent, nPos, nTotal, nPercentComplete){
		_setProgressBar(nPercentComplete);
	}

	/**
	 * 업로드 진행상태 표시바 너비 지정
	 * Set Progress Bar Width 
	 * @param {Number} nProgress
	 */
	function _setProgressBar(nProgress) {
		nProgress = nProgress * 1;
		htElements.welProgressBar.css("width", nProgress + "%");
		htElements.welProgressNum.text(nProgress + "%");
	}

	
	/**
	 * 첨부파일 목록에서 항목을 클릭할 때 이벤트 핸들러
	 * On Click attached files list
	 * @param {Wrapped Event} weEvt
	 */
	function _onClickListItem(weEvt){
		var welTarget = $(weEvt.target);
		var welItem = $(weEvt.currentTarget);
		
		// 파일 아이템 전체에 이벤트 핸들러가 설정되어 있으므로
		// 클릭이벤트 발생한 위치를 삭제버튼과 나머지 영역으로 구분하여 처리
		if(welTarget.hasClass("btn-delete")){
			_deleteAttachedFile(welItem);    // 첨부파일 삭제
		} else {
			_insertLinkToTextarea(welItem);  // <textarea>에 링크 텍스트 추가
		}
	}
	
	/**
	 * 선택한 파일 아이템의 링크 텍스트를 <textarea>에 추가하는 함수
     * @param {Wrapped Element} welItem
	 */
	function _insertLinkToTextarea(welItem){
		var welTextarea = htElements.welTextarea;
		if(!welTextarea){
			return false;
		}
		
		var nPos  = welTextarea.prop('selectionStart');
		var sText = welTextarea.val();
		var sLink = _getLinkText(welItem);
		
		welTextarea.val(sText.substring(0, nPos) + sLink + sText.substring(nPos));
	}
	
	/**
	 * 선택한 파일 아이템을 첨부 파일에서 삭제
	 * <textarea>에서 해당 파일의 링크 텍스트도 제거함 (_clearLinkInTextarea)
	 * @param {Wrapped Element} welItem
	 */
	function _deleteAttachedFile(welItem){	
		var nFileSize = welItem.attr("data-size") * 1;
		
		$hive.sendForm({
			"sURL": welItem.attr("data-href"),
			"htOptForm": {
				"method" :"post",
				"enctype":"multipart/form-data"
			},
			"htData" : {"_method":"delete"},
			"fOnLoad": function(){
				_updateTotalFilesize(nFileSize * -1);
				_clearLinkInTextarea(welItem);
				_setProgressBar(0);
				welItem.remove();				
			}
		});
	}
	
	/**
	 * 파일 아이템으로부터 링크 텍스트를 생성하여 반환하는 함수
	 * @param {Wrapped Element} welItem 템플릿 htVar.sTplFileItem 에 의해 생성된 첨부 파일 아이템
	 * @return {String}
	 */
	function _getLinkText(welItem){
		var sMimeType = welItem.attr("data-mime");
		var sFileName = welItem.attr("data-name");
		var sFilePath = welItem.attr("data-href");
		
		var sLinkText = '';
		if (sMimeType.substr(0,5) == "image"){
			sLinkText = '<img src="' + sFilePath + '">';
		} else {
			sLinkText = '[' + sFileName +'](' + sFilePath + ')';
		}
		
		return sLinkText;
	}
	
	/**
	 * <textarea>에서 해당 파일 아이템의 링크 텍스트를 제거하는 함수
	 * @param {Wrapped Element} welItem
	 */
	function _clearLinkInTextarea(welItem){
		var welTextarea = htElements.welTextarea;
		if(!welTextarea){
			return false;
		}
		
		var sLink = _getLinkText(welItem);		
		welTextarea.val(welTextarea.val().split(sLink).join(''));		
	}
	
	/**
	 * 인터페이스 반환
	 */
	return {
		"init": _init
	};
})();

