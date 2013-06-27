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

		var project_name = htOptions.sProjectName;
		
		var oEditor, oSession;
        var oBranch = new hive.ui.Dropdown({
            "elContainer" : $("#branches")
        });
		var rxImg = /\.(jpg|png|gif|tif|bmp|ico|jpeg)$/i;
		var htExtMap = null;
		
        // initial path loading
        var rootPath = "";
        var treeSelectorId = "#folderList";
       
		/**
		 * 코드브라우저 초기화
		 */
		function _init(htOptions){
            if (oBranch.getValue() == "") {
                oBranch.selectByValue("HEAD");
            }

            $(window).bind('hashchange', _onHashChange);
	        $(window).trigger('hashchange');
	        //_updateDynaTree();
	        
	        oBranch.onChange(function(){
	            _updateDynaTree();
	            $(window).trigger('hashchange');
	        });
	        
	        _initResizeList();

	        // dynaTree
            rootPath = "code/" + getBranch() + "/!/";
            
            $.ajax({
                "url": rootPath,
                "success": function(result, textStatus){
                    treeInit(adaptorForDynatree(result.data));
                    findTreeNode(getHash(true).substr(1));  // path.substr(1) "/a/b/c" => "a/b/c"
                }
            });
		}

		/**
		 * hashChange 이벤트 핸들러
		 * @param {Event} e
		 */
		function _onHashChange(e){
            var path = getHash().replace(/^#/, "");
            var branch = getBranch();

            $.ajax("code/" + branch + "/!" + path, {
                "datatype" : "json",
                "success" : function(data){
                    updateBreadcrumbs(path);
                    
                    switch(data.type){
                      case "file" :
                          handleFile(path, data);
                          break;
                      case "folder" :
                          handleFolder(path, data);
                          break;
                    }
                    
                    var treeheight = $('.code-tree').height();
                    var codeheight = $('code-viewer').height();
                    var btnheight = (treeheight > codeheight) ? treeheight : codeheight;
                    
                    $(".btnResize").height(btnheight);
                },
                "error" : function(){
                    $("#codeError").show();
                }
            });
		}
		
    
        /**
         * 파일 내용 표시
         * _onHashChange 에서 호출
         * @param {String} path
         * @param {Hash Table} data
         */
        function handleFile(path, data){
            var author = data.author || '';
            var msg = data.msg || '';
            var revisionNo = data.revisionNo || '';  

            $("#commiter").text(author);
            $("#commitMessage").text(msg);
            $("#revisionNo").text((revisionNo != '') ? "Revision#: " + revisionNo : '');
            $("#commitDate").text(moment(new Date(data.createdDate)).fromNow());
            
            if(isImageFile(path)) {
                $("#showImage").html("<img src='./image" + path + "'>");
                $("#showImage").show();
                $("#showCode").hide();
            } else {
                $("#showImage").hide();
                $("#showCode").show();

                if(!oEditor){
                    oEditor = _getEditor("showCode");
                }
                oEditor.setValue(data.data, 0);
                oEditor.clearSelection();
                
                _setEditorMode(path);
                
                setTimeout(function(){
                    _resizeEditor();
                }, 0);
            }
        
            $("#rawCode").attr("href", "rawcode"+path);
            $("#fileList").hide();
            $("#fileView").show();
        }

        /**
         * 대소문자 구분없이 정의된 확장자명으로 끝나는지를 검사하여 반환
         * @param {String} pathName
         * @return {Boolean}
         */
        function isImageFile(pathName){
            return rxImg.test(pathName);
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
            oAce.setFontSize(13);
            oAce.setShowPrintMargin(false);
            
            return oAce;
        }

        /**
         * aceEditor 높이를 내용에 맞게 키우는 함수
         * handleFile 에서 파일 내용이 바뀔 때 마다 호출함
         */
        function _resizeEditor(){
            var nLineHeight = oEditor.renderer.lineHeight 
            var newHeight = (oSession.getScreenLength() * nLineHeight) + oEditor.renderer.scrollBar.getWidth();
            $("#showCode").height(newHeight);
            oEditor.resize();
        }
      
        /**
         * 파일명을 기준으로 에디터 모드를 결정해주는 함수
         * handleFile 에서 파일 내용이 바뀔 때 마다 호출함 
         * @param {String} sPath
         */
        function _setEditorMode(sPath){
            var sExt = getExt(basename(sPath));
            var sMode = ext2mode(sExt);
            
            oSession = oEditor.getSession();
            oSession.setMode("ace/mode/" + sMode);
        }
        
        /**
         * 확장자를 aceEditor mode 로 변환
         * @param {String} sExt
         */
        function ext2mode(sExt){
            sExt = sExt.toLowerCase();
            
            htExtMap = htExtMap || {
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
            
            for(var sMode in htExtMap){
                if(htExtMap[sMode].indexOf(sExt) > -1){
                    return sMode;
                }
            }
            
            return "txt";
        }
        
        /**
         * 폴더 내용 표시
         * _onHashChange 에서 호출
         * @param {String} path
         * @param {Hash Table} data
         */
        function handleFolder(path, data){
            data.data = sortData(data.data);
          
            var author = data.author || '';
            var msg = data.msg || '';
            var revisionNo = data.revisionNo || '';
            var aTmp = [];
            var info, tablerow, type, sFilePath;

            // 디렉토리 트리에서 발생한 이벤트를 파일 리스트에 반영하는 영역.
            $("#commiter").text(author);
            $("#commitMessage").text(msg);
            $("#revisionNo").text((revisionNo != '') ? "Revision#: " + revisionNo : '');
            $("#commitDate").text(data.date);
            $(".contents").children().remove();

            if(path.length > 1) {
                var pathArray = path.split('/');
                var upPath = pathArray.slice(0,pathArray.length-1).join("/");

                upPath = (upPath=='/') ? '' : upPath;
                sFilePath = "#" + upPath;
            
                tablerow = makeTableRow('..', sFilePath, 'none');
                aTmp.push(tablerow);
            }

            for(var name in data.data){
                sFilePath = "#" + (path !== "/" ? path : "") + "/" +name;
                info = data.data[name],
                type = data.data[name].type;
                tablerow = makeTableRow(name, sFilePath, type, info.msg, info.createdDate, info.author, info.avatar);
                aTmp.push(tablerow);
            }
            $(".contents").append(aTmp);
            aTmp = null;

            $("#fileList").show();
            $("#fileView").hide();
        }

        /**
         * 데이터 정렬
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
         * 파일 목록 생성
         */
        function makeTableRow(name, path, type, message, date, author, avatar){
            author = author || '';
            date = (typeof date !=='undefined') ? (moment(new Date(date)).fromNow()) : '';
            avatar = (typeof avatar !== 'undefined') ? '<a href="/'+ author+'" class="avatar-wrap"><img src="' + avatar + '"></a>' : '';
            message = message || '';
            fileClass= (name=='..') ? 'filename updir' : 'filename';

            if (message.length > 70 && typeof message.length === 'undefined'){
                message = message.substr(0, 70) + "...";
            }
            
            var welRow = $("<tr>")
                .append($('<td><a class="'+fileClass+'" href="' + path + '" title="' + name + '"><i class="ico ico-'+type+'"></i>' + name + '</a></td>'))
                .append($('<td class="messages"><span>' + message + '</span></td>'))
                .append($('<td class="date">' + date + '</td>'))
                .append($('<td class="author">'+avatar+'</td>'));

            try {
                return welRow;
            } finally {
                welRow = sFilePath = null;
            }
        }

        /**
         * 현재 위치 표시
         */
        function updateBreadcrumbs(path){
            var $breadcrumbs = $("#breadcrumbs");
            $($breadcrumbs).html('<a href="#/">'+project_name+'</a>');

            var names = path.split("/");
            var str = "#";
            var name;

            for(var i = 1, nLength = names.length; i < nLength; i++){
                name = names[i];
                str += "/" + name;
                $breadcrumbs.append("/");
                $("<a>").text(name).attr("href", str).appendTo($breadcrumbs);
            }
        }
        
    	function getHash() {
    		return document.location.hash;
    	}
    
    	function setHash(hash) {
    	    return document.location.hash = hash;
    	}
    
    	function getBranch(){
    	    return encodeURIComponent(oBranch.getValue());
    	}
    
    	/**
    	 * 목록 영역 크기 변경 초기화
    	 */
    	function _initResizeList(){
    		var nFolderListX = $("#folderList").offset().left;
    		var welBtnResize = $(".btnResize");
    		var welWrapDirectory = $(".directory-wrap");
    		var waWrapFile = $(".file-wrap"); // fileList, fileView
    
    	    var draggable = true;
            var welBrowseWrap = $(".code-browse-wrap");
    
    	    welBtnResize.on('drag',function(weEvt){
    	    	_resizeList(weEvt);
    	    });
    
    		$(window).click(function(){ // for IE
    			$(window).off("mousemove", _resizeList);
    		});
    
    		// 더블클릭하면 디렉토리 목록 숨김
    		welBtnResize.dblclick(function(){
    		    $(window).unbind("mousemove", _resizeList);
    			if(welWrapDirectory.css("display") == "none"){
    			    draggable = true;
    				welWrapDirectory.show();
    				waWrapFile.width(welBrowseWrap.width() - welWrapDirectory.width() - 20);
    			} else {
        	        draggable = false;
        	        $(window).off("mousemove", _resizeList);
    				welWrapDirectory.hide();
    				waWrapFile.width(welBrowseWrap.width() + 20);
    			}
    		});
    
    		function _resizeList(weEvt){
    			var htDirectoryOffset = $('.code-tree').position(),
                    nCodeViewerWidth = $('.code-viewer-wrap').width(),
                    nCodeTreeWidth = (weEvt.clientX > htDirectoryOffset.left) ? Math.round(weEvt.clientX - htDirectoryOffset.left) : 0;        

                $('.code-tree').width(nCodeTreeWidth);		
    			$('.code-viewer').width(nCodeViewerWidth - nCodeTreeWidth -2);
    			/*
    			var nWidth = weEvt.clientX - nFolderListX;
                $(".directory-wrap").width(nWidth - 10);
                $(".directories").width(nWidth - 10);
                $(".file-wrap").width(welBrowseWrap.width() - nWidth - 10);
                */
    		}
    		
    		$(window).on("resize", function(){
    		    var welTree = $(".code-tree");
    		    var welView = $('.code-viewer');
    		    var welWrap = $('.code-viewer-wrap');
    		    var nGap = (welTree.width() > 0) ? welTree.width() + 3 : 0;
    		    welView.width(welWrap.width() - nGap);
    		});
    	}
    	/** end of resize list **/
    
    
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
    		  			
    		_getObjectKeys(target).sort(lacending).forEach(function(key){
    		    ht[key]=target[key];
    		});
    
    		_.each(ht, function(value, key, list){
    		    if(value.type === "folder") {
    		        rs.push({ title: key, isFolder: true, isLazy: true});
    		    } else {
    		        rs.push({ title: key});
    		    }
    		});
    
    		return _.sortBy(rs, function(elm){
    		    return -elm.hasOwnProperty("isFolder");
    		});
    	}
    
    	function findTreeNode(path){
    	    var root = $("#folderList").dynatree("getRoot");
    	    var nodes = path.split("/");  // "a/b/c" => a, b, c
    	    var currentNode = root;
    	    var searchTarget;
    
    	    for(var idx in nodes){
    	      searchTarget = currentNode.getChildren();
    	      for( var jdx in  searchTarget){
    	        if ( searchTarget[jdx].data.title === nodes[idx] ) {
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
    	    
    	    rootPath = "code/" + branch + "/!/";
    
    	    $.ajax({
    	        "url": rootPath,
    	        "success": function(result){
    	            var oRoot = $(treeSelectorId).dynatree("getRoot");
    	            
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
    		$(treeSelectorId).dynatree({
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

    	function _onLazyRead(node){
    	    $.ajax({
                "url" : rootPath + getTreePath(node),
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
    	function ascending(a, b) {
    	    return (a < b) ? -1 : (a > b ? 1 : 0);
    	}
    
    	function descending(a, b) {
    	    return (b < a) ? -1 : (b > a ? 1 : 0);
    	}
    
    	function lacending(a, b) {
    	    a = a.toLowerCase();
    	    b = b.toLowerCase();
    	    return ascending(a, b);
    	}
    
    	function ldescending(a, b) {
    	    a = a.toLowerCase();
    	    b = b.toLowerCase();
    	    return descending(a, b);
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
