/**
 * @(#)yobi.code.Browser.js 2013.09.09
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://yobi.dev.naver.com/license
 */

(function(ns){

	var oNS = $yobi.createNamespace(ns);
	oNS.container[oNS.name] = function(htOptions){

	    var htVar = {};
        var htElement = {};

        /**
         * 코드브라우저 초기화
         * 
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
         * 변수 초기화
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
            htVar.nFontSize = 13;
            htVar.aPathQueue = [];
            
            // Spinner Option
            htVar.htOptSpinner = {
                lines: 10,    // The number of lines to draw
                length: 8,    // The length of each line
                width: 4,     // The line thickness
                radius: 8,    // The radius of the inner circle
                corners: 1,   // Corner roundness (0..1)
                rotate: 0,    // The rotation offset
                direction: -1, // 1: clockwise, -1: counterclockwise
                color: '#000',  // #rgb or #rrggbb
                speed: 1.5,     // Rounds per second
                trail: 60,      // Afterglow percentage
                shadow: false,  // Whether to render a shadow
                hwaccel: false, // Whether to use hardware acceleration
                className: 'spinner', // The CSS class to assign to the spinner
                zIndex: 2e9, // The z-index (defaults to 2000000000)
                top: 'auto', // Top position relative to parent in px
                left: 'auto' // Left position relative to parent in px
            };
        }
        
        /**
         * 엘리먼트 변수 초기화
         * initialize element variables
         */
        function _initElement(htOptions){
            htElement.welFileView = $(".file-wrap[data-type=file]");
            htElement.welShowFile = $("#showFile"); // fileInfo
            htElement.welShowCode = $("#showCode"); // aceEditor
            htElement.welCodeVal  = $("#codeVal");
            htElement.welBreadCrumbs = $("#breadcrumbs");
            htElement.elSpinTarget = document.getElementById('spin');
        }
        
        /**
         * 첫 페이지 로딩 때 현재 페이지의 list-wrap 목록들을 계층단위로 맞추어 배치하는 함수
         * code/view.scala.html 에서 상위 경로부터 순서대로 파일 목록을 표시하는 구조임
         * 목록 페이지 로딩때 처음 한 번만 호출한다
         * 
         * 예: app/models/resource 경로를 표현하는 경우
         * 1. [app] 목록, [app/models] 목록, [app/models/resource] 목록을 각각 만든 뒤
         * 
         * 2. [app/models] 목록을 [app] 목록의 
         *    data-path="app/models" 항목 바로 다음에 위치하도록 만들고
         * 
         * 3. [app/models/resource] 목록을 [app/models] 목록의 
         *    data-path="app/models/resource" 항목 바로 다음에 위치하도록 만드는 것이다
         */
        function _initDepthedList(){
            var waFileWrap = $("div.list-wrap[data-listpath]");
            
            waFileWrap.each(function(i, elList){
                var welList = $(elList);
                var sListPath = welList.data("listpath");
                var welTarget = $('[data-path="' + sListPath + '"]');
                
                welList.data("content", sListPath); // 목록이 표시하고 있는 경로
                welList.data("depth", i+1);         // indent 정보
                welList.addClass("depth-" + (i+1)); // indent CSS
                _setIndentByDepth(i+1);              // indent CSS 설정
                
                // 목록의 위치 이동
                welTarget.after(welList);
            });
            $(".list-wrap").show();

            // 현재 페이지 주소와 일치하는 항목을 강조표시
            _setCurrentPathBold(htVar.sPath);
        }
        
        /**
         * 이벤트 핸들러 설정
         */
        function _attachEvent(){
            $('.code-viewer-wrap').click(_onClickWrap);
            $(window).on("hashchange", _onHashChange);
        }
        
        /**
         * 코드 목록 영역을 클릭했을 때의 이벤트 핸들러
         * - 각 항목에 핸들러를 거는 것 보다 메모리 절약
         * 
         * @param {Wrapped Event} weEvt
         */
        function _onClickWrap(weEvt){
            var elTarget = weEvt.target;
            var welTarget = $(elTarget);
            
            /// 폴더 링크 클릭시
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
        
        /**
         * HashChange 이벤트 핸들러
         * 하위 폴더 목록을 동적으로 취급하기 위한 함수
         */
        function _onHashChange(){
            if(document.location.hash.length < 1){
                return false;
            }
            
            var sTargetPath = document.location.hash.substr(1);
            var welList = $('[data-listpath="' + sTargetPath + '"]');
            
            if(welList.length > 0){ // 이미 리스트를 붙인 상태라면
                welList.toggle();   // 목록 표시 여부만 토글하고
                _setCurrentPathBold(sTargetPath);
            } else {                // 없으면 새로 리스트 만들어서 붙임
                var sCheckPath = "";
                htVar.aPathQueue = [];
                htVar.aWelList = [];
                
                // Path 단위로 목록이 필요함
                sTargetPath.split("/").forEach(function(sPath){
                    sCheckPath = (sCheckPath === "") ? sPath : (sCheckPath + "/" + sPath);
                    htVar.aPathQueue.push(sCheckPath);
                });
                
                _updateBreadcrumbs(htVar.aPathQueue);
                _requestFolderList();
            }
        }
        
        /**
         * htVar.aPathQueue 변수를 이용해서
         * 순서대로 폴더 목록을 가져오기 위해 필요한 함수
         */
        function _requestFolderList(){
            if(htVar.aPathQueue.length === 0){
                _stopSpinner();
                htVar.aWelList.forEach(function(welList){
                    welList.css("display", "block");
                });
                return false;
            }
            
            var sTargetPath = htVar.aPathQueue.shift();
            var welTarget = $('[data-targetpath="' + sTargetPath + '"]');
            var welList = $('[data-listpath="' + sTargetPath + '"]');

            if(welList.length === 0){
                // 해당 경로의 목록이 존재하지 않을 때에만 요청하고
                _appendFolderList(welTarget, sTargetPath);
            } else {
                // 이미 있으면 다음 경로순서로 넘어간다
                _requestFolderList();
            }
        }
        
        /**
         * 지정한 경로의 목록을 생성하여 적절한 위치에 붙인다
         * 
         * @param {Wrapped Element} welTarget
         * @param {String} sTargetPath
         */
        function _appendFolderList(welTarget, sTargetPath){
            var sURL = _getCorrectedPath(htVar.sMetaInfoURL, sTargetPath);
            var nParentDepth = welTarget.closest(".list-wrap").data("depth") || 0; // 부모 경로의 depth
            var nNewDepth = nParentDepth + 1; // 지정한 경로의 depth 판단
            _setIndentByDepth(nNewDepth); // CSS Rule 먼저 추가해 놓고
            
            // AJAX 호출로 데이터 요청
            _startSpinner();
            $.ajax(sURL, {
                "success": function(oRes){
                    var aHTML = _getListHTML(oRes.data, sTargetPath);
                    var welTargetItem = $('.listitem[data-path="' + sTargetPath + '"]');
                    var welList = $('<div class="list-wrap" data-listPath="' + sTargetPath + '"></div>');
                    
                    welList.data("depth", nNewDepth);
                    welList.addClass("depth-" + nNewDepth);
                    welList.css("display", "none");
                    welList.html(aHTML);          // 내용을 채우고
                    welTargetItem.after(welList); // 목록에 추가
                    htVar.aWelList.push(welList);

                    if(htVar.aPathQueue.length > 0){
                        _requestFolderList();
                    } else {
                        _setCurrentPathBold(sTargetPath);
                        htVar.aWelList.forEach(function(welList){
                            welList.css("display", "block");
                        });
                    }
                    _stopSpinner();
                },
                "error"  : function(){
                    // TODO: #255 서버 응답에 맞는 오류 메시지 보여주기
                    _stopSpinner();
                }
            });
        }
        
        /**
         * 경로 내 목록 데이터를 템플릿과 결합해서 HTML 문자열을 담은 배열로 반환한다
         * 
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
         * 목록 데이터를 타입별로 정리해서 반환
         * 
         * @param {Hash Table} htData
         * @return {Hash Table}
         */
        function _getSortedList(htData){
            var sType;
            var htListByType = {};
            var htResult = {};
            
            // 먼저 타입별로 정리하고
            for(var sFileName in htData){
                htFileInfo = htData[sFileName];
                htFileInfo.fileName = sFileName;
                sType = htFileInfo.type;
                
                htListByType[sType] = htListByType[sType] || [];
                htListByType[sType].push(sFileName);
            }
            
            // 타입 내에서 알파벳 순으로 정렬
            for(var sType in htListByType){
                htResult[sType] = [];
                htListByType[sType].sort();
                htListByType[sType].forEach(function(sFileName){
                    htResult[sType].push(htData[sFileName]);
                });
            }

            return htResult;
        }
        
        /**
         * 템플릿 처리를 위한 파일 정보 가공
         * _getListHTML 에서 호출한다
         * 
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
         * 경로 끝에 파일명을 바르게 붙여서 반환해준다
         * - path//filename 이 아니라 path/filename 이 되도록
         * 
         * @param {String} sPath
         * @param {String} sFileName
         * @return {String}
         */
        function _getCorrectedPath(sPath, sFileName){
            return sPath + (sPath.substr(-1) === "/" ? "" : "/") + sFileName;
        }
        
        /**
         * 현재 경로와 일치하는 항목을 강조 표시한다
         * 
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
         * 지정한 depth 를 표현하는 indent 스타일 규칙을 추가한다
         * 
         * @param {Number} nDepth
         */
        function _setIndentByDepth(nDepth){
            nDepth = parseInt(nDepth, 10);
            htVar.aAddedDepth = htVar.aAddedDepth || [];
            
            // 중복 방지용
            if(htVar.aAddedDepth.indexOf(nDepth) === -1){
                htVar.aAddedDepth.push(nDepth);
                htVar.elStyle.addRule('.depth-' + nDepth + ' .filename', 'padding-left:' + (20 * nDepth) + 'px');
            }
        }
        
        /**
         * 파일 보기 화면일 경우
         * _init 에서 호출된다
         */
        function _initShowFile(){
            if(htElement.welShowCode.length > 0){
                _initCodeView(); // 코드보기
            } else if(htElement.welShowFile.length > 0){
                _beautifyFileSize(); // 파일정보
            }
        }

        /**
         * 파일 보기 화면일 때 파일 크기를 읽기 좋게 변환해준다
         * 
         * @require humanize.js 
         */
        function _beautifyFileSize(){
            htElement.welShowfile.find(".filesize").each(function(i, el){
                var welTarget = $(el);
                welTarget.html(humanize.filesize(welTarget.text()));
            });
        }

        /**
         * 코드 보기 화면일 때 aceEditor 를 이용해 표시한다
         *
         * @param {String} sCode
         * @param {String} sMode
         */
        function _initCodeView(){
            if(!htVar.oEditor){
                htVar.oEditor = _getEditor("showCode");
            }

            // Use explicit MIME Type if the server told which language is used to write the source code.
            // or the client should guess it.
            var sMimeType = htElement.welShowCode.data("mimetype");
            var aMatch = sMimeType.match(htVar.rxSub);
            var sMode = aMatch ? aMatch[1] : _getEditorModeByPath(htVar.sPath);

            htVar.oSession.setMode("ace/mode/" + sMode);
            htVar.oSession.setValue(htElement.welCodeVal.text());
            setTimeout(_resizeEditor, 50);
        }

        /**
         * aceEditor 객체 초기화해서 반환
         * 에디터 초기화 할 때 한번만 사용
         * 
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

        /**
         * 파일명을 기준으로 사용가능한 aceEditor mode 반환
         * @param {String} sPath
         * @return {String}
         */
        function _getEditorModeByPath(sPath){
            var sExt = getExt(basename(sPath));
            return ext2mode(sExt);
        }
        
        /**
         * 확장자를 aceEditor mode 로 변환
         * 
         * @param {String} sExt
         * @return {Variant} 지원 가능한 형식이면 해당 모드의 문자열(String), 그 이외에는 false(Boolean)
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
         * 경로명에서 파일명만 추출해서 반환
         * @param {String} sPath
         * @return {String}
         */
        function basename(sPath){
            return sPath.split("/").pop();
        }
        
        /**
         * 파일명에서 확장자만 추출해서 반환
         * 
         * @param {String} sFilename
         * @return {String}
         */
        function getExt(sFilename){
            return htVar.rxScala.test(sFilename) ? "scala" : sFilename.split(".").pop();
        }
        
        /**
         * aceEditor 높이를 내용에 맞게 키우는 함수
         */
        function _resizeEditor(){
            var nLineHeight = htVar.oEditor.renderer.lineHeight;
            nLineHeight = (nLineHeight === 1) ? (htVar.nFontSize + 4) : nLineHeight;

            var newHeight = (htVar.oSession.getScreenLength() * nLineHeight) + htVar.oEditor.renderer.scrollBar.getWidth();
            htElement.welShowCode.height(newHeight);
            htVar.oEditor.resize();
        }
        
        /**
         * Spinner 시작
         */
        function _startSpinner(){
            htVar.oSpinner = htVar.oSpinner || new Spinner(htVar.htOptSpinner);
            htVar.oSpinner.spin(htElement.elSpinTarget);
        }
        
        /**
         * Spinner 종료
         */
        function _stopSpinner(){
            if(htVar.oSpinner){
                htVar.oSpinner.stop();
            }
            htVar.oSpinner = null;
        }

        /**
         * 현재 파일/폴더 경로 표시
         * 
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

            htElement.welBreadCrumbs.html(aCrumbs.join(""));
        }
        
        _init(htOptions || {});
    };
})("yobi.code.Browser");