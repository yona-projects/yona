/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * `data-submit-once` 속성이 있는 폼은 제출하는 순간 제출 단추를 잠가, 같은 요청이 두 번 서버로 가지 않게 한다.
 * OAuth 앱과 개인용 토큰을 만드는 폼에서 단추를 두 번 누르거나 새로 고침으로 다시 보내면 같은 것이 중복으로 만들어졌다(oauth-F3, usersec-F16).
 * 서버에서 막는 방식은 이름이 같은 앱을 허용하는 정책과 충돌하므로 화면에서만 막는다.
 *
 * 단추는 제출 이벤트가 끝난 다음 순서에 잠근다. 제출 중에 바로 잠그면 브라우저가 단추의 `name`과 `value`를 보내지 않을 수 있기 때문이다.
 * 제출이 검증에 걸려 취소되면(`defaultPrevented`) 잠그지 않는다. 쪽을 떠나기 전에 뒤로 가기로 돌아오면 브라우저가 폼 상태를 되살리므로 단추가 잠긴 채 남지 않게
 * `pageshow`에서 풀어 준다.
 */
document.addEventListener("submit", (event) => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement) || !form.hasAttribute("data-submit-once")) return;
    setTimeout(() => {
        if (event.defaultPrevented) return;
        form.querySelectorAll("[type=submit]").forEach((button) => { button.disabled = true; });
    }, 0);
});

window.addEventListener("pageshow", (event) => {
    if (!event.persisted) return;
    document.querySelectorAll("form[data-submit-once] [type=submit]").forEach((button) => { button.disabled = false; });
});
