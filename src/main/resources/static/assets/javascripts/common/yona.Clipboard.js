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
 *
 * Bootstrap 모달처럼 포커스를 가두는 곳(모달 밖으로 포커스가 가면 모달로 되돌린다) 안의 단추에서 복사하려면 두 번째 인자로 그 모달 요소를 넘긴다.
 * 대체 경로의 숨은 입력 칸이 모달 밖에 생기면 선택하는 순간 포커스를 빼앗겨 복사가 비어 버린다(시험 `clipboard.test.mjs`가 재현한다).
 * 이메일 목록 복사(이슈 보기의 추천한 사람 모달)가 이렇게 쓴다. 표준 API 경로는 입력 칸을 만들지 않아 이 인자가 필요 없다.
 */
(function (window, document) {
    "use strict";

    const yona = window.yona = window.yona || {};

    /**
     * 보이지 않는 입력 칸을 거쳐 `execCommand("copy")`로 복사한다.
     *
     * @param {string} sText 복사할 글
     * @param {Element} [elContainer] 입력 칸을 붙일 요소(포커스를 가두는 모달 안에서 부를 때 그 모달). 없으면 `document.body`
     * @return {boolean} 복사했으면 true
     */
    function legacyCopy(sText, elContainer) {
        const elInput = document.createElement("textarea");
        const elActive = document.activeElement;
        let bCopied = false;

        elInput.value = sText;
        elInput.setAttribute("readonly", "");
        elInput.style.cssText = "position:fixed;top:-1000px;left:0;opacity:0;";
        const elParent = elContainer || document.body;
        elParent.appendChild(elInput);
        elInput.select();
        try {
            bCopied = document.execCommand("copy");
        } catch (e) {
            bCopied = false;
        }
        elParent.removeChild(elInput);
        if (elActive && elActive.focus) {
            elActive.focus();
        }
        return bCopied;
    }

    /**
     * 글을 클립보드에 복사한다.
     *
     * @param {string} sText 복사할 글
     * @param {Element} [elContainer] 대체 경로의 입력 칸을 붙일 요소(포커스를 가두는 모달 안에서 부를 때 그 모달)
     * @return {Promise<boolean>} 복사했으면 true로 이행한다(실패해도 거절하지 않고 false로 이행한다)
     */
    function copy(sText, elContainer) {
        const sValue = sText == null ? "" : String(sText);

        if (window.navigator.clipboard && window.isSecureContext) {
            return window.navigator.clipboard.writeText(sValue).then(() => {
                return true;
            }, () => {
                // 권한이 없거나 문서가 포커스를 잃었으면 대체 수단으로 한 번 더 시도한다
                return legacyCopy(sValue, elContainer);
            });
        }
        return Promise.resolve(legacyCopy(sValue, elContainer));
    }

    yona.Clipboard = {"copy": copy};
})(window, document);
