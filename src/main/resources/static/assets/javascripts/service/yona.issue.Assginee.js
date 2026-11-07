/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

function yonaAssgineeModule(findAssignableUsersApiUrl, updateAssgineesApiUrl, message){
  function formatter(result){
    if(!result.avatarUrl){
      return '<div>' + result.name + '</div>';
    }

    // Template text
    var tplUserItem = $("#tplSelect2FormatUser").text() || '<div class="usf-group" title="${name} ${loginId}">' +
        '<span class="avatar-wrap smaller"><img src="${avatarURL}" width="20" height="20"></span>' +
        '<strong class="name">${name}</strong>' +
        '<span class="loginid">${loginId}</span></div>';

    var formattedResult = $.tmpl(tplUserItem, {
      "avatarURL": result.avatarUrl,
      "name"     : result.name,
      "loginId"  : result.loginId
    });

    return formattedResult;
  }

  function matcher(params, data){
    var term = (params.term || "").trim().toLowerCase();
    if(term === ""){
      return data;
    }

    var formattedResult = (data.text || "").toLowerCase();
    var loginId = (typeof data.loginId !== "undefined") ? data.loginId.toLowerCase() : "";

    return ((loginId.indexOf(term) > -1) || (formattedResult.indexOf(term) > -1)) ? data : null;
  }

  var $assignee = $("#assignee");
  $assignee.select2({
    minimumInputLength: 0,
    ajax: yona.ui.Select2.userSearch(findAssignableUsersApiUrl),
    templateResult: yona.ui.Select2.userTemplate(formatter),
    templateSelection: yona.ui.Select2.userTemplate(formatter),
    matcher: matcher,
    escapeMarkup: function (m) { return m; } // we do not want to escape markup since we are displaying html in results
  });

  // the select tag has a preselected option that points to a preselected user's loginId.
  // this resolves that loginId to a user object so that the user is shown with the avatar (3.x initSelection)
  var selectedLoginId = $assignee.val();
  if (selectedLoginId) {
    $.ajax(findAssignableUsersApiUrl + "?query=" + selectedLoginId + "&type=loginId", {
      dataType: "json"
    }).done(function(data) {
      if(data && data.length > 0) {
        yona.ui.Select2.selectUsers($assignee, [data[0]]);
      }
    });
  }

  $assignee.on("select2:selecting", function(selected) {
    var data = { assignees: [selected.params.args.data.id] };

    if(updateAssgineesApiUrl){
        $.ajax(updateAssgineesApiUrl, {
            method: "POST",
            dataType: "json",
            contentType: "application/json",
            data: JSON.stringify(data)
        }).done(function(response){
            $yona.notify(message + ": " + response.assignee.name, 3000);
        });
    }
  });
}
