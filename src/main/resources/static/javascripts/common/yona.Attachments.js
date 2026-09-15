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
     * P3-70 라운드3: elContainer/elTextarea/targetFormId 정규화 헬퍼(yona.Files.js의 _toElement와
     * 동일한 관례) - raw element/jQuery 객체/셀렉터 문자열을 모두 받아준다. 이 생성자는 아직 jQuery인
     * 여러 호출부(board.View.js/code.Diff.js/code.SvnDiff.js/issue.View.js, 라운드4/5 대상)와 이미
     * vanilla인 호출부(board.Write.js/milestone.View.js/issue.Write.js/milestone.Write.js)에서 함께
     * 쓰인다.
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
            // isYonaAttachment는 milestone.View.js/issue.View.js/code.Diff.js/code.SvnDiff.js/
            // board.View.js가 읽는 공개 계약이다(중복 초기화 가드 - 이 컨테이너에 이미
            // Attachments를 붙였는지). 6단계(jQuery 완전 제거)에서 window.jQuery.data() 정적
            // 접근자를 걷어내고 순수 expando 프로퍼티로 바꿨다 - $yona.requestAs의
            // el._yonaRequestAs, yona.ui.Switch의 el._yonaSwitch와 동일한 관례. 읽는 쪽 5개
            // 파일도 전부 같은 프로퍼티로 갱신했다.
            elContainer._isYonaAttachment = true;
        }
        htVar.sResourceId = htVar.sResourceId || (elContainer ? elContainer.dataset.resourceId : undefined);
        htVar.sResourceType = htVar.sResourceType || (elContainer ? elContainer.dataset.resourceType : undefined);

        if (!htVar.attachments) {
            // P3-70 라운드3 함정 발견: data-attachments는 JSON 배열 문자열이다(예: issue/view.html의
            // th:data-attachments="${attachmentsJson}"). jQuery .data()는 "["로 시작하는 문자열을
            // 자동으로 JSON.parse해서 돌려주지만(내부 dataAttr 변환 - 라운드1에서 발견한 boolean
            // 자동변환과 같은 계열의 함정), 네이티브 dataset은 항상 raw 문자열 그대로 돌려준다.
            // 여기서 명시적으로 JSON.parse하지 않으면 htVar.attachments가 배열이 아니라 문자열이
            // 되어(그래도 truthy라 아래 _init의 분기까지는 타지만) _updateAttachments()가 기대하는
            // 모양이 아니게 되어 기존 첨부파일이 페이지 로드시 전혀 렌더링되지 않는 실제 회귀가
            // 생긴다(milestone.View.js/board.View.js 등이 이 경로로 Attachments를 초기화함).
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
                // progressBar 참조는 이 파일 내부에서만 쓰는 값이라(다른 파일이 읽지 않음) 코드베이스
                // 관례대로 커스텀 expando 프로퍼티에 raw element를 그대로 보관한다.
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
            // .upload-wrap .attached-files/.help는 CSS 기본값이 display:none이라(round2와 동일한
            // 판단), jQuery .show()가 <ul>/<p> 기본 표시값(block)으로 복원하던 것과 동일하게 맞춘다.
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

        // 원본은 "#nSubmitId, .attached-file[data-id=oRes.id]" 콤보 셀렉터로 전체 문서에서 찾았다
        // (welContainer로 스코핑하지 않음) - _updateFileItem이 정상 처리된 경우 두 조건 모두 같은
        // 노드를 가리켜 결과가 같고, welItemExists 분기(기존 파일과 중복)로 임시 노드가 이미
        // remove()된 경우엔 data-id 쪽만 남아 그걸 찾아준다. 동일하게 문서 전체에서 OR로 찾는다.
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
     * P3-50: <yona-markdown-editor>가 감싼 textarea는 CodeMirror -> textarea 단방향
     * 동기화만 있다 — 이 함수들처럼 raw textarea.val()을 직접 써서 프로그램적으로 내용을
     * 바꾸는 코드는 CodeMirror가 전혀 인지하지 못해 실제로는 반영되지 않는다(첨부파일 업로드
     * 성공 시 마크다운 링크가 삽입된 것처럼 보여도 실제 제출되는 내용에는 빠져있었음 —
     * Playwright로 실제 재현). raw textarea 조작 결과를 계산한 뒤 그 최종 문자열을
     * CodeMirror 쪽에도 강제로 밀어넣어야 한다 - 이 파일의 여러 호출 경로(클릭/드롭/붙여넣기/
     * 성공콜백)마다 시점·컨텍스트가 달라 CodeMirror API 경로 자체를 타지 못하는 경우가
     * 실측으로 발견됐기 때문에(예: 카드 클릭으로 링크를 넣은 직후엔 raw textarea에 정상
     * 반영되지만, 이후 제출 버튼 클릭으로 포커스가 빠지는 순간 CodeMirror가 자신의 변경
     * 없는 내부 버퍼를 textarea에 다시 밀어써 방금 넣은 값이 사라지는 것을 Playwright로
     * 재현) raw textarea를 항상 최종 소스오브트루스로 강제 동기화한다.
     *
     * 6단계(jQuery 완전 제거): lib/yona-markdown-editor(수정 금지 대상)가 예전엔
     * `window.jQuery(textarea).data(...)`로 노출하던 것을, 이제 커스텀 엘리먼트
     * 자신의 네이티브 `value` getter/setter로 노출한다 - `textarea.closest(
     * 'yona-markdown-editor')`로 그 엘리먼트를 직접 찾아 jQuery 없이 바로 접근한다(순수
     * textarea만 쓰는 화면에서는 closest()가 null을 반환해 그대로 조용히 스킵된다).
     */
    function _syncMarkdownEditor(welTextarea){
        var elEditor = welTextarea ? welTextarea.closest("yona-markdown-editor") : null;
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
