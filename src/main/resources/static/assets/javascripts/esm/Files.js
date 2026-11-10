/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

import { register } from "./namespace.js";

const Files = (function(){
    const htVar = {};
    const htElements = {};
    const htHandlers = {};

    /**
     * initialize fileUploader
     *
     * @param {Hash Table} htOptions
     * @param {String} htOptions.sListURL
     * @param {String} htOptions.sUploadURL
     * @param {Number} htOptions.maxFileSize 올릴 수 있는 파일 크기의 최대(바이트)
     */
    function _init(htOptions){
        htOptions = htOptions || {};

        htVar.sListURL     = htOptions.sListURL;
        htVar.sUploadURL   = htOptions.sUploadURL;

        // 끌어놓기는 HTML5 File API가 필요하다
        htVar.bDroppable = (typeof window.File != "undefined");

        // 붙여넣기는 문서의 paste 이벤트가 필요하다. v1.6은 "FireFox"(철자가 틀려 항상 참)를 제외하려 했으나 실제로는 제외되지 않았고 지금도 같다(R-140)
        htVar.bPastable = (typeof document.onpaste != "undefined");

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
     * @param {Variant} oFiles FileList or File Object
     * @param {String} sNamespace (Optional)
     */
    function _uploadFile(oFiles, sNamespace){
        if(oFiles && oFiles.length){
            for(let i = 0; i < oFiles.length; i++){
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
        const bEventResult = _fireEvent("beforeUpload", {
            "oFile": oFile,
            "nSubmitId": oFile ? oFile.nSubmitId : nSubmitId
        }, sNamespace);
        if(bEventResult === false){ // upload cancel by event handler
            return false;
        }

        return _uploadFileXHR(nSubmitId, oFile, sNamespace);
    }

    /**
     * 파일 하나를 올린다. 업로드 진행률(`upload`의 `progress`)이 필요하므로 `fetch`가 아니라 `XMLHttpRequest`를 쓴다.
     *
     * 토큰(CSRF)은 `yona.Csrf.js`가 `XMLHttpRequest`를 전역으로 가로채 싣는다.
     * 서버가 아약스 요청을 알아보는 `X-Requested-With`는 직접 싣는다(`$.ajax`가 대신해 주던 일).
     * 응답이 JSON이면 객체로 해석하고, 깨졌으면 `parsererror`로 오류 처리기에 알린다.
     * 오류 처리기는 `status`와 `statusText`를 읽는다.
     *
     * @param {Number} nSubmitId
     * @param {File} oFile
     * @param {String} sNamespace
     */
    function _uploadFileXHR(nSubmitId, oFile, sNamespace){
        // check maximum filesize (<= 2,147,483,454 bytes) if available
        if(oFile.size && oFile.size > htVar.nMaxFileSize){
            return _onErrorSubmit(nSubmitId, {
                "status"    : $yona.formatFileSize(oFile.size),
                "statusText": Messages("error.toolargefile", $yona.formatFileSize(htVar.nMaxFileSize))
            }, sNamespace);
        }

        const oData = new FormData();
        const filename = oFile.name === 'image.png' ? nSubmitId + ".png" : oFile.name;
        oData.append("filePath", oFile, filename);

        const oXHR = new XMLHttpRequest();
        oXHR.open("POST", htVar.sUploadURL);
        oXHR.setRequestHeader("X-Requested-With", "XMLHttpRequest");

        oXHR.upload.addEventListener("progress", (weEvt) =>{
            if(weEvt.lengthComputable){
                _onUploadProgress(nSubmitId, Math.ceil((weEvt.loaded / weEvt.total) * 100), sNamespace);
            }
        }, false);

        oXHR.addEventListener("load", () =>{
            const oResult = _readResponse(oXHR);
            if(oResult.error){
                _onErrorSubmit(nSubmitId, oResult.error, sNamespace);
            } else {
                _onSuccessSubmit(nSubmitId, oResult.data, sNamespace);
            }
        });
        const fOnFail = function(){
            _onErrorSubmit(nSubmitId, {"status": 0, "statusText": "error", "responseText": ""}, sNamespace);
        };
        oXHR.addEventListener("error", fOnFail);
        oXHR.addEventListener("abort", fOnFail);

        oXHR.send(oData);
    }

    /**
     * 끝난 요청의 응답을 해석한다.
     *
     * @param {XMLHttpRequest} oXHR
     * @return {{data: *, error: (Object|undefined)}} 오류면 `error`에 `{status, statusText, responseText}`
     * @private
     */
    function _readResponse(oXHR){
        const bSuccess = (oXHR.status >= 200 && oXHR.status < 300) || oXHR.status === 304;
        const sText = oXHR.responseText;

        if(!bSuccess){
            return {"error": {"status": oXHR.status, "statusText": oXHR.statusText || "error", "responseText": sText}};
        }
        if(/json/i.test(oXHR.getResponseHeader("Content-Type") || "")){
            try {
                return {"data": JSON.parse(sText)};
            } catch(e) {
                return {"error": {"status": oXHR.status, "statusText": "parsererror", "responseText": sText}};
            }
        }
        return {"data": sText};
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
            // v1.6은 이름 공간을 빠뜨려 이름 공간에 붙인 오류 처리기가 불리지 않았다(C004)
            return _onErrorSubmit(nSubmitId, oRes, sNamespace);
        }

        // clear inputFile
        if(sNamespace && htElements[sNamespace] && htElements[sNamespace].welInputFile){
            htElements[sNamespace].welInputFile.val("");
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
        yona.http.ajax({
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
     * @param {HTMLElement} elContainer
     * @param {HTMLTextareaElement} elTextarea (Optional)
     * @param {String} sNamespace
     * @return {Wrapped Element}
     */
    function _getUploader(elContainer, elTextarea, sNamespace){
        sNamespace = sNamespace || _getSubmitId();

        // only single uploader can be attached on single Container/Textarea
        if($(elContainer).data("isYobiUploader") || $(elTextarea).data("isYobiUploader")){
            return false;
        }

        _initElement({
            "elContainer": elContainer,
            "elTextarea" : elTextarea,
            "sNamespace" : sNamespace
        });
        _attachEvent(sNamespace);

        return htElements[sNamespace].welContainer;
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
        const sNamespace = htOptions.sNamespace;

        htElements[sNamespace] = {};
        htElements[sNamespace].welContainer = $(htOptions.elContainer);
        htElements[sNamespace].welTextarea  = $(htOptions.elTextarea);
        htElements[sNamespace].welInputFile = htElements[sNamespace].welContainer.find("input[type=file]");
        htElements[sNamespace].welContainer.attr("data-namespace", sNamespace);
    }

    /**
     * @param {String} sNamespace
     */
    function _attachEvent(sNamespace){
        const htElement = htElements[sNamespace];
        htElement.welInputFile.on("change", _onChangeFile.bind(this, sNamespace));

        // Upload by Drag & Drop
        if(htVar.bDroppable){
            htElement.welContainer.on({
                "dragover" : _onDragOver.bind(this, sNamespace),
                "drop"     : _onDropFile.bind(this, sNamespace)
            });

            const sTplDropper = $("#tplDropFilesHere").text().trim() ||
                '<div class="upload-drop-here"><div class="msg-wrap"><div class="msg">' +
                Messages("common.attach.dropFilesHere") +
                '</div></div></div>';
            htElement.welDropper = $(sTplDropper);
            htElement.welTextarea.before(htElement.welDropper);
            htElement.welTextarea.on({
                "dragover" : _onDragOver.bind(this, sNamespace),
                "dragenter": _onDragEnter.bind(this, sNamespace),
                "dragleave": _onDragLeave.bind(this, sNamespace),
                "drop"     : _onDropFile.bind(this, sNamespace)
            });
        }

        // Upload by paste
        if(htVar.bPastable && htElement.welTextarea){
            htElement.welTextarea.on("paste", _onPasteFile.bind(this, sNamespace));
        }

        // Mark as already attached
        htElement.welContainer.data("isYobiUploader", true);
        htElement.welTextarea.data("isYobiUploader", true);
    }

    /**
     * Show "Drop files here"
     * @private
     */
    function _showDropper(){
        $(document.body).addClass("dragover");
    }

    /**
     * Hide "Drop files here"
     * @private
     */
    function _hideDropper(){
        $(document.body).removeClass("dragover");
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

        weEvt.originalEvent.dataTransfer.dropEffect = _getDropEffect(weEvt);
        weEvt.stopPropagation();
        weEvt.preventDefault();
    }

    /**
     * @param weEvt
     * @returns {string}
     * @private
     */
    function _getDropEffect(weEvt){
        const oData = weEvt.originalEvent.dataTransfer;

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

        weEvt.originalEvent.dataTransfer.dropEffect = "none";
        weEvt.stopPropagation();
        weEvt.preventDefault();
    }

    /**
     * @param {String} sNamespace
     */
    function _detachEvent(sNamespace){
        const htElement = htElements[sNamespace];
        htElement.welInputFile.off();
        htElement.welContainer.off();
        htElement.welTextarea.off();
        htElement.welContainer.data("isYobiUploader", false);
        htElement.welTextarea.data("isYobiUploader", false);
    }

    /**
     * change event handler on input[type="file"]
     *
     * @param {String} sNamespace
     */
    function _onChangeFile(sNamespace){
        const oFiles = htElements[sNamespace].welInputFile[0].files;
        if(!oFiles || oFiles.length === 0){
            return;
        }

        _uploadFile(oFiles, sNamespace);
    }

    /**
     * @param {String} sNamespace
     * @param {Wrapped Event} weEvt
     */
    function _onDropFile(sNamespace, weEvt){
        _hideDropper();

        const oFiles = weEvt.originalEvent.dataTransfer.files;
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
     * @param {Wrapped Event} weEvt
     */
    function _onPasteFile(sNamespace, weEvt){
        const oClipboardData = weEvt.originalEvent.clipboardData;

        if(!oClipboardData || !oClipboardData.items){
            return;
        }

        let oItem, nSubmitId, oFile;


        if( hasBothTextAndImage(oClipboardData.items)){
            _fireEvent("pasteMarkdownTable", {
                "nSubmitId": _getSubmitId(),
                "markdownTableText": getPlainText(oClipboardData.items, weEvt)
            }, sNamespace);
            return weEvt.preventDefault();
        }

        for(let i = 0, nLength = oClipboardData.items.length; i < nLength; i++){
            oItem = oClipboardData.items[i];
            oFile = oItem.getAsFile();

            if(oFile && oFile.type.indexOf("image/") === 0){
                nSubmitId = _getSubmitId();
                // v1.6은 여기서 `oFile.name = nSubmitId + ".png"`로 이름을 바꾸려 했으나 File.name은 읽기 전용이라 대입이 무시되었다(엄격 모드인 ES 모듈에서는 오류가 된다).
                // 동작을 그대로 두려고 대입을 지웠다. 이름을 실제로 바꾸는 것은 동작이 달라지므로 refactor-deviations.md에 적어 두었다.

                _uploadSingleFile(oFile, nSubmitId, sNamespace);

                _fireEvent("pasteFile", {
                    "nSubmitId": nSubmitId,
                    "oFile"    : oFile
                }, sNamespace);

                weEvt.preventDefault();
            }
        }

        function hasBothTextAndImage(data) {
            let hasStringData = false;
            let hasImageData = false;

            if (data && data.length > 1) {
                for (let i = 0, nLength = data.length; i < nLength; i++) {
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

        // v1.6은 인자 대신 전역 `window.event`를 읽었다(C010). 받은 이벤트를 쓴다
        function getPlainText(data, weEvt) {
            let text;

            if (data && data.length > 1) {
                for (let i = 0, nLength = data.length; i < nLength; i++) {
                    if ((data[i].kind === 'string') &&
                        (data[i].type.match('^text/plain'))) {

                        // The rest of this block codes are derived from
                        // https://github.com/jonmagic/copy-excel-paste-markdown/blob/master/script.js
                        const clipboard = weEvt.originalEvent.clipboardData
                        var data = clipboard.getData('text/plain').trim()

                        if(looksLikeTable(data)) {
                            weEvt.preventDefault()
                        }else{
                            return
                        }

                        var rows = data.split((/[\u0085\u2028\u2029]|\r\n?/g)).map((row) => {
                            row = row.replace('\n', ' ')
                            return row.split("\t")
                        })

                        var colAlignments = []

                        var columnWidths = rows[0].map((column, columnIndex) => {
                            let alignment = "l"
                            const re = /^(\^[lcr])/i
                            const m = column.match(re)
                            if (m) {
                                const align = m[1][1].toLowerCase()
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
                        const markdownRows = rows.map((row, rowIndex) => {
                            // | Name         | Title | Email Address  |
                            // |--------------|-------|----------------|
                            // | Jane Atler   | CEO   | jane@acme.com  |
                            // | John Doherty | CTO   | john@acme.com  |
                            // | Sally Smith  | CFO   | sally@acme.com |
                            return "| " + row.map((column, index) => {
                                return column + Array(columnWidths[index] - column.length + 1).join(" ")
                            }).join(" | ") + " |"
                            row.map

                        })
                        markdownRows.splice(1, 0, "|" + columnWidths.map((width, index) => {
                            let prefix = ""
                            let postfix = ""
                            let adjust = 0
                            const alignment = colAlignments[index]
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
            return Math.max.apply(null, rows.map((row) => {
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
      const now = new Date();
      return  now.getSeconds() + "" + now.getMilliseconds() + '-' + now.getFullYear() + '-' + (now.getMonth() + 1)
          + '-' + now.getDate() + '-' + now.getHours() + '-' + now.getMinutes();
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
            for(const sKey in sEventName){
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

        const aHandlers = htHandlers[sNamespace + sEventName];
        const nIndex = aHandlers ? aHandlers.indexOf(fHandler) : -1;

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

        const aGlobalHandlers = htHandlers[sEventName] || [];
        const aLocalHandlers = htHandlers[sNamespace + sEventName] || [];
        const aHandlers = aGlobalHandlers.concat(aLocalHandlers);

        if((aHandlers instanceof Array) === false){
            return;
        }

        let bResult;
        aHandlers.forEach((fHandler) =>{
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

// 옛 전역 이름으로도 부를 수 있게 등록한다
register("yona.Files", Files);
