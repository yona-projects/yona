/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

import { register } from "../namespace.js";

function yonaAssgineeModule(findAssignableUsersApiUrl, updateAssgineesApiUrl, message){
  function formatter(result){
    if(!result.avatarUrl){
      return '<div>' + $yona.escapeHtml(result.name) + '</div>';
    }

    // Template text
    const tplUserItem = $("#tplSelect2FormatUser").text() || '<div class="usf-group" title="{{name}} {{loginId}}">' +
        '<span class="avatar-wrap smaller"><img src="{{avatarURL}}" width="20" height="20"></span>' +
        '<strong class="name">{{name}}</strong>' +
        '<span class="loginid">{{loginId}}</span></div>';

    const formattedResult = $yona.render(tplUserItem, {
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

  const $assignee = $("#assignee");
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
  const selectedLoginId = $assignee.val();
  if (selectedLoginId) {
    (async () => {
      let data;
      try {
        data = await yona.http.ajax(findAssignableUsersApiUrl + "?query=" + selectedLoginId + "&type=loginId", {
          dataType: "json"
        });
      } catch (failure) {
        return; // 예전에도 실패 처리기가 없어 조용히 넘어갔다
      }
      if(data && data.length > 0) {
        yona.ui.Select2.selectUsers($assignee, [data[0]]);
      }
    })();
  }

  $assignee.on("select2:selecting", async (selected) => {
    const data = { assignees: [selected.params.args.data.id] };

    if(updateAssgineesApiUrl){
        let response;
        try {
            response = await yona.http.ajax(updateAssgineesApiUrl, {
                method: "POST",
                dataType: "json",
                contentType: "application/json",
                data: JSON.stringify(data)
            });
        } catch (failure) {
            return; // 예전에도 실패 처리기가 없어 조용히 넘어갔다
        }
        $yona.notify(message + ": " + response.assignee.name, 3000);
    }
  });
}

// 템플릿의 인라인 스크립트와 다른 모듈이 전역 이름으로 부른다
register("yonaAssgineeModule", yonaAssgineeModule);
