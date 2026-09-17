/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona Authors & NAVER Corp.
 * https://yona.io
 **/
var lastClicked = "";
var mainWidth = "";
var isLeftMenuHide = false;

// jquery.pageslide.js(lib/, 수정 금지) 플러그인 중 이 파일이 실제로 쓰는 옵션
// 조합(direction:"left", speed:0, modal:true, iframe 기본값)만 정확히 재현한
// 최소 vanilla 대체 구현이다. 원본 플러그인의 "문서 클릭/ESC로 닫기"(non-modal
// 전용) 분기는 이 파일이 항상 modal:true로만 호출하므로 원본에서도 도달하지
// 않는 죽은 경로라 이식하지 않았다 - 이식해도 동작 차이가 없다.
var _pageslide = {
    lastCaller: null
};

// host에 원본과 같은 id="pageslide"를 주면 yona.css의 전역
// `#pageslide { display: none; }` 규칙이 host 자체에 적용돼 shadow 트리 전체가
// 안 보이게 된다(컴포넌트 내부 인라인 스타일과 무관) - 그래서 host에는 그 id를
// 아예 안 주고, 클로저 변수로 싱글턴 엘리먼트를 캐싱한다(document.getElementById 대신).
var _pageslideVueEl = null;

function _isVuePageSlide() {
    return typeof customElements !== 'undefined' && customElements.get('yona-page-slide');
}

function _getPageslideElement() {
    if (_isVuePageSlide()) {
        if (!_pageslideVueEl) {
            _pageslideVueEl = document.createElement('yona-page-slide');
            document.body.appendChild(_pageslideVueEl);
        }
        return _pageslideVueEl;
    }

    var el = document.getElementById("pageslide");
    if (!el) {
        el = document.createElement("div");
        el.id = "pageslide";
        el.style.display = "none";
        document.body.appendChild(el);
    }
    return el;
}

function _isPageslideVisible(el) {
    if (_isVuePageSlide() && el === _pageslideVueEl) {
        return el.isVisible();
    }
    return getComputedStyle(el).display !== "none";
}

function _pageslideOpen(href, direction, speed) {
    var el = _getPageslideElement();

    if (_isVuePageSlide() && el === _pageslideVueEl) {
        el.show(href, direction);
        return;
    }

    var wasHidden = !_isPageslideVisible(el);

    // 원본과 동일: 이전 iframe은 즉시 제거하고, 새 iframe은 300ms 뒤에 채운다.
    var existingIframe = el.querySelector("iframe");
    if (existingIframe) {
        existingIframe.remove();
    }
    setTimeout(function () {
        var iframe = document.createElement("iframe");
        iframe.setAttribute("allowtransparency", "true");
        iframe.setAttribute("frameborder", "0");
        iframe.setAttribute("hspace", "0");
        iframe.style.width = "100%";
        iframe.style.height = "100%";
        iframe.src = href;
        el.appendChild(iframe);
    }, 300);

    el.dataset.pageslideDirection = direction;

    if (wasHidden) {
        var slideWidth = el.getBoundingClientRect().width;
        if (direction === "left") {
            el.style.left = "auto";
            el.style.right = -slideWidth + "px";
        } else {
            el.style.left = -slideWidth + "px";
            el.style.right = "auto";
        }
        el.style.display = "block";
        // speed:0 이므로 애니메이션 없이 즉시 열린 위치로 이동한다
        // (원본 jQuery .animate(props, 0)과 동일한 최종 결과).
        if (direction === "left") {
            el.style.right = "0px";
        } else {
            el.style.left = "0px";
        }
    }
}

function _pageslideClose() {
    if (_isVuePageSlide()) {
        if (!_pageslideVueEl || !_pageslideVueEl.isVisible()) {
            return;
        }
        _pageslideVueEl.hide();
    } else {
        var el = document.getElementById("pageslide");
        if (!el || !_isPageslideVisible(el)) {
            return;
        }
        var direction = el.dataset.pageslideDirection || "left";
        var slideWidth = el.getBoundingClientRect().width;
        if (direction === "left") {
            el.style.right = -slideWidth + "px";
        } else {
            el.style.left = -slideWidth + "px";
        }
        el.style.display = "none";
    }

    // .left-menu 복원은 위젯이 모르는 페이지 고유 관심사라 하이브리드 여부와
    // 무관하게 어댑터가 그대로 소유한다(원본과 동일).
    var leftMenu = document.querySelector(".left-menu");
    if (leftMenu) {
        leftMenu.style.display = "";
    }
}

