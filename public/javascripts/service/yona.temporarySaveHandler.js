/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

function temporarySaveHandler($textarea, contentInitialized) {
    contentInitialized = contentInitialized !== false;      // default : true
    var noticePanel = $textarea.closest('div.write-comment-box').find(".editor-notice-label");   // 화면 어딘가 임시저장 상태 표시할 곳
    var keydownTimer;

    // this 대신 editor 컨테이너. this 에 붙여두면 화면 전환 시 handler가 메모리에 중첩됨
    $textarea.on('keyup', function () {
        if ($textarea.val() !== localStorage.getItem(location.pathname)) {
            clearTimeout(keydownTimer);

            if ($textarea.val() === "") {
                localStorage.removeItem(location.pathname);
                return;
            }

            keydownTimer = setTimeout(function () {
                localStorage.setItem(location.pathname, $textarea.val());

                noticePanel.html("<span class=\"saved\">Draft saved</span>");
                noticePanel.children().fadeOut(1000);
            }, 5000);
        }
    });

    if (contentInitialized && localStorage.getItem(location.pathname)) {
        $textarea.val(localStorage.getItem(location.pathname));
    }
}

function removeCurrentPageTemprarySavedContent() {
    localStorage.removeItem(location.pathname);
}
