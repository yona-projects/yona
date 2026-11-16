---
title: 스크립트 TypeScript 전환 조사: 현황과 단계 후보
type: architecture
sources:
  - ../../src/main/resources/static/assets/javascripts/esm/
  - ../../src/main/resources/static/assets/javascripts/common/
  - ../../src/main/resources/static/assets/javascripts/lib/
  - ../../src/main/resources/templates/
  - ../../src/test/js/harness.mjs
  - ../../src/test/js/run.mjs
  - ../../src/test/resources/js-vendor-manifest.tsv
  - ../../build.gradle.kts
  - ../../build-tools/js-bundle/
  - ../../src/main/kotlin/dev/yona/web/AssetFilter.kt
  - ../../src/main/kotlin/dev/yona/web/StaticAssets.kt
  - ../../src/main/kotlin/dev/yona/web/JsMessagesController.kt
  - ui-ia-gitlab.md
  - ui-htmx-plan.md
  - ui-client-storage.md
  - ui-shortcuts.md
  - esm-skeleton.md
updated: 2026-11-11
---

# 스크립트 TypeScript 전환 조사: 현황과 단계 후보

> **상태: 사실 조사(2026-11-11, 읽기 전용).** 선생님이 새 UI의 스크립트를 TypeScript로 재작성하라고 하셨다(D-134~D-137 갱신 UI 계약과 함께 들어온 지시). 이 문서는 **지금 코드가 어떤 모양인지**, **무엇이 걸림돌인지**, **어떤 순서로 옮길 수 있는지**를 측정해 적은 것이다. 설계 결정은 총괄이 한다. 그래서 선택이 필요한 곳은 "선택지와 권장안과 이유"로 적었다. 코드, 템플릿, 시험, 빌드는 건드리지 않았고 Gradle, npm, 앱 실행도 하지 않았다(`grep`, `find`, `wc`, 파일 읽기만 했다).
>
> **측정 방법의 한계.** 줄 수는 `wc -l`이다. jQuery, bootstrap, select2 사용 횟수와 의존 표시는 **주석을 걷어낸 뒤 정규식으로 센 근사치**이다(코드 의미를 해석한 것이 아니다). "최상위 부작용"은 모듈 평가 때 실행되는 문서와 창 수준 핸들러, 타이머, 즉시 실행 호출을 소스에서 직접 읽어 판정했다. 시험 수는 `grep`으로 센 값이고 시험을 실행해 확인한 값이 아니다.

## 0. 한눈에 보기

- **옮길 대상**: `esm/**` 88개 모듈 18,636줄, `common/` 3개 1,008줄, 번들 2개(`yona-layout.js`, `yona-common.js`, 둘 다 압축된 산출물), 템플릿 인라인 JS 119블록 2,131줄(템플릿 106개가 `<script`를 가지고 그 가운데 102개가 인라인 블록을 가진다).
- **이미 ES 모듈이다.** `esm/` 88개는 모두 `<script type="module">`로 불러온다(`common/scripts.html:56`, 화면 템플릿의 `esm/service/*.js`). 옛 전역 `yona.*`와는 `register()`로 잇는다(`esm/namespace.js`). 그래서 **전환은 "모듈 시스템 변경"이 아니라 "타입 붙이기와 빌드 단계 추가"**가 중심이다. 이것이 가장 큰 유리한 사실이다.
- **빌드 단계가 지금은 없다.** `esm/` 파일은 저장소에 있는 그대로 브라우저가 받는다. 반면 Gradle에는 이미 npm을 부르는 `Exec` 작업이 하나 있다(`build.gradle.kts:266` `installSass`, `:274` `compileSass`, `:308` `processResources.from(compileSass)`). 그러므로 "Gradle 한 번의 명령으로 재현"하는 TS 빌드는 **같은 방식의 두 번째 작업을 더하는 일**이고 새로운 종류의 의존(Node)이 아니다.
- **`package.json`은 저장소 루트에 없다.** 도구별로 흩어져 있다(`build-tools/sass/package.json`, `build-tools/js-bundle/package.json`, 상위 `~/yona-convert/package.json`은 Playwright용). `frontend/`는 추적되지 않는 폴더이고 `node_modules/@hotwired/turbo`(8.0.23)와 잠금 파일뿐이다(6.1절).
- **가장 먼저 부딪힐 계약**: (1) 템플릿 인라인이 `yona.Mention({...})`처럼 **생성자 없이 함수로** 부르는 전역이 있다. class로 바꾸면 깨진다. (2) `common/yona.Common.js`가 `$yona = yona.Common = ...`로 **선언 없는 암묵 전역**을 만든다. 모듈(엄격 모드)로 옮길 수 없는 문장이다. (3) `common/yona.Csrf.js`는 **XHR와 `fetch`를 가로채는 클래식 스크립트로, 요청을 보내는 어떤 코드보다 먼저 실행**되어야 한다. (4) 시험 하네스가 **소스 텍스트를 정규식으로 잘라 클래식 스크립트로 바꿔** 실행한다(`classicFromEsm`). TS 소스는 이 방식으로 못 읽는다.
- **시험 체계는 Gradle 밖이다.** `src/test/js/run.mjs`는 수동 실행이다(`build.gradle.kts`에 node 시험 연결 없음, `grep` 확인). Kotlin 시험 11개가 JS 소스 **원문**을 읽는다(5절).

### 0.1 규모 표

| 영역 | 파일 | 줄 | 비고 |
|---|---:|---:|---|
| `esm/` 루트 | 29 | 5,937 | 공통 모듈(첨부, 멘션, 요청, 단축키 등) |
| `esm/ui/` | 12 | 2,320 | 위젯(달력, select2 감싸기, 대화상자, 자동완성 등) |
| `esm/service/` | 47 | 10,379 | 화면별 모듈. 5개 안팎의 큰 파일이 절반 가까이 |
| **`esm/` 합계** | **88** | **18,636** | `index.js` 포함 |
| `common/*.js` | 3 | 1,008 | `yona.Common.js` 797, `yona.Csrf.js` 84, `yona.Htmx.js` 127. 클래식 스크립트 |
| `yona-layout.js` | 1 | (3줄, 161,412바이트) | 압축 산출물: nprogress + jquery 3.7.1 + htmx + `yona.Common.js` + `yona.Htmx.js`(`build-tools/js-bundle/bundles.mjs`) |
| `yona-common.js` | 1 | (3줄, 22,173바이트) | 압축 산출물: mustache + xss(`Filter`) |
| 템플릿 인라인 JS | 119블록 | 2,131 | 템플릿 102개. `th:inline="javascript"` 블록 75개, 서버 값 표현식 212개 |
| 템플릿 JS 모양이 아닌 `<script>` | 18블록 | (별도) | `type="text/x-mustache"` 13개, `type="text/x-yona-template"` 5개(문자열 조각이지 코드가 아니다) |
| `lib/` | 파일 다수 | | 3절. ace만 9.5MB(모드 파일 202개) |
| JS 시험 | 파일 67 | 7,063 | `src/test/js/*.test.mjs`. 항목 약 650(`run:` 개수). 하네스 172줄, `run.mjs` 20줄 |

- 큰 모듈 상위: `service/Migration.js` 1,006, `Files.js` 729, `service/code.Diff.js` 646, `Attachments.js` 620, `service/code.Browser.js` 605, `service/code.SvnDiff.js` 558, `service/issue.View.js` 509, `ui/Select2.js` 492, `Mention.js` 470, `CodeCommentBlock.js` 457, `service/user.Setting.js` 433. 이 11개가 6,525줄로 전체의 35%이다.
- 인라인 JS 상위 10개 템플릿이 954줄로 인라인 전체의 45%이다: `issue/view.html` 164, `common/scripts.html` 144, `layout/migration-layout.html` 128, `common/analytics.html` 110, `issue/edit.html` 91, `board/view.html` 86, `index/notifications.html` 70, `issue/create.html` 59, `layout/layout.html` 53, `site/user-list.html` 49.

## 1. 전체 규모와 구성: 모듈별 표

### 1.1 읽는 법

- **DOM 결합도**: 상은 jQuery 호출이 25곳 이상이거나 화면 마크업 구조에 크게 의존, 중은 8~24곳 또는 특정 선택자와 템플릿 id에 묶임, 하는 그보다 적고 DOM을 거의 만지지 않음. 모듈마다 근사치이며 일부는 읽은 뒤 손으로 고쳤다.
- **의존 열**의 `$yona`, `Messages`, `NProgress`, `ctx`(`window.yonaContextPath`)는 전역 이름이다. `yona.X`는 다른 모듈을 **전역 이름으로** 부르는 것(import가 아니다). `import X`는 진짜 ES import이다. 표에 `htmx`가 있으면 전역 `htmx`를 쓴다.
- **최상위 부작용(D-136 swap-safe 판정용)**: "있음"은 **모듈 평가 때(처음 한 번)** 문서나 창 수준 핸들러, 타이머, 즉시 실행 호출을 단다는 뜻이다. 이런 모듈은 htmx로 본문만 바꾼 뒤 두 번째 방문에서 다시 실행되지 않거나(초기화 누락), 반대로 문서 수준이라 한 번만 걸려 문제가 없을 수 있다. "평가 때는 없음. 생성 때 ..."는 모듈 안의 생성자가 호출될 때 문서나 창 수준 핸들러를 다는 것으로, **화면을 다시 초기화하면 중복 등록**될 위험이 있다(`ui-htmx-plan.md` 5절). "없음"은 정의만 하는 순수 모듈이다.
- **시험 열**은 `src/test/js/*.test.mjs` 파일 이름(`.mjs` 생략)이다. 하네스 파일(`harness.mjs`)은 `util`, `dates`, `http`를 모든 시험 페이지에 항상 불러오므로 개별 시험으로 세지 않았다.
- **묶음 번호**는 7절의 전환 순서이다. `0`은 도구 체인과 계약 다리, `N4(대체)`, `N3(대체)`는 새 UI 모듈이 옛 모듈을 **대체**하므로 TS로 이식하지 않고 대체 전까지 JS를 그대로 두는 것을 뜻한다.

### 1.2 `esm/` 모듈 88개 (묶음별)

묶음 표에서 상세 근거는 각 파일의 머리 주석과 `import` 줄이다. 아래 표는 묶음 0, 1, 2, 4, 5, 6, 7, N4, N3 순서로 정렬했다(묶음 3은 `esm/`가 아니라 1.3절의 클래식 파일이다).


#### 묶음 0 (모듈 1개, 46줄)

| 경로(`esm/`) | 줄 | 한 줄 용도 | 의존 | DOM | 시험 | 최상위 부작용 | 난이도, 이유 |
|---|---:|---|---|---|---|---|---|
| `index.js` | 46 | 진입점: 모듈을 import하고 `yona.esm.ready`와 `yona:esm-ready`를 발생 | ES import 22개(전부 `esm/`), 전역 `yona` | 하 | `esm.test`, Kotlin `JsLibraryVersionsTest` | 있음(전역 `yona.esm` 설정, 이벤트 발행) | 하: import 목록뿐. 새 모듈이 늘면 이 파일이 바뀐다 |

#### 묶음 1 (모듈 14개, 1619줄)

| 경로(`esm/`) | 줄 | 한 줄 용도 | 의존 | DOM | 시험 | 최상위 부작용 | 난이도, 이유 |
|---|---:|---|---|---|---|---|---|
| `dates.js` | 248 | `moment` 대체: 날짜 검사와 상대 시각(`yona.dates`) | 없음 | 하 | dates.test | 없음(모듈 상수만) | 하: 순수 함수, 언어는 `window.yonaLanguage`를 읽음 |
| `http.js` | 228 | `fetch` 기반 `yona.http`(ajax/get/post/getJSON) | 없음 | 하 | highlight.test, http.test | 없음 | 중: `$.ajax` 호환 모양의 옵션과 반환(`then/done/fail`)이라 타입이 느슨하다. 거의 모든 화면이 부른다 |
| `ui/FormValidator.js` | 211 | 양식 검사기(가입, 비밀번호) | 없음 | 하 | orgmisc.test | 없음(`prototype` 대입만) | 하: 의존 없음. `prototype` 방식은 class로 바꿀 때 호출 모양 유지 확인 |
| `ui/Elevator.js` | 130 | 이슈, 게시글의 맨 위, 맨 아래 단추 | 없음 | 하 | `smalllibs.test` 일부, Kotlin `JsLibraryVersionsTest`(존재) | 있음(`document` scroll 리스너, 생성 때 단다) | 하: jQuery 없음(주석 제외). 전용 DOM 시험이 없어 이식 전에 시험 추가 |
| `KeyControl.js` | 118 | textarea의 Tab, Shift+Tab 들여쓰기(`window._tab/_untab` 전역) | 없음 | 하 | keycontrol.test | 없음(전역 함수 등록) | 중: `execCommand` 사용(lib.dom에는 폐기 표시). 전역 함수 이름 유지 필요 |
| `ui/Favicon.js` | 113 | 탭 파비콘에 배지를 그림 | 없음 | 하 | `smalllibs.test` 일부, Kotlin `JsLibraryVersionsTest`(존재) | 없음 | 하: canvas 코드, 의존 없음 |
| `OriginalMessage.js` | 96 | 이메일 회신의 원문 부분을 접음 | jQuery | 하 | jqutil.test | 없음 | 하: 작음 |
| `webauthn.js` | 83 | WebAuthn 등록, 로그인 호출 래퍼(전역 `yona.webauthn` 계열) | 없음 | 하 | `keys.test`(+Kotlin WebAuthn 시험) | 없음(IIFE, 호출 때 동작) | 중: `navigator.credentials` 타입은 lib.dom에 있으나 Base64URL 변환과 서버 JSON 모양을 타입으로 적어야 한다 |
| `Clipboard.js` | 80 | 클립보드 복사 도우미(`yona.Clipboard.copy`) | 없음 | 하 | clipboard.test | 없음 | 하: 표준 API뿐, 시험 있음 |
| `ReceiverList.js` | 79 | 알림 수신자 목록 조회(`findNotiReceiversHandler` 전역) | $yona, NProgress, yona.http, import util | 하 | receiver.test | 없음 | 하: 전역 이름 1개를 템플릿이 부름(`issue/view.html`) |
| `ui/ItemSearch.js` | 70 | 입력란으로 목록 항목 거르기 | 없음 | 하 | Kotlin `ItemSearchModuleTest`(원문) | 있음(`document` focusin 위임을 평가 때 한 번) | 하: Kotlin 원문 시험(`ItemSearchModuleTest`)만 있다. DOM 시험 추가 필요 |
| `namespace.js` | 68 | `register`/`ensureNamespace`: 점 이름을 전역 `yona.*`에 등록하는 다리 | 없음 | 하 | esm.test | 없음 | 하: 순수 함수 2개, 의존 없음. TS 전환의 계약 다리가 되므로 가장 먼저 옮긴다 |
| `util.js` | 66 | 작은 도우미 모음(`yona.util`) | 없음 | 하 | util.test | 없음 | 하: 순수 함수 |
| `ui/SubmitOnce.js` | 29 | `data-submit-once` 폼의 이중 제출 방지 | 없음 | 하 | Kotlin `TemplateContractRegressionTest`(속성 존재만) | 있음(`document` submit, `window` pageshow 리스너를 모듈 평가 때 한 번) | 하: 29줄. 위임 리스너 1회 등록이라 swap-safe 모양에 가까움. 전용 시험 없음 |

#### 묶음 2 (모듈 8개, 1791줄)

| 경로(`esm/`) | 줄 | 한 줄 용도 | 의존 | DOM | 시험 | 최상위 부작용 | 난이도, 이유 |
|---|---:|---|---|---|---|---|---|
| `ui/Select2.js` | 492 | select2 감싸기(`yona.ui.Select`), 담당자, 라벨 선택 | jQuery, select2, $yona, Messages | 상 | select2.test | 있음(`$(() =>{` 문서 준비) | 상: 492줄, select2 플러그인 호출 33곳, jQuery 35곳. `@types/select2`(일반 지식)와 옵션 모양 맞춤. htmx 교체 뒤 정리 문제(D-96) |
| `ui/Typeahead.js` | 407 | `yona.ui.Typeahead` 자동완성 입력 | jQuery, $yona, ctx | 중 | typeahead.test | 없음(정의만) | 상: 407줄, 키보드/IME(`isComposingKey`)/서버 질의 상태 복합, `yonaContextPath` 사용 |
| `ui/Calendar.js` | 327 | vanilla-calendar-pro 감싸기(`yona.ui.Calendar`) | jQuery, $yona | 중 | calendar.test | 있음(`$(() =>{` 문서 준비) | 중: 외부 라이브러리 타입(3.x 자체 포함, 일반 지식)을 쓰면 오히려 쉬워짐. 언어는 `yona.language` |
| `ui/Dropdown.js` | 175 | `yona.ui.Dropdown` 선택 드롭다운(`.btn-group[data-name]`) | jQuery, $yona | 중 | toast.test | 없음(정의만). 생성은 `scripts.html` 인라인이 함 | 중: IIFE+createNamespace, jQuery 이벤트 위임 |
| `ui/Dialog.js` | 162 | `yona.ui.Dialog` 모달 알림 대화상자 | jQuery, bootstrap, $yona, Messages | 중 | dialog.test | 없음(IIFE는 정의만) | 중: `$yona.createNamespace` 사용 IIFE를 모듈로 바꾸면서 import 구조를 정해야 함. bootstrap Modal 사용 |
| `ui/Toast.js` | 117 | `yona.ui.Toast` 알림 상자 | $yona | 중 | toast.test | 없음(정의만) | 중: IIFE+createNamespace, 템플릿 `tplYobiToast`에 결합 |
| `ui/Tabs.js` | 87 | 탭 상태 유지(`yona.ui.Tabs`)와 `$yona.showTab` | jQuery, $yona | 중 | tabs.test, tasklist.test | 있음(`$(document).ready`) | 중: 문서 준비 핸들러를 `htmx:load` 규약으로 바꿔야 함 |
| `Tabs.js` | 24 | `.nav` 밖 탭 목록의 활성 표시 이동 | jQuery | 하 | tabs.test, tasklist.test | 있음(`$(document).on('shown.bs.tab')` 문서 수준, 평가 때 한 번) | 하: 24줄. 위임이 한 번만 등록돼 swap-safe |

