moment.lang('ko', {
    months : "1월_2월_3월_4월_5월_6월_7월_8월_9월_10월_11월_12월".split("_"),
    monthsShort : "1월_2월_3월_4월_5월_6월_7월_8월_9월_10월_11월_12월".split("_"),
    weekdays : "일요일_월요일_화요일_수요일_목요일_금요일_토요일".split("_"),
    weekdaysShort : "일_월_화_수_목_금_토".split("_"),
    weekdaysMin : "일_월_화_수_목_금_토".split("_"),
    longDateFormat : {
        LT : "A h시 mm분",
        L : "YYYY.MM.DD",
        LL : "YYYY년 MMMM D일",
        LLL : "YYYY년 MMMM D일 LT",
        LLLL : "YYYY년 MMMM D일 dddd LT"
    },
    meridiem : function (hour, minute, isUpper) {
        return hour < 12 ? '오전' : '오후';
    },
    calendar : {
        sameDay : '오늘 LT',
        nextDay : '내일 LT',
        nextWeek : 'dddd LT',
        lastDay : '어제 LT',
        lastWeek : '지난주 dddd LT',
        sameElse : 'L'
    },
    relativeTime : {
        future : "%s 후",
        past : "%s 전",
        s : "몇초",
        ss : "%d초",
        m : "일분",
        mm : "%d분",
        h : "한시간",
        hh : "%d시간",
        d : "하루",
        dd : "%d일",
        M : "한달",
        MM : "%d달",
        y : "일년",
        yy : "%d년"
    },
    ordinal : '%d일'
});

$(document).ready(function(){
      $(window).bind('hashchange', function(e){
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
            if( data.hasOwnProperty("msg") ) $("#commitMessage").text(data.msg);
            if( data.hasOwnProperty("revisionNo") ) $("#revisionNo").text("Revision#: " + data.revisionNo);
            $("#commitDate").text(moment(new Date(data.date)).fromNow());
            $("code").text(data.data);
            $("#rawCode").attr("href", "rawcode"+path);//TODO 현재 동작하지 않음.
            
            $("#folderView").hide();
            $("#codeView").show();
            $("code").highlight();
        }
        function handleFolder(data){
            //폴더내용을 리스팅 한다.
            $("#commiter").text(data.author);
            if( data.hasOwnProperty("msg") ) $("#commitMessage").text(data.msg);
            if( data.hasOwnProperty("revisionNo") ) $("#revisionNo").text("Revision #: " + data.revisionNo);
            $("#commitDate").text(data.date);

            $(".contents").children().remove();

            for(var name in data.data){
              var info = data.data[name];
              var tablerow = makeTableRow(name, info.msg, info.date, info.author);
              $(".contents").append(tablerow);
            }

            $("#folderView").show();
            $("#codeView").hide();
        }
        function makeTableRow(name, message, date, author){
          if (message.length > 70){
            message = message.substr(0, 70) + "...";
          }
          return $("<tr>")
              .append(
                  $("<td>").append(
                      $("<a>").text(name).attr("href", "#" + (path !== "/" ? path : "") + "/" +name)
                    ).addClass("filename")
                  )
              .append($("<td>").text(message).addClass("message"))
              .append($("<td>").text(moment(new Date(date)).fromNow()).addClass("date"))
              //.append($("<td>").text(author).addClass("author"))
              .append($('<td class="author"><a href="/'+ author+'"><img src="/assets/images/default-avatar-34.png" alt="avatar" class="img-rounded"></a></td>'));
        }
        function updateBreadcrumbs(path){
          var $breadcrumbs = $("#breadcrumbs");
          $($breadcrumbs).html('<a href="#/">'+project_name+'</a>');
                      
          var names = path.split("/");
          var str = "#"
          for(var i = 1; i < names.length; i++){
            var name = names[i];
            str += "/" + name;
            $breadcrumbs.append("/");
            $("<a>").text(name).attr("href", str).appendTo($breadcrumbs);
          }
        }
      });

      if (!$("#selected-branch").text()) $("#selected-branch").text('HEAD');
      $(window).trigger('hashchange');
  });

  function getHash(){
      //혹시 있을지도 모를 호완성을 위해.
      return location.hash;
  }
  function setHash(hash){
      return location.hash = hash;
  }
  function standizePath(path){
    return "/" + path.split("/").filter(isEmpty).join("/");
    function isEmpty(data){
      if(data == ""){
        return false;
      } else {
        return true;
      }
    }
  }

  $(".branch-item").click(function(ev){
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

