/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

// P3-66: 점진적 jQuery 제거 작업 - lib/elevator/jquery.elevator.js(jQuery 플러그인, "맨 위로/맨
// 아래로 스크롤" 버튼)를 순수 vanilla JS로 대체. board/view.html·issue/view.html이 실제로
// 사용하는 옵션 범위(shape/glass/tooltips)만 재현하며, navigation/item_top/item_bottom 등
// 두 화면 모두 넘기지 않는(=기본값만 쓰는) 옵션은 다루지 않는다. jquery.elevator.css는 순수
// 스타일이라 그대로 재사용하므로, 여기서 생성하는 DOM/클래스명은 원본 플러그인과 동일하게
// 맞춘다.
(function () {
    'use strict';

    var CLASS_DIV = 'jq-elevator';
    var CLASS_TOUCH = 'touch';
    var CLASS_BIG = 'jq-big';
    var CLASS_MIDDLE = 'jq-mid';
    var CLASS_SMALL = 'jq-sml';
    var CLASS_TITLE = 'jq-title';

    var DEFAULTS = {
        align: 'bottom right',
        shape: 'rounded',
        glass: false,
        tooltips: false,
        margin: 100
    };

    function isTouchDevice() {
        return ('ontouchstart' in window) || !!navigator.msMaxTouchPoints;
    }

    function atTop(margin) {
        return window.scrollY <= margin;
    }

    function atBottom(margin) {
        return (window.scrollY + window.innerHeight) >= (document.documentElement.scrollHeight - margin);
    }

    function setSizeClass(el, className) {
        el.classList.remove(CLASS_BIG, CLASS_MIDDLE, CLASS_SMALL);
        el.classList.add(className);
    }

    function createLink(className, symbol, title, useTooltip) {
        var a = document.createElement('a');
        a.className = className + ' ' + CLASS_MIDDLE;
        a.href = '#';
        a.textContent = symbol;

        if (useTooltip) {
            var span = document.createElement('span');
            span.className = CLASS_TITLE;
            span.textContent = title;
            a.appendChild(span);
        } else {
            a.title = title;
        }

        return a;
    }

    /**
     * "맨 위로/맨 아래로 스크롤" 버튼을 document.body에 append하고 스크롤 상태에 따른 크기
     * 전환 및 클릭 시 스크롤 동작을 연결한다.
     *
     * @param {Object} [options]
     * @param {String} [options.align='bottom right'] 공백으로 구분된 정렬 토큰들 - 각각
     *        align-<token> 클래스로 컨테이너에 추가된다.
     * @param {String} [options.shape='rounded'] 컨테이너에 추가할 shape 클래스명.
     * @param {Boolean} [options.glass=false] true면 컨테이너에 glass 클래스를 추가한다.
     * @param {Boolean} [options.tooltips=false] true면 각 링크 안에 jq-title span으로
     *        타이틀을 넣고, false면 링크 자체의 title 속성으로 넣는다.
     * @param {Number} [options.margin=100] atTop/atBottom 판정에 쓰는 margin(px).
     * @returns {{destroy: Function}} 리스너를 해제할 수 있는 destroy 함수를 담은 핸들.
     */
    function createScrollElevator(options) {
        var opts = Object.assign({}, DEFAULTS, options || {});

        var container = document.createElement('div');
        container.className = CLASS_DIV;

        opts.align.split(' ').forEach(function (token) {
            if (token) {
                container.classList.add('align-' + token);
            }
        });

        container.classList.add(opts.shape);

        if (opts.glass) {
            container.classList.add('glass');
        }

        if (isTouchDevice()) {
            container.classList.add(CLASS_TOUCH);
        }

        var topLink = createLink('jq-top', '▲', 'Move to Top', opts.tooltips);
        var bottomLink = createLink('jq-bottom', '▼', 'Move to Bottom', opts.tooltips);

        container.appendChild(topLink);
        container.appendChild(bottomLink);
        document.body.appendChild(container);

        function refreshSizeClasses() {
            if (atTop(opts.margin)) {
                setSizeClass(topLink, CLASS_SMALL);
                setSizeClass(bottomLink, CLASS_BIG);
            } else if (atBottom(opts.margin)) {
                setSizeClass(topLink, CLASS_BIG);
                setSizeClass(bottomLink, CLASS_SMALL);
            } else {
                setSizeClass(topLink, CLASS_MIDDLE);
                setSizeClass(bottomLink, CLASS_MIDDLE);
            }
        }

        function onTopClick(e) {
            e.preventDefault();
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }

        function onBottomClick(e) {
            e.preventDefault();
            window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'smooth' });
        }

        topLink.addEventListener('click', onTopClick);
        bottomLink.addEventListener('click', onBottomClick);
        // 원본 플러그인과 동일하게 document(window가 아님)에 scroll 리스너를 붙인다.
        document.addEventListener('scroll', refreshSizeClasses);

        refreshSizeClasses();

        return {
            destroy: function () {
                topLink.removeEventListener('click', onTopClick);
                bottomLink.removeEventListener('click', onBottomClick);
                document.removeEventListener('scroll', refreshSizeClasses);
                if (container.parentNode) {
                    container.parentNode.removeChild(container);
                }
            }
        };
    }

    window.yona = window.yona || {};
    window.yona.createScrollElevator = createScrollElevator;
})();
