/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

function findNotiReceiversHandler(elTextarea, url) {
    var MAX_DISPLAY = 10
    var DEBOUNCE_DURATION = 1000;

    if (!window.displayTimeout) window.displayTimeout = 0;

    findNotiReceivers();

    function _onKeyup() {
        clearTimeout(window.displayTimeout);
        window.displayTimeout = setTimeout(findNotiReceivers, DEBOUNCE_DURATION);
    }
    // removeEventListener는 같은 함수 참조가 필요하므로 unbind에서 쓸 수 있게 엘리먼트에 보관한다.
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
