/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

function yonaIssueSharerModule(findUsersByloginIdsApiUrl, findSharableUsersApiUrl, updateSharingApiUrl, message){
  const userOf = function(state){ return yona.ui.Select2.userOf(state); };

  function formatter(result){
    if(!result.avatarUrl){
      return "<div>" + result.name + "</div>";
    }

    // Template text. Also you can use predefined template: $("#tplSelect2FormatUser").text()
    const tplUserItem = "<div class='usf-group' title='${name} ${loginId}'>" +
        "<strong class='name'>${name}</strong>";

    const formattedResult = $yona.tmpl(tplUserItem, {
      "avatarURL": result.avatarUrl,
      "name"     : result.name,
      "loginId"  : result.loginId
    });

    return formattedResult;
  }

  function matcher(params, data){
    const term = (params.term || "").trim().toLowerCase();
    if(term === ""){
      return data;
    }

    const formattedResult = (data.text || "").toLowerCase();
    const loginId = (typeof data.loginId !== "undefined") ? data.loginId.toLowerCase() : "";

    return ((loginId.indexOf(term) > -1) || (formattedResult.indexOf(term) > -1)) ? data : null;
  }

  const $issueSharer = $("#issueSharer");
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
  const selectedLoginIds = ($issueSharer.val() || []).join(",");
  if (selectedLoginIds !== "") {
    $.ajax(findUsersByloginIdsApiUrl+ "?query=" + selectedLoginIds, {
      dataType: "json"
    }).done((data) => {
      if(data && data.length > 0) {
        yona.ui.Select2.selectUsers($issueSharer, data);
      }
    });
  }

  $issueSharer.on("select2:selecting", (selected) => {
    const chosen = userOf(selected.params.args.data);
    const data = { sharer: {loginId: chosen.loginId, type: chosen.type}, action: 'add'};

    if(updateSharingApiUrl){
        $.ajax(updateSharingApiUrl, {
            method: "POST",
            dataType: "json",
            contentType: "application/json",
            data: JSON.stringify(data)
        }).done((response) =>{
            $yona.notify(response.action + ": " + response.sharer, 3000);
        });
    }
  });

  $issueSharer.on("select2:unselecting", (selected) => {
    const removed = userOf(selected.params.args.data);
    const data = { sharer: {loginId: removed.loginId, type: removed.type}, action: 'delete'};

    if(updateSharingApiUrl){
      $.ajax(updateSharingApiUrl, {
        method: "POST",
        dataType: "json",
        contentType: "application/json",
        data: JSON.stringify(data)
      }).done((response) =>{
        $yona.notify(response.action + ": " + response.sharer, 3000);
      });
    }
  });

  $issueSharer.on('change', () => {
    $(".issue-sharer-count").text(($issueSharer.val() || []).length);
  });
}