#### 묶음 4 (모듈 17개, 3766줄)

| 경로(`esm/`) | 줄 | 한 줄 용도 | 의존 | DOM | 시험 | 최상위 부작용 | 난이도, 이유 |
|---|---:|---|---|---|---|---|---|
| `Files.js` | 729 | 파일 업로드, 목록, 삭제(`yona.Files`) | jQuery, $yona, Messages, yona.http | 중 | attachments.test, files.test | 없음(`yona.Files.init`은 `scripts.html` 인라인이 부름) | 상: 729줄, 업로더 객체와 `XMLHttpRequest` 업로드 진행률, `Messages`, `$yona`, `yona.http`, 파일 크기 설정 `maxFileSize` |
| `Attachments.js` | 620 | 첨부 UI(드롭, 붙여넣기, 미리보기)(`yona.Attachments`) | jQuery, $yona, Messages, yona.Files | 중 | attachments.test | 없음 | 상: 620줄, `Files`에 의존, 클립보드/드롭 이벤트, jQuery 14곳 |
| `Mention.js` | 470 | 본문 `@`, `#`, `:` 자동완성(`yona.Mention`) | jQuery, NProgress, yona.http | 중 | mention.test | 평가 때는 없음. 호출 때 `document` 캡처 리스너 3개를 단다 | 상: 470줄, text-expander 요소 이벤트, 서버 질의, 템플릿이 `yona.Mention({...})`를 함수로 직접 호출 |
| `Pagination.js` | 350 | 페이지 이동과 htmx 목록 교체(`yona.Pagination`) | jQuery, $yona, Messages, htmx, yona.ShortcutKey | 중 | htmx.test | 있음(`$(document).on('click.pagination.number-api')` 평가 때 한 번) | 중: htmx 이벤트와 결합 |
| `CommentAttachmentsUpdate.js` | 201 | 댓글 첨부 갱신 | jQuery, $yona, ctx, NProgress, yona.http | 중 | commentattach.test | 있음(`$(() => {`) | 중: `yonaContextPath`로 `/files` 호출 |
| `Markdown.js` | 193 | 마크다운 렌더, 코드 강조, 가공(`yona.Markdown`) | jQuery, $yona, yona.http | 중 | highlight.test | 없음 | 중: marked 4.3, highlight.js 11, xss 필터에 의존. 템플릿 `common/markdown.html`이 `init` 호출 |
| `service/temporarySaveHandler.js` | 187 | 초안 임시 저장(localStorage, 전역 `temporarySaveHandler`) | jQuery, $yona, Messages | 중 | draft.test, logindraft.test | 있음(`setInterval` 2개를 평가 때 시작) | 중: 타이머가 모듈 평가 때 시작. IndexedDB 저장소(N2)가 이 기능을 대체할 예정이라 옮기는 대신 N2로 대체 검토 |
| `Request.js` | 176 | `data-request-method` 단추의 서버 요청과 화면 갱신(`yona.Request`) | $yona, Messages | 하 | requestas.test, site.test | 있음(`DOMContentLoaded`에 `bind(document)`) | 중: `bind`는 이미 단 요소를 건너뛰므로 다시 불러도 중복되지 않는다. 첫 문서 로드에만 `DOMContentLoaded`로 걸려 있어 교체된 조각에는 `bind`를 따로 불러야 함 |
| `service/twoColumnMode.js` | 161 | 오른쪽 패널 2단 보기(`_initTwoColumnMode` 전역) | jQuery, bootstrap, NProgress | 상 | twocolumn.test | 없음(호출 때 동작) | 중: `#pageslide` 오프캔버스. D-96에서 이후 UI 개편 때 2단 보기 개선 예정 |
| `Tasklist.js` | 137 | 마크다운 체크박스 목록 갱신 | jQuery, $yona, NProgress, yona.http | 중 | tasklist.test | 있음(`$(() => {`) | 중: 서버 PUT 호출과 NProgress |
| `CommentForm.js` | 128 | 댓글 폼의 키 입력과 초안 타이머 취소(`cancelAllDraftTimers`) | jQuery, $yona, Messages, NProgress | 중 | draft.test, keys.test | 있음(`$(() =>{` 문서 준비) | 중: 전역 함수 `cancelAllDraftTimers`를 부름(temporarySaveHandler와 결합) |
| `TitleHeadAutoCompletion.js` | 118 | 제목 머리말 `[` 자동완성을 `<title-input>`에 연결 | jQuery, select2, $yona, NProgress | 중 | titlehead.test | 없음 | 중: Lit 요소 `<title-input>`(자체 컴포넌트)와 연결. 전역 `yonaTitleHeadModule` |
| `service/detectChange.js` | 81 | 다른 사람이 이슈를 고쳤는지 감지해 알림(`detectPageChange`) | jQuery, $yona, yona.http/ui.Favicon | 하 | logindraft.test | 없음(템플릿이 호출) | 하: Favicon 배지와 `yona.http` |
| `Comment.js` | 78 | 댓글 요청 처리(`yona.Request.send`) | jQuery, bootstrap, yona.Request | 중 | tasklist.test | 없음 | 하: 78줄 |
| `SubComment.js` | 68 | 답글 폼 | jQuery, $yona | 중 | subcomment.test | 있음(`$(() =>{`) | 중: jQuery 15곳, 문서 준비 블록 |
| `Subtask.js` | 36 | 하위 이슈 입력칸 표시와 select2 갱신 | jQuery, select2, $yona | 중 | tasklist.test | 있음(`$(() => {`) | 하: 36줄 |
| `service/showSubtask.js` | 33 | 하위 이슈 보기 체크박스 | jQuery | 중 | orgmisc.test | 있음(`_initShowSubtasks()`를 평가 때 즉시 호출) | 하: 33줄. 평가 때 즉시 실행 = swap-safe 아님 |

#### 묶음 5 (모듈 21개, 3364줄)

| 경로(`esm/`) | 줄 | 한 줄 용도 | 의존 | DOM | 시험 | 최상위 부작용 | 난이도, 이유 |
|---|---:|---|---|---|---|---|---|
| `service/user.Setting.js` | 433 | 사용자 설정(탭 5개, 아바타 자르기)(`yona.user.Setting`) | jQuery, bootstrap, $yona, Messages, yona.Files/http/ui.FormValidator | 중 | avatarcrop.test | 없음 | 상: 433줄, cropper.js 2.3.1, 5개 템플릿이 로드 |
| `service/organization.Member.js` | 274 | 조직 구성원 관리(`yona.organization.Member`) | jQuery, bootstrap, $yona, Messages, yona.http/ui.Typeahead | 중 | member.test | 없음 | 중: 274줄, Typeahead와 bootstrap |
| `service/project.Member.js` | 258 | 프로젝트 멤버(`yona.project.Member`) | jQuery, $yona, Messages, yona.http/ui.Typeahead | 중 | projectmember.test | 없음 | 중: 258줄 |
| `service/project.New.js` | 231 | 프로젝트 만들기와 가져오기(`yona.project.New`) | jQuery, $yona, Messages, NProgress | 중 | misc.test | 없음 | 중: 231줄, jQuery 25곳 |
| `service/site.MassMail.js` | 216 | 대량 메일(`yona.site.MassMail`) | jQuery, $yona, Messages, yona.ui.Typeahead | 중 | misc.test, privates.test | 없음 | 중: 216줄, Typeahead |
| `service/user.SignUp.js` | 208 | 가입(`yona.user.SignUp`) | jQuery, $yona, Messages, yona.http/ui.FormValidator, import util | 중 | signup.test | 없음 | 중: 208줄, FormValidator, 서버 검사 |
| `LoginDialog.js` | 193 | 로그인 대화상자와 Passkey | jQuery, bootstrap, Messages, yona.http | 중 | logindraft.test, securitykey.test | 있음(`$(() =>{` 문서 준비, `$(document.body).on` 위임) | 중: webauthn.js와 서버 JSON 결합 |
| `service/user.View.js` | 179 | 사용자 홈(`yona.user.View`) | jQuery, $yona, Messages, yona.http | 중 | smalllibs.test, userview.test | 없음 | 중: 179줄 |
| `service/project.Home.js` | 161 | 프로젝트 홈(`yona.project.Home`) | jQuery, bootstrap, $yona, Messages, yona.Clipboard/Markdown/http | 중 | projecthome.test | 없음 | 중: 161줄, Clipboard, Markdown |
| `service/project.Global.js` | 146 | 프로젝트 메뉴와 단축키 연결(`yona.project.Global`) | jQuery, $yona, Messages, yona.ShortcutKey/http | 중 | projectsettings.test | 없음 | 중: `ShortcutKey.setKeymapLink` 호출. N3와 N1이 건드림 |
| `service/project.Setting.js` | 142 | 프로젝트 설정(`yona.project.Setting`) | jQuery, $yona, Messages | 중 | projectsettings.test | 없음 | 중: `ProjectSettingScriptTest`가 원문을 읽음 |
| `service/organization.Setting.js` | 116 | 조직 설정(`yona.organization.Setting`) | jQuery, $yona, Messages, NProgress, yona.ui.FormValidator | 중 | orgmisc.test | 없음 | 중: 이미지 업로드, FormValidator |
| `service/project.ChangeVCS.js` | 107 | 프로젝트 VCS 바꾸기(`yona.project.ChangeVCS`) | jQuery, bootstrap, $yona, Messages, yona.http | 하 | projectsettings.test | 없음 | 하: 작음 |
| `service/resetPassword.js` | 107 | 비밀번호 재설정(`yona.resetPassword`) | jQuery, $yona, Messages, yona.ui.FormValidator | 하 | orgmisc.test | 없음 | 하: FormValidator |
| `service/organization.New.js` | 101 | 조직 만들기(`yona.organization.New`) | jQuery, Messages, NProgress, yona.ui.FormValidator | 하 | orgmisc.test | 없음 | 하: FormValidator |
| `service/organization.View.js` | 95 | 조직 홈(`yona.organization.View`) | jQuery, bootstrap, $yona, Messages, yona.http | 하 | orgmisc.test | 없음 | 하: 작음 |
| `service/project.Transfer.js` | 94 | 프로젝트 이전(`yona.project.Transfer`) | jQuery, bootstrap, $yona, Messages, yona.http | 하 | projectsettings.test | 없음 | 하: 작음 |
| `service/project.Webhook.js` | 87 | 웹훅(`yona.project.Webhook`) | jQuery, $yona, Messages | 하 | projectsettings.test | 없음 | 하: 작음 |
| `service/project.Delete.js` | 76 | 프로젝트 삭제(`yona.project.Delete`) | jQuery, bootstrap, $yona, Messages, yona.Request | 하 | projectsettings.test | 없음 | 하: 작음 |
| `service/organization.Global.js` | 73 | 조직 헤더 메뉴(`yona.organization.Global`) | jQuery, $yona, yona.http | 하 | orgmisc.test | 없음 | 하: 74줄 |
| `service/project.Fork.js` | 67 | 프로젝트 포크(`yona.project.Fork`) | jQuery | 하 | projectsettings.test | 없음 | 하: 작음 |

#### 묶음 6 (모듈 16개, 2402줄)

| 경로(`esm/`) | 줄 | 한 줄 용도 | 의존 | DOM | 시험 | 최상위 부작용 | 난이도, 이유 |
|---|---:|---|---|---|---|---|---|
| `service/issue.MassUpdate.js` | 379 | 이슈 일괄 변경 | jQuery, $yona, yona.ShortcutKey/ui.Dropdown | 상 | massupdate.test, privates.test | 없음 | 중: jQuery 26곳, 템플릿(`yona.tmpl`) |
| `service/issue.List.js` | 316 | 이슈 목록, 필터, 정렬, htmx(`yona.issue.List`) | jQuery, select2, $yona, Messages, htmx, yona.Htmx/Pagination/ui.Calendar, import dates | 상 | htmx.test, privates.test | 평가 때는 없음. 생성 때 `$(document).on('htmx:afterSwap')`를 단다 | 중: `htmx`와 `Pagination`, jQuery 25곳 |
| `service/git.Write.js` | 306 | 풀 리퀘스트 만들기, 고치기(`yona.git.Write`) | jQuery, $yona, Messages, NProgress, yona.Attachments/Files/http | 중 | gitwrite.test | 평가 때는 없음. 생성 때 `$(document.body).on` 위임을 단다 | 중: 첨부, 모달 |
| `service/issue.Write.js` | 140 | 이슈 쓰기(`yona.issue.Write`) | jQuery, $yona, Messages, yona.Attachments/Files/http/ui.Dropdown, import dates | 중 | writeforms.test | 없음 | 중: 첨부, 달력, 담당자 선택 |
| `service/milestone.Write.js` | 136 | 마일스톤 쓰기 | jQuery, $yona, Messages, yona.Attachments/Files/ui.Calendar | 중 | writeforms.test | 없음 | 중: 달력 `inline` 도우미 |
| `service/pullrequest.Meta.js` | 130 | 풀 리퀘스트 담당자, 라벨 선택 | jQuery, select2, $yona, Messages, yona.http | 중 | select2.test | 있음(`$(() => {`) | 중: select2 |
| `service/board.View.js` | 119 | 게시글 보기(`yona.board.View`) | jQuery, $yona, Messages, yona.Attachments/Files | 중 | listviews.test | 없음 | 중: 댓글, 첨부 결합 |
| `service/issue.Sharer.js` | 118 | 공유자 select2(`yonaIssueSharerModule` 전역) | jQuery, select2, $yona, yona.http | 중 | select2.test | 없음 | 중: select2 결합 |
| `service/pullrequest.Reviewers.js` | 113 | 풀 리퀘스트 리뷰어 칸 | jQuery, select2, $yona, Messages, yona.http | 중 | select2.test | 있음(`$(() => {` 안에서 `$(document).on` 위임 2개) | 중: select2 |
| `service/board.Write.js` | 112 | 게시글 쓰기(`yona.board.Write`) | jQuery, $yona, Messages, yona.Attachments/Files | 중 | writeforms.test | 없음 | 중: 첨부 업로더 결합 |
| `service/board.List.js` | 107 | 게시판 목록(`yona.board.List`) | jQuery, yona.Pagination | 중 | listviews.test | 없음 | 하: `div[list-container]`, Pagination |
| `service/review.List.js` | 102 | 리뷰 목록 | jQuery, yona.Pagination | 중 | listviews.test | 없음 | 하: Pagination |
| `service/issue.Assginee.js` | 93 | 담당자 select2(`yonaAssgineeModule` 전역) | jQuery, select2, $yona, yona.http | 중 | select2.test | 없음 | 중: select2 결합, 전역 이름 3개 템플릿에서 호출 |
| `service/code.Nohead.js` | 90 | 커밋 없는 저장소 안내 | jQuery, yona.http | 하 | orgmisc.test | 없음 | 하: 90줄 |
| `service/milestone.View.js` | 83 | 마일스톤 보기 | jQuery, $yona, Messages, yona.Attachments | 중 | milestoneview.test, orgmisc.test | 없음 | 하: 83줄 |
| `service/review.CommentEdit.js` | 58 | 리뷰 댓글 고치기 | jQuery, $yona, Messages, yona.http | 하 | listviews.test | 있음(`$(document).on('click', '.comment-edit')` 평가 때 한 번) | 하: 58줄. 위임 1회 |

#### 묶음 7 (모듈 9개, 5105줄)

