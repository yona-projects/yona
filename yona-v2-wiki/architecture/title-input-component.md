---
title: title-input 컴포넌트 (제목 머리말 자동완성)
type: architecture
sources:
  - ../../components/title-input/README.md
  - ../../components/title-input/src/title-input.js
  - ../../components/title-input/src/query.js
  - decisions.md
updated: 2026-11-08
---

# title-input 컴포넌트 (제목 머리말 자동완성)

`D-102`에 따라 이슈 제목의 `[` 머리말 자동완성을 Lit 요소 `<title-input>`으로 만들고(`components/title-input`) yona의 세 화면(`issue/create`, `issue/edit`, `board/create`)에 붙였다. v1.6은 `<input id="title">`에 At.js 1.5.3을 붙였다. 컴포넌트는 `yona`와 분리된 저장소에 있고 yona에는 빌드한 `dist/title-input.min.js`만 `lib/title-input/`으로 복사해 쓴다(sha256은 `js-vendor-manifest.tsv`). 본문 멘션은 그대로 At.js다.

## At.js에서 옮긴 것
| 항목 | 규칙 | 시험 |
|---|---|---|
| 질의 | `[` 뒤 글자가 영문, 숫자, `_`, `'`, `.`, `+`, `-`, 라틴 확장 글자이거나 한글 등으로만 됨(섞이면 닫힘), 공백이 들어가면 닫힘, 최대 20자, 단어 중간의 `[`도 열림 | `query.test.mjs` |
| 걸러내기와 정렬 | `searchText`에 질의가 들어 있는 후보, 사용 빈도 내림차순, 분류(대소문자 무시) 오름차순, 이름 오름차순, 최대 10개 | `query.test.mjs`, `element.test.mjs` |
| 서버 조회 | `GET src?query=`, `Accept: application/json`, `X-Requested-With`, 입력이 멈춘 뒤 300ms, 오래된 응답 버림, 진행 표시 | `element.test.mjs` |
| 키 | 위/아래/Ctrl+P/Ctrl+N(끝에서 처음으로), Enter와 Tab 선택, Space는 선택하지 않음, Esc 닫기, Alt+Enter 선택 | `element.test.mjs` |
| 마우스와 포커스 | 올리면 선택, 클릭하면 삽입, 포커스를 잃으면 300ms 뒤 닫힘 | `element.test.mjs` |
| 삽입 | `[이름]`(접미 공백 없음), 라벨은 글자를 남기지 않고 `[질의`만 지움, 캐럿은 삽입 글자 뒤 | `element.test.mjs`, `glue.test.mjs` |
| 모양 | 흰 바탕, 선택 항목 파란 바탕, 강조 글자, 분류는 라벨 색 | `element.test.mjs`(색, 강조) |

## v1.6 패치 4건
| 패치 | 지금 사본 | 컴포넌트 |
|---|---|---|
| Alt 키 오동작(2014-05) | 없음(1.5.1 갱신 때 사라짐) | 보조 키만으로는 조회하지 않음, 닫은 뒤에도 다시 열리지 않음 |
| 캐럿 버그와 Firefox 한글 `keyfix`(2014-04) | 없음(`yona.Mention.js`와 `yona.TitleHeadAutoCompletion.js`의 100ms 폴링만 남음) | `input` 이벤트로 조합 중에도 갱신, 폴링 불필요 |
| 2단 보기 monkey patch(2017-04) | 있음 | 요소가 속한 창을 기준으로 위치(iframe 시험) |
| IME 이벤트 `undefined` 방어(2018-03) | 있음 | 조합 종료 뒤 요소가 사라져도 오류 없음 |

## At.js와 다른 점
- 후보 목록과 입력란이 Shadow DOM 안이라 페이지의 Bootstrap 클래스가 닿지 않는다. 모양은 CSS 변수와 `::part()`로 맞춘다(연동 때 화면 확인 필요).
- 목록은 캐럿 줄이 아니라 입력란 아래 2px에 `[` 글자의 x로 놓는다(한 줄 입력란이라 차이가 작다).
- 같은 질의는 keyup이 와도 다시 묻지 않는다(At.js는 선택 항목이 처음으로 돌아갔다).
- 강조는 질의를 글자 그대로 찾고 서버 값을 HTML로 해석하지 않는다(At.js는 `.`이 모든 글자와 맞고 HTML을 해석했다).
- `category`가 `null`이거나 `searchText`가 없는 후보에서 예외를 내지 않는다.
- 서버 오류에는 목록을 닫고 `title-input-error`와 `loadend`를 보낸다(At.js는 진행 막대가 멈췄다).
- 조회 중에 같은 입력란에서 조합이 끝나면 확정된 글자로 한 번 더 조회한다(At.js와 같음).
- Enter 암묵 제출을 컴포넌트가 대신한다(Shadow DOM 안 입력란은 브라우저가 제출하지 않음).

