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
 * - 파일 업로드(.uploadFile)
 * - 파일 삭제  (.deleteFile)
 * - 첨부 목록 수신(.getList)
 * - 업로더 영역 설정(.setUploader)
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
        
        htVar.bXHR2 = (typeof FormData != "undefined"); // XMLHttpRequest 2 required
        htVar.bDroppable = (typeof window.File != "undefined"); // HTML5 FileAPI required
        htVar.bPastable = (typeof document.onpaste != "undefined") && htVar.bXHR2; // onpaste & XHR2 required        
    }

    /**
     * Returns Environment information
     * @return {Hash Table}
     */
    function _getEnv(){
        return htVar;
    }
    
    /**
     * Upload files
     * 
     * @param {Variant} oFiles FileList or File Object, HTMLInputElement(IE)
     * @param {String} sNamespace (Optional)
     */
    function _uploadFile(oFiles, sNamespace){
        if(oFiles && oFiles.length){
            for(var i = 0; i < oFiles.length; i++){
                _uploadSingleFile(oFiles[i], _getSubmitId(), sNamespace);
            }
        } else {
            _uploadSingleFile(oFiles, _getSubmitId(), sNamespace);
        }
    }

    /**
     * Upload single file with specified submitId
     * 
     * @param {File} oFile
     * @param {Number} nSubmitId
     * @param {String} sNamespace (Optional)
     */
    function _uploadSingleFile(oFile, nSubmitId, sNamespace){
        // append file on list
        if(oFile){
            oFile.nSubmitId = nSubmitId || _getSubmitId();
        }
        
        // fireEvent: beforeUpload
        var bEventResult = _fireEvent("beforeUpload", {
            "oFile": oFile,
            "nSubmitId": oFile ? oFile.nSubmitId : nSubmitId
        }, sNamespace);
        if(bEventResult === false){ // upload cancel by event handler
            return false;
        }
        
        return htVar.bXHR2 ? _uploadFileXHR(nSubmitId, oFile, sNamespace) : _uploadFileForm(nSubmitId, oFile, sNamespace);
    }

    /**
     * Upload file with XHR2
     * available in IE 10+, FF4+, Chrome7+, Safari5+
     * Reference: http://caniuse.com/xhr2
     * 
     * @param {Number} nSubmitId
     * @param {File} oFile
     * @param {String} sNamespace
     */
    function _uploadFileXHR(nSubmitId, oFile, sNamespace){
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
                _onSuccessSubmit(nSubmitId, oRes, sNamespace);
            },
            "error": function(oRes){
                _onErrorSubmit(nSubmitId, oRes, sNamespace);
            },
            "xhrFields": {"onprogress": function(weEvt){
                if(weEvt.lengthComputable){
                    _onUploadProgress(nSubmitId, Math.ceil(weEvt.loaded / weEvt.total), sNamespace);
                }
            }}
        });
    }

    /**
     * Upload file with $.ajaxForm
     * available in almost browsers, except Safari on OSX.
     * Reference: http://malsup.com/jquery/form/
     * 
     * @param {Number} nSubmitId
     * @param {HTMLElement} elFile
     * @param {String} sNamespace
     */
    function _uploadFileForm(nSubmitId, elFile, sNamespace){
        var htElement = htElements[sNamespace];
        
        // FileForm 이용한 업로드는 input[type=file] 이 반드시 필요하다
        if(!htElement.welInputFile && !elFile){
            return false;
        }
        
        var welInputFile = htElement.welInputFile || $(elFile); // 원래의 file input
        var welInputFileClone = welInputFile.clone();            // 새로 끼워넣을 복제품.
        var welForm = $('<form method="post" enctype="multipart/form-data" style="display:none">');

        welInputFileClone.insertAfter(welInputFile); // 예전 input 뒤에 끼워넣고
        welInputFileClone.change(_onChangeFile);     // 이벤트 핸들러
        htElement.welInputFile = welInputFileClone; // 레퍼런스 교체

        welForm.attr('action', htVar.sUploadURL);
        welForm.append(welInputFile).appendTo(document.body);

        // free memory finally
        var fClear = function(){
            welInputFile.remove();
            welForm.remove();
            welForm = welInputFile = null;
        };
        
        // 폼 이벤트 핸들러 설정: nSubmitId 가 필요한 부분만
        var htUploadOpts = htVar.htUploadOpts;
        htUploadOpts.success = function(oRes){
            _onSuccessSubmit(nSubmitId, oRes, sNamespace);
            fClear();
            fClear = null;
        };
        htUploadOpts.uploadProgress = function(oEvent, nPos, nTotal, nPercentComplete){
            _onUploadProgress(nSubmitId, nPercentComplete, sNamespace);
            fClear();
            fClear = null;
        };
        htUploadOpts.error = function(oRes){
            _onErrorSubmit(nSubmitId, oRes, sNamespace);
            fClear();
            fClear = null;
        };

        welForm.ajaxForm(htUploadOpts);
        welForm.submit();
    }
        
    /**
     * 파일 업로드 진행상태 처리 함수
     * uploadProgress event handler 
     * 
     * @param {Object} oEvent
     * @param {Number} nPercentComplete
     */
    function _onUploadProgress(nSubmitId, nPercentComplete, sNamespace){
        _fireEvent("uploadProgress", {
            "nSubmitId": nSubmitId,
            "nPercentComplete": nPercentComplete
        }, sNamespace);
    }

    /**
     * 첨부 파일 전송에 성공시 이벤트 핸들러
     * On success to submit temporary form created in onChangeFile()
     * 
     * @param {Hash Table} htData
     * @return
     */
    function _onSuccessSubmit(nSubmitId, oRes, sNamespace){
        // Validate server response
        if(!(oRes instanceof Object) || !oRes.name || !oRes.url){
            return _onErrorSubmit(nSubmitId, oRes);
        }

        // clear inputFile
        if(sNamespace && htElements[sNamespace].welInputFile){
            htElements[sNamespace].welInputFile.val("");
        }

        // fireEvent: onSuccessSubmit        
        _fireEvent("successUpload", {
            "nSubmitId": nSubmitId,
            "oRes": oRes
        }, sNamespace);
    }

    /**
     * 파일 전송에 실패한 경우
     * On error to submit temporary form created in onChangeFile().
     * 
     * @param {Number} nSubmitId
     * @param {Object} oRes
     */
    function _onErrorSubmit(nSubmitId, oRes, sNamespace){
        // fireEvent: onError
        _fireEvent("errorUpload", {
            "nSubmitId": nSubmitId,
            "oRes": oRes
        }, sNamespace);
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
     * 래핑된 컨테이너 엘리먼트에 이벤트 구분을 위한 네임스페이스ID 를 부여해서 반환
     * 
     * @param {HTMLElement} elContainer
     * @param {HTMLTextareaElement} elTextarea (Optional)
     * @return {Wrapped Element}
     */
    function _getUploader(elContainer, elTextarea, sNamespace){
        sNamespace = sNamespace || _getSubmitId();
        
        _initElement({
            "elContainer": elContainer, 
            "elTextarea" : elTextarea,
            "sNamespace" : sNamespace
        });
        _attachEvent(sNamespace);
        
        return htElements[sNamespace].welContainer;
    }
    
    /**
     * 엘리먼트 변수 설정
     * 
     * @param {Hash Table} htOptions
     * @param {HTMLElement} htOptions.elContainer
     * @param {HTMLTextareaElement} htOptions.elTextarea (Optional)
     * @param {String} sNamespace
     */
    function _initElement(htOptions){
        var sNamespace = htOptions.sNamespace;

        htElements[sNamespace] = {};
        htElements[sNamespace].welContainer = $(htOptions.elContainer);
        htElements[sNamespace].welTextarea  = $(htOptions.elTextarea);
        htElements[sNamespace].welInputFile = htElements[sNamespace].welContainer.find("input[type=file]");
        htElements[sNamespace].welContainer.attr("data-namespace", sNamespace);
    }
    
    /**
     * 컨테이너 영역에 이벤트 설정
     * 
     * @param {String} sNamespace
     */
    function _attachEvent(sNamespace){
        var htElement = htElements[sNamespace];
        htElement.welInputFile.change(function(weEvt){
            _onChangeFile(sNamespace, weEvt);
        });

        // Upload by Drag & Drop
        if(htVar.bDroppable){
            htElement.welContainer.bind("dragover", function(weEvt){
                weEvt.preventDefault();
                return false;
            });
            
            if(htElement.welTextarea){
                htElement.welTextarea.bind("drop", function(weEvt){
                    _onDropFile(sNamespace, weEvt);
                });
            }
            htElement.welContainer.bind("drop", function(weEvt){
                _onDropFile(sNamespace, weEvt);
            });
        }
        
        // Upload by paste
        if(htVar.bPastable && htElement.welTextarea){
            htElement.welTextarea.bind("paste", function(weEvt){
                _onPasteFile(sNamespace, weEvt);
            });
        }
    }
    
    /**
     * 파일 선택시 이벤트 핸들러
     * change event handler on input[type="file"]
     * 
     * @param {String} sNamespace
     */
    function _onChangeFile(sNamespace){
        var htElement = htElements[sNamespace];
        var sFileName = _getBasename(htElement.welInputFile.val());
        if(!sFileName || sFileName === ""){
            return;
        }

        _uploadFile(htElement.welInputFile[0].files || htElement.welInputFile[0], sNamespace);
    }

    /**
     * 이미지 데이터를 클립보드에서 붙여넣었을 때 이벤트 핸들러
     * 
     * @param {String} sNamespace
     * @param {Wrapped Event} weEvt
     */
    function _onPasteFile(sNamespace, weEvt){
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
        _uploadSingleFile(oFile, nSubmitId, sNamespace);
    }

    /**
     * 파일을 드래그앤드롭해서 가져왔을 때 이벤트 핸들러
     * 
     * @param {String} sNamespace
     * @param {Wrapped Event} weEvt
     */
    function _onDropFile(sNamespace, weEvt){
        var oFiles = weEvt.originalEvent.dataTransfer.files;
        if(!oFiles || oFiles.length === 0){
            return;
        }
        _uploadFile(oFiles, sNamespace);

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
     * @param {String} sNamespace
     * @example
     * yobi.Files.attach("eventName", function(){}, "namespace");
     * // or
     * yobi.Files.attach({
     *    "event1st": function(){},
     *    "event2nd": function(){}
     * }, "namespace");
     */
    function _attachCustomEvent(sEventName, fHandler, sNamespace){
        if(typeof sEventName === "object"){
            sNamespace = fHandler ? (fHandler+".") : "";
            for(var sKey in sEventName){
                htHandlers[sNamespace + sKey] = htHandlers[sNamespace + sKey] || [];
                htHandlers[sNamespace + sKey].push(sEventName[sKey]);
            }
        } else {
            sNamespace = sNamespace ? (sNamespace+".") : "";
            htHandlers[sNamespace + sEventName] = htHandlers[sNamespace + sEventName] || [];
            htHandlers[sNamespace + sEventName].push(fHandler);
        }
    }
    
    /**
     * Detach custom event handler
     * clears all handler of sEventName when fHandler is empty
     * 
     * @param {String} sEventName
     * @param {Function} fHandler
     * @param {String} sNamespace
     */
    function _detachCustomEvent(sEventName, fHandler, sNamespace){
        sNamespace = sNamespace ? (sNamespace+".") : "";
        
        if(!fHandler){
            htHandlers[sNamespace + sEventName] = [];
            return;
        }
        
        var nIndex = htHandlers[sNamespace + sEventName].indexOf(fHandler);
        if(nIndex > -1){
            htHandlers[sNamespace + sEventName].splice(nIndex, 1);
        }
    }
    
    /**
     * Run specified custom event handlers
     * 
     * @param {String} sEventName
     * @param {Object} oData
     * @param {String} sNamespace
     */
    function _fireEvent(sEventName, oData, sNamespace){
        sNamespace = sNamespace ? (sNamespace+".") : "";

        var aGlobalHandlers = htHandlers[sEventName] || [];
        var aLocalHandlers = htHandlers[sNamespace + sEventName] || [];
        var aHandlers = aGlobalHandlers.concat(aLocalHandlers);
        
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
        "getEnv"     : _getEnv,
        "setUploader": _getUploader,
        "getUploader": _getUploader,
        "attach"     : _attachCustomEvent,
        "detach"     : _detachCustomEvent,
        "getList"    : _getFileList,
        "uploadFile" : _uploadFile,
        "deleteFile" : _deleteFile
    };
})();