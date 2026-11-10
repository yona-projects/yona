/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

import { register } from "../namespace.js";

/** 초안 저장을 기다리는 타이머. 입력란(요소)마다 하나씩 둔다. */
const pendingDrafts = new Map();

/** 초안을 기다리는 시간(밀리초). 마지막 입력 뒤 이만큼 지나야 저장한다. */
const DRAFT_DELAY = 5000;

/** 수정 댓글 초안의 키 접두어. 입력란마다 댓글 번호를 붙여 서로 덮어쓰지 않게 한다. */
const UPDATE_DRAFT_KEY = '-last-comment-update-draft';

/**
 * 입력란이 초안을 저장할 localStorage 키를 돌려준다. 새 글과 새 댓글은 현재 경로이고, 수정 댓글은
 * 경로에 접두어와 댓글 번호(`comment-editform-번호`)를 붙인다. 예전에는 수정 댓글이 모두 같은 키라 서로 덮어썼다.
 *
 * @param {jQuery} $textarea 입력란 하나
 * @return {String}
 */
function draftKey($textarea) {
    if ($textarea.data("editorMode") !== "update-comment-body") {
        return location.pathname;
    }
    const formId = $textarea.closest('[id^="comment-editform-"]').attr("id");
    return location.pathname + UPDATE_DRAFT_KEY + (formId ? "-" + formId.replace("comment-editform-", "") : "");
}

/**
 * 입력란에 키를 칠 때마다 마지막 입력 5초 뒤에 초안을 localStorage에 저장한다. 타이머는 입력란마다 따로 둔다
 * (예전에는 페이지에 하나라서 5초 안에 다른 입력란에 치면 앞 입력란의 저장이 사라졌다, D-124).
 *
 * @param {jQuery} $textarea 입력란
 * @param {boolean} [contentInitialized=true] 참이면 저장된 초안으로 입력란을 채운다
 */
function temporarySaveHandler($textarea, contentInitialized) {
    const noticePanel = $(".editor-notice-label");   // 화면 어딘가 임시저장 상태 표시할 곳

    // this 대신 editor 컨테이너. this 에 붙여두면 화면 전환 시 handler가 메모리에 중첩됨
    $textarea.on('keyup', (event) => {
        const $input = $(event.currentTarget);
        const element = event.currentTarget;
        const key = draftKey($input);
        if ($input.val() !== localStorage.getItem(key)) {
            clearTimeout(pendingDrafts.get(element));
            pendingDrafts.delete(element);

            if ($input.val() === "") {
                localStorage.removeItem(key);
                return;
            }

            noticePanel.children().fadeOut();

            pendingDrafts.set(element, setTimeout(() => {
                pendingDrafts.delete(element);
                localStorage.setItem(key, $input.val());
                if ($input.data("editorMode") === "update-comment-body") {
                    return;
                }
                noticePanel.html("<span class=\"saved\">Draft saved</span>");
            }, DRAFT_DELAY));
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

/**
 * 저장을 기다리는 초안 타이머를 모두 취소한다. 댓글을 올릴 때 부르면 제출 뒤에 초안이 다시 저장되어
 * 다음에 열었을 때 되살아나는 일을 막는다.
 */
function cancelAllDraftTimers() {
    pendingDrafts.forEach((timer) => { clearTimeout(timer); });
    pendingDrafts.clear();
}

/** 현재 페이지의 초안을 지운다. 본문 키와 수정 댓글 키(번호가 붙은 것 포함)를 모두 지운다. */
function removeCurrentPageTemprarySavedContent() {
    localStorage.removeItem(location.pathname);
    const prefix = location.pathname + UPDATE_DRAFT_KEY;
    localStorage.removeItem(prefix);
    const keys = [];
    for (let i = 0; i < localStorage.length; i++) {
        const name = localStorage.key(i);
        if (name && name.indexOf(prefix) === 0) {
            keys.push(name);
        }
    }
    keys.forEach((name) => { localStorage.removeItem(name); });
}

// 오래 열어 둔 탭에 남은 초안 타이머를 시스템이 1시간마다 취소한다(D-124)
setInterval(cancelAllDraftTimers, 3600 * 1000);

// 번들(yona-lib.js)에서 전역 함수로 불리던 것이라, 댓글과 글 작성 화면의 클래식 스크립트가 같은 이름으로 부른다
register("temporarySaveHandler", temporarySaveHandler);
register("removeCurrentPageTemprarySavedContent", removeCurrentPageTemprarySavedContent);
register("cancelAllDraftTimers", cancelAllDraftTimers);
