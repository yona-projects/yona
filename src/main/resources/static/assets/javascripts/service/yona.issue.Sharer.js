/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

function yonaIssueSharerModule(findUsersByloginIdsApiUrl, findSharableUsersApiUrl, updateSharingApiUrl, message){
  var userOf = function(state){ return yona.ui.Select2.userOf(state); };

  function formatter(result){
    if(!result.avatarUrl){
      return "<div>" + result.name + "</div>";
    }

    // Template text. Also you can use predefined template: $("#tplSelect2FormatUser").text()
    var tplUserItem = "<div class='usf-group' title='${name} ${loginId}'>" +
        "<strong class='name'>${name}</strong>";

    var formattedResult = $yona.tmpl(tplUserItem, {
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

  var $issueSharer = $("#issueSharer");
  $issueSharer.select2({
    minimumInputLength: 1,
    ajax: yona.ui.Select2.userSearch(findSharableUsersApiUrl),
    templateResult: yona.ui.Select2.userTemplate(formatter),
    templateSelection: yona.ui.Select2.userTemplate(formatter),
    matcher: matcher,
    escapeMarkup: function (m) { return m; } // we do not want to escape markup since we are displaying html in results
  });

  // the select tag has preselected options that point to the sharers' loginIds.
  // this resolves those loginIds to user objects so that each sharer has a type (3.x initSelection)
  var selectedLoginIds = ($issueSharer.val() || []).join(",");
  if (selectedLoginIds !== "") {
    $.ajax(findUsersByloginIdsApiUrl+ "?query=" + selectedLoginIds, {
      dataType: "json"
    }).done(function(data) {
      if(data && data.length > 0) {
        yona.ui.Select2.selectUsers($issueSharer, data);
      }
    });
  }

  $issueSharer.on("select2:selecting", function(selected) {
    var chosen = userOf(selected.params.args.data);
    var data = { sharer: {loginId: chosen.loginId, type: chosen.type}, action: 'add'};

    if(updateSharingApiUrl){
        $.ajax(updateSharingApiUrl, {
            method: "POST",
            dataType: "json",
            contentType: "application/json",
            data: JSON.stringify(data)
        }).done(function(response){
            $yona.notify(response.action + ": " + response.sharer, 3000);
        });
    }
  });

  $issueSharer.on("select2:unselecting", function(selected) {
    var removed = userOf(selected.params.args.data);
    var data = { sharer: {loginId: removed.loginId, type: removed.type}, action: 'delete'};

    if(updateSharingApiUrl){
      $.ajax(updateSharingApiUrl, {
        method: "POST",
        dataType: "json",
        contentType: "application/json",
        data: JSON.stringify(data)
      }).done(function(response){
        $yona.notify(response.action + ": " + response.sharer, 3000);
      });
    }
  });

  $issueSharer.on('change', function () {
    $(".issue-sharer-count").text(($issueSharer.val() || []).length);
  });
}
