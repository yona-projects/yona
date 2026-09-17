// watcher.name(WatchController가 반환, 서버가 이스케이프하지 않는 필드)을 문자열로 이어붙여
// .html(...)에 꽂던 저장형 XSS가 있었다 - createElement+textContent로 DOM을 직접 구성해 제거.
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