| 경로(`esm/`) | 줄 | 한 줄 용도 | 의존 | DOM | 시험 | 최상위 부작용 | 난이도, 이유 |
|---|---:|---|---|---|---|---|---|
| `service/Migration.js` | 1006 | GitHub 이전 화면(Vue 3 앱) | jQuery, Messages, ctx, NProgress | 중 | migration.test | 있음(`$(() => {` 문서 준비) | 상: 1006줄. Vue 3 CDN 파일을 전역으로 씀. 이 화면 전용 레이아웃(`migration-layout.html`) |
| `service/issue.LabelEditor.js` | 795 | 프로젝트 이슈 라벨 편집 | jQuery, bootstrap, select2, $yona, Messages, NProgress, yona.http/ui.Typeahead | 중 | labeleditor.test | 없음 | 상: 795줄, 색 선택, 서버 CRUD, bootstrap Modal |
| `service/code.Diff.js` | 646 | 커밋, PR 변경 비교와 리뷰 댓글(`yona.code.Diff`) | jQuery, $yona, yona.Attachments/CodeCommentBlock/CodeCommentBox/Files, import util | 상 | requestas.test | 평가 때는 없음. 생성 때 `window` scroll, resize 리스너를 달고 정리하지 않음 | 상: 646줄, jQuery 39곳, CodeCommentBlock과 CodeCommentBox 결합 |
| `service/code.Browser.js` | 605 | 코드 보기(ace, 파일 트리) | jQuery, $yona, NProgress, yona.http, import dates | 중 | ace.test, browser.test, codebrowserxss.test, misc.test | 없음 | 상: 605줄, ace 1.45 타입과 모드 동적 로딩 |
| `service/code.SvnDiff.js` | 558 | SVN 비교(`yona.code.SvnDiff`) | jQuery, $yona, Messages, yona.Attachments/Files, import util | 상 | svndiff.test | 평가 때는 없음. 생성 때 `window` scroll, resize 리스너를 달고 정리하지 않음 | 상: 558줄, jQuery 63곳 |
| `service/issue.View.js` | 509 | 이슈 보기(`yona.issue.View`) | jQuery, select2, $yona, Messages, yona.Attachments/Files/Markdown/Request/ShortcutKey 외 1, import dates | 중 | issueview.test | 없음 | 상: 509줄, 첨부, Markdown, ShortcutKey, 요청 도우미, 상태 변경이 모두 얽힘 |
| `CodeCommentBlock.js` | 457 | 코드 줄 선택, 댓글 블록(`yona.CodeCommentBlock`) | jQuery | 상 | codecommentblock.test | 없음 | 상: 457줄, 선택 범위 계산 |
| `CodeCommentBox.js` | 304 | 코드 댓글 입력 상자(`yona.CodeCommentBox`) | jQuery, yona.Attachments/Files | 중 | codecommentbox.test | 없음 | 중: 304줄 |
| `service/git.View.js` | 225 | 풀 리퀘스트 보기(`yona.git.View`) | jQuery, $yona, Messages, yona.Attachments/CodeCommentBox/Files/http | 중 | gitview.test, requestas.test | 평가 때는 없음. 생성 때 `window` 입력 리스너를 단다 | 상: 225줄, `yona.Interval`, 첨부, 코드 댓글 상자(`CodeCommentBox`) 결합 |

#### 묶음 N4(대체) (모듈 1개, 250줄)

| 경로(`esm/`) | 줄 | 한 줄 용도 | 의존 | DOM | 시험 | 최상위 부작용 | 난이도, 이유 |
|---|---:|---|---|---|---|---|---|
| `Usermenu.js` | 250 | 사용자 메뉴, 즐겨찾기, 핀 사이드바, `F` 키 | jQuery, $yona, Messages, yona.http | 상 | usermenu.test | 있음(`$(() => {` 문서 준비와 `$(document).on('keypress.openFavorite')`) | 상: 250줄, jQuery 46곳. IA의 셸(N1)이 대체한다 |

#### 묶음 N3(대체) (모듈 1개, 293줄)

| 경로(`esm/`) | 줄 | 한 줄 용도 | 의존 | DOM | 시험 | 최상위 부작용 | 난이도, 이유 |
|---|---:|---|---|---|---|---|---|
| `ShortcutKey.js` | 293 | 전역 단축키(`yona.ShortcutKey`) | jQuery | 하 | shortcutkey.test | 있음(IIFE가 평가 때 `$(window).on(keydown)`을 단다) | 중: N3(단축키 선언 모듈)이 대체한다. 대체 전까지 JS 그대로 둔다 |


### 1.3 `esm/`가 아닌 클래식 스크립트와 번들

| 경로 | 줄(바이트) | 한 줄 용도 | 의존 | DOM | 시험 | 최상위 부작용 | 난이도, 이유 | 묶음 |
|---|---:|---|---|---|---|---|---|---|
| `common/yona.Common.js` | 797 | `$yona`(= `yona.Common`): `loadModule`, `alert/notify/confirm`, `render`(Mustache), `xssClean`, `sendForm`, 키 판정, 팝오버 도우미 등 약 30개 공개 함수(`:765-795`) | jQuery(12곳), bootstrap(7), Mustache(7), `Messages`, `Filter`(`lib/xss.js:904`, `yona-common.js`로 들어감) | 중 | 하네스는 실제 `yona.Common.js` 대신 대역(`COMMON_STUBS`)을 쓰고(`harness.mjs:15-38`), 실물은 `template.test`, `xssclean.test` 등 30개 가까운 시험이 불러온다 | **있음**: `window.yona = ...`와 **선언 없는 `$yona = yona.Common = ...`**(`:21-23`) | 상: 암묵 전역 대입은 모듈에서 불가(`window.$yona =`로 바꾸어야 함), 거의 모든 모듈과 템플릿이 의존, `new oModule(htOptions)`와 `.init()`로 모듈을 만드는 `registerModule`이 계약 | 3 |
| `common/yona.Csrf.js` | 84 | `XMLHttpRequest`, `fetch`, 폼 제출에 `X-XSRF-TOKEN`과 `_csrf`를 싣는다 | 없음(표준 API만) | 하 | `csrf.test` | **있음, 로드 순서가 계약**: `XMLHttpRequest.prototype.open/send`와 `window.fetch`를 교체하고 문서 `submit` 캡처 리스너를 단다. `scripts.html:41`이 본문 스크립트 가운데 첫째로 불러온다(`layout-framed.html:38`, `migration-layout.html:38`은 jQuery보다도 앞) | 하(코드는 작다). 단 **클래식 스크립트로 남겨야** 순서가 지켜진다(모듈은 지연 실행) | 3 |
| `common/yona.Htmx.js` | 127 | htmx 공통 설정(`historyCacheSize=0`, `attributesToSettle=[]`, `allowEval=false`), 진행 막대, 대체 이동, `yona.Htmx.submit` | 전역 `htmx`, `NProgress`, jQuery(`submit`에서 1곳) | 하 | `htmx.test` | **있음**: 평가 때 `htmx.config`를 쓰고 `document`에 `htmx:*` 리스너를 단다 | 중: htmx 타입(2.x는 자체 포함, 일반 지식)과 이벤트 상세(`detail`) 타입을 적어야 한다. 새 `Page` 모듈이 이 파일의 이벤트를 쓴다 | 3 |
| `yona-layout.js` | 3줄, 161,412바이트 | **압축 번들(소스가 아님)**: `nprogress.js`, `jquery-3.7.1.js`, `htmx.js`, `yona.Common.js`, `yona.Htmx.js`를 이 순서로 이어 붙여 압축(`bundles.mjs:11-17`). 레이아웃 `<head>`에서 불러옴(`layout/layout.html:41`) | | | `build-tools/js-bundle/test/bundle.test.mjs`(전역 이름 동등성) | 압축기가 최상위 이름을 바꾸지 않는 스크립트 모드이다(`vite.config.mjs`) | 중: **`yona.Common.js`와 `yona.Htmx.js`가 TS 산출물로 바뀌면 입력이 `static/…/common/*.js`에서 빌드 산출 경로로 바뀐다.** 번들 목록과 순서 계약(`expected-globals.json`)을 갱신 | 3 |
| `yona-common.js` | 3줄, 22,173바이트 | **압축 번들**: `mustache.js`, `xss.js`(`Filter`) | | | 같은 시험 | 위와 같음 | 하: 라이브러리만 있어 TS 이식 대상이 아니다 | 3(입력 목록 유지) |

- `static/assets/javascripts/` 바로 아래에는 위 두 번들과 `common/`, `esm/`, `lib/`만 있다. 그 밖의 JS 자원은 `static/assets/bootstrap/js/`(`bootstrap.bundle.js` 5.3.8, `bootstrap.bundle.min.js`, `bootstrap-switch.js`)이다. `bootstrap-switch.js`는 Yona가 쓰는 확장이다(내용은 읽지 않았다. 사용 템플릿은 `common/scripts.html:50` 한 줄이다).
- `welcome/restart.html`, `welcome/secret.html`, `layout/migration-layout.html`은 번들 없이 `lib/jquery/jquery-3.7.1.js`와 `common/yona.Common.js`를 따로 불러온다(`grep` 확인). 번들의 입력과 순서를 바꾸면 이 세 곳도 함께 바뀐다.

### 1.4 템플릿 인라인 스크립트

- **전체**: `<script`를 가진 템플릿 106개 중 102개가 인라인 블록(속성 `src` 없음)을 가진다. 나머지 4개(`common/calendar.html`, `common/comment-form.html`, `common/login-dialog.html`, `welcome/restart.html`)는 외부 파일 불러오기만 한다. JS로 실행되는 인라인 블록은 119개(`type`이 없거나 `text/javascript`, `module`, `importmap`), 2,131줄이다. `type="module"`이 붙은 인라인은 없고 모듈 불러오기는 모두 `src`가 있는 태그(115개)이다.
- **서버 값을 박는 것**: `th:inline="javascript"` 블록 75개, 표현식 212개(`[[...]]` 또는 `[(...)]`). 종류별(소스를 직접 분류한 개수):

| 종류 | 개수 | 예 | 성격 |
|---|---:|---|---|
| 주소 `@{...}` | 95 | `@{/files}`, `@{${base + '/issue/labels'}}`, `@{/users}` | 컨텍스트 경로가 붙은 URL. 가장 많다 |
| 모델 값 `${...}` | 104 | `${totalPageCount}`, `${project.name}`, `${mentionUrl}`, `${watchUrl}`, `${errorsJson}`, `${canComment}`, `${#strings.toString(issue.id)}` | 숫자(쪽 수, id), 문자열(이름), 불리언(권한), 이미 만든 URL, 일부는 JSON 문자열(`errorsJson`) |
| 현재 사용자 이름 | 8 | `${helper.escape(currentUser.pureName)}` | 이슈, 게시글 화면의 멘션 제외 목록 같은 곳 |
| 메시지 `#{...}`와 `helper.escapedMessage(...)` | 5와 약 8 | `#{issue.assignee}`, `${helper.escapedMessage('button.copy.email.success.message')}` | 화면별로 `Messages()`를 쓰지 않고 박는 문구. 번역 키 접근이 두 갈래 |
| 로케일 | 2 | `${#locale.language}`(`window.yona.language`, `window.yonaLanguage`) | 달력과 상대 시각의 언어. 같은 값이 두 이름으로 들어간다(`scripts.html:68`, `:77`) |

- **JSON처럼 쓰는 것**: `errorsJson`(가입 화면)처럼 서버가 만든 JSON 문자열을 박는 곳과 `th:inline="javascript"`가 객체를 직접 직렬화하는 곳이 섞여 있다. 저장소에는 `<script type="application/json">`으로 값을 내려 주는 템플릿이 **없다**(`grep` 확인, `application/json`은 `contentType` 문자열 3곳뿐).
- **인라인 속성 핸들러**: `onclick=` 6개, `onsubmit=` 17개(`grep`). TS 모듈은 이 이름이 전역 함수로 보이도록 계속 `window`에 올리거나 템플릿에서 지워야 한다.
- **인라인이 모듈을 부르는 방식**: 54곳이 `$yona.loadModule("이름", {옵션})`을 부른다. 거의 모두 `$(document).ready(function(){ ... })` 안이며, 옵션에 `th:inline`으로 서버 값(주소, id)을 싣는다. 자세한 방식은 2.3절.
- **전체 인라인의 구조**: 큰 인라인(`issue/view.html` 164줄 등)은 모듈 초기화만이 아니라 **화면 로직 자체**를 담는다. 모듈로 옮기지 않으면 TS의 타입 이득이 미치지 못한다. 그래서 7절은 템플릿 인라인을 "그 화면의 묶음에서 함께 모듈로 흡수"하는 일로 계획했다.
- **`th:inline`이 아닌 인라인**: `common/importmap.html`은 `th:inline="text"`이다(JSON 형식의 import map, 2.2절). `help/markdown.html`은 `th:inline="none"`이다.

### 1.5 템플릿 106개의 묶음 배정

범위 점검(맨 아래)에 전부 나열했다. 요약은 다음과 같다. 묶음 0: 7개(레이아웃과 공통 스크립트), 1: 2개, 2: 3개, 3: 2개, 4: 5개, 5: 40개, 6: 35개, 7: 12개 (합 106). 배정 규칙은 "그 템플릿이 불러오는 `esm/` 모듈 중 가장 큰 묶음", 모듈이 없으면 디렉터리 기본이다(`audit`, `site`, `user`, `organization`, `project`는 5, `board`, `issue`, `milestone`, `pullrequest`, `reviewthread`, `index`, `search`, `help`, `wiki`는 6, `code`는 7, `common` 나머지는 4, `welcome`은 3).

## 2. 전역 이름 공간과 로딩 방식

### 2.1 로딩 순서(현재, 일반 화면)

`layout/layout.html`과 `common/scripts.html`에서 읽은 순서이다(`layout-framed.html`, `migration-layout.html`은 약간 다르다).

1. `<head>` 맨 앞: **import map**(`layout.html:18`, `layout-framed.html:16`, `migration-layout.html:14`가 `common/importmap`을 가장 먼저 포함한다).
2. `<head>`: `lib/viewerjs/viewer.css`, 그리고 **클래식 `yona-layout.js`**(`layout.html:41`, 동기 실행): nprogress, jQuery 3.7.1, htmx, `$yona`(`yona.Common.js`), `yona.Htmx`가 전역에 올라온다.
3. 본문 끝 `common/scripts.html`: (가) 모달과 토스트 마크업 `#yobiDialog`, `#yobiToasts`, `tplYobiToast`(`:11-36`), (나) 클래식 `common/yona.Csrf.js`(`:41`), (다) 인라인 `window.yonaContextPath`(`:43`), (라) 클래식 `/messages.js`(`:44`, `Messages` 전역), (마) 클래식 `yona-common.js`(`:45`), (바) `bootstrap.bundle.min.js`(`:47`)와 `bootstrap.Dropdown.Default.display = "static"`, (사) `bootstrap-switch.js`(`:50`), `autosize.min.js`, (아) **모듈** `esm/Tabs.js`, `KeyControl.js`, `Clipboard.js`, `esm/index.js`(`:56`), `text-expander-element.js`, `markdown-toolbar-element.js`, (자) 클래식 `popper.min.js`, `tippy-bundle.umd.min.js`, (차) 인라인 `$yona.setScriptPath(...)`, `window.yona.language`, 그리고 `$(document).ready(...)` 안의 전역 초기화(툴팁, `yona.ui.Dropdown`, `yona.ShortcutKey.attach/setKeymapLink`, `yona.Files.init`, tippy, 플래시 메시지, `NProgress` 연결, 링크 `_blank` 처리, `autosize`, 이프레임 안 링크 처리 등).
4. 각 화면 템플릿 끝: 화면 모듈 `<script type="module" th:src="@{/assets/javascripts/esm/service/X.js}">`와 `$(document).ready(function(){ $yona.loadModule("X", {...}); })` 인라인.

`yona-layout.js`가 `<head>`에서 동기로 실행되고, 모듈 스크립트(`type="module"`)는 문서 분석이 끝난 뒤 `DOMContentLoaded` 전에 순서대로 실행된다. jQuery의 `$(document).ready`는 **그 뒤**에 돈다. 이 순서가 "모듈 평가 때 등록, ready 때 초기화"를 성립시킨다.

### 2.2 import map과 모듈 경로

- 내용: `{"imports": {"@github/combobox-nav": ".../lib/text-expander/combobox-nav.js", "yona/": ".../esm/"}}`(`common/importmap.html`, `th:inline="text"`). `yona/` 접두어 덕에 `import { register } from "yona/namespace.js"`가 가능하다. 서비스 모듈은 상대 경로(`"../namespace.js"`)와 접두어 경로를 섞어 쓴다(둘 다 같은 파일을 가리킨다. `esm/index.js`는 접두어, `esm/service/*.js`는 상대 경로).
- 하위 경로 배포(`yonaContextPath`)에서도 import map의 값에 `@{...}`가 들어 있어 맞는다.
- **TS에서의 사실**: `tsc`는 import 지정자를 고쳐 쓰지 않으므로 산출물의 `"yona/namespace.js"`는 그대로 남고, 형 검사 때는 `paths: {"yona/*": ["<ts 원본 폴더>/*"]}`(`moduleResolution: "bundler"`)로 해석한다(일반 지식, 설정 값은 도입 때 확인). 번들러(esbuild/Vite)를 쓰면 `external`로 두거나 별칭을 줘야 한다. 어느 쪽이든 import map 계약(`JsLibraryVersionsTest`가 `"yona/": "[[@{/assets/javascripts/esm/}]]"` 문자열을 확인한다. `JsLibraryVersionsTest.kt:352`)은 산출물이 `esm/` 아래에 같은 이름으로 나오면 지켜진다.
- Firefox는 모듈 태그 뒤의 import map을 무시하므로 항상 `<head>` 맨 앞이어야 한다(`importmap.html` 주석).

### 2.3 `esm/namespace.js`, `index.js`, `$yona.loadModule`

