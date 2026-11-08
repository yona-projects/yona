---
title: EasyMDE 시험 (멘션 자동완성과 At.js 패치 항목)
type: architecture
sources:
  - ../e2e/editortrial.tmp/trial.mjs
  - ../e2e/editortrial.tmp/v3entry.mjs
  - decisions.md
updated: 2026-11-08
---

# EasyMDE 시험 (멘션 자동완성과 At.js 패치 항목)

선생님 요청(2026-11-08)으로 EasyMDE를 Yona의 멘션 자동완성(`@` 사용자, `:` 이모지, `#` 이슈) 조건에서 시험했다. 질문은 두 가지였다. **(1) At.js에 패치를 가했던 문제들이 EasyMDE의 기본 상태에서도 생기는가, (2) 생기면 At.js의 패치 내용을 EasyMDE에 적용해 해결할 수 있는가.** 코드와 템플릿은 바꾸지 않았다. 빌드 금지 지시에 따라 Yona의 Gradle과 번들 빌드는 하지 않았고, 임시 폴더에서 패키지를 설치해 Playwright(Chromium)로 시험했다.

## 이 프로젝트의 이전 기록
EasyMDE는 이미 한 번 채택했다가 버렸다(`docs/parity/tickets/p3-46.md`, 2026-09-10~11). EasyMDE 2.21.0과 Tribute.js를 붙이다가 **CodeMirror 5의 숨은 `<textarea>`가 Backspace나 Delete 직후 순간적으로 비어 Tribute.js가 맥락을 잃는 구조적 결함**(zurb/tribute#511)에 부딪혔다. 완화책을 시도하자 줄 중간에서 타이핑할 때 문서가 반복 중복되고 무너지는 더 큰 회귀가 생겨 비활성화했고, CodeMirror 6 기반 자체 웹 컴포넌트(`<yona-markdown-editor>`)로 방향을 바꿨다.

## 시험 대상
| 변형 | 구성 |
|---|---|
| 기준 | 지금의 Yona: `<textarea>` + At.js 1.5.3(Yona 패치 포함, jQuery 4용 3곳 치환) + jQuery 3.7.1 |
| EasyMDE2+Tribute | EasyMDE 2.21.0(CodeMirror 5) + Tribute.js 5.1.3, 입력란에 붙임(예전 `next`의 조합) |
| EasyMDE2+hint | EasyMDE 2.21.0 + CodeMirror 5의 자체 자동완성 애드온(`show-hint`) |
| EasyMDE3+플러그인 | EasyMDE 3.0.0-beta.1(CodeMirror 6) + 직접 만든 DOM 플러그인(공개 `EditorView` 메서드만 사용) |

EasyMDE 3 beta에는 자동완성을 끼울 공식 경로가 없다: `codemirrorExtensions` 옵션은 아직 동작하지 않고(README에 명시), 번들이 자체 `@codemirror/*` 사본을 품고 있어 외부 `@codemirror/autocomplete`를 붙일 수 없다. 그래서 플러그인 API(`addPlugin`)에서 `EditorView`의 `state.doc`, `coordsAtPos`, `dispatch`만 써서 팝업을 직접 구현했다. 처음에는 `input` 이벤트를 들었으나 CodeMirror 6이 `beforeinput`을 직접 처리해 `input`이 오지 않았고(시험 방식의 오류), `beforeinput` 뒤 다음 틱에 읽도록 고쳤다.

## 시험 항목과 결과
같은 항목을 편집기 하나로, 그리고 2단 보기처럼 iframe 안에서도 돌렸다. iframe 결과는 모든 변형에서 iframe 밖과 같았다.

| 항목 | 기준(At.js) | EasyMDE2+Tribute | EasyMDE2+hint | EasyMDE3+플러그인 |
|---|---|---|---|---|
| T1 `hello @al`에서 후보가 뜬다 | 뜸 | 뜸 | 뜸 | 뜸 |
| T2 `@alic`까지 치고 Backspace해도 후보가 유지된다 | 유지 | **`@alic`에서 이미 닫힘** | 유지 | 유지 |
| T3 줄 중간(`aaa bbb`의 가운데)에 ` @al` 입력 후 Enter | `aaa @alice  bbb` | **줄바꿈이 들어가 `aaa @al\nbbb`** | `aaa @alice  bbb` | `aaa @alice  bbb` |
| T4 Enter로 삽입하고 이어 입력하면 캐럿이 삽입 뒤에 있다 | `hi @alice X` | **줄바꿈만 들어감** | `hi @alice X` | `hi @alice X` |
| T4b Tab으로 삽입 | `@bob ` | **탭 문자가 들어감** | `@bob ` | `@bob ` |
| T5 한글 입력기 조합 중 후보가 뜬다(CDP 시뮬레이션) | 뜸 | 뜸 | 뜸 | 뜸 |
| T6 Alt+Enter | 삽입 | 삽입 | **무시(후보 유지)** | 삽입 |
| T7 후보 상자 위치(캐럿 아래 간격) | 2px | 5px | 0px(단어 시작에 맞춤) | 0px |
| T8 폼 제출 시 `name=body` 값 | 정상 | 정상 | 정상 | 정상(`forceSync` 켠 상태) |
| 페이지 오류 | 없음 | 없음 | 없음 | 없음 |

## 해석
### At.js에 패치했던 네 문제는 기본 상태에서 생기는가
| At.js 패치(v1.6 이력) | 새 조합에서 |
|---|---|
| IME 버그(2018-03, 이벤트가 `undefined`일 때 방어) | At.js 자체 코드의 결함이라 재현되지 않는다. 시험한 모든 조합에서 페이지 오류가 없었다. 다만 입력기가 조합하는 동안 Enter가 후보 선택과 조합 확정 중 무엇이 되어야 하는지는 변형마다 다르고, 시험(CDP의 `imeSetComposition`과 `press("Enter")`)이 실제 입력기의 키 흐름(`keyCode` 229)과 같지 않아 확정적인 결론은 내지 못했다 |
| 2단 보기 monkey patch(2017-04, iframe 안의 부모 창 좌표) | 재현되지 않는다. 후보 상자가 편집기와 같은 문서 안에 만들어져 iframe 안에서도 위치와 동작이 밖과 같았다(srcdoc 같은 출처 iframe) |
| Alt 키 오동작(2014-05) | 어느 조합에서도 오류나 문서 손상은 없다. Alt+Enter의 결과만 갈린다(EasyMDE2+hint는 무시, 나머지는 삽입) |
| 캐럿 버그(2014-04) | 재현되지 않는다. 삽입 뒤 캐럿이 삽입 텍스트 뒤에 있고 후보 상자도 캐럿 아래에 붙는다 |

즉 **At.js 패치 네 건은 At.js의 구조에서 나온 문제라 EasyMDE의 어느 조합에서도 같은 모양으로 생기지 않았다.** 대신 EasyMDE2+Tribute 조합은 이 프로젝트가 이미 기록한 다른 종류의 결함(CodeMirror 5의 숨은 `<textarea>`)이 재현되었다.

### 문제가 있을 때 At.js의 패치를 적용해 해결할 수 있는가
- **EasyMDE2+Tribute의 문제(T2, T3, T4, T4b)**: 위 네 패치와 무관하다. 원인이 Tribute가 읽는 숨은 `<textarea>`가 CodeMirror 5에서 비는 것이라 At.js 패치를 옮길 대상이 없다. 해결하려면 입력란에 붙이는 방식을 버리고 CodeMirror 5의 자체 자동완성(`show-hint`)이나 직접 만든 팝업으로 가야 하고, 그것이 EasyMDE2+hint와 EasyMDE3+플러그인이다.
- **EasyMDE2+hint**: 8개 항목이 모두 기준과 같거나 허용 가능한 차이다. 다만 At.js가 하던 일(`@`와 `#`의 서버 조회, 입력이 멈춘 뒤 300ms 지연, 후보 개수 제한, 정렬 함수, `[` 제목 머리말의 `beforeInsert`)을 우리 `hint` 함수로 다시 구현해야 하고, Alt+Enter를 삽입으로 하려면 `extraKeys`를 더해야 한다. 이번에 만든 구현은 `@`, `:`, `#`만 있고 서버 조회는 가짜 데이터다. 또한 CodeMirror 5는 마지막 릴리스가 5.65.21(2026-02-07)로 유지만 되는 상태다.
- **EasyMDE3+플러그인**: 8개 항목이 모두 기준과 같았다. 하지만 이 플러그인은 우리가 쓰고 유지하는 코드이고, EasyMDE 3은 아직 beta이며 확장 API가 없어 이 길밖에 없다.

## 한계
- Chromium만 시험했다(한글 조합은 CDP 시뮬레이션, 실제 입력기는 자동화할 수 없다). Firefox와 WebKit은 돌리지 않았다.
- iframe은 srcdoc(같은 출처) 하나로 만든 것이고 실제 2단 보기(`pageslide`가 띄우는 이슈 화면)가 아니다.
- 자동완성은 `@`, `:`, `#`만 가짜 데이터로 구성했다. `[` 제목 머리말, 서버 응답, 입력 지연, 이슈 정렬 함수의 세부는 시험하지 않았다.
- EasyMDE를 Yona의 실제 템플릿, 첨부 붙여넣기, 임시 저장, 서버 렌더 미리보기, 알림 수신자와 합친 통합 시험은 하지 않았다. 모양(Yona CSS와의 어울림)도 보지 않았다.
- 기준의 T7 가로 차이와 변형별 위치 차이는 후보 상자를 어디에 맞추는지의 설계 차이이고 오류라고 볼 근거는 없다.

## 재현
시험 스크립트와 결과는 `e2e/editortrial.tmp/`(추적하지 않는 파일)에 있다. 임시 폴더에 `easymde@2.21.0`, `easymde3@npm:easymde@3.0.0-beta.1`, `tributejs@5.1.3`, `esbuild`를 `--ignore-scripts`로 설치한 뒤 `esbuild v3entry.mjs --bundle --format=iife`로 EasyMDE 3 묶음을 만들고 `node trial.mjs [atjs,cm5tribute,cm5hint,cm6v3]`로 돌린다(경로는 스크립트 앞부분의 `N`, `R` 상수).
