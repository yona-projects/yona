/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
// #issueSharer는 issue/view.html에서 실제로 로드/호출되는 코드다.
function yonaIssueSharerModule(findUsersByloginIdsApiUrl, findSharableUsersApiUrl, updateSharingApiUrl, message){
  var MIN_INPUT_LENGTH = 1;
  var resultCache = {};

  // 원본 formatter를 그대로 이식한다 - 닫히지 않은 </div>, 아바타/로그인id 미표시 등 malformed한
  // 부분까지 포함해 "완전히 일치" 원칙에 따라 의도적으로 고치지 않았다.
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

      fetch(findSharableUsersApiUrl + "?" + new URLSearchParams({ query: query }))
        .then(function(response){
          if(!response.ok){
            return Promise.reject(response);
          }
          return response.json();
        })
        .then(function(data){
          resultCache[query] = data || [];
          callback(resultCache[query]);
        })
        .catch(function(){
          callback();
        });
    },
    render: {
      option: formatter,
      item: formatter,
      not_loading: function(data){
        var n = MIN_INPUT_LENGTH - data.input.length;
        return n > 0 ? '<div class="no-results">' + yona.ui.TomSelect.i18n.tooShort(n) + '</div>' : '';
      },
      no_results: function(){ return '<div class="no-results">' + yona.ui.TomSelect.i18n.noResults + '</div>'; },
      loading: function(){ return '<div class="no-results">' + yona.ui.TomSelect.i18n.searching + '</div>'; }
    }
  });

  yona.ui.TomSelect.bridgeChangeEvent(tomSelectInstance, issueSharerElement);

  // initSelection 대응: input의 초기 value(콤마로 join된 loginId 목록)는 Tom Select가 <input>
  // 텍스트박스 초기화 경로(getSettings.ts init_textbox)에서 이미 알아서 delimiter(',')로 쪼개
  // "loginId만 있는" 아이템으로 선택해둔다(tomSelectInstance.items). 여기서는 그 loginId들을
  // 서버에 다시 조회해 이름/아바타가 채워진 완전한 데이터로 갱신한다(원본과 동일하게 비동기로
  // 뒤늦게 갱신됨).
  var initialIds = tomSelectInstance.items.join(",");
  if(initialIds !== ""){
    fetch(findUsersByloginIdsApiUrl + "?query=" + initialIds)
      .then(function(response){
        if(!response.ok){
          return Promise.reject(response);
        }
        return response.json();
      })
      .then(function(data){
        if(data && data.length > 0){
          data.forEach(function(user){
            tomSelectInstance.updateOption(user.loginId, user);
          });
          tomSelectInstance.refreshItems();
        }
      })
      .catch(function(){
        // 원본 jQuery 버전에도 fail 핸들러가 없어 실패 시 조용히 무시됐다.
      });
  }

  tomSelectInstance.on("item_add", function(value){
    var data = tomSelectInstance.options[value];
    if(!data){
      return;
    }
    var payload = { sharer: { loginId: data.loginId, type: data.type }, action: "add" };

    if(updateSharingApiUrl){
      fetch(updateSharingApiUrl, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(payload)
      })
      .then(function(response){
        if(!response.ok){
          return Promise.reject(response);
        }
        return response.json();
      })
      .then(function(response){
        $yona.notify(response.action + ": " + response.sharer, 3000);
      })
      .catch(function(){
        // 원본 jQuery 버전에도 fail 핸들러가 없어 실패 시 조용히 무시됐다.
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
      fetch(updateSharingApiUrl, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(payload)
      })
      .then(function(response){
        if(!response.ok){
          return Promise.reject(response);
        }
        return response.json();
      })
      .then(function(response){
        $yona.notify(response.action + ": " + response.sharer, 3000);
      })
      .catch(function(){
        // 원본 jQuery 버전에도 fail 핸들러가 없어 실패 시 조용히 무시됐다.
      });
    }
  });

  issueSharerElement.addEventListener("change", function(){
    document.querySelectorAll(".issue-sharer-count").forEach(function(el){
      el.textContent = tomSelectInstance.items.length;
    });
  });
}
