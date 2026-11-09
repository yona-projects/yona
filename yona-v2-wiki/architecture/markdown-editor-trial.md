---
title: EasyMDE 시험 (멘션 자동완성과 At.js 패치 항목)
type: architecture
sources:
  - ../e2e/editortrial.tmp/trial.mjs
  - ../e2e/editortrial.tmp/v3entry.mjs
  - ../e2e/editortrial.tmp/fill.mjs
  - ../e2e/cmmodule.tmp/editor.js
  - ../e2e/cmmodule.tmp/run.mjs
  - ../e2e/textexp.tmp.mjs
  - decisions.md
updated: 2026-11-09
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

## 채우기 시험 (EasyMDE 3 beta + 직접 만든 플러그인, 2026-11-09)
선생님 요청(2026-11-09)으로 8개 항목을 **서버가 채운 textarea**(수정 화면의 `th:text`와 같은 조건)에서 다시 돌리고 채우기 고유 항목을 더했다. Chromium과 Firefox, 임시 폴더에서 한 시험이며 코드와 템플릿은 바꾸지 않았다(`e2e/editortrial.tmp/fill.mjs`). Firefox는 CDP가 없어 한글 조합 항목(T5b)만 돌리지 못했다.

### 8개 항목: 채워진 본문에서도 어제와 같다
T1(후보), T2(Backspace 뒤 유지), T3(줄 중간 삽입 `aaa @alice  bbb`), T4(삽입 뒤 캐럿 `hi @alice X`), T4b(Tab), T5b(한글 조합, Chromium), T6(Alt+Enter 삽입), T7(후보 상자 위치 세로 0, 가로 0), T8(폼 제출 값), `:`와 `#` 후보까지 두 브라우저에서 같았고 페이지 오류는 없었다.

### 채우기 고유 항목
| 항목 | 결과 |
|---|---|
| F1 한글, 멘션, 빈 줄이 든 본문 | 그대로 나온다 |
| F2 `<`, `&`, `</textarea><script>`, 따옴표 | 그대로 나오고 스크립트는 실행되지 않는다 |
| F4 채운 값을 고치지 않고 제출 | 원본 그대로 제출된다(편집기가 textarea를 건드리지 않는다) |
| F9 2만 줄 채우기 | 약 90ms, 길이 같음 |
| F8 `form.reset()` | 편집기 화면은 그대로이고 제출 값만 초기 값으로 돌아가 서로 어긋난다 |
| F5 외부 코드가 `textarea.value`를 바꿈(첨부 삽입 등) | 편집기에 반영되지 않고 제출 값만 바뀐다. 다음에 사용자가 입력하면 편집기 내용으로 덮어써진다 |
| F6 외부 코드가 `editor.value = ...`(공식 방법) | 편집기와 제출 값이 모두 바뀐다(공백도 보존) |
| **F10 앞뒤 공백이 의미 있는 본문** | **채울 때 앞뒤 공백과 빈 줄을 잘라 낸다.** `"    코드 블록\n\n줄 끝 두 칸  \n다음 줄\n\n"`이 편집기에서는 `"코드 블록\n\n줄 끝 두 칸  \n다음 줄"`이 된다. 고치지 않으면 제출 값은 원본이지만, **한 글자라도 고치면 잘린 값이 저장되어 코드 블록 들여쓰기가 사라진다** |
| F3, F7 | 같은 원인: 첫 줄이 빈 줄인 본문, 끝 공백이 있는 본문이 편집기에서 잘린다 |

### 해석
- **멘션 8개 항목은 채우기와 무관하게 통과**한다. 어제 결론이 채워진 편집기에서도 유지된다.
- **채우기에서 생기는 실제 결함은 EasyMDE 3의 값 설정 시 `trim`이다.** 생성자의 `initialValue` 옵션은 textarea 내용을 읽는 경로보다 약해서 빈 값이 되었다. 생성 직후 `view.dispatch({changes:{from:0,to:len,insert:원문}})`로 원문을 다시 넣으면 앞뒤 공백까지 보존되어 해결된다(복원 뒤 편집기와 제출 값이 원문과 같았다). 즉 채택하려면 이 복원 단계가 필수이다.
- 외부 코드가 `textarea.value`를 직접 바꾸는 곳(첨부 삽입, 본문 채우기)은 `editor.value = ...` 또는 `view.dispatch`로 바꿔야 한다. 지금 Yona의 `yona.Attachments.js`와 본문 템플릿 채우기가 그런 곳인지는 이번에 확인하지 않았다.
- `form.reset()` 불일치(F8)는 취소 단추가 `reset`을 쓰는 화면에서만 문제가 된다.

## CodeMirror 6 직접 사용, 번들 없이 `<script type="module">` (2026-11-09)
선생님의 요건: (1) 빌드하지 않고 `script type=module`로 CodeMirror를 포함, (2) 폼 제출과 수정 시 채우기, (3) Yona의 `@`, `:`, `#` 멘션 3종. EasyMDE를 거치지 않고 CodeMirror 6를 직접 쓰는 시험본을 `e2e/cmmodule.tmp/`에 만들어 Chromium과 Firefox에서 확인했다. 코드와 템플릿은 바꾸지 않았다.

