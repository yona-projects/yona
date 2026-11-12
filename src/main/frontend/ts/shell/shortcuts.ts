/**
 * 햄버거로 여는 바로가기 패널(즐겨찾기, 프로젝트, 최근 읽은 이슈)을 맥락 사이드바 안으로 옮긴다(sidebar 브랜치).
 *
 * v1.6의 왼쪽 사이드바(`.pin`)를 잇는 패널(`#mySidenav`)은 지금까지 화면 왼쪽에 사이드바를 덮고 튀어나왔다. 이 모듈은
 * 맥락 사이드바가 있는 화면에서 그 내용(`.right-menu`)을 사이드바의 `[data-shell-shortcuts]` 자리로 옮기고 덮는 패널은 치운다.
 * 내용 요소를 새로 만들지 않고 그대로 옮기므로 `Usermenu.js`가 붙인 핸들러와 목록 읽기가 그대로 동작한다.
 *
 * - 사이드바가 없는 화면(프로필, 새 프로젝트 등)에서는 아무것도 하지 않아 덮는 패널이 그대로 쓰인다.
 * - 사이드바가 htmx로 교체되면(`hx-select-oob`) 새로 그려진 빈 자리에 옮겨 둔 내용을 다시 붙인다.
 * - 사이드바가 있는 화면에서는 상단 바의 햄버거가 접기 단추가 된다. 넓은 폭(992px 이상)에서는 사이드바를 접고 펴고(`shell/sidebar.ts`가
 *   `data-shell-sidebar-toggle` 요소의 클릭을 맡는다), 좁은 폭에서는 사이드바 서랍을 연다(바로가기가 서랍 안에 있다).
 *   덮는 패널을 여는 속성(`data-shell-switcher`)은 떼어 `Usermenu.js`가 이 단추에 열기 핸들러를 달지 않게 한다. 사이드바가 없는 화면은 그대로이다.
 * - 사이드바 맨 위의 선택 단추 줄(홈, 즐겨찾기, 프로젝트, 최근 읽은 이슈)로 보기를 고른다. 홈은 맥락 메뉴(`data-shell-view="home"`),
 *   나머지는 바로가기(`data-shell-view="shortcuts"`)이며 안쪽의 같은 탭 링크를 눌러 `Usermenu.js`의 탭 전환과 기억이 그대로 동작하게 한다.
 */

/** 바로가기를 담을 사이드바 안의 자리이다. */
const HOST_SELECTOR = "[data-shell-shortcuts]";
/** 덮는 패널과 그 안의 내용이다(`common/usermenu.html`). */
const OVERLAY_SELECTOR = "#mySidenav";
const MENU_SELECTOR = ".right-menu";
/** 상단 바의 햄버거이다. */
const HAMBURGER_SELECTOR = ".shell-switcher-btn, [data-shell-switcher]";
const SIDEBAR_SELECTOR = "nav.shell-sidebar";
/** 서랍으로 바뀌는 폭이다. Bootstrap 기본 `lg` 미만과 같다. */
const NARROW_QUERY = "(max-width: 991.98px)";

/** 바로가기 안의 탭 기억 키이다. `Usermenu.js`가 쓰는 것과 같다. 보기(홈 또는 바로가기)는 저장하지 않는다. */
const TAB_KEY = "sidebarActiveMenu";
const TAB_NAMES = ["myOrganizationList", "myProjectList", "myRecentIssueList"];
const DEFAULT_TAB = "myOrganizationList";
const SWITCH_SELECTOR = "[data-shell-switch]";
type View = "home" | "shortcuts";

interface OffcanvasStatic {
  getOrCreateInstance(element: Element): { toggle(): void };
}

/** 옮겨 둔 내용이다. 사이드바가 교체되어 자리가 새로 그려지면 이 요소를 다시 붙인다. */
let kept: HTMLElement | null = null;

