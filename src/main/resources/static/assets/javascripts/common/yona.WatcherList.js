var apiUrlMemo;
$(".show-watchers").on("click", function () {
    $(".watcher-list").toggle();
});

function watcherListApi(apiUrl, currentUserInfoUrl){
    if (apiUrl) {
        apiUrlMemo = apiUrl;
    }

    $.get(apiUrl || apiUrlMemo)
        .done(function (data) {
            var watcherList = "";
            if ( data.watchersInList && data.watchersInList === 1 && data.watchers[0].url === currentUserInfoUrl) {
                $(".show-watchers").css("display", "none");
                return;
            }

            if( data.watchersInList > 1 ){
                $(".watcherCount").text(" " + data.totalWatchers);
                $(".show-watchers").css("display", "inline-block");
                data.watchers.forEach(function (watcher) {
                    watcherList += '<a href="' + watcher.url + '" class="watcher-name">' + watcher.name + "</a>";
                });
                if(data.totalWatchers > data.watchersInList) {
                    watcherList += Messages("watchers.more", (data.totalWatchers - data.watchersInList))
                }
                $(".watcher-list").html(watcherList);
            } else {
                $(".show-watchers").css("display", "none");
            }
        });
}