/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

function yonaDraftKey(element) {
    element = element && element.jquery ? element[0] : element;
    var wrapper = element && element.closest("[data-draft-key]");
    return wrapper ? wrapper.getAttribute("data-draft-key") : location.pathname;
}

function temporarySaveHandler($textarea, contentInitialized) {
    var textarea = $textarea.jquery ? $textarea[0] : $textarea;
    var key = yonaDraftKey(textarea);
    var root = textarea.closest("[data-draft-key]") || document;
    var noticePanel = root.querySelector(".editor-notice-label");
    var storageKey = key + (textarea.dataset.editorMode === "update-comment-body" ? "-last-comment-update-draft" : "");
    var timer;
    var pending = false;
    var disposed = false;

    if (contentInitialized === undefined || contentInitialized === true) {
        var lastTextAreaEls = root.querySelectorAll("textarea.content[data-editor-mode='update-comment-body']");
        var lastTextAreaText = lastTextAreaEls.length > 0 ? lastTextAreaEls[lastTextAreaEls.length - 1].value : undefined;
        var storedDraftText = localStorage.getItem(key);
        if (storedDraftText && lastTextAreaText && storedDraftText.trim() === lastTextAreaText.trim()) {
            removeCurrentPageTemprarySavedContent(textarea);
        } else if (storedDraftText) {
            var markdownEditor = textarea.closest("yona-markdown-editor");
            if (markdownEditor) markdownEditor.value = storedDraftText;
            else textarea.value = storedDraftText;
        }
    }
    var lastValue = textarea.value;

    function save() {
        if (!pending) return;
        pending = false;
        lastValue = textarea.value;
        if (lastValue === "") {
            localStorage.removeItem(storageKey);
        } else {
            localStorage.setItem(storageKey, lastValue);
            if (noticePanel && storageKey === key) {
                noticePanel.innerHTML = "<span class=\"saved\">Draft saved</span>";
            }
        }
    }

    function changed() {
        if (textarea.value === lastValue) return;
        lastValue = textarea.value;
        clearTimeout(timer);
        pending = true;
        if (lastValue === "") {
            save();
            return;
        }
        if (noticePanel) {
            Array.prototype.forEach.call(noticePanel.children, function(child) {
                child.style.display = "none";
            });
        }
        timer = setTimeout(save, 5000);
    }

    function cleared(event) {
        if (event.detail !== key) return;
        clearTimeout(timer);
        pending = false;
        lastValue = textarea.value;
    }

    textarea.addEventListener("input", changed);
    textarea.addEventListener("keyup", changed);
    document.addEventListener("yona:draft-cleared", cleared);
    return function() {
        if (disposed) return;
        disposed = true;
        clearTimeout(timer);
        save();
        textarea.removeEventListener("input", changed);
        textarea.removeEventListener("keyup", changed);
        document.removeEventListener("yona:draft-cleared", cleared);
    };
}

function removeCurrentPageTemprarySavedContent(element) {
    var key = yonaDraftKey(element);
    localStorage.removeItem(key);
    localStorage.removeItem(key + "-last-comment-update-draft");
    document.dispatchEvent(new CustomEvent("yona:draft-cleared", {detail: key}));
}

document.addEventListener("click", function(event) {
    var button = event.target.closest(".editor-clear-temporary-button");
    if (!button) return;
    var editor = button.closest('[data-toggle="markdown-editor"]');
    var textarea = editor && editor.querySelector("textarea");
    var markdownEditor = textarea && textarea.closest("yona-markdown-editor");
    if (markdownEditor) markdownEditor.value = "";
    else if (textarea) textarea.value = "";
    removeCurrentPageTemprarySavedContent(button);
    window.location.reload();
});
