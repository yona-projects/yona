---
title: ES 모듈 뼈대
type: architecture
updated: 2026-11-10
---

# ES 모듈 뼈대 (D-122, 2026-11-10)

지금의 스크립트는 전부 클래식 스크립트(`(function (ns) { ... })("yona.ui.Calendar")`)이고 전역 `yona.*`에 이름을 단다. 이 구조를 한 번에 바꾸지 않고 **파일을 하나씩 ES 모듈로 옮겨 갈 수 있는 길**만 열어 두었다. **옮긴 모듈 3개**: `esm/ui/Elevator.js`, `esm/ui/Favicon.js`, `esm/ui/FormValidator.js`(D-122, 선생님 지시: 표준 기능으로 코드와 최대한 같은 로직을 갖추게 리팩터링하고 ES 모듈로 옮기되 템플릿은 `<script type="module">`로 불러온다).

## 구성

| 파일 | 역할 |
|---|---|
| `esm/namespace.js` | `ensureNamespace(path)`와 `register(path, value)`. 점으로 이은 이름의 단계를 만들어 값을 옛 전역 이름 공간에 등록한다. 이미 있는 단계와 값은 지우지 않고, 같은 이름을 다시 등록하면 새 값으로 바꾼다. 이름이 올바르지 않으면 오류를 던진다 |
| `esm/index.js` | 진입점. `yona.esm`(`ready`, `register`, `ensureNamespace`)을 만들고 `yona:esm-ready` 이벤트를 한 번 낸다. 옮겨 온 모듈은 여기서 `import`한다 |
| `common/importmap.html` | import map에 `"yona/": "/assets/javascripts/esm/"`를 더했다. 모듈은 `import { register } from "yona/namespace.js"`로 가져온다 |
| `common/scripts.html` | `<script type="module" src="esm/index.js">`를 모든 레이아웃의 본문 끝에서 불러온다(번들 없음) |

## 템플릿 규칙 (선생님 지시)
모듈로 옮긴 파일은 템플릿에서 클래식 `<script type="text/javascript">`로 부르지 않는다. 지금은 모든 레이아웃의 본문 끝(`common/scripts.html`)이 `<script type="module" th:src="@{/assets/javascripts/esm/index.js}">` 하나를 불러오고 진입점이 옮긴 모듈을 `import`하므로, 옮긴 모듈을 쓰는 템플릿에는 개별 태그가 필요 없다. 어떤 화면만을 위한 모듈이면 그 템플릿에 `<script type="module" th:src="@{/assets/javascripts/esm/...}">`를 두고 `esm/index.js`에서는 빼도 된다. 템플릿의 인라인 스크립트가 모듈을 쓸 때는 `$(function () { ... })`나 `<script type="module">` 안에서 부르고, 문서 분석 중에 바로 실행되는 클래식 인라인 스크립트에서는 부르지 않는다(모듈은 지연 실행된다). 클래식 파일이 맨 위에서 모듈을 바로 부르던 곳(예: `detectChange.js`의 파비콘)은 처음 쓸 때 만들도록 고쳤다.

## 모듈 하나를 옮기는 순서
1. 시험이 없으면 먼저 특성 시험을 만든다(옛 구현에서 통과시킨다. S-10, pageslide, `jquery.form`에서 쓴 방식).
2. `common/yona.X.js`를 `esm/X.js`로 옮기고 즉시 실행 함수 대신 `export`한다. 같은 파일 끝에서 `register("yona.X", X)`로 옛 이름을 유지한다.
3. `esm/index.js`에 `import "yona/X.js"`를 더한다.
4. 옛 `<script>` 태그와 `build-tools/js-bundle/bundles.mjs`의 입력에서 원래 파일을 뺀다.
5. 같은 모듈을 `import`하는 다른 모듈이 모두 옮겨지면 `register`를 지운다.

## 알아 둘 것
- **실행 순서**: 모듈 스크립트는 문서 분석이 끝난 뒤 `DOMContentLoaded` 전에 순서대로 실행된다. `$(function () { ... })` 안에서 부르는 호출은 문제 없지만, 문서 분석 중에 바로 실행되는 인라인 스크립트가 옮긴 모듈을 부르면 아직 없을 수 있다. 그런 호출은 `yona:esm-ready`나 `yona.esm.ready`를 기다리거나 옮길 때 템플릿 쪽을 함께 고친다.
- **Firefox**: import map은 모듈 태그보다 앞에 있어야 한다. `common/importmap.html`을 레이아웃 `<head>`의 맨 앞에 두는 기존 규칙(D-106)을 지킨다.
- **번들**: 지금 번들 3개(`yona-layout.js`, `yona-common.js`, `yona-lib.js`)는 vite 8.3(D-92)의 이어 붙이기 방식이다. 옮긴 모듈은 번들에 넣지 않고 개별로 내려 준다. 모듈이 많아지면 `esm/index.js`를 입력으로 하는 vite 빌드(모듈 그래프)로 바꿀 수 있다. 이는 모듈이 쌓인 뒤의 결정이다.
- **시험**: 하네스의 `esmPage()`가 `http://esm.test/assets/javascripts/**`를 저장소 파일로 내려 주고 레이아웃과 같은 import map과 진입점을 넣는다. `src/test/js/esm.test.mjs`(5개)가 import map 해석, 등록, 기존 값 보존, 이벤트, 잘못된 이름을 확인한다. `JsLibraryVersionsTest`가 import map과 진입점 연결을 지킨다.
