/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

function findNotiReceiversHandler($textarea, url) {
    const MAX_DISPLAY = 10
    const DEBOUNCE_DURATION = 1000;

    if (!window.displayTimeout) window.displayTimeout = 0;

    findNotiReceivers();

    $textarea.on('keyup.receiverList', () => {
        clearTimeout(window.displayTimeout);
        window.displayTimeout = setTimeout(findNotiReceivers, DEBOUNCE_DURATION);
    });

    function findNotiReceivers() {
        const parentCommentId = $textarea.closest("form").find(".parentCommentId").val()

        $.ajax({
            method: "POST",
            url: url,
            contentType: "application/json",
            data: JSON.stringify({ comment: $textarea.val(), parentCommentId: parentCommentId || "" }),
        })
        .done((data) => {
            NProgress.done();
            let receivers = "";
            // v1.6는 `&&`라 응답이 비어 있으면 오히려 오류가 났다(C009)
            if (!data || !data.receivers) {
                return;
            }

            sortByName(data.receivers)

            for (let i = 0; i < data.receivers.length; i++) {
                if (i === MAX_DISPLAY) {
                    receivers += `<span>+${data.receivers.length - MAX_DISPLAY}</span>`
                    break;
                }
                const user = data.receivers[i];
                receivers += `<span title="${user.name} @${user.loginId}">${user.pureNameOnly}</span>`
            }

            // Display notification receivers
            $textarea.closest("form").find(".notification-receiver-list").html(receivers);
        })
        .fail((jqXHR, textStatus) => {
            const response = JSON.parse(jqXHR.responseText);
            const message = '[' + jqXHR.statusText + '] ' + response.message + '\n\nRefresh the page!';
            $yona.showAlert(message);
        });
    }

    function sortByName(receivers) {
        receivers.sort((a, b) => {
            const nameA = a.name.toUpperCase(); // ignore upper and lowercase
            const nameB = b.name.toUpperCase(); // ignore upper and lowercase
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


function unbindFindNotiReceiversHandler($textarea) {
    $textarea.off('keyup.receiverList')
}
