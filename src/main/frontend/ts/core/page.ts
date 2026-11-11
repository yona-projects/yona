/**
 * 화면 수명주기(D-136, A-475).
 *
 * 사이드바 이동으로 본문(`.shell-main`)만 바꿀 때는 문서가 그대로여서, 화면이 `document`나 `window`, 전역 단축키, 타이머에 걸어 둔 것이
 * 새 화면에 남는다. 이런 화면은 정리 함수를 {@link onPageLeave}로 등록하고, 셸이 본문을 바꾸기 직전에 {@link runPageLeave}로 한 번씩 부른다.
 * 일반 이동(문서를 새로 여는 경우)에서는 필요 없으므로 부르지 않는다.
 */

/** 다음 본문 교체 직전에 부를 정리 함수 목록이다. 부른 뒤에는 비운다. */
const leaveHandlers: Array<() => void> = [];

/**
 * 이 화면을 떠날 때(본문이 교체되기 직전) 한 번 부를 정리 함수를 등록한다.
 * 같은 함수를 여러 번 등록하면 등록한 만큼 불린다.
 *
 * @param handler 정리 함수. 던진 예외는 다른 정리 함수를 막지 않도록 콘솔에만 남긴다.
 */
export function onPageLeave(handler: () => void): void {
  leaveHandlers.push(handler);
}

/** 등록된 정리 함수를 등록한 순서대로 모두 부르고 목록을 비운다. 셸이 본문 교체 직전에 부른다. */
export function runPageLeave(): void {
  const handlers = leaveHandlers.splice(0, leaveHandlers.length);
  for (const handler of handlers) {
    try {
      handler();
    } catch (error) {
      console.error("[Yona] 화면 정리 함수에서 오류가 났다", error);
    }
  }
}