/**
 * 지금 고른 보기이다. 브라우저에 저장하지 않는다: 선택 단추 줄은 목록을 잠깐 보고 맥락 메뉴로 돌아오는 용도라, 화면을 새로 열면(새로고침,
 * 링크로 이동) 홈으로 시작한다. htmx로 사이드바만 교체되는 동안에는 이 값이 남아 같은 보기를 다시 적용한다.
 */
let view: View = "home";

function read(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // 저장소를 쓸 수 없으면 이번 화면에서만 동작한다.
  }
}

function currentTab(): string {
  const stored = read(TAB_KEY);
  return stored && TAB_NAMES.includes(stored) ? stored : DEFAULT_TAB;
}

/** 사이드바의 보기와 선택 단추의 선택 상태를 맞춘다. 사이드바가 교체되어 서버가 홈으로 다시 그린 뒤에도 부른다. */
function render(): void {
  const sidebar = document.querySelector(SIDEBAR_SELECTOR);
  if (!sidebar) return;
  const tab = currentTab();
  sidebar.setAttribute("data-shell-view", view);
  sidebar.querySelectorAll<HTMLElement>(SWITCH_SELECTOR).forEach((button) => {
    const key = button.getAttribute("data-shell-switch");
    const selected = view === "home" ? key === "home" : key === tab;
    button.setAttribute("aria-selected", String(selected));
  });
}

/** 선택 단추 클릭: 홈이면 맥락 메뉴, 아니면 바로가기를 보이고 안쪽의 같은 탭 링크를 눌러 준다. */
function onSwitchClick(event: Event): void {
  const target = event.target;
  if (!(target instanceof Element)) return;
  const button = target.closest<HTMLElement>(SWITCH_SELECTOR);
  if (!button) return;
  const key = button.getAttribute("data-shell-switch");
  if (key === "home") {
    view = "home";
  } else if (key && TAB_NAMES.includes(key)) {
    view = "shortcuts";
    write(TAB_KEY, key);
    document.querySelector<HTMLElement>(`${HOST_SELECTOR} .nav-tabs .${key} > a`)?.click();
  }
  render();
}

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

/** 내용을 붙이고(필요하면) 보기를 다시 맞춘다. htmx 교체 뒤에도 같은 길로 부른다. */
function refresh(): void {
  adopt();
  render();
}

/**
 * 사이드바가 있는 화면에서 햄버거를 접기 단추로 바꾼다. 서버는 햄버거를 덮는 패널을 여는 단추(`data-shell-switcher`)로 그리고 사이드바가
 * 있는지 모르므로(상단 바가 본문보다 먼저 그려진다) 클라이언트가 바꾼다. `Usermenu.js`가 `[data-shell-switcher]`에 핸들러를 다는
 * `$(...)` 준비 처리기는 모듈이 실행된 뒤에 돌므로 속성을 먼저 떼면 핸들러가 붙지 않는다. `shell/sidebar.ts`보다 먼저 불러야
 * `aria-expanded`가 처음부터 맞게 정해진다.
 */
function repurposeHamburger(sidebar: Element): void {
  const button = document.querySelector<HTMLElement>(HAMBURGER_SELECTOR);
  if (!button) return;
  button.removeAttribute("data-shell-switcher");
  button.setAttribute("data-shell-sidebar-toggle", "");
  button.removeAttribute("aria-haspopup");
  if (sidebar.id) button.setAttribute("aria-controls", sidebar.id);
  const label = button.getAttribute("data-toggle-label");
  if (label) {
    button.setAttribute("aria-label", label);
    button.setAttribute("title", `${label} ([)`);
  }
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
  const sidebar = document.querySelector(SIDEBAR_SELECTOR);
  if (sidebar) repurposeHamburger(sidebar);
  render();
  // 사이드바가 교체되는 응답은 본문 교체와 함께 `htmx:afterSettle`을 낸다. 같은 화면에서 여러 번 불려도 한 번만 붙는다.
  document.addEventListener("htmx:afterSettle", refresh);
  document.addEventListener("htmx:oobAfterSwap", refresh);
  document.addEventListener("click", onHamburgerClick, true);
  document.addEventListener("click", onSwitchClick);
  return adopted;
}
