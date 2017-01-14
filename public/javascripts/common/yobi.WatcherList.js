var apiUrlMemo;
$(".show-watchers").on("click", function () {
    $(".watcher-list").toggle();
});
function watcherListApi(apiUrl){

    if (apiUrl) {
        apiUrlMemo = apiUrl;
    }

    $.get(apiUrl || apiUrlMemo)
        .done(function (data) {
            var watcherList = "";
            if( data.watchersInList > 0 ){
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