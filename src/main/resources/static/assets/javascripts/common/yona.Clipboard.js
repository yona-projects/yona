/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * 글을 클립보드에 복사하는 도우미이다(JS 라이브러리 정리, `decisions.md` D-91, A-394).
 *
 * 예전에는 브라우저가 지원하지 않으면 Flash 기반 복사 플러그인으로 넘어갔으나 Flash는 모든 브라우저에서 사라졌으므로 쓰지 않는다.
 * 보안 연결(https 또는 localhost)이면 비동기 클립보드 API(`navigator.clipboard.writeText`)를 쓰고, 사내망의 http처럼 보안 연결이 아니거나
 * API가 거절하면 숨은 입력 칸에 글을 넣고 `document.execCommand("copy")`로 복사한다(이 방식은 사용자의 클릭 안에서만 동작한다).
 *
 * 사용: `yona.Clipboard.copy("글").then(function (copied) { if (copied) { ... } });`
 */
(function (window, document) {
    "use strict";

    var yona = window.yona = window.yona || {};

    /**
     * 보이지 않는 입력 칸을 거쳐 `execCommand("copy")`로 복사한다.
     *
     * @param {string} sText 복사할 글
     * @return {boolean} 복사했으면 true
     */
    function legacyCopy(sText) {
        var elInput = document.createElement("textarea");
        var elActive = document.activeElement;
        var bCopied = false;

        elInput.value = sText;
        elInput.setAttribute("readonly", "");
        elInput.style.cssText = "position:fixed;top:-1000px;left:0;opacity:0;";
        document.body.appendChild(elInput);
        elInput.select();
        try {
            bCopied = document.execCommand("copy");
        } catch (e) {
            bCopied = false;
        }
        document.body.removeChild(elInput);
        if (elActive && elActive.focus) {
            elActive.focus();
        }
        return bCopied;
    }

    /**
     * 글을 클립보드에 복사한다.
     *
     * @param {string} sText 복사할 글
     * @return {Promise<boolean>} 복사했으면 true로 이행한다(실패해도 거절하지 않고 false로 이행한다)
     */
    function copy(sText) {
        var sValue = sText == null ? "" : String(sText);

        if (window.navigator.clipboard && window.isSecureContext) {
            return window.navigator.clipboard.writeText(sValue).then(function () {
                return true;
            }, function () {
                // 권한이 없거나 문서가 포커스를 잃었으면 대체 수단으로 한 번 더 시도한다
                return legacyCopy(sValue);
            });
        }
        return Promise.resolve(legacyCopy(sValue));
    }

    yona.Clipboard = {"copy": copy};
})(window, document);
