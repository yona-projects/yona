/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
yona.Files = (function(){
    var htVar = {};
    var htElements = {};
    var htHandlers = {};

    /**
     * 호출부가 아직 jQuery 객체를 넘기는 곳과 raw DOM 엘리먼트를 넘기는 곳이 섞여 있어
     * 양쪽에 셀렉터 문자열까지 함께 받아 정규화한다.
     *
     * @param {Variant} el
     * @return {HTMLElement|null}
     */
    function _toElement(el){
        if(el && el.jquery){
            return el[0] || null;
        }
        if(typeof el === "string"){
            return document.querySelector(el);
        }
        return el || null;
    }

    /**
     * initialize fileUploader
     *
     * @param {Hash Table} htOptions
     * @param {String} htOptions.sListURL
     * @param {String} htOptions.sUploadURL
     */
    function _init(htOptions){
        htOptions = htOptions || {};

        htVar.sListURL     = htOptions.sListURL;
        htVar.sUploadURL   = htOptions.sUploadURL;
        htVar.htUploadOpts = htOptions.htUploadOpts || {"dataType": "json"};

        // XMLHttpRequest2 file upload
        // The FileReader API is not actually used, but works as feature detection.
        // Check for window.ProgressEvent instead to detect XHR2 file upload capability
        // ref: http://blueimp.github.io/jQuery-File-Upload
        htVar.bXHR2 = !!(window.ProgressEvent && window.FileReader) && !!window.FormData;

        // HTTPS connection is required for XHR upload on MSIE Browsers
        // even if FormData feature available.
        if(navigator.userAgent.toLowerCase().indexOf("trident") > -1){
            htVar.bXHR2 = htVar.bXHR2 && (location.protocol.toLowerCase().indexOf("https") > -1);
        }

        // HTML5 FileAPI required
        htVar.bDroppable = (typeof window.File != "undefined") && htVar.bXHR2;

        // onpaste & XHR2 required
        htVar.bPastable = (typeof document.onpaste != "undefined") && htVar.bXHR2
                       && (navigator.userAgent.indexOf("FireFox") === -1); // and not FireFox

        // maximum filesize (<= 2,147,483,454 bytes = 2Gb)
        htVar.nMaxFileSize = htOptions.maxFileSize || 2147483454;
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
        // check maximum filesize (<= 2,147,483,454 bytes) if available
        if(oFile.size && oFile.size > htVar.nMaxFileSize){
            return _onErrorSubmit(nSubmitId, {
                "status"    : humanize.filesize(oFile.size),
                "statusText": Messages("error.toolargefile", humanize.filesize(htVar.nMaxFileSize))
            }, sNamespace);
        }

        var oData = new FormData();
        var filename = oFile.name === 'image.png' ? nSubmitId + ".png" : oFile.name;
        oData.append("filePath", oFile, filename);

        // fetch는 업로드 진행률(uploadProgress)을 지원하지 않으므로(다운로드 스트림만 가능),
        // jQuery $.ajax의 xhr: 커스터마이징이 하던 일(XMLHttpRequest.upload의 progress 이벤트
        // 구독)을 그대로 유지하기 위해 순수 XMLHttpRequest를 쓴다.
        var oXHR = new XMLHttpRequest();
        oXHR.open("POST", htVar.sUploadURL);

        // site/layout.html의 전역 CSRF 자동 주입 패치는 jQuery.ajaxSend와 window.fetch만
        // 감싸고 순수 XMLHttpRequest는 다루지 않는다 - 이 파일만 진행률 이벤트 때문에 XHR을
        // 직접 쓰는 사각지대라, 실제로 파일 업로드가 항상 403으로 실패하는 것을 발견해
        // 여기서 동일한 방식(XSRF-TOKEN 쿠키 원문을 X-XSRF-TOKEN 헤더로)으로 직접 주입한다.
        var sCsrfCookieMatch = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
        if(sCsrfCookieMatch){
            oXHR.setRequestHeader("X-XSRF-TOKEN", decodeURIComponent(sCsrfCookieMatch[1]));
        }

        if(oXHR.upload){
            oXHR.upload.addEventListener("progress", function(weEvt){
                if(weEvt.lengthComputable){
                    _onUploadProgress(nSubmitId, Math.ceil((weEvt.loaded / weEvt.total) * 100), sNamespace);
                }
            }, false);
        }

        oXHR.addEventListener("load", function(){
            if(oXHR.status >= 200 && oXHR.status < 300){
                var oRes;
                try{
                    oRes = JSON.parse(oXHR.responseText);
                }catch(e){
                    oRes = oXHR.responseText;
                }
                _onSuccessSubmit(nSubmitId, oRes, sNamespace);
            } else {
                _onErrorSubmit(nSubmitId, oXHR, sNamespace);
            }
        });
        oXHR.addEventListener("error", function(){
            _onErrorSubmit(nSubmitId, oXHR, sNamespace);
        });

        oXHR.send(oData);
    }

    /**
     * Upload file with $.ajaxForm
     * available in almost browsers, except Safari on OSX.
     * Reference: http://malsup.com/jquery/form/
     *
     * htVar.bXHR2가 false인 구형 브라우저(XHR2/FormData/FileReader 미지원)에서만 호출되는
     * legacy 폴백 경로다. jQuery Form 플러그인(.ajaxForm())에 강하게 결합돼 있어 그대로
     * 두었다 - 현대 브라우저는 모두 bXHR2가 true라 이 경로 자체가 실행되지 않는다.
     *
     * @param {Number} nSubmitId
     * @param {HTMLElement} elFile
     * @param {String} sNamespace
     */
    function _uploadFileForm(nSubmitId, elFile, sNamespace){
        var htElement = htElements[sNamespace];
        var welInputFileRaw = (htElement && htElement.welInputFile) || elFile;

        if(!welInputFileRaw){
            return false;
        }

        var welInputFile = $(welInputFileRaw);
        var welInputFileClone = welInputFile.clone();
        var welForm = $('<form method="post" enctype="multipart/form-data" style="display:none">');

        welInputFileClone.insertAfter(welInputFile);
        welInputFileClone.on("change", $.proxy(_onChangeFile, this, sNamespace));
        htElement.welInputFile = welInputFileClone;

        welForm.attr('action', htVar.sUploadURL);
        welForm.append(welInputFile).appendTo(document.body);

        // free memory finally
        var fClear = function(){
            welInputFile.remove();
            welForm.remove();
            welForm = welInputFile = null;
        };

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

        // clear inputFile - htElements[sNamespace].welInputFile은 평소엔 raw element지만,
        // _uploadFileForm(legacy 폴백, 위 주석 참고)을 거치면 jQuery clone으로 바뀐다 - 양쪽 다
        // 지원.
        if(sNamespace && htElements[sNamespace] && htElements[sNamespace].welInputFile){
            var welInputFileRef = htElements[sNamespace].welInputFile;
            if(welInputFileRef.jquery){
                welInputFileRef.val("");
            } else {
                welInputFileRef.value = "";
            }
        }

        // fireEvent: onSuccessSubmit
        _fireEvent("successUpload", {
            "nSubmitId": nSubmitId,
            "oRes": oRes
        }, sNamespace);
    }

    /**
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
     * delete specified file
     *
     * @param {Hash Table} htOptions
     * @param {String} htOptions.sURL
     * @param {Function} htOptions.fOnLoad
     * @param {Function} htOptions.fOnError
     */
    function _deleteFile(htOptions){
        $yona.sendForm({
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
     * request attached file list
     *
     * @param {Hash Table} htOptions
     * @param {String} htOptions.sResourceType
     * @param {String} htOptions.sResourceId
     * @param {Function} htOptions.fOnLoad
     * @param {Function} htOptions.fOnError
     */
    function _getFileList(htOptions){
        // jQuery의 $.param()은 값이 undefined/null인 키를 빈 문자열로 직렬화했다(키 자체는
        // 유지) - URLSearchParams는 undefined를 문자열 "undefined"로 바꿔버리므로(String(undefined)),
        // sResourceId가 없는 호출(단일 리소스가 아닌 페이지)에서 동일하게 동작하도록 빈 문자열로
        // 맞춰준다.
        fetch(htVar.sListURL + "?" + new URLSearchParams({
            "containerType": htOptions.sResourceType || "",
            "containerId"  : htOptions.sResourceId || ""
        }))
        .then(function(response){
            if(!response.ok){
                return Promise.reject(response);
            }
            return response.json();
        })
        .then(function(data){
            if(typeof htOptions.fOnLoad === "function"){
                htOptions.fOnLoad(data);
            }
        })
        .catch(function(err){
            if(typeof htOptions.fOnError === "function"){
                htOptions.fOnError(err);
            }
        });
    }

    /**
     * @param {HTMLElement} elContainer
     * @param {HTMLTextareaElement} elTextarea (Optional)
     * @param {String} sNamespace
     * @return {Wrapped Element}
     */
    function _getUploader(elContainer, elTextarea, sNamespace){
        sNamespace = sNamespace || _getSubmitId();

        var elContainerNode = _toElement(elContainer);
        var elTextareaNode  = _toElement(elTextarea);

        // only single uploader can be attached on single Container/Textarea
        // (isYonaUploader는 이 파일 내부에서만 쓰는 플래그라 - 다른 파일이 읽지 않음 - 코드베이스
        // 관례대로 커스텀 expando 프로퍼티로 boolean 값을 그대로 보존한다, dataset 문자열
        // 변환 함정 회피)
        if((elContainerNode && elContainerNode._yonaIsUploader) || (elTextareaNode && elTextareaNode._yonaIsUploader)){
            return false;
        }

        _initElement({
            "elContainer": elContainerNode,
            "elTextarea" : elTextareaNode,
            "sNamespace" : sNamespace
        });

        // <yona-attachments> 컨테이너는 drag/drop/paste/input-change를 자기 Shadow DOM
        // 안에서 직접 소유한다 - dragover/drop은 Shadow DOM 경계를 넘어 전파되는 합성
        // 이벤트라, 여기서 컨테이너에 동일한 리스너를 또 걸면 업로드가 중복 실행된다.
        // data-namespace 설정과 반환값 모양은 그대로 유지하고 리스너 연결만 건너뛴다.
        if(elContainerNode && elContainerNode.tagName.toLowerCase() === "yona-attachments"){
            elContainerNode._yonaIsUploader = true;
            return [htElements[sNamespace].welContainer];
        }

        _attachEvent(sNamespace);

        // 모든 호출부가 이미 raw element 관례(oUploader[0].getAttribute(...))를 쓰고
        // .attr()로 접근하는 곳이 없어, jQuery 컬렉션일 필요 없이 동일한 [0] 인덱싱
        // 계약만 유지한 채 순수 배열로 바꾼다.
        return [htElements[sNamespace].welContainer];
    }

    /**
     * @param {String} sNamespace
     */
    function _destroyUploader(sNamespace){
        if(sNamespace && htElements[sNamespace]){
            _detachEvent(sNamespace);
            delete htElements[sNamespace];
        }
    }

    /**
     * @param {Hash Table} htOptions
     * @param {HTMLElement} htOptions.elContainer
     * @param {HTMLTextareaElement} htOptions.elTextarea (Optional)
     * @param {String} sNamespace
     */
    function _initElement(htOptions){
        var sNamespace = htOptions.sNamespace;

        htElements[sNamespace] = {};
        htElements[sNamespace].welContainer = htOptions.elContainer;
        htElements[sNamespace].welTextarea  = htOptions.elTextarea;
        htElements[sNamespace].welInputFile = htOptions.elContainer ? htOptions.elContainer.querySelector("input[type=file]") : null;

        if(htOptions.elContainer){
            htOptions.elContainer.setAttribute("data-namespace", sNamespace);
        }

        if(!htVar.bXHR2 && htElements[sNamespace].welInputFile){
            htElements[sNamespace].welInputFile.removeAttribute("multiple");
        }
    }

    /**
     * @param {String} sNamespace
     */
    function _attachEvent(sNamespace){
        var htElement = htElements[sNamespace];
        var welContainer = htElement.welContainer;
        var welTextarea  = htElement.welTextarea;

        htElement._onChangeFileHandler = function(){ _onChangeFile(sNamespace); };
        if(htElement.welInputFile){
            htElement.welInputFile.addEventListener("change", htElement._onChangeFileHandler);
        }

        // Upload by Drag & Drop
        if(htVar.bDroppable && welContainer){
            htElement._onDragOverHandler = function(weEvt){ _onDragOver(sNamespace, weEvt); };
            htElement._onDropFileHandler = function(weEvt){ _onDropFile(sNamespace, weEvt); };
            welContainer.addEventListener("dragover", htElement._onDragOverHandler);
            welContainer.addEventListener("drop", htElement._onDropFileHandler);

            var elTplDropper = document.getElementById("tplDropFilesHere");
            var sTplDropper = (elTplDropper ? elTplDropper.textContent.trim() : "") ||
                '<div class="upload-drop-here"><div class="msg-wrap"><div class="msg">' +
                Messages("common.attach.dropFilesHere") +
                '</div></div></div>';
            var elTemplateHolder = document.createElement("div");
            elTemplateHolder.innerHTML = sTplDropper;
            htElement.welDropper = elTemplateHolder.firstElementChild;

            if(welTextarea && htElement.welDropper){
                welTextarea.parentNode.insertBefore(htElement.welDropper, welTextarea);

                htElement._onDragEnterHandler = function(weEvt){ _onDragEnter(sNamespace, weEvt); };
                htElement._onDragLeaveHandler = function(weEvt){ _onDragLeave(sNamespace, weEvt); };
                welTextarea.addEventListener("dragover", htElement._onDragOverHandler);
                welTextarea.addEventListener("dragenter", htElement._onDragEnterHandler);
                welTextarea.addEventListener("dragleave", htElement._onDragLeaveHandler);
                welTextarea.addEventListener("drop", htElement._onDropFileHandler);
            }
        }

        // Upload by paste
        if(htVar.bPastable && welTextarea){
            htElement._onPasteFileHandler = function(weEvt){ _onPasteFile(sNamespace, weEvt); };
            welTextarea.addEventListener("paste", htElement._onPasteFileHandler);
        }

        // Mark as already attached
        if(welContainer){
            welContainer._yonaIsUploader = true;
        }
        if(welTextarea){
            welTextarea._yonaIsUploader = true;
        }
    }

    /**
     * Show "Drop files here"
     * @private
     */
    function _showDropper(){
        document.body.classList.add("dragover");
    }

    /**
     * Hide "Drop files here"
     * @private
     */
    function _hideDropper(){
        document.body.classList.remove("dragover");
    }

    /**
     * @param sNamespace
     * @param weEvt
     * @returns {boolean}
     * @private
     */
    function _onDragOver(sNamespace, weEvt){
        _showDropper();

        weEvt.stopPropagation();
        weEvt.preventDefault();
        return false;
    }

    /**
     * @param sNamespace
     * @param weEvt
     * @private
     */
    function _onDragEnter(sNamespace, weEvt){
        _showDropper();

        weEvt.dataTransfer.dropEffect = _getDropEffect(weEvt);
        weEvt.stopPropagation();
        weEvt.preventDefault();
    }

    /**
     * @param weEvt
     * @returns {string}
     * @private
     */
    function _getDropEffect(weEvt){
        var oData = weEvt.dataTransfer;

        if(!oData.types){
            return "none";
        }

        if(oData.types.indexOf("text/uri-list") > -1){
            return "link";
        } else if((oData.types.indexOf("Files") > -1) ||
                  (oData.types.indexOf("text/plain") > -1)){
            return "copy";
        }

        return "none";
    }

    /**
     * @param sNamespace
     * @param weEvt
     * @private
     */
    function _onDragLeave(sNamespace, weEvt){
        _hideDropper();

        weEvt.dataTransfer.dropEffect = "none";
        weEvt.stopPropagation();
        weEvt.preventDefault();
    }

    /**
     * @param {String} sNamespace
     */
    function _detachEvent(sNamespace){
        var htElement = htElements[sNamespace];
        var welContainer = htElement.welContainer;
        var welTextarea  = htElement.welTextarea;

        if(htElement.welInputFile && htElement._onChangeFileHandler){
            if(htElement.welInputFile.jquery){
                htElement.welInputFile.off();
            } else {
                htElement.welInputFile.removeEventListener("change", htElement._onChangeFileHandler);
            }
        }

        if(welContainer){
            if(htElement._onDragOverHandler){
                welContainer.removeEventListener("dragover", htElement._onDragOverHandler);
            }
            if(htElement._onDropFileHandler){
                welContainer.removeEventListener("drop", htElement._onDropFileHandler);
            }
        }

        if(welTextarea){
            if(htElement._onDragOverHandler){
                welTextarea.removeEventListener("dragover", htElement._onDragOverHandler);
            }
            if(htElement._onDragEnterHandler){
                welTextarea.removeEventListener("dragenter", htElement._onDragEnterHandler);
            }
            if(htElement._onDragLeaveHandler){
                welTextarea.removeEventListener("dragleave", htElement._onDragLeaveHandler);
            }
            if(htElement._onDropFileHandler){
                welTextarea.removeEventListener("drop", htElement._onDropFileHandler);
            }
            if(htElement._onPasteFileHandler){
                welTextarea.removeEventListener("paste", htElement._onPasteFileHandler);
            }
        }

        if(welContainer){
            welContainer._yonaIsUploader = false;
        }
        if(welTextarea){
            welTextarea._yonaIsUploader = false;
        }
    }

    /**
     * change event handler on input[type="file"]
     *
     * @param {String} sNamespace
     */
    function _onChangeFile(sNamespace){
        var htElement = htElements[sNamespace];
        var welInputFile = htElement.welInputFile;
        var sRawValue = welInputFile.jquery ? welInputFile.val() : welInputFile.value;
        var sFileName = _getBasename(sRawValue);
        if(!sFileName || sFileName === ""){
            return;
        }

        var elInputFile = welInputFile.jquery ? welInputFile[0] : welInputFile;
        _uploadFile(elInputFile.files || elInputFile, sNamespace);
    }

    /**
     * @param {String} sNamespace
     * @param {Event} weEvt
     */
    function _onDropFile(sNamespace, weEvt){
        _hideDropper();

        var oFiles = weEvt.dataTransfer.files;
        if(!oFiles || oFiles.length === 0){
            return;
        }

        _uploadFile(oFiles, sNamespace);

        _fireEvent("dropFile", {
            "weEvt" : weEvt,
            "oFiles": oFiles
        }, sNamespace);

        weEvt.stopPropagation();
        weEvt.preventDefault();
        return false;
    }

    /**
     * @param {String} sNamespace
     * @param {Event} weEvt
     */
    function _onPasteFile(sNamespace, weEvt){
        var oClipboardData = weEvt.clipboardData;

        if(!oClipboardData || !oClipboardData.items){
            return;
        }

        var oItem, nSubmitId, oFile;


        if( hasBothTextAndImage(oClipboardData.items)){
            _fireEvent("pasteMarkdownTable", {
                "nSubmitId": _getSubmitId(),
                "markdownTableText": getPlainText(oClipboardData.items)
            }, sNamespace);
            return weEvt.preventDefault();
        }

        for(var i = 0, nLength = oClipboardData.items.length; i < nLength; i++){
            oItem = oClipboardData.items[i];
            oFile = oItem.getAsFile();

            if(oFile && oFile.type.indexOf("image/") === 0){
                nSubmitId = _getSubmitId();
                oFile.name = nSubmitId + ".png";

                _uploadSingleFile(oFile, nSubmitId, sNamespace);

                _fireEvent("pasteFile", {
                    "nSubmitId": nSubmitId,
                    "oFile"    : oFile
                }, sNamespace);

                weEvt.preventDefault();
            }
        }

        function hasBothTextAndImage(data) {
            var hasStringData = false;
            var hasImageData = false;

            if (data && data.length > 1) {
                for (var i = 0, nLength = data.length; i < nLength; i++) {
                    if ((data[i].kind === 'string') &&
                        (data[i].type.match('^text/plain'))) {
                        hasStringData = true;
                    } else if ((data[i].kind === 'file') &&
                        (data[i].type.match('^image/'))) {
                        hasImageData = true;
                    }
                }
            }
            return hasStringData && hasImageData;
        }

        function getPlainText(data) {
            var text;

            if (data && data.length > 1) {
                for (var i = 0, nLength = data.length; i < nLength; i++) {
                    if ((data[i].kind === 'string') &&
                        (data[i].type.match('^text/plain'))) {

                        // The rest of this block codes are derived from
                        // https://github.com/jonmagic/copy-excel-paste-markdown/blob/master/script.js
                        var clipboard = event.clipboardData
                        var data = clipboard.getData('text/plain').trim()

                        if(looksLikeTable(data)) {
                            event.preventDefault()
                        }else{
                            return
                        }

                        var rows = data.split((/[\u0085\u2028\u2029]|\r\n?/g)).map(function(row) {
                            row = row.replace('\n', ' ')
                            return row.split("\t")
                        })

                        var colAlignments = []

                        var columnWidths = rows[0].map(function(column, columnIndex) {
                            var alignment = "l"
                            var re = /^(\^[lcr])/i
                            var m = column.match(re)
                            if (m) {
                                var align = m[1][1].toLowerCase()
                                if (align === "c") {
                                    alignment = "c"
                                } else if (align === "r") {
                                    alignment = "r"
                                }
                            }
                            colAlignments.push(alignment)
                            column = column.replace(re, "")
                            rows[0][columnIndex] = column
                            return columnWidth(rows, columnIndex)
                        })
                        var markdownRows = rows.map(function(row, rowIndex) {
                            // | Name         | Title | Email Address  |
                            // |--------------|-------|----------------|
                            // | Jane Atler   | CEO   | jane@acme.com  |
                            // | John Doherty | CTO   | john@acme.com  |
                            // | Sally Smith  | CFO   | sally@acme.com |
                            return "| " + row.map(function(column, index) {
                                return column + Array(columnWidths[index] - column.length + 1).join(" ")
                            }).join(" | ") + " |"
                            row.map

                        })
                        markdownRows.splice(1, 0, "|" + columnWidths.map(function(width, index) {
                            var prefix = ""
                            var postfix = ""
                            var adjust = 0
                            var alignment = colAlignments[index]
                            if (alignment === "r") {
                                postfix = ":"
                                adjust = 1
                            } else if (alignment == "c") {
                                prefix = ":"
                                postfix = ":"
                                adjust = 2
                            }
                            return prefix + Array(columnWidths[index] + 3 - adjust).join("-") + postfix
                        }).join("|") + "|")

                        text = markdownRows.join("\n");
                    }
                }
            }
            return text;
        }

        function columnWidth(rows, columnIndex) {
            return Math.max.apply(null, rows.map(function(row) {
                return ('' + row[columnIndex]).length
            }))
        }

        function looksLikeTable(data) {
            return true
        }
    }

    /**
     * Get submitId for each upload
     *
     * @return {Number}
     */
    function _getSubmitId(){
      var now = new Date();
      return  now.getSeconds() + "" + now.getMilliseconds() + '-' + now.getFullYear() + '-' + (now.getMonth() + 1)
          + '-' + now.getDate() + '-' + now.getHours() + '-' + now.getMinutes();
    }

    /**
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
     * yona.Files.attach("eventName", function(){}, "namespace");
     * // or
     * yona.Files.attach({
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

        var aHandlers = htHandlers[sNamespace + sEventName];
        var nIndex = aHandlers ? aHandlers.indexOf(fHandler) : -1;

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
        "getUploader": _getUploader,
        "destroyUploader": _destroyUploader,
        "attach"     : _attachCustomEvent,
        "detach"     : _detachCustomEvent,
        "getList"    : _getFileList,
        "uploadFile" : _uploadFile,
        "deleteFile" : _deleteFile
    };
})();
