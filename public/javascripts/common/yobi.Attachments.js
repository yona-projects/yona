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
yobi.Attachments = function(htOptions) {
    var htVar = {};
    var htElements = {};

    /**
     * initialize fileUploader
     *
     * @param {Hash Table} htOptions
     * @param {Variant} htOptions.elContainer
     * @param {Variant} htOptions.elTextarea
     * @param {String}  htOptions.sTplFileList
     * @param {String}  htOptions.sTplFileItem
     * @param {String}  htOptions.sResourceId
     * @param {String}  htOptions.sResourceType
     * @param {String}  htOptions.sUploaderID
     */
    function _init(htOptions){
        htOptions = htOptions || {};

        _initVar(htOptions);
        _initElement(htOptions);

        // Request attachments only if the container is specified.
        if (htVar.attachments) {
            _updateAttachments(htVar.attachments);
        } else if ((htVar.sResourceType && htVar.sResourceId) || htVar.attachments) {
            _requestList();
        }


        if(htOptions.sUploaderId){
            _attachUploaderEvent(htOptions.sUploaderId);
        }
    }

    /**
     * @param {Hash Table} htOptions
     */
    function _initVar(htOptions){
        var sFileLink = '<a href="${fileHref}" class="vmiddle" target="_blank"><i class="yobicon-paperclip"></i><span class="filename">${fileName}</span><span class="filesize">(${fileSizeReadable})</span></a>';
        var sFileDownloadLink = '<a href="${fileHref}?action=download" class="download ybtn ybtn-mini" title="' + Messages("button.download") + ' ${fileName}"><i class="yobicon-download"></i></a>';
        htVar.sTplFileList = htOptions.sTplFileList || '<ul class="attaches wm">';
        htVar.sTplFileItem = htOptions.sTplFileItem || '<li class="attach">'+ sFileLink + sFileDownloadLink + '</li>';
        htVar.sResourceId = htOptions.sResourceId; // ResId: Optional
        htVar.sResourceType = htOptions.sResourceType; // ResType: Required
    }

    /**
     * @param {Hash Table} htOptions
     */
    function _initElement(htOptions){

        // parentForm
        htElements.welToAttach = htOptions.targetFormId || $(htOptions.elContainer);
        var sTagName = htOptions.sTagNameForTemporaryUploadFiles || "temporaryUploadFiles";
        htElements.welTemporaryUploadFileList = $('<input type="hidden" name="'+sTagName+'">');
        htElements.welToAttach.prepend(htElements.welTemporaryUploadFileList);
        htVar.aTemporaryFileIds = [];

        // welContainer
        htElements.welContainer = $(htOptions.elContainer);
        htElements.welContainer.data("isYobiAttachment", true);
        htVar.sResourceId = htVar.sResourceId || htElements.welContainer.data('resourceId');
        htVar.sResourceType = htVar.sResourceType || htElements.welContainer.data('resourceType');

        if (!htVar.attachments) {
            htVar.attachments = htElements.welContainer.data('attachments');
        }

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
            "errorUpload"   : _onErrorUpload,
            "pasteFile"     : _onPasteFile,
            "dropFile"      : _onDropFile
        }, sUploaderId);
    }

    /**
     * attach Uploader custom event handlers
     *
     * @param {String} sUploaderId
     */
    function _detachUploaderEvent(sUploaderId){
        yobi.Files.detach({
            "beforeUpload"  : _onBeforeUpload,
            "uploadProgress": _onUploadProgress,
            "successUpload" : _onSuccessUpload,
            "errorUpload"   : _onErrorUpload,
            "pasteFile"     : _onPasteFile,
            "dropFile"      : _onDropFile
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
     * @param {Hash Table} htData
     * @param {Variant} htData.vFile
     * @param {Boolean} htData.bTemporary
     *
     * @return {Number}
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
                welItem.addClass("complete");

                if(htElements.welTextarea.length > 0){
                    welItem.click(_onClickListItem);
                }
            } else {
                welItem.attr("id", oFile.nSubmitId);
                welItem.css("opacity", "0.2");
                welItem.data("progressBar", welItem.find(".progress > .bar"));
            }

            aWelItems.push(welItem);
            nFileSize += parseInt(oFile.size, 10);
        });

        if(aWelItems.length > 0){
            if(htElements.welFileList.length === 0){
                htElements.welFileList = $(htVar.sTplFileList);
                htElements.welContainer.append(htElements.welFileList);
            }
            htElements.welFileList.show();
            htElements.welFileListHelp.show();

            htElements.welFileList.append(aWelItems);
        }

        return nFileSize;
    }

    /**
     * Create uploaded file item HTML element using template string
     *
     * @param {Hash Table} htFile
     * @param {Boolean} bTemp
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

        _showMimetypeIcon(welItem, htFile.mimeType);

        if(bTemp){
            welItem.addClass("temporary");
        }
        return welItem;
    }

    /**
     * @param {Number} nSubmitId
     * @param {Object} oRes
     */
    function _updateFileItem(nSubmitId, oRes){
        var welItem = $("#" + nSubmitId);
        var welItemExists = htElements.welFileList.find('[data-id="' + oRes.id + '"]');

        if(welItemExists.length > 0){
            welItem.remove();
            _blinkFileItem(welItemExists);
            return false;
        }

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

    function _blinkFileItem(welItem, sBlinkColor){
        var sBgColor;

        sBlinkColor = sBlinkColor || "#f36c22";
        sBgColor = welItem.css("background");
        welItem.css("background", sBlinkColor);

        setTimeout(function(){
            welItem.css("background", sBgColor);
        }, 500);
    }

    function _addUploadFileIdToListAndForm(sFileId) {
        if(htVar.aTemporaryFileIds.indexOf(sFileId) === -1) {
            htVar.aTemporaryFileIds.push(sFileId);
            htElements.welTemporaryUploadFileList.val(htVar.aTemporaryFileIds.join(","));
        }
    }

    function _removeDeletedFileIdFromListAndForm(sFileId) {
        var nIndex = htVar.aTemporaryFileIds.indexOf(sFileId.toString());
        if( nIndex !== -1){
            htVar.aTemporaryFileIds.splice(nIndex, 1);
            htElements.welTemporaryUploadFileList.val(htVar.aTemporaryFileIds.join(","));
        }
    }

    /**
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

        _addUploadFileIdToListAndForm(htData.oRes.id);
        // Validate server response
        if(!(oRes instanceof Object) || !oRes.name || !oRes.url){
            return _onErrorUpload(nSubmitId, oRes);
        }

        if(_updateFileItem(nSubmitId, oRes) !== false){
            _setProgressBar(nSubmitId, 100);
        }

        var aFileItemQuery = [
            "#" + htData.nSubmitId,
            '.attached-file[data-id="' + htData.oRes.id + '"]'
        ];

        var welFileItem = $(aFileItemQuery.join(", "));
        var sTempLink = _getTempLinkText(htData.nSubmitId);
        var sRealLink = _getLinkText(welFileItem);
        _replaceLinkInTextarea(sTempLink, sRealLink);

        _showMimetypeIcon(welFileItem, htData.oRes.mimeType);
    }

    /**
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
     * Set Progress Bar status
     *
     * @param {Number} nSubmitId
     * @param {Number} nProgress
     */
    function _setProgressBar(nSubmitId, nProgress) {
        var welItem = $("#" + nSubmitId);
        welItem.data("progressBar").css("width", nProgress + "%");

        if(nProgress*1 === 100){
            welItem.css("opacity", "1");
            setTimeout(function(){
                welItem.addClass("complete");
            }, 1000);
        }
    }

    /**
     * On error to submit temporary form created in onChangeFile().
     *
     * @param {Hash Table} htData
     * @param {Number} htData.nSubmitId
     * @param {Object} htData.oRes
     */
    function _onErrorUpload(htData){
        $("#" + htData.nSubmitId).remove();

        if(htElements.welFileList.children().length === 0){
            htElements.welFileList.hide();
            htElements.welFileListHelp.hide();
        }

        $yobi.notify(Messages("common.attach.error.upload", htData.oRes.status, htData.oRes.statusText));
        _clearLinkInTextarea(_getTempLinkText(htData.nSubmitId + ".png"));
    }

    /**
     * On Click attached files list
     *
     * @param {Wrapped Event} weEvt
     */
    function _onClickListItem(weEvt){
        var welTarget = $(weEvt.target);
        var welItem = $(weEvt.currentTarget);

        if(welTarget.hasClass("btn-delete")){
            _deleteAttachedFile(welItem);
        } else {
            _insertLinkToTextarea(welItem);
        }
    }

    /**
     * @param {Wrapped Element} welItem
     */
    function _deleteAttachedFile(welItem){
       var sURL = welItem.attr("data-href");

        yobi.Files.deleteFile({
           "sURL"   : sURL,
           "fOnLoad": function(){
                _removeDeletedFileIdFromListAndForm(welItem.data("id"))
                _clearLinkInTextarea(welItem);
                welItem.remove();

                if(htElements.welFileList.children().length === 0){
                    htElements.welFileList.hide();
                    htElements.welFileListHelp.hide();
                }
            },
            "fOnError": function(oRes){
                $yobi.notify(Messages("common.attach.error.delete", oRes.status, oRes.statusText));
            }
       });
    }

    /**
     * @param {Variant} vLink
     */
    function _insertLinkToTextarea(vLink){
        var welTextarea = htElements.welTextarea;

        if(welTextarea.length === 0){
            return false;
        }

        var nPos = welTextarea.prop("selectionStart");
        var sText = welTextarea.val();
        var sLink = (typeof vLink === "string") ? vLink : _getLinkText(vLink);

        welTextarea.val(sText.substring(0, nPos) + sLink + sText.substring(nPos));
        _setCursorPosition(welTextarea, nPos + sLink.length);
    }

    /**
     * @return {Boolean} true if sMimeType is supported by HTML5 video element
     */
    function isHtml5Video(sMimeType) {
        return ["video/mp4", "video/ogg", "video/webm"]
            .indexOf($.trim(sMimeType).toLowerCase()) >= 0;
    }

    /**
     * Show a icon matches sMimeType on welFileItem
     */
    function _showMimetypeIcon(welFileItem, sMimeType) {
        if (isHtml5Video(sMimeType)) {
            welFileItem.children('i.mimetype').addClass('yobicon-video2').show();
        }
    }

    /**
     * @param {Wrapped Element} welItem
     * @return {String}
     */
    function _getLinkText(welItem){
        var sMimeType = welItem.attr("data-mime");
        var sFileName = welItem.attr("data-name");
        var sFilePath = welItem.attr("data-href");

        var sLinkText = '[' + sFileName + '](' + sFilePath + ')\n';

        if (sMimeType.substr(0,5) === "image") {
            return '!' + sLinkText;
        } else if (isHtml5Video(sMimeType)) {
            return $('<div>').append(
                    $('<video>').attr('controls', true)
                    .append($('<source>').attr('src', sFilePath))
                    .append(sLinkText)
                   ).html();

        } else {
            return sLinkText;
        }
    }

    /**
     * @param sFilename
     * @returns {string}
     * @private
     */
    function _getTempLinkText(sFilename){
        return "<!--_" + sFilename + "_-->";
    }

    /**
     * @param {Variant} vLink
     */
    function _clearLinkInTextarea(vLink){
        var welTextarea = htElements.welTextarea;
        if(welTextarea.length === 0){
            return false;
        }

        var sLink = (typeof vLink === "string") ? vLink : _getLinkText(vLink);
        var sData = welTextarea.val().split(sLink).join('');
        sData = sData.split(sLink.trim()).join('');
        welTextarea.val(sData);
    }

    /**
     * @param sLink1
     * @param sLink2
     * @private
     */
    function _replaceLinkInTextarea(sLink1, sLink2){
        var welTextarea = htElements.welTextarea;
        if(welTextarea.length === 0){
            return false;
        }

        var nCurPos = _getCursorPosition(welTextarea);
        var nGap = (sLink2.length - sLink1.length - 1);

        welTextarea.val(welTextarea.val().split(sLink1).join(sLink2));

        if(nGap > 0){
            _setCursorPosition(welTextarea, nCurPos + nGap);
        }
    }

    /**
     * @param welTextarea
     * @param nPos
     * @private
     */
    function _setCursorPosition(welTextarea, nPos){
        var elTextarea = welTextarea.get(0);

        if(elTextarea.setSelectionRange){
            elTextarea.setSelectionRange(nPos, nPos);
        } else if(elTextarea.createTextRange){
            var oRange = elTextarea.createTextRange();
            oRange.collapse(true);
            oRange.moveEnd("character", nPos);
            oRange.moveStart("character", nPos);
            oRange.select();
        }
    }

    /**
     * @param welTextarea
     * @return {Number}
     * @private
     */
    function _getCursorPosition(welTextarea){
        return welTextarea.prop("selectionStart");
    }

    /**
     * @param htData
     * @private
     */
    function _onPasteFile(htData){
        _insertLinkToTextarea(_getTempLinkText(htData.nSubmitId));
    }

    /**
     * @param htData
     * @private
     */
    function _onDropFile(htData){
        var oFiles = htData.oFiles;
        var nLength = oFiles.length;
        var elTarget = htData.weEvt.target;

        if(elTarget.tagName.toLowerCase() === "textarea"){
            for(var i =0; i < nLength; i++){
                _insertLinkToTextarea(_getTempLinkText(oFiles[i].nSubmitId));
            }
        }
    }

    /**
     * request attached file list
     */
    function _requestList(){
        yobi.Files.getList({
            "fOnLoad"      : _updateAttachments,
            "sResourceType": htVar.sResourceType,
            "sResourceId"  : htVar.sResourceId
        });
    }

    /**
     * @param {Object} oRes
     */
    function _updateAttachments(oRes) {
        _appendFileItem({
            "vFile"     : oRes.attachments, // Array
            "bTemporary": false
        });

        if(typeof htVar.sResourceId === "undefined"){
            _appendFileItem({
                "vFile"     : oRes.tempFiles,   // Array
                "bTemporary": true
            });
        }
    }

    function _destroy(){
        if(htOptions.sUploaderId){
            _detachUploaderEvent(htOptions.sUploaderId);
        }

        // truncate HTMlElement references
        for(var sKey in htElements){
            htElements[sKey] = null;
        }
        htElements = null;
    }

    // call initiator
    _init(htOptions || {});

    // return public interface
    return {
        "destroy": _destroy
    };
};
