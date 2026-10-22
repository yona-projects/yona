/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

var favicon=new Favico({
    position: 'up',
    bgColor: '#4183c4',
    animation:'none'
});

function detectPageChange(url){
    var issueBodyChecksum = $("#issueBodyChecksum").val();
    var numOfComments = $("#numOfComments").val();
    var issueUpdateDate = $("#issueUpdateDate").val();

    var duration = 3000;

    runIntervalAction(detectChange, duration);

    ////////////////////////////////

    function runIntervalAction(fn, duration) {
        if (duration > 60 * 5 * 1000) {
            duration = 60 * 5 * 1000; // 5 min
        } else {
            duration = duration * 1.2
        }
        setTimeout(function(){
            fn();
            runIntervalAction(fn, duration);
        }, duration);
    }

    function detectChange(){
        $.ajax({
            method: "POST",
            url: url,
            contentType: "application/json",
            data: JSON.stringify({
                issueBodyChecksum: issueBodyChecksum,
                numOfComments: numOfComments,
                lastUpdateDate: issueUpdateDate
            }),
        })
            .done(function (data) {
                if (data.numOfComments - numOfComments === 1) {
                    numOfComments = data.numOfComments;
                    $yobi.notify(`<a href="javascript:location.reload(true)" class="reload-page-link">Reload page</a>`, 0, "New comment by " + data.commentAuthorName);
                    favicon.badge('N');
                } else if (data.numOfComments - numOfComments > 1) {
                    numOfComments = data.numOfComments;
                    $yobi.notify(`<a href="javascript:location.reload(true)" class="reload-page-link">Reload page</a>`, 0, "New comments added!");
                    favicon.badge('N');
                }

                if (data.issueBodyChanged) {
                    issueBodyChecksum = data.issueBodyChecksum;
                    $yobi.notify(`<a href="javascript:location.reload(true)" class="reload-page-link">Reload page</a>`, 0, "Issue updated!");
                    favicon.badge('N');
                }
            })
    }
}

