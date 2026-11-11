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

/** 초안을 보관하는 시간(밀리초). 마지막 저장 뒤 이 시간이 지나도 제출되지 않은 초안은 지운다(D-124). */
const DRAFT_TTL = 3600 * 1000;

/** 초안 키별 마지막 저장 시각(밀리초)을 JSON으로 담아 두는 localStorage 키. */
const DRAFT_TIMES_KEY = 'yona.draftTimes';

/** 만료된 초안을 점검하는 간격(밀리초). */
const SWEEP_INTERVAL = 5 * 60 * 1000;

/** 수정 댓글 초안의 키 접두어. 입력란마다 댓글 번호를 붙여 서로 덮어쓰지 않게 한다. */
const UPDATE_DRAFT_KEY = '-last-comment-update-draft';

/** 초안 저장 시각 표를 읽는다. 없거나 깨져 있으면 빈 표이다. */
function readDraftTimes() {
    try {
        return JSON.parse(localStorage.getItem(DRAFT_TIMES_KEY) || '{}') || {};
    } catch (e) {
        return {};
    }
}

/** 초안 저장 시각 표를 저장한다. 비었으면 키 자체를 지운다. */
function writeDraftTimes(times) {
    if (Object.keys(times).length === 0) {
        localStorage.removeItem(DRAFT_TIMES_KEY);
    } else {
        localStorage.setItem(DRAFT_TIMES_KEY, JSON.stringify(times));
    }
}

/** [key] 초안의 저장 시각을 지금으로 적는다. */
function touchDraft(key) {
    const times = readDraftTimes();
    times[key] = Date.now();
    writeDraftTimes(times);
}

/** [key] 초안의 시간 기록을 지운다. */
function forgetDraft(key) {
    const times = readDraftTimes();
    if (key in times) {
        delete times[key];
        writeDraftTimes(times);
    }
}

/**
 * 마지막 저장 뒤 1시간이 지난 초안을 지운다. 페이지를 열 때와 5분마다 부른다.
 * 시간 기록이 없는 초안(이 기능이 생기기 전에 저장된 것)은 그 시점부터 1시간을 센다.
 */
function removeExpiredDrafts() {
    const times = readDraftTimes();
    const now = Date.now();
    let changed = false;
    Object.keys(times).forEach((key) => {
        if (now - times[key] > DRAFT_TTL) {
            localStorage.removeItem(key);
            delete times[key];
            changed = true;
        }
    });
    if (changed) {
        writeDraftTimes(times);
    }
}

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
                forgetDraft(key);
                return;
            }

            noticePanel.children().fadeOut();

            pendingDrafts.set(element, setTimeout(() => {
                pendingDrafts.delete(element);
                localStorage.setItem(key, $input.val());
                touchDraft(key);
                if ($input.data("editorMode") === "update-comment-body") {
                    return;
                }
                noticePanel.html("<span class=\"saved\">" + $yona.escapeHtml(Messages("draft.saved")) + "</span>");
            }, DRAFT_DELAY));
        }
    });

    // 오래 두어 만료된 초안은 되살리지 않고 지운다
    removeExpiredDrafts();
    if (localStorage.getItem(location.pathname) !== null && !(location.pathname in readDraftTimes())) {
        touchDraft(location.pathname);   // 시간 기록이 없는 옛 초안은 지금부터 1시간을 센다
    }

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
    forgetDraft(location.pathname);
    const prefix = location.pathname + UPDATE_DRAFT_KEY;
    localStorage.removeItem(prefix);
    forgetDraft(prefix);
    const keys = [];
    for (let i = 0; i < localStorage.length; i++) {
        const name = localStorage.key(i);
        if (name && name.indexOf(prefix) === 0) {
            keys.push(name);
        }
    }
    keys.forEach((name) => { localStorage.removeItem(name); forgetDraft(name); });
}

// 오래 열어 둔 탭에 남은 초안 타이머를 시스템이 1시간마다 취소하고, 1시간 넘게 제출되지 않은 초안은 5분마다 점검해 지운다(D-124)
setInterval(cancelAllDraftTimers, 3600 * 1000);
setInterval(removeExpiredDrafts, SWEEP_INTERVAL);

// 번들(yona-lib.js)에서 전역 함수로 불리던 것이라, 댓글과 글 작성 화면의 클래식 스크립트가 같은 이름으로 부른다
register("temporarySaveHandler", temporarySaveHandler);
register("removeCurrentPageTemprarySavedContent", removeCurrentPageTemprarySavedContent);
register("cancelAllDraftTimers", cancelAllDraftTimers);
