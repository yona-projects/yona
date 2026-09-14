/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * P3-70 라운드10: elTextarea는 이제 raw DOM 엘리먼트다(호출부 issue/view.html:723도 $(...)
 * 래핑을 제거했다) - jQuery `.on/.off/.closest/.find/.val/.html`을 각각 addEventListener(핸들러
 * 참조를 엘리먼트에 보관해 off에서 재사용)/removeEventListener/closest/querySelector/.value/
 * .innerHTML로 대체했다. 로직 자체(디바운스 시간, fetch 요청/에러 처리, 정렬)는 완전히 동일하다.
 */
function findNotiReceiversHandler(elTextarea, url) {
    var MAX_DISPLAY = 10
    var DEBOUNCE_DURATION = 1000;

    if (!window.displayTimeout) window.displayTimeout = 0;

    findNotiReceivers();

    function _onKeyup() {
        clearTimeout(window.displayTimeout);
        window.displayTimeout = setTimeout(findNotiReceivers, DEBOUNCE_DURATION);
    }
    // unbindFindNotiReceiversHandler가 동일한 핸들러 참조로 해제할 수 있도록 엘리먼트에
    // 보관한다(jQuery의 네임스페이스 이벤트 "keyup.receiverList"가 하던 역할과 동일).
    elTextarea._receiverListKeyupHandler = _onKeyup;
    elTextarea.addEventListener('keyup', _onKeyup);

    function findNotiReceivers() {
        var elForm = elTextarea.closest("form");
        var elParentCommentId = elForm ? elForm.querySelector(".parentCommentId") : null;
        var parentCommentId = elParentCommentId ? elParentCommentId.value : "";

        fetch(url, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({ comment: elTextarea.value, parentCommentId: parentCommentId || "" })
        })
        .then(function(response){
            if(!response.ok){
                return response.text().then(function(text){
                    return Promise.reject({statusText: response.statusText, responseText: text});
                });
            }
            return response.json();
        })
        .then(function (data) {
            NProgress.done();
            var receivers = "";
            if (!data && !data.receivers) {
                return;
            }

            sortByName(data.receivers)

            for (let i = 0; i < data.receivers.length; i++) {
                if (i === MAX_DISPLAY) {
                    receivers += `<span>+${data.receivers.length - MAX_DISPLAY}</span>`
                    break;
                }
                var user = data.receivers[i];
                receivers += `<span title="${user.name} @${user.loginId}">${user.pureNameOnly}</span>`
            }

            // Display notification receivers
            var elReceiverList = elForm ? elForm.querySelector(".notification-receiver-list") : null;
            if (elReceiverList) {
                elReceiverList.innerHTML = receivers;
            }
        })
        .catch(function (err) {
            var response = JSON.parse(err.responseText);
            var message = '[' + err.statusText + '] ' + response.message + '\n\nRefresh the page!';
            $yona.showAlert(message);
        });
    }

    function sortByName(receivers) {
        receivers.sort(function (a, b) {
            var nameA = a.name.toUpperCase(); // ignore upper and lowercase
            var nameB = b.name.toUpperCase(); // ignore upper and lowercase
            if (nameA < nameB) {
                return -1;
            }
            if (nameA > nameB) {
                return 1;
            }
            return 0;
        });
    }
}


function unbindFindNotiReceiversHandler(elTextarea) {
    if (elTextarea._receiverListKeyupHandler) {
        elTextarea.removeEventListener('keyup', elTextarea._receiverListKeyupHandler);
        elTextarea._receiverListKeyupHandler = null;
    }
}