- **`namespace.js`**: `register("yona.ui.Calendar", value)`가 점 이름의 중간 단계를 만들고 전역에 값을 넣는다. 이름 검사 정규식이 있다(`NAME_PATTERN`). `ensureNamespace`는 이미 있는 값을 지우지 않는다(클래식 스크립트가 먼저 만든 전역과 공존, `esm.test`가 확인).
- **`index.js`**(46줄): `yona.esm = { ready:false, register, ensureNamespace }`를 만들고 22개 모듈을 import한 뒤 `ready = true`, `document.dispatchEvent(new CustomEvent("yona:esm-ready"))`. **`yona:esm-ready`를 듣는 소비자는 지금 저장소에 없다**(`grep` 확인, 문서 주석에만 있음). 화면 모듈 약 47개와 `Usermenu.js` 등은 `index.js` 밖에서 각자 `<script type="module">`로 불린다.
- **`$yona.loadModule(이름, 옵션, 콜백)`**(`yona.Common.js:77-126`, `registerModule`은 `:106`): 이름을 `.`으로 나눠 전역 `yona`에서 찾는다. (가) 값이 **함수면 `new` 호출**, (나) **객체면 `.init()` 호출**하고 결과를 `htModuleInstance`에 둔다. 찾지 못하면 `scriptPath + "service/yona.<이름>.js"` 주소의 스크립트를 동적으로 붙여 최대 3번 재시도한다. 이 폴백 주소는 **지금 존재하지 않는다**(`static/assets/javascripts/` 아래에 `service/` 폴더가 없다. `ls` 확인). 그래서 모듈은 반드시 템플릿이 먼저 `<script type="module">`로 불러야 한다. TS로 옮길 때 이 폴백은 죽은 경로이므로 `import()` 동적 불러오기로 대체할지, 지울지 결정이 필요하다(8절). `registerModule`은 **모듈 존재 여부와 무관하게 호출될 때마다** `[data-bs-toggle=popover]`를 Popover로 초기화한다(`:116`), 이 부수 효과도 계약이다.
- **전역에 올라가는 비 `yona.*` 이름**(모듈 간 호출 또는 템플릿 호출): `$yona`, `Messages`, `window.yonaContextPath`, `window.yonaLanguage`, `window.yona.language`, `temporarySaveHandler`, `removeCurrentPageTemprarySavedContent`, `cancelAllDraftTimers`(`CommentForm.js`, `git.Write`, `board.Write`, `issue.Write`, `milestone.Write`, `Comment.js`가 부름), `findNotiReceiversHandler`(템플릿 1곳), `yonaTitleHeadModule`(템플릿 3곳), `yonaAssgineeModule`(3곳), `yonaIssueSharerModule`(1곳), `detectPageChange`(1곳), `_initTwoColumnMode`(템플릿 1곳, 모듈 4곳), `_initShowSubtasks`(모듈 2곳), `window._tab`, `window._untab`(`Markdown.js`가 맨 이름으로 부름). 템플릿이 쓰는 `yona.*` 이름은 `yona.loadModule` 54, `yona.Mention(`(함수로 직접 호출) 24줄, `yona.Pagination.update` 13, `yona.alert` 11, `yona.http.post` 10, `yona.http.ajax` 8, `yona.escapeHtml` 8, `yona.ShortcutKey.setKeymapLink` 8, `yona.notify` 5, `yona.Htmx.submit` 5, 그 밖 소수이다(`grep -o` 집계).
- **호출 모양 계약**: 모듈 대부분은 `const X = function(htOptions){ ... }; export default X; register("yona.X", X)`로 **함수 생성자**이고 `$yona.loadModule`이 `new`로 만든다. `yona.Mention({...})`처럼 템플릿이 **`new` 없이 함수로** 부르는 것도 있다. TS에서 `class`로 바꾸면 `new` 없는 호출이 `TypeError`가 된다.

### 2.4 클래식 스크립트와 모듈의 공존 방식(현재)

- 클래식은 전역 이름 공간(`window.yona`, `$yona`, jQuery 플러그인)을 쓰고, 모듈은 `register()`로 같은 전역에 이름을 단다. 모듈끼리도 **서로 import하지 않고 전역을 읽는** 경우가 대부분이다(`yona.http`, `yona.Files`, `$yona`). 진짜 `import`의 대상은 `namespace.js`, `util.js`, `dates.js` 세 개와 `index.js`가 가져오는 목록뿐이다(표의 `import` 의존).
- 모듈 최상위 코드는 한 번만 실행되므로, `$(function(){...})`를 모듈 최상위에 둔 모듈은 htmx로 본문만 바꾸면 다시 돌지 않는다. 이 점이 `ui-htmx-plan.md` 5절의 swap-safe 문턱이다. 표의 "최상위 부작용" 열이 그 판정 입력이고 평가 때 부작용이 "있음"인 모듈은 **24개**, "평가 때는 없음, 생성 때 문서나 창 수준 핸들러를 다는" 모듈이 8개이다(소스를 직접 읽어 센 값).

### 2.5 TS로 옮길 때 이어 가야 하는 것(사실 정리)

1. 산출물 경로와 이름이 같아야 한다: `esm/<경로>.js`. 템플릿 `th:src`와 import map, 11개 Kotlin 시험(5절)이 이 경로를 쓴다.
2. 전역 `yona.*`, `$yona`, 위 비 `yona.*` 이름은 **옮기는 동안 그대로 등록**해야 한다(`register()` 유지, 새 모듈은 import로 서로 연결하되 옛 호출처를 위해 등록은 남긴다).
3. 클래식으로 남을 것: `yona.Csrf.js`(순서와 프로토타입 교체)와, `yona-layout.js`에 들어가는 `yona.Common.js`, `yona.Htmx.js`의 현재 실행 시점(head에서 동기). 이를 모듈로 바꾸면 **실행 시점이 늦어진다**(2.1절 순서). 시점이 중요한 이유: `<head>`의 인라인(`layout.html:42-60`)과 본문의 인라인이 `$`, `$yona`, `htmx`를 즉시 쓴다.
4. 모듈 사이의 타입은 전역 선언 파일(`yona-globals.d.ts`, 이름은 가칭)로 먼저 적고, 모듈이 TS가 되면 그 선언을 `import`로 대체한다. `Messages`, `$yona`, `window.yona`, `window.yonaContextPath`, jQuery 플러그인(`select2`, `modal`)의 선언이 첫 묶음에서 필요하다.

## 3. 들여온 라이브러리

근거: `src/test/resources/js-vendor-manifest.tsv`(경로, 이름, 버전, sha256, 항목 30개. `JsLibraryVersionsTest`가 해시를 고정한다), `src/test/resources/ace-vendor-manifest.tsv`(Ace 파일 해시, `AceVendorTest`), `bootstrap.bundle.js` 머리 주석. **타입 선언 열은 일반 지식이며 설치 때 확인해야 한다.** 파일은 npm 패키지가 아니라 공식 배포 파일을 **복사해 둔 것**이므로, TS는 `@types/*`(또는 패키지 자체 타입)를 **형 검사용 devDependency로만** 설치하고 실행 파일은 지금처럼 `lib/`의 복사본을 쓰는 방식이 가능하다.

| 이름(경로) | 버전 | 용도 | 사용하는 모듈과 템플릿 | 공식 타입(일반 지식) | TS에서 필요한 것 |
|---|---|---|---|---|---|
| jQuery(`lib/jquery/jquery-3.7.1.js`, `yona-layout.js`에 포함, 단독 로드는 migration-layout, welcome 2개) | 3.7.1 | DOM, 이벤트, Ajax 일부, 플러그인 호출 | `esm/` 72개 모듈에서 약 767곳(주석 제외 `$(`/`jQuery` 근사). 거의 전부 | `@types/jquery` | `$.fn` 확장 선언(`select2`, `modal`, `popover` 등). 이벤트 핸들러의 `this` 타입 |
| jQuery UI 효과 묶음(`lib/jquery/jquery-ui-1.14.2.custom.min.js`) | 1.14.2 | 효과(`blind`, `shake` 등) | `common/login-dialog.html:73`이 불러옴 | `@types/jqueryui` | 효과 호출 선언만 |
| Bootstrap 번들(`assets/bootstrap/js/bootstrap.bundle.min.js`, Popper 2 포함) | 5.3.8 | 모달, 드롭다운, 툴팁, 팝오버, 탭, 오프캔버스 | `esm/` 12개 모듈(`Dialog`, `LoginDialog`, `issue.LabelEditor`, `organization.*`, `project.*`, `twoColumnMode`, `user.Setting` 등)과 `scripts.html` | `@types/bootstrap`(Bootstrap 5는 자체 타입이 없다) | 전역 `bootstrap` 선언. jQuery 어댑터(`$().modal()`)는 jQuery가 먼저 있으면 등록된다(`scripts.html:46` 주석, X-38d) |
| bootstrap-switch(`assets/bootstrap/js/bootstrap-switch.js`) | (Yona가 고친 파일, 매니페스트 없음) | 스위치 입력 | `scripts.html:50` | 없음 | `$.fn.bootstrapSwitch` 선언 |
| select2(`lib/select2/select2.js`, `select2-bootstrap-5-theme.css`, `i18n/{ko,ja,ru,en}.js`) | 4.1.0, 테마 1.3.0 | 다중 선택, 검색 선택 | `ui/Select2.js`(33곳), `issue.View`, `issue.List`, `issue.Assginee`, `issue.Sharer`, `issue.LabelEditor`, `pullrequest.Meta`, `pullrequest.Reviewers`, `Subtask`, `TitleHeadAutoCompletion`(10개 모듈). 템플릿은 `common/select2.html` | `@types/select2` | 옵션 객체, `data()` 반환 모양(서버 결과 `results`) 선언 |
| NProgress(`lib/nprogress/nprogress.js`) | 0.2.0 | 진행 막대 | 14개 모듈, `yona.Htmx.js`, 레이아웃 | `@types/nprogress` | 전역 `NProgress` 선언 |
| htmx(`lib/htmx.js`) | 2.0.11 | 부분 교체 | `yona.Htmx.js`, `Pagination.js`, `issue.List.js`, 템플릿 `hx-*` | htmx 2.x는 자체 `.d.ts`를 낸다(불확실, 설치 때 확인. 없으면 필요한 만큼 `declare`) | `htmx:*` 이벤트 `detail` 타입. 새 `Page` 모듈이 가장 많이 쓴다 |
| Mustache(`lib/mustache.js`, `yona-common.js`에 포함) | 4.2.0 | 템플릿 `render` | `yona.Common.js`(7곳) | `@types/mustache` | 전역 `Mustache` 선언 |
| xss(`lib/xss.js`, `yona-common.js`에 포함) | Chris O'Hara 2010 판(매니페스트 없음) | 옛 필터 `Filter` | `yona.Common.js:760` 폴백 | 없음 | 전역 `Filter` 선언 한 줄 |
| autosize(`lib/autosize/autosize.min.js`) | 6.0.1 | textarea 자동 높이 | `scripts.html` 인라인 | 패키지에 포함(일반 지식, 불확실) | 전역 `autosize` 선언 |
| Tippy.js와 Popper(`lib/tippy/`) | 6.3.7, 2.11.8 | 링크 미리보기 툴팁 | `scripts.html` 인라인 한 곳만 | 자체 포함 | 전역 `tippy` 선언 |
| marked(`lib/marked.js`), `marked-yona.js` | 4.3.0, Yona 래퍼 54줄 | 마크다운 렌더 | `common/markdown.html`, `Markdown.js` | marked 4는 자체 타입(일반 지식) | 전역 `marked` 선언 |
| highlight.js 팩(`lib/highlight/highlight.pack.js`) | 11.12.0 | 코드 강조 | `Markdown.js`(`hljs.registerAliases`) | 자체 포함 | 전역 `hljs` 선언 |
| Ace(`lib/ace/`, 파일 216개, 9.5MB) | 1.45.2 | 코드 보기 편집기 | `service/code.Browser.js`(`ace.edit`), `code/view.html` | `ace-builds`가 `.d.ts`를 낸다(일반 지식) | `ace.edit` 계열 선언. 모드 이름은 문자열로 동적 로딩 |
| Cropper.js(`lib/cropperjs/cropper.js`) | 2.3.1 | 아바타 자르기 | `service/user.Setting.js`, `user/edit.html` | 자체 TS(일반 지식) | 사용자 정의 요소(`<cropper-canvas>`) 선언 |
| vanilla-calendar-pro(`lib/vanilla-calendar-pro/`) | 3.4.0 | 달력 | `ui/Calendar.js`, `common/calendar.html` | 자체 TS | 전역 `VanillaCalendarPro` 선언 또는 import 전환 |
| Viewer.js(`lib/viewerjs/viewer.js`, `jquery-viewer.js`) | 1.15.2 | 이미지 보기 | `layout/layout.html:83-84` | 자체 포함 | `$.fn.viewer` 선언 |
| video.js(`lib/videojs/`) | 7.21.2 | 영상 재생 | `issue/view.html` | `@types/video.js` | 전역 `videojs` 선언 |
| Vue(`lib/vue/vue.global.prod.js`) | 3.5.45 | 이전(Migration) 화면만 | `layout/migration-layout.html`, `service/Migration.js` | 자체 포함 | 전역 `Vue` 선언 |
| title-input(`lib/title-input/title-input.min.js`) | 0.1.0(Lit 3.2.1 포함) | 제목 `[` 머리말 요소 | `issue/create`, `issue/edit`, `board/create`, `TitleHeadAutoCompletion.js` | 원본은 JS(`components/title-input/src/title-input.js`) | 사용자 정의 요소 선언 |
| markdown-toolbar-element(`lib/markdown-toolbar/`) | 2.2.3 | 편집기 서식 단추 | `scripts.html` | GitHub 배포본(일반 지식, 확인 필요) | 요소 선언 |
| text-expander-element, combobox-nav(`lib/text-expander/`) | 2.9.4, 2.3.1 | 멘션 자동완성 | `Mention.js` 이벤트, import map | GitHub 배포본(일반 지식, 확인 필요) | 이벤트 `detail` 타입(`text-expander-change`) |
| filetype.js(`lib/filetype.js`) | 1,078줄, file-icons 파생 | 파일 아이콘 매핑 | `user/files.html` | 없음 | 필요 시 선언 |
| `lib/elevator/jquery.elevator.css` | | 스타일만 | `issue/view.html`, `board/view.html` | | 없음 |

- **형 검사용 의존의 위험**: 위 `@types/*`는 일반 지식이고, 복사해 둔 파일의 버전과 `@types` 버전이 어긋날 수 있다. 매니페스트(`js-vendor-manifest.tsv`)에 `@types` 버전을 적는 열이 필요하다는 것이 8절의 열린 질문이다.
- **새로 들일 것**: `ui-client-storage.md` 6절은 IndexedDB 래퍼 `idb`를 D-91의 vendoring 방식으로 들이기로 제안했다. 이것은 TS 소스에서 `import`하는 첫 npm 의존이므로, 복사 파일 + `lib/idb/` 경로를 import map에 넣거나, 번들러로 묶어 산출하는 두 방식 중 하나를 정해야 한다(6절).
- **저장소 밖 빌드물**: `title-input`(Lit)과 `vue-widgets`는 별도 저장소(`~/yona-convert/components`, `components-wt/vue-widgets`)에서 TS와 Vite로 만들어 산출물만 복사해 들인다(`vue-widgets/package.json`의 `vue-tsc --noEmit && vite build`, `typescript ^5.9.3`, `esbuild 0.28.2`). **이미 이 조직에 TS 빌드 선례가 있다**는 사실이다. 이 조사에서는 `vue-widgets`의 소스를 읽지 않았고 `package.json`과 폴더 목록만 보았다.

## 4. 서버가 박는 값

### 4.1 지금의 전달 방식

| 종류 | 방식 | 근거 |
|---|---|---|
| 컨텍스트 경로 | 인라인 `window.yonaContextPath = /*[[@{/}]]*/ "/"` 뒤 끝 `/` 제거(`scripts.html:43`). 루트 배포면 빈 문자열. **읽는 모듈은 3개**이다(`CommentAttachmentsUpdate.js:54,122`, `ui/Typeahead.js:326`, `service/Migration.js:20`). 나머지 모듈은 템플릿 인라인이 `@{...}`로 박은 **완성된 URL**을 옵션으로 받는다 | `grep -rn yonaContextPath` |
| 메시지 | 클래식 `GET /messages.js`가 전역 함수 `Messages(키, 인자...)`를 만든다(`JsMessagesController.kt:31`). 응답은 현재 요청 로캘의 `messages` 번들 전체(영어 기본 위에 번역 덮어쓰기), `Cache-Control: no-cache`, `Vary: Accept-Language`. 키가 없으면 키를 그대로 돌려주고 `{0}` 치환은 처음 하나만 바꾼다. 43개 모듈이 `Messages`를 쓴다 | `JsMessagesController.kt:14-45`, 표 의존 열 |
| 언어 | `window.yona.language`(달력)와 `window.yonaLanguage`(상대 시각)가 같은 `${#locale.language}`를 두 이름으로 받는다 | `scripts.html:68`, `:77` |
| 화면 옵션(주소, id, 쪽 수, 플래그) | `$yona.loadModule("X", {옵션})`에 `th:inline="javascript"` 표현식으로 박는다(54곳). 표현식 212개 중 대부분(95 + 104) | 1.4절 |
| 현재 사용자 이름, 권한 플래그 | 같은 방식(`helper.escape(currentUser.pureName)`, `canComment`) | 1.4절 |
| 플래시 메시지 | `scripts.html` 인라인이 `$yona.notify("[(${notice})]", 5000)` 형태로 박는다(`[(...)]`는 **이스케이프하지 않는** 출력이다) | `scripts.html:132-137` |
| CSRF 토큰 | 쿠키 `XSRF-TOKEN`을 `yona.Csrf.js`가 읽는다. 인라인에 박지 않는다 | `yona.Csrf.js` |
| 파일 업로드 설정 | `yona.Files.init({sListURL, sUploadURL, maxFileSize})`를 `scripts.html` 인라인이 부른다 | `scripts.html:106` |
| 에러 목록 | `errorsJson` 같은 서버 JSON 문자열 | 가입 화면 등 |

