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
 * 이미 불러온 라이브러리를 판별해 그 `<script src>` 태그를 응답에서 걷어내는 규칙이다. 본문을 바꾸면 htmx가 응답의 스크립트를 다시 실행하는데,
 * 화면마다 라이브러리를 싣는 조각이 있어 그대로 두면 라이브러리가 두 번 실행된다. 주소 조각(`path`)으로 태그를 찾고, 전역 이름으로 이미 있는지 본다.
 */
const LOADED_LIBRARIES: ReadonlyArray<{ path: RegExp; loaded: () => boolean }> = [
  { path: /\/lib\/select2\//, loaded: () => typeof (window as unknown as { jQuery?: { fn?: Record<string, unknown> } }).jQuery?.fn?.["select2"] === "function" },
  { path: /\/lib\/vanilla-calendar-pro\//, loaded: () => "VanillaCalendarPro" in window },
  { path: /\/esm\/ui\/Select2\.js/, loaded: () => typeof (window as unknown as { yona?: { ui?: Record<string, unknown> } }).yona?.ui?.["Select2"] === "function" },
  { path: /\/esm\/ui\/Calendar\.js/, loaded: () => typeof (window as unknown as { yona?: { ui?: Record<string, unknown> } }).yona?.ui?.["Calendar"] === "function" },
];

function dropLoadedLibraries(response: string): string {
  let result = response;
  for (const rule of LOADED_LIBRARIES) {
    if (!rule.loaded()) continue;
    result = result.replace(/<script\b[^>]*\bsrc="([^"]*)"[^>]*>\s*<\/script>/gi, (tag, src: string) => (rule.path.test(src) ? "" : tag));
  }
  return result;
}

/** 이미지 보기(viewer)를 붙였다는 표시이다. 같은 영역에 두 번 붙이지 않는다. */
const VIEWER_BOUND = "yonaViewerBound";

/**
 * 본문 교체로 들어온 마크다운 영역(`.markdown-wrap`)에 이미지 보기를 붙인다. 레이아웃은 이 일을 문서 준비 때 한 번만 하므로,
 * 교체로 새로 들어온 영역은 여기서 같은 규칙(이미지 위에서 손 모양, `viewer()`)으로 붙인다. jQuery나 viewer 플러그인이 없으면 하지 않는다.
 */
function bindMarkdownViewer(): void {
  const jq = (window as unknown as { jQuery?: (el: Element) => { viewer?: () => void } }).jQuery;
  if (typeof jq !== "function") return;
  document.querySelectorAll<HTMLElement>(".shell-main .markdown-wrap").forEach((area) => {
    if (area.dataset[VIEWER_BOUND]) return;
    area.dataset[VIEWER_BOUND] = "1";
    const wrapped = jq(area);
    if (typeof wrapped.viewer === "function") wrapped.viewer();
  });
}

/** 위젯 모듈(`yona.ui.*`)이 노출하는 초기화 함수의 모양이다. */
type UiWidget = (element: Element, options?: unknown) => unknown;

/**
 * 본문 교체로 들어온 select2와 달력 입력란을 초기화한다. 두 모듈은 문서가 준비될 때 한 번만 모든 입력란을 훑어 초기화하므로,
 * 교체로 새로 들어온 입력란은 여기서 같은 규칙(`data-toggle="select2"`, `data-toggle="calendar"`)으로 초기화한다.
 * 교체한 본문 안의 입력란만 대상이므로 이미 초기화한 입력란을 다시 만지지 않는다.
 */
function bindWidgets(): void {
  const ui = (window as unknown as { yona?: { ui?: Record<string, UiWidget | undefined> } }).yona?.ui;
  if (!ui) return;
  const jq = (window as unknown as { jQuery?: (el: Element) => { data(): unknown } }).jQuery;
  document.querySelectorAll(".shell-main [data-toggle=\"select2\"]").forEach((el) => ui["Select2"]?.(el));
  document.querySelectorAll(".shell-main [data-toggle=\"calendar\"]").forEach((el) => ui["Calendar"]?.(el, jq ? jq(el).data() : undefined));
}

/** 사이드바 이동을 켜고 본문이 바뀔 때마다 다시 판정하며 문서 제목도 따라 바꾼다. */
export function initNavigation(): void {
  let shellSwapped = false;
  enable();
  document.addEventListener("htmx:afterSettle", () => {
    enable();
    if (!shellSwapped) return;
    shellSwapped = false;
    bindMarkdownViewer();
    bindWidgets();
  });
  document.addEventListener("htmx:beforeSwap", (event) => {
    const detail = (event as CustomEvent<{ shouldSwap?: boolean; serverResponse?: unknown; target?: Element }>).detail;
    if (detail.shouldSwap === false || !(detail.target instanceof Element) || !detail.target.classList.contains("shell-main")) return;
    runPageLeave();
    shellSwapped = true;
    applyTitle(detail.serverResponse);
    if (typeof detail.serverResponse === "string") detail.serverResponse = dropLoadedLibraries(detail.serverResponse);
  });
}
