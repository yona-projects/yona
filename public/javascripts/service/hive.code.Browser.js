/**
 * @(#)hive.code.Browser.js 2013.03.19
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
		 * initialize
		 */
		function _init(htOptions){
			_initVar(htOptions);
			_initElement(htOptions);
			_attachEvent();
	
			// set path
			if (!htElement.welSelectedBranch.text()) {
				htElement.welSelectedBranch.text('HEAD');
			}
			$(window).trigger("hashchange");
			
			// initialize folder tree
			_initFinder();
			
			// initialize copy URL
			_initCopyURL();
		}
		
		/**
		 * initialize variables
		 */
		function _initVar(htOptions){
			htVar.rxHash = /^#/;
			htVar.rxTmp = /\!\//;
			htVar.sProjectName = htOptions.sProjectName;
			htVar.sTplFileListItem = $("#tplFileListItem").html() || "";
		}
		
		/**
		 * initialize element
		 */
		function _initElement(htOptions){
			// copy repository URL
			htElement.welInputRepoURL = $("#repositoryURL");
			htElement.welBtnCopyURL = $("#copyURL");
			
			// folder list (dynaTree)
			htElement.welFolderList = $("#folderList");
			
			// file list
			htElement.welContainerList = $("#fileList");
			htElement.welFileList = htElement.welContainerList.find("tbody");
			
			// file view
			htElement.welContainerView = $("#fileView");
			htElement.welFileView = htElement.welContainerView.find(".code-wrap");
			
			// actions
			htElement.aBranches = $(".branch-item"); // branches
			htElement.welBtnRawCode = $("#rawCode"); // get raw file
			htElement.welSelectedBranch = $("#selected-branch");
			
			// file info
			htElement.welFileInfo = $("#fileInfo");
			htElement.welFileCommiter   = htElement.welFileInfo.find(".commiter");
			htElement.welFileCommitMsg  = htElement.welFileInfo.find(".commitMsg");
			htElement.welFileCommitDate = htElement.welFileInfo.find(".commitDate");
			htElement.welFileRevision   = htElement.welFileInfo.find(".revision");
			
			htElement.welPath = $("#breadcrumbs");
		}
		
		/**
		 * attach event handler
		 */
		function _attachEvent(){
			$(window).bind("hashchange", _onHashChange);
			htElement.aBranches.click(_onSelectBranch);
		}
		
		/**
		 * on select branch item
		 */
		function _onSelectBranch(){
			htElement.welSelectedBranch.text($(this).text());
			$(window).trigger("hashchange");
		}
		
		/**
		 * on hash change
		 */
		function _onHashChange(){
			var sPath = getHash(true);//.replace(htVar.rxTmp, "!#/");
	        var sBranch = window.encodeURIComponent(htElement.welSelectedBranch.text());
	        var sURL = "code/" + sBranch + "/!" + sPath;
	        sURL = sURL.replace(/\!\!/, '!#');
	        console.log("hashchange", sURL);
	        
	        $.ajax(sURL, {
				"datatype": "json",
				"success" : _onLoadListCode,
				"error"   : _onErrorListCode
			});
		}
		
		/**
		 * on load file list or file content
		 * callback function of _onHashChange
		 */
		function _onLoadListCode(oRes){
			console.log("onload");
			_updateFileInfo(oRes); 
	
			switch(oRes.type){
				case "folder":
					_viewFolder(oRes);
					break;
				case "file":
					_viewFile(oRes);
					break;
			}
		}
		
		/**
		 * update file info
		 * 
		 * @param {Hash Table} htData
		 * @param {String} htData.author
		 * @param {String} htData.date
		 * @param {String} htData.msg
		 * @param {String} htData.reivisionNo
		 */
		function _updateFileInfo(htData){
			htElement.welFileCommiter.text(htData.commiter);
			htElement.welFileCommitDate.text(getDateAgo(htData.commitDate));
			htElement.welFileCommitMsg.text(getEllipsis(htData.commitMessage || "", 70));
			htElement.welFileRevision.text("Revision #: " + (htData.revisionNo || "Unknown"));
			
			updateBreadcrumbs();
		}
		
		/**
		 * render folder list
		 */
		function _viewFolder(htData){
			var sKey, htTmp;
			var aTplData = [];
			
			// 템플릿용 데이터로 가공해서 배열에 추가
			var sPath = (getHash(true) || "");
			var aData = _sortFolderList(htData.data);
			
			aData.forEach(function(htTmp){
				htTmp.name = htTmp.title;
				htTmp.dateAgo  = getDateAgo(htTmp.date); 
				htTmp.message  = getEllipsis(htTmp.message, 70);
				htTmp.filePath = "#" + (sPath !== "/" ? sPath : "") + "/" + htTmp.name;
				aTplData.push(htTmp);				
			});

			// 배열을 이용해 한꺼번에 템플릿 변환.
			var waList = $.tmpl(htVar.sTplFileListItem, aTplData);
			
			try {
				htElement.welFileList.children().remove(); // clear list
				htElement.welFileList.append(waList);
				htElement.welContainerList.show();
				htElement.welContainerView.hide();
			} finally {
				aTplData = waList = htTmp = null;
			}
		}
		
		/**
		 * render file view
		 */
		function _viewFile(htData){
			var sPath = getHash().replace(htVar.rxHash, "");
			htElement.welBtnRawCode.attr("href", "rawcode" + sPath);
	
			htElement.welFileView.text(htData.data || "");
			
			htElement.welContainerList.hide();
			htElement.welContainerView.show();
			htElement.welFileView.highlight();
		}
	
		/**
		 * update bread crumbs
		 * 파일 경로 업데이트
		 */
		function updateBreadcrumbs() {
			var aResult = ['<a href="#/">' + htVar.sProjectName + '</a>'];
			var sPath = getHash().replace(htVar.rxHash, "");
			var sLink = "#";
	
			sPath.split("/").forEach(function(sDir){
				if(sDir.length > 0){
					sLink += ("/" + sDir);
					aResult.push('<a href="#/' + sLink + '">' + sDir + '</a>');
				}
			});
					
			htElement.welPath.html(aResult.join("/"));
		}
		
		/**
		 * on error on file list or file content
		 * callback function of _onHashChange
		 */
		function _onErrorListCode(){
	//		$("#codeError").show();
		}
		
		/**
		 * Get relative date string from now depends on feature of moment.js
		 * 
		 * @requires moment.js
		 * @param {String} sDate
		 * @return {String}
		 */
		function getDateAgo(sDate){
			return moment(new Date(sDate)).fromNow();
		}
		
		/**
		 * get String with Ellipsis
		 * 
		 * @param {String} sStr
		 * @param {Number} nLength
		 * @return {String} 
		 */
		function getEllipsis(sStr, nLength){
			if(sStr && (sStr.length > nLength)){
				sStr = sStr.substr(0, nLength) + "&hellip;"; // &hellip; = ...
			}
			return sStr;
		}
		
		/**
		 * getHash
		 * 혹시나 document.location.hash 이외의 접근법이 나올까봐?
		 */
		function getHash(bTrim){
			var sHash = document.location.hash;
			return bTrim ? sHash.replace(htVar.rxHash, "") : sHash;
		}
	
		/**
		 * setHash
		 * 혹시나 document.location.hash 이외의 접근법이 나올까봐?
		 */
		function setHash(hash){
			return document.location.hash = hash;
		}
	
		/**
		 * getBranch
		 */
		function getBranch(){
			return window.encodeURIComponent(htElement.welSelectedBranch.text());
		}
		
		/*
		function standizePath(path) {
			var sPath = "/" + path.split("/").filter(function(data){
				return !(data == "");
			}).join("/");
			
			return sPath;
		}
		*/
	
		/**
		 * initialize Finder
		 * 폴더 목록 영역 초기화 함수
		 */
		function _initFinder(){
		    var sPath = getHash(true);
		    var sBranch = getBranch();
		    var sRootPath = "code/" + sBranch + "/!/";
			    
		    $.ajax({
		        "url": sRootPath,
		        "success": function(oRes){
		        	_initDynaTree(_sortFolderList(oRes.data));
		            _findTreeNode(sPath.substr(1));
		        }
		    });
		}
		
		/**
		 * initialize DynaTree
		 * see: http://wwwendt.de/tech/dynatree/doc/dynatree-doc.html
		 * @requires jquery.dynatree
		 */
		function _initDynaTree(oData){
			var sBranch = getBranch();
		    var sRootPath = "code/" + sBranch + "/!/";	    
			var htData = {
		        "title"     : "/",
		        "isLazy"    : true,
		        "autoFocus" : false,
		        "onLazyRead": _onLazyReadDynaTree,
		        "onActivate": _onActivateDynaTree,
		        "children"  : oData,
		        "debugLevel": 0,
		        "fx": {
		        	"height": "toggle", 
		        	"duration": 200
		        }	        
		    };
			
			htElement.welFolderList.dynatree(htData);
		}
		
		/**
		 * A DynaTreeNode object is passed to the activation handler
		 * Note: we also get this event, if persistence is on, and the page is reloaded.
		 * @param {Object} oNode 
		 */
		function _onActivateDynaTree(oNode){
			window.location = "#/" + _getTreePath(oNode);
		}
	
	    /**
	     * Called after nodes have been created and the waiting icon was removed.
	     * 'this' is the options for this AJAX request
	     * @param {Object} oNode 
	     */
		function _onLazyReadDynaTree(oNode){
			var sBranch = getBranch();
		    var sRootPath = "code/" + sBranch + "/!/";
		    
			$.ajax({
	            "url": sRootPath + _getTreePath(oNode),
	            "success": function(oRes) {
	                // Server returned an error condition: set node status accordingly
	                if(!oRes){
	                	oNode.setLazyNodeStatus(DTNodeStatus_Error, {
	                        "info"   : oRes,
	                    	"tooltip": "Loading failed"
	                    });
	                    return;
	                }
	
	                oNode.setLazyNodeStatus(DTNodeStatus_Ok);
	                oNode.addChild(_sortFolderList(oRes.data));
	            },
	            "error": function(oRes) {
	                // Called on error, after error icon was created.
	                console.log(oRes);
	            },
	            "cache": false // Append random '_' argument to URL to prevent caching.
	        });		
		}
		
		/**
		 * Sort Folder list 
		 * @param  {Array} aData
		 * @return {Array}
		 */
		function _sortFolderList(oData){
			var aTmp = [];
			var htData;
			
			for(sKey in oData){
				htData = oData[sKey];
				htData.title = sKey;

				if(htData.type === "folder"){
					htData.isLazy   = true;
					htData.isFolder = true;
				}
				
				aTmp.push(htData);
			}
			
			// sort folder first
			/**/
			var aResult = _sortBy(aTmp, function(htData){
				return -1 * htData.hasOwnProperty("isFolder");
			});/*/
			var aResult = aTmp;
			*/
			// free memory after return
			try {
				return aResult;
			} finally {
				aTmp = htTmp = null;
			}
		}
		
	
		/**
		 * Sort the object's values by a criterion produced by an iterator.
		 * underscore.js 의 코드를 참조하여 재작성
		 * @param {Array} aData
		 * @param {Function} fIterator
		 * @param {Object} oContext (optional)
		 */
		function _sortBy(aData, fIterator, oContext) {
			var aTmp;
			
			// Hash Table 배열로 변환 (nCriteria 추가) 
			aTmp = aData.map(function(vValue, nIndex, aList) {
				return {
					"vValue"	: vValue,
					"nIndex"	: nIndex,
					"nCriteria"	: fIterator.call(oContext, vValue, nIndex, aList)
				};
			});
			
			// 정렬하고나서
			aTmp = aTmp.sort(function(oLeft, oRight) {
				var nCrLeft = oLeft.nCriteria;
				var nCrRight = oRight.nCriteria;
				
				if (nCrLeft !== nCrRight) {
					if (nCrLeft > nCrRight || nCrLeft === void 0) {
						return 1;
					}
					if (nCrLeft < nCrRight || nCrRight === void 0) {
						return -1;
					}
				}
				return (oLeft.index < oRight.index) ? -1 : 1;
			});
	
			// 처음 들어왔던 형식의 데이터만 남김
			aTmp = aTmp.map(function(htData) {
				return htData.vValue;
			});
			
			return aTmp;
		}
		
		/**
		 * Traverse the path of selected tree item
		 * 
		 * @param {Object} oNode
		 */
		function _getTreePath(oNode){
		    var sPath = "";
		    
		    if( oNode.getParent() && oNode.getParent().data.title !== null ){
		        sPath = _getTreePath(oNode.getParent()) + "/" + oNode.data.title;
		    } else {
		        sPath = oNode.data.title;
		    }
		    
		    return sPath;
		}
	
		/**
		 * find TreeNode by path string
		 * 
		 * @param {String} sPath
		 */
		function _findTreeNode(sPath){
		    var oRoot = htElement.welFolderList.dynatree("getRoot");
		    var oNodeCurrent = oRoot;	  // currentNode
		    var aPath = sPath.split("/"); // nodes
		    var aNode;					  // searchTarget
		    var oNode;
		    
		    aPath.forEach(function(sName){
		    	aNode = oNodeCurrent.getChildren();
	
		    	if(aNode){
			    	for(var i=0, n=aNode.length; i < n; i++){
			    		oNode = aNode[i];
			    		
			    		if(oNode.data.title === sName){
			    			oNodeCurrent = oNode;
			    			oNodeCurrent.expand();
			    			break;
			    		}
			    	}
		    	}
		    });
		}
	
		/**
		 * Copy repository URL to clipBoard
		 * 
		 * @require ZeroClipboard
		 */
		function _initCopyURL(){
			htElement.welBtnCopyURL.zclip({
				"path": "/assets/javascripts/lib/jquery/ZeroClipboard.swf",
				"copy": function(){
					return htElement.welInputRepoURL.val();
				}
			});
		}
		
		_init(htOptions || {});
	};

})("hive.CodeBrowser");