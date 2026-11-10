/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Jihan Kim
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

import { register } from "../namespace.js";
import { fromNow } from "../dates.js";

const Browser = function(htOptions){

    const htVar = {};
    const htElement = {};

    /**
     * @param {Hash Table} htOptions
     */
    function _init(htOptions){
        _initVar(htOptions);
        _initElement(htOptions);

        if(htElement.welFileView.length > 0){
            // 파일 보기
            _initShowFile();
        } else {
            // 폴더 목록
            _initDepthedList();
            _attachEvent();

            $(window).trigger("hashchange");
        }
    }

    /**
     * initialize variables
     *
     * @param {Hash Table} htOptions
     */
    function _initVar(htOptions){
        htVar.sProjectName = htOptions.sProjectName;
        htVar.sMetaInfoURL = htOptions.sMetaInfoURL;
        htVar.sBasePathURL = htOptions.sBasePathURL;
        htVar.elStyle = document.styleSheets[0];
        htVar.sTplListItem = $("#tplFileListItem").text();
        htVar.rxSub = /text\/x-(.+)-source/;
        htVar.rxScala = /\.scala\.html$/i;
        htVar.sPath = htOptions.sInitialPath;
        htVar.nFontSize = 12;
        htVar.aPathQueue = [];
        htVar.aMarkdownExtension = ['markdown', 'mdown', 'mkdn', 'mkd', 'md', 'mdwn'];
    }

    /**
     * initialize element variables
     */
    function _initElement(htOptions){
        htElement.welFileView = $(".file-wrap[data-type=file]");
        htElement.welShowFile = $("#showFile"); // fileInfo
        htElement.welShowCode = $("#showCode"); // aceEditor
        htElement.welCodeVal  = $("#codeVal");
        htElement.welBreadCrumbs = $("#breadcrumbs");
        htElement.welBranches = $("#branches");
    }

    function _initDepthedList(){
        const waFileWrap = $("div.list-wrap[data-listpath]");

        waFileWrap.each((i, elList) =>{
            const welList = $(elList);
            const sListPath = welList.data("listpath");
            const welTarget = _findByAttr("data-path", sListPath);

            welList.data("content", sListPath);
            welList.data("depth", i+1);
            welList.addClass("depth-" + (i+1));
            _setIndentByDepth(i+1);

            welTarget.after(welList);
        });
        $(".list-wrap").show();

        _setCurrentPathBold(htVar.sPath);
    }

    function _attachEvent(){
        $('.code-viewer-wrap').click(_onClickWrap);
        $(window).on("hashchange", _onHashChange);
        htElement.welBranches.on("change", _onChangeBranch);
    }

    function _onChangeBranch(weEvt){
        location.href = weEvt.val;
    }

    /**
     * @param {Wrapped Event} weEvt
     */
    function _onClickWrap(weEvt){
        const elTarget = weEvt.target;
        const welTarget = $(elTarget);

        if(elTarget.tagName.toLowerCase() === 'a' && welTarget.data("type") === "folder"){
            const sPreviousHash = document.location.hash;
            const sTargetPath = welTarget.data("targetpath");
            document.location.hash = sTargetPath;

            if(document.location.hash === sPreviousHash){
                $(window).trigger("hashchange");
            }

            weEvt.preventDefault();
            weEvt.stopPropagation();
            return false;
        }
    }

    /**
     * 주소 해시에서 폴더 경로를 읽는다. 폴더 링크에 붙는 `#cb-<경로>` 앵커는 접두어 `cb-`를 벗겨 경로로 읽는다.
     * 다만 `cb-`로 시작하는 폴더가 화면에 실제로 있으면 접두어가 아니라 폴더 이름이다.
     *
     * @return {String} 폴더 경로. 해시가 없으면 빈 문자열
     */
    /**
     * 속성 값이 [sValue]와 같은 요소를 찾는다. 경로(주소 해시나 파일 이름)를 선택자 문자열에 이어 붙이면 따옴표나 대괄호가 든 경로에서
     * 선택자 오류가 나므로 속성 값을 직접 비교한다. 평범한 경로에서는 `[속성="값"]` 선택자와 같은 요소를 돌려준다.
     *
     * @param {String} sAttr 속성 이름(data-path 등)
     * @param {String} sValue
     * @return {Wrapped Array}
     */
    function _findByAttr(sAttr, sValue){
        return $("[" + sAttr + "]").filter((i, el) => el.getAttribute(sAttr) === sValue);
    }

    function _getHashPath(){
        const sHash = document.location.hash.substr(1);

        if(sHash.indexOf("cb-") === 0 && _findByAttr("data-targetpath", sHash).length === 0){
            return sHash.substr(3);
        }

        return sHash;
    }

    function _onHashChange(){
        if(document.location.hash.length < 1){
            return false;
        }

        const sHash = document.location.hash.substr(1);
        const sTargetPath = _getHashPath();
        const welList = _findByAttr("data-listpath", sTargetPath);
        const bAnchor = (sTargetPath !== sHash);

        let sCheckPath = "";
        htVar.aPathQueue = [];
        htVar.aWelList = [];

        sTargetPath.split("/").forEach((sPath) =>{
            sCheckPath = (sCheckPath === "") ? sPath : (sCheckPath + "/" + sPath);
            htVar.aPathQueue.push(sCheckPath);
        });
        _updateBreadcrumbs(htVar.aPathQueue);

        if(welList.length > 0){
            // 앵커로 들어왔을 때 이미 그려진 목록은 접지 않는다
            if(!bAnchor){
                welList.toggle();
            }
            _setCurrentPathBold(sTargetPath);
        } else {
            _requestFolderList();
        }

    }

    function _requestFolderList(){
        if(htVar.aPathQueue.length === 0){
            NProgress.done();
            htVar.aWelList.forEach((welList) =>{
                welList.css("display", "block");
            });
            return false;
        }

        const sTargetPath = decodeURI(htVar.aPathQueue.shift());
        const welTarget = _findByAttr("data-targetpath", sTargetPath);

        if(_isListExistsByPath(sTargetPath)){
            _requestFolderList();
        } else {
            _appendFolderList(welTarget, sTargetPath);
        }
    }

    /**
     * @param {Wrapped Element} welTarget
     * @param {String} sTargetPath
     */
    function _appendFolderList(welTarget, sTargetPath){
        const sURL = _getCorrectedPath(htVar.sMetaInfoURL, sTargetPath);
        const nParentDepth = welTarget.closest(".list-wrap").data("depth") || 0;
        const nNewDepth = nParentDepth + 1;
        _setIndentByDepth(nNewDepth);

        NProgress.start();
        yona.http.ajax(sURL, {
            "success": function(oRes){
                if(_isListExistsByPath(sTargetPath)){
                    NProgress.done();
                    return;
                }

                const aHTML = _getListHTML(oRes.data, sTargetPath);
                const welTargetItem = _findByAttr("data-path", sTargetPath).filter(".listitem");
                const welList = $('<div class="list-wrap"></div>').attr("data-listPath", sTargetPath);

                welList.data("depth", nNewDepth);
                welList.addClass("depth-" + nNewDepth);
                welList.css("display", "none");
                welList.html(aHTML);
                welTargetItem.after(welList);
                htVar.aWelList.push(welList);

                if(htVar.aPathQueue.length > 0){
                    _requestFolderList();
                } else {
                    _setCurrentPathBold(sTargetPath);
                    htVar.aWelList.forEach((welList) =>{
                        welList.css("display", "block");
                    });
                }

                NProgress.done();
            },
            "error"  : function(){
                NProgress.done();
            }
        });
    }

    /**
     * @param sTargetPath
     * @returns {boolean}
     * @private
     */
    function _isListExistsByPath(sTargetPath){
        return (_findByAttr("data-listpath", sTargetPath).length > 0);
    }

    /**
     * @param {Hash Table} htData
     * @param {String} sTargetPath
     * @return {Array}
     */
    function _getListHTML(htData, sTargetPath){
        const aHTML = [];

        // 폴더 먼저/ 파일 나중 순으로 만들기
        const htSortedData = _getSortedList(htData);
        const aProcessOrder = ["folder", "file"];

        aProcessOrder.forEach((sType) =>{
            if(htSortedData[sType] instanceof Array){
                htSortedData[sType].forEach((htFile) =>{
                    htFile = _getFileInfoForTpl(htFile, sTargetPath);
                    aHTML.push($yona.tmpl(htVar.sTplListItem, _escapeFileInfo(htFile)));
                });
            }
        });
       return aHTML;
    }

    /**
     * @param {Hash Table} htData
     * @return {Hash Table}
     */
    function _getSortedList(htData){
        let sType;
        let htFileInfo; // 모듈은 엄격 모드라 선언 없는 대입이 오류이다(옛 코드는 전역을 만들었다)
        const htListByType = {};

        // 타입별로 정리
        for(const sFileName in htData){
            htFileInfo = htData[sFileName];
            htFileInfo.fileName = sFileName;
            sType = htFileInfo.type;

            htListByType[sType] = htListByType[sType] || [];
            htListByType[sType].push(htData[sFileName]);
        }

        return htListByType;
    }

    /**
     * @param {Hash Table} htFile
     * @param {String} sTargetPath
     */
    /**
     * 템플릿에 넣을 값 중 파일 이름과 경로처럼 저장소에서 온 글자를 이스케이프한 복사본을 돌려준다.
     * `avatarImg`, `commitMsg`, `commitDate`는 이미 만든 HTML이거나 이스케이프된 값이라 그대로 둔다.
     * 파일 이름에 `<img src=x onerror=...>` 같은 글자가 있어도 마크업이 되지 않게 하려는 것이다.
     *
     * @param {Hash Table} htFile
     * @return {Hash Table}
     */
    function _escapeFileInfo(htFile){
        const htEscaped = $.extend({}, htFile);
        ["fileName", "listPath", "targetPath", "path", "fileClass", "type", "commitUrl"].forEach((sKey) =>{
            if(typeof htEscaped[sKey] === "string"){
                htEscaped[sKey] = $yona.escapeHtml(htEscaped[sKey]);
            }
        });
        return htEscaped;
    }

    function _getFileInfoForTpl(htFile, sTargetPath){
        const sAuthorURL = (htFile.userLoginId) ? '/'+ htFile.userLoginId : 'javascript:void(0); return false;';

        htFile.commitDate = (typeof htFile.createdDate !=='undefined') ? fromNow(new Date(htFile.createdDate)) : '';
        htFile.fileClass = (htFile.fileName ==='..') ? 'updir' : (htFile.type === "folder" ? 'dynatree-ico-cf' : 'dynatree-ico-c');
        htFile.avatarImg = (typeof htFile.avatar !== 'undefined') ? '<a href="'+ sAuthorURL + '" class="avatar-wrap smaller"><img src="' + htFile.avatar + '"></a>' : '';
        htFile.commitMsg = $yona.htmlspecialchars(htFile.msg || '');
        htFile.listPath = sTargetPath;
        htFile.targetPath = _getCorrectedPath(sTargetPath, htFile.fileName);
        htFile.path = _getCorrectedPath(htVar.sBasePathURL, htFile.targetPath);

        if(htFile.type === "folder"){
            htFile.path += ("#cb-" + sTargetPath + htFile.fileName);
        }

        return htFile;
    }

    /**
     * @param {String} sPath
     * @param {String} sFileName
     * @return {String}
     */
    function _getCorrectedPath(sPath, sFileName){
        return sPath + (sPath.substr(-1) === "/" ? "" : "/") + sFileName;
    }

    /**
     * @param {String} sPath
     */
    function _setCurrentPathBold(sPath){
        const welCurrent = _findByAttr("data-path", sPath);

        if(welCurrent.length > 0){
            $(".currentPath").removeClass("currentPath");
            welCurrent.addClass("currentPath");
        }
    }

    /**
     * @param {Number} nDepth
     */
    function _setIndentByDepth(nDepth){
        nDepth = parseInt(nDepth, 10);
        htVar.aAddedDepth = htVar.aAddedDepth || [];

        // 중복 방지용
        if(htVar.aAddedDepth.indexOf(nDepth) === -1){
            htVar.aAddedDepth.push(nDepth);
            _addCSSRule('.depth-' + nDepth + ' .filename', 'padding-left:' + (20 * nDepth) + 'px');
        }
    }

    /**
     * @param {String} sSelector
     * @param {String} sRule
     */
    function _addCSSRule(sSelector, sRule){
        const elStyle = htVar.elStyle;

        if(elStyle.addRule){ // Chrome, IE
            elStyle.addRule(sSelector, sRule);
        } else if(htVar.elStyle.insertRule){ // Firefox
            elStyle.insertRule(sSelector + ' { ' + sRule + ' }', elStyle.cssRules.length);
        }
    }

    function _initShowFile(){
        if(htElement.welShowCode.length > 0){
            _initCodeView(); // 코드보기
        } else if(htElement.welShowFile.length > 0){
            _beautifyFileSize(); // 파일정보
        }
    }

    /**
     * @require common/yona.Common.js
     */
    function _beautifyFileSize(){
        // v1.6는 `welShowfile`(소문자 f)을 읽어 TypeError가 났다(C022, C113-1)
        htElement.welShowFile.find(".filesize").each((i, el) =>{
            const welTarget = $(el);
            welTarget.html($yona.formatFileSize(welTarget.text()));
        });
    }

    /**
     * @param {String} sCode
     * @param {String} sMode
     */
    function _initCodeView(){
        
        if(_isMarkdownExtension(htVar.sPath)) {

            htElement.welFileView.removeClass('file-wrap');

            htElement.welCodeVal
                .removeClass('hidden')
                .addClass('markdown-wrap codebrowser-markdown');
        } else {

            if(!htVar.oEditor){
                htVar.oEditor = _getEditor("showCode");
            }

            // Use explicit MIME Type if the server told which language is used to write the source code.
            // or the client should guess it.
            const sMimeType = htElement.welShowCode.data("mimetype");
            const aMatch = sMimeType.match(htVar.rxSub);
            const sMode = (aMatch ? aMatch[1] : _getEditorModeByPath(htVar.sPath)) || "text";

            htVar.oSession.setMode("ace/mode/" + sMode);
            htVar.oSession.setValue(htElement.welCodeVal.text());
            setTimeout(_resizeEditor, 50);
        }
    }

    /**
     * @param {String} sContainer
     * @return {Object}
     */
    function _getEditor(sContainer){
        const oAce = ace.edit(sContainer);
        oAce.setTheme("ace/theme/clouds");
        oAce.setHighlightActiveLine(false);
        oAce.setReadOnly(true);
        oAce.setFontSize(htVar.nFontSize);
        oAce.setShowPrintMargin(false);

        // EditSession
        htVar.oSession = oAce.getSession();

        return oAce;
    }

    function _isMarkdownExtension(sPath) {
        const sExt =  getExt(basename(htVar.sPath));
        return htVar.aMarkdownExtension.includes(sExt);
    }

    /**
     * @param {String} sPath
     * @return {String}
     */
    function _getEditorModeByPath(sPath){
        const sExt = getExt(basename(sPath));
        return ext2mode(sExt);
    }

    /**
     * @param {String} sExt
     * @return {Variant}
     */
    function ext2mode(sExt){
        sExt = sExt.toLowerCase();

        htVar.htExtMap = htVar.htExtMap || {
            "actionscript": ["as", "actionscript"],
            "assembly_x86": ["a", "a86"],
            "ada": ["ada"],
            "batchfile": ["bat"],
            "coffee": ["coffee"],
            "c_cpp": ["c", "cp", "cpp", "c__", "cxx"],
            "csharp": ["cs"],
            "css": ["css"],
            "d": ["d"],
            "diff": ["diff"],
            "dart": ["dart"],
            "erlang": ["erl"],
            "html": ["html", "htm"],
            "ini": ["ini", "config"],
            "jade": ["jade"],
            "java": ["java"],
            "javascript": ["js"],
            "json": ["json"],
            "jsp": ["jsp"],
            "latex": ["dtx", "tex"],
            "less": ["less"],
            "makefile": ["mk", "emakrfile", "emakerfile"],
            "markdown": ["md", "readme", "license"],
            "php": ["php","php3","php4","php5","php6","phps","inc"],
            "python": ["py"],
            "r": ["r"],
            "ruby": ["rb", "ruby"],
            "sh": ["sh"],
            "svg": ["svg"],
            "scala": ["scala"],
            "sql": ["sql"],
            "text": ["txt", "gitignore", "sbt"],
            "vbscript": ["vbs"],
            "xml": ["xml"],
            "yaml": ["yaml", "yml"]
        };

        for(const sMode in htVar.htExtMap){
            if(htVar.htExtMap[sMode].indexOf(sExt) > -1){
                return sMode;
            }
        }

        return false;
    }

   /**
     * @param {String} sPath
     * @return {String}
     */
    function basename(sPath){
        return sPath.split("/").pop();
    }

    /**
     * @param {String} sFilename
     * @return {String}
     */
    function getExt(sFilename){
        return htVar.rxScala.test(sFilename) ? "scala" : sFilename.split(".").pop();
    }

    function _resizeEditor(){
        let nLineHeight = htVar.oEditor.renderer.lineHeight;
        nLineHeight = (nLineHeight === 1) ? (htVar.nFontSize + 4) : nLineHeight;

        const newHeight = (htVar.oSession.getScreenLength() * nLineHeight) + htVar.oEditor.renderer.scrollBar.getWidth();
        htElement.welShowCode.height(newHeight);
        htVar.oEditor.resize();
    }

    /**
     * @param {Array} aPathQueue
     */
    function _updateBreadcrumbs(aPathQueue){
        let sLink, sName;
        const aCrumbs = ['<a href="' + htVar.sBasePathURL + '">' + htVar.sProjectName + '</a>'];

        aPathQueue.forEach((sPath) =>{
            sPath = decodeURI(sPath);
            sLink = _getCorrectedPath(htVar.sBasePathURL, sPath);
            sName = sPath.split("/").pop();
            aCrumbs.push('<a href="' + sLink + '">' + sName + '</a>');
        });

        const breadcrumb = $yona.xssClean(aCrumbs.join(""));

        htElement.welBreadCrumbs.html(breadcrumb);

        const $newFileLink = $("#new-file-link");
        const path = _getHashPath();

        // 'New File' Button supports only git repositories.
        if ($newFileLink[0]) {
            const newPath = updateQueryStringParameter($newFileLink.attr("href"), "path", path + "/");
            $newFileLink.attr("href", newPath);
        }
    }

    function updateQueryStringParameter(uri, key, value) {
        const re = new RegExp("([?&])" + key + "=.*?(&|$)", "i");
        const separator = uri.indexOf('?') !== -1 ? "&" : "?";
        if (uri.match(re)) {
            return uri.replace(re, '$1' + key + "=" + value + '$2');
        }
        else {
            return uri + separator + key + "=" + value;
        }
    }

    _init(htOptions || {});
};

export default Browser;

register("yona.code.Browser", Browser);