### 4.2 TS에서 받는 방식: 선택지

| 안 | 모양 | 장점 | 단점 |
|---|---|---|---|
| A. 인라인 유지 | 짧은 `th:inline="javascript"` 블록이 타입 있는 진입점을 부른다(`yona.page.start("issue.View", {...})`) | 템플릿 변경이 가장 작다. Thymeleaf의 JS 이스케이프가 그대로 적용된다. 지금과 동일해 시험 계약이 같다 | 인라인은 TS가 아니다(타입 검사 불가). htmx 조각 안의 인라인은 실행되지만 수명주기가 화면마다 다르다. CSP를 쓰게 되면 `unsafe-inline`이 필요하다(지금 `script-src` CSP는 없다. `OAuthConsentController.kt:39`의 `frame-ancestors`만 있다) |
| B. `data-*` 속성 | 화면 루트에 `data-yona-module="issue.View"`와 `data-yona-url-list="..."`, 값마다 속성 | 인라인 JS가 사라진다. 값이 DOM과 함께 교체되어 htmx 조각과 자연스럽다. Thymeleaf `th:data-*`가 HTML 이스케이프를 해 준다. 템플릿에 `data-*`가 이미 261곳 있다(`grep`) | 중첩 객체와 배열은 JSON 문자열을 한 번 더 파싱해야 한다. 값마다 이름과 타입 변환 규칙이 필요하다 |
| C. 단일 JSON `<script type="application/json" id="yona-page">` | 서버가 화면 옵션 전체를 JSON 하나로 낸다. TS가 `JSON.parse` 뒤 타입으로 단언 | 한 곳에서 타입을 선언하고 서버 쪽 DTO와 맞출 수 있다. 값이 많은 화면에 맞다 | 서버에 안전한 JSON 직렬화 도우미가 필요하다(`</script>`, `<!--` 탈출. 지금은 Thymeleaf가 해 준다). 저장소에는 이 방식이 아직 없다 |
| D. 메타 태그 | `<meta name="yona-context-path" content="...">` | 전역 값에 간단하다 | 값이 소수일 때만 맞는다 |

- **권장안**: **전역 값은 `<html>` 또는 `<meta>` 한 곳(D)**, **화면 값은 B(`data-*`)를 기본**으로 하고 **값이 객체나 배열이면 C**로 한다. 단 한 번에 바꾸지 말고 **그 화면의 묶음(7절)에서 모듈을 옮길 때 함께** 바꾼다. 옮기기 전까지는 A로 둔다.
- **이유**: (1) htmx 화면 교체(D-136)에서 인라인 `<script>`는 조각마다 수명주기가 달라 swap-safe 판정이 어렵고, `data-*`는 요소와 함께 사라지고 나타나 `Page.mount(root)` 모델(`ui-htmx-plan.md` 5절)에 바로 맞는다. (2) 타입은 `data-*`를 읽는 한 곳(`Page` 모듈의 `readOptions<T>()`)에서 선언하면 된다. (3) 인라인 블록 119개 중 **서버 값을 전혀 박지 않는 44개**는 데이터 전달 문제가 아니라 로직이므로 모듈로 옮기는 별개 작업이다(그 블록 목록은 범위 점검의 `th:inline` 열이 0인 템플릿).
- **`Messages`**: 현재 `/messages.js`(전역 함수)는 요청 로캘로 만든 **클래식 스크립트**라 그대로 둬도 TS에서 `declare function Messages(key: string | string[], ...args: unknown[]): string`으로 쓸 수 있다. 선택지: (가) 지금 방식 유지(변경 없음, 키 오타는 런타임에만 드러난다), (나) 키 목록에서 TS 키 유니온 타입을 생성(오타를 컴파일러가 잡지만 `messages*.properties`(5개 언어)와 동기 빌드 단계가 필요하다). **권장: (가)로 시작하고 (나)는 후속**. 이유: 계약을 안 바꾸고 첫 묶음을 작게 유지하며, 키 유니온 생성은 Gradle 빌드 단계가 하나 더 필요하다.
- **`window.yonaContextPath`**: 읽는 모듈이 3개뿐이므로 옮기기 쉽다. TS에서는 한 함수 `url(path)`가 `<meta>` 값을 읽어 붙이게 하고 호출처를 그리로 모으는 것이 선택지이다. 권장: 같은 이름으로 `window.yonaContextPath`를 당분간 유지(옛 모듈 호환), 새 모듈은 `url()`만 쓴다.

## 5. 현재 시험 체계

### 5.1 구성(읽은 사실)

- **하네스**(`src/test/js/harness.mjs`, 172줄): Playwright `chromium`을 띄우고(`import { chromium } from 'playwright'`, 모듈은 **저장소 위 폴더 `~/yona-convert/node_modules`**에서 찾는다. 파일 머리 주석), `pageWith({html, scripts, stubs, jq4})`로 빈 페이지를 만들어 jQuery 3.7.1을 주입한다. 기본으로 jQuery 4에서 지워지는 API를 지운 환경(`JQUERY4_REMOVALS`)과 `$yona`, `Messages`, `bootstrap`의 **대역(`COMMON_STUBS`, `:15-38`)**, Mustache, `yona.http`, `yona.util`, `yona.dates`를 깔고 시험 대상 스크립트를 주입한다.
- **`classicFromEsm(경로)`**: `esm/` 파일을 **텍스트로 읽어 정규식으로** `import`를 지우고(`util`, `dates`는 전역에서 구조 분해로 대체), `export default X;`, `export {…}`, `export function/const/class`의 `export`를 걷어내고, `register`를 지역 함수로 바꿔 `(function () { "use strict"; ... })();`로 감싼다(`:82-91`). `classicHttpSource()`는 `http.js`에 같은 일을 하는 별도 함수이다. 문자열 단위 `sourceSnippet`, `templateSnippet`(템플릿 인라인 조각을 표지 문자열로 잘라 실행)도 소스 텍스트에 의존한다(5개 시험 파일에서 import 줄 포함 17곳).
- **모듈 방식 시험은 일부만 있다**: `esm.test.mjs`는 자체 `open()`이 `page.route`로 `http://esm.test/assets/javascripts/**`를 저장소 파일로 내려 주고 import map(`yona/` 접두어)으로 **실제 `<script type="module">`**을 돌린다. 하네스의 `esmPage()`(`harness.mjs:150`)는 같은 방식에 진입점 `esm/index.js`와 **실제 `yona.Common.js`**(`PRELUDE`)를 깔며 `smalllibs.test`, `http.test`가 쓴다. `mention.test`, `commentattach.test`도 모듈 태그를 쓴다(`grep` 확인). 나머지 시험은 `classicFromEsm`으로 읽는 클래식 방식이다.
- **`run.mjs`**(20줄): `src/test/js/*.test.mjs`를 정렬해 모두 불러 `export const tests = [{ name, run }]`을 차례로 실행하고, 하나라도 실패하면 종료 코드 1을 낸다. 인자로 파일 이름 일부를 주면 그 파일만 돌린다(`node src/test/js/run.mjs 이름`).
- **규모**: 시험 파일 67개(7,063줄), 항목 약 650(`run:` 개수). `esm/` 경로를 부르는 시험 파일이 58개(서로 다른 경로 80개), `common/yona.*` 경로를 부르는 시험이 34개이다.
- **Gradle과 연결되어 있지 않다.** `build.gradle.kts`에는 node 시험 작업이 없다(`grep`). `build-tools/js-bundle`의 `npm test`(번들 동등성, jsdom)도 수동이다. 따라서 "서버를 띄울 때 Gradle 한 번" 제약은 **시험이 아니라 서버 부팅에 걸린다.**
- **Kotlin 쪽 원문 시험 11개**가 JS 소스 **텍스트**를 읽는다: `JsLibraryVersionsTest`, `AceVendorTest`, `ItemSearchModuleTest`, `NotifyEscapeContractTest`, `TemplateContractRegressionTest`, `MigrationPageTest`, `ProjectSettingScriptTest`, `StickyPanelsTest`, `SiteMailTest`, `SignupTest`, `LoginFormPageTest`. 예: `JsLibraryVersionsTest.kt:356`은 `index.js`가 `import { register, ensureNamespace } from "yona/namespace.js";`를 포함하는지 문자열로 확인하고, `StickyPanelsTest`는 서비스 파일에 `yona.affix`가 없는지 확인한다. 읽는 경로는 `static/assets/javascripts/esm/...js`이다.
- **같은 문자열 시험이 TS 소스에 닿으면**: 타입 주석과 `import type`이 원문에 들어가 `contains(...)`류 단언이 깨질 수 있다.

### 5.2 TS 전환 후 시험을 잇는 선택지

| 안 | 내용 | 장점 | 단점 |
|---|---|---|---|
| A. 산출물(JS)을 현행 하네스로 시험 | 시험 전에 TS를 빌드해 산출 폴더를 `JS`(`harness.mjs:9`)로 가리키게 하고 `classicFromEsm`은 산출 JS를 읽는다 | 시험 650개가 거의 그대로 돈다. `tsc` 산출은 원본 모양을 유지하므로 정규식 변환이 대체로 통한다(`export {}`가 붙는 정도) | 시험이 빌드에 의존한다(빌드 후 실행 순서). 산출물에 타입 정보가 없어 "타입 시험"은 별도 |
| B. 모듈 방식 시험으로 점진 이전 | `esm.test.mjs` 방식(실제 모듈 로드, import map, `page.route`)을 하네스 기본으로 만들고 `classicFromEsm`을 줄인다 | **TS의 `import`가 그대로 실행되므로** 정규식 변환이 필요 없다. 실제 브라우저 계약(import map, 실행 순서)과 같다 | 시험 58개의 작성 방식을 바꿔야 한다. 대역(`COMMON_STUBS`)을 실제 `Common` 모듈로 대체하는 작업이 따라온다 |
| C. 시험 코드를 TS로 | 하네스와 시험을 `.ts`로 옮기고 Playwright Test(`@playwright/test`, 상위 `package.json`에 이미 있음)로 | 시험도 형 검사. 러너 기능 풍부 | 시험 7,063줄을 모두 건드리는 큰 작업. 이번 요청 범위를 넘는다 |
| D. jsdom/happy-dom 단위 시험 | 모듈별 Vitest | 빠름 | 지금의 실제 Chromium 시험보다 계약 신뢰가 낮고, htmx와 레이아웃 시험을 잃는다 |

- **권장안: A로 시작하고 새로 쓰는 모듈은 B 방식**으로 시험한다(7절의 N 묶음). **시험 파일은 `.mjs`로 유지**한다(C는 후속).
- **이유**: (1) 650개 시험은 지금까지 "v1.6 동등성"을 지킨 안전망이고 TS 전환이 이것을 흔들면 안 된다. A는 시험 수정이 하네스 한 곳(`JS` 경로)이다. (2) 새 모듈(`Page`, 저장소, 단축키)은 처음부터 모듈 방식이어야 `htmx:load`와 `BroadcastChannel`, IndexedDB 같은 실제 브라우저 기능을 시험할 수 있다. (3) `tsc --noEmit`(형 검사)를 **시험의 첫 단계**로 두면 타입 오류도 같은 명령에서 잡힌다.
- **같이 해야 할 것**: Kotlin 원문 시험 11개를 산출물 경로 또는 TS 소스 경로로 읽도록 고치고, 문자열 단언이 타입 주석에 영향받지 않는지 확인한다. 이는 TS 전환의 **직접 영향 범위**이다(표 외에 이 조사에서는 11개 파일의 단언을 모두 읽지는 않았고 `grep`으로 경로만 확인했다).

## 6. 빌드와 배포 현황, 도구 체인 선택지

### 6.1 현재(읽은 사실)

- **Gradle의 node 단계는 SCSS 하나뿐이다.** `build.gradle.kts:253-310`: `installSass`(`Exec`, `npm ci`, 입력은 `build-tools/sass/package.json`과 잠금 파일, 출력은 `node_modules`)가 `compileSass`(SCSS 원본을 `build/generated/sass/static/assets/stylesheets`로 복사하고 dart-sass 1.69.7을 실행)에 선행하고, `processResources`가 `from(compileSass)`로 산출물을 클래스패스 자원으로 합친다. **JS에는 Gradle 단계가 없다.** `esm/`와 `common/`은 저장소 파일 그대로 자원이 된다. 즉 이 프로젝트의 서버 빌드는 이미 **Node(`npm` 명령)를 필수 도구로 가진다.**
- **번들 2개는 수동 빌드 + 커밋**이다. `build-tools/minify-js.sh`가 `build-tools/js-bundle`에서 `npm ci` 뒤 `vite build`(vite 8.3.0)를 돌려 `yona-layout.js`, `yona-common.js`를 만들고 결과를 커밋한다. README 마지막 줄: "서버 빌드(Gradle)와는 연결하지 않았다. 입력을 고치면 직접 실행하고 번들을 커밋한다"(`build-tools/js-bundle/README.md`). 맞춤 플러그인(`vite.config.mjs`의 `concatScripts`)이 입력을 **모듈로 만들지 않고 이어 붙여 압축**한다(전역 스크립트라서).
- **`package.json`은 저장소 루트에 없다.** 있는 곳: `build-tools/sass/package.json`, `build-tools/js-bundle/package.json`(devDependencies `vite 8.3.0`, `jsdom 31.0.0`), 저장소 위 폴더의 `~/yona-convert/package.json`(`playwright ^1.60.0`, `xlsx`, `@playwright/test ^1.63.0`. 시험 하네스가 여기서 Playwright를 찾는다). 이 조사 시점 로컬 Node는 v24.19.0(`fnm`)이다.
- **`frontend/`의 정체**: `git status`에 `?? frontend/`(추적되지 않음). 안에는 `node_modules/@hotwired/turbo`(8.0.23)와 `node_modules/.package-lock.json`(이름 `yona-frontend`)뿐이고 **`package.json`, 소스, 설정이 없다**. `js-library-alternatives.md`가 htmx의 대안으로 Turbo Drive를 비교한 적이 있고 D-96이 htmx를 택했으므로 **Turbo를 받아 본 흔적으로 보인다(추측, 확인 근거는 이 두 사실뿐)**. TS 작업이 이 폴더 이름을 그대로 쓸지는 총괄이 정한다. 이미 있는 빈 껍데기라 혼선이 없게 정리 여부를 묻는 것이 안전하다(8절 질문 8).
- **정적 파일 서빙**: `AssetFilter`(`Ordered.HIGHEST_PRECEDENCE + 10`)가 `/assets/**`와 `/favicon.ico`를 보안 필터와 디스패처보다 먼저 가로채 `StaticAssets.serve`로 응답한다. 응답은 v1.6 규칙을 따른다: `Cache-Control: public, max-age=3600`, 강한 `ETag`(`sha1("<Last-Modified> -> <자원 주소>")`), `Last-Modified`, `If-None-Match`/`If-Modified-Since` 304(`StaticAssets.kt:16-40`). MIME 표는 Play의 확장자 표이며 `js`는 `application/javascript; charset=utf-8`, **표에 없는 확장자(`.ts`)는 `application/octet-stream`, `.map`은 `application/x-navimap`**이다(`:54-64`).
- **캐시와 해시**: JS 주소에 지문(해시, 버전 쿼리)이 없다. 배포 뒤 클라이언트는 최대 1시간 동안 옛 파일을 쓸 수 있다. 모듈 88개가 서로 다른 시점의 파일로 섞여 평가될 수 있는 것은 **지금도 같은 성질**이며 TS 전환이 새로 만든 문제는 아니다. 다만 TS 빌드는 산출물 전체를 한 번에 만들어 **지문 주소를 붙이기 쉬운 지점**이 된다(선택지 6.4). `js-vendor-manifest.tsv`와 `JsLibraryVersionsTest`는 **`lib/`의 벤더 파일 sha256만** 고정하고 `esm/`, `common/`, 번들은 해시로 고정하지 않는다(번들은 전역 이름 집합 동등성만 `bundle.test.mjs`가 본다).

### 6.2 도구 체인 선택지

전제: **서버를 띄울 때 Gradle 한 번의 명령으로 재현 가능**해야 한다. 아래는 모두 `installTs`(`npm ci`) + `compileTs` 두 작업을 `compileSass`와 같은 모양으로 더해 `processResources.from(compileTs)`로 잇는 것을 가정한다.

