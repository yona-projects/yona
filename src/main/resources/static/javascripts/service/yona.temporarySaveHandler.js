/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

function temporarySaveHandler($textarea, contentInitialized) {
    // 호출부(board/issue/milestone/git Write.js, Comment.js 등)가 jQuery 객체를 넘기므로
    // 호출부를 건드리지 않고 내부만 vanilla로 전환하기 위해 실제 DOM 요소를 꺼내 쓴다.
    var textarea = $textarea.jquery ? $textarea[0] : $textarea;
    var noticePanel = document.querySelector(".editor-notice-label");   // 화면 어딘가 임시저장 상태 표시할 곳
    if (!window.draftSavingTimeout) window.draftSavingTimeout = 0;

    // this 대신 editor 컨테이너. this 에 붙여두면 화면 전환 시 handler가 메모리에 중첩됨
    textarea.addEventListener('keyup', function () {
        if (textarea.value !== localStorage.getItem(location.pathname)) {
            clearTimeout(window.draftSavingTimeout);

            if (textarea.value === "") {
                localStorage.removeItem(location.pathname);
                return;
            }

            if (noticePanel) {
                Array.prototype.forEach.call(noticePanel.children, function(child){
                    child.style.display = "none";
                });
            }

            window.draftSavingTimeout = setTimeout(function () {
                if(textarea.dataset.editorMode === "update-comment-body") {
                    // FIXME: There are bug when editing comment.
                    // NOW, just make it skipping to store at local storage
                    localStorage.setItem(location.pathname + '-last-comment-update-draft', textarea.value);
                    return;
                }
                localStorage.setItem(location.pathname, textarea.value);

                if (noticePanel) {
                    noticePanel.innerHTML = "<span class=\"saved\">Draft saved</span>";
                }
            }, 5000);
        }
    });

    if (contentInitialized === undefined || contentInitialized === true) {     // default: true
        var lastTextAreaEls = document.querySelectorAll("textarea.content[data-editor-mode='update-comment-body']");
        var lastTextAreaText = lastTextAreaEls.length > 0 ? lastTextAreaEls[lastTextAreaEls.length - 1].value : undefined;
        var storedDraftText = localStorage.getItem(location.pathname);
        if (storedDraftText && lastTextAreaText
            && storedDraftText.trim() === lastTextAreaText.trim()) {
            removeCurrentPageTemprarySavedContent();
        } else if (storedDraftText) {
            textarea.value = storedDraftText;
        }
    }
}

function removeCurrentPageTemprarySavedContent() {
    localStorage.removeItem(location.pathname);
    localStorage.removeItem(location.pathname + '-last-comment-update-draft');
}
