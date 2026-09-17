/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

yona.Attachments = function(htOptions) {
    var htVar = {};
    var htElements = {};

    /**
     * 호출부가 아직 jQuery 객체를 넘기는 곳과 vanilla 엘리먼트를 넘기는 곳이 섞여 있어
     * raw element/jQuery 객체/셀렉터 문자열을 모두 받아준다(yona.Files.js의 _toElement와 동일).
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
     * @param {HTMLElement} el
     * @param {Boolean} bShow
     * @param {String} sDisplayWhenShown
     */
    function _setDisplay(el, bShow, sDisplayWhenShown){
        if(!el){
            return;
        }
        el.style.display = bShow ? sDisplayWhenShown : "none";
    }

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

        if(htVar.bIsVueAttachments){
            return;
        }

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
        htVar.sTplFileItem = htOptions.sTplFileItem || '<li class="attach">'+ sFileDownloadLink + sFileLink + '</li>';
        htVar.sResourceId = htOptions.sResourceId; // ResId: Optional
        htVar.sResourceType = htOptions.sResourceType; // ResType: Required
    }

    /**
     * @param {Hash Table} htOptions
     */
    function _initElement(htOptions){
        var elContainer = _toElement(htOptions.elContainer);

        // 컨테이너가 <yona-attachments>면 드롭존/업로드/카드 목록을 그 커스텀 엘리먼트가 전부
        // 담당한다 - configure()로 textarea 참조만 넘기고 이 파일의 나머지 초기화는 건너뛴다.
        if(elContainer && elContainer.tagName.toLowerCase() === "yona-attachments"){
            htVar.bIsVueAttachments = true;
            elContainer._isYonaAttachment = true;
            elContainer.configure({"textarea": _toElement(htOptions.elTextarea)});
            return;
        }

        // parentForm
        htElements.welToAttach = _toElement(htOptions.targetFormId) || elContainer;
        var sTagName = htOptions.sTagNameForTemporaryUploadFiles || "temporaryUploadFiles";
        htElements.welTemporaryUploadFileList = document.createElement("input");
        htElements.welTemporaryUploadFileList.setAttribute("type", "hidden");
        htElements.welTemporaryUploadFileList.setAttribute("name", sTagName);
        if(htElements.welToAttach){
            htElements.welToAttach.insertBefore(htElements.welTemporaryUploadFileList, htElements.welToAttach.firstChild);
        }
        htVar.aTemporaryFileIds = [];

        // welContainer
        htElements.welContainer = elContainer;
        if(elContainer){
            // _isYonaAttachment는 milestone.View.js/issue.View.js/code.Diff.js/code.SvnDiff.js/
            // board.View.js가 중복 초기화 가드로 읽는 공개 계약이다 - 이름을 바꾸려면 그 5개
            // 파일도 함께 갱신해야 한다.
            elContainer._isYonaAttachment = true;
        }
        htVar.sResourceId = htVar.sResourceId || (elContainer ? elContainer.dataset.resourceId : undefined);
        htVar.sResourceType = htVar.sResourceType || (elContainer ? elContainer.dataset.resourceType : undefined);

        if (!htVar.attachments) {
            // data-attachments는 JSON 배열 문자열이다. jQuery .data()는 "["로 시작하는 문자열을
            // 자동으로 JSON.parse했지만 네이티브 dataset은 raw 문자열 그대로 돌려준다 - 여기서
            // 명시적으로 parse하지 않으면 기존 첨부파일이 페이지 로드시 렌더링되지 않는다.
            var sAttachmentsRaw = elContainer ? elContainer.dataset.attachments : undefined;
            htVar.attachments = sAttachmentsRaw ? JSON.parse(sAttachmentsRaw) : undefined;
        }

        // welTextarea (Optional)
        htElements.welTextarea = _toElement(htOptions.elTextarea);

        // attached files list
        htElements.welFileList = elContainer ? elContainer.querySelector("ul.attached-files") : null;
        htElements.welFileListHelp = elContainer ? elContainer.querySelector("p.help") : null;

        // -- help messages for additional uploader features
        var htEnv = yona.Files.getEnv();
        htElements.welHelpDroppable = elContainer ? elContainer.querySelector(".help-droppable") : null;
        htElements.welHelpPastable  = elContainer ? elContainer.querySelector(".help-pastable") : null;
        // 두 span 모두 CSS상 기본 표시값이 inline(.help 클래스 자체는 display:none, .help-droppable은
        // 그걸 오버라이드) - jQuery .show()가 <span> 기본 표시값(inline)으로 복원하던 것과 동일하게.
        _setDisplay(htElements.welHelpDroppable, htEnv.bDroppable, "inline");
        _setDisplay(htElements.welHelpPastable, htEnv.bPastable, "inline");
    }

    /**
     * 이벤트 핸들러 설정
     * attach Uploader custom event handlers
     *
     * @param {String} sUploaderId
     */
    function _attachUploaderEvent(sUploaderId){
        yona.Files.attach({
            "beforeUpload"  : _onBeforeUpload,
            "uploadProgress": _onUploadProgress,
            "successUpload" : _onSuccessUpload,
            "errorUpload"   : _onErrorUpload,
            "pasteFile"     : _onPasteFile,
            "pasteMarkdownTable"  : _onPasteMarkdownTable,
            "dropFile"      : _onDropFile
        }, sUploaderId);
    }

    /**
     * attach Uploader custom event handlers
     *
     * @param {String} sUploaderId
     */
    function _detachUploaderEvent(sUploaderId){
        yona.Files.detach({
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
                welItem.classList.add("complete");

                if(htElements.welTextarea){
                    welItem.addEventListener("click", _onClickListItem);
                }
            } else {
                welItem.id = oFile.nSubmitId;
                welItem.style.opacity = "0.2";
                welItem._yonaProgressBar = welItem.querySelector(".progress > .bar");
            }

            aWelItems.push(welItem);
            nFileSize += parseInt(oFile.size, 10);
        });

        if(aWelItems.length > 0){
            if(!htElements.welFileList){
                var elTplHolder = document.createElement("div");
                elTplHolder.innerHTML = htVar.sTplFileList;
                htElements.welFileList = elTplHolder.firstElementChild;
                htElements.welContainer.appendChild(htElements.welFileList);
            }
            // .attached-files/.help는 CSS 기본값이 display:none이라 jQuery .show()와
            // 동일하게 block으로 명시해야 한다.
            htElements.welFileList.style.display = "block";
            if(htElements.welFileListHelp){
                htElements.welFileListHelp.style.display = "block";
            }

            aWelItems.forEach(function(el){
                htElements.welFileList.appendChild(el);
            });
        }

        return nFileSize;
    }

    /**
     * Create uploaded file item HTML element using template string
     *
     * @param {Hash Table} htFile
     * @param {Boolean} bTemp
     *
     * @return {HTMLElement}
     */
    function _getFileItem(htFile, bTemp) {
        var sHtml = $yona.tmpl(htVar.sTplFileItem, {
            "fileId"  : htFile.id,
            "fileName": htFile.name,
            "fileHref": htFile.url,
            "fileSize": htFile.size,
            "fileSizeReadable": humanize.filesize(htFile.size),
            "mimeType": htFile.mimeType
        });
        var elTplHolder = document.createElement("div");
        elTplHolder.innerHTML = sHtml;
        var welItem = elTplHolder.firstElementChild;

        _showMimetypeIcon(welItem, htFile.mimeType);

        if(bTemp){
            welItem.classList.add("temporary");
        }
        return welItem;
    }

    /**
     * @param {Number} nSubmitId
     * @param {Object} oRes
     */
    function _updateFileItem(nSubmitId, oRes){
        var welItem = document.getElementById(String(nSubmitId));
        var welItemExists = htElements.welFileList ? htElements.welFileList.querySelector('[data-id="' + oRes.id + '"]') : null;

        if(welItemExists){
            if(welItem){
                welItem.remove();
            }
            _blinkFileItem(welItemExists);
            return false;
        }

        if(!welItem){
            return;
        }

        welItem.setAttribute("data-id", oRes.id);
        welItem.setAttribute("data-href", oRes.url);
        welItem.setAttribute("data-name", oRes.name);
        welItem.setAttribute("data-mime", oRes.mimeType);

        // for IE (uploadFileForm)
        var elName = welItem.querySelector(".name");
        if(elName){
            elName.innerHTML = oRes.name;
        }
        var elSize = welItem.querySelector(".size");
        if(elSize){
            elSize.innerHTML = humanize.filesize(oRes.size);
        }

        welItem.addEventListener("click", _onClickListItem);
    }

    function _blinkFileItem(welItem, sBlinkColor){
        var sBgColor;

        sBlinkColor = sBlinkColor || "#f36c22";
        sBgColor = getComputedStyle(welItem).background;
        welItem.style.background = sBlinkColor;

        setTimeout(function(){
            welItem.style.background = sBgColor;
        }, 500);
    }

    function _addUploadFileIdToListAndForm(sFileId) {
        if(htVar.aTemporaryFileIds.indexOf(sFileId) === -1) {
            htVar.aTemporaryFileIds.push(sFileId);
            htElements.welTemporaryUploadFileList.value = htVar.aTemporaryFileIds.join(",");
        }
    }

    function _removeDeletedFileIdFromListAndForm(sFileId) {
        var nIndex = htVar.aTemporaryFileIds.indexOf(sFileId.toString());
        if( nIndex !== -1){
            htVar.aTemporaryFileIds.splice(nIndex, 1);
            htElements.welTemporaryUploadFileList.value = htVar.aTemporaryFileIds.join(",");
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

        // welContainer로 스코핑하지 않고 문서 전체에서 OR로 찾는다 - 정상 처리시엔 두 조건이
        // 같은 노드를 가리키고, 중복 파일이라 임시 노드가 이미 remove()된 경우엔 data-id
        // 쪽만 남아 그걸로 찾는다.
        var welFileItem = document.getElementById(String(htData.nSubmitId)) ||
            document.querySelector('.attached-file[data-id="' + htData.oRes.id + '"]');
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
        var welItem = document.getElementById(String(nSubmitId));
        if(welItem && welItem._yonaProgressBar){
            welItem._yonaProgressBar.style.width = nProgress + "%";
        }

        if(nProgress*1 === 100 && welItem){
            welItem.style.opacity = "1";
            setTimeout(function(){
                welItem.classList.add("complete");
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
        var welItem = document.getElementById(String(htData.nSubmitId));
        if(welItem){
            welItem.remove();
        }

        if(!htElements.welFileList || htElements.welFileList.children.length === 0){
            if(htElements.welFileList){
                htElements.welFileList.style.display = "none";
            }
            if(htElements.welFileListHelp){
                htElements.welFileListHelp.style.display = "none";
            }
        }

        $yona.notify(Messages("common.attach.error.upload", htData.oRes.status, htData.oRes.statusText));
        _clearLinkInTextarea(_getTempLinkText(htData.nSubmitId + ".png"));
    }

    /**
     * On Click attached files list
     *
     * @param {Event} weEvt
     */
    function _onClickListItem(weEvt){
        var welTarget = weEvt.target;
        var welItem = weEvt.currentTarget;

        if(welTarget.classList.contains("btn-delete")){
            _deleteAttachedFile(welItem);
        } else {
            _insertLinkToTextarea(welItem);
        }
    }

    /**
     * @param {HTMLElement} welItem
     */
    function _deleteAttachedFile(welItem){
       var sURL = welItem.getAttribute("data-href");

        yona.Files.deleteFile({
           "sURL"   : sURL,
           "fOnLoad": function(){
                _removeDeletedFileIdFromListAndForm(welItem.dataset.id)
                _clearLinkInTextarea(welItem);
                welItem.remove();

                if(!htElements.welFileList || htElements.welFileList.children.length === 0){
                    if(htElements.welFileList){
                        htElements.welFileList.style.display = "none";
                    }
                    if(htElements.welFileListHelp){
                        htElements.welFileListHelp.style.display = "none";
                    }
                }
            },
            "fOnError": function(oRes){
                $yona.notify(Messages("common.attach.error.delete", oRes.status, oRes.statusText));
            }
       });
    }

    /**
     * <yona-markdown-editor>가 감싼 textarea는 CodeMirror -> textarea 단방향 동기화만
     * 있어서, raw textarea.value를 직접 바꿔도 CodeMirror는 이를 인지하지 못한다 - 포커스가
     * 빠지는 순간 CodeMirror가 자신의 버퍼로 값을 되돌려써 방금 삽입한 링크가 사라진다
     * (Playwright로 재현). textarea.closest('yona-markdown-editor, yona-markdown-editor-vue')로
     * 에디터를 찾아 value를 강제로 다시 밀어넣는다. 두 커스텀 엘리먼트 모두 같은 value
     * getter/setter 계약을 제공하며, 순수 textarea 화면에서는 closest()가 null이라 조용히
     * 스킵된다.
     */
    function _syncMarkdownEditor(welTextarea){
        var elEditor = welTextarea ? welTextarea.closest("yona-markdown-editor, yona-markdown-editor-vue") : null;
        if(elEditor){
            elEditor.value = welTextarea.value;
        }
    }

    /**
     * @param {Variant} vLink
     */
    function _insertLinkToTextarea(vLink){
        var welTextarea = htElements.welTextarea;

        if(!welTextarea){
            return false;
        }

        var sLink = (typeof vLink === "string") ? vLink : _getLinkText(vLink);
        var nPos = welTextarea.selectionStart;
        var sText = welTextarea.value;

        welTextarea.value = sText.substring(0, nPos) + sLink + sText.substring(nPos);
        _setCursorPosition(welTextarea, nPos + sLink.length);
        _syncMarkdownEditor(welTextarea);
    }

    /**
     * @return {Boolean} true if sMimeType is supported by HTML5 video element
     */
    function isHtml5Video(sMimeType) {
        return ["video/mp4", "video/ogg", "video/webm"]
            .indexOf((sMimeType || "").toString().trim().toLowerCase()) >= 0;
    }

    /**
     * Show a icon matches sMimeType on welFileItem
     */
    function _showMimetypeIcon(welFileItem, sMimeType) {
        if (isHtml5Video(sMimeType) && welFileItem) {
            var elIcon = welFileItem.querySelector(":scope > i.mimetype");
            if(elIcon){
                elIcon.classList.add('yobicon-video2');
                elIcon.style.display = "";
            }
        }
    }

    /**
     * @param {HTMLElement} welItem
     * @return {String}
     */
    function _getLinkText(welItem){
        var sMimeType = welItem.getAttribute("data-mime");
        var sFileName = welItem.getAttribute("data-name");
        var sFilePath = welItem.getAttribute("data-href");

        var sLinkText = '[' + sFileName + '](' + sFilePath + ') ';

        if (sMimeType.substr(0,5) === "image") {
            return '!' + sLinkText;
        } else if (isHtml5Video(sMimeType)) {
            var elWrap = document.createElement('div');
            var elVideo = document.createElement('video');
            elVideo.className = 'video-js';
            elVideo.setAttribute('data-setup', '{}');
            // jQuery .attr('controls', true)는 boolean 속성 관례대로 controls="controls"로
            // 직렬화한다 - 네이티브 setAttribute(name, true)는 문자열 "true"로 직렬화해버려
            // 결과 마크다운 원문 텍스트가 달라지므로 명시적으로 맞춘다.
            elVideo.setAttribute('controls', 'controls');
            var elSource = document.createElement('source');
            elSource.setAttribute('src', sFilePath);
            elSource.setAttribute('type', sMimeType);
            elVideo.appendChild(elSource);
            elWrap.appendChild(elVideo);
            elWrap.appendChild(document.createTextNode(sLinkText));
            return elWrap.innerHTML;
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
        if(!welTextarea){
            return false;
        }

        var sLink = (typeof vLink === "string") ? vLink : _getLinkText(vLink);
        var sRawData = welTextarea.value.split(sLink).join('');
        sRawData = sRawData.split(sLink.trim()).join('');
        welTextarea.value = sRawData;
        _syncMarkdownEditor(welTextarea);
    }

    /**
     * @param sLink1
     * @param sLink2
     * @private
     */
    function _replaceLinkInTextarea(sLink1, sLink2){
        var welTextarea = htElements.welTextarea;
        if(!welTextarea){
            return false;
        }

        var nCurPos = _getCursorPosition(welTextarea);
        var nGap = (sLink2.length - sLink1.length - 1);

        welTextarea.value = welTextarea.value.split(sLink1).join(sLink2);

        if(nGap > 0){
            _setCursorPosition(welTextarea, nCurPos + nGap);
        }
        _syncMarkdownEditor(welTextarea);
    }

    /**
     * @param welTextarea
     * @param nPos
     * @private
     */
    function _setCursorPosition(welTextarea, nPos){
        var elTextarea = welTextarea;

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
        return welTextarea.selectionStart;
    }

    /**
     * @param htData
     * @private
     */
    function _onPasteFile(htData){
        _insertLinkToTextarea(_getTempLinkText(htData.nSubmitId));
    }

    function _onPasteMarkdownTable(htData){
        var welTextarea = htElements.welTextarea;

        if(!welTextarea){
            return false;
        }

        var nPos = welTextarea.selectionStart;
        var sText = welTextarea.value;

        welTextarea.value = sText.substring(0, nPos) + htData.markdownTableText + sText.substring(nPos);
        _setCursorPosition(welTextarea, nPos + htData.markdownTableText.length);
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
        yona.Files.getList({
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
        if(htVar.bIsVueAttachments){
            return;
        }

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
