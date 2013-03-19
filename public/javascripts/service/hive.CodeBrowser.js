/**
 * @(#)hive.CodeBrowser.js 2013.03.19
 *
 * Copyright NHN Corporation.
 * Released under the MIT license
 * 
 * http://hive.dev.naver.com/license
 */

hive.CodeBrowser = function(htOptions){
	
	var htVar = {};
	var htElement = {};
	
	/**
	 * initialize
	 */
	function _init(htOptions){
		_initVar(htOptions);
		_initElement(htOptions);
		_attachEvent();

		if (!htElement.welSelectedBranch.text()) {
			htElement.welSelectedBranch.text('HEAD');
		}
		$(window).trigger("hashchange");
	}
	
	/**
	 * initialize variables
	 */
	function _initVar(htOptions){
		htVar.rxHash = /^#/;
		htVar.sProjectName = htOptions.sProjectName;
		htVar.sTplFileListItem = $("#tplFileListItem").text() || "";
	}
	
	/**
	 * initialize element
	 */
	function _initElement(htOptions){
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
		var sPath = getHash().replace(htVar.rxHash, "");
        var sBranch = window.encodeURIComponent(htElement.welSelectedBranch.text());
        var sURL = "code/" + sBranch + "/!" + sPath;
        
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
		htElement.welFileCommiter.text(htData.author);
		htElement.welFileCommitDate.text(htData.date);
		htElement.welFileCommitMsg.text(htData.msg || "");
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
		for(sKey in htData.data){
			htTmp = htData.data[sKey];
			htTmp.name = sKey;
			htTmp.dateAgo  = getDateAgo(htTmp.date); 
			htTmp.message  = getEllipsis(htTmp.message, 70);
			htTmp.filePath = "#" + (htTmp.path !== "/" ? htTmp.path : "") + "/" + htTmp.name;
			
			aTplData.push(htTmp);
		}
		
		// 배열을 이용해 한꺼번에 템플릿 변환.
		// 1. append 보다 innerHTML 사용이 빠르고
		// 2. DOM 건드리는 작업은 가능한 적게 해야
		var sHTML = $.tmpl(htVar.sTplFileListItem, aTplData).text();
		
		try {
			htElement.welContainerList.text(sHTML);
			htElement.welContainerList.show();
			htElement.welContainerView.hide();
		} finally {
			aTplData = sHTML = htTmp = null;
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
		return;
		
		var path = getHash().replace(htVar.rxHash, "")
		var $breadcrumbs = $("#breadcrumbs");
		$($breadcrumbs).html('<a href="#/">' + project_name + '</a>');

		var names = path.split("/");
		var str = "#"
		for ( var i = 1; i < names.length; i++) {
			var name = names[i];
			str += "/" + name;
			$breadcrumbs.append("/");
			$("<a>").text(name).attr("href", str).appendTo($breadcrumbs);
		}
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
		if(sStr.length > nLength){
			sStr = sStr.substr(0, nLength) + "&hellip;"; // &hellip; = ...
		}
		return sStr;
	}
	
	/**
	 * getHash
	 * 혹시나 document.location.hash 이외의 접근법이 나올까봐?
	 */
	function getHash(){
		return document.location.hash;
	}

	/**
	 * setHash
	 * 혹시나 document.location.hash 이외의 접근법이 나올까봐?
	 */
	function setHash(hash){
		return document.location.hash = hash;
	}

	/*
	function standizePath(path) {
		var sPath = "/" + path.split("/").filter(function(data){
			return !(data == "");
		}).join("/");
		
		return sPath;
	}
	*/
	
	_init(htOptions || {});
};


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

var result = [];
function adaptorForDynatree(target){
    result = [];
    _.each(target, function(value, key, list){
        if(value.type === "folder") {
            result.push({ title: key, isFolder: true, isLazy: true});
        } else {
            result.push({ title: key});
        }
    })
    return _.sortBy(result, function(elm){
        return -elm.hasOwnProperty("isFolder");
    })
}

function findTreeNode(path){
    var root = $("#folderNav").dynatree("getRoot");
    var nodes = path.split("/");  // "a/b/c" => a, b, c
    var currentNode = root;
    for(var idx in nodes){
        var searchTarget = currentNode.getChildren()
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

// initial path loading
var rootPath = "";
var treeSelectorId = "#folderNav";
$(function(){
    var path = document.location.hash.replace(/^#/, "");
    var branch = encodeURIComponent($("#selected-branch").text());
    rootPath = "code/" + branch + "/!/";
    $.ajax({
        url: rootPath,
        success: function(result, textStatus){
            treeInit(adaptorForDynatree(result.data));
            findTreeNode(path.substr(1));  // path.substr(1) "/a/b/c" => "a/b/c"
        }
    });
});

// DynaTree Init function
// see: http://wwwendt.de/tech/dynatree/doc/dynatree-doc.html

function treeInit(initData){
    $(treeSelectorId).dynatree({
        fx: {  height: "toggle", duration: 200 },
        autoFocus: false,
        isLazy: true,
        onLazyRead: function(node){

            $.ajax({
                url: rootPath + getTreePath(node),
                success: function(result, textStatus) {
                    // Called after nodes have been created and the waiting icon was removed.
                    // 'this' is the options for this Ajax request
                    if(result){
                        node.setLazyNodeStatus(DTNodeStatus_Ok);
                        node.addChild(adaptorForDynatree(result.data));
                    }else{
                        // Server returned an error condition: set node status accordingly
                        node.setLazyNodeStatus(DTNodeStatus_Error, {
                            tooltip: "Loading failed",
                            info: result
                        });
                    }
                },
                error: function(node, XMLHttpRequest, textStatus, errorThrown) {
                    // Called on error, after error icon was created.
                    console.log(node);
                },
                cache: false // Append random '_' argument to url to prevent caching.
            });
        },
        onActivate: function(node) {
            // A DynaTreeNode object is passed to the activation handler
            // Note: we also get this event, if persistence is on, and the page is reloaded.
            window.location = "#/" + getTreePath(node);
        },
        title: "/",
        children: initData,
        debugLevel: 0
    });
}

