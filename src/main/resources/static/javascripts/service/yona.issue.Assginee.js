/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
// P3-46 #5: Select2(v3) -> Tom Select 교체.
//
// #assignee(이슈 담당자, issue/view.html)는 data-toggle="select2" 자동 초기화 대상이 아니라
// (그런 속성이 없다) 이 모듈이 직접 TomSelect를 생성한다 - 그래서 change 이벤트 브릿지도 여기서
// 직접 걸어야 한다(yobi.ui.Select2.js 상단 주석 참고, yobi.issue.Write.js/yobi.project.New.js처럼
// evt.val을 읽는 코드와의 호환을 위해 필요).
function yonaAssgineeModule(findAssignableUsersApiUrl, updateAssgineesApiUrl, message){
  var MIN_INPUT_LENGTH = 0;
  var resultCache = {};

  function formatter(data, escape){
    if(!data.avatarUrl){
      return "<div>" + escape(data.text) + "</div>";
    }

    var loginId = data.loginId ? "@" + data.loginId : "";

    return '<div class="usf-group" title="' + escape(data.text) + ' ' + escape(loginId) + '">' +
      '<span class="avatar-wrap smaller"><img src="' + escape(data.avatarUrl) + '" width="20" height="20"></span>' +
      '<strong class="name">' + escape(data.text) + '</strong>' +
      '<span class="loginid">' + escape(loginId) + '</span>' +
      '</div>';
  }

  function score(search){
    var term = search.toLowerCase();
    return function(item){
      var text = (item.text || "").toString().toLowerCase();
      var loginId = (item.loginId || "").toString().toLowerCase();
      return (loginId.indexOf(term) > -1 || text.indexOf(term) > -1) ? 1 : 0;
    };
  }

  var assigneeElement = document.getElementById("assignee");

  var tomSelectInstance = new TomSelect(assigneeElement, {
    valueField: "loginId",
    labelField: "name",
    searchField: ["name", "loginId"],
    highlight: false,
    score: score,
    loadThrottle: 300, // select2 ajax.quietMillis:300 대응
    // minimumInputLength:0 대응 - 검색어가 비어 있어도(길이 0) 항상 load를 허용한다.
    shouldLoad: function(query){ return query.length >= MIN_INPUT_LENGTH; },
    load: function(query, callback){
      if(resultCache.hasOwnProperty(query)){ // select2 ajax.cache:true 대응
        callback(resultCache[query]);
        return;
      }

      $.ajax(findAssignableUsersApiUrl, {
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

  yobi.ui.Select2.bridgeChangeEvent(tomSelectInstance, assigneeElement);

  // initSelection 대응: 단일 선택이라 초기 아이템은 최대 1개(hidden input의 초기 value가 이미
  // Tom Select의 <input> 파싱 경로에서 아이템으로 선택돼 있다). 이름/아바타가 채워진 완전한
  // 데이터로 비동기 갱신한다.
  var initialId = tomSelectInstance.items[0];
  if(initialId){
    $.ajax(findAssignableUsersApiUrl + "?query=" + initialId + "&type=loginId", {
      dataType: "json"
    }).done(function(data){
      if(data && data.length > 0){
        tomSelectInstance.updateOption(data[0].loginId, data[0]);
        tomSelectInstance.refreshItems();
      }
    });
  }

  tomSelectInstance.on("item_add", function(value){
    var data = { assignees: [value] };

    if(updateAssgineesApiUrl){
      $.ajax(updateAssgineesApiUrl, {
        method: "POST",
        dataType: "json",
        contentType: "application/json",
        data: JSON.stringify(data)
      }).done(function(response){
        $yobi.notify(message + ": " + response.assignee.name, 3000);
      });
    }
  });
}