| 안 | 구성 | 장점 | 단점 |
|---|---|---|---|
| A. `tsc` 단독 | `tsc -p`가 형 검사와 출력을 모두 한다. 출력은 입력과 1:1(`esm/X.ts` → `esm/X.js`), import 지정자를 고치지 않는다 | 도구가 하나(`typescript`)이고 설정 파일도 하나다. **산출 구조가 지금과 같아 import map, `th:src`, `yona/` 접두어 계약이 그대로**다. 번들러가 없어 `register`의 모듈 단일성이 유지된다 | 압축과 해시가 없다(지금도 `esm/`는 압축하지 않으므로 퇴보 아님). 대형 프로젝트에서 느릴 수 있다(88개 18.6k줄 규모의 소요는 측정하지 않았다) |
| B. esbuild 변환 + `tsc --noEmit` | esbuild가 파일마다 JS로 변환(번들 없이 `--outdir`), `tsc`는 형 검사만 | 변환이 매우 빠르다. 소스맵과 대상 문법 지정이 쉽다. **저장소 밖 선례가 있다**(`components/editor`, `components-wt/vue-widgets`가 esbuild 0.28.2와 `tsc`/`vue-tsc`를 쓴다. `package.json` 확인) | 도구가 둘이다. esbuild는 형을 보지 않으므로 `isolatedModules`, `verbatimModuleSyntax`를 켜 제약(`const enum` 등)을 지켜야 한다 |
| C. Vite 번들 | `build-tools/js-bundle`의 vite(8.3.0)를 확장해 `esm/`를 번들 | 이미 vite가 있다. 지문과 압축이 쉽다 | `esm/**`를 번들하면 **파일 구조와 `th:src` 경로, `register` 단일성, 모듈 이름 계약이 바뀐다.** 현재 vite 설정은 "번들러를 쓰지 않고 이어 붙이는" 맞춤이라 재사용도 안 된다. 멀티 엔트리와 청크 해시는 템플릿에 매니페스트 연동이 필요하다 |
| D. `allowJs` + `checkJs` | `.js`에 JSDoc 타입을 달고 `tsc --noEmit`으로 검사만 | **빌드 단계 없이 곧 시작**할 수 있다. 옛 코드를 옮기기 전에 타입 오류를 먼저 드러낸다. 소스 대부분에 이미 JSDoc(`@param {String}`)이 있다 | 지시("TypeScript로 재작성")를 충족하지 않는다. 옛 코드의 중간 단계로만 가치가 있다 |

- **권장안: A(`tsc` 단독)** 를 기본으로 하되 `isolatedModules`와 `verbatimModuleSyntax`를 켜 두어 B로 바꿀 길을 열어 둔다. **옛 코드 점검에는 D를 보조로** 쓸 수 있다(`checkJs`로 형 오류를 미리 보고, 파일을 `.ts`로 옮길 때 같은 설정으로 이어 간다).
- **이유**: (1) 지금 구조(`esm/` 안의 개별 ES 모듈 + import map)가 이미 "번들 없는 모듈"로 정리되어 있다(D-122). A는 이것을 **하나도 바꾸지 않고** 타입만 더한다. (2) 새 의존이 `typescript`와 `@types/*`뿐이라 `package-lock.json` 변동이 작다. (3) 저장소의 SCSS 단계와 같은 모양이라 Gradle 연결과 유지가 쉽다. (4) 번들, 압축, 지문은 요구되지 않았고 지금도 없다. 성능 때문에 C가 필요해지면 그때 결정해도 늦지 않다. B는 `tsc`가 느려질 때의 대안이다.
- **TS 원본 위치**: `src/main/resources/static/...` 아래에 `.ts`를 두면 `AssetFilter`가 **원문 그대로 서빙**한다(MIME은 `octet-stream`, 누구나 받을 수 있음). 그래서 원본은 자원 폴더 밖(예: `src/main/frontend/ts/**`, 이름은 가칭)에 두고 산출물만 `build/generated/ts/static/assets/javascripts/esm/**`로 내 `processResources`에 합치는 것이 SCSS와 같은 방식이다. 옮긴 파일은 **같은 커밋에서** `esm/<이름>.js`를 지운다(자원 폴더와 산출 폴더에 같은 경로가 둘이면 중복이 생긴다).
- **`package.json` 위치**: 선례대로 `build-tools/ts/package.json`(가칭)에 `typescript`, `@types/*`(형 검사용), 필요하면 `idb`를 둔다. 저장소 루트에 하나로 합치는 안(워크스페이스)은 기존 두 도구와 시험 하네스 위치(상위 폴더)를 함께 건드려 범위가 커진다. 권장: 선례대로 도구별 폴더.

### 6.3 출력물을 커밋할지 빌드 때 만들지

| 안 | 내용 | 장점 | 단점 |
|---|---|---|---|
| 가. 빌드 때 만든다 | Gradle `compileTs`가 매번(입력이 바뀔 때만) 산출해 `processResources`에 합친다. 산출물은 `build/`에만 있다 | 원본과 산출물이 어긋날 수 없다. 리뷰에 산출 JS 노이즈가 없다. SCSS와 같은 선례. 서버 부팅이 Gradle 한 번으로 재현된다 | Node와 `npm ci`가 없으면 서버 빌드가 실패한다(**이미 SCSS 때문에 필요**). 오프라인 환경은 `node_modules` 캐시가 필요하다 |
| 나. 커밋한다 | 산출 JS를 `esm/`에 커밋(번들 2개와 같은 방식, D-92) | Node 없이 서버를 띄울 수 있다. 배포 파일이 저장소에 보인다 | 원본과 산출물이 이중이라 어긋나기 쉽다(실수로 산출만 고침). 리뷰 노이즈. 현재의 "수동 실행 후 커밋"이 이미 번들 2개에서 불편을 만든다 |
| 다. 혼합 | `esm/`는 가, 번들 2개는 지금처럼 커밋 | 번들 영향 범위를 작게 | 묶음 3에서 번들 입력이 `common/*.ts` 산출물이 되면 **번들만 옛 산출을 들고 있는 어긋남**이 생긴다 |

- **권장안: 가(빌드 때 만든다)**. 번들 2개도 묶음 3에서 같은 Gradle 단계로 옮기는 것까지 포함한다(그때까지는 다).
- **이유**: Node가 이미 서버 빌드의 필수 도구이므로 가는 새 요구를 더하지 않는다. 나는 원본과 산출물의 이중 관리가 TS 전환 기간(수개월 단위의 점진 이동)에 가장 큰 사고 원인이 된다. 다는 `common/`을 옮기는 순간 어긋남이 생기므로 짧은 과도기에만 맞다.

### 6.4 부수 선택지: 캐시 지문

모듈 그래프 전체가 같은 시점 파일인지 보장하려면 산출 폴더 이름에 빌드 식별자를 넣고(`esm-<id>/`) import map의 `yona/` 값과 `th:src`를 그 폴더로 쓰게 하는 안이 있다(`springBoot { buildInfo() }`가 이미 빌드 정보를 만든다. `build.gradle.kts:314` 부근). **이번 전환의 필수는 아니다.** 권장: 후속으로 미룬다. 이유: 지금도 같은 1시간 창이 있고, `th:src` 경로와 11개 Kotlin 시험이 읽는 `esm/` 경로를 함께 바꾸게 되어 TS 전환과 한 번에 하면 위험이 곱해진다.

## 7. 전환 단계 후보

### 7.1 원칙

- **새로 쓰는 모듈**(N 묶음)과 **옛 모듈을 옮기는 것**(0~7 묶음)을 구분한다. 새 모듈은 처음부터 `strict` TS, 모듈 방식 시험(5.2절 B), `import` 우선이다. 옛 모듈은 **동작을 바꾸지 않고** 타입만 붙인다(v1.6 동등성). **타입 옮기기와 수명주기(swap-safe) 고치기는 같은 변경에 섞지 않는다.** 한 화면의 TS 이식은 "그 화면이 htmx 옵트인 대상이 되는 시점"에 `Page` 규약으로 바꾸는 별도 변경으로 둔다.
- 대체되는 옛 모듈(`ShortcutKey.js`, `Usermenu.js`)은 **TS로 옮기지 않고** 새 모듈이 설 때까지 JS 그대로 둔다(`ui-shortcuts.md` 6절 마지막 줄과 같은 정책).
- 옛 모듈 이식의 단위는 "묶음"이고, 묶음 안 모듈은 한 모듈씩 `git mv`로 `.ts`가 된다. 각 모듈의 마무리 조건은 (가) 해당 시험이 산출물로 통과, (나) `tsc` 오류 0, (다) 템플릿과 Kotlin 원문 시험이 통과, (라) 전역 등록 이름과 호출 모양이 그대로(7.3절 계약 시험)이다.
- 줄 수는 `wc -l` 기준 JS 줄이고 TS로 옮기면 선언이 더해져 늘어난다(경험적으로 1~2할, **추측**).

### 7.2 새로 쓰는 모듈(N 묶음, 가장 먼저)

IA(`ui-ia-gitlab.md`), htmx 수명주기(`ui-htmx-plan.md`), IndexedDB(`ui-client-storage.md`), 단축키(`ui-shortcuts.md`) 설계가 스크립트를 새로 쓰게 만드는 곳이다. 규모는 설계 문서의 구조 설명에서 가까운 기존 모듈을 기준으로 잡은 **추측**이다.

| 묶음 | 대상(가칭, 설계 문서 기준) | 선행 조건 | 예상 규모(추측) | 시험 방법 |
|---|---|---|---|---|
| N0 | 도구 체인과 계약 다리: `tsconfig`, `installTs`/`compileTs`, `yona-globals.d.ts`(`$yona`, `Messages`, `yona`, jQuery 플러그인 선언), 시험 하네스의 산출물 경로 전환, `namespace.ts`, `index.ts`(묶음 0, 1의 일부와 공유) | 6절 결정(도구, 출력물) | 설정 수십 줄과 선언 수백 줄 | 산출 JS 위로 기존 시험이 모두 통과, `tsc --noEmit` 0 오류, `esm.test`, `JsLibraryVersionsTest`의 import map 문자열 시험 |
| N1 | `esm/shell/Page.ts`(htmx 수명주기 규약: `htmx:load`로 마운트, `htmx:beforeCleanupElement`로 해제, 문서 수준 핸들러 한 번만 등록하는 가드, `data-swap-safe` 판정 도우미). `ui-htmx-plan.md` 5절, 14절 5번 | N0 | 100~200줄(`yona.Htmx.js` 127줄 참고) | 모듈 방식 시험: 같은 화면을 일반 이동과 htmx 이동으로 열어 DOM, 콘솔 오류, 클릭 1회 동작 1회, 두 번째 방문, 뒤로 간 뒤가 같은지(`ui-htmx-plan.md` 5절 3번) |
| N2 | `esm/store/`(IndexedDB `yona-ui`, `drafts`, `switcher`, `prefs`, `visited`, `search-history`, `meta`, `idb` 래퍼, 메모리 폴백, `BroadcastChannel`). `ui-client-storage.md` 4~6절 | N0, `idb` 도입 결정(vendoring 또는 번들, 3절) | 300~500줄 | 실제 Chromium의 IndexedDB 시험, 다중 탭(`BroadcastChannel`), 쓸 수 없을 때 폴백, localStorage 초안 이전(`temporarySaveHandler` 키 유지) |
| N3 | `esm/shell/shortcuts.ts`(`data-shortcut` 선언 수집, 문서 수준 핸들러 하나, `htmx:load`마다 재수집, 우선순위 화면 > 맥락 > 전역, `?` 안내 자동 표). 대체 대상 `ShortcutKey.js`(293줄), `Usermenu.js`의 `F` 키, 템플릿 `setKeymapLink` 호출 8곳 | N1(`htmx:load` 재수집), 사이드바 데이터의 `shortcut` 필드(N4) | 200~300줄(`ShortcutKey.js` 참고) | 키 충돌 구조 시험(같은 범위에 같은 키를 두 번 선언하면 실패), `ㄹ`과 `KeyF` 물리 키 처리, 입력 중 무시, IME |
| N4 | `esm/shell/`의 셸 동작(사이드바 접기, 레일, 서랍 전환, 접힘 기억, 폭 구간, 전환기 탭). 대체 대상 `Usermenu.js`(250줄, jQuery 46곳)와 핀 사이드바 iframe, `layout.html`의 `shallWeOpenLeftNavigation` 인라인 | N1, N2(`prefs`, `switcher`) | 300~600줄(`Usermenu.js` 참고) | 반응형 뷰포트 6가지(344, 390, 673, 768, 1024, 1280), 접근성(`aria-current`, 키보드), 주소 불변 시험 |

새 모듈이 **옛 모듈에 의존하는 곳**은 단 하나로 줄여야 한다: 옛 전역 `$yona.notify/alert`, `Messages`, `yona.http`는 `yona-globals.d.ts`의 선언으로만 부르고 새 모듈끼리는 `import`로 잇는다. 옛 모듈이 새 모듈을 부를 때(예: `yona.ShortcutKey.setKeymapLink` 호출처를 새 선언으로 교체하는 기간)는 전역 `register`를 유지한다.

### 7.3 옛 모듈 이식(묶음 0~7)

| 묶음 | 대상 모듈(`esm/`) | 선행 조건 | 규모(JS 줄) | 시험 방법 | 비고 |
|---|---|---|---:|---|---|
| 0 | `index.js`(`index.ts`로) | N0 | 46 | `esm.test`, Kotlin `JsLibraryVersionsTest`가 읽는 `index.js` 원문 단언(`import { register, ensureNamespace } from "yona/namespace.js"`)의 대상을 산출물로 바꿈 | 파일은 하나이나 **계약 변경점이 가장 많이 걸리는 곳**(원문 시험, import map). 새 모듈이 늘수록 이 파일이 갱신된다 |
| 1 | `namespace`, `util`, `dates`, `http`, `ui/Favicon`, `ui/FormValidator`, `ui/Elevator`, `webauthn`, `ReceiverList`, `OriginalMessage`, `Clipboard`, `KeyControl`, `ui/SubmitOnce`, `ui/ItemSearch` (14개) | N0 | 1,619 | 각 파일의 기존 시험(`util.test`, `dates.test`, `http.test`, `clipboard.test`, `keycontrol.test` 등)을 산출물로. **시험이 없는 `Elevator`, `SubmitOnce`, `ItemSearch`, `Favicon`은 이식 전에 시험을 먼저 추가**(`ItemSearch`는 Kotlin 원문 시험뿐) | 순수하거나 jQuery가 거의 없다. 하네스가 `util`, `dates`, `http`를 항상 불러오므로 **이 세 개를 옮기면 하네스 로딩 경로가 처음으로 산출물을 시험**한다(가장 좋은 첫 관문) |
| 2 | `ui/Dialog`, `ui/Toast`, `ui/Dropdown`, `ui/Tabs`, `Tabs`, `ui/Typeahead`, `ui/Calendar`, `ui/Select2` (8개) | 1, `@types/jquery`, `@types/bootstrap`, `@types/select2`, 달력 타입 | 1,791 | `dialog.test`, `toast.test`, `tabs.test`, `typeahead.test`, `calendar.test`, `select2.test`(산출물). `ui/Select2`는 htmx 교체 뒤 정리(D-96)를 N1의 `Page`로 옮길 때 다시 확인 | 대부분 `(function(ns){ ... })("yona.ui.X")` 형태와 `$yona.createNamespace`를 쓴다. 모듈 형태로 바꾸는 방식을 정해야 한다(8절 질문 5) |
| 3 | `common/yona.Common.js`, `yona.Csrf.js`, `yona.Htmx.js`, 번들 2개의 입력 | 1, 2, 4 일부(모듈이 `$yona`를 import로 받기 전까지는 전역 유지) | 1,008 | `template.test`, `xssclean.test`, `csrf.test`, `htmx.test`, `bundle.test.mjs`(번들 동등성), 기존 시험 전체(`yona.Common.js`를 부르는 시험 34개) | **가장 민감한 묶음.** (1) `$yona = ...` 암묵 전역 대입을 `window.$yona =`로, (2) `Csrf`는 클래식 스크립트로 유지(순서), (3) 번들 입력을 빌드 산출 경로로. 번들을 Gradle 단계로 옮기는 시점(6.3) |
| 4 | `Files`, `Attachments`, `Mention`, `Markdown`, `TitleHeadAutoCompletion`, `temporarySaveHandler`(N2로 대체 검토), `CommentForm`, `Comment`, `SubComment`, `Subtask`, `Tasklist`, `CommentAttachmentsUpdate`, `Request`, `Pagination`, `twoColumnMode`, `showSubtask`, `detectChange` (17개) | 1, 2, 3 | 3,766 | `attachments.test`, `files.test`, `mention.test`, `highlight.test`, `titlehead.test`, `draft.test`, `subcomment.test`, `tasklist.test`, `commentattach.test`, `requestas.test`, `htmx.test`, `twocolumn.test` | 댓글과 첨부의 한 덩어리(이슈, 게시글, 코드 댓글이 모두 쓴다). `yona.Mention({...})` 직접 호출 계약과 `temporarySaveHandler` 전역 이름(7개 모듈이 부름)에 주의 |
| 5 | `organization.*`(5), `project.*`(10), `resetPassword`, `user.Setting`, `user.SignUp`, `user.View`, `site.MassMail`, `LoginDialog`(21개) | 1~4 | 3,364 | `orgmisc.test`, `projectsettings.test`, `projectmember.test`, `projecthome.test`, `avatarcrop.test`, `signup.test`, `userview.test`, `misc.test`, `securitykey.test`, `logindraft.test`, `member.test` + 해당 Kotlin 화면 시험 | IA의 P1(사이트 관리, 사용자 설정), P3(조직)과 같은 순서로 하면 swap-safe 판정 후보가 가장 일찍 나온다(`ui-htmx-plan.md` 5절 4번). 단순 폼이 많다 |
| 6 | `board.*`(3), `issue.Write/List/MassUpdate/Assginee/Sharer`, `milestone.*`(2), `review.List/CommentEdit`, `pullrequest.Meta/Reviewers`, `git.Write`, `code.Nohead`(16개) | 1~4 | 2,402 | `listviews.test`, `writeforms.test`, `massupdate.test`, `select2.test`, `milestoneview.test`, `htmx.test`, `privates.test`, `gitwrite.test`, `orgmisc.test` | 목록과 작성 화면. `issue.List`는 htmx와 `Pagination`에 직접 결합(전역 `htmx`) |
| 7 | `issue.View`, `issue.LabelEditor`, `code.Browser`, `code.Diff`, `code.SvnDiff`, `CodeCommentBlock`, `CodeCommentBox`, `git.View`, `Migration`(9개) | 1~6 | 5,105 | `issueview.test`, `labeleditor.test`, `browser.test`, `ace.test`, `codebrowserxss.test`, `requestas.test`, `svndiff.test`, `codecommentblock.test`, `codecommentbox.test`, `gitview.test`, `migration.test` + Kotlin 원문 시험(`AceVendorTest`, `MigrationPageTest`, `StickyPanelsTest`) | 큰 모듈 6개가 3,800줄. 한 모듈을 여러 PR로 쪼갤 수 있도록 안쪽 함수 단위 시험이 있는지 먼저 확인. `Migration`은 Vue 전역(`Vue`)과 전용 레이아웃이라 별도 |
| N4대체 | `Usermenu.js`(250줄) | N4 | 250 | N4의 시험 | **이식하지 않고** N4가 대체. 대체 전까지 JS 유지 |
| N3대체 | `ShortcutKey.js`(293줄) | N3 | 293 | N3의 시험 | **이식하지 않고** N3가 대체. 템플릿 `setKeymapLink` 8곳과 `project.Global.js` 호출을 정리 |

