/**
 * 셸 사이드바 이동을 htmx로 켠다(D-136, 화면 단위 옵트인).
 *
 * 사이드바(`nav.shell-sidebar`)의 `hx-target`, `hx-select`, `hx-select-oob`, `hx-swap`은 템플릿이 선언한다. 이 모듈은 **지금 화면이
 * swap-safe일 때만** 사이드바에 `hx-boost`를 켠다. swap-safe인 화면은 본문(`.shell-main`) 안에 `data-swap-safe` 표지를 둔다. 표지가
 * 없는 화면(문서 수준 핸들러나 타이머를 쌓는 옛 스크립트가 있는 화면)에서는 일반 이동이고, 표지가 없는 화면으로 가는 응답도
 * `yona.Htmx.js`가 일반 이동으로 물러나게 한다.
 *
 * 본문을 바꾸면 사이드바도 새로 그려지므로(활성 항목 갱신) 교체가 끝날 때마다 다시 켠다.
 */

/** swap-safe 표지이다. 본문 안에 이 속성을 가진 요소가 있으면 그 화면은 본문 교체가 안전하다. */
import { runPageLeave } from "../core/page.js";

const SWAP_SAFE_SELECTOR = ".shell-main [data-swap-safe]";
const SIDEBAR_SELECTOR = "nav.shell-sidebar";

/** 지금 화면이 swap-safe이면 사이드바 이동을 켜고 아니면 끈다. htmx가 없으면 아무 일도 하지 않는다. */
function enable(): void {
  const htmx = window.htmx;
  const sidebar = document.querySelector(SIDEBAR_SELECTOR);
  if (!htmx || !sidebar) return;
  if (document.querySelector(SWAP_SAFE_SELECTOR)) sidebar.setAttribute("hx-boost", "true");
  else sidebar.removeAttribute("hx-boost");
  htmx.process(sidebar);
}

/**
 * 응답의 `<title>`을 문서 제목으로 옮긴다. `hx-select`로 일부만 고르면 htmx가 응답의 제목을 쓰지 않으므로 직접 갱신한다.
 * 제목 안의 HTML 문자(`&amp;` 등)는 풀어서 쓴다.
 */
function applyTitle(response: unknown): void {
  if (typeof response !== "string") return;
  const match = /<title[^>]*>([\s\S]*?)<\/title>/i.exec(response);
  if (!match || match[1] === undefined) return;
  const holder = document.createElement("textarea");
  holder.innerHTML = match[1];
  document.title = holder.value.trim();
}

/**
 * 응답에서, 이미 불러온 라이브러리의 `<script src>`를 걷어낸다. 본문을 바꾸면 htmx가 응답의 스크립트를 다시 실행하는데,
 * 화면마다 select2 같은 라이브러리를 싣는 조각(`common/select2`)이 있어 그대로 두면 라이브러리가 두 번 실행된다.
 * 지금은 select2만 다룬다(`jQuery.fn.select2`가 있으면 select2 라이브러리 태그를 뺀다).
 */
function dropLoadedLibraries(response: string): string {
  const jq = (window as unknown as { jQuery?: { fn?: Record<string, unknown> } }).jQuery;
  if (!jq || !jq.fn || typeof jq.fn["select2"] !== "function") return response;
  return response.replace(/<script\b[^>]*\bsrc="[^"]*\/lib\/select2\/[^"]*"[^>]*>\s*<\/script>/gi, "");
}

/** 사이드바 이동을 켜고 본문이 바뀔 때마다 다시 판정하며 문서 제목도 따라 바꾼다. */
export function initNavigation(): void {
  enable();
  document.addEventListener("htmx:afterSettle", enable);
  document.addEventListener("htmx:beforeSwap", (event) => {
    const detail = (event as CustomEvent<{ shouldSwap?: boolean; serverResponse?: unknown; target?: Element }>).detail;
    if (detail.shouldSwap === false || !(detail.target instanceof Element) || !detail.target.classList.contains("shell-main")) return;
    runPageLeave();
    applyTitle(detail.serverResponse);
    if (typeof detail.serverResponse === "string") detail.serverResponse = dropLoadedLibraries(detail.serverResponse);
  });
}
