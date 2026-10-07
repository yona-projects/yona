// 왼쪽 사이드바(<yona-sidebar>)와 GNB 핀 버튼을 연결한다. 열림 상태의 저장/복원은 컴포넌트가 맡고, 이 스크립트는
// 핀 클릭을 컴포넌트의 toggle()로 전달하고 그 결과(yona-sidebar-toggle 이벤트)를 <html>의 클래스와 aria 속성에 반영한다.
(function () {
    "use strict";

    var root = document.documentElement;

    // 이슈 2단 보기의 슬라이드 iframe 안에서는 사이드바를 만들지 않는다(최상위 창의 것만 쓴다). 컴포넌트 모듈은
    // 지연 실행되므로 그 전에 요소를 지우면 데이터 요청도 일어나지 않는다.
    if (window.parent !== window) {
        document.querySelectorAll("yona-sidebar").forEach(function (el) { el.remove(); });
        return;
    }

    function sync(open) {
        root.classList.toggle("left-sidebar-open", open);
        document.querySelectorAll("[data-sidebar-toggle]").forEach(function (trigger) {
            trigger.setAttribute("aria-expanded", String(open));
        });
    }

    function toggle() {
        var sidebar = document.querySelector("yona-sidebar");
        if (!sidebar) return;
        if (typeof sidebar.toggle === "function") {
            sidebar.toggle();
            return;
        }
        // 컴포넌트 모듈이 아직 정의되기 전의 클릭은 정의된 뒤에 처리한다.
        customElements.whenDefined("yona-sidebar").then(function () { sidebar.toggle(); });
    }

    function triggerOf(event) {
        return event.target && event.target.closest ? event.target.closest("[data-sidebar-toggle]") : null;
    }

    document.addEventListener("click", function (event) {
        if (!triggerOf(event)) return;
        event.preventDefault();
        toggle();
    });

    document.addEventListener("keydown", function (event) {
        if ((event.key !== "Enter" && event.key !== " ") || !triggerOf(event)) return;
        event.preventDefault();
        toggle();
    });

    document.addEventListener("yona-sidebar-toggle", function (event) {
        sync(!!(event.detail && event.detail.open));
    });

    // 저장된 열림 상태로 로드한 직후에는 슬라이드가 재생되지 않아야 하므로, 첫 두 프레임이 지난 뒤에야 전환(left-sidebar-ready)을
    // 켠다. 그 뒤의 열기/닫기만 애니메이션된다.
    requestAnimationFrame(function () {
        requestAnimationFrame(function () { root.classList.add("left-sidebar-ready"); });
    });

    // 첫 페인트 전 스크립트(head)가 정한 상태로 aria를 맞추고, 컴포넌트가 정의되면 실제 상태와 다시 맞춘다.
    sync(root.classList.contains("left-sidebar-open"));
    if (window.customElements) {
        customElements.whenDefined("yona-sidebar").then(function () {
            var sidebar = document.querySelector("yona-sidebar");
            if (sidebar) sync(sidebar.hasAttribute("open"));
        });
    }
}());
