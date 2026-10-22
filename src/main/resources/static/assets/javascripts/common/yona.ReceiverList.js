/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

function findNotiReceiversHandler($textarea, url) {
    var MAX_DISPLAY = 10
    var DEBOUNCE_DURATION = 1000;

    if (!window.displayTimeout) window.displayTimeout = 0;

    findNotiReceivers();

    $textarea.on('keyup.receiverList', function () {
        clearTimeout(window.displayTimeout);
        window.displayTimeout = setTimeout(findNotiReceivers, DEBOUNCE_DURATION);
    });

    function findNotiReceivers() {
        var parentCommentId = $textarea.closest("form").find(".parentCommentId").val()

        $.ajax({
            method: "POST",
            url: url,
            contentType: "application/json",
            data: JSON.stringify({ comment: $textarea.val(), parentCommentId: parentCommentId || "" }),
        })
        .done(function (data) {
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
            $textarea.closest("form").find(".notification-receiver-list").html(receivers);
        })
        .fail(function (jqXHR, textStatus) {
            var response = JSON.parse(jqXHR.responseText);
            var message = '[' + jqXHR.statusText + '] ' + response.message + '\n\nRefresh the page!';
            $yobi.showAlert(message);
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


function unbindFindNotiReceiversHandler($textarea) {
    $textarea.off('keyup.receiverList')
}
