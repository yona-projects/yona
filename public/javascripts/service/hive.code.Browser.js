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
	
		$(document).ready(function(){
			$(window).bind('hashchange', function(e){
//				_updateDynaTree();
				
		        //대기 표시 한다.
		        //여기서 요청을 보내고
		        var path = getHash().replace(/^#/, "");
		        var branch = encodeURIComponent($("#selected-branch").text());
		
		        $.ajax("code/" + branch + "/!" + path, {
		          datatype : "json",
		          success : function(data, textStatus, jqXHR){
		            updateBreadcrumbs(path);
		            switch(data.type){
		              case "file" :
		                  handleFile(data);
		                break;
		              case "folder" :
		                  handleFolder(data);
		                break;
		            }
		          },
		          error : function(){
		            $("#codeError").show();
		          }
		        });
		        
		        function handleFile(data){
		            //파일을 표시한다.
		            $("#commiter").text(data.author);
		            if(data.hasOwnProperty("msg")){
		            	$("#commitMessage").text(data.msg);
		            }
		            if(data.hasOwnProperty("revisionNo")){
		            	$("#revisionNo").text("Revision#: " + data.revisionNo);
		            }
		            $("#commitDate").text(moment(new Date(data.createdDate)).fromNow());
		            $("pre").text(data.data);
		            $("#rawCode").attr("href", "rawcode"+path);
		            
		            $("#fileList").hide();
		            $("#fileView").show();
		            $("pre").highlight();
		        }
		        
		        function handleFolder(data){
		        	data.data = sortData(data.data);
		        	
		            //폴더내용을 리스팅 한다.
		            $("#commiter").text(data.author);
		            if(data.hasOwnProperty("msg")){
		            	$("#commitMessage").text(data.msg);
		            }
		            if(data.hasOwnProperty("revisionNo")){
		            	$("#revisionNo").text("Revision #: " + data.revisionNo);
		            }
		            $("#commitDate").text(data.date);
		            $(".contents").children().remove();
		
		            var aTmp = [];
		            var info, tablerow;
		            
		            for(var name in data.data){
		            	info = data.data[name];
		              	tablerow = makeTableRow(name, info.msg, info.createdDate, info.author);
		              	aTmp.push(tablerow);
		            }
		            $(".contents").append(aTmp);
		            aTmp = null;

		            $("#fileList").show();
		            $("#fileView").hide();
		        }
		        
				function sortData(target){
				    result = [];
				    
				    /** case-insensitive sort **/
				    var aTmp = [];
				    for(var key in target){
				    	aTmp.push(key);
				    }
				    aTmp.sort(function(a,b){
				    	if(a.toLowerCase() < b.toLowerCase()) { return -1; }
				    	if(a.toLowerCase() > b.toLowerCase()) { return 1; }
				    	return 0;
				    });
				    
				    var ht = {};
				    aTmp.forEach(function(sKey){
				    	ht[sKey] = target[sKey];
				    });
				    /** **/
				    
				    _.each(ht, function(value, key, list){
				    	ht[key].name = key;
				        result.push(ht[key]);
				    });
				    
				    result = _.sortBy(result, function(elm){
				        return -(elm.type === "folder");
				    });
				    
				    var htResult = {};
				    result.forEach(function(o){
				    	htResult[o.name] = o;
				    });
				    
				    try {
				    	return htResult;
				    } finally {
				    	aTmp = ht = null;
				    }
				}

				function makeTableRow(name, message, date, author){
					if (message.length > 70){
						message = message.substr(0, 70) + "...";
					}

					var sFilePath = "#" + (path !== "/" ? path : "") + "/" +name;
					var welRow = $("<tr>")
			              .append($('<td><a class="filename" href="' + sFilePath + '">' + name + '</a></td>'))
			              .append($('<td class="message">' + message + '</td>'))
			              .append($('<td class="date">' + (moment(new Date(date)).fromNow()) + '</td>'))
			              .append($('<td class="author"><a href="/'+ author+'"><img src="/assets/images/default-avatar-34.png" alt="avatar" class="img-rounded"></a></td>'));

					try {
						return welRow;
					} finally {
						welRow = sFilePath = null;
					}
		        }

		        function updateBreadcrumbs(path){
		        	var $breadcrumbs = $("#breadcrumbs");
		        	$($breadcrumbs).html('<a href="#/">'+project_name+'</a>');

		        	var names = path.split("/");
		        	var str = "#";
		        	var name;

		        	for(var i = 1; i < names.length; i++){
			            name = names[i];
			            str += "/" + name;
			            $breadcrumbs.append("/");
			            $("<a>").text(name).attr("href", str).appendTo($breadcrumbs);
		        	}
		        }
		      }); // end-of-document_ready

		      if(!$("#selected-branch").text()){
		    	  $("#selected-branch").text('HEAD');
		      }
		      $(window).trigger('hashchange');
		      _updateDynaTree();
		});

		function getHash() {
			return document.location.hash;
		}

		function setHash(hash) {
			return document.location.hash = hash;
		}
		
		/** resize list **/
		function _initResizeList(){
			var nFolderListX = $("#folderList").offset().left;
			var welBtnResize = $(".btnResize");
			var welWrapDirectory = $(".directory-wrap");
			var waWrapFile = $(".file-wrap"); // fileList, fileView
			
			welBtnResize.mousedown(function(){
				$(window).bind("mousemove", _resizeList);
				return false;
			});
			welBtnResize.mouseup(function(){
				$(window).unbind("mousemove", _resizeList);
				return false;
			});
			$(window).click(function(){ // for IE
				$(window).unbind("mousemove", _resizeList);
			});
			
			// 더블클릭하면 디렉토리 목록 숨김
			welBtnResize.dblclick(function(){
				if(welWrapDirectory.css("display") == "none"){
					welWrapDirectory.show();
					waWrapFile.width(850 - welWrapDirectory.width());
				} else {
					welWrapDirectory.hide();
					waWrapFile.width(850);
				}
			});
			
			function _resizeList(weEvt){
				var nWidth = weEvt.clientX - nFolderListX;
				$(".directory-wrap").width(nWidth);
				$(".file-wrap").width(850 - nWidth);
			}
		}
		
		_initResizeList();
		/** end of resize list **/

		$(".branch-item").click(function(ev) {
			_updateDynaTree();
			$("#selected-branch").text($(this).text());
			$(window).trigger('hashchange');
		});

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

		    /** case-insensitive sort **/
		    var aTmp = [];
		    for(var key in target){
		    	aTmp.push(key);
		    }
		    aTmp.sort(function(a,b){
		    	if(a.toLowerCase() < b.toLowerCase()) { return -1; }
		    	if(a.toLowerCase() > b.toLowerCase()) { return 1; }
		    	return 0;
		    });
		    var ht = {};
		    aTmp.forEach(function(sKey){
		    	ht[sKey] = target[sKey];
		    });
		    /** **/

		    _.each(ht, function(value, key, list){
		        if(value.type === "folder") {
		            result.push({ title: key, isFolder: true, isLazy: true});
		        } else {
		            result.push({ title: key});
		        }
		    });

		    return _.sortBy(result, function(elm){
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

		// initial path loading
		var rootPath = "";
		var treeSelectorId = "#folderList";

		$(function(){
		    var path = getHash().replace(/^#/, "");
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

		function _updateDynaTree(){
		    var path = getHash(true);
		    var branch = encodeURIComponent($("#selected-branch").text());
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
		        "debugLevel": 0,
		        "title"		: "/",
		        "isLazy"	: true,
		        "autoFocus"	: false,
		        "children"	: initData,
		        "fx"		: {"height": "toggle", "duration": 200},
		        "onLazyRead": _onLazyRead,
		        "onActivate": function(node) {
		            // A DynaTreeNode object is passed to the activation handler
		            // Note: we also get this event, if persistence is on, and the page is reloaded.
		            window.location = "#/" + getTreePath(node);
		        }
		    });
		}

		function _onLazyRead(node){
            $.ajax({
                "url": rootPath + getTreePath(node),
                "success": function(result, textStatus) {
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
                "error": function(node, XMLHttpRequest, textStatus, errorThrown) {
                    // Called on error, after error icon was created.
                    console.log(node);
                },
                "cache": false // Append random '_' argument to url to prevent caching.
            });
		}
	};
})("hive.code.Browser");