- 합계: 묶음 0~7(대체 제외) 18,093줄 + 대체 2개 543줄 = `esm/` 18,636줄은 일치한다(0:46, 1:1,619, 2:1,791, 4:3,766, 5:3,364, 6:2,402, 7:5,105 = 18,093. N1대체 250 + N3대체 293 = 543. 18,093 + 543 = 18,636). 묶음 3은 `esm/` 밖의 1,008줄이다.
- **템플릿 인라인 2,131줄(119블록)**은 별도 묶음이 아니라 **그 템플릿이 속한 묶음에서 함께** 모듈로 흡수하거나 4.2절의 전달 방식(B, C)으로 줄인다. 범위 점검의 묶음별 표에 템플릿과 블록 수를 적었다. 인라인이 가장 큰 곳(`issue/view.html` 164줄)은 묶음 7과 같이 가야 한다.
- **IA 단계와의 대응(제안, 총괄 결정)**: IA P0(셸)은 N0, N1, N4(+N3, N2 일부)와 같이, P1은 묶음 5의 `user.*`, `site.*`와 묶음 2, P2는 묶음 6, 7과 묶음 5의 `project.*`, P3는 묶음 5의 `organization.*`와 홈 화면이다. 묶음 1~4(공통 하부)는 P0와 병행해 앞서 끝내는 것이 이후 단계가 모두 TS 위에서 시작하게 해 준다.

### 7.4 순서의 이유(요약)

1. **N0 → 묶음 1**: 순수 모듈이고 하네스가 이미 매번 불러오는 `util`, `dates`, `http`를 옮기면 "산출물로 650개 시험이 도는가"라는 가장 큰 불확실성을 **가장 작은 변경으로** 먼저 풀 수 있다.
2. **묶음 2**: 위젯은 여러 화면이 공유하고(`Select2`는 10개 모듈) 타입 선언 비용(`@types/*`)이 처음 몰리는 곳이다. 여기서 jQuery 플러그인 타입 규칙이 정해지면 5~7이 빨라진다.
3. **N1, N2, N3, N4는 0~2와 병행**할 수 있다. 새 모듈은 옛 모듈에 거의 의존하지 않기 때문이다(전역 선언만).
4. **묶음 3은 4 앞이 아니라 4와 맞물려** 한다. `$yona`의 선언은 N0에서 `.d.ts`로 먼저 적고(전역 유지), 구현 이식은 사용처가 안정된 뒤에 한다. 실행 시점 계약이 가장 민감하기 때문이다.
5. **7이 마지막**: 가장 크고 얽혀 있다. 앞 묶음이 만든 타입과 도우미를 쓴다.

## 8. 위험과 열린 질문

### 8.1 TS 전환이 기존 화면 동작(계약)을 깨뜨릴 수 있는 곳

| # | 위험 | 근거 | 완화 |
|---|---|---|---|
| R1 | `class`로 바꾸면 템플릿의 `yona.Mention({...})` 같은 **`new` 없는 호출**이 깨진다 | 템플릿 24줄, `registerModule`은 함수면 `new`, 객체면 `.init()`(`yona.Common.js:106-126`) | 함수 생성자 모양을 유지(타입은 `interface`와 생성자 시그니처로). 이식 전에 템플릿의 호출 모양을 표로 만들어 계약 시험으로 둔다 |
| R2 | `$yona = yona.Common = (function(){...})()`는 **선언 없는 암묵 전역**이다. 엄격 모드와 모듈에서는 `ReferenceError` | `yona.Common.js:21-23` | `window.$yona = ...`로 명시. 같은 변경에 클래식 유지(묶음 3) |
| R3 | `yona.Csrf.js`가 늦게 실행되면 CSRF 토큰이 안 실린다. 이 파일은 요청 전에 `XMLHttpRequest`와 `fetch`를 교체해야 한다 | `scripts.html:41`, `layout-framed.html:38`, `migration-layout.html:38` | 클래식(IIFE)으로 컴파일해 **모듈로 만들지 않는다**. 이식하더라도 `tsc`가 `import/export` 없는 파일을 스크립트로 내므로 가능하다 |
| R4 | `yona-layout.js`에 들어 있는 `yona.Common.js`와 `yona.Htmx.js`의 실행 시점이 head 동기에서 모듈 지연으로 바뀌면, head와 본문 인라인이 `$yona`/`htmx`를 즉시 쓰는 곳이 깨진다 | `layout.html:42-60`, `scripts.html` 인라인 | 이 둘은 번들(클래식)에 계속 넣는다. 번들 입력만 빌드 산출 경로로 바꾼다 |
| R5 | 시험 하네스가 소스 텍스트를 정규식으로 잘라 쓴다(`classicFromEsm`, `sourceSnippet`, `templateSnippet`). TS 소스 문법(`import type`, 타입 주석, `satisfies`)이 정규식을 깨뜨린다 | `harness.mjs:82-91`, 시험 5개 파일 | 산출 JS를 시험한다(5.2 A). 경계 문자열을 쓰는 `sourceSnippet`의 표지 문자열이 산출물에서 바뀌지 않는지 확인 |
| R6 | Kotlin 원문 시험 11개가 `esm/*.js` 경로와 원문 문자열에 의존한다 | 5.1절 | 경로를 산출 폴더(또는 TS 원본)로 바꾸고 문자열 단언을 타입 주석에 영향받지 않게 조정 |
| R7 | 전역에 올라가는 비 `yona.*` 이름(`temporarySaveHandler`, `cancelAllDraftTimers`, `_tab` 등)과 `onclick`/`onsubmit` 속성 23개는 모듈 스코프로 옮기면 사라진다 | 2.3절, 1.4절 | `register`/`window` 대입을 유지. 이름 목록을 계약 시험(템플릿과 모듈이 쓰는 전역 이름이 모두 존재하는지)으로 |
| R8 | `loadModule`의 폴백 로딩(`service/yona.X.js`)은 죽은 경로이나, 동적 `import()`로 바꾸면 실행 순서와 오류 처리가 달라진다 | `yona.Common.js:77-100`(폴백 주소 폴더 없음, `ls` 확인) | 지금은 템플릿이 먼저 모듈 태그를 불러 폴백이 쓰이지 않는다. 지우지 말고 그대로 두거나, 지울 때 선생님 확인을 받는다(정리 판단은 선생님 확인이 필요하다) |
| R9 | `registerModule`이 호출될 때마다 `[data-bs-toggle=popover]`를 초기화하는 부수 효과가 있다 | `yona.Common.js:116` | TS에서도 같은 위치에서 같은 일을 하게 하고 시험으로 고정 |
| R10 | 타입 정의와 실제 복사해 둔 라이브러리 버전의 불일치(`@types/select2` 등)가 가짜 안전감을 준다 | 3절 | 형 검사용 `@types`의 버전을 매니페스트에 기록. 선언이 실제와 다르면 `unknown`으로 두고 좁히는 규칙 |
| R11 | 지원 브라우저 기준(출력 `target`)을 정하지 않으면 새 문법(`?.`, `??`, 클래스 필드)을 옛 브라우저가 못 읽는다 | import map 자체가 Chrome 89, Firefox 108, Safari 16.4 이상(일반 지식) | 이미 import map을 쓰므로 그 이상이다. `target: ES2022` 같은 값은 도입 때 결정 |
| R12 | `jQuery` 이벤트와 네이티브 이벤트가 섞여 있다(`$(document).on('shown.bs.tab')`, `htmx:afterSwap`를 jQuery로 구독) | `Tabs.js:21`, `issue.List.js:210` | Bootstrap 5는 jQuery가 먼저 있으면 jQuery 어댑터를 등록한다(`scripts.html:46` 주석). 동작을 바꾸지 않고 타입만 붙인다. 네이티브 이벤트 이전은 별도 변경 |
| R13 | 소스맵과 `.ts` 원문의 서빙: `.map`은 `application/x-navimap`, `.ts`는 `octet-stream`으로 나간다 | `StaticAssets.kt:54-64` | 소스맵을 낼지(개발 시) 정하고, 원본 `.ts`를 자원 폴더에 두지 않는다 |

### 8.2 옛 모듈과 새 모듈이 공존하는 동안의 규칙(제안)

1. **경로 불변**: 산출물은 항상 `esm/<원래 이름>.js`로 나오고 템플릿의 `th:src`와 import map은 바꾸지 않는다.
2. **한 파일에 확장자 둘 금지**: 한 이름은 `.js`이거나 `.ts`이다. 옮길 때 같은 커밋에서 `.js`를 지운다.
3. **전역 이름 유지**: 이식한 모듈도 `register("yona.X", X)`를 그대로 부른다. 모든 호출처가 `import`로 바뀐 뒤에만 지운다(`namespace.js` 머리 주석의 원칙).
4. **새 모듈은 전역을 읽지 않는다**: `import`로만 잇고, 옛 전역은 `yona-globals.d.ts`에 선언한 것만 부른다. 선언이 늘어나는 것을 이식 진행 지표로 쓴다(선언이 줄어든다).
5. **수명주기 옵트인은 별도 변경**: 이식한 옛 모듈을 `Page` 규약으로 바꾸는 일은 swap-safe 판정(`ui-htmx-plan.md` 5절 3번)을 통과한 화면에서만 한다.
6. **저장소 직접 접근 금지 시험**: 새 화면 모듈은 `indexedDB`, `localStorage`를 직접 부르지 않고 `store/`를 거치는지 구조 시험이 지킨다(`ui-client-storage.md` 6절). 옛 모듈의 직접 접근(`temporarySaveHandler`, `Usermenu`)은 대체될 때까지 예외로 목록화한다.
7. **계약 시험(제안)**: (가) 템플릿이 쓰는 `yona.*` 전역 이름과 호출 모양(함수형인지 생성자형인지), (나) 모듈이 쓰는 비 `yona.*` 전역 이름이 모두 등록되는지, (다) import map 문자열, (라) 산출 폴더에 `.ts` 원문이 새지 않는지를 하나의 시험으로 둔다. 이 시험은 이식 때마다 실패로 이식 누락을 알려 준다.

### 8.3 열린 질문(총괄 판단 필요)

| # | 질문 | 선택지와 권장안 |
|---|---|---|
| 1 | 도구 체인 | 6.2절. **권장: A(`tsc` 단독, `isolatedModules`와 `verbatimModuleSyntax`를 켜 B로 갈 길을 열어 둠).** 근거: `esm/` 구조와 import map 계약을 바꾸지 않는다 |
| 2 | 출력물 | 6.3절. **권장: 가(빌드 때 만든다, Gradle `compileTs`).** 근거: Node가 이미 빌드 필수(SCSS), 이중 관리 방지 |
| 3 | 서버 값 전달 | 4.2절. **권장: 전역은 `<meta>`, 화면은 `data-*`, 객체는 단일 JSON. 옮길 때 화면 단위로.** 서버에 안전한 JSON 직렬화 도우미가 필요하다 |
| 4 | `Messages` | **권장: 지금 방식 유지(`declare function Messages`), 키 유니온 생성은 후속** |
| 5 | `(function(ns){...})("yona.ui.X")` + `$yona.createNamespace` 형태 모듈 6개(`Calendar`, `Dialog`, `Dropdown`, `Select2`, `Toast`, `Typeahead`)와 다른 IIFE 5개(`Clipboard`, `KeyControl`, `Mention`, `Tabs`, `webauthn`)를 어떻게 모듈로 바꿀지: 안쪽 코드를 그대로 두고 export만 붙이는지, 내부를 `class`나 모듈 함수로 재구성하는지 | **권장: 1차는 형태를 유지**(바깥 IIFE를 `export default`로 감싸 `register`만 붙임), 재구성은 별도 변경. 이유: R1(호출 모양) |
| 6 | `loadModule` 폴백 경로(죽은 코드 후보)를 정리할지 | **권장: 지금은 건드리지 않고 `@deprecated` 표시만.** 정리 판단은 선생님 확인 필요 |
| 7 | 형 검사 엄격도 | **권장: 새 모듈은 처음부터 `strict`, 옛 모듈 이식분은 `strict`를 목표로 하되 시작할 때는 `noImplicitAny`만 예외로 둔다.** 이유: jQuery 이벤트 핸들러의 `this`와 `$.fn` 플러그인 때문에 처음부터 전부 `strict`를 요구하면 묶음 2에서 멈출 수 있다(**추측**) |
| 8 | `frontend/` 폴더 | 추적되지 않는 Turbo 잔재로 보인다(추측). **정리할지, TS 원본 위치로 쓸지** 정해 주십시오(정리는 삭제이므로 확인이 필요하다) |
| 9 | `idb` 도입 방식 | `lib/idb/`에 복사해 import map에 추가(D-91 vendoring 방식) 또는 TS 빌드에서 번들. **권장: 복사 + import map.** 이유: 번들러 없이 가는 6.2 권장안과 맞다 |
| 10 | 시험 코드 자체를 TS로 바꿀지 | **권장: 후속(`.mjs` 유지).** 이유: 650개 시험이 v1.6 동등성의 안전망이라 같은 시기에 흔들지 않는다 |
| 11 | JS 시험을 Gradle에 연결할지 | 지금은 수동이다. 연결하면 Playwright 브라우저와 상위 폴더 `node_modules` 의존이 빌드에 들어온다. **권장: 이번 전환에서는 연결하지 않고 `tsc --noEmit`만 Gradle에 건다.** 이유: 서버 부팅 재현성 제약과 분리 |
| 12 | 출력 `target`과 지원 브라우저 | 일반 지식으로는 import map 지원 브라우저 이상이면 ES2022가 안전하다. **도입 때 `tsconfig`에 적고 위키에 기록** |

### 8.4 이 조사에서 끝까지 확인하지 못한 것

- **TS 빌드 시간과 `tsc`의 실제 오류 수**: Gradle, npm을 실행하지 않는 조사라서 측정하지 않았다. 이식 전에 `allowJs`+`checkJs`로 한 번 돌려 오류 수를 재면 묶음 크기 추정의 근거가 된다(7절의 "추측" 표시는 이 때문이다).
- **시험 650개의 현재 통과 상태**: 실행하지 않았다(`run.mjs` 미실행). 개수는 `grep`으로 센 값이다.
- **`@types/*`와 라이브러리 자체 타입의 존재**: 일반 지식이다. 설치해 확인해야 한다.
- **Kotlin 원문 시험 11개의 단언 전부**: 경로 의존은 `grep`으로 확인했고 단언 내용은 대표 사례(`JsLibraryVersionsTest`, `StickyPanelsTest`)만 읽었다.
- **`bootstrap-switch.js`의 내용과 `vue-widgets` 소스**: 사용처와 `package.json`만 보았다.
- **일부 DOM 결합도와 난이도 판정**: 표의 DOM 열은 jQuery 호출 횟수에 손으로 고친 값이며, 난이도는 줄 수와 의존, 읽은 코드로 정한 판단이다. 이식에 들어가기 전에 해당 묶음 시작 때 한 번 더 검토해야 한다.

## 범위 점검

확인용 표이다. 앞의 모든 수량이 아래에서 맞아야 한다. 집계는 `find`와 위 생성 표에서 낸 값이다.

### A. `esm/` 모듈 88개, `common/` 3개, 번들 2개의 묶음 배정

