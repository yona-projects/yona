$(document).ready(function(){      
      $(window).bind('hashchange', function(e){
        //대기 표시 한다.
        //여기서 요청을 보내고
        var path = getHash().replace(/^#/, "");
        var branch = encodeURIComponent($("#selected-branch").text());
        
        $.ajax("code/" + branch + "/!" + path, {
          datatype : "json",
          success : function(data, textStatus, jqXHR){
              console.log(data);
            updateBreadcrumbs(path);
//            updateNav(path);
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
            $("#commitMessage").text(data.msg);
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
            $("#commitMessage").text(data.msg);
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
          $($breadcrumbs).html('<a href="#/">/</a>');
                      
          var names = path.split("/");
          var str = "#"
          for(var i = 1; i < names.length; i++){
            var name = names[i];
            str += "/" + name;
            $breadcrumbs.append(" &gt; ");
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

// Traverse the path of selected tree item
function getTreePath(node){
    var path = "";
    var title = "";
    if( node.getParent() && node.getParent().data.title !== null ){
        path = getTreePath(node.getParent()) + "/" + node.data.title;
    } else {
        path = node.data.title;
    }
    return path;
}

// initial path loading
var rootPath = "";
$(function(){
    var path = getHash().replace(/^#/, "");
    var branch = encodeURIComponent($("#selected-branch").text());
    rootPath = "code/" + branch + "/!/";
    console.log("rootPath=", rootPath);
    $.ajax({
        url: rootPath,
        success: function(result, textStatus){
            treeInit(adaptorForDynatree(result.data));
        }
    });
});

// DynaTree Init function
// see: http://wwwendt.de/tech/dynatree/doc/dynatree-doc.html
function treeInit(initData){
    $("#folderNav").dynatree({
        fx: { height: "toggle", duration: 200 },
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
                            tooltip: result.faultDetails,
                            info: result.faultString
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
        children: initData
    });
}