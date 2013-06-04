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
	 * 
	 * @param {Hash Table} htOptions
	 * @param {Variant} htOptions.elTarget     첨부파일 
	 * @param {Variant} htOptions.elTextarea   이미지 첨부파일의 경우 클릭시 이 영역에 태그를 삽입한다   
	 * @param {String}  htOptions.sTplFileItem 첨부한 파일명을 표시할 HTML 템플릿   
	 */
	function _init(htOptions){
		htOptions = htOptions || {};
		
		_initElement(htOptions);
		_initVar(htOptions);
		_initDroppable();
		
		_attachEvent();
        _requestList();
	}

	/**
	 * 변수 초기화
	 * initialize variables
	 * 
	 * @param {Hash Table} htOptions 초기화 옵션
	 */
	function _initVar(htOptions){
		htVar.sAction      = htOptions.sAction;
		htVar.sTplFileItem = htOptions.sTplFileItem;
		htVar.htUploadOpts = {"dataType": "json"};
		
		htVar.sMode = htOptions.sMode;
		htVar.sResourceId = htElements.welContainer.attr('data-resourceId');
		htVar.sResourceType = htElements.welContainer.attr('data-resourceType');
		
		htVar.bDroppable = false;
	}

	/**
	 * 엘리먼트 초기화
	 * initialize elements
	 * 
	 * @param {Hash Table} htOptions 초기화 옵션
	 */
	function _initElement(htOptions){
		htElements.welContainer = $(htOptions.elContainer);
		htElements.welTextarea  = $(htOptions.elTextarea);
		
		htElements.welInputFile = htElements.welContainer.find("input.file");
		htElements.welFileList  = htElements.welContainer.find("ul.attached-files");
		htElements.welFileListHelp = htElements.welContainer.find("p.help");
		htElements.welHelpDroppable = htElements.welContainer.find(".help-droppable");
	}
	
	/**
	 * 드래그앤드롭 설정
	 * initialize drag&drop upload 
	 */
	function _initDroppable(){
	    if(window.File && window.FileList){
	        htVar.bDroppable = true;
	        htElements.welHelpDroppable.show();
	    }
	}
	
	/**
	 * 이벤트 핸들러 설정
	 * attach event handlers
	 */
	function _attachEvent(){
		htElements.welInputFile.change(_onChangeFile);
		
		if(htVar.bDroppable){
		    htElements.welContainer.bind("dragover", function(weEvt){
		        weEvt.preventDefault(); 
		    });
		    htElements.welContainer.bind("drop", _onDropFile);
		}
	}
	
	/**
	 * 파일을 드래그앤드롭해서 가져왔을 때 이벤트 핸들러
	 * @param {Wrapped Event} weEvt
	 */
	function _onDropFile(weEvt){
	    var oFiles = weEvt.originalEvent.dataTransfer.files;
	    if(!oFiles || oFiles.length === 0){
	        return;
	    }
	    
	    var welForm = _getAjaxForm(oFiles);
        var oFile = welForm.data("file");
        _appendFileItem(oFile, true);
	    
        try {
            welForm.submit();
        } finally {
            welForm.remove();
            welForm = null;
        }
        
        // TODO: 여러 파일 전송 지원
        if(oFiles.length > 1){
            $hive.notify(Messages("attach.multipleNotYet"));
        }
        
	    weEvt.stopPropagation();
	    weEvt.preventDefault();
	    return false;
	}
	
	/**
	 * 서버에 첨부파일 목록 요청
	 * request attached file list
	 */
	function _requestList(){
        var htData = {};
        
        if(typeof htVar.sResourceType !== "undefined"){
            htData.containerType = htVar.sResourceType;
        }
        
        if(typeof htVar.sResourceId !== "undefined"){
            htData.containerId = htVar.sResourceId;
        }

		$hive.sendForm({
			"sURL"     : htVar.sAction,
			"htData"   : htData,
			"htOptForm": {"method":"get"},
			"fOnLoad"  : _onLoadRequest
		});		
	}
	
    /**
     * 서버에서 수신한 첨부파일 목록 처리함수
     * 
     * @param {Object} oRes
     */
	function _onLoadRequest(oRes) {
        _appendFileItem(oRes.attachments, false); // 이미 첨부되어 있는 파일
        _appendFileItem(oRes.tempFiles, true);    // 임시 파일 (저장하면 첨부됨)
	}

	/**
	 * 파일 선택시 이벤트 핸들러
	 * change event handler on <input type="file">
	 */
	function _onChangeFile(){
		var sFileName = _getBasename(htElements.welInputFile.val());
		if(!sFileName || sFileName === ""){
			return;
		}
		
		// AjaxForm 생성하고 파일 첨부 목록에 항목 추가
        var welForm = _getAjaxForm();
        var oFile = welForm.data("file");
		_appendFileItem(oFile, true);

		try {
			welForm.submit();
		} finally {
			welForm.remove();
			welForm = null;
		}
	}

	/**
	 * 파일 전송을 위한 ajaxForm 객체를 반환하는 함수
     * 전송 상태 표시를 위한 이벤트 핸들러를 설정하고
     * 선택한 파일 정보는 welForm.data("files") 로 제공한다 
	 * 
	 * - _onChangeFile 에서 호출하는 경우:
	 *    파일을 선택한 <input type="file">을 복제하여 사용한다
	 * 
	 * - _onDropFile 에서 호출하는 경우:
	 *    파일 목록(FileList) 객체를 인자로 받아 사용한다
	 * 
	 * @param {FileList} oFiles FileList 객체 (Optional)
	 * @return {Wrapped Element}
	 */
	function _getAjaxForm(oFiles){
	    var nSubmitId = parseInt(Math.random() * new Date().getTime());
        var welInputFile = htElements.welInputFile.clone();
        var welForm = $('<form method="post" enctype="multipart/form-data" style="display:none">');    
        welInputFile[0].files = oFiles || htElements.welInputFile[0].files;
        
        // TODO: 여러 파일 전송
        
        // 전송 상태 표시를 위한 submitId 값
        var oFile = welInputFile[0].files[0];
        oFile.nSubmitId = nSubmitId;
        
        welForm.attr('action', htVar.sAction);
        welForm.data('file', oFile);
        welForm.append(welInputFile).appendTo(document.body);
        
        // 폼 이벤트 핸들러 설정: nSubmitId 가 필요한 부분만
        var htUploadOpts = htVar.htUploadOpts;
        htUploadOpts.success = function(oRes){
            _onSuccessSubmit(nSubmitId, oRes);
        };
        htUploadOpts.uploadProgress = function(oEvent, nPos, nTotal, nPercentComplete){
            _onUploadProgress(nSubmitId, oEvent, nPos, nTotal, nPercentComplete);
        };
        htUploadOpts.error = function(oRes){
            _onErrorSubmit(nSubmitId, oRes);
        };

        welForm.ajaxForm(htUploadOpts);
        
        return welForm;
	}
	
	/**
	 * 파일 항목을 첨부 파일 목록에 추가한다
	 * 이미 전송된 파일 목록은 _onLoadRequest 에서 호출하고
	 * 아직 전송전 임시 파일은 _onChangeFile  에서 호출한다
	 *  
	 * oFile.id 가 존재하는 경우는 이미 전송된 파일 항목이고
	 * oFile.id 가 없는 경우는 전송대기 상태의 임시 항목이다
	 *  
	 * @param {Variant} vFile 하나의 파일 항목 객체(Object) 또는 여러 파일 항목을 담고 있는 배열(Array)
	 * @param {Boolean} bTemp 임시 저장 여부
	 * @return {Number} 이번에 목록에 추가된 파일들의 크기 합계
	 */
	function _appendFileItem(vFile, bTemp){
	    if(typeof vFile === "undefined"){
	        return 0;
	    }
	    
        var welItem;
        var nFileSize = 0;
	    var aFiles = (vFile instanceof Array) ? vFile : [vFile]; // 배열 변수로 단일화
	    var aWelItems = [];
	    
        aFiles.forEach(function(oFile) {
            welItem = _getFileItem(oFile, bTemp);
            
            if(typeof oFile.id !== "undefined"){ // 서버의 첨부 목록에서 가져온 경우
                welItem.addClass("complete");
                welItem.click(_onClickListItem);
            } else {                            // 전송하기 전의 임시 항목
                welItem.attr("id", oFile.nSubmitId);
                welItem.css("opacity", "0.2");
                welItem.data("progressBar", welItem.find(".progress > .bar"));                
            }
            
            aWelItems.push(welItem);
            nFileSize += parseInt(oFile.size, 10);
        });

        if(aWelItems.length > 0){
            htElements.welFileList.show();
            htElements.welFileListHelp.show();
        }

        // DOM 변형 작업은 한번에 하는게 성능향상
        htElements.welFileList.append(aWelItems);
        return nFileSize;
	}
	
    /**
     * 파일 목록에 추가할 수 있는 LI 엘리먼트를 반환하는 함수
     * Create uploaded file item HTML element using template string
     * 
     * @param {Hash Table} htFile 파일 정보
     * @param {Boolean} bTemp 임시 파일 여부
     * @return {Wrapped Element} 
     */
    function _getFileItem(htFile, bTemp) {
        var welItem = $.tmpl(htVar.sTplFileItem, {
            "fileId"  : htFile.id,
            "fileName": htFile.name,
            "fileHref": htFile.url,
            "fileSize": htFile.size,
            "fileSizeReadable": humanize.filesize(htFile.size),
            "mimeType": htFile.mimeType
        });
        
        // 임시 파일 표시
        if(bTemp){
            welItem.addClass("temporary");
        }
        return welItem;
    }
    
    /**
     * 파일 목록에 임시 추가 상태의 항목을 업데이트 하는 함수
     * _onChangeFile    에서 _appendFileItem 을 할 때는 파일 이름만 있는 상태 (oFile.id 없음)
     * _onSuccessSubmit 에서 _updateFileItem 을 호출해서 나머지 정보 마저 업데이트 하는 구조
     * 
     * @param {Number} nSubmitId
     * @param {Object} oRes
     */
    function _updateFileItem(nSubmitId, oRes){
        var welItem = $("#" + nSubmitId);
        welItem.attr({
            "data-id"  : oRes.id,
            "data-href": oRes.url,
            "data-name": oRes.name,
            "data-mime": oRes.mimeType
        });
        welItem.click(_onClickListItem);
    }

    /**
     * 파일 업로드 진행상태 처리 함수
     * uploadProgress event handler 
     * 
     * @param {Object} oEvent
     * @param {Number} nPos
     * @param {Number} nTotal
     * @param {Number} nPercentComplete
     */
    function _onUploadProgress(nSubmitId, oEvent, nPos, nTotal, nPercentComplete){
        _setProgressBar(nSubmitId, nPercentComplete);
    }

	/**
	 * 첨부 파일 전송에 성공시 이벤트 핸들러
	 * On success to submit temporary form created in onChangeFile()
	 * 
	 * @param {Hash Table} htData
	 * @return
	 */
	function _onSuccessSubmit(nSubmitId, oRes){
		htElements.welInputFile.val("");
		
		// Validate server response
		if(!(oRes instanceof Object) || !oRes.name || !oRes.url){
		    return _onErrorSubmit(nSubmitId, oRes);
		}

        // 업로드 완료된 뒤 항목 업데이트
		_updateFileItem(nSubmitId, oRes);
		_setProgressBar(nSubmitId, 100);
	}

    /**
     * 업로드 진행상태 표시
     * Set Progress Bar status 
     * 
     * @param {Number} nSubmitId
     * @param {Number} nProgress
     */
    function _setProgressBar(nSubmitId, nProgress) {
        var welItem = $("#" + nSubmitId);
        welItem.data("progressBar").css("width", nProgress + "%");

        // 완료 상태로 표시
        if(nProgress*1 === 100){
            welItem.css("opacity", "1");
            setTimeout(function(){
                welItem.addClass("complete");
            }, 1000);
        }
    }

    /**
	 * 파일 전송에 실패한 경우
	 * On error to submit temporary form created in onChangeFile().
	 * 
	 * @param {Number} nSubmitId
	 * @param {Object} oRes
	 */
	function _onErrorSubmit(nSubmitId, oRes){
	    var welItem = $("#" + nSubmitId);
	        welItem.remove();
	    $hive.notify(Messages("attach.error"));
	}

	/**
	 * 첨부파일 목록에서 항목을 클릭할 때 이벤트 핸들러
	 * On Click attached files list
	 * 
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
	 * 선택한 파일 아이템의 링크 텍스트를 textarea에 추가하는 함수
	 * 
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
	 * textarea에서 해당 파일의 링크 텍스트도 제거함 (_clearLinkInTextarea)
	 * 
	 * @param {Wrapped Element} welItem
	 */
	function _deleteAttachedFile(welItem){			
		$hive.sendForm({
			"sURL": welItem.attr("data-href"),
			"htOptForm": {
				"method" :"post",
				"enctype":"multipart/form-data"
			},
			"htData" : {"_method":"delete"},
			"fOnLoad": function(){
				_clearLinkInTextarea(welItem);
				welItem.remove();
				
				// 남은 항목이 없으면 목록 감춤
				if(htElements.welFileList.children().length === 0){
				    htElements.welFileList.hide();
				    htElements.welFileListHelp.hide();
				}
			}
		});
	}
	
	/**
	 * 파일 아이템으로부터 링크 텍스트를 생성하여 반환하는 함수
	 * 
	 * @param {Wrapped Element} welItem 템플릿 htVar.sTplFileItem 에 의해 생성된 첨부 파일 아이템
	 * @return {String}
	 */
	function _getLinkText(welItem){
		var sMimeType = welItem.attr("data-mime");
		var sFileName = welItem.attr("data-name");
		var sFilePath = welItem.attr("data-href");
		
		var sLinkText = '';
		if (sMimeType.substr(0,5) === "image"){
			sLinkText = '<img src="' + sFilePath + '">';
		} else {
			sLinkText = '[' + sFileName +'](' + sFilePath + ')';
		}
		
		return sLinkText;
	}
	
	/**
	 * textarea에서 해당 파일 아이템의 링크 텍스트를 제거하는 함수
	 * _deleteAttachedFile 에서 호출한다
	 * 
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
     * 문자열에서 경로를 제거하고 파일명만 반환
     * return trailing name component of path
     * 
     * @param {String} sPath
     * @return {String}  
     */
    function _getBasename(sPath){
        var sSeparator = 'fakepath';
        var nPos = sPath.indexOf(sSeparator);       
        return (nPos > -1) ? sPath.substring(nPos + sSeparator.length + 1) : sPath;
    }

    /**
	 * 인터페이스 반환
	 */
	return {
		"init": _init
	};
})();