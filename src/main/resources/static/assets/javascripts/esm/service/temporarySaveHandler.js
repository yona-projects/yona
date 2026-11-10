/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

import { register } from "../namespace.js";

function temporarySaveHandler($textarea, contentInitialized) {
    const noticePanel = $(".editor-notice-label");   // 화면 어딘가 임시저장 상태 표시할 곳
    if (!window.draftSavingTimeout) window.draftSavingTimeout = 0;

    // this 대신 editor 컨테이너. this 에 붙여두면 화면 전환 시 handler가 메모리에 중첩됨
    $textarea.on('keyup', () => {
        if ($textarea.val() !== localStorage.getItem(location.pathname)) {
            clearTimeout(window.draftSavingTimeout);

            if ($textarea.val() === "") {
                localStorage.removeItem(location.pathname);
                return;
            }

            noticePanel.children().fadeOut();

            window.draftSavingTimeout = setTimeout(() => {
                if($textarea.data("editorMode") === "update-comment-body") {
                    // FIXME: There are bug when editing comment.
                    // NOW, just make it skipping to store at local storage
                    localStorage.setItem(location.pathname + '-last-comment-update-draft', $textarea.val());
                    return;
                }
                localStorage.setItem(location.pathname, $textarea.val());

                noticePanel.html("<span class=\"saved\">Draft saved</span>");
            }, 5000);
        }
    });

    if (contentInitialized === undefined || contentInitialized === true) {     // default: true
        const lastTextAreaText = $("textarea.content[data-editor-mode='update-comment-body']").last().val();
        const storedDraftText = localStorage.getItem(location.pathname);
        if (storedDraftText && lastTextAreaText
            && storedDraftText.trim() === lastTextAreaText.trim()) {
            removeCurrentPageTemprarySavedContent();
        } else if (storedDraftText) {
            $textarea.val(storedDraftText);
        }
    }
}

function removeCurrentPageTemprarySavedContent() {
    localStorage.removeItem(location.pathname);
    localStorage.removeItem(location.pathname + '-last-comment-update-draft');
}

// 번들(yona-lib.js)에서 전역 함수로 불리던 것이라, 댓글과 글 작성 화면의 클래식 스크립트가 같은 이름으로 부른다
register("temporarySaveHandler", temporarySaveHandler);
register("removeCurrentPageTemprarySavedContent", removeCurrentPageTemprarySavedContent);