### (1) 번들 없이 가능한가: 가능하다
- CodeMirror 6 패키지는 모두 ES 모듈 단일 파일(`dist/index.js`)을 낸다. `components/editor`가 쓰는 6개 패키지(`state`, `view`, `commands`, `language`, `autocomplete`, `lang-markdown`)의 전이 의존성까지 **20개 파일(비압축 1.4MB, gzip 약 350KB)**을 `vendor/`에 그대로 복사하고, 맨 이름 import(`@codemirror/state` 등)는 **`<script type="importmap">`으로 파일 경로에 연결**하면 번들러가 필요 없다. 소스는 4.3KB 한 파일(`editor.js`)이다.
- 20개 파일은 모두 서로를 맨 이름으로만 부르고 상대 경로 import가 없어 import map 하나로 연결된다(`importmap.json`은 의존 목록에서 자동으로 만들 수 있다).
- 단점: **요청이 21개**로 늘어난다(HTTP/2에서는 부담이 작고, 시험에서 로드 0.2~1초). `lang-markdown`이 `lang-html`, `lang-css`, `lang-javascript`(코드 블록 하이라이트용)를 끌고 와서 크기의 대부분을 차지한다. 코드 블록 하이라이트가 필요 없으면 `markdown({codeLanguages: []})`로 쓰더라도 파일 자체는 import되므로, 줄이려면 `@lezer/markdown`만 쓰는 방식이 따로 필요하다. import map 지원은 Chrome 89, Firefox 108, Safari 16.4 이상이다.

### (2) 제출과 채우기: 통과
EasyMDE 3의 문제였던 앞뒤 공백 자르기가 **생기지 않는다**(문서를 `textarea.value` 그대로 `EditorState`에 넣으므로). 숨긴 `<textarea name="body">`에 문서가 바뀔 때마다 값을 동기화하고 `input` 이벤트를 다시 발행한다.

| 항목 | Chromium | Firefox |
|---|---|---|
| 한글, 멘션, 빈 줄이 든 본문 채우기 | 같음 | 같음 |
| `<b>`, `&amp;`, `</textarea><script>` 채우기 | 같음, 스크립트 실행 없음 | 같음 |
| 앞 4칸 들여쓰기, 줄 끝 공백 두 칸, 끝 빈 줄 | 보존, 한 글자 고쳐도 보존 | 보존 |
| 고치지 않고 제출 / 실제 폼 제출(`requestSubmit`) | `body=처음 수정 @alice ` | 같음 |
| 저장 값을 서버가 다시 채우는 왕복 | 같음 | 같음 |
| reset(취소 단추) | 편집기와 제출 값이 함께 초기값 | 같음 |
| 외부 코드가 `ed.set()`으로 값 변경 | 편집기와 제출 값이 함께 바뀜 | 같음 |
| 2만 줄 채우기 | 약 0.3초 | 약 0.3초 |
`reset` 처리는 `form`의 reset 이벤트에서 편집기 문서를 `textarea.defaultValue`로 되돌리는 코드를 직접 넣어서 맞췄다. 외부 코드가 `textarea.value`를 직접 바꾸는 경우는 EasyMDE와 같게 편집기에 반영되지 않으므로, 첨부 삽입 같은 곳은 `ed.set()` 또는 `view.dispatch`를 쓰도록 고쳐야 한다.

### (3) 멘션 3종: 통과
`@codemirror/autocomplete`의 `override` 소스 하나가 At.js 규칙(공백 뒤에서 시작, 공백 없이 이어진 질의, 질의 20자까지)으로 `@`(사용자), `#`(이슈), `:`(이모지)를 처리한다. 서버 조회(`mentionType=user|issue`)는 `fetch`로 한다.

| 항목 | 결과 |
|---|---|
| 후보 표시, Backspace 뒤 유지, 줄 중간 삽입(`aaa @alice  bbb`), 삽입 뒤 캐럿(`hi @alice X`), Tab 삽입, Alt+Enter 삽입 | 두 브라우저 모두 통과 |
| `:sm` → 🙂, `#12` → 이슈 후보 두 개, 모두 Enter로 삽입 | 통과 |
| 한글 조합(`@` 뒤 `아`) | Chromium 통과(`채움 @아`), Firefox는 CDP가 없어 시험하지 못함 |
| 후보 상자 위치 | 캐럿 아래(세로 간격 0). 가로는 트리거 시작에 맞춰 캐럿보다 19~24px 왼쪽 |
| 페이지 오류 | 없음 |
이모지 목록은 시험본에서 4개만 넣었다. 실제 적용에서는 `components/editor/src/mention.ts`의 목록과 정렬 규칙(`sortUsersBySearchText`, `sortIssues`, 최대 10개)을 그대로 옮겨야 한다.

