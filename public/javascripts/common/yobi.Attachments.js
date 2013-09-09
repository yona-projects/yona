/**
 * @(#)yobi.Attachments 2013.08.19
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://yobi.dev.naver.com/license
 */
/**
 * 서버와 직접 통신하는 영역은 yobi.Files 를 사용한다.
 * yobi.Files 의 초기화 작업은 이미 scripts.scala.html 에서 전역으로 되어 있다.
 * yobi.Attachments 의 역할은 첨부파일 목록을 표현하는데 있다.
 */
yobi.Attachments = function(htOptions) {
    var htVar = {};
    var htElements = {};
    
    /**
     * initialize fileUploader
     * 파일 업로더 초기화 함수. fileUploader.init(htOptions) 으로 사용한다.
     * 
     * @param {Hash Table} htOptions
     * @param {Variant} htOptions.elContainer   첨부파일 목록을 표현할 컨테이느 엘리먼트
     * @param {Variant} htOptions.elTextarea    첨부파일 클릭시 이 영역에 태그를 삽입한다
     * @param {String}  htOptions.sTplFileList  첨부한 파일명을 표시할 목록 HTML 템플릿
     * @param {String}  htOptions.sTplFileItem  첨부한 파일명을 표시할 HTML 템플릿
     * @param {String}  htOptions.sResourceId   리소스 ID
     * @param {String}  htOptions.sResourceType 리소스 타입
     * @param {String}  htOptions.sUploaderID   업로더ID (Optional).
     */
    function _init(htOptions){
        htOptions = htOptions || {};
        
        _initVar(htOptions);
        _initElement(htOptions);    
        _requestList(); // 첨부파일 목록 호출 (ResourceType, ResourceId 이용)
        
        // 업로더 ID가 지정된 경우, 해당 업로더의 커스텀 이벤트에 맞추어
        // 첨부파일 목록을 제어하도록 이벤트 핸들러를 설정한다
        if(htOptions.sUploaderId){
            _attachUploaderEvent(htOptions.sUploaderId);
        }
    }

    /**
     * 변수 초기화
     * initialize variables
     * 
     * @param {Hash Table} htOptions 초기화 옵션
     */
    function _initVar(htOptions){
        htVar.sTplFileList = htOptions.sTplFileList || '<ul class="attaches wm">';
        htVar.sTplFileItem = htOptions.sTplFileItem || '<li class="attach"><a href="${fileHref}"><i class="yobicon-paperclip"></i>${fileName} (${fileSizeReadable})</a></li>';
        htVar.sResourceId = htOptions.sResourceId; // ResId: Optional
        htVar.sResourceType = htOptions.sResourceType; // ResType: Required
    }

    /**
     * 엘리먼트 초기화
     * initialize elements
     * 
     * @param {Hash Table} htOptions 초기화 옵션
     */
    function _initElement(htOptions){
        // welContainer
        htElements.welContainer = $(htOptions.elContainer);
        htVar.sResourceId = htVar.sResourceId || htElements.welContainer.attr('data-resourceId');
        htVar.sResourceType = htVar.sResourceType || htElements.welContainer.attr('data-resourceType');
        
        // welTextarea (Optional)
        htElements.welTextarea  = $(htOptions.elTextarea);
        
        // attached files list
        htElements.welFileList  = htElements.welContainer.find("ul.attached-files");
        htElements.welFileListHelp = htElements.welContainer.find("p.help");
        
        // -- help messages for additional uploader features
        var htEnv = yobi.Files.getEnv();
        htElements.welHelpDroppable = htElements.welContainer.find(".help-droppable");
        htElements.welHelpPastable  = htElements.welContainer.find(".help-pastable");
        htElements.welHelpDroppable[htEnv.bDroppable ? "show" : "hide"]();
        htElements.welHelpPastable[htEnv.bPastable ? "show" : "hide"]();
    }

    /**
     * 이벤트 핸들러 설정
     * attach Uploader custom event handlers
     * 
     * @param {String} sUploaderId
     */
    function _attachUploaderEvent(sUploaderId){
        yobi.Files.attach({
            "beforeUpload"  : _onBeforeUpload,
            "uploadProgress": _onUploadProgress,
            "successUpload" : _onSuccessUpload,
            "errorUpload"   : _onErrorUpload
        }, sUploaderId);
    }
    
    /**
     * beforeUpload single file
     * 
     * @param {File} htOptions.oFile
     * @param {Number} htOptions.nSubmitId
     */
    function _onBeforeUpload(htOptions){
        _appendFileItem({
            "vFile"     : (htOptions.oFile.files ? htOptions.oFile.files[0] : htOptions.oFile), // Object
            "bTemporary": true
        });
    }

    /**
     * 파일 항목을 첨부 파일 목록에 추가한다
     * 
     * 이미 전송된 파일 목록은 _onLoadRequest 에서 호출하고
     * 아직 전송전 임시 파일은 _uploadFile    에서 호출한다
     *  
     * oFile.id 가 존재하는 경우는 이미 전송된 파일 항목이고
     * oFile.id 가 없는 경우는 전송대기 상태의 임시 항목이다
     *  
     * @param {Hash Table} htData
     * @param {Variant} htData.vFile      하나의 파일 항목 객체(Object) 또는 여러 파일 항목을 담고 있는 배열(Array)
     * @param {Boolean} htData.bTemporary 임시 저장 여부
     * 
     * @return {Number} 이번에 목록에 추가된 파일들의 크기 합계
     */
    function _appendFileItem(htData){
        if(typeof htData.vFile === "undefined"){
            return 0;
        }

        var welItem;
        var nFileSize = 0;
        var aWelItems = [];
        var aFiles = (htData.vFile instanceof Array) ? htData.vFile : [htData.vFile]; // 배열 변수로 단일화

        aFiles.forEach(function(oFile) {
            welItem = _getFileItem(oFile, htData.bTemporary);

            if(typeof oFile.id !== "undefined" && oFile.id !== ""){ 
                // 서버의 첨부 목록에서 가져온 경우
                welItem.addClass("complete");
                
                // textarea 가 있는 경우에만 클릭 이벤트 핸들러 추가
                if(htElements.welTextarea.length > 0){
                    welItem.click(_onClickListItem);
                }
            } else {
                // 전송하기 전의 임시 항목인 경우
                welItem.attr("id", oFile.nSubmitId);
                welItem.css("opacity", "0.2");
                welItem.data("progressBar", welItem.find(".progress > .bar"));                
            }
            
            aWelItems.push(welItem);
            nFileSize += parseInt(oFile.size, 10);
        });

        // 추가할 항목이 있는 경우에만
        if(aWelItems.length > 0){
            if(htElements.welFileList.length === 0){
                htElements.welFileList = $(htVar.sTplFileList);
                htElements.welContainer.append(htElements.welFileList);
            }
            htElements.welFileList.show();
            htElements.welFileListHelp.show();

            // DOM 변형 작업은 한번에 하는게 성능향상
            htElements.welFileList.append(aWelItems);
        }

        return nFileSize;
    }
    
    /**
     * 파일 목록에 추가할 수 있는 LI 엘리먼트를 반환하는 함수
     * Create uploaded file item HTML element using template string
     * 
     * @param {Hash Table} htFile 파일 정보
     * @param {Boolean} bTemp 임시 파일 여부
     * 
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
        
        // for IE (uploadFileForm)
        welItem.find(".name").html(oRes.name);
        welItem.find(".size").html(humanize.filesize(oRes.size));
        
        welItem.click(_onClickListItem);
    }

    /**
     * 첨부 파일 전송에 성공시 이벤트 핸들러
     * On success to submit temporary form created in onChangeFile()
     * 
     * @param {Hash Table} htData
     * @param {Number} htData.nSubmitId
     * @param {Object} htData.oRes
     * @return
     */
    function _onSuccessUpload(htData){
        var oRes = htData.oRes;
        var nSubmitId = htData.nSubmitId;
        
        // Validate server response
        if(!(oRes instanceof Object) || !oRes.name || !oRes.url){
            return _onErrorUpload(nSubmitId, oRes);
        }

        // 업로드 완료된 뒤 항목 업데이트
        _updateFileItem(nSubmitId, oRes);
        _setProgressBar(nSubmitId, 100);
    }

    /**
     * 파일 업로드 진행상태 처리 함수
     * uploadProgress event handler 
     * 
     * @param {Hash Table} htData
     * @param {Number} htData.nSubmitId
     * @param {Number} htData.nPercentComplete
     */
    function _onUploadProgress(htData){
        _setProgressBar(htData.nSubmitId, htData.nPercentComplete);
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
     * @param {Hash Table} htData
     * @param {Number} htData.nSubmitId
     * @param {Object} htData.oRes
     */
    function _onErrorUpload(htData){
        $("#" + htData.nSubmitId).remove();
        
        // 항목이 없으면 목록 감춤
        if(htElements.welFileList.children().length === 0){
            htElements.welFileList.hide();
            htElements.welFileListHelp.hide();
        }
        
        $yobi.notify(Messages("attach.error", htData.oRes.status, htData.oRes.statusText));
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
        if(welTextarea.length === 0){
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
       var sURL = welItem.attr("data-href");
       yobi.Files.deleteFile({
           "sURL"   : sURL,
           "fOnLoad": function(){
                _clearLinkInTextarea(welItem);
                welItem.remove();

                // 남은 항목이 없으면 목록 감춤
                if(htElements.welFileList.children().length === 0){
                    htElements.welFileList.hide();
                    htElements.welFileListHelp.hide();
                }
            },
            "fOnError": function(){
                $yobi.alert(Messages("error.internalServerError"));
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
        if(welTextarea.length === 0){
            return false;
        }
        
        var sLink = _getLinkText(welItem);        
        welTextarea.val(welTextarea.val().split(sLink).join(''));        
    }
    
    /**
     * 서버에 첨부파일 목록 요청
     * request attached file list
     */
    function _requestList(){
        yobi.Files.getList({
            "fOnLoad"      : _onLoadRequest,
            "sResourceType": htVar.sResourceType,
            "sResourceId"  : htVar.sResourceId
        });
    }
    
    /**
     * 서버에서 수신한 첨부파일 목록 처리함수
     * 
     * @param {Object} oRes
     */
    function _onLoadRequest(oRes) {
        // 이미 첨부되어 있는 파일
        _appendFileItem({
            "vFile"     : oRes.attachments, // Array
            "bTemporary": false
        });
        
        // 임시 파일 (저장하면 첨부됨) : 업로더 상태에서만 표시
        if(typeof htVar.sResourceId === "undefined"){
            _appendFileItem({
                "vFile"     : oRes.tempFiles,   // Array 
                "bTemporary": true
            });
        }
    }

    // call initiator
    _init(htOptions || {});
};