/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author JiHan Kim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
yobi.Files = (function(){
    var htVar = {};
    var htElements = {};
    var htHandlers = {};

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
        htVar.nMaxFileSize = 2147483454;
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
                "status"    : 0,
                "statusText": Messages("error.toolargefile", humanize.filesize(htVar.nMaxFileSize))
            }, sNamespace);
        }

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
            "xhr": function(){
                var oXHR = $.ajaxSettings.xhr();

                if(oXHR.upload){
                    oXHR.upload.addEventListener("progress", function(weEvt){
                        if(weEvt.lengthComputable){
                            _onUploadProgress(nSubmitId, Math.ceil((weEvt.loaded / weEvt.total) * 100), sNamespace);
                        }
                    }, false);
                }

                return oXHR;
            }
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

        if(!htElement.welInputFile && !elFile){
            return false;
        }

        var welInputFile = htElement.welInputFile || $(elFile);
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
     * request attached file list
     *
     * @param {Hash Table} htOptions
     * @param {String} htOptions.sResourceType
     * @param {String} htOptions.sResourceId
     * @param {Function} htOptions.fOnLoad
     * @param {Function} htOptions.fOnError
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
        var sNamespace = htOptions.sNamespace;

        htElements[sNamespace] = {};
        htElements[sNamespace].welContainer = $(htOptions.elContainer);
        htElements[sNamespace].welTextarea  = $(htOptions.elTextarea);
        htElements[sNamespace].welInputFile = htElements[sNamespace].welContainer.find("input[type=file]");
        htElements[sNamespace].welContainer.attr("data-namespace", sNamespace);

        if(!htVar.bXHR2){
            htElements[sNamespace].welInputFile.attr("multiple", null);
        }
    }

    /**
     * @param {String} sNamespace
     */
    function _attachEvent(sNamespace){
        var htElement = htElements[sNamespace];
        htElement.welInputFile.on("change", $.proxy(_onChangeFile, this, sNamespace));

        // Upload by Drag & Drop
        if(htVar.bDroppable){
            htElement.welContainer.on({
                "dragover" : $.proxy(_onDragOver, this, sNamespace),
                "drop"     : $.proxy(_onDropFile, this, sNamespace)
            });

            var sTplDropper = $("#tplDropFilesHere").text().trim() ||
                '<div class="upload-drop-here"><div class="msg-wrap"><div class="msg">' +
                Messages("common.attach.dropFilesHere") +
                '</div></div></div>';
            htElement.welDropper = $(sTplDropper);
            htElement.welTextarea.before(htElement.welDropper);
            htElement.welTextarea.on({
                "dragover" : $.proxy(_onDragOver,  this, sNamespace),
                "dragenter": $.proxy(_onDragEnter, this, sNamespace),
                "dragleave": $.proxy(_onDragLeave, this, sNamespace),
                "drop"     : $.proxy(_onDropFile,  this, sNamespace)
            });
        }

        // Upload by paste
        if(htVar.bPastable && htElement.welTextarea){
            htElement.welTextarea.on("paste", $.proxy(_onPasteFile, this, sNamespace));
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
        var oData = weEvt.originalEvent.dataTransfer;

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
        var htElement = htElements[sNamespace];
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
        var htElement = htElements[sNamespace];
        var sFileName = _getBasename(htElement.welInputFile.val());
        if(!sFileName || sFileName === ""){
            return;
        }

        _uploadFile(htElement.welInputFile[0].files || htElement.welInputFile[0], sNamespace);
    }

    /**
     * @param {String} sNamespace
     * @param {Wrapped Event} weEvt
     */
    function _onDropFile(sNamespace, weEvt){
        _hideDropper();

        var oFiles = weEvt.originalEvent.dataTransfer.files;
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
        var oClipboardData = weEvt.originalEvent.clipboardData;

        if(!oClipboardData || !oClipboardData.items){
            return;
        }

        var oItem, nSubmitId, oFile;

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