| 묶음 | 모듈 수 | 줄 합 | 모듈(`esm/` 기준 경로) |
|---|---:|---:|---|
| 0 | 1 | 46 | `index` |
| 1 | 14 | 1619 | `Clipboard`, `KeyControl`, `OriginalMessage`, `ReceiverList`, `dates`, `http`, `namespace`, `ui/Elevator`, `ui/Favicon`, `ui/FormValidator`, `ui/ItemSearch`, `ui/SubmitOnce`, `util`, `webauthn` |
| 2 | 8 | 1791 | `Tabs`, `ui/Calendar`, `ui/Dialog`, `ui/Dropdown`, `ui/Select2`, `ui/Tabs`, `ui/Toast`, `ui/Typeahead` |
| 4 | 17 | 3766 | `Attachments`, `Comment`, `CommentAttachmentsUpdate`, `CommentForm`, `Files`, `Markdown`, `Mention`, `Pagination`, `Request`, `SubComment`, `Subtask`, `Tasklist`, `TitleHeadAutoCompletion`, `service/detectChange`, `service/showSubtask`, `service/temporarySaveHandler`, `service/twoColumnMode` |
| 5 | 21 | 3364 | `LoginDialog`, `service/organization.Global`, `service/organization.Member`, `service/organization.New`, `service/organization.Setting`, `service/organization.View`, `service/project.ChangeVCS`, `service/project.Delete`, `service/project.Fork`, `service/project.Global`, `service/project.Home`, `service/project.Member`, `service/project.New`, `service/project.Setting`, `service/project.Transfer`, `service/project.Webhook`, `service/resetPassword`, `service/site.MassMail`, `service/user.Setting`, `service/user.SignUp`, `service/user.View` |
| 6 | 16 | 2402 | `service/board.List`, `service/board.View`, `service/board.Write`, `service/code.Nohead`, `service/git.Write`, `service/issue.Assginee`, `service/issue.List`, `service/issue.MassUpdate`, `service/issue.Sharer`, `service/issue.Write`, `service/milestone.View`, `service/milestone.Write`, `service/pullrequest.Meta`, `service/pullrequest.Reviewers`, `service/review.CommentEdit`, `service/review.List` |
| 7 | 9 | 5105 | `CodeCommentBlock`, `CodeCommentBox`, `service/Migration`, `service/code.Browser`, `service/code.Diff`, `service/code.SvnDiff`, `service/git.View`, `service/issue.LabelEditor`, `service/issue.View` |
| N3(대체) | 1 | 293 | `ShortcutKey` |
| N4(대체) | 1 | 250 | `Usermenu` |
| **합계** | **88** | **18636** | `find esm -name '*.js'` 88개, `wc -l` 합 18,636줄과 일치 |
| 3 | 3(`esm/` 밖) | 1,008 | `common/yona.Common.js`(797), `common/yona.Csrf.js`(84), `common/yona.Htmx.js`(127) |
| 3(번들 입력 관리) | 2(산출물) | (압축 3줄씩) | `yona-layout.js`(161,412바이트), `yona-common.js`(22,173바이트): 소스가 아니라 산출물. 입력 목록 `build-tools/js-bundle/bundles.mjs`는 묶음 3에서 관리 |

`esm/` 88 = 0(1) + 1(14) + 2(8) + 4(17) + 5(21) + 6(16) + 7(9) + N3대체(1) + N4대체(1). 누락과 중복이 없다(생성 스크립트가 88개 전부에 묶음이 있는지 확인했고 빠진 항목이 있으면 오류를 냈다).

### B. `<script`를 가진 템플릿 106개의 묶음 배정

집계: `grep -rl '<script' templates` 106개(템플릿 전체 230개 가운데). 인라인 JS 블록은 `src`가 없고 `type`이 없거나 `text/javascript`, `module`, `importmap`인 `<script>`이며, 서버 값 표현식은 `th:inline="javascript"` 블록 안의 `[[...]]`, `[(...)]` 개수이다(주석 안의 `/*[[...]]*/` 형태 포함). `text/x-mustache`, `text/x-yona-template`는 코드가 아니라서 제외했다.


#### 묶음 0: 템플릿 7개 (인라인 JS 블록 14개, 491줄, `th:inline="javascript"` 블록 8개, 서버 값 표현식 32개)

| 템플릿 | JS 블록 | 줄 | `th:inline` 블록 | 표현식 | 근거 |
|---|---:|---:|---:|---:|---|
| `common/analytics.html` | 1 | 110 | 1 | 2 | 기반(레이아웃, 공통 스크립트) |
| `common/importmap.html` | 1 | 7 | 0 | 0 | 기반(레이아웃, 공통 스크립트) |
| `common/scripts.html` | 3 | 144 | 2 | 12 | 기반(레이아웃, 공통 스크립트) |
| `common/sidebar.html` | 1 | 35 | 0 | 0 | 기반(레이아웃, 공통 스크립트) |
| `layout/layout-framed.html` | 2 | 14 | 1 | 6 | 기반(레이아웃, 공통 스크립트) |
| `layout/layout.html` | 4 | 53 | 3 | 9 | 기반(레이아웃, 공통 스크립트) |
| `layout/migration-layout.html` | 2 | 128 | 1 | 3 | 기반(레이아웃, 공통 스크립트) |

#### 묶음 1: 템플릿 2개 (인라인 JS 블록 2개, 32줄, `th:inline="javascript"` 블록 2개, 서버 값 표현식 5개)

| 템플릿 | JS 블록 | 줄 | `th:inline` 블록 | 표현식 | 근거 |
|---|---:|---:|---:|---:|---|
| `user/edit-two-factor-key.html` | 1 | 16 | 1 | 3 | 로드: webauthn |
| `user/login-two-factor.html` | 1 | 16 | 1 | 2 | 로드: webauthn |

#### 묶음 2: 템플릿 3개 (인라인 JS 블록 2개, 15줄, `th:inline="javascript"` 블록 1개, 서버 값 표현식 1개)

| 템플릿 | JS 블록 | 줄 | `th:inline` 블록 | 표현식 | 근거 |
|---|---:|---:|---:|---:|---|
| `common/calendar.html` | 0 | 0 | 0 | 0 | 로드: ui/Calendar |
| `common/select2.html` | 1 | 1 | 1 | 1 | 로드: ui/Select2 |
| `user/partial-token-form.html` | 1 | 14 | 0 | 0 | 로드: ui/Calendar |

#### 묶음 3: 템플릿 2개 (인라인 JS 블록 1개, 3줄, `th:inline="javascript"` 블록 0개, 서버 값 표현식 0개)

| 템플릿 | JS 블록 | 줄 | `th:inline` 블록 | 표현식 | 근거 |
|---|---:|---:|---:|---:|---|
| `welcome/restart.html` | 0 | 0 | 0 | 0 | 모듈 없음, 디렉터리 기본 |
| `welcome/secret.html` | 1 | 3 | 0 | 0 | 모듈 없음, 디렉터리 기본 |

#### 묶음 4: 템플릿 5개 (인라인 JS 블록 3개, 21줄, `th:inline="javascript"` 블록 2개, 서버 값 표현식 2개)

| 템플릿 | JS 블록 | 줄 | `th:inline` 블록 | 표현식 | 근거 |
|---|---:|---:|---:|---:|---|
| `common/comment-delete-modal.html` | 1 | 3 | 1 | 1 | 로드: Comment |
| `common/comment-form.html` | 0 | 0 | 0 | 0 | 로드: CommentForm |
| `common/file-uploader.html` | 0 | 0 | 0 | 0 | 모듈 없음, 디렉터리 기본 |
| `common/markdown.html` | 1 | 15 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `pullrequest/list.html` | 1 | 3 | 0 | 0 | 로드: service/twoColumnMode |

#### 묶음 5: 템플릿 40개 (인라인 JS 블록 43개, 384줄, `th:inline="javascript"` 블록 22개, 서버 값 표현식 31개)

| 템플릿 | JS 블록 | 줄 | `th:inline` 블록 | 표현식 | 근거 |
|---|---:|---:|---:|---:|---|
| `audit/list.html` | 1 | 19 | 0 | 0 | 모듈 없음, 디렉터리 기본 |
| `common/login-dialog.html` | 0 | 0 | 0 | 0 | 로드: LoginDialog, webauthn |
| `organization/create.html` | 1 | 5 | 0 | 0 | 로드: service/organization.New |
| `organization/delete-form.html` | 1 | 20 | 1 | 2 | 모듈 없음, 디렉터리 기본 |
| `organization/header.html` | 1 | 3 | 0 | 0 | 로드: service/organization.Global |
| `organization/list.html` | 1 | 9 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `organization/members.html` | 1 | 5 | 1 | 1 | 로드: service/organization.Member |
| `organization/pullrequest-list.html` | 2 | 18 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `organization/setting.html` | 1 | 3 | 0 | 0 | 로드: service/organization.Setting |
| `organization/view.html` | 1 | 3 | 0 | 0 | 로드: service/organization.View |
| `project/change-vcs.html` | 1 | 6 | 1 | 2 | 로드: service/project.ChangeVCS |
| `project/create.html` | 1 | 6 | 1 | 1 | 로드: service/project.New |
| `project/delete.html` | 1 | 5 | 1 | 1 | 로드: service/project.Delete |
| `project/home.html` | 2 | 16 | 1 | 1 | 로드: service/project.Home |
| `project/importing.html` | 2 | 18 | 1 | 1 | 로드: service/project.New |
| `project/list.html` | 1 | 9 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `project/members.html` | 1 | 5 | 1 | 1 | 로드: service/project.Member |
| `project/menu.html` | 1 | 29 | 1 | 8 | 로드: service/project.Global |
| `project/ruleset-form.html` | 1 | 14 | 0 | 0 | 모듈 없음, 디렉터리 기본 |
| `project/setting.html` | 1 | 6 | 0 | 0 | 로드: service/project.Setting |
| `project/transfer.html` | 1 | 5 | 1 | 1 | 로드: service/project.Transfer |
| `project/webhooks.html` | 1 | 16 | 0 | 0 | 로드: service/project.Webhook |
| `pullrequest/fork.html` | 1 | 3 | 0 | 0 | 로드: service/project.Fork |
| `site/data.html` | 1 | 6 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `site/issue-list.html` | 1 | 4 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `site/mass-mail.html` | 1 | 6 | 1 | 2 | 로드: service/site.MassMail |
| `site/post-list.html` | 1 | 4 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `site/project-list.html` | 1 | 35 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `site/user-list.html` | 2 | 49 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `user/edit-emails.html` | 1 | 3 | 0 | 0 | 로드: service/user.Setting |
| `user/edit-notifications.html` | 1 | 3 | 0 | 0 | 로드: service/user.Setting |
| `user/edit-password.html` | 1 | 3 | 0 | 0 | 로드: service/user.Setting |
| `user/edit-token.html` | 1 | 3 | 0 | 0 | 로드: service/user.Setting |
| `user/edit-two-factor.html` | 1 | 1 | 0 | 0 | 모듈 없음, 디렉터리 기본 |
| `user/edit.html` | 1 | 3 | 0 | 0 | 로드: service/user.Setting |
| `user/files.html` | 1 | 24 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `user/login.html` | 1 | 3 | 0 | 0 | 모듈 없음, 디렉터리 기본 |
| `user/reset-password.html` | 1 | 3 | 0 | 0 | 로드: service/resetPassword |
| `user/signup.html` | 1 | 6 | 1 | 0 | 로드: service/user.SignUp |
| `user/view.html` | 1 | 5 | 1 | 1 | 로드: service/showSubtask, service/twoColumnMode, service/user.View |

#### 묶음 6: 템플릿 35개 (인라인 JS 블록 41개, 843줄, `th:inline="javascript"` 블록 31개, 서버 값 표현식 74개)

| 템플릿 | JS 블록 | 줄 | `th:inline` 블록 | 표현식 | 근거 |
|---|---:|---:|---:|---:|---|
| `board/create.html` | 1 | 38 | 1 | 3 | 로드: TitleHeadAutoCompletion, service/board.Write |
| `board/edit.html` | 1 | 11 | 1 | 1 | 로드: service/board.Write |
| `board/list.html` | 1 | 25 | 1 | 2 | 로드: service/board.List, service/twoColumnMode |
| `board/view.html` | 1 | 86 | 1 | 16 | 로드: CommentAttachmentsUpdate, SubComment, Tasklist, service/board.View |
| `code/nohead-hg.html` | 2 | 15 | 1 | 1 | 로드: service/code.Nohead |
| `code/nohead-svn.html` | 1 | 5 | 1 | 1 | 로드: service/code.Nohead |
| `code/nohead.html` | 2 | 15 | 1 | 1 | 로드: service/code.Nohead |
| `help/markdown.html` | 1 | 23 | 0 | 0 | 모듈 없음, 디렉터리 기본 |
| `help/toc.html` | 1 | 5 | 0 | 0 | 모듈 없음, 디렉터리 기본 |
| `index/notifications.html` | 1 | 70 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `index/partial-notifications.html` | 1 | 30 | 1 | 3 | 모듈 없음, 디렉터리 기본 |
| `issue/create.html` | 1 | 59 | 1 | 8 | 로드: Subtask, TitleHeadAutoCompletion, service/issue.Assginee, service/issue.Write |
| `issue/edit.html` | 1 | 91 | 1 | 11 | 로드: Subtask, TitleHeadAutoCompletion, service/issue.Assginee, service/issue.Write |
| `issue/list.html` | 1 | 19 | 1 | 1 | 로드: service/issue.List, service/showSubtask, service/twoColumnMode |
| `issue/my-list.html` | 1 | 30 | 1 | 1 | 로드: service/issue.List, service/showSubtask, service/twoColumnMode |
| `issue/partial-massupdate.html` | 1 | 15 | 1 | 1 | 로드: service/issue.MassUpdate |
| `milestone/create.html` | 1 | 13 | 1 | 1 | 로드: service/milestone.Write, ui/Calendar |
| `milestone/edit.html` | 1 | 13 | 1 | 1 | 로드: service/milestone.Write, ui/Calendar |
| `milestone/list.html` | 1 | 12 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `milestone/view.html` | 1 | 16 | 1 | 3 | 로드: service/milestone.View |
| `organization/board-list.html` | 1 | 10 | 1 | 1 | 로드: service/board.List, service/twoColumnMode |
| `organization/issue-list.html` | 1 | 7 | 0 | 0 | 로드: service/issue.List, service/twoColumnMode |
| `pullrequest/clone.html` | 1 | 17 | 1 | 5 | 모듈 없음, 디렉터리 기본 |
| `pullrequest/create.html` | 1 | 16 | 1 | 2 | 로드: service/git.Write, service/pullrequest.Meta |
| `pullrequest/edit.html` | 1 | 16 | 1 | 2 | 로드: service/git.Write |
| `pullrequest/partial-info.html` | 1 | 26 | 0 | 0 | 모듈 없음, 디렉터리 기본 |
| `pullrequest/partial-search.html` | 2 | 29 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `reviewthread/list.html` | 1 | 7 | 1 | 1 | 로드: service/review.List |
| `search/partial-items.html` | 1 | 4 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `search/partial-milestones.html` | 1 | 4 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `search/partial-projects.html` | 1 | 4 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `search/partial-search.html` | 2 | 39 | 2 | 1 | 모듈 없음, 디렉터리 기본 |
| `search/partial-users.html` | 1 | 4 | 1 | 1 | 모듈 없음, 디렉터리 기본 |
| `wiki/edit.html` | 3 | 34 | 1 | 0 | 모듈 없음, 디렉터리 기본 |
| `wiki/shell.html` | 1 | 35 | 0 | 0 | 모듈 없음, 디렉터리 기본 |

#### 묶음 7: 템플릿 12개 (인라인 JS 블록 13개, 342줄, `th:inline="javascript"` 블록 9개, 서버 값 표현식 67개)

| 템플릿 | JS 블록 | 줄 | `th:inline` 블록 | 표현식 | 근거 |
|---|---:|---:|---:|---:|---|
| `code/branches.html` | 1 | 14 | 0 | 0 | 모듈 없음, 디렉터리 기본 |
| `code/compare-svn.html` | 1 | 10 | 1 | 5 | 로드: service/code.SvnDiff |
| `code/compare.html` | 1 | 5 | 0 | 0 | 로드: service/code.Diff |
| `code/diff.html` | 1 | 23 | 1 | 5 | 로드: CodeCommentBlock, CodeCommentBox, service/code.Diff, service/review.CommentEdit |
| `code/history.html` | 1 | 29 | 1 | 3 | 모듈 없음, 디렉터리 기본 |
| `code/svn-diff.html` | 1 | 15 | 1 | 7 | 로드: service/code.SvnDiff |
| `code/tags.html` | 1 | 8 | 0 | 0 | 모듈 없음, 디렉터리 기본 |
| `code/view.html` | 1 | 15 | 1 | 4 | 로드: service/code.Browser |
| `issue/view.html` | 1 | 164 | 1 | 33 | 로드: CommentAttachmentsUpdate, ReceiverList, SubComment, Tasklist, service/detectChange, service/issue.Assginee, service/issue.Sharer, service/issue.View |
| `project/issue-labels.html` | 1 | 9 | 1 | 1 | 로드: service/issue.LabelEditor |
| `pullrequest/changes.html` | 1 | 21 | 1 | 2 | 로드: CodeCommentBlock, CodeCommentBox, service/code.Diff, service/review.CommentEdit |
| `pullrequest/view.html` | 2 | 29 | 1 | 7 | 로드: CodeCommentBox, service/git.View, service/pullrequest.Meta, service/pullrequest.Reviewers |


합계: 템플릿 106개, 인라인 JS 블록 119개, 2,131줄, `th:inline="javascript"` 블록 75개, 표현식 212개. 인라인 블록이 없는 4개 템플릿(`common/calendar.html`, `common/comment-form.html`, `common/login-dialog.html`, `welcome/restart.html`)은 표에서 0으로 나온다(외부 파일 불러오기만 있다).
