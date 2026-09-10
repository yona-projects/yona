/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
// P3-46 #5: Select2(v3) -> Tom Select 교체.
//
// 범위 밖 발견(최종 보고 참고): yonaIssueSharerModule(...)는 어느 템플릿에서도 호출되지 않고,
// 이 파일 자체도 <script src>로 로드된 적이 없다(grep으로 재확인) - #issueSharer(issue/view.html)
// input은 현재 순수 텍스트 입력일 뿐이며 이 모듈은 완전한 죽은 코드다. 그대로 두면 향후 누군가
// 이 모듈을 다시 연결할 수 있으므로, 브리핑 대상 5개 파일에 포함된 만큼 Tom Select로는 이식하되
// 새로 <script src>를 추가해 활성화하지는 않았다(원본의 malformed 템플릿 - 닫히지 않은 div,
// 아바타/로그인id 미표시 - 도 "완전히 일치" 원칙에 따라 그대로 보존했다).
function yonaIssueSharerModule(findUsersByloginIdsApiUrl, findSharableUsersApiUrl, updateSharingApiUrl, message){
  var MIN_INPUT_LENGTH = 1;
  var resultCache = {};

  // 원본 formatter를 그대로 이식한다 - 닫히지 않은 </div>, 아바타/로그인id 미표시 등 malformed한
  // 부분까지 포함해 의도적으로 고치지 않았다(범위 밖 발견, 최종 보고 참고).
  function formatter(data, escape){
    if(!data.avatarUrl){
      return "<div>" + escape(data.text) + "</div>";
    }

    return "<div class='usf-group' title='" + escape(data.text) + " " + escape(data.loginId || "") + "'>" +
      "<strong class='name'>" + escape(data.text) + "</strong>";
  }

  function score(search){
    var term = search.toLowerCase();
    return function(item){
      var text = (item.text || "").toString().toLowerCase();
      var loginId = (item.loginId || "").toString().toLowerCase();
      return (loginId.indexOf(term) > -1 || text.indexOf(term) > -1) ? 1 : 0;
    };
  }

  var issueSharerElement = document.getElementById("issueSharer");

  var tomSelectInstance = new TomSelect(issueSharerElement, {
    valueField: "loginId",
    labelField: "name",
    searchField: ["name", "loginId"],
    // select2 시절 multiple:true 대응 - input에는 HTML "multiple" 속성이 없어 그대로 두면
    // Tom Select가 단일 선택으로 오인한다.
    maxItems: null,
    highlight: false,
    // select2 v3 멀티select는 기본으로 각 선택 항목에 닫기(x) 버튼을 보여준다 - remove_button
    // 플러그인이 동일 기능이다("새 부가기능"이 아니라 기존에도 있던 동작의 이식).
    plugins: ["remove_button"],
    score: score,
    loadThrottle: 300, // select2 ajax.quietMillis:300 대응
    shouldLoad: function(query){ return query.length >= MIN_INPUT_LENGTH; },
    load: function(query, callback){
      if(resultCache.hasOwnProperty(query)){ // select2 ajax.cache:true 대응
        callback(resultCache[query]);
        return;
      }

      $.ajax(findSharableUsersApiUrl, {
        type: "GET",
        dataType: "json",
        data: { query: query }
      }).done(function(data){
        resultCache[query] = data || [];
        callback(resultCache[query]);
      }).fail(function(){
        callback();
      });
    },
    render: {
      option: formatter,
      item: formatter,
      not_loading: function(data){
        var n = MIN_INPUT_LENGTH - data.input.length;
        return n > 0 ? '<div class="no-results">' + yobi.ui.Select2.i18n.tooShort(n) + '</div>' : '';
      },
      no_results: function(){ return '<div class="no-results">' + yobi.ui.Select2.i18n.noResults + '</div>'; },
      loading: function(){ return '<div class="no-results">' + yobi.ui.Select2.i18n.searching + '</div>'; }
    }
  });

  yobi.ui.Select2.bridgeChangeEvent(tomSelectInstance, issueSharerElement);

  // initSelection 대응: input의 초기 value(콤마로 join된 loginId 목록)는 Tom Select가 <input>
  // 텍스트박스 초기화 경로(getSettings.ts init_textbox)에서 이미 알아서 delimiter(',')로 쪼개
  // "loginId만 있는" 아이템으로 선택해둔다(tomSelectInstance.items). 여기서는 그 loginId들을
  // 서버에 다시 조회해 이름/아바타가 채워진 완전한 데이터로 갱신한다(원본과 동일하게 비동기로
  // 뒤늦게 갱신됨).
  var initialIds = tomSelectInstance.items.join(",");
  if(initialIds !== ""){
    $.ajax(findUsersByloginIdsApiUrl + "?query=" + initialIds, {
      dataType: "json"
    }).done(function(data){
      if(data && data.length > 0){
        data.forEach(function(user){
          tomSelectInstance.updateOption(user.loginId, user);
        });
        tomSelectInstance.refreshItems();
      }
    });
  }

  tomSelectInstance.on("item_add", function(value){
    var data = tomSelectInstance.options[value];
    if(!data){
      return;
    }
    var payload = { sharer: { loginId: data.loginId, type: data.type }, action: "add" };

    if(updateSharingApiUrl){
      $.ajax(updateSharingApiUrl, {
        method: "POST",
        dataType: "json",
        contentType: "application/json",
        data: JSON.stringify(payload)
      }).done(function(response){
        $yobi.notify(response.action + ": " + response.sharer, 3000);
      });
    }
  });

  tomSelectInstance.on("item_remove", function(value){
    var data = tomSelectInstance.options[value];
    if(!data){
      return;
    }
    var payload = { sharer: { loginId: data.loginId, type: data.type }, action: "delete" };

    if(updateSharingApiUrl){
      $.ajax(updateSharingApiUrl, {
        method: "POST",
        dataType: "json",
        contentType: "application/json",
        data: JSON.stringify(payload)
      }).done(function(response){
        $yobi.notify(response.action + ": " + response.sharer, 3000);
      });
    }
  });

  $(issueSharerElement).on("change", function(){
    $(".issue-sharer-count").text(tomSelectInstance.items.length);
  });
}
