/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

var favicon=new Favico({
    position: 'up',
    bgColor: '#4183c4',
    animation:'none'
});

function detectPageChange(url){
    var issueBodyChecksum = document.getElementById("issueBodyChecksum").value;
    var numOfComments = document.getElementById("numOfComments").value;
    var issueUpdateDate = document.getElementById("issueUpdateDate").value;

    var duration = 3000;
    var timer;
    var disposed = false;
    var controller = new AbortController();

    runIntervalAction(detectChange, duration);
    return function() {
        disposed = true;
        clearTimeout(timer);
        controller.abort();
    };

    ////////////////////////////////

    function runIntervalAction(fn, duration) {
        if (disposed) return;
        if (duration > 60 * 5 * 1000) {
            duration = 60 * 5 * 1000; // 5 min
        } else {
            duration = duration * 1.2
        }
        timer = setTimeout(function(){
            if (disposed) return;
            fn();
            runIntervalAction(fn, duration);
        }, duration);
    }

    function detectChange(){
        fetch(url, {
            method: "POST",
            signal: controller.signal,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                issueBodyChecksum: issueBodyChecksum,
                numOfComments: numOfComments,
                lastUpdateDate: issueUpdateDate
            })
        })
            .then(function(response){ return response.json(); })
            .then(function (data) {
                if (disposed) return;
                if (data.numOfComments - numOfComments === 1) {
                    numOfComments = data.numOfComments;
                    $yona.notify(`<a href="javascript:location.reload(true)" class="reload-page-link">Reload page</a>`, 0, "New comment by " + data.commentAuthorName);
                    favicon.badge('N');
                } else if (data.numOfComments - numOfComments > 1) {
                    numOfComments = data.numOfComments;
                    $yona.notify(`<a href="javascript:location.reload(true)" class="reload-page-link">Reload page</a>`, 0, "New comments added!");
                    favicon.badge('N');
                }

                if (data.issueBodyChanged) {
                    issueBodyChecksum = data.issueBodyChecksum;
                    $yona.notify(`<a href="javascript:location.reload(true)" class="reload-page-link">Reload page</a>`, 0, "Issue updated!");
                    favicon.badge('N');
                }
            })
            .catch(function(error) {
                if (!disposed) console.error("Issue change detection failed", error);
            });
    }
}

