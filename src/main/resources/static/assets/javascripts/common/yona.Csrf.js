/**
 * 요청 위조 방지(CSRF) 토큰을 상태를 바꾸는 요청에 싣는다(R-02).
 *
 * 서버는 쿠키 `XSRF-TOKEN`에 토큰을 심고, 상태를 바꾸는 요청(POST, PUT, PATCH, DELETE)이 같은 값을 헤더 `X-XSRF-TOKEN`이나 인자 `_csrf`로 보내기를 요구한다.
 * 이 스크립트는 같은 사이트로 가는 요청에만 토큰을 싣는다.
 * - XMLHttpRequest(jQuery의 Ajax 포함)와 fetch: 헤더로 보낸다
 * - 폼 제출: 숨은 `_csrf` 입력을 더한다. 스크립트가 `form.submit()`을 부르는 경우도 같다
 * v1.6에는 요청 위조 방지가 없었다.
 */
(function () {
    "use strict";

    const SAFE = /^(GET|HEAD|OPTIONS|TRACE)$/i;

    function token() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : "";
    }

    function sameOrigin(url) {
        try {
            return new URL(url, window.location.href).origin === window.location.origin;
        } catch (e) {
            return true;
        }
    }

    // XMLHttpRequest
    const originalOpen = XMLHttpRequest.prototype.open;
    const originalSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (method, url) {
        this.__yonaCsrf = !SAFE.test(method || "GET") && sameOrigin(String(url));
        return originalOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function () {
        const value = token();
        if (this.__yonaCsrf && value) {
            try { this.setRequestHeader("X-XSRF-TOKEN", value); } catch (e) { /* 이미 보낸 요청이면 무시한다 */ }
        }
        return originalSend.apply(this, arguments);
    };

    // fetch
    if (window.fetch) {
        const originalFetch = window.fetch;
        window.fetch = function (input, init) {
            const method = (init && init.method) || (input && input.method) || "GET";
            const url = typeof input === "string" ? input : (input && input.url) || "";
            const value = token();
            if (!SAFE.test(method) && sameOrigin(url) && value) {
                init = init || {};
                const headers = new Headers(init.headers || (input && input.headers) || {});
                if (!headers.has("X-XSRF-TOKEN")) headers.set("X-XSRF-TOKEN", value);
                init.headers = headers;
            }
            return originalFetch.call(this, input, init);
        };
    }

    // 폼
    function addTokenTo(form) {
        if (!form || !form.tagName || form.tagName.toLowerCase() !== "form") return;
        const method = form.getAttribute("method") || "get";
        if (SAFE.test(method) || !sameOrigin(form.getAttribute("action") || window.location.href)) return;
        const value = token();
        if (!value) return;
        let input = form.querySelector('input[name="_csrf"]');
        if (!input) {
            input = document.createElement("input");
            input.type = "hidden";
            input.name = "_csrf";
            form.appendChild(input);
        }
        input.value = value;
    }

    document.addEventListener("submit", (event) => { addTokenTo(event.target); }, true);

    const originalSubmit = HTMLFormElement.prototype.submit;
    HTMLFormElement.prototype.submit = function () {
        addTokenTo(this);
        return originalSubmit.apply(this, arguments);
    };
})();
