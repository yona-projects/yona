/**
 * @(#)hive.code.Browser1.js 2013.04.09
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 *
 * http://hive.dev.naver.com/license
 */

(function(ns){

	var oNS = $hive.createNamespace(ns);
	oNS.container[oNS.name] = function(htOptions){

	    var htVar = {};
        var htElement = {};

		/**
		 * 코드브라우저 초기화
		 * @param {Hash Table} htOptions
		 */
		function _init(htOptions){
		    _initVar(htOptions);
		    _initElement();
            _attachEvent();
		    
            // initialize branches
            if (htVar.oBranch.getValue() === "") {
                htVar.oBranch.selectByValue("HEAD");
            }
            htVar.sRootPath = "code/" + getBranch() + "/!/"; // dynaTree

	        $(window).trigger('hashchange');
            //_initDynaTree();
		}

		/**
		 * 변수 초기화
		 * initialize variables
		 * @param {Hash Table} htOptions
		 */
		function _initVar(htOptions){
		    htVar.sProjectName = htOptions.sProjectName || "";
		    htVar.sRootPath = "";
            htVar.htExtMap = null;
	        htVar.oEditor = null;
	        htVar.oSession = null;        
	        htVar.oBranch = new hive.ui.Dropdown({
	            "elContainer" : $("#branches")
	        });
	        
	        htVar.rxImg = /\.(jpg|png|gif|tif|bmp|ico|jpeg)$/i;
	        htVar.rxGetHash = /^#/;
	        
	        htVar.bInitTree = false;
	        htVar.nFontSize = 13;
	        
            htVar.sTplListItem = '<tr>\
                <td><a href="${path}" class="${fileClass}" title="${name}"><span class="dynatree-icon vmiddle"></span>${name}</a></td>\
                <td class="messages"><span>${msg}</span></td>\
                <td class="date">${date}</td>\
                <td class="author">${avatar}</td>\
                </tr>';
                
            htVar.htOptSpinner = {
                lines: 10,    // The number of lines to draw
                length: 8,    // The length of each line
                width: 4,     // The line thickness
                radius: 8,    // The radius of the inner circle
                corners: 1,   // Corner roundness (0..1)
                rotate: 0,    // The rotation offset
                direction: 1, // 1: clockwise, -1: counterclockwise
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
        function _initElement(){
            htElement.welDynaTree = $("#folderList");
            htElement.welCodeTree = $(".code-tree");
            htElement.welCodeViewer = $('.code-viewer');
            htElement.welCodeViewerWrap = $(".code-viewer-wrap");
            htElement.welBtnResize = $(".btnResize");
            
            htElement.welBreadCrumbs = $("#breadcrumbs");
            htElement.welCommiter = $("#commiter");
            htElement.welCommitMsg = $("#commitMessage");
            htElement.welCommitRev = $("#revisionNo");
            htElement.welCommitDate = $("#commitDate");
            
            htElement.welShowImage = $("#showImage");
            htElement.welShowCode  = $("#showCode");
            htElement.welShowFile  = $("#showFile");
            
            htElement.welShowImageSrc = htElement.welShowImage.find("img");
            htElement.welShowFileName = htElement.welShowFile.find(".filename");
            htElement.welShowFileSize = htElement.welShowFile.find(".filesize");
            htElement.welShowFileHref = htElement.welShowFile.find(".filehref");
            
            htElement.welFileList = $("#fileList");
            htElement.welFileView = $("#fileView");
            htElement.welFileListContent = htElement.welFileList.find(".contents");
            htElement.welBtnRawCode = $("#rawCode");
            htElement.welBtnFullScreen = $("#fullScreen");
            htElement.welBtnFullScreenIcon = htElement.welBtnFullScreen.find("i");
            
            htElement.elSpinTarget = document.getElementById('spin');
        }

		/**
		 * 이벤트 초기화
		 */
		function _attachEvent(){
            $(window).on('hashchange', _onHashChange);
            
            htVar.oBranch.onChange(function(){
                _updateDynaTree();
                $(window).trigger('hashchange');
            });

            // related with resizeList           
            $(window).on("resize", _onResizeWindow);
            $(window).click(function(){ // for IE
                $(window).off("mousemove", _resizeList);
            });

            htElement.welBtnResize.on('drag',function(weEvt){
                _resizeList(weEvt);
            });
            // -- end of resizeList -- //
            
            htElement.welBtnFullScreen.click(_onClickBtnFullScreen);
            hive.ShortcutKey.attach("ALT+ENTER", _onClickBtnFullScreen);
		}

		/**
		 * hashChange 이벤트 핸들러
		 * 파일 목록 또는 내용을 서버에 요청한다
		 * @param {Event} e
		 */
		function _onHashChange(e){
            var sPath = getHash().replace(htVar.rxGetHash, "");
            var sBranch = getBranch();

            if(sPath != ""){
                _initDynaTree();
                htVar.bInitTree = true;
            }

            $.ajax("code/" + sBranch + "/!" + sPath, {
                "datatype": "json",
                "success" : _onLoadFiles,
                "error"   : function(){
                    $hive.showAlert("Server Error. Try again later");
                }
            });
		}
		
		/**
		 * Spinner 시작
		 */
		function _startSpinner(){
            htVar.oSpinner = new Spinner(htVar.htOptSpinner)
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
         * dynaTree 초기화
         */
        function _initDynaTree(){
            $.ajax({
                "url": htVar.sRootPath,
                "success": function(result, textStatus){
                    treeInit(adaptorForDynatree(result.data));
                    findTreeNode(getHash(true).substr(1));  // path.substr(1) "/a/b/c" => "a/b/c"
                    _stopSpinner();
                }
            });
        }

        /**
         * 파일 목록을 불러온 뒤 이벤트 핸들러
         * _onHashChange 에서 호출한 AJAX 성공시 실행된다
         * @param {Object} oData
         */
		function _onLoadFiles(oData){
            _stopSpinner();
            
		    // dynaTree 초기화. 최초에 한 번만 호출됨
		    if(htVar.bInitTree === false){
		        treeInit(adaptorForDynatree(oData.data));
		        findTreeNode(getHash(true).substr(1));  // path.substr(1) "/a/b/c" => "a/b/c"
		        htVar.bInitTree = true;
		    }
		    
		    var sPath = getHash().replace(htVar.rxGetHash, "");

            switch(oData.type){
                case "file" :
                    handleFile(sPath, oData);
                    break;
                case "folder" :
                    handleFolder(sPath, oData);
                    break;
            }

            _updateBreadcrumbs(sPath);
            _updateBtnResizeHeight();
		}
    
		/**
		 * btnResize 높이 조절
		 */
		function _updateBtnResizeHeight(){
            var nHeightBtnResize = Math.max(htElement.welCodeTree.height(), htElement.welCodeViewer.height());
            htElement.welBtnResize.height(nHeightBtnResize);		    
		}
		
        /**
         * 파일 내용 표시
         * _onHashChange 에서 호출
         * @param {String} sPath
         * @param {Hash Table} htData
         */
        function handleFile(sPath, htData){
            // 커밋 정보 업데이트
            htElement.welCommiter.text(htData.author || '');
            htElement.welCommitMsg.text(htData.msg || '');
            htElement.welCommitRev.text((htData.revisionNo && htData.revisionNo != '') ? "Revision#: " + htData.revisionNo : '');
            htElement.welCommitDate.text(moment(new Date(htData.createdDate)).fromNow());
            
            // 파일 종류에 따라 구분
            if(_isImageFile(sPath)){
                _showImage(sPath);
            } else if(_getEditorModeByPath(sPath)){
                _showCode(htData.data, _getEditorModeByPath(sPath));
            } else {
                _showFile(sPath, htData);
            }

            htElement.welFileList.hide();
            htElement.welFileView.show();
            htElement.welBtnRawCode.attr("href", "rawcode"+ sPath);
        }

        /**
         * 지정한 경로의 이미지 표시
         * handleFile 에서 호출한다
         * @param {String} sPath
         */
        function _showImage(sPath){
            htElement.welShowImage.show();
            htElement.welShowCode.hide();
            htElement.welShowFile.hide();

            htElement.welBtnRawCode.show();
            htElement.welBtnFullScreen.show();
            
            htElement.welShowImageSrc.attr("src", "./image" + sPath);
        }
        
        /**
         * 코드를 에디터를 이용해 표시한다
         * handleFile 에서 호출
         * @param {String} sCode
         * @param {String} sMode
         */
        function _showCode(sCode, sMode){
            htElement.welShowCode.show();
            htElement.welShowImage.hide();
            htElement.welShowFile.hide();

            if(!htVar.oEditor){
                htVar.oEditor = _getEditor("showCode");
            }

            htElement.welBtnRawCode.show();
            htElement.welBtnFullScreen.show();

            htVar.oSession.setMode("ace/mode/" + sMode);
            htVar.oEditor.setValue(sCode, -1);
            
            setTimeout(_resizeEditor, 50);
        }
        
        /**
         * 파일 정보를 표시한다
         * handleFile 에서 호출
         * @param {String} sPath
         * @param {Hash Table} htData
         */
        function _showFile(sPath, htData){
            htElement.welShowFile.show();
            htElement.welShowCode.hide();
            htElement.welShowImage.hide();

            htElement.welBtnRawCode.hide();
            htElement.welBtnFullScreen.hide();

            htElement.welShowFileSize.html(humanize.filesize(htData.data.length));
            htElement.welShowFileName.html(basename(sPath));
            htElement.welShowFileHref.attr("href", "rawcode" + sPath);
        }
        
        /**
         * aceEditor 높이를 내용에 맞게 키우는 함수
         * handleFile 에서 파일 내용이 바뀔 때 마다 호출함
         */
        function _resizeEditor(){
            var nLineHeight = htVar.oEditor.renderer.lineHeight;
            nLineHeight = (nLineHeight === 1) ? (htVar.nFontSize + 4) : nLineHeight;

            var newHeight = (htVar.oSession.getScreenLength() * nLineHeight) + htVar.oEditor.renderer.scrollBar.getWidth();
            htElement.welShowCode.height(newHeight);
            htVar.oEditor.resize();
        }
      
        /**
         * 대소문자 구분없이 정의된 확장자명으로 끝나는지를 검사하여 반환
         * @param {String} sPathName
         * @return {Boolean}
         */
        function _isImageFile(sPathName){
            return htVar.rxImg.test(sPathName);
        }

        /**
         * aceEditor 객체 초기화해서 반환
         * 에디터 초기화 할 때 한번만 사용
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
                "javascript": ["js"],
                "json": ["json"],
                "jsp": ["jsp"],
                "latex": ["dtx", "tex"],
                "less": ["less"],
                "makefile": ["mk", "emakrfile", "emakerfile"],
                "markdown": ["md"],
                "php": ["php","php3","php4","php5","php6","phps","inc"],
                "python": ["py"],
                "ruby": ["rb", "ruby"],
                "sh": ["sh"],
                "svg": ["svg"],
                "scala": ["scala"],
                "sql": ["sql"],
                "text": ["txt"],
                "vbscript": ["vbs"],
                "xml": ["xml"],
                "yaml": ["yaml"] 
            };
            
            for(var sMode in htVar.htExtMap){
                if(htVar.htExtMap[sMode].indexOf(sExt) > -1){
                    return sMode;
                }
            }
            
            return false;
        }
        
        /**
         * 폴더 내용 표시
         * _onHashChange 에서 호출
         * @param {String} sPath
         * @param {Hash Table} htData
         */
        function handleFolder(sPath, htData){
            htData.data = sortData(htData.data);

            // 폴더 목록 업데이트
            var sHTML = _getFileListHTML(sPath, htData);
            htElement.welFileListContent.html(sHTML);

            // 완료 후 목록 보여주기
            htElement.welFileList.show();
            htElement.welFileView.hide();
            _updateBtnResizeHeight();
        }

        /**
         * 폴더 내의 파일 목록 HTML을 생성하는 함수
         * handleFolder 에서 호출한다
         * @param {String} sPath
         * @param {Hash Table} htData
         * @return {String}
         */
        function _getFileListHTML(sPath, htData){
            var aResult = [];

            // 하위 폴더인 경우 상위 경로로의 이동링크 표시
            if(sPath.length > 1) {
                var aPath = sPath.split("/");
                    aPath.pop();
                var sPathUpper = aPath.join("/");

                aResult.push(_getFileListItem({
                   "name": "..",
                   "type": "none",
                   "path": "#" + ((sPathUpper === "/") ? '' : sPathUpper)
                }));
            }

            // 파일 목록 생성
            var htInfo = {};
            var sHTML = "";

            for(var sName in htData.data){
                htInfo = htData.data[sName];
                htInfo.path = "#" + (sPath !== "/" ? sPath : "") + "/" + sName;
                sHTML = _getFileListItem(htInfo);
                aResult.push(sHTML);
            }
            
            return aResult.join("\n");
        }
        
        /**
         * 파일 목록에 추가할 HTML 문자열을 반환하는 함수
         * @param {Hash Table} htData 파일 정보 객체 
         * @param {String} htData.name 파일 이름
         * @param {String} htData.path 파일 경로
         * @param {String} htData.type 파일 종류
         * @param {String} htData.msg  커밋 메시지
         * @param {String} htData.date 커밋 날짜
         * @param {String} htData.author 작성자 이름
         * @param {String} htData.avatar 작성자 아바타 이미지 URL
         * @return {String}
         * @requires moment.js
         */
        function _getFileListItem(htData){
            htData.date = (typeof htData.createdDate !=='undefined') ? (moment(new Date(htData.createdDate)).fromNow()) : '';
            //htData.fileClass = (htData.name ==='..') ? 'filename updir' : 'filename';
            htData.fileClass = (htData.name ==='..') ? 'filename updir' : (htData.type === "folder" ? 'filename dynatree-ico-cf' : 'filename dynatree-ico-c');
            htData.avatar = (typeof htData.avatar !== 'undefined') ? '<a href="/'+ htData.author + '" class="avatar-wrap smaller"><img src="' + htData.avatar + '"></a>' : '';
            htData.msg = htData.msg || '';
            
            if(htData.msg.length && htData.msg.length > 70){
                htData.msg = htData.msg.substr(0, 70) + "...";
            }

            var sHTML = $hive.tmpl(htVar.sTplListItem, htData);
            return sHTML;
        }
     
        /**
         * 데이터 정렬
         * @param {Array} target
         * @requires underscore.js
         */
        function sortData(target){
            var rs = [];
            var htResult = {};
              
            _getObjectKeys(target).sort(lacending).forEach(function(key){
                target[key].name = key;
                rs.push(target[key]);
            });

            rs = _.sortBy(rs, function(elm){
                return -(elm.type === "folder");
            });

            rs.forEach(function(o){
                htResult[o.name] = o;
            });

            try {
                return htResult;
            } finally {
                rs = null;
            }
        }

        /**
         * 현재 파일/폴더 경로 표시
         * @param {String} sPath
         */
        function _updateBreadcrumbs(sPath){
            var sHTML;
            var sLink = "#";
            var aCrumbs = ['<a href="#/">'+htVar.sProjectName+'</a>'];
            var aPath = sPath.split("/");
            aPath.shift();
            
            aPath.forEach(function(sName){
                sLink += "/" + sName;
                aCrumbs.push('<a href="' + sLink + '">' + sName + '</a>');                
            });

            sHTML = aCrumbs.join("/");
            htElement.welBreadCrumbs.html(sHTML);            
        }
        
        /**
         * Hash 반환 함수
         * @return {String}
         */
    	function getHash() {
    		return document.location.hash;
    	}

        /**
         * Hash 설정 함수
         * @param {String} hash 
         */
    	function setHash(hash) {
    	    return document.location.hash = hash;
    	}

        /**
         * 현재 선택된 브랜치 반환
         * @return {String}
         */
    	function getBranch(){
    	    return encodeURIComponent(htVar.oBranch.getValue());
    	}

    	/**
    	 * 창 크기 자체가 변할 때 welCodeViewer 영역의 너비를 조절하는 함수
    	 * _attachEvent 에서 초기화 함
    	 */
    	function _onResizeWindow(){
            var nGap = (htElement.welCodeTree.width() > 0) ? htElement.welCodeTree.width() + 3 : 0;
            htElement.welCodeViewer.width(htElement.welCodeViewerWrap.width() - nGap);    	    
    	}
    	
    	/**
    	 * dynaTree 영역과 codeView 영역 사이의 크기를 조절하는 함수
    	 * welBtnResize.on("drag") 이벤트 발생할 때마다 호출되는 핸들러 
    	 */
        function _resizeList(weEvt){
            var htDirectoryOffset = htElement.welCodeTree.position();
            var nCodeViewerWidth = htElement.welCodeViewerWrap.width();
            var nCodeTreeWidth = (weEvt.clientX > htDirectoryOffset.left) ? Math.round(weEvt.clientX - htDirectoryOffset.left) : 0;        

            htElement.welCodeTree.width(nCodeTreeWidth);
            htElement.welCodeViewer.width(nCodeViewerWidth - nCodeTreeWidth -2);
        }

        /**
         * 코드 뷰어의 [전체화면] 버튼 클릭시 이벤트 핸들러
         */
        function _onClickBtnFullScreen(){
            _toggleCodeTree();
            htElement.welBtnFullScreenIcon.toggleClass("icon-resize-small");
            htElement.welBtnFullScreenIcon.toggleClass("icon-resize-full");
        }
        
        /**
         * 코드 트리 영역 토글.
         * 트리 영역의 width 를 0으로 만들거나 다시 복원
         */
        function _toggleCodeTree(){
            // 트리 영역을 감추고 파일 내용으로 가득 채움
            if(htElement.welCodeTree.width() > 0){
                htVar.nLastTreeWidth = htElement.welCodeTree.width();
                htElement.welCodeTree.width(0);
                htElement.welCodeViewer.width(htElement.welCodeViewerWrap.width());
            } else { // 트리 영역 크기를 복원
                htElement.welCodeTree.width(htVar.nLastTreeWidth || "20%");
                htElement.welCodeViewer.width(htElement.welCodeViewerWrap.width() - htElement.welCodeTree.width() - 2);
            }
        }
        
    	// adaptorForDynatree adaptor function is used for existed function
    	// Also, it pass the below tests
    	//
    	// it("file & folder combine", function() {
    	//     //Given
    	//     var target = {
    	//         "attachment": {
    	//             "type": "folder",
    	//             "msg": "add folders",
    	//             "author": "doortts",
    	//             "date": "Mon Jan 28 14:21:07 KST 2013"
    	//         },
    	//         "favicon.ico": {
    	//             "type": "file",
    	//             "msg": "add folders",
    	//             "author": "doortts",
    	//             "date": "Mon Jan 28 14:21:07 KST 2013"
    	//         }
    	//     };
    	//     var expected = [
    	//         {title: "attachment", isFolder: true, isLazy: true},
    	//         {title: "favicon.ico"}
    	//     ];
    	//     //When
    	//     var result = adaptorForDynatree(target);
    	//     //Then
    	//     assert.deepEqual(result, expected);
    	// });
    	function adaptorForDynatree(target){
    		var rs = [];
    		var ht = {};
    		var htResult = {};
            var value;
    		
    		_getObjectKeys(target).sort(lacending).forEach(function(key){
                ht[key]=target[key];
    		});

            for(var key in ht){
                value = ht[key];
                if(value.type === "folder") {
                    rs.push({"title": key, "isFolder": true, "isLazy": true});
                } else {
                    rs.push({"title": key});
                }
            }

    		return _.sortBy(rs, function(elm){
    		    return -elm.hasOwnProperty("isFolder");
    		});
    	}
    
    	function findTreeNode(path){
    	    var root = htElement.welDynaTree.dynatree("getRoot");
    	    var nodes = path.split("/");  // "a/b/c" => a, b, c
    	    var currentNode = root;
    	    var searchTarget;
    
            for(var idx in nodes){
                searchTarget = currentNode.getChildren();
                for(var jdx in  searchTarget){
                    if(searchTarget[jdx].data.title === nodes[idx]){
                        currentNode = searchTarget[jdx];
                        currentNode.expand();
                        break;
                    }
                }
            }
    	}
    
    	// Traverse the path of selected tree item
    	function getTreePath(node){
    	    var path = "";
    	    if( node.getParent() && node.getParent().data.title !== null ){
                path = getTreePath(node.getParent()) + "/" + node.data.title;
    	    } else {
                path = node.data.title;
    	    }
    	    return path;
    	}
    
    	// updateDynaTree
    	function _updateDynaTree(){
    	    var path = getHash(true);
    	    var branch = getBranch();
    	    
    	    htVar.sRootPath = "code/" + branch + "/!/";

    	    $.ajax({
    	        "url": htVar.sRootPath,
    	        "success": function(result){
    	            var oRoot = htElement.welDynaTree.dynatree("getRoot");

    	            if(!(oRoot instanceof jQuery)){
    	                oRoot.removeChildren(true);
    	                oRoot.addChild(adaptorForDynatree(result.data));
    	            }
    	        }
    	    });
    	}
    	// DynaTree Init function
    	// see: http://wwwendt.de/tech/dynatree/doc/dynatree-doc.html
    	function treeInit(initData){
    	    htElement.welDynaTree.dynatree({
    	      "debugLevel" : 0,
    	      "title"      : "/",
    	      "isLazy"     : true,
    	      "autoFocus"  : false,
              "children"   : initData,
    	      "onLazyRead" : _onLazyRead,
    	      "onActivate" : function(node) {
    	          // A DynaTreeNode object is passed to the activation handler
    	          // Note: we also get this event, if persistence is on, and the page is reloaded.
    	          window.location = "#/" + getTreePath(node);
    	      },
              "fx": {
                  "height": "toggle", 
                  "duration": 200    	      
              }
    		});
    	}

        /**
         * DynaTree lazyRead handler
         * @param {Object} node dynaTree node
         */
    	function _onLazyRead(node){
    	    $.ajax({
                "url" : htVar.sRootPath + getTreePath(node),
                "success" : function(result, textStatus) {
                    // Called after nodes have been created and the waiting icon
                    // was removed.
                    // 'this' is the options for this Ajax request
                    if (result) {
                        node.setLazyNodeStatus(DTNodeStatus_Ok);
                        node.addChild(adaptorForDynatree(result.data));
                    } else {
                        // Server returned an error condition: set node status
                        // accordingly
                        node.setLazyNodeStatus(DTNodeStatus_Error, {
                            tooltip : "Loading failed",
                            info : result
                        });
                    }
                },
                "error" : function(node, XMLHttpRequest, textStatus, errorThrown) {
                    // Called on error, after error icon was created.
                    console.log(node);
                },
                "cache" : false
                // Append random '_' argument to url to prevent caching.
            });
    	}
    	
    	// 정렬 함수
    	function lacending(a, b) {
    	    a = a.toLowerCase();
    	    b = b.toLowerCase();
    	    return (a < b) ? -1 : (a > b ? 1 : 0);
    	}
    
    	function ldescending(a, b) {
    	    a = a.toLowerCase();
    	    b = b.toLowerCase();
    	    return (b < a) ? -1 : (b > a ? 1 : 0);
    	}
    	
    	// for IE8 or less.
    	function _getObjectKeys(obj){
    	    if(obj.keys){
    	        return obj.keys;
    	    }
    
    	    var aKeyNames = [];
    	    for (var sKeyName in obj) {
    	        aKeyNames.push(sKeyName);
    	    }
    	    return aKeyNames;
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
         * @param {String} sFilename
         * @return {String}
         */
        function getExt(sFilename){
            if(sFilename.indexOf("scala.html") > -1){
                return "scala";
            }
            return sFilename.split(".").pop();
        }
        
    	// 초기화 실행
    	_init(htOptions);
	};
})("hive.code.Browser");
