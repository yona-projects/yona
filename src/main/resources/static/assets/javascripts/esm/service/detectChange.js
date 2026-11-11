/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

import { register } from "../namespace.js";

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

    async function detectChange(){
        let data;
        try {
            data = await yona.http.ajax({
                method: "POST",
                url: url,
                contentType: "application/json",
                data: JSON.stringify({
                    issueBodyChecksum: issueBodyChecksum,
                    numOfComments: numOfComments,
                    lastUpdateDate: issueUpdateDate
                }),
            });
        } catch (failure) {
            return; // 예전에도 실패 처리기가 없어 조용히 넘어갔다
        }
        if (data.numOfComments - numOfComments === 1) {
            numOfComments = data.numOfComments;
            $yona.notify(`<a href="javascript:location.reload(true)" class="reload-page-link">Reload page</a>`, 0, "New comment by " + $yona.escapeHtml(data.commentAuthorName));
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
    }
}

// 템플릿의 인라인 스크립트와 다른 모듈이 전역 이름으로 부른다
register("detectPageChange", detectPageChange);
