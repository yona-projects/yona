/**
 * 사이드바 패널을 화면 아래까지 잇는다(A-482).
 *
 * 본문이 짧으면 사이드바 패널이 항목 높이에서 끊겨 그 아래가 빈 바탕이 된다. 와이어프레임의 사이드바는 세로로 끊기지 않으므로,
 * 본문이 화면보다 짧을 때 `.shell`에 최소 높이를 주어 패널이 푸터 바로 위, 즉 화면 아래까지 이어지게 한다.
 *
 * CSS만으로 `#main`을 세로 flex로 만들면 `position: absolute`인 프로젝트 상단 바의 위치가 맨 위로 올라가 관리자 띠와 겹치므로 계산으로 한다.
 * 넓은 폭(Bootstrap `lg`, 992px 이상)에서만 하고, 좁은 폭의 서랍은 건드리지 않는다.
 */

/** 패널이 고정되는 폭이다. Bootstrap 기본 `lg`와 같다. */
const WIDE_QUERY = "(min-width: 992px)";
const SHELL_SELECTOR = ".shell";
const FOOTER_SELECTOR = ".page-footer-outer, footer";
/** 본문 래퍼이다. 기본 `min-height: 450px`가 있어 짧은 본문 아래 간격을 흡수하므로, 채울 때는 이 값을 같이 푼다. */
const WRAP_SELECTOR = ".page-wrap-outer";

/** [initFill]이 돌려주는 손잡이이다. */
export interface FillApi {
  /** 최소 높이를 다시 계산한다. 본문이나 창이 바뀐 뒤에 부른다. */
  fit(): void;
}

/** 요소의 문서 기준 위쪽과 아래쪽 위치이다. */
function documentBox(element: Element): { top: number; bottom: number } {
  const rect = element.getBoundingClientRect();
  return { top: rect.top + window.scrollY, bottom: rect.bottom + window.scrollY };
}

/**
 * `.shell`의 최소 높이를 계산해 적용한다. 푸터가 없으면 화면 아래까지를 기준으로 한다.
 * 계산식: 화면 높이 - 푸터 높이 - (`.shell` 아래와 푸터 위 사이 간격) - `.shell` 위쪽 위치. 값은 현재 최소 높이에 의존하지 않으므로 되먹임이 없다.
 * 본문 래퍼의 기본 최소 높이(450px)가 간격을 흡수하면 계산이 어긋나므로, 계산하는 동안 그 값을 풀고 채울 때만 풀어 둔다.
 */
function fit(): void {
  const shell = document.querySelector<HTMLElement>(SHELL_SELECTOR);
  if (!shell) return;
  const wrap = shell.closest<HTMLElement>(WRAP_SELECTOR);
  const reset = (): void => {
    shell.style.minHeight = "";
    if (wrap) wrap.style.minHeight = "";
  };
  reset();
  if (!window.matchMedia(WIDE_QUERY).matches) return;
  // 래퍼의 기본 최소 높이를 풀고, 본문이 자연스럽게 차지하는 위치에서 잰다.
  if (wrap) wrap.style.minHeight = "0";
  const footer = document.querySelector<HTMLElement>(FOOTER_SELECTOR);
  const shellBox = documentBox(shell);
  const footerHeight = footer ? footer.offsetHeight : 0;
  const gap = footer ? Math.max(0, documentBox(footer).top - shellBox.bottom) : 0;
  const free = window.innerHeight - footerHeight - gap - shellBox.top;
  const natural = shellBox.bottom - shellBox.top;
  if (free > natural) {
    shell.style.minHeight = `${Math.floor(free)}px`;
  } else {
    reset(); // 본문이 화면보다 길면 아무것도 바꾸지 않는다.
  }
}

/** 화면 크기와 본문 높이가 바뀔 때마다 다시 맞춘다. 처음에도 한 번 맞춘다. */
export function initFill(): FillApi {
  let queued = false;
  const schedule = (): void => {
    if (queued) return;
    queued = true;
    window.requestAnimationFrame(() => {
      queued = false;
      fit();
    });
  };
  window.addEventListener("resize", schedule);
  // 본문 높이가 달라지면(목록 교체 등) 짧음과 긺이 바뀔 수 있다.
  const main = document.querySelector(".shell-main");
  if (main && typeof ResizeObserver !== "undefined") new ResizeObserver(schedule).observe(main);
  // 사이드바 이동으로 본문을 바꿀 때(htmx)도 맞춘다.
  document.addEventListener("htmx:afterSettle", schedule);
  fit();
  return { fit };
}
