/**
 * 새 셸 사이드바의 숨기기와 다시 보이기이다(D-141, A-469 5번).
 *
 * 넓은 폭(Bootstrap `lg`, 992px 이상)에서만 사이드바를 숨길 수 있고, 숨김 상태는 `<html data-sidebar="collapsed">`로 그리며
 * localStorage(`shellSidebarCollapsed`)에 기억한다. 좁은 폭은 서랍(`offcanvas`)이 맡으므로 상태를 바꾸지도 기억하지도 않고, 같은 단추가 서랍을 연다(A-487).
 * 저장소를 쓸 수 없어도(차단, 가득 참) 이번 화면에서는 동작한다.
 */

/** 기억 키이다. 첫 그림 전에 `<head>`의 짧은 스크립트가 같은 키를 읽는다(깜빡임 방지, D-135의 거울). */
const STORAGE_KEY = "shellSidebarCollapsed";
const ATTRIBUTE = "data-sidebar";
const COLLAPSED = "collapsed";
/** 숨기기를 허용하는 폭이다. Bootstrap 기본 `lg`와 같다. */
const WIDE_QUERY = "(min-width: 992px)";
/** 좁은 폭에서 서랍으로 열리는 사이드바이다. */
const DRAWER_SELECTOR = "nav.shell-sidebar";
/** 숨기기 단추를 가리키는 선택자이다. */
const TOGGLE_SELECTOR = "[data-shell-sidebar-toggle]";

/** [initSidebar]가 돌려주는 제어 손잡이이다. */
export interface SidebarApi {
  /** 지금 숨겨져 있으면 `true`이다. */
  collapsed(): boolean;
  /** 숨김 상태를 뒤집는다. 좁은 폭이면 아무 일도 하지 않고 현재 상태를 돌려준다. */
  toggle(): boolean;
}

function readStored(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === "true";
  } catch {
    return false;
  }
}

function writeStored(value: boolean): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, String(value));
  } catch {
    // 저장소를 쓸 수 없으면 이번 화면에서만 동작한다.
  }
}

/** 글을 쓰는 중인 요소인지 판정한다. 입력란에서는 한 글자 단축키가 글자로 들어가야 한다(WCAG 2.1.4). */
function isEditing(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName);
}

/**
 * 숨기기 단추와 `[` 단축키를 켜고, 기억해 둔 숨김 상태를 적용한다.
 *
 * @param root 단추를 찾을 문서이다(시험에서 바꿀 수 있다)
 * @returns 상태를 읽고 바꾸는 손잡이
 */
export function initSidebar(root: Document = document): SidebarApi {
  let collapsed = readStored();

  const apply = (): void => {
    const html = root.documentElement;
    if (collapsed) html.setAttribute(ATTRIBUTE, COLLAPSED);
    else html.removeAttribute(ATTRIBUTE);
    root.querySelectorAll<HTMLElement>(TOGGLE_SELECTOR).forEach((button) => {
      button.setAttribute("aria-expanded", String(!collapsed));
    });
  };

  const toggle = (): boolean => {
    if (!window.matchMedia(WIDE_QUERY).matches) {
      // 좁은 폭에서는 사이드바가 서랍(offcanvas)이므로 숨김 상태를 바꾸지 않고 서랍을 연다. Bootstrap이 없는 화면에서는 아무 일도 하지 않는다.
      const drawer = root.querySelector(DRAWER_SELECTOR);
      if (drawer) window.bootstrap?.Offcanvas.getOrCreateInstance(drawer).toggle();
      return collapsed;
    }
    collapsed = !collapsed;
    writeStored(collapsed);
    apply();
    return collapsed;
  };

  root.addEventListener("click", (event) => {
    if (event.target instanceof Element && event.target.closest(TOGGLE_SELECTOR)) toggle();
  });
  root.addEventListener("keydown", (event) => {
    if (event.key !== "[" || event.ctrlKey || event.metaKey || event.altKey || event.isComposing || isEditing(event.target)) return;
    event.preventDefault();
    toggle();
  });

  apply();
  return { collapsed: () => collapsed, toggle };
}
