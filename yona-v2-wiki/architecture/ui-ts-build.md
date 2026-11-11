---
title: TypeScript 빌드와 새 스크립트의 구성
type: architecture
sources:
  - ../build.gradle.kts
  - ../build-tools/ts/package.json
  - ../build-tools/ts/tsconfig.json
  - ../src/main/frontend/ts/core/config.ts
  - ../src/test/js/tsmodule.mjs
  - ../src/test/js/tsfoundation.test.mjs
  - ../src/test/kotlin/dev/yona/web/TsAssetBuildTest.kt
updated: 2026-11-11
---

# TypeScript 빌드와 새 스크립트의 구성

새 UI 스크립트를 TypeScript로 쓰는 기반이다(`decisions.md` D-139, 단계 P-1). 설계는 `ui-ia-v2.md` 6절, 규모와 전환 순서는 `ui-ts-inventory.md`에 있다.

## 위치와 흐름

| 무엇 | 어디 |
|---|---|
| TS 원본 | `src/main/frontend/ts/**`. `static/` 밖이다(`AssetFilter`가 `.ts` 원문을 그대로 내려 주는 것을 피한다). 시험 `TsAssetBuildTest`가 `static/` 안에 `.ts`가 없음을 지킨다 |
| 컴파일러 | `build-tools/ts`(`typescript 5.9.3`). Sass와 같은 방식으로 Gradle `installTs`가 `npm ci`를 부른다 |
| 설정 | `build-tools/ts/tsconfig.json`. `strict`, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, `isolatedModules`, `verbatimModuleSyntax` |
| 산출물 | `build/generated/ts/static/assets/javascripts/esm/**`(`.js`와 소스 맵). **커밋하지 않고 빌드 때 만든다**. Gradle `compileTs`가 만들고 `processResources`가 싣는다 |
| 서버가 내려 주는 주소 | `/assets/javascripts/esm/<경로>.js`. 옛 `esm/` 모듈과 같은 폴더 구조이므로 import map(`yona/` 접두어)과 `th:src` 계약이 그대로이다 |

- `tsc`의 입출력이 1:1이라 파일 구조가 바뀌지 않는다. 모듈 지정자 `yona/core/config.js`는 `paths`(`yona/*`)로 형 검사만 풀고 산출물에는 그대로 남아 브라우저의 import map이 푼다.
- 옮긴 옛 모듈은 같은 커밋에서 `static/assets/javascripts/esm/<이름>.js`를 지운다(자원 폴더와 산출 폴더에 같은 경로가 둘이면 중복이 생긴다).
- 형 검사 오류가 있으면 `compileTs`가 실패해 서버 빌드가 멈춘다.

## 시험

- `src/test/js/tsmodule.mjs`: `tsc`로 빌드하고 산출물을 실제 Chromium에서 import map으로 불러 오는 도우미(`buildTs`, `openTs`). 새 모듈은 이 방식으로 시험한다.
- `src/test/js/tsfoundation.test.mjs`: 빌드 성공과 `core/config.ts`의 `url()`.
- Kotlin `TsAssetBuildTest`: 서버가 산출물을 내려 주고 원본(`.ts`)은 내려 주지 않는다.
- 실행: `npm ci --prefix build-tools/ts` 뒤 `node src/test/js/run.mjs tsfoundation`. 전체 JS 시험은 요청할 때만 돌린다.

## 모듈

| 모듈 | 내용 |
|---|---|
| `core/config.ts` | `contextPath()`, `url(path)`. 새 코드는 `"/"`로 시작하는 주소 문자열을 직접 쓰지 않고 `url()`을 거친다(컨텍스트 경로 접두어가 빠지거나 두 번 붙는 결함을 막는다, A-466) |
| `shell/sidebar.ts` | 넓은 폭(992px 이상)에서 사이드바를 숨기고 다시 보이는 첫 화면 모듈. 단추와 `[` 키, 상태를 `html[data-sidebar="collapsed"]`와 localStorage `shellSidebarCollapsed`에 기억하고 첫 그림 전에 적용한다. 좁은 폭은 서랍이 맡아 기억하지 않는다. 시험 `shellsidebar.test.mjs`(5개) |
| `types/yona-globals.d.ts` | 서버와 옛 스크립트가 만드는 전역의 형(`window.yonaContextPath`). 옛 전역은 옮기는 묶음에서 여기에 더한다 |

## 다음 단계(P-1의 남은 일)
(`shell/sidebar`가 첫 사용처가 되었다.) `Page` 수명주기(`shell/Page.ts`), 저장소(`store/`, `idb`), 단축키(`shortcuts`), `yona-globals.d.ts`의 `$yona`와 `Messages` 선언, 하네스의 산출물 경로 전환과 순수 모듈 14개 이식. 순서는 `ui-ts-inventory.md` 7절이다.