### 판단
세 요건을 모두 만족하는 방식이 번들러 없이 성립한다. EasyMDE 3 beta보다 낫다: 앞뒤 공백 자르기가 없고, 멘션이 공식 확장(`autocomplete`)을 쓰며, beta 의존이 없다. 대가는 **툴바, 미리보기, 테마를 직접 만들어야 하고(`components/editor`에 TypeScript로 있으나 번들 전제), 20개 vendor 파일을 직접 관리한다**는 점이다. 아직 확인하지 않은 것: 실제 Yona 화면, 툴바와 미리보기, 첨부 삽입과 `yona.Attachments.js` 연동, 한글 조합의 Firefox 확인, Shadow DOM 안에서의 동작.

## text-expander-element 재확인: At.js 패치 네 건 (2026-11-09, D-105 후속)
`@github/text-expander-element` 2.9.4(`combobox-nav` 사용)를 textarea에 붙여 At.js 패치 네 건의 조건을 Chromium과 Firefox로 다시 확인했다(`e2e/textexp.tmp.mjs`, 본문 13개 항목은 `/tmp/.../c-trial`의 이전 시험과 같은 결과). 서버 조회(`mentionType=user|issue`)는 가짜 응답이고 후보 상자는 `text-expander` 요소 안에 만들어진다.

| 패치 항목 | 결과 |
|---|---|
| **IME** (2018-03, 이벤트가 `undefined`일 때 방어) | `undefined` 이벤트 오류는 없다(`compositionstart/end`와 `keyup` 합성 후 페이지 오류 0). 한글 질의 `알`로 서버 후보(`알리스 박`)가 뜨고 Enter로 `@alis2 `가 삽입된다(두 브라우저). 조합 중에도 후보 목록은 보인다(At.js에서 `suspendOnComposing: false`와 같다). 조합 중 keydown이 Chrome, Firefox처럼 `key="Process"`(keyCode 229)로 오면 Enter가 후보를 고르지 않는다(`x @a` 유지, 확정 뒤 `x @아`). **Safari처럼 조합 중에도 `key="Enter"`(isComposing=true)로 오면 후보를 골라 `x @alice 아`가 된다.** 원인은 후보 목록을 갱신할 때마다 `combobox-nav`의 `Combobox`를 새로 만들어 `isComposing`이 초기화되는 것이다(`text-expander-element.js` 47, 78줄). **보호막** `text-expander`에 캡처 단계 keydown 한 줄(`e.isComposing \|\| e.keyCode === 229`이면 `stopPropagation`)을 더하면 막힌다(확인함). 이 한 줄은 컴포넌트 연결 코드에 둔다 |
| **2단 보기** (2017-04, iframe 안의 부모 창 좌표) | 페이지가 iframe 안에 있고 iframe 안 문서가 스크롤된 경우에도 후보가 열리고 선택된다. 후보 상자는 iframe 안 문서의 `text-expander` 요소 안에만 생기고 바깥 문서에는 없다(`바깥문서의상자수 0`). 좌표 보정이 필요 없는 구조라 패치 대상 자체가 없다. 긴 본문(스크롤된 textarea)의 끝에서도 입력란 안쪽에 뜬다. 실제 2단 보기(`pageslide`가 띄우는 이슈 화면)에서는 확인하지 않았다 |
| **Alt 키** (2014-05) | Alt가 눌리면 `combobox-nav`가 키를 무시한다. Alt+Enter는 삽입하지 않고(At.js는 삽입) 글자와 후보가 그대로이며, Alt+ArrowDown과 Alt 단독도 문서를 깨지 않고 이어지는 Enter는 정상 삽입된다. **차이는 Alt+Enter 하나**이고 오류는 없다 |
| **캐럿** (2014-04) | 줄 중간 삽입 뒤 캐럿이 삽입 글자 바로 뒤에 있고(`aaa @alice X bbb`, 캐럿 12), 이모지(서로게이트 쌍) 삽입 뒤 이어 입력이 맞다(`x 🙂 X`). 후보 상자는 캐럿 줄 바로 아래에 뜬다. 삽입 뒤 Ctrl+Z는 되돌려지지 않는다(At.js와 같다) |

### 해석
- **네 건 모두 조건을 만족한다.** 단 두 가지가 남는다: (1) Safari형 조합 중 Enter를 막는 보호막 한 줄이 필요하다. (2) Alt+Enter 삽입은 지원되지 않는다. Alt+Enter 삽입은 v1.6 At.js의 동작이지만 사용자가 쓰는 단축키로 알려진 것은 없고, 필요하면 `text-expander-value` 대신 별도 keydown으로 `Alt+Enter`를 `Enter`로 바꿔 줄 수 있다.
- 실제 한글 입력기(Chromium CDP 합성이 아닌)로 쓰는 확인은 이번에도 하지 못했다. 선생님이 직접 확인해 주셔야 한다.
- 이전 제목 머리말(`[`) 시험 결과(D-102)는 라벨 선택에 내부 동작에 기대는 우회 코드가 필요해서 제목 입력란에는 `<title-input>`을 쓰고, 본문만 `text-expander`로 가는 구성이 맞다.
