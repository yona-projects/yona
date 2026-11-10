/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * ES 모듈과 옛 전역 이름 공간(`yona.*`)을 잇는 다리이다(ES 모듈 뼈대, D-122).
 *
 * 지금의 스크립트는 `(function (ns) { ... })("yona.ui.Calendar")`처럼 전역 `yona` 아래에 이름을 달고,
 * 템플릿의 인라인 스크립트와 다른 파일이 `yona.ui.Calendar`로 부른다. 파일을 ES 모듈로 바꿔 가는 동안에도 이 호출이 그대로 동작해야 하므로,
 * 모듈은 자기 값을 `export`하는 동시에 `register`로 같은 이름의 전역에 등록한다.
 * 모든 호출처가 `import`로 바뀌면 `register`만 지우면 된다.
 *
 * import map(`common/importmap.html`)이 `yona/` 접두어를 이 폴더에 연결하므로 다른 모듈은 `import { register } from "yona/namespace.js"`로 가져온다.
 *
 * 모듈 하나를 옮기는 순서(자세한 것은 위키 `architecture/esm-skeleton.md`)
 * 1. `common/yona.X.js`를 `esm/X.js`로 옮기고 즉시 실행 함수 대신 `export`한다.
 * 2. 같은 파일 끝에서 `register("yona.X", X)`를 부른다.
 * 3. `esm/index.js`가 그 모듈을 `import`한다.
 * 4. 옛 `<script>` 태그와 번들 입력에서 원래 파일을 뺀다.
 */

const NAME_PATTERN = /^[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*$/;

/**
 * 점으로 이은 이름(`"yona.ui"`)의 단계를 [root] 아래에 없으면 빈 객체로 만들어 돌려준다. 이미 있는 값은 그대로 둔다.
 *
 * @param {string} path 점으로 이은 이름
 * @param {object} [root=globalThis] 시작 객체
 * @returns {object} 마지막 단계의 객체
 * @throws {Error} 이름이 비었거나 단계가 식별자 모양이 아닐 때
 */
export function ensureNamespace(path, root = globalThis) {
  if (typeof path !== "string" || !NAME_PATTERN.test(path)) {
    throw new Error(`잘못된 이름 공간입니다: ${JSON.stringify(path)}`);
  }
  let node = root;
  for (const part of path.split(".")) {
    if (node[part] === undefined || node[part] === null) {
      node[part] = {};
    }
    node = node[part];
  }
  return node;
}

/**
 * 값을 옛 전역 이름 공간에 등록한다. 없는 상위 단계는 만들고, 같은 이름이 이미 있으면 새 값으로 바꾼다.
 *
 * @param {string} path 점으로 이은 전체 이름(`"yona.ui.Calendar"`)
 * @param {*} value 등록할 값
 * @param {object} [root=globalThis] 시작 객체
 * @returns {*} 등록한 값
 * @throws {Error} 이름이 올바르지 않을 때
 */
export function register(path, value, root = globalThis) {
  const dot = path.lastIndexOf(".");
  if (dot < 0) {
    ensureNamespace(path, root); // 이름 검사
    root[path] = value;
    return value;
  }
  ensureNamespace(path, root); // 전체 이름 검사
  ensureNamespace(path.slice(0, dot), root)[path.slice(dot + 1)] = value;
  return value;
}
