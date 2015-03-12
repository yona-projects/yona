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
(function(ns){

    var oNS = $yobi.createNamespace(ns);
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
            var waFileWrap = $("div.list-wrap[data-listpath]");

            waFileWrap.each(function(i, elList){
                var welList = $(elList);
                var sListPath = welList.data("listpath");
                var welTarget = $('[data-path="' + sListPath + '"]');

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
            var elTarget = weEvt.target;
            var welTarget = $(elTarget);

            if(elTarget.tagName.toLowerCase() === 'a' && welTarget.data("type") === "folder"){
                var sPreviousHash = document.location.hash;
                var sTargetPath = welTarget.data("targetpath");
                document.location.hash = sTargetPath;

                if(document.location.hash === sPreviousHash){
                    $(window).trigger("hashchange");
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
            var welList = $('[data-listpath="' + sTargetPath + '"]');

            var sCheckPath = "";
            htVar.aPathQueue = [];
            htVar.aWelList = [];

            sTargetPath.split("/").forEach(function(sPath){
                sCheckPath = (sCheckPath === "") ? sPath : (sCheckPath + "/" + sPath);
                htVar.aPathQueue.push(sCheckPath);
            });
            _updateBreadcrumbs(htVar.aPathQueue);

            if(welList.length > 0){
                welList.toggle();
                _setCurrentPathBold(sTargetPath);
            } else {
                _requestFolderList();
            }

        }

        function _requestFolderList(){
            if(htVar.aPathQueue.length === 0){
                NProgress.done();
                htVar.aWelList.forEach(function(welList){
                    welList.css("display", "block");
                });
                return false;
            }

            var sTargetPath = htVar.aPathQueue.shift();
            var welTarget = $('[data-targetpath="' + sTargetPath + '"]');

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
            var sURL = _getCorrectedPath(htVar.sMetaInfoURL, sTargetPath);
            var nParentDepth = welTarget.closest(".list-wrap").data("depth") || 0;
            var nNewDepth = nParentDepth + 1;
            _setIndentByDepth(nNewDepth);

            NProgress.start();
            $.ajax(sURL, {
                "success": function(oRes){
                    if(_isListExistsByPath(sTargetPath)){
                        NProgress.done();
                        return;
                    }

                    var aHTML = _getListHTML(oRes.data, sTargetPath);
                    var welTargetItem = $('.listitem[data-path="' + sTargetPath + '"]');
                    var welList = $('<div class="list-wrap" data-listPath="' + sTargetPath + '"></div>');

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
                        htVar.aWelList.forEach(function(welList){
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
            return ($('[data-listpath="' + sTargetPath + '"]').length > 0);
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
                        aHTML.push($yobi.tmpl(htVar.sTplListItem, htFile));
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
            htFile.commitMsg = $yobi.htmlspecialchars(htFile.msg || '');
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
            var welCurrent = $('[data-path="' + sPath + '"]');

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
            var elStyle = htVar.elStyle;

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
         * @require humanize.js
         */
        function _beautifyFileSize(){
            htElement.welShowfile.find(".filesize").each(function(i, el){
                var welTarget = $(el);
                welTarget.html(humanize.filesize(welTarget.text()));
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
                var sMimeType = htElement.welShowCode.data("mimetype");
                var aMatch = sMimeType.match(htVar.rxSub);
                var sMode = (aMatch ? aMatch[1] : _getEditorModeByPath(htVar.sPath)) || "text";

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
            return ($.inArray(sExt, htVar.aMarkdownExtension) !== -1);
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
            htElement.welShowCode.height(newHeight);
            htVar.oEditor.resize();
        }

        /**
         * @param {Array} aPathQueue
         */
        function _updateBreadcrumbs(aPathQueue){
            var sLink, sName;
            var aCrumbs = ['<a href="' + htVar.sBasePathURL + '">' + htVar.sProjectName + '</a>'];

            aPathQueue.forEach(function(sPath){
                sLink = _getCorrectedPath(htVar.sBasePathURL, sPath);
                sName = sPath.split("/").pop();
                aCrumbs.push('<a href="' + sLink + '">' + sName + '</a>');
            });

            var breadcrumb = $yobi.xssClean(aCrumbs.join(""));

            htElement.welBreadCrumbs.html(breadcrumb);
        }

        _init(htOptions || {});
    };
})("yobi.code.Browser");
