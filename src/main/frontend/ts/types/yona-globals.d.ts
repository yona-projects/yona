/**
 * 서버와 옛 스크립트가 만드는 전역 값의 형이다(D-139). 새 모듈은 이 선언을 통해서만 전역을 읽는다.
 * 옛 전역(`yona.*`, `$yona`, `Messages`)은 옮기는 묶음에서 여기에 하나씩 선언을 더한다.
 */
interface Window {
  /** 서버가 `common/scripts.html`에서 박는 컨텍스트 경로이다(예: `/yona`). 루트 배포에서는 없거나 빈 글자이다. */
  yonaContextPath?: string;

  /** 화면이 불러 둔 htmx이다(`lib/htmx.js`). 새 모듈은 쓰는 메서드만 선언한다. 없는 화면도 있다. */
  htmx?: { process(element: Element): void };
}
