/**
 * 햄버거로 여는 바로가기 패널(즐겨찾기, 프로젝트, 최근 읽은 이슈)을 맥락 사이드바 안으로 옮긴다(sidebar 브랜치).
 *
 * v1.6의 왼쪽 사이드바(`.pin`)를 잇는 패널(`#mySidenav`)은 지금까지 화면 왼쪽에 사이드바를 덮고 튀어나왔다. 이 모듈은
 * 맥락 사이드바가 있는 화면에서 그 내용(`.right-menu`)을 사이드바의 `[data-shell-shortcuts]` 자리로 옮기고 덮는 패널은 치운다.
 * 내용 요소를 새로 만들지 않고 그대로 옮기므로 `Usermenu.js`가 붙인 핸들러와 목록 읽기가 그대로 동작한다.
 *
 * - 사이드바가 없는 화면(프로필, 새 프로젝트 등)에서는 아무것도 하지 않아 덮는 패널이 그대로 쓰인다.
 * - 사이드바가 htmx로 교체되면(`hx-select-oob`) 새로 그려진 빈 자리에 옮겨 둔 내용을 다시 붙인다.
 * - 좁은 폭(992px 미만)에서는 사이드바가 서랍이므로, 상단 바의 햄버거가 그 서랍을 연다(바로가기가 서랍 안에 있다).
 */

/** 바로가기를 담을 사이드바 안의 자리이다. */
const HOST_SELECTOR = "[data-shell-shortcuts]";
/** 덮는 패널과 그 안의 내용이다(`common/usermenu.html`). */
const OVERLAY_SELECTOR = "#mySidenav";
const MENU_SELECTOR = ".right-menu";
/** 상단 바의 햄버거이다. */
const HAMBURGER_SELECTOR = "[data-shell-switcher]";
const SIDEBAR_SELECTOR = "nav.shell-sidebar";
/** 서랍으로 바뀌는 폭이다. Bootstrap 기본 `lg` 미만과 같다. */
const NARROW_QUERY = "(max-width: 991.98px)";

interface OffcanvasStatic {
  getOrCreateInstance(element: Element): { toggle(): void };
}

/** 옮겨 둔 내용이다. 사이드바가 교체되어 자리가 새로 그려지면 이 요소를 다시 붙인다. */
let kept: HTMLElement | null = null;

/**
 * 내용을 사이드바 자리에 붙인다. 이미 붙어 있으면 아무것도 하지 않는다.
 *
 * @returns 사이드바 자리에 내용이 있으면 `true`이다. 사이드바가 없거나 옮길 내용이 없으면 `false`이다.
 */
function adopt(): boolean {
  const host = document.querySelector<HTMLElement>(HOST_SELECTOR);
  if (!host) return false;
  if (host.querySelector(MENU_SELECTOR)) return true;
  const menu = kept ?? document.querySelector<HTMLElement>(`${OVERLAY_SELECTOR} ${MENU_SELECTOR}`);
  if (!menu) return false;
  host.appendChild(menu);
  kept = menu;
  document.querySelector(OVERLAY_SELECTOR)?.remove();
  return true;
}

/** 좁은 폭에서 햄버거 클릭을 사이드바 서랍 열기로 바꾼다. 덮는 패널을 여는 옛 핸들러로는 넘기지 않는다. */
function onHamburgerClick(event: Event): void {
  const target = event.target;
  if (!(target instanceof Element) || !target.closest(HAMBURGER_SELECTOR)) return;
  if (!document.querySelector(HOST_SELECTOR) || !window.matchMedia(NARROW_QUERY).matches) return;
  const sidebar = document.querySelector(SIDEBAR_SELECTOR);
  const offcanvas = (window as unknown as { bootstrap?: { Offcanvas?: OffcanvasStatic } }).bootstrap?.Offcanvas;
  if (!sidebar || !offcanvas) return;
  event.stopImmediatePropagation();
  event.preventDefault();
  offcanvas.getOrCreateInstance(sidebar).toggle();
}

/**
 * 바로가기 패널을 사이드바 안으로 옮기고 햄버거와 htmx 교체를 다룬다. 화면마다 한 번 부른다.
 *
 * @returns 사이드바 안에 바로가기가 있으면 `true`이다.
 */
export function initShortcuts(): boolean {
  const adopted = adopt();
  // 사이드바가 교체되는 응답은 본문 교체와 함께 `htmx:afterSettle`을 낸다. 같은 화면에서 여러 번 불려도 한 번만 붙는다.
  document.addEventListener("htmx:afterSettle", adopt);
  document.addEventListener("htmx:oobAfterSwap", adopt);
  document.addEventListener("click", onHamburgerClick, true);
  return adopted;
}
