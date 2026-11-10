/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

// 파비콘 배지 도우미는 ES 모듈(esm/ui/Favicon.js)이라 문서 분석 중에는 아직 없다. 처음 쓸 때 만든다.
let favicon = null;

function getFavicon(){
    if(!favicon){
        favicon = new yona.ui.Favicon({
            position: 'up',
            bgColor: '#4183c4'
        });
    }
    return favicon;
}

function detectPageChange(url){
    let issueBodyChecksum = $("#issueBodyChecksum").val();
    let numOfComments = $("#numOfComments").val();
    const issueUpdateDate = $("#issueUpdateDate").val();

    const duration = 3000;

    runIntervalAction(detectChange, duration);

    ////////////////////////////////

    function runIntervalAction(fn, duration) {
        if (duration > 60 * 5 * 1000) {
            duration = 60 * 5 * 1000; // 5 min
        } else {
            duration = duration * 1.2
        }
        setTimeout(() =>{
            fn();
            runIntervalAction(fn, duration);
        }, duration);
    }

    function detectChange(){
        yona.http.ajax({
            method: "POST",
            url: url,
            contentType: "application/json",
            data: JSON.stringify({
                issueBodyChecksum: issueBodyChecksum,
                numOfComments: numOfComments,
                lastUpdateDate: issueUpdateDate
            }),
        })
            .done((data) => {
                if (data.numOfComments - numOfComments === 1) {
                    numOfComments = data.numOfComments;
                    $yona.notify(`<a href="javascript:location.reload(true)" class="reload-page-link">Reload page</a>`, 0, "New comment by " + data.commentAuthorName);
                    getFavicon().badge('N');
                } else if (data.numOfComments - numOfComments > 1) {
                    numOfComments = data.numOfComments;
                    $yona.notify(`<a href="javascript:location.reload(true)" class="reload-page-link">Reload page</a>`, 0, "New comments added!");
                    getFavicon().badge('N');
                }

                if (data.issueBodyChanged) {
                    issueBodyChecksum = data.issueBodyChecksum;
                    $yona.notify(`<a href="javascript:location.reload(true)" class="reload-page-link">Reload page</a>`, 0, "Issue updated!");
                    getFavicon().badge('N');
                }
            })
    }
}