## 폼 요소로서의 동작
`ElementInternals`(폼 연동 커스텀 요소)라서 `FormData`, `form.elements`, `el.form`, `form.reset()`(`value` 속성으로 되돌림), `required`와 `maxlength` 검증, `<label for>` 포커스에 일반 입력란처럼 참여한다(Chromium과 Firefox 시험). Safari는 시험하지 못했다.

## 크기와 불러오는 방법
| 방식 | 요청 | 크기 |
|---|---|---|
| 빌드한 단일 번들 | 1건 | 26KB(gzip 10KB), import map 불필요 |
| 소스 그대로 + import map | 9건 | 43KB(gzip 합 18KB), Lit 6개 파일과 import map 필요 |

Lit 자체는 16.6KB라 번들의 이득은 우리 소스를 줄이는 데서 나온다.

## 시험과 한계
시험 142개(순수 로직 18, 요소 100(Chromium과 Firefox), yona 라벨 연결 12). 번들 모드로 요소 시험 112개를 다시 돌렸다. 한글 입력기는 조합 이벤트를 흉내 낸 것이다. WebKit은 시험하지 못했다.

## 연동 결과 (2026-11-08, A-408)
- 템플릿 3곳의 `<input id="title">`을 `<title-input id="title" class="text title" ...></title-input>`으로 바꿨다(`form-control` 제거, `error` 표지와 `th:value`, `maxlength`, `tabindex`, `th:title` 유지). 번들은 세 화면만 `type="module"`로 불러온다.
- `yona.TitleHeadAutoCompletion.js`는 `yonaTitleHeadModule({target, url})` 호출 형태를 유지하고 `title-input-select`, `loadstart`, `loadend` 이벤트에 연결하는 코드로 바꿨다(라벨 선택, 단일 선택 분류, select2 갱신, 알림, 진행 막대).
- 스타일은 `_page.scss`의 `.content-wrap` 안에 호스트와 `::part(input)` 규칙을 더했다(옛 `.content-wrap .title`과 `input.text.title`의 값을 크롬 개발자 도구로 읽어 옮김).
- 교체 전후 제목 영역 픽셀 비교: 게시글 작성(보통, 포커스)과 이슈 작성(오류)은 동일, 이슈 작성과 수정의 제목 영역도 동일하다.

### 실제 앱 확인에서 찾아 고친 결함
| 결함 | 원인 | 고침 |
|---|---|---|
| 목록이 열리지 않음 | 템플릿 호출의 선택자가 옛 `input[id=title]` | 선택자를 `title-input#title`로(시험으로 고정) |
| 제목에 "bug"를 치면 게시판으로 이동 | 전역 단축키(`yona.ShortcutKey.js`, H/B/C/I/M/P)가 키 이벤트의 `target` 태그 이름으로 입력란을 판단하는데 Shadow DOM 경계에서 `target`이 호스트로 바뀜 | `composedPath()[0]`을 실제 대상으로 봄. `yona-lib.js` 번들을 다시 빌드(변경 전 소스로 다시 빌드하면 번들이 같음을 확인) |
| 후보를 Enter로 고르면 포커스가 본문으로 감 | 페이지의 Enter 처리가 무조건 본문으로 포커스를 옮김. v1.6은 At.js가 입력란에 포커스를 되돌려 상쇄 | 후보를 고르는 데 쓴 Enter는 컴포넌트가 전파하지 않음 |
| 게시글 작성에서 포커스와 Enter 처리가 붙지 않음 | `yona.board.Write.js`가 `input#title`로 찾음 | `#title`로 |
| Enter가 페이지의 `preventDefault()`를 무시하고 제출 | 안쪽 입력란의 처리기가 호스트의 페이지 처리기보다 먼저 돌아 바로 제출 | 이벤트 전파가 끝난 뒤 `defaultPrevented`를 보고 제출 |
| 서버가 채운 제목에서 캐럿이 맨 끝으로 감 | 안쪽 입력란의 값을 property로 채움(일반 입력란의 `value` 속성은 캐럿이 맨 앞) | 초기값이 적용될 때 캐럿을 맨 앞으로 |
| 호스트에 밑줄이 한 줄 더 그려져 높이가 1px 큼 | 옛 `.content-wrap .title` 규칙이 호스트(`class="title"`)에도 적용 | 호스트에 `border: none` |
| 안내 글자 색이 다름 | 컴포넌트 기본 #757575, 옛 입력란은 #595959 | `::part(input)::placeholder` |

### 확인하지 못한 것
Safari(WebKit), 실제 한글 입력기, 연동 화면의 모바일 폭. 교체 전 기준 앱이 옛 select2 3.4.5 jar라서 이슈 수정 화면 아래쪽(부모 이슈 선택)의 높이 차이(select2 4.x BS5 테마)는 이번 변경과 무관하다. 다만 **같은 영역의 글자 차이**(교체 후 "프로젝트 선택")는 select2 4.1.0 교체(D-95)에서 생긴 실제 회귀였고 고쳤다(A-409, R-138).
