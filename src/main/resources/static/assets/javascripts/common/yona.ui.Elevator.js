/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * 화면 오른쪽 아래에 떠서 문서 맨 위와 맨 아래로 이동시키는 단추 한 쌍이다(이슈와 게시글 보기).
 * v1.6이 쓰던 jQuery 플러그인 `jquery.elevator`(1.0.6)에서 이 화면들이 쓰던 기능만 표준 API로 다시 썼다(D-122 2단계).
 * 요소의 구조와 클래스 이름은 플러그인과 같아서 스타일(`lib/elevator/jquery.elevator.css`)을 그대로 쓴다.
 *
 * 사용: `yona.ui.Elevator.init({shape: "rounded", tooltips: true});`
 *
 * 옵션
 * - `shape`: `square`, `rounded`, 그 밖의 값은 `circle`(기본)
 * - `tooltips`: 참이면 단추 안에 `span.jq-title`로 설명을 넣고, 아니면 `title` 속성에 넣는다
 * - `align`: `"bottom right"`(기본)처럼 공백으로 나눈 위치. `top`, `bottom`, `left`, `right`를 쓴다
 * - `margin`: 문서 끝에서 이만큼(px, 기본 100) 안이면 끝에 닿은 것으로 본다
 *
 * 스크롤 위치에 따라 단추 크기 클래스가 바뀐다. 맨 위에서는 위 단추가 `jq-sml`, 아래 단추가 `jq-big`이고,
 * 맨 아래에서는 반대이며, 그 사이에서는 둘 다 `jq-mid`이다. 이동은 브라우저의 부드러운 스크롤(`scrollTo`의 `behavior: "smooth"`)이고
 * 링크의 기본 동작(`#`으로 이동)은 막는다.
 */
(function (window, document) {
    "use strict";

    const yona = window.yona = window.yona || {};
    yona.ui = yona.ui || {};

    const SIZE_CLASSES = ["jq-sml", "jq-mid", "jq-big"];

    /**
     * 단추 한 개를 만든다.
     *
     * @param {String} sClassName 위(`jq-top`)나 아래(`jq-bottom`)를 가리키는 클래스
     * @param {String} sSymbol 단추 안의 기호
     * @param {String} sTitle 설명 글
     * @param {Boolean} bTooltips 설명을 span으로 넣을지 title 속성으로 넣을지
     * @param {Function} fOnClick 누를 때 부를 함수
     * @return {HTMLAnchorElement}
     */
    function createLink(sClassName, sSymbol, sTitle, bTooltips, fOnClick) {
        const elLink = document.createElement("a");
        elLink.className = sClassName + " jq-mid";
        elLink.setAttribute("href", "#");
        elLink.appendChild(document.createTextNode(sSymbol));

        if (bTooltips) {
            const elTitle = document.createElement("span");
            elTitle.className = "jq-title";
            elTitle.textContent = sTitle;
            elLink.appendChild(elTitle);
        } else {
            elLink.setAttribute("title", sTitle);
        }

        elLink.addEventListener("click", (weEvt) => {
            weEvt.preventDefault();
            fOnClick();
        });
        return elLink;
    }

    function setSize(elLink, sSize) {
        SIZE_CLASSES.forEach((sClass) => {
            elLink.classList.remove(sClass);
        });
        elLink.classList.add(sSize);
    }

    /**
     * 엘리베이터를 문서에 붙인다.
     *
     * @param {Object} [htOptions] `shape`, `tooltips`, `align`, `margin`
     * @return {HTMLElement} 만든 요소(`div.jq-elevator`)
     */
    function init(htOptions) {
        const htOpt = htOptions || {};
        const nMargin = htOpt.margin === undefined ? 100 : htOpt.margin;
        const aAlign = (htOpt.align || "bottom right").split(" ");
        const elDiv = document.createElement("div");

        elDiv.classList.add("jq-elevator");
        if ("ontouchstart" in window || window.navigator.msMaxTouchPoints) {
            elDiv.classList.add("touch");
        }
        ["top", "bottom", "left", "right"].forEach((sPosition) => {
            if (aAlign.indexOf(sPosition) >= 0) {
                elDiv.classList.add("align-" + sPosition);
            }
        });
        elDiv.classList.add(htOpt.shape === "square" || htOpt.shape === "rounded" ? htOpt.shape : "circle");

        const elTop = createLink("jq-top", "▲", "Move to Top", htOpt.tooltips, () => {
            window.scrollTo({"top": 0, "behavior": "smooth"});
        });
        const elBottom = createLink("jq-bottom", "▼", "Move to Bottom", htOpt.tooltips, () => {
            window.scrollTo({"top": document.documentElement.scrollHeight, "behavior": "smooth"});
        });
        elDiv.appendChild(elTop);
        elDiv.appendChild(elBottom);

        function atTop() {
            return window.pageYOffset <= nMargin;
        }

        function atBottom() {
            return window.pageYOffset + window.innerHeight >= document.documentElement.scrollHeight - nMargin;
        }

        function update() {
            if (atTop()) {
                setSize(elTop, "jq-sml");
                setSize(elBottom, "jq-big");
            } else if (atBottom()) {
                setSize(elTop, "jq-big");
                setSize(elBottom, "jq-sml");
            } else {
                setSize(elTop, "jq-mid");
                setSize(elBottom, "jq-mid");
            }
        }

        update();
        document.addEventListener("scroll", update, {"passive": true});
        document.body.appendChild(elDiv);
        return elDiv;
    }

    yona.ui.Elevator = {"init": init};
})(window, document);
