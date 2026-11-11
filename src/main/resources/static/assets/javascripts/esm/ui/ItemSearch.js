/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * 입력란에 친 글자로 목록 항목을 거르는 동작이다(마일스톤 상세의 이슈 거르기, 그룹 홈의 프로젝트 거르기).
 * v1.6의 jQuery 플러그인 `jquery.search.js`와 같은 동작을 표준 API로 다시 썼다.
 *
 * 마크업 계약
 * - 입력란: `data-toggle="item-search"`와 `data-items="항목이름"`
 * - 항목: `data-item="항목이름"`과 `data-value="거를 때 비교할 글"`
 *
 * 동작
 * - 입력란에 처음 초점이 갈 때 연결한다(나중에 그려진 입력란도 같다. 문서에 `focusin`을 한 번만 걸어 둔다).
 * - 키를 뗄 때마다 200ms 뒤에 거른다. 입력이 공백뿐이면 모든 항목을 보여 준다.
 * - 항목의 `data-value`를 소문자로 바꾼 글에 입력한 글(소문자)이 들어 있으면 보이고, 아니면 숨긴다.
 */

import { register } from "../namespace.js";

/** 키를 뗀 뒤 거르기까지 기다리는 시간(ms, v1.6 `$.fn.search.defaults.delay`) */
const DELAY = 200;

/** 이미 연결한 입력란이다(두 번 연결하지 않는다). */
const attached = new WeakSet();

/**
 * 입력한 글에 맞춰 항목을 보이거나 숨긴다.
 *
 * @param {string} itemName `data-item` 값
 * @param {string} filter 소문자로 바꾼 입력
 */
function filterItems(itemName, filter) {
    const items = document.querySelectorAll(`[data-item="${CSS.escape(itemName)}"]`);
    const blank = filter.trim() === "";
    items.forEach((item) => {
        const value = (item.dataset.value || "").toLowerCase();
        item.style.display = blank || value.includes(filter) ? "" : "none";
    });
}

/**
 * 입력란을 연결한다. 이미 연결했으면 아무것도 하지 않는다.
 *
 * @param {HTMLInputElement} input `data-toggle="item-search"` 입력란
 */
function attach(input) {
    if (attached.has(input)) {
        return;
    }
    attached.add(input);
    let timer = null;
    input.addEventListener("keyup", () => {
        clearTimeout(timer);
        const filter = input.value.toLowerCase();
        timer = setTimeout(() => filterItems(input.dataset.items || "", filter), DELAY);
    });
}

document.addEventListener("focusin", (event) => {
    const target = event.target;
    if (target instanceof HTMLElement && target.matches('[data-toggle="item-search"]')) {
        attach(target);
    }
});

register("yona.ui.ItemSearch", { attach, filterItems });
