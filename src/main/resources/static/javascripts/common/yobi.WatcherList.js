// 점진적 jQuery 제거 - jQuery($) 없이 순수 DOM API/fetch로 재작성.
//
// 이 과정에서 저장형 XSS를 발견해 함께 고쳤다: watcher.name(WatchController가 반환하는
// 사용자 표시 이름 - 서버가 이스케이프하지 않는 순수 JSON 필드)을 문자열로 이어붙여
// $(".watcher-list").html(...)에 그대로 꽂고 있었다. mention.ts에서 고친 것과 동일한 유형의
// 문제라, 문자열 조립 대신 createElement+textContent로 DOM을 직접 구성하도록 바꿨다.
var apiUrlMemo;

document.querySelectorAll(".show-watchers").forEach(function(el){
    el.addEventListener("click", function(){
        document.querySelectorAll(".watcher-list").forEach(function(list){
            list.style.display = (getComputedStyle(list).display === "none") ? "" : "none";
        });
    });
});

function watcherListApi(apiUrl, currentUserInfoUrl){
    if (apiUrl) {
        apiUrlMemo = apiUrl;
    }

    fetch(apiUrl || apiUrlMemo)
        .then(function(response){ return response.json(); })
        .then(function(data){
            if (data.watchersInList && data.watchersInList === 1 && data.watchers[0].url === currentUserInfoUrl) {
                document.querySelectorAll(".show-watchers").forEach(function(el){ el.style.display = "none"; });
                return;
            }

            if (data.watchersInList > 1) {
                document.querySelectorAll(".watcherCount").forEach(function(el){
                    el.textContent = " " + data.totalWatchers;
                });
                document.querySelectorAll(".show-watchers").forEach(function(el){ el.style.display = "inline-block"; });
                document.querySelectorAll(".watcher-list").forEach(function(watcherListEl){
                    watcherListEl.innerHTML = "";
                    data.watchers.forEach(function(watcher){
                        var a = document.createElement("a");
                        a.href = watcher.url;
                        a.className = "watcher-name";
                        a.textContent = watcher.name;
                        watcherListEl.appendChild(a);
                    });
                    if (data.totalWatchers > data.watchersInList) {
                        watcherListEl.appendChild(
                            document.createTextNode(Messages("watchers.more", data.totalWatchers - data.watchersInList))
                        );
                    }
                });
            } else {
                document.querySelectorAll(".show-watchers").forEach(function(el){ el.style.display = "none"; });
            }
        });
}
