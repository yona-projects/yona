/**
 * 화면 스크립트가 서버 설정을 읽는 한 곳이다(D-139).
 *
 * 새 코드는 `"/"`로 시작하는 주소 문자열을 직접 쓰지 않고 반드시 [url]을 거친다. 하위 경로로 배포한 서버
 * (`yona.application-context`)에서 접두어가 빠지거나 두 번 붙는 결함이 반복되었기 때문이다(A-466).
 */

/**
 * 서버가 알려 준 컨텍스트 경로이다. 앞에 빗금이 있고 뒤에 빗금이 없는 모양으로 돌려주며, 루트 배포이면 빈 글자이다.
 *
 * @returns 예: `/yona`, 없으면 `""`
 */
export function contextPath(): string {
  const raw = window.yonaContextPath;
  if (raw === undefined || raw === "") return "";
  const trimmed = raw.replace(/\/+$/, "");
  return trimmed.startsWith("/") ? trimmed : `/${trimmed}`;
}

/**
 * 서버 주소에 컨텍스트 경로를 한 번만 붙인다.
 *
 * - `"/"`로 시작하는 서버 경로(`/a/b?x=1#h`)만 접두어를 붙인다. 질의와 해시는 그대로 둔다.
 * - 외부 주소(`https://...`), 프로토콜 상대 주소(`//cdn/...`), 상대 경로(`a/b`), 해시나 질의만 있는 주소, `mailto:` 같은
 *   다른 종류의 주소는 바꾸지 않는다.
 *
 * @param path 서버 경로이다(예: `/issues`)
 * @returns 컨텍스트 경로가 붙은 주소이다(예: `/yona/issues`)
 */
export function url(path: string): string {
  if (!path.startsWith("/") || path.startsWith("//")) return path;
  return contextPath() + path;
}
