/**
 * @(#)yobi.Files 2013.08.06
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */
/**
 * yobi.Files
 * 첨부 파일 관리자
 * 
 * - 파일 업로드(.uploadFile, .uploadFiles)
 * - 파일 삭제(.deleteFile)
 * - 첨부 목록 수신(.getList)
 * - 컨테이너 영역에 이벤트 핸들러 설정(.wrap)
 * - 커스텀 이벤트 핸들러(.attach)
 *     - beforeUpload  : 업로드 시작
 *     - uploadProgress: 업로드 진행
 *     - successUpload : 업로드 성공
 *     - errorUpload   : 업로드 실패
 */
yobi.Files = (function(){
    
	var htVar = {};
	var htElements = {};
	var htHandlers = {};
	
	/**
	 * 파일 관리자 초기화 함수
     * initialize fileUploader
	 * 
	 * @param {Hash Table} htOptions
	 * @param {String} htOptions.sListURL   파일 목록 URL
	 * @param {String} htOptions.sUploadURL 파일 전송 URL
	 */
	function _init(htOptions){
		htOptions = htOptions || {};
		
        htVar.sListURL     = htOptions.sListURL;
        htVar.sUploadURL   = htOptions.sUploadURL;
        htVar.htUploadOpts = {"dataType": "json"} || htOptions.htUploadOpts;
        
        htVar.bXHR2 = (typeof FormData != "undefined");
        htVar.bDroppable = (typeof window.ondrop != "undefined");
        htVar.bPastable = (typeof document.onpaste != "undefined");
	}

    /**
     * Upload files
     * 
     * @param {Variant} oFiles FileList or File Object, HTMLInputElement(IE)
     */
    function _uploadFile(oFiles){
        if(oFiles && oFiles.length){
            for(var i = 0; i < oFiles.length; i++){
                _uploadSingleFile(oFiles[i], _getSubmitId());
            }
        } else {
            _uploadSingleFile(oFiles, _getSubmitId());
        }
    }

    /**
     * Upload single file with specified submitId
     * 
     * @param {File} oFile
     * @param {Number} nSubmitId
     */
    function _uploadSingleFile(oFile, nSubmitId){
        // append file on list
        if(oFile){
            oFile.nSubmitId = nSubmitId || _getSubmitId();
        }
        
        // fireEvent: beforeUpload
        var bEventResult = _fireEvent("beforeUpload", {
            "oFile": oFile,
            "nSubmitId": oFile ? oFile.nSubmitId : nSubmitId
        });
        if(bEventResult === false){ // upload cancel by event handler
            return false;
        }
        
        return htVar.bXHR2 ? _uploadFileXHR(oFile, nSubmitId) : _uploadFileForm(oFile, nSubmitId);
    }

    /**
     * Upload file with XHR2
     * available in IE 10+, FF4+, Chrome7+, Safari5+
     * http://caniuse.com/xhr2
     * 
     * @param {File} oFile
     * @param {Number} nSubmitId
     */
    function _uploadFileXHR(oFile, nSubmitId){
        var oData = new FormData();
        oData.append("filePath", oFile, oFile.name);

        $.ajax({
            "type" : "post",
            "url"  : htVar.sUploadURL,
            "data" : oData,
            "cache": false,
            "processData": false,
            "contentType": false,
            "success": function(oRes){
                _onSuccessSubmit(nSubmitId, oRes);
            },
            "error": function(oRes){
                _onErrorSubmit(nSubmitId, oRes);
            },
            "xhrFields": {"onprogress": function(weEvt){
                if(weEvt.lengthComputable){
                    _onUploadProgress(nSubmitId, Math.ceil(weEvt.loaded / weEvt.total));
                }
            }}
        });
    }

    /**
     * Upload file with $.ajaxForm
     * available in almost browsers, except Safari on OSX.
     * http://malsup.com/jquery/form/
     * 
     * @param {File} oFile
     * @param {Number} nSubmitId
     */
    function _uploadFileForm(oFile, nSubmitId){
        // 폼 이벤트 핸들러 설정: nSubmitId 가 필요한 부분만
        var htUploadOpts = htVar.htUploadOpts;
        htUploadOpts.target = htVar.sUploadURL;
        htUploadOpts.success = function(oRes){
            _onSuccessSubmit(nSubmitId, oRes);
        };
        htUploadOpts.error = function(oRes){
            _onErrorSubmit(nSubmitId, oRes);
        };
        htUploadOpts.uploadProgress = function(oEvent, nPos, nTotal, nPercentComplete){
            _onUploadProgress(nSubmitId, nPercentComplete);
        };

        htElements.welFormFile.ajaxForm(htUploadOpts);
        htElements.welFormFile.ajaxSubmit();
    }
    
    /**
     * 파일 업로드 진행상태 처리 함수
     * uploadProgress event handler 
     * 
     * @param {Object} oEvent
     * @param {Number} nPercentComplete
     */
    function _onUploadProgress(nSubmitId, nPercentComplete){
        _fireEvent("uploadProgress", {
            "nSubmitId": nSubmitId,
            "nPercentComplete": nPercentComplete
        });
    }

	/**
	 * 첨부 파일 전송에 성공시 이벤트 핸들러
	 * On success to submit temporary form created in onChangeFile()
	 * 
	 * @param {Hash Table} htData
	 * @return
	 */
	function _onSuccessSubmit(nSubmitId, oRes){
		// Validate server response
		if(!(oRes instanceof Object) || !oRes.name || !oRes.url){
		    return _onErrorSubmit(nSubmitId, oRes);
		}

		// fireEvent: onSuccessSubmit
        _fireEvent("successUpload", {
            "nSubmitId": nSubmitId,
            "oRes": oRes
        });
	}

    /**
	 * 파일 전송에 실패한 경우
	 * On error to submit temporary form created in onChangeFile().
	 * 
	 * @param {Number} nSubmitId
	 * @param {Object} oRes
	 */
	function _onErrorSubmit(nSubmitId, oRes){
	    console.log(oRes);
	    // fireEvent: onError
	    _fireEvent("errorUpload", {
	        "nSubmitId": nSubmitId,
	        "oRes": oRes
	    });
	}

    /**
     * 파일 삭제 요청
     * delete specified file
     * 
     * @param {Hash Table} htOptions
     * @param {String} htOptions.sURL 삭제할 파일 주소
     * @param {Function} htOptions.fOnLoad 성공시 콜백 함수
     * @param {Function} htOptions.fOnError 실패시 콜백 함수
     */
    function _deleteFile(htOptions){
        $yobi.sendForm({
            "sURL"     : htOptions.sURL,
            "fOnLoad"  : htOptions.fOnLoad,
            "fOnError" : htOptions.fOnError,
            "htData"   : {"_method":"delete"},
            "htOptForm": {
                "method" :"post",
                "enctype":"multipart/form-data"
            }
        });
    }

    /**
     * 서버에 첨부파일 목록 요청
     * request attached file list
     * 
     * @param {Hash Table} htOptions
     * @param {String} htOptions.sResourceType 리소스 타입
     * @param {String} htOptions.sResourceId   리소스 식별자 (Optional)
     * @param {Function} htOptions.fOnLoad     성공시 콜백 함수
     * @param {Function} htOptions.fOnError    실패시 콜백 함수
     */
    function _getFileList(htOptions){
        $.ajax({
            "type"   : "get",
            "url"    : htVar.sListURL,
            "success": htOptions.fOnLoad,
            "error"  : htOptions.fOnError,
            "data"   : {
                "containerType": htOptions.sResourceType,
                "containerId"  : htOptions.sResourceId
            }
        });
    }
    
    /**
     * 지정한 컨테이너 영역에 이벤트 핸들러를 설정해서
     * input[type=file] 이나 드래그앤드롭 등의 파일 첨부 기능을 활성화 시켜준다
     * 
     * @param {HTMLElement} elContainer
     * @param {HTMLTextareaElement} elTextarea (Optional)
     */
    function _setUploader(elContainer, elTextarea){
        _initElement(elContainer, elTextarea);
        _attachEvent();
    }
    
    /**
     * 엘리먼트 변수 설정
     * 
     * @param {HTMLElement} elContainer
     * @param {HTMLTextareaElement} elTextarea (Optional)
     */
    function _initElement(elContainer, elTextarea){
        htElements.welContainer = $(elContainer);
        htElements.welTextarea  = $(elTextarea);
        htElements.welInputFile = htElements.welContainer.find("input[type=file]");
        
        // temporarily
        if(htVar.bXHR2 === false){
            htElements.welContainer.hide();
        }
    }
    
    /**
     * 컨테이너 영역에 이벤트 설정
     */
    function _attachEvent(){
        htElements.welInputFile.change(_onChangeFile);
        htElements.welFormFile = $('<form action="' + htVar.sUploadURL + '" method="post" enctype="multipart/form-data"></form>');
        htElements.welInputFile.wrap(htElements.welFormFile);

        // Upload by Drag & Drop
        if(htVar.bDroppable){
            htElements.welContainer.bind("dragover", function(weEvt){
                weEvt.preventDefault();
                return false;
            });
            
            if(htElements.welTextarea){
                htElements.welTextarea.bind("drop", _onDropFile);
            }
            htElements.welContainer.bind("drop", _onDropFile);
        }
        
        // Upload by paste
        if(htVar.bPastable && htElements.welTextarea){
            htElements.welTextarea.bind("paste", _onPasteFile);
        }
    }
    
    /**
     * 파일 선택시 이벤트 핸들러
     * change event handler on input[type="file"]
     */
    function _onChangeFile(){
        var sFileName = _getBasename(htElements.welInputFile.val());
        if(!sFileName || sFileName === ""){
            return;
        }

        _uploadFile(htElements.welInputFile[0].files);
    }

    /**
     * 이미지 데이터를 클립보드에서 붙여넣었을 때 이벤트 핸들러
     * 
     * @param {Wrapped Event} weEvt
     */
    function _onPasteFile(weEvt){
        if(!weEvt.originalEvent.clipboardData || !weEvt.originalEvent.clipboardData.items || !weEvt.originalEvent.clipboardData.items[0]){
            return;
        }
        
        var oItem = weEvt.originalEvent.clipboardData.items[0];
        var nSubmitId = _getSubmitId();
        var oFile = oItem.getAsFile();
        
        if(!oFile || oFile.type.indexOf("image/") !== 0){
            return;
        }
        
        oFile.name = nSubmitId + ".png"; // works in Chrome
        _uploadSingleFile(oFile, nSubmitId);
    }

    /**
     * 파일을 드래그앤드롭해서 가져왔을 때 이벤트 핸들러
     * 
     * @param {Wrapped Event} weEvt
     */
    function _onDropFile(weEvt){
        var oFiles = weEvt.originalEvent.dataTransfer.files;
        if(!oFiles || oFiles.length === 0){
            return;
        }
        _uploadFile(oFiles);

        weEvt.stopPropagation();
        weEvt.preventDefault();
        return false;
    }

    /**
     * Get submitId for each upload
     * 
     * @return {Number}
     */
    function _getSubmitId(){
        return parseInt(Math.random() * new Date().getTime());
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
     * Attach custom event handler
     * 
     * @param {String} sEventName
     * @param {Function} fHandler
     */
    function _attachCustomEvent(sEventName, fHandler){
        if(typeof sEventName === "object"){
            for(var sKey in sEventName){
                htHandlers[sKey] = htHandlers[sKey] || [];
                htHandlers[sKey].push(sEventName[sKey]);
            }
        } else {
            htHandlers[sEventName] = htHandlers[sEventName] || [];
            htHandlers[sEventName].push(fHandler);
        }
    }
    
    /**
     * Detach custom event handler
     * clears all handler of sEventName when fHandler is empty
     * 
     * @param {String} sEventName
     * @param {Function} fHandler 
     */
    function _detachCustomEvent(sEventName, fHandler){
        if(!fHandler){
            htHandlers[sEventName] = [];
            return;
        }
        
        var nIndex = htHandlers[sEventName].indexOf(fHandler);
        if(nIndex > -1){
            htHandlers[sEventName].splice(nIndex, 1);
        }
    }
    
    /**
     * Run specified custom event handlers
     * 
     * @param {String} sEventName
     * @param {Object} oData
     */
    function _fireEvent(sEventName, oData){
        var aHandlers = htHandlers[sEventName];
        if((aHandlers instanceof Array) === false){
            return;
        }

        var bResult;
        aHandlers.forEach(function(fHandler){
            bResult = bResult || fHandler(oData);
        });
        
        return bResult;
    }

    // public interface
	return {
	    "init"       : _init,
	    "setUploader": _setUploader,
	    "attach"     : _attachCustomEvent,
	    "detach"     : _detachCustomEvent,
	    "getList"    : _getFileList,
		"uploadFile" : _uploadFile,
		"deleteFile" : _deleteFile
	};
})();