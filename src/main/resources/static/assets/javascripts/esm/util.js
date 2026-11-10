/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * 여러 화면 스크립트가 같이 쓰는 작은 도우미 모음이다(표준 기능 리팩터링 5단계, D-123).
 */
import { register } from "./namespace.js";

/**
 * 연달아 불러도 마지막 호출만 [wait]밀리초 뒤에 한 번 실행하는 함수를 만든다. 직접 `clearTimeout`과 `setTimeout`을 짝지어 쓰던 뒤쪽 지연(trailing debounce)과 같다.
 * 돌려주는 함수의 `cancel()`은 아직 실행하지 않은 호출을 취소한다. 실행할 때의 `this`와 인자는 마지막 호출의 것이다.
 *
 * @param {Function} fn 지연해서 부를 함수
 * @param {number} wait 기다릴 밀리초
 * @returns {Function} 지연 호출 함수(`cancel` 속성을 가진다)
 */
export function debounce(fn, wait) {
  let timer = null;
  const debounced = function (...args) {
    clearTimeout(timer);
    timer = setTimeout(() => {
      timer = null;
      fn.apply(this, args);
    }, wait);
  };
  debounced.cancel = () => {
    clearTimeout(timer);
    timer = null;
  };
  return debounced;
}

/**
 * 연달아 불러도 다음 화면 갱신(`requestAnimationFrame`) 때 마지막 호출만 한 번 실행하는 함수를 만든다. 스크롤처럼 프레임보다 자주 오는
 * 이벤트에서 화면 갱신을 프레임당 한 번으로 줄일 때 쓴다. 실행할 때의 `this`와 인자는 마지막 호출의 것이다.
 *
 * @param {Function} fn 프레임마다 한 번만 부를 함수
 * @returns {Function} 묶인 함수(`cancel` 속성을 가진다)
 */
export function rafThrottle(fn) {
  let frame = 0;
  let lastThis;
  let lastArgs;
  const throttled = function (...args) {
    lastThis = this;
    lastArgs = args;
    if (frame) {
      return;
    }
    frame = requestAnimationFrame(() => {
      frame = 0;
      fn.apply(lastThis, lastArgs);
    });
  };
  throttled.cancel = () => {
    cancelAnimationFrame(frame);
    frame = 0;
  };
  return throttled;
}

register("yona.util", { debounce, rafThrottle });
