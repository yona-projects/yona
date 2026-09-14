/**
 * Yona, Project Hosting SW
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
(function(ns){

    var oNS = $yona.createNamespace(ns);
    oNS.container[oNS.name] = function(htOptions){

        var htVar = {};
        var htElement = {};

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

                window.dispatchEvent(new Event("hashchange"));
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
            // jQuery `$("#tplFileListItem").text()`는 매치가 없어도 빈 문자열을 반환한다.
            var elTplListItem = document.getElementById("tplFileListItem");
            htVar.sTplListItem = elTplListItem ? elTplListItem.textContent : "";
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
            htElement.welFileView = document.querySelectorAll('.file-wrap[data-type="file"]');
            htElement.welShowFile = document.getElementById("showFile"); // fileInfo
            htElement.welShowCode = document.getElementById("showCode"); // aceEditor
            htElement.welCodeVal  = document.getElementById("codeVal");
            htElement.welBreadCrumbs = document.getElementById("breadcrumbs");
            htElement.welBranches = document.getElementById("branches");
        }

        function _initDepthedList(){
            var waFileWrap = document.querySelectorAll("div.list-wrap[data-listpath]");

            waFileWrap.forEach(function(elList, i){
                var sListPath = elList.dataset.listpath;
                var welTarget = document.querySelector('[data-path="' + sListPath + '"]');

                // "content" data 키는 이 모듈 내부에서만 쓰는 값으로, 다른 파일이 읽지 않는
                // 커스텀 상태다(전수 조사 완료) - dataset 문자열 변환 함정과 무관하게 커스텀
                // expando로 그대로 보존.
                elList.__content = sListPath;
                // "depth"는 이후 산술 연산(+1)에 쓰이므로 dataset(항상 문자열)로 옮기면
                // 문자열 연결(예: "2"+1 === "21")로 깨진다 - 커스텀 expando로 숫자를 그대로 보존.
                elList.__depth = i + 1;
                elList.classList.add("depth-" + (i + 1));
                _setIndentByDepth(i + 1);

                if(welTarget){
                    welTarget.insertAdjacentElement("afterend", elList);
                }
            });
            document.querySelectorAll(".list-wrap").forEach(function(el){
                el.style.display = "block";
            });

            _setCurrentPathBold(htVar.sPath);
        }

        function _attachEvent(){
            document.querySelectorAll(".code-viewer-wrap").forEach(function(el){
                el.addEventListener("click", _onClickWrap);
            });
            window.addEventListener("hashchange", _onHashChange);
            if(htElement.welBranches){
                htElement.welBranches.addEventListener("change", _onChangeBranch);
            }
        }

        function _onChangeBranch(weEvt){
            location.href = weEvt.val;
        }

        /**
         * @param {Event} weEvt
         */
        function _onClickWrap(weEvt){
            var elTarget = weEvt.target;

            if(elTarget.tagName.toLowerCase() === 'a' && elTarget.dataset.type === "folder"){
                var sPreviousHash = document.location.hash;
                var sTargetPath = elTarget.dataset.targetpath;
                document.location.hash = sTargetPath;

                if(document.location.hash === sPreviousHash){
                    window.dispatchEvent(new Event("hashchange"));
                }

                weEvt.preventDefault();
                weEvt.stopPropagation();
                return false;
            }
        }

        function _onHashChange(){
            if(document.location.hash.length < 1){
                return false;
            }

            var sTargetPath = document.location.hash.substr(1);
            var waList = document.querySelectorAll('[data-listpath="' + sTargetPath + '"]');

            var sCheckPath = "";
            htVar.aPathQueue = [];
            htVar.aWelList = [];

            sTargetPath.split("/").forEach(function(sPath){
                sCheckPath = (sCheckPath === "") ? sPath : (sCheckPath + "/" + sPath);
                htVar.aPathQueue.push(sCheckPath);
            });
            _updateBreadcrumbs(htVar.aPathQueue);

            if(waList.length > 0){
                // jQuery의 인자 없는 `.toggle()`은 인라인 스타일이 아니라 실제 계산된 가시성
                // (`:visible`)을 보고 반전한다 - `.list-wrap`의 CSS 기본값이 `display:none`이라
                // 인라인 스타일만 비교하면(라운드1에서 발견된 Subtask.js와 동일한 함정) 최초
                // 1회는 항상 "숨김→숨김"으로 잘못 판정된다. 라운드4에서 확립한 `:visible` 근사
                // (offsetWidth||offsetHeight||getClientRects().length)로 실제 가시성을 판별한다.
                waList.forEach(function(el){
                    var bVisible = !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
                    el.style.display = bVisible ? "none" : "block";
                });
                _setCurrentPathBold(sTargetPath);
            } else {
                _requestFolderList();
            }

        }

        function _requestFolderList(){
            if(htVar.aPathQueue.length === 0){
                NProgress.done();
                htVar.aWelList.forEach(function(welList){
                    welList.style.display = "block";
                });
                return false;
            }

            var sTargetPath = decodeURI(htVar.aPathQueue.shift());
            var welTarget = document.querySelector('[data-targetpath="' + sTargetPath + '"]');

            if(_isListExistsByPath(sTargetPath)){
                _requestFolderList();
            } else {
                _appendFolderList(welTarget, sTargetPath);
            }
        }

        /**
         * @param {Element} welTarget
         * @param {String} sTargetPath
         */
        function _appendFolderList(welTarget, sTargetPath){
            var sURL = _getCorrectedPath(htVar.sMetaInfoURL, sTargetPath);
            var elParentWrap = welTarget ? welTarget.closest(".list-wrap") : null;
            var nParentDepth = (elParentWrap && elParentWrap.__depth) || 0;
            var nNewDepth = nParentDepth + 1;
            _setIndentByDepth(nNewDepth);

            NProgress.start();
            fetch(sURL).then(function(oResponse){
                if(!oResponse.ok){
                    throw new Error("request failed");
                }
                return oResponse.json();
            }).then(function(oRes){
                if(_isListExistsByPath(sTargetPath)){
                    NProgress.done();
                    return;
                }

                var aHTML = _getListHTML(oRes.data, sTargetPath);
                var welTargetItem = document.querySelector('.listitem[data-path="' + sTargetPath + '"]');
                var welList = document.createElement("div");
                welList.className = "list-wrap";
                welList.dataset.listpath = sTargetPath;

                welList.__depth = nNewDepth;
                welList.classList.add("depth-" + nNewDepth);
                welList.style.display = "none";
                welList.innerHTML = aHTML.join("");
                if(welTargetItem){
                    welTargetItem.insertAdjacentElement("afterend", welList);
                }
                htVar.aWelList.push(welList);

                if(htVar.aPathQueue.length > 0){
                    _requestFolderList();
                } else {
                    _setCurrentPathBold(sTargetPath);
                    htVar.aWelList.forEach(function(welList){
                        welList.style.display = "block";
                    });
                }

                NProgress.done();
            }).catch(function(){
                NProgress.done();
            });
        }

        /**
         * @param sTargetPath
         * @returns {boolean}
         * @private
         */
        function _isListExistsByPath(sTargetPath){
            return (document.querySelectorAll('[data-listpath="' + sTargetPath + '"]').length > 0);
        }

        /**
         * @param {Hash Table} htData
         * @param {String} sTargetPath
         * @return {Array}
         */
        function _getListHTML(htData, sTargetPath){
            var aHTML = [];

            // 폴더 먼저/ 파일 나중 순으로 만들기
            var htSortedData = _getSortedList(htData);
            var aProcessOrder = ["folder", "file"];

            aProcessOrder.forEach(function(sType){
                if(htSortedData[sType] instanceof Array){
                    htSortedData[sType].forEach(function(htFile){
                        htFile = _getFileInfoForTpl(htFile, sTargetPath);
                        aHTML.push($yona.tmpl(htVar.sTplListItem, htFile));
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
            var sType;
            var htListByType = {};

            // 타입별로 정리
            for(var sFileName in htData){
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
        function _getFileInfoForTpl(htFile, sTargetPath){
            var sAuthorURL = (htFile.userLoginId) ? '/'+ htFile.userLoginId : 'javascript:void(0); return false;';

            htFile.commitDate = (typeof htFile.createdDate !=='undefined') ? (moment(new Date(htFile.createdDate)).fromNow()) : '';
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
            var welCurrent = document.querySelector('[data-path="' + sPath + '"]');

            if(welCurrent){
                document.querySelectorAll(".currentPath").forEach(function(el){
                    el.classList.remove("currentPath");
                });
                welCurrent.classList.add("currentPath");
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
            var elStyle = htVar.elStyle;

            if(elStyle.addRule){ // Chrome, IE
                elStyle.addRule(sSelector, sRule);
            } else if(htVar.elStyle.insertRule){ // Firefox
                elStyle.insertRule(sSelector + ' { ' + sRule + ' }', elStyle.cssRules.length);
            }
        }

        function _initShowFile(){
            if(htElement.welShowCode){
                _initCodeView(); // 코드보기
            } else if(htElement.welShowFile){
                _beautifyFileSize(); // 파일정보
            }
        }

        /**
         * @require humanize.js
         */
        function _beautifyFileSize(){
            // 원본 코드 그대로 보존: `htElement.welShowfile`(소문자 f)는 `_initElement`가 실제로
            // 채우는 `htElement.welShowFile`(대문자 F)와 이름이 달라 항상 undefined다 - 이
            // 함수가 실제로 호출되면 원본도 이 지점에서 TypeError를 던졌을 pre-existing 오타
            // 버그다(이 파일 자체가 어느 템플릿에서도 로드되지 않는 죽은 코드라 실제로 발현된
            // 적은 없다). jQuery 전환과 무관해 고치지 않고 동일하게 보존한다.
            htElement.welShowfile.querySelectorAll(".filesize").forEach(function(el){
                el.innerHTML = humanize.filesize(el.textContent);
            });
        }

        /**
         * @param {String} sCode
         * @param {String} sMode
         */
        function _initCodeView(){

            if(_isMarkdownExtension(htVar.sPath)) {

                htElement.welFileView.forEach(function(el){
                    el.classList.remove('file-wrap');
                });

                htElement.welCodeVal.classList.remove('hidden');
                htElement.welCodeVal.classList.add('markdown-wrap', 'codebrowser-markdown');
            } else {

                if(!htVar.oEditor){
                    htVar.oEditor = _getEditor("showCode");
                }

                // Use explicit MIME Type if the server told which language is used to write the source code.
                // or the client should guess it.
                var sMimeType = htElement.welShowCode.dataset.mimetype;
                var aMatch = sMimeType.match(htVar.rxSub);
                var sMode = (aMatch ? aMatch[1] : _getEditorModeByPath(htVar.sPath)) || "text";

                htVar.oSession.setMode("ace/mode/" + sMode);
                htVar.oSession.setValue(htElement.welCodeVal.textContent);
                setTimeout(_resizeEditor, 50);
            }
        }

        /**
         * @param {String} sContainer
         * @return {Object}
         */
        function _getEditor(sContainer){
            var oAce = ace.edit(sContainer);
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
            var sExt =  getExt(basename(htVar.sPath));
            return (htVar.aMarkdownExtension.indexOf(sExt) !== -1);
        }

        /**
         * @param {String} sPath
         * @return {String}
         */
        function _getEditorModeByPath(sPath){
            var sExt = getExt(basename(sPath));
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

            for(var sMode in htVar.htExtMap){
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
            var nLineHeight = htVar.oEditor.renderer.lineHeight;
            nLineHeight = (nLineHeight === 1) ? (htVar.nFontSize + 4) : nLineHeight;

            var newHeight = (htVar.oSession.getScreenLength() * nLineHeight) + htVar.oEditor.renderer.scrollBar.getWidth();
            htElement.welShowCode.style.height = newHeight + "px";
            htVar.oEditor.resize();
        }

        /**
         * @param {Array} aPathQueue
         */
        function _updateBreadcrumbs(aPathQueue){
            var sLink, sName;
            var aCrumbs = ['<a href="' + htVar.sBasePathURL + '">' + htVar.sProjectName + '</a>'];

            aPathQueue.forEach(function(sPath){
                sPath = decodeURI(sPath);
                sLink = _getCorrectedPath(htVar.sBasePathURL, sPath);
                sName = sPath.split("/").pop();
                aCrumbs.push('<a href="' + sLink + '">' + sName + '</a>');
            });

            var breadcrumb = $yona.xssClean(aCrumbs.join(""));

            htElement.welBreadCrumbs.innerHTML = breadcrumb;

            var elNewFileLink = document.getElementById("new-file-link");
            var path = window.location.hash.substr(1);

            // 'New File' Button supports only git repositories.
            if (elNewFileLink) {
                var newPath = updateQueryStringParameter(elNewFileLink.getAttribute("href"), "path", path + "/");
                elNewFileLink.setAttribute("href", newPath);
            }
        }

        function updateQueryStringParameter(uri, key, value) {
            var re = new RegExp("([?&])" + key + "=.*?(&|$)", "i");
            var separator = uri.indexOf('?') !== -1 ? "&" : "?";
            if (uri.match(re)) {
                return uri.replace(re, '$1' + key + "=" + value + '$2');
            }
            else {
                return uri + separator + key + "=" + value;
            }
        }

        _init(htOptions || {});
    };
})("yona.code.Browser");