function _initTwoColumnMode(){
    var twoColumnMode = document.getElementById("two-column-mode");
    var useTwoColumnMode = localStorage.getItem('useTwoColumnMode');
    var titleEls = document.querySelectorAll('.title, .twoColumeModeTarget');

    var projectPageWrap = document.querySelector('.project-page-wrap');
    if (mainWidth === "") {
        var pageWrap = document.querySelector(".page-wrap");
        mainWidth = (projectPageWrap && getComputedStyle(projectPageWrap).width) ||
            (pageWrap && getComputedStyle(pageWrap).width);
    }

    if( isLeftMenuHide ) {
        var leftMenuOnInit = document.querySelector(".left-menu");
        var userInfoBoxOnInit = document.querySelector(".user-info-box");
        if (leftMenuOnInit) { leftMenuOnInit.style.display = "none"; }
        if (userInfoBoxOnInit) { userInfoBoxOnInit.style.display = "none"; }
    }

    $yona.initHoverPopovers("#two-column-mode-checkbox");

    // when to check box click
    document.querySelectorAll('.mass-update-check').forEach(function (el) {
        el.addEventListener('click', function (e) {
            e.stopPropagation();
        });
    });

    if( useTwoColumnMode  === 'true'){
        attachPageSlideEvent(twoColumnMode, titleEls);
        bindFrameLoading();
    } else {
        if (twoColumnMode) {
            twoColumnMode.checked = false;
        }
        document.querySelectorAll('.post-item').forEach(function (el) {
            el.style.cursor = "";
        });
        unbindEvents();
    }

    if (twoColumnMode) {
        twoColumnMode.addEventListener('click', function () {
            if (this.checked) {
                localStorage.setItem('useTwoColumnMode', true);
                attachPageSlideEvent(twoColumnMode, titleEls);
                bindFrameLoading();
            } else {
                localStorage.setItem('useTwoColumnMode', false);
                document.querySelectorAll('.post-item').forEach(function (el) {
                    el.classList.remove('highlightBg');
                    el.style.cursor = "";
                });
                unbindEvents();
            }
        });
    }

    ////////////////////////////

    function attachPageSlideEvent(twoColumnModeEl, titleElements){
        if (twoColumnModeEl) {
            twoColumnModeEl.checked = true;
        }

        titleElements.forEach(function (el) {
            var pageslideHandler = function (e) {
                e.preventDefault();
                e.stopPropagation();
                var href = el.getAttribute('href');
                var pageslideEl = _getPageslideElement();
                if (_isPageslideVisible(pageslideEl) && _pageslide.lastCaller === el) {
                    // 같은 요소를 두 번째로 클릭하면 토글해서 닫는다
                    _pageslideClose();
                } else {
                    _pageslideOpen(href, "left", 0);
                    _pageslide.lastCaller = el;
                }
            };
            el.__pageslideClickHandler = pageslideHandler;
            el.addEventListener('click', pageslideHandler);
        });

        document.querySelectorAll('.post-item').forEach(function (el) {
            el.style.cursor = "pointer";
        });

        titleElements.forEach(function (el) {
            var changeUrlHandler = function (e) {
                if (!history.state) {
                    window.history.pushState({ startPath: location.pathname }, el.textContent, el.getAttribute("href"));
                } else {
                    window.history.replaceState(history.state, el.textContent, el.getAttribute("href"));
                }
            };
            el.__changeUrlClickHandler = changeUrlHandler;
            el.addEventListener('click', changeUrlHandler);
        });
    }

    function unbindEvents() {
        titleEls.forEach(function (el) {
            if (el.__pageslideClickHandler) {
                el.removeEventListener('click', el.__pageslideClickHandler);
                el.__pageslideClickHandler = null;
            }
            if (el.__iframeLoadingClickHandler) {
                el.removeEventListener('click', el.__iframeLoadingClickHandler);
                el.__iframeLoadingClickHandler = null;
            }
            if (el.__changeUrlClickHandler) {
                el.removeEventListener('click', el.__changeUrlClickHandler);
                el.__changeUrlClickHandler = null;
            }
        });
        _pageslideClose();
        var userInfoBox = document.querySelector(".user-info-box");
        if (userInfoBox) {
            userInfoBox.style.display = "";
        }
    }

    function bindFrameLoading() {
        titleEls.forEach(function (el) {
            var iframeLoadingHandler = function (e) {
                document.querySelectorAll('.post-item').forEach(function (item) {
                    item.classList.remove('highlightBg');
                });
                var postItem = el.closest('.post-item');
                if (postItem) {
                    postItem.classList.add('highlightBg');
                }

                // Vue 위젯이 활성화된 경우 host에 id="pageslide"를 안 주므로 getElementById로는
                // 못 찾는다(위 _getPageslideElement 주석 참고) - 캐시된 클로저 변수를 쓴다.
                var pageslideEl = _isVuePageSlide() ? _pageslideVueEl : document.getElementById('pageslide');
                var userInfoBox = document.querySelector(".user-info-box");
                var leftMenu = document.querySelector(".left-menu");
                if (pageslideEl && _isPageslideVisible(pageslideEl)) {
                    if (leftMenu) { leftMenu.style.display = "none"; }
                    if (userInfoBox) { userInfoBox.style.display = "none"; }
                    isLeftMenuHide = true;
                } else {
                    if (userInfoBox) { userInfoBox.style.display = ""; }
                }
                setTimeout(function () {
                    // 원본 $('#pageslide > iframe').ready(fn)은 실제로는 iframe의
                    // 로드를 기다리지 않는다 - jQuery의 .ready()는 매칭된 요소와
                    // 무관하게 항상 "메인 문서" ready 상태에만 반응하므로(문서는
                    // 이 클릭이 발생하는 시점엔 이미 ready), fn은 사실상 즉시
                    // 실행된다. iframe load 이벤트를 기다리도록 "개선"하면 새
                    // 동작을 추가하는 것이라 원본의 이 quirk를 그대로 재현한다.
                    NProgress.done();
                }, 100);
            };
            el.__iframeLoadingClickHandler = iframeLoadingHandler;
            el.addEventListener('click', iframeLoadingHandler);
        });
    }
}
