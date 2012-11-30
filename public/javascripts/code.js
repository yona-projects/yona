$(document).ready(function(){      
      $(window).bind('hashchange', function(e){
        //대기 표시 한다.
        //여기서 요청을 보내고
        var path = getHash().replace(/^#/, "");
        
        $.ajax("code/!" + path, {
          datatype : "json",
          success : function(data, textStatus, jqXHR){
            updateBreadcrumbs(path);
            updateNav(path);
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
            $("#commitDate").text(data.date);
            $("code").text(data.data);
            $("#rawCode").attr("href", path.replace(/\/!/, ""));//TODO 현재 동작하지 않음.
            
            $("#folderView").hide();
            $("#codeView").show();
            $("code").highlight();
        }
        function handleFolder(data){
            //폴더내용을 리스팅 한다.
            $("#commiter").text(data.author);
            $("#commitMessage").text(data.msg);
            $("#commitDate").text(data.date);
            
            $("tbody").children().remove();
            
            for(var name in data.data){
              var info = data.data[name];
              var tablerow = makeTableRow(name, info.msg, info.date, info.author);
              $("tbody").append(tablerow);
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
              .append($("<td>").text("just now"/*date*/).addClass("date"))
              //.append($("<td>").text(author).addClass("author"))
              .append($('<td class="author"><a href="/'+ author+'" class="img-rounded"><img src="/assets/images/default-avatar-34.png" width="32" height="32" alt="avatar"></a></td>'));
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

  function updateNav(path){
    console.log("update triggered!");
    var stack = [];
    var pathSeg = path.split("/");
    pathSeg = pathSeg.filter(function(data){if(data == "") return false; else return true;});
    path = "/" + pathSeg.join("/");
    while(true){
      if($("#folderNav").find("li[data-path='" + path +"']").length == 0){
        stack.push(path);
        pathSeg.pop();
        path = "/" + pathSeg.join("/");
      } else {
        stack.push(path);
        break;
      }
    }
    //request all
    //check response
    var count = stack.length;
    stack.map(function(path){
      $.ajax("code/!" + path, {
        success : function(data){
          stack = stack.map(function(d){
            if(path == d){
              data.path = path;
              return data;
            } else {
              return d;
            }
          });

          if(--count <= 0){
            addItem(stack);
          }
        }
      });
      return path;
    });
  }
  function addItem(stack){
    
    $folderNav = $("#folderNav");

    while(stack.length !== 0){
      var data = stack.pop();
      if(data.type === "file") continue;

      var $target = $folderNav.find("li[data-path='" + data.path + "']");
      for(var name in data.data){
        if(data.data[name].type === "file") continue;
        var tpath = (data.path == "/"? "" : data.path) + "/" +name;
        if($folderNav.find("li[data-path='" + tpath + "']").length == 0){
          var $li = makeListItem(name, data.path);
          $target.after($li);
        }
      }
    }
    function makeListItem(name, path){
      var depth = path.split("/").length - 1;
      if(path =="/") depth -= 1;
      var margin = depth * 5 + 25;
      var tpath = (path == "/"? "" : path) + "/" +name
      var $a = $("<a>").attr("href", "#" + tpath).appendTo($li);
      $("<i>").addClass("ico btn-folder").appendTo($a);
      $a.append(name);
      return $("<li>").addClass("directory").attr("data-path", tpath).css({paddingLeft: margin}).append($a);
    }
  }
  
  $("#branchSelector option").click(function(){
      alert($(this).text());
  